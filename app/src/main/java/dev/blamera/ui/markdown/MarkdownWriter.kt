package dev.blamera.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import java.net.URI
import java.net.URISyntaxException
import java.util.IdentityHashMap
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak

internal class MarkdownWriter {
    private val output = MarkdownOutput()
    private val listNumbers = IdentityHashMap<OrderedList, Int>()

    fun enter(node: Node) {
        if (!enterBlock(node) && !enterInline(node)) enterLiteral(node)
    }

    fun leave(node: Node) {
        when (node) {
            is Heading, is Emphasis, is StrongEmphasis, is BlockQuote, is Link -> output.pop()
        }
    }

    fun result() = output.result()

    private fun enterBlock(node: Node): Boolean {
        when (node) {
            is Document -> Unit
            is Paragraph -> output.separate(paragraphGap(node))
            is Heading -> {
                output.separate(2)
                output.pushStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize(node))
                )
            }
            is ListBlock -> output.separate(1)
            is ListItem -> beginItem(node)
            is BlockQuote -> {
                output.separate(2)
                output.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            }
            is ThematicBreak -> {
                output.separate(2)
                output.append("────────")
            }
            else -> return false
        }
        return true
    }

    private fun enterInline(node: Node): Boolean {
        when (node) {
            is Text -> output.append(node.literal)
            is StrongEmphasis -> output.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            is Emphasis -> output.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            is Link -> beginLink(node.destination)
            is Code -> code(node.literal)
            is SoftLineBreak -> output.append(" ")
            is HardLineBreak -> output.append("\n")
            else -> return false
        }
        return true
    }

    private fun enterLiteral(node: Node) {
        when (node) {
            is HtmlInline -> output.append(node.literal)
            is HtmlBlock -> {
                output.separate(2)
                output.append(node.literal.trimEnd('\n'))
            }
            is FencedCodeBlock -> {
                output.separate(2)
                code(node.literal.trimEnd('\n'))
            }
            is IndentedCodeBlock -> {
                output.separate(2)
                code(node.literal.trimEnd('\n'))
            }
        }
        // Image nodes are traversed as alt text; no remote URI is loaded.
    }

    private fun code(value: String) {
        output.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
        output.append(value)
        output.pop()
    }

    private fun beginItem(node: ListItem) {
        output.separate(1)
        val parent = node.parent
        val marker = if (parent is OrderedList) {
            val number = listNumbers[parent] ?: parent.markerStartNumber
            listNumbers[parent] = number + 1
            "$number. "
        } else {
            "• "
        }
        val depth = generateSequence(node.parent) { it.parent }.count { it is ListBlock }
        output.append("  ".repeat((depth - 1).coerceAtLeast(0)) + marker)
    }

    private fun beginLink(destination: String) {
        val target = safeMarkdownUrl(destination)
        if (target == null) {
            output.pushStyle(SpanStyle())
        } else {
            output.pushLink(
                LinkAnnotation.Url(
                    target,
                    TextLinkStyles(
                        SpanStyle(
                            color = Color(LINK_COLOR),
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            )
        }
    }

    private companion object {
        const val LINK_COLOR = 0xFF245C9FL
    }
}

private fun paragraphGap(node: Paragraph) =
    if (node.parent is ListItem && node.previous == null) 0 else 2

private fun headingSize(heading: Heading) = when (heading.level) {
    1 -> 24.sp
    2 -> 22.sp
    else -> 20.sp
}

internal fun safeMarkdownUrl(value: String): String? {
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase()
    return value.takeIf {
        (scheme == "https" || scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}
