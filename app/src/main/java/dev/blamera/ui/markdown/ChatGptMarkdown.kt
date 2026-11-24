package dev.blamera.ui.markdown

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
internal fun ChatGptMarkdown(text: String, modifier: Modifier = Modifier) {
    Text(remember(text) { markdownText(text) }, modifier)
}
