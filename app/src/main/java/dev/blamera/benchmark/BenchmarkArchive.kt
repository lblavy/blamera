package dev.blamera.benchmark

import dev.blamera.storage.AtomicFileReplacement
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

internal const val MAX_BENCHMARK_REPORT_BYTES = 8 * 1024 * 1024

internal fun checkedReportDocument(document: String): JSONObject {
    require(document.toByteArray(Charsets.UTF_8).size <= MAX_BENCHMARK_REPORT_BYTES) {
        "Benchmark report too large"
    }
    try {
        return JSONObject(document).also {
            require(it.getInt("schema") == 2)
            require(it.getString("runId").matches(Regex("[A-Za-z0-9-]{1,80}")))
            require(it.getString("status") in setOf("running", "complete", "cancelled", "error"))
        }
    } catch (error: JSONException) {
        throw IllegalArgumentException("Invalid benchmark report", error)
    }
}

internal class BenchmarkArchive(
    private val file: File,
    private val replacement: AtomicFileReplacement = AtomicFileReplacement()
) {
    fun begin(document: String) {
        require(checkedReportDocument(document).getString("status") == "running")
        synchronized(lock) { writeLocked(document) }
    }
    fun update(runId: String, document: String): Boolean {
        require(checkedReportDocument(document).getString("runId") == runId)
        return synchronized(lock) {
            val current = readLocked()
            if (current == null || checkedReportDocument(current).getString("runId") != runId) {
                false
            } else {
                writeLocked(document)
                true
            }
        }
    }
    fun read(): String? = synchronized(lock) { readLocked() }

    private fun readLocked(): String? {
        if (!file.exists()) return null
        val bytes = file.inputStream().use {
            it.readNBytes(MAX_BENCHMARK_REPORT_BYTES + 1)
        }
        require(bytes.size <= MAX_BENCHMARK_REPORT_BYTES) { "Benchmark report too large" }
        return bytes.toString(Charsets.UTF_8).also(::checkedReportDocument)
    }
    private fun writeLocked(document: String) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(document.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Same-directory atomic replacement, supported by Android internal storage and Windows tests.
            replacement.replace(pending.toPath(), file.toPath())
        } finally {
            pending.delete()
        }
        if (readLocked() != document) throw IOException("Benchmark report was not persisted")
    }
    private companion object {
        // Serialize freshness and replacement across recreated Activities, including late cancelled calls.
        val lock = Any()
    }
}
