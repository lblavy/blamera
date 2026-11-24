package dev.blamera.benchmark

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BenchmarkActivityTest {
    @get:Rule val compose = createAndroidComposeRule<BenchmarkActivity>()

    @Test fun internalActivityStartsIdleAndRecreationNeverStartsMeasurements() {
        val activity = compose.activity
        val info = activity.packageManager.getActivityInfo(
            ComponentName(activity, BenchmarkActivity::class.java),
            0
        )
        assertFalse(info.exported)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, info.screenOrientation)
        compose.onNodeWithText("Prêt. Aucun calcul démarré.").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Prêt. Aucun calcul démarré.").assertExists()
    }
}
