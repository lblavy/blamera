package dev.blamera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchedActivityOffersTheUnifiedCameraAndGalleryEntry() {
        compose.onNodeWithText("Blamera").assertIsDisplayed()
        compose.onNodeWithText("Capturer").assertIsDisplayed()
        compose.onNodeWithText("Galerie").assertIsDisplayed()
        compose.onNodeWithText("Aucune source sélectionnée").assertDoesNotExist()
    }
}
