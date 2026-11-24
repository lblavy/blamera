package dev.blamera.chatgpt.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatGptTrialActivityTest {
    @get:Rule val compose = createAndroidComposeRule<ChatGptTrialActivity>()

    @Test fun realFactoryForegroundGateTracksPauseAndIsRetainedAcrossRotation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val before = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        var released = false
        val waiter = scope.launch {
            before.appForeground.await()
            released = true
        }
        try {
            assertFalse("Paused factory gate must block", released)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertTrue(released)
            compose.activityRule.scenario.recreate()
            val after = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
            assertSame(before.appForeground, after.appForeground)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            var releasedAfterRotation = false
            scope.launch {
                before.appForeground.await()
                releasedAfterRotation = true
            }
            assertFalse("Retained gate must follow the new Activity pause", releasedAfterRotation)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertTrue(releasedAfterRotation)
            assertNull(shadowOf(compose.activity).nextStartedActivity)
            assertNull(after.state.account)
        } finally {
            waiter.cancel()
            scope.cancel()
        }
    }

    @Test fun ownActivityLifecycleIsReportedWithoutOpeningAnyExternalApp() {
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        val events = org.robolectric.shadows.ShadowLog.getLogsForTag(
            dev.blamera.chatgpt.CHATGPT_DIAGNOSTIC_TAG
        ).map { it.msg }.filter { it.startsWith("LIFECYCLE_") }
        assertEquals(listOf("LIFECYCLE_RESUMED", "LIFECYCLE_PAUSED", "LIFECYCLE_RESUMED"), events)
        assertNull(shadowOf(compose.activity).nextStartedActivity)
    }

    @Test fun rotationRetainsTheTrialWithoutOpeningABrowserOrSubmitting() {
        val before = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
        compose.onNodeWithText("Essai ChatGPT").assertIsDisplayed()
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        compose.activityRule.scenario.recreate()
        val after = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
        assertSame(before, after)
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals("Aucune image envoyée.", after.state.status)
        assertNull(after.state.account)
    }

    @Test fun fixtureIsAReadableSmallJpegWithoutAnyPersonalSource() {
        val jpeg = createTrialJpeg()
        assertEquals(0xff, jpeg[0].toInt() and 0xff)
        assertEquals(0xd8, jpeg[1].toInt() and 0xff)
        org.junit.Assert.assertTrue(jpeg.size < 100_000)
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNotNull(bitmap)
        assertEquals(640, bitmap.width)
        assertEquals(480, bitmap.height)
        bitmap.recycle()
    }
}
