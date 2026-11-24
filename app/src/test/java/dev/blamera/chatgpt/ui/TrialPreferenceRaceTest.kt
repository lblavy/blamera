package dev.blamera.chatgpt.ui

import dev.blamera.chatgpt.ChatGptTrialGateway
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialPreferenceRaceTest {
    @Test fun changingReasoningDuringASuspendedModelSavePreservesBothChoicesAfterReopening() {
        val network = StreamingTrialGateway()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var savedModel: String? = "gpt-6.1-sol"
        var savedEffort: String? = null
        var firstWrite = true
        val gateway = object : ChatGptTrialGateway by network {
            override val preferredModel: String? get() = savedModel
            override val preferredReasoningEffort: String? get() = savedEffort
            override suspend fun models() = listOf(
                TrialModel("gpt-6.1-sol", "Sol"),
                TrialModel("gpt-6.1-terra", "Terra")
            )
            override suspend fun rememberModel(slug: String) {
                if (firstWrite) {
                    firstWrite = false
                    entered.complete(Unit)
                    release.await()
                }
                savedModel = slug
            }
            override suspend fun rememberReasoningEffort(effort: String?) {
                savedEffort = effort
            }
        }
        val first = model(gateway)
        var reopened: ChatGptTrialViewModel? = null
        try {
            first.connect {}
            first.selectModel("gpt-6.1-terra")
            assertTrue("The first model write must actually be suspended", entered.isCompleted)
            first.selectReasoningEffort("high")
            release.complete(Unit)
            reopened = model(gateway)
            reopened.connect {}
            assertEquals("gpt-6.1-terra", reopened.state.selectedModel)
            assertEquals("high", reopened.state.selectedReasoningEffort)
            assertEquals(0, network.sends)
        } finally {
            release.complete(Unit)
            reopened?.close()
            first.close()
        }
    }

    private fun model(gateway: ChatGptTrialGateway) = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        appForeground = TrialAppForeground { 0L }
    )
}
