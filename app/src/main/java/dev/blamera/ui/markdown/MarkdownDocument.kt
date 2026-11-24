package dev.blamera.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import org.commonmark.node.Node
import org.commonmark.parser.Parser

internal const val MARKDOWN_MAX_CHARS = 65_536
internal const val MARKDOWN_MAX_DEPTH = 32
internal const val MARKDOWN_MAX_NODES = 2048
private const val TRUNCATION_NOTICE = "\n[Réponse tronquée]"

internal fun markdownText(text: String): AnnotatedString {
    if (text.length > MARKDOWN_MAX_CHARS) {
        return AnnotatedString(text.take(MARKDOWN_MAX_CHARS) + TRUNCATION_NOTICE)
    }
    val document = try {
        Parser.builder().maxOpenBlockParsers(MARKDOWN_MAX_DEPTH).build().parse(text)
    } catch (_: IllegalStateException) {
        // Preserve the bounded plain response if the parser cannot construct its tree.
        null
    }
    return document?.let(::renderMarkdown) ?: AnnotatedString(text)
}

private data class MarkdownFrame(val node: Node, val depth: Int, val leaving: Boolean = false)

private fun renderMarkdown(document: Node): AnnotatedString? {
    val pending = ArrayDeque<MarkdownFrame>()
    pending.addLast(MarkdownFrame(document, 0))
    val writer = MarkdownWriter()
    var nodes = 0
    while (pending.isNotEmpty()) {
        val frame = pending.removeLast()
        if (frame.leaving) {
            writer.leave(frame.node)
            continue
        }
        nodes++
        if (nodes > MARKDOWN_MAX_NODES || frame.depth > MARKDOWN_MAX_DEPTH) return null
        writer.enter(frame.node)
        pending.addLast(frame.copy(leaving = true))
        var child = frame.node.lastChild
        while (child != null) {
            pending.addLast(MarkdownFrame(child, frame.depth + 1))
            child = child.previous
        }
    }
    return writer.result()
}
