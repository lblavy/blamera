package dev.blamera.ui

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.camera.PreviewProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val application: Application = RuntimeEnvironment.getApplication()
    private val registry = PermissionRegistry()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState =
                Lifecycle.State.RESUMED
        }
    }
    private val visible = mutableStateOf(true)
    private var binds = 0
    private var closes = 0
    private val provider =
        PreviewProvider { _, _, report ->
            binds++
            report(PreviewStatus.STREAMING)
            AutoCloseable { closes++ }
        }

    @Test
    fun previewRequiresExplicitActivationAndRefusalDoesNotOpenIt() {
        show(PreviewProviderLoader { provider })
        assertEquals(0, registry.requests)
        assertEquals(0, binds)
        compose.onNodeWithText(
            "Analyse locale sur demande. Les catégories peuvent être inexactes."
        ).assertIsDisplayed()
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText(
            "Autorisation caméra bloquée. Utilisez Image ou réactivez-la manuellement dans les réglages Android."
        ).assertIsDisplayed()
        assertEquals(1, registry.requests)
        assertEquals(0, binds)
    }

    @Test
    fun analysisStartsOnlyOnClickAndStoppingKeepsPreviewBound() {
        registry.reply = true
        show(PreviewProviderLoader { provider })
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText("Analyser en direct").performClick()
        compose.onNodeWithText("Arrêter l’analyse").performClick()
        compose.onNodeWithText("Analyser en direct").assertIsDisplayed()
        assertEquals(1, binds)
        assertEquals(0, closes)
        compose.onNodeWithText("Analyser en direct").performClick()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Analyser en direct").assertIsDisplayed()
    }

    @Test
    fun previewFailureStopsAnalysisAndRetryStartsWithAnalysisOff() {
        registry.reply = true
        var report: (PreviewStatus) -> Unit = {}
        show(
            PreviewProviderLoader {
                PreviewProvider { _, _, callback ->
                    report = callback
                    callback(PreviewStatus.STREAMING)
                    AutoCloseable {}
                }
            }
        )
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText("Analyser en direct").performClick()
        compose.runOnIdle { report(PreviewStatus.FAILED) }
        compose.onNodeWithText("Analyser en direct").assertIsDisplayed()
        compose.onNodeWithText("Réessayer la caméra").performClick()
        compose.onNodeWithText("Analyser en direct").assertIsDisplayed()
    }

    @Test
    fun stopRemainsAvailableWhilePreviewWaitsAndClearsAnalysisWithoutRebinding() {
        registry.reply = true
        var report: (PreviewStatus) -> Unit = {}
        show(
            PreviewProviderLoader {
                PreviewProvider { _, _, callback ->
                    binds++
                    report = callback
                    callback(PreviewStatus.STREAMING)
                    AutoCloseable { closes++ }
                }
            }
        )
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText("Analyser en direct").performClick()
        compose.runOnIdle { report(PreviewStatus.CONNECTING) }
        compose.onNodeWithText("Arrêter l’analyse").assertIsEnabled().performClick()
        compose.onNodeWithText("Analyser en direct").assertIsNotEnabled()
        compose.onNodeWithText(
            "Analyse locale en direct… Gardez la scène immobile pour juger les contours."
        ).assertDoesNotExist()
        assertEquals(1, binds)
        assertEquals(0, closes)
    }

    @Test
    fun manualPermissionGrantStartsPreviewAndLeavingReleasesIt() {
        registry.reply = true
        show(PreviewProviderLoader { provider })
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText("Aperçu caméra actif.").assertIsDisplayed()
        assertEquals(1, binds)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun alreadyGrantedPermissionStillNeedsActivationAndRotationRestoresIt() {
        grant()
        val restore = StateRestorationTester(compose)
        restore.setContent { Content(PreviewProviderLoader { provider }) }
        assertEquals(0, binds)
        compose.onNodeWithText("Activer la caméra").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Aperçu caméra actif.").assertIsDisplayed()
        assertEquals(2, binds)
        assertEquals(1, closes)
        assertEquals(0, registry.requests)
    }

    @Test
    fun backgroundPauseReleasesPreviewAndResumeRechecksRevocation() {
        grant()
        show(PreviewProviderLoader { provider })
        compose.onNodeWithText("Activer la caméra").performClick()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("Caméra en pause.").assertIsDisplayed()
        assertEquals(1, closes)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Aperçu caméra actif.").assertIsDisplayed()
        assertEquals(2, binds)
        compose.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            shadowOf(application).denyPermissions(Manifest.permission.CAMERA)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText(
            "Autorisation caméra retirée. L’image importée reste disponible."
        ).assertIsDisplayed()
        compose.onNodeWithText("Autoriser la caméra").assertIsDisplayed()
        assertEquals(2, binds)
        assertEquals(2, closes)
    }

    @Test
    fun bindingFailureOffersRetryAndThenStreams() {
        grant()
        var attempts = 0
        show(
            PreviewProviderLoader {
                if (attempts++ == 0) throw IllegalStateException("occupied")
                provider
            }
        )
        compose.onNodeWithText("Activer la caméra").performClick()
        compose.onNodeWithText(
            "Caméra indisponible ou occupée. Réessayez ou utilisez Image."
        ).assertIsDisplayed()
        compose.onNodeWithText("Réessayer la caméra").performClick()
        compose.onNodeWithText("Aperçu caméra actif.").assertIsDisplayed()
        assertEquals(2, attempts)
    }

    @Test
    fun lateProviderAfterSourceIsHiddenCannotOpenCamera() {
        grant()
        val pending = CompletableDeferred<PreviewProvider>()
        show(PreviewProviderLoader { withContext(NonCancellable) { pending.await() } })
        try {
            compose.onNodeWithText("Activer la caméra").performClick()
            compose.onNodeWithText("Ouverture de la caméra…").assertIsDisplayed()
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            pending.complete(provider)
            compose.waitForIdle()
            assertEquals(0, binds)
        } finally {
            pending.complete(provider)
        }
    }

    @Test
    fun permissionResultFromBeforeRecreationCannotActivateNewPreview() {
        registry.reply = null
        val restore = StateRestorationTester(compose)
        restore.setContent { Content(PreviewProviderLoader { provider }) }
        compose.onNodeWithText("Autoriser la caméra").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.complete(true) }
        compose.waitForIdle()
        assertEquals(0, binds)
        compose.onNodeWithText("Autoriser la caméra").performClick()
        compose.onNodeWithText("Aperçu caméra actif.").assertIsDisplayed()
        assertEquals(1, binds)
    }

    private fun show(loader: PreviewProviderLoader) {
        compose.setContent { Content(loader) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(loader: PreviewProviderLoader) {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides registryOwner,
            LocalLifecycleOwner provides owner
        ) {
            MaterialTheme { if (visible.value) CameraPane(loader) }
        }
    }
    private fun grant() {
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
    }

    private inner class PermissionRegistry : ActivityResultRegistry() {
        var requests = 0
        var reply: Boolean? = false
        private var code = 0
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            assertEquals(Manifest.permission.CAMERA, input)
            requests++
            code = requestCode
            reply?.let(::complete)
        }
        fun complete(allowed: Boolean) {
            if (allowed) {
                grant()
            } else {
                shadowOf(
                    application
                ).denyPermissions(Manifest.permission.CAMERA)
            }
            dispatchResult(code, allowed)
        }
    }
}
