package dev.blamera.chatgpt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.blamera.chatgpt.SessionFixture
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialHttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h640dp")
class ChatGptSessionControlsTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = SessionFixture()
    private val foreground = TrialAppForeground { 0L }.apply { update(true) }
    private val model = ChatGptTrialViewModel(
        fixture.gateway(foreground::await, foreground::awaitRefresh),
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        appForeground = foreground
    )
    private val opened = mutableListOf<String>()

    @After fun close() = model.close()

    @Test fun noSavedConnectionOffersOnlyExplicitLogin() {
        render()
        compose.runOnIdle { model.restoreOnce() }
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retrouver ma connexion").assertDoesNotExist()
        compose.onNodeWithText("Oublier la connexion enregistrée").assertDoesNotExist()
        assertTrue(fixture.requests.isEmpty())
        assertTrue(opened.isEmpty())
    }

    @Test fun offlineRetryRecoversTheStoredAccountWithoutBrowserOrCreditConfirmation() {
        seed()
        render()
        compose.runOnIdle { model.restoreOnce() }
        compose.onNodeWithText(TrialFailure.SERVICE_UNAVAILABLE.message)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retrouver ma connexion").performScrollTo().assertIsDisplayed()
        fixture.alter = { _, response -> response }
        compose.onNodeWithText("Retrouver ma connexion").performClick()
        assertEquals("Test account", model.state.account?.displayName)

        assertTrue(model.state.canAnalyze)
        assertTrue(opened.isEmpty())
        assertEquals(1, fixture.browserCount)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
    }

    @Test fun forgettingAnOfflineSessionRemovesTheRetryAndPreventsRestartRestoration() {
        seed()
        render()
        compose.runOnIdle { model.restoreOnce() }
        compose.onNodeWithContentDescription("Compte ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Oublier la connexion enregistrée").performClick()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retrouver ma connexion").assertDoesNotExist()
        assertNull(fixture.store.value)
        assertTrue(fixture.registration.sessionBlocked)
        assertTrue(opened.isEmpty())
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
    }

    private fun seed() = runBlocking {
        val previous = fixture.gateway()
        fixture.connect(previous)
        previous.close()
        fixture.alter = { request, response ->
            if (request.url.endsWith("models")) TrialHttpResponse(503, "{}") else response
        }
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ChatGptAccountControls(model, opened::add, { })
                }
            }
        }
    }
}
