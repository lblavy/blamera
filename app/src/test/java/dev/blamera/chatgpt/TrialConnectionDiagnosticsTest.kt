package dev.blamera.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialConnectionDiagnosticsTest {
    @Test fun diagnosticLogContainsOnlyWhitelistedPhaseAndFailureEnums() {
        TrialConnectionPhase.entries.forEach { writeTrialDiagnostic(TrialDiagnostic(it)) }
        TrialFailure.entries.forEach { writeTrialDiagnostic(TrialDiagnostic(null, it)) }
        writeTrialDiagnostic(TrialDiagnostic(null, failed = true))
        val logs = ShadowLog.getLogsForTag(CHATGPT_DIAGNOSTIC_TAG)
        assertEquals(TrialConnectionPhase.entries.size + TrialFailure.entries.size + 1, logs.size)
        assertTrue(logs.all { it.msg.matches(Regex("(PHASE|FAILURE)_[A-Z_]+")) })
        assertTrue(logs.all { it.throwable == null })
    }
}
