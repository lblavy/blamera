package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.MAX_TRIAL_REPLY_CHARS
import dev.blamera.chatgpt.TrialFailure

private const val PREVIEW_INTERVAL_NANOS = 50_000_000L
private const val PREVIEW_GROWTH_CHARS = 64

internal class TrialStreamPreview(private val clock: () -> Long = System::nanoTime) {
    var latest: String? = null
        private set
    private var published: String? = null
    private var publishedAt = 0L
    fun update(text: String): Boolean {
        if (text.length > MAX_TRIAL_REPLY_CHARS) {
            throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
        }
        if (text.isEmpty()) return false
        latest = text
        val previous = published
        val now = clock()
        val ready = previous == null || text.length - previous.length >= PREVIEW_GROWTH_CHARS ||
            now - publishedAt >= PREVIEW_INTERVAL_NANOS
        val publish = previous != text && ready
        if (publish) {
            published = text
            publishedAt = now
        }
        return publish
    }
}
