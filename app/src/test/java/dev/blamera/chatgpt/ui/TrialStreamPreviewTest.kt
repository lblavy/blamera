package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.MAX_TRIAL_REPLY_CHARS
import dev.blamera.chatgpt.TrialFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialStreamPreviewTest {
    @Test fun firstTextIsImmediateThenUpdatesAreCoalescedWithoutLosingTheLatestPartialText() {
        var now = 0L
        val preview = TrialStreamPreview { now }
        assertFalse(preview.update(""))
        assertTrue(preview.update("Premier"))
        assertFalse(preview.update("Premier"))
        assertFalse(preview.update("Premier fragment"))
        assertEquals("Premier fragment", preview.latest)
        now = 50_000_000L
        assertTrue(preview.update("Premier fragment reçu"))
        assertTrue(preview.update("Premier fragment reçu" + "x".repeat(64)))
    }

    @Test fun oversizedTextFailsInsteadOfSilentlyTruncatingTheModelAnswer() {
        val preview = TrialStreamPreview { 0L }
        val failure = assertThrows(ChatGptTrialException::class.java) {
            preview.update("x".repeat(MAX_TRIAL_REPLY_CHARS + 1))
        }
        assertEquals(TrialFailure.INVALID_RESPONSE, failure.kind)
    }
}
