package com.example.mytexteditor.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class TreeSitterVisualTransformation(
    private val tokens: IntArray,
    private val colorScheme: SyntaxColorScheme = SyntaxColorScheme()
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val textLength = text.length

        if (tokens.isEmpty() || textLength == 0) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(text)
        val count = tokens.size / 3

        for (i in 0 until count) {
            val idx = i * 3
            val start = tokens[idx]
            val end = tokens[idx + 1]
            val type = tokens[idx + 2]

            // Bảo vệ an toàn index không bao giờ văng Exception
            if (start in 0 until textLength && end in (start + 1)..textLength) {
                val color = when (type) {
                    1 -> colorScheme.keywordColor
                    2 -> colorScheme.typeColor
                    3 -> colorScheme.stringColor
                    4 -> colorScheme.commentColor
                    5 -> colorScheme.functionColor
                    6 -> colorScheme.numberColor
                    else -> null
                }
                if (color != null) {
                    builder.addStyle(SpanStyle(color = color), start, end)
                }
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TreeSitterVisualTransformation) return false
        return tokens.contentEquals(other.tokens) && colorScheme == other.colorScheme
    }

    override fun hashCode(): Int {
        return tokens.contentHashCode() * 31 + colorScheme.hashCode()
    }
}