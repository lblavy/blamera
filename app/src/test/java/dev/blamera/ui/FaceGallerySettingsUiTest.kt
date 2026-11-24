package dev.blamera.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.blamera.faces.FaceGallerySettings
import dev.blamera.faces.FaceMemoryStore
import dev.blamera.faces.faceDecoder
import dev.blamera.faces.faceFixture
import dev.blamera.information.AndroidSearchImagePreparer
import dev.blamera.information.FakeSharedImage
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.SearchImagePublisher
import dev.blamera.information.fakeImageShareSender
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceGallerySettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun close() = scope.cancel()

    @Test fun emptyGalleryOffersImportOnlyOnExplicitClick() {
        val settings = FaceGallerySettings(scope, FaceMemoryStore(), faceDecoder())
        var choices = 0
        compose.setContent { MaterialTheme { FaceGallerySettingsContent(settings) { choices++ } } }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !settings.busy
        }
        compose.onNodeWithText("Galerie faciale").assertIsDisplayed()
        compose.onNodeWithText("Aucune personne importée.").assertIsDisplayed()
        assertEquals(0, choices)
        compose.onNodeWithText("Importer une galerie").assertIsEnabled().performClick()
        assertEquals(1, choices)
    }

    @Test fun savedGalleryDisplaysPeopleAndReplacementAction() {
        val settings = FaceGallerySettings(scope, FaceMemoryStore(faceFixture()), faceDecoder())
        compose.setContent { MaterialTheme { FaceGallerySettingsContent(settings) {} } }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !settings.busy
        }
        compose.onNodeWithText("2 personnes importées").assertIsDisplayed()
        compose.onNodeWithText(
            "Personne synthétique A · Personne synthétique B"
        ).assertIsDisplayed()
        compose.onNodeWithText("Remplacer la galerie").assertIsEnabled()
    }

    @Test fun pickerResultStillImportsAfterConfigurationDialogCloses() {
        val settings = FaceGallerySettings(scope, FaceMemoryStore(), faceDecoder())
        val session = CaptureSession(scope, faces = settings)
        lateinit var activity: ComponentActivity
        val tools = ObjectInformationTools(
            AndroidSearchImagePreparer(),
            SearchImagePublisher { FakeSharedImage() },
            fakeImageShareSender { }
        )
        compose.runOnUiThread {
            session.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Success(emptyList()) })
        }
        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            MaterialTheme { ObjectInformationCard(session, tools) }
        }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !settings.busy
        }
        compose.onNodeWithContentDescription("Configuration").performClick()
        compose.onNodeWithText("Importer une galerie").performClick()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        compose.onNodeWithText("Fermer").performClick()
        val file = File(activity.cacheDir, "face-gallery-fixture.json").apply {
            writeText(faceFixture())
        }
        compose.runOnIdle {
            activity.activityResultRegistry.dispatchResult(
                started.requestCode,
                Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(file))
            )
        }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            settings.gallery != null
        }
        assertEquals(2, settings.gallery?.identities?.size)
        session.close()
    }

    @Test fun singlePersonFailureAndBusyImportAreVisibleWithoutChangingNames() {
        val json = org.json.JSONObject(faceFixture())
        json.getJSONArray("identities").remove(1)
        val settings = FaceGallerySettings(scope, FaceMemoryStore(json.toString()), faceDecoder())
        compose.setContent { MaterialTheme { FaceGallerySettingsContent(settings) {} } }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !settings.busy
        }
        compose.onNodeWithText("1 personne importée").assertIsDisplayed()
        compose.runOnIdle { settings.import { byteArrayOf(1) } }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            !settings.busy
        }
        compose.onNodeWithText("Galerie invalide ou incompatible.").assertIsDisplayed()
        val pending = kotlinx.coroutines.CompletableDeferred<ByteArray>()
        compose.runOnIdle { settings.import { pending.await() } }
        compose.onNodeWithText("Remplacer la galerie").assertIsNotEnabled()
        compose.onNodeWithText("Chargement de la galerie…").assertIsDisplayed()
        compose.onNodeWithText("1 personne importée").assertIsDisplayed()
        settings.cancel()
    }
}
