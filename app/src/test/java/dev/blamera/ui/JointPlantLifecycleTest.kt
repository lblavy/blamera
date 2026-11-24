package dev.blamera.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.blamera.MainActivity
import dev.blamera.botany.PlantAnalysis
import dev.blamera.botany.PlantHttp
import dev.blamera.botany.PlantIdentifier
import dev.blamera.botany.PlantSettings
import dev.blamera.chatgpt.storage.TrialSessionStore
import dev.blamera.image.PhotoLoadResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JointPlantLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = JointPlantFixture()
    private var additional: PlantAnalysis? = null
    private val local = VisionAnalyzer { AnalysisResult.Success(emptyList()) }

    @After fun close() {
        additional?.close()
        fixture.close()
    }

    @Test fun stoppingTheCameraWhileCaptureIsLoadingPreventsAnyLatePhotoOrBotanicalStart() {
        val holder = install(fixture.plants)
        val pending = CompletableDeferred<PhotoLoadResult>()
        compose.runOnIdle {
            holder.session.capture({ withContext(NonCancellable) { pending.await() } }, local)
        }
        assertEquals(CaptureScreen.CAMERA, holder.session.screen)
        assertTrue(holder.session.loading)
        try {
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            pending.complete(captureLoaded())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, fixture.requests)
            assertNull(holder.session.photo)
            assertFalse(holder.session.loading)
        } finally {
            pending.complete(captureLoaded())
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test fun stoppingWhileProtectedKeyIsLoadingPreventsARequestAfterTheKeyArrives() {
        val key = CompletableDeferred<String?>()
        var requests = 0
        val settings = PlantSettings(
            fixture.scope,
            object : TrialSessionStore {
                override suspend fun load() = key.await()
                override suspend fun save(value: String) = error("No key save")
                override suspend fun clear() = Unit
            }
        )
        val plants = PlantAnalysis(
            fixture.scope,
            settings,
            PlantIdentifier(
                PlantHttp { _, _ ->
                    requests++
                    plantResponse()
                },
                quotaDispatcher = Dispatchers.Unconfined
            )
        ) { byteArrayOf(1) }
        additional = plants
        val holder = install(plants)
        compose.runOnIdle { holder.session.accept(capturePhoto(), local) }
        assertEquals(0, requests)
        assertTrue(settings.loading)
        try {
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            key.complete("LOADED-FAKE")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, requests)
            assertFalse(plants.snapshot?.running == true)
            assertNull(plants.snapshot?.result)
        } finally {
            key.complete("LOADED-FAKE")
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test fun aNewPickerResultDeliveredOnRestartStillRunsBothAnalysesNormally() {
        val holder = install(fixture.plants)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.runOnUiThread { holder.session.capture({ captureLoaded() }, local) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(1, fixture.requests)
        assertTrue(holder.session.result is AnalysisResult.Success)
        assertFalse(holder.session.loading)
    }

    private fun install(plants: PlantAnalysis): CaptureHolder {
        val holder = CaptureHolder(compose.activity.application) { _, _ -> plants }
        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            ViewModelProvider(
                compose.activity,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        modelClass.cast(holder)!!
                }
            )[CaptureHolder::class.java]
        }
        compose.activityRule.scenario.recreate()
        return holder
    }
}
