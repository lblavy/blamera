package dev.blamera.benchmark

import java.security.MessageDigest
import java.util.Base64

internal fun benchmarkSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun benchmarkReportLines(document: String, transferId: String): Sequence<String> {
    val report = checkedReportDocument(document)
    require(transferId.matches(Regex("[A-Za-z0-9-]{1,80}")))
    val bytes = document.toByteArray(Charsets.UTF_8)
    val parts = (bytes.size + REPORT_CHUNK_BYTES - 1) / REPORT_CHUNK_BYTES
    val identity = "transfer=$transferId run=${report.getString("runId")}"
    val checksum = benchmarkSha256(bytes)
    return sequence {
        yield("report begin $identity parts=$parts bytes=${bytes.size} sha256=$checksum schema=2")
        for (index in 0 until parts) {
            val offset = index * REPORT_CHUNK_BYTES
            val data = Base64.getEncoder().encodeToString(
                bytes.copyOfRange(offset, minOf(offset + REPORT_CHUNK_BYTES, bytes.size))
            )
            yield("report chunk $identity part=${index + 1}/$parts data=$data")
        }
        yield("report end $identity parts=$parts sha256=$checksum")
    }
}
private const val REPORT_CHUNK_BYTES = 1024
