package dev.blamera.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.blamera.MainActivity
import dev.blamera.botany.PlantHttpResponse
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JointPlantRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = JointPlantFixture()

    @After fun close() = fixture.close()

    @Test fun activityRecreationKeepsBotanyAndItsApplicationDependencyButRealBackgroundCancels() {
        val first = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { first.await() }
        var factoryCalls = 0
        val application = compose.activity.application
        val holder = CaptureHolder(application) { context, _ ->
            assertSame(application.applicationContext, context)
            factoryCalls++
            fixture.plants
        }
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
        val local = VisionAnalyzer { AnalysisResult.Success(emptyList()) }
        compose.runOnIdle { holder.session.accept(capturePhoto(), local) }
        assertEquals(1, fixture.requests)
        compose.onNodeWithContentDescription("Configuration").performScrollTo().performClick()
        compose.onNodeWithTag("plantnet-key").performScrollTo()
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        compose.onNodeWithTag("plantnet-key").performTextInput("UNSAVED-FAKE")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Configuration").performScrollTo().performClick()
        val keyEditor = compose.onNodeWithTag("plantnet-key").performScrollTo()
        keyEditor.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals(
            "",
            keyEditor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        compose.onNodeWithContentDescription("Afficher la clé").assertExists()
        compose.onNodeWithText("Fermer").performClick()
        compose.runOnIdle {
            assertSame(holder, ViewModelProvider(compose.activity)[CaptureHolder::class.java])
            assertSame(fixture.plants, holder.session.plants)
            assertTrue(fixture.plants.snapshot!!.running)
            first.complete(plantResponse())
        }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            fixture.plants.snapshot?.result !=
                null
        }
        val second = CompletableDeferred<PlantHttpResponse>()
        compose.runOnIdle {
            fixture.response = { second.await() }
            holder.session.retry(local)
        }
        assertTrue(fixture.plants.snapshot!!.running)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(fixture.plants.snapshot!!.running)
        second.complete(plantResponse())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertNull(fixture.plants.snapshot!!.result)
        assertEquals(2, fixture.requests)
        assertEquals(1, factoryCalls)
    }
}
