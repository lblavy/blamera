package dev.blamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.blamera.chatgpt.ui.retainChatGptTrialModel
import dev.blamera.ui.BlameraApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val chatGpt = retainChatGptTrialModel(this, persistent = true)
        setContent {
            BlameraApp(chatGpt = chatGpt)
        }
    }
}
