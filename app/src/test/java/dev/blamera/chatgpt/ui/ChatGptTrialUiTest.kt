package dev.blamera.chatgpt.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.TrialFailure
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatGptTrialUiTest {
    @get:Rule val compose = createComposeRule()
    private val gateway = FakeTrialGateway()
    private val model = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )
    private val opened = mutableListOf<String>()
    private var usage = 0
    private var backs = 0
    private var browserFailure: Exception? = null
    private lateinit var view: View

    @After fun close() = model.close()

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h360dp")
    fun browserWaitPhaseAndCancelStayVisibleWithoutScrolling() {
        gateway.connectionGate = CompletableDeferred()
        render()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText(dev.blamera.chatgpt.TrialConnectionPhase.WAITING_BROWSER.status)
            .assertIsDisplayed()
        compose.onNodeWithText("Annuler").assertIsDisplayed().performClick()
        gateway.connectionGate!!.complete(gateway.account)
        compose.onNodeWithText("Essai annulé.").performScrollTo().assertIsDisplayed()
        assertEquals(0, gateway.sent.size)
    }

    @Test fun imageIsVisibleWithoutInstructionAndOnlyExplicitSendUploadsIt() {
        render()
        compose.onNodeWithContentDescription("Image de test synthétique").assertIsDisplayed()
        screenshot("portrait")
        compose.onNodeWithText(TRIAL_INSTRUCTION).assertDoesNotExist()
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
        assertEquals("Compte de test", model.state.account?.displayName)
        assertEquals(listOf("https://auth.openai.com/fixture"), opened)
        assertEquals(0, gateway.sent.size)
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Compte ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Limites ChatGPT").performClick()
        assertEquals(1, usage)
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().assertIsEnabled()
            .performClick()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        assertEquals(1, gateway.sent.size)
        assertEquals(TRIAL_INSTRUCTION, gateway.sent.single().instruction)
        compose.onNodeWithText("Retour à Blamera").performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test fun modelChoiceAndQuotaFailureStayAccessibleWithoutAutomaticRetry() {
        gateway.analysisFailure = ChatGptTrialException(TrialFailure.USAGE_LIMIT)
        render()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
        compose.onNodeWithTag("model-selector").performScrollTo().performClick()
        compose.onNodeWithText("Autre modèle").performClick()
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().performClick()
        compose.onNodeWithText(
            TrialFailure.USAGE_LIMIT.message
        ).performScrollTo().assertIsDisplayed()
        assertEquals("other-fixture", gateway.sent.single().model)
        assertEquals(1, gateway.sent.size)
        compose.onNodeWithContentDescription("Compte ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Se déconnecter").performClick()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().assertIsNotEnabled()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun landscapeLetsUserSendAndReadTheResponse() {
        render()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().performClick()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retour à Blamera").performScrollTo().assertIsDisplayed()
        screenshot("landscape-response")
    }

    @Test fun waitingLoginCanBeCancelledAndNeverSubmitsAnImage() {
        gateway.connectionGate = CompletableDeferred()
        render()
        compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText(dev.blamera.chatgpt.TrialConnectionPhase.WAITING_BROWSER.status)
            .assertIsDisplayed()
        compose.onNodeWithText("Envoyer l’image de test").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Annuler").assertIsDisplayed().performClick()
        gateway.connectionGate!!.complete(gateway.account)
        compose.onNodeWithText("Essai annulé.").performScrollTo().assertIsDisplayed()
        assertEquals(0, gateway.sent.size)
    }

    @Test fun refusedOrUnavailableBrowserLeavesARecoverableSafeError() {
        render()
        listOf(
            android.content.ActivityNotFoundException("private-url"),
            SecurityException("secret-token")
        ).forEach { failure ->
            browserFailure = failure
            compose.onNodeWithText("Continuer avec ChatGPT").performScrollTo().performClick()
            compose.onNodeWithText("L’essai a échoué. Vous pouvez réessayer.")
                .performScrollTo().assertIsDisplayed()
            assertEquals(0, gateway.sent.size)
        }
    }

    private fun render() {
        compose.setContent {
            view = LocalView.current
            MaterialTheme {
                ChatGptTrialScreen(model, { url ->
                    browserFailure?.let { throw it }
                    opened.add(url)
                }, { usage++ }, { backs++ })
            }
        }
    }

    private fun screenshot(name: String) {
        val current = File(checkNotNull(System.getProperty("user.dir")))
        val root = if (File(
                current,
                "AGENTS.md"
            ).isFile
        ) {
            current
        } else {
            checkNotNull(current.parentFile)
        }
        val output = File(root, "build/reports/validation/m2-chatgpt-plus/ui/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
