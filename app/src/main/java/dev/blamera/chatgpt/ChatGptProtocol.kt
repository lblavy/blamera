package dev.blamera.chatgpt

import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val MAX_TRIAL_JPEG_BYTES = TrialLimits.RESPONSE_BYTES
internal const val MAX_TRIAL_REPLY_CHARS = TrialLimits.JSON_BYTES

internal fun parseTrialModels(body: String): List<TrialModel> = trialJson {
    val models = JSONObject(body).getJSONArray("models")
    require(models.length() <= TrialLimits.CATALOG_MODELS)
    val listed = (0 until models.length()).map { models.getJSONObject(it) }
        .filter { it.optString("visibility") == "list" }
        .map { TrialModel(it.getString("slug"), it.getString("display_name")) }
    require(
        listed.all {
            it.slug.isNotBlank() && it.slug.length <= TrialLimits.NAME_CHARS &&
                it.displayName.length <= TrialLimits.NAME_CHARS
        }
    )
    require(listed.map { it.slug }.distinct().size == listed.size)
    listed
}

internal fun trialImageRequest(
    model: String,
    jpeg: ByteArray,
    instruction: String,
    effort: String? = null
): String {
    validateTrialEffort(model, effort)
    val validModel = model.isNotBlank() && model.length <= TrialLimits.NAME_CHARS
    val validInstruction =
        instruction.isNotBlank() && instruction.length <= TrialLimits.INSTRUCTION_CHARS
    if (!validModel || !validInstruction) throw ChatGptTrialException(TrialFailure.INPUT)
    if (!isTrialJpeg(jpeg)) throw ChatGptTrialException(TrialFailure.INPUT)
    val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", instruction))
        .put(
            JSONObject().put("type", "input_image")
                .put(
                    "image_url",
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
                )
        )
    return JSONObject().put("model", model).put("store", false).put("stream", true)
        .apply { if (effort != null) put("reasoning", JSONObject().put("effort", effort)) }
        .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        .toString()
}

private fun isTrialJpeg(bytes: ByteArray): Boolean =
    bytes.size in JPEG_MIN_BYTES..MAX_TRIAL_JPEG_BYTES &&
        bytes[0] == (-1).toByte() && bytes[1] == JPEG_START &&
        bytes[bytes.lastIndex - 1] == (-1).toByte() && bytes.last() == JPEG_END

internal fun parseTrialStream(body: String): String {
    val stream = TrialResponseStream()
    stream.accept(body.toByteArray(Charsets.UTF_8))
    return stream.finish()
}

internal fun trialFailureCode(code: String?): TrialFailure = when (code) {
    "subscription_sharing_usage_limit_exceeded", "subscription_sharing_usage_unavailable",
    "rate_limit_exceeded", "insufficient_quota" -> TrialFailure.USAGE_LIMIT
    "invalid_token", "invalid_grant", "invalid_refresh_token", "refresh_token_reused",
    "refresh_token_expired", "refresh_token_invalidated" -> TrialFailure.AUTHENTICATION
    "model_not_found", "unsupported_model", "unsupported_image", "invalid_image" ->
        TrialFailure.MODEL_UNAVAILABLE
    else -> TrialFailure.INVALID_RESPONSE
}

internal fun requireTrial(valid: Boolean, failure: TrialFailure = TrialFailure.INVALID_RESPONSE) {
    if (!valid) throw ChatGptTrialException(failure)
}

internal fun <T> trialJson(action: () -> T): T = try {
    action()
} catch (_: JSONException) {
    throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
} catch (_: IllegalArgumentException) {
    throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
}

private const val JPEG_MIN_BYTES = 4
private const val JPEG_START: Byte = -40
private const val JPEG_END: Byte = -39
