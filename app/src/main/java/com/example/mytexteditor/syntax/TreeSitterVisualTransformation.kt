package com.example.mytexteditor.syntax

import androidx.compose.runtime.State
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class TreeSitterVisualTransformation(
    private val tokensState: State<IntArray>,
    private val colorScheme: SyntaxColorScheme = SyntaxColorScheme()
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val tokens = tokensState.value
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
}
