package com.example.mytexteditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.R
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun LeftPanel(
    isShiftMode: Boolean,
    isSymbolMode: Boolean,
    isCtrlPressed: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSymbolMode) {
            // Mode ?123 - Cụm tay trái: Bộ Ngoặc
            // Row 1: 1, 2, 3, 4, 5
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("1", "2", "3", "4", "5").forEach { key ->
                    KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                }
            }
            // Row 2: (, ), [, ], "
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("(", ")", "[", "]", "\"").forEach { key ->
                    KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                }
            }
            // Row 3: {, }, <, >, '
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("{", "}", "<", ">", "'").forEach { key ->
                    KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                }
            }
            // Row 4: CTRL, TAB, ABC
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyButton(
                    primaryText = "CTRL",
                    modifier = Modifier.weight(1f),
                    bgColor = if (isCtrlPressed) EditorColors.accent else EditorColors.keySurface
                ) { onKey("CTRL") }
                KeyButton(primaryText = "TAB", modifier = Modifier.weight(1f)) { onKey("TAB") }
                KeyButton(
                    primaryText = "ABC",
                    modifier = Modifier.weight(1f),
                    bgColor = EditorColors.accent
                ) { onKey("ABC") }
            }
        } else {
            // Mode ABC - Chữ cái
            val transform = { c: String -> if (isShiftMode) c.uppercase() else c.lowercase() }

            // Row 1: q(1), w(2), e(3), r(4), t(5)
            Row(modifier = Modifier.fillMaxWidth()) {
                val row1 = listOf("q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5")
                row1.forEach { (pri, sec) ->
                    KeyButton(
                        primaryText = transform(pri),
                        secondaryText = sec,
                        modifier = Modifier.weight(1f)
                    ) { onKey(transform(pri)) }
                }
            }
            // Row 2: a(~), s(!), d(@), f(#), g($)
            Row(modifier = Modifier.fillMaxWidth()) {
                val row2 = listOf("a" to "~", "s" to "!", "d" to "@", "f" to "#", "g" to "$")
                row2.forEach { (pri, sec) ->
                    KeyButton(
                        primaryText = transform(pri),
                        secondaryText = sec,
                        modifier = Modifier.weight(1f)
                    ) { onKey(transform(pri)) }
                }
            }
            // Row 3: SHIFT/CAPS, z(^), x(&), c(*), v(/)
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyButton(
                    primaryText = "SHIFT",
                    iconRes = R.drawable.ic_shift,
                    modifier = Modifier.weight(1f),
                    bgColor = if (isShiftMode) Color(0xFFE5A50A) else EditorColors.keySurface,
                    textColor = if (isShiftMode) Color.Black else Color.White
                ) { onKey("SHIFT") }

                val row3 = listOf("z" to "^", "x" to "&", "c" to "*", "v" to "/")
                row3.forEach { (pri, sec) ->
                    KeyButton(
                        primaryText = transform(pri),
                        secondaryText = sec,
                        modifier = Modifier.weight(1f)
                    ) { onKey(transform(pri)) }
                }
            }
            // Row 4: CTRL, TAB, ?123
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyButton(
                    primaryText = "CTRL",
                    modifier = Modifier.weight(1f),
                    bgColor = if (isCtrlPressed) EditorColors.accent else EditorColors.keySurface
                ) { onKey("CTRL") }
                KeyButton(primaryText = "TAB", modifier = Modifier.weight(1f)) { onKey("TAB") }
                KeyButton(
                    primaryText = "?123",
                    modifier = Modifier.weight(1f),
                    bgColor = EditorColors.accent
                ) { onKey("?123") }
            }
        }
    }
}
