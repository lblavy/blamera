package dev.blamera.faces

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONObject

/** Android JSONObject alone accepts duplicates and trailing input. Keep the wire contract strict. */
internal fun strictFaceJson(text: String): JSONObject =
    JsonReader(StringReader(text)).use { reader ->
        val result = readFaceValue(reader, 0)
        require(reader.peek() == JsonToken.END_DOCUMENT && result is JSONObject)
        result
    }

private fun readFaceValue(reader: JsonReader, depth: Int): Any = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> readFaceObject(reader, depth + 1)
    JsonToken.BEGIN_ARRAY -> readFaceArray(reader, depth + 1)
    JsonToken.STRING -> reader.nextString()
    JsonToken.NUMBER -> readFaceNumber(reader.nextString())
    JsonToken.BOOLEAN -> reader.nextBoolean()
    JsonToken.NULL -> {
        reader.nextNull()
        JSONObject.NULL
    }
    else -> throw IllegalArgumentException("Invalid gallery JSON token.")
}

private fun readFaceObject(reader: JsonReader, depth: Int): JSONObject {
    require(depth <= MAX_JSON_DEPTH)
    reader.beginObject()
    val result = JSONObject()
    val keys = mutableSetOf<String>()
    while (reader.hasNext()) {
        val name = reader.nextName()
        require(keys.add(name))
        result.put(name, readFaceValue(reader, depth))
    }
    reader.endObject()
    return result
}

private fun readFaceArray(reader: JsonReader, depth: Int): JSONArray {
    require(depth <= MAX_JSON_DEPTH)
    reader.beginArray()
    val result = JSONArray()
    while (reader.hasNext()) result.put(readFaceValue(reader, depth))
    reader.endArray()
    return result
}

private fun readFaceNumber(text: String): Number {
    require(text.length <= MAX_NUMBER_CHARACTERS && JSON_NUMBER.matches(text))
    return if (text.indexOfAny(charArrayOf('.', 'e', 'E')) < 0) {
        requireNotNull(text.toLongOrNull())
    } else {
        text.toDouble().also { require(it.isFinite()) }
    }
}

internal fun sameFaceJson(left: Any, right: Any): Boolean = when {
    left is JSONObject && right is JSONObject -> {
        val keys = left.keys().asSequence().toSet()
        keys == right.keys().asSequence().toSet() &&
            keys.all { sameFaceJson(left.get(it), right.get(it)) }
    }
    left is JSONArray && right is JSONArray -> left.length() == right.length() &&
        (0 until left.length()).all { sameFaceJson(left.get(it), right.get(it)) }
    else -> left == right
}

private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
private const val MAX_JSON_DEPTH = 12
private const val MAX_NUMBER_CHARACTERS = 64
