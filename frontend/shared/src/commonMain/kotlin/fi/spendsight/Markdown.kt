package fi.spendsight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal sealed interface MdInline {
    data class Text(val value: String) : MdInline
    data class Bold(val value: String) : MdInline
    data class Italic(val value: String) : MdInline
    data class Code(val value: String) : MdInline
}

internal sealed interface MdBlock {
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Bullet(val inlines: List<MdInline>) : MdBlock
    data class Numbered(val number: Int, val inlines: List<MdInline>) : MdBlock
    data class Heading(val inlines: List<MdInline>) : MdBlock
}

private val link = Regex("""\[([^\]\n]+)\]\([^)\s]+\)""")
private val bullet = Regex("""^[-*•]\s+(.+)$""")
private val numbered = Regex("""^(\d{1,3})\.\s+(.+)$""")
private val heading = Regex("""^#{1,3}\s+(.+)$""")

/** Coach answers may use paragraphs, bold, italics, and lists. Links stay as their label. */
internal fun parseMarkdown(source: String): List<MdBlock> {
    val text = unwrapFence(source).replace("\r\n", "\n").trim()
    if (text.isEmpty()) return emptyList()
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<MdInline>()
    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks.add(MdBlock.Paragraph(paragraph.toList()))
        paragraph.clear()
    }
    for (raw in text.split('\n')) {
        if (raw.isBlank()) {
            flushParagraph()
            continue
        }
        val line = stripLinks(raw.trim())
        val asBullet = bullet.matchEntire(line)
        val asNumber = numbered.matchEntire(line)
        val asHeading = heading.matchEntire(line)
        when {
            asBullet != null -> {
                flushParagraph()
                blocks.add(MdBlock.Bullet(parseInlines(asBullet.groupValues[1])))
            }
            asNumber != null -> {
                flushParagraph()
                blocks.add(MdBlock.Numbered(asNumber.groupValues[1].toInt(), parseInlines(asNumber.groupValues[2])))
            }
            asHeading != null -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(parseInlines(asHeading.groupValues[1])))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.add(MdInline.Text(" "))
                paragraph.addAll(parseInlines(line))
            }
        }
    }
    flushParagraph()
    return blocks
}

internal fun parseInlines(input: String): List<MdInline> {
    val out = mutableListOf<MdInline>()
    val buf = StringBuilder()
    fun flush() {
        if (buf.isEmpty()) return
        out.add(MdInline.Text(buf.toString()))
        buf.clear()
    }
    var i = 0
    while (i < input.length) {
        if (input.startsWith("**", i)) {
            val end = input.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                out.add(MdInline.Bold(input.substring(i + 2, end)))
                i = end + 2
                continue
            }
        }
        if (input[i] == '`') {
            val end = input.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                out.add(MdInline.Code(input.substring(i + 1, end)))
                i = end + 1
                continue
            }
        }
        if (input[i] == '*' && (i + 1 >= input.length || input[i + 1] != '*')) {
            val end = input.indexOf('*', i + 1)
            if (end > i + 1 && (end + 1 >= input.length || input[end + 1] != '*')) {
                flush()
                out.add(MdInline.Italic(input.substring(i + 1, end)))
                i = end + 1
                continue
            }
        }
        buf.append(input[i])
        i++
    }
    flush()
    return out
}

private fun stripLinks(text: String): String = link.replace(text) { it.groupValues[1] }

private fun unwrapFence(source: String): String {
    val trimmed = source.trim()
    if (!trimmed.startsWith("```")) return source
    val firstBreak = trimmed.indexOf('\n')
    if (firstBreak < 0) return source
    val body = trimmed.substring(firstBreak + 1)
    return if (body.trimEnd().endsWith("```")) body.trimEnd().removeSuffix("```") else source
}

@Composable
fun MarkdownBody(text: String) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        parseMarkdown(text).forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> BodyText(inlines(block.inlines))
                is MdBlock.Heading -> Text(
                    inlines(block.inlines),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp,
                )
                is MdBlock.Bullet -> MarkerRow("•", block.inlines)
                is MdBlock.Numbered -> MarkerRow("${block.number}.", block.inlines)
            }
        }
    }
}

@Composable
private fun MarkerRow(marker: String, parts: List<MdInline>) {
    Row(Modifier.fillMaxWidth()) {
        Text(marker, color = Forest, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(22.dp))
        BodyText(inlines(parts), Modifier.weight(1f))
    }
}

@Composable
private fun BodyText(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Ink, fontSize = 17.sp, lineHeight = 24.sp)
}

private fun inlines(parts: List<MdInline>): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        when (part) {
            is MdInline.Text -> append(part.value)
            is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part.value) }
            is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part.value) }
            is MdInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(part.value) }
        }
    }
}
