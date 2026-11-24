package dev.blamera.chatgpt.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

class ChatGptTrialActivity : ComponentActivity() {
    private lateinit var model: ChatGptTrialViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = retainChatGptTrialModel(this)
        setContent {
            MaterialTheme {
                ChatGptTrialScreen(
                    model,
                    ::openBrowser,
                    {
                        try {
                            openBrowser(CHATGPT_USAGE_URL)
                        } catch (_: ActivityNotFoundException) {
                            model.browserUnavailable()
                        } catch (_: SecurityException) {
                            model.browserUnavailable()
                        }
                    },
                    ::finish
                )
            }
        }
    }

    private fun openBrowser(url: String) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        )
    }
}
