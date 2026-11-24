package dev.blamera.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle

/** Tracks spacing alongside spans without repeatedly materializing the whole styled response. */
internal class MarkdownOutput {
    private val output = AnnotatedString.Builder()
    private var trailingNewlines = 0

    fun pushStyle(style: SpanStyle) = output.pushStyle(style)
    fun pushLink(link: LinkAnnotation.Url) = output.pushLink(link)
    fun pop() = output.pop()

    fun append(value: String) {
        if (value.isEmpty()) return
        val newlines = value.length - value.trimEnd('\n').length
        trailingNewlines = if (newlines == value.length) trailingNewlines + newlines else newlines
        output.append(value)
    }

    fun separate(newlines: Int) {
        if (output.length == 0) return
        append("\n".repeat((newlines - trailingNewlines).coerceAtLeast(0)))
    }

    fun result(): AnnotatedString {
        val value = output.toAnnotatedString()
        return value.subSequence(0, value.text.trimEnd('\n').length)
    }
}
