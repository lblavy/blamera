package dev.blamera

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLocalTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainActivityOwnsTheRetainedChatGptSessionAndGatesTheBrowserReturn() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val before = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
        assertTrue(
            "Principal Activity must use protected session retention",
            before.sessionRetention
        )
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        var released = false
        scope.launch {
            before.appForeground.await()
            released = true
        }
        try {
            assertEquals(false, released)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertEquals(true, released)
            compose.activityRule.scenario.recreate()
            val after = ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
            org.junit.Assert.assertSame(before, after)
            org.junit.Assert.assertSame(before.objects, after.objects)
            assertEquals(null, after.state.account)
            assertEquals(
                null,
                org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun comparisonIsAnExplicitDebugAction() {
        compose.onNodeWithText("Comparer les modèles").assertDoesNotExist()
        compose.onNodeWithContentDescription("Diagnostics").performClick()
        compose.onNodeWithText("Comparer les modèles").assertIsDisplayed().performClick()
        val intent = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals("dev.blamera.benchmark.BenchmarkActivity", intent.component?.className)
    }

    @Test
    fun chatGptTrialIsAnExplicitPrivateActivityWithNoAutomaticOpening() {
        val activity = compose.activity
        assertEquals(null, org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        compose.onNodeWithText("Essai ChatGPT").assertDoesNotExist()
        compose.onNodeWithContentDescription("Diagnostics").performClick()
        compose.onNodeWithText("Essai ChatGPT").assertIsDisplayed().performClick()
        val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals("dev.blamera.chatgpt.ui.ChatGptTrialActivity", intent.component?.className)
        val info = activity.packageManager.getActivityInfo(checkNotNull(intent.component), 0)
        assertEquals(false, info.exported)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, info.screenOrientation)
    }

    @Test
    fun activityRecreatesTheCameraEntryWithoutALegacySourceSelector() {
        compose.onNodeWithText("Blamera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
        compose.onNodeWithText("Aucune source sélectionnée").assertDoesNotExist()
    }

    @Test
    fun activityAllowsFourUserOrientationsWithoutOverridingTheirRotationLock() {
        val context = compose.activity
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, info.screenOrientation)
        assertEquals(
            0,
            info.configChanges and
                (ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE)
        )
    }

    @Test
    fun mergedPermissionsAllowCameraExplicitChatGptNetworkAndTheInternalReceiver() {
        val context = compose.activity
        val permissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        ).requestedPermissions.orEmpty().toSet()
        val forbidden =
            setOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE"
            )
        assertTrue(
            "Unexpected permissions: ${permissions.intersect(forbidden)}",
            permissions.intersect(forbidden).isEmpty()
        )
        assertEquals(
            setOf(
                "android.permission.CAMERA",
                "android.permission.INTERNET",
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            ),
            permissions
        )
    }
}
