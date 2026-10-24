package com.example.mytexteditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.R
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun RightPanel(
    rightMode: RightMode,
    isShiftMode: Boolean,
    isSymbolMode: Boolean,
    onKey: (String) -> Unit,
    onJoystickDirection: (Direction) -> Unit,
    onToggleMode: (RightMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (rightMode == RightMode.TYPING) {
            if (isSymbolMode) {
                // Mode ?123 - Cụm tay phải: Toán tử
                // Row 1: 6, 7, 8, 9, 0
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("6", "7", "8", "9", "0").forEach { key ->
                        KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                    }
                }
                // Row 2: =, +, -, *, /
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("=", "+", "-", "*", "/").forEach { key ->
                        KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                    }
                }
                // Row 3: &, |, !, %, _
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("&", "|", "!", "%", "_").forEach { key ->
                        KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                    }
                }
                // Row 4: ⌫, SPACE (double width), ENT, JOY
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeyButton(
                        primaryText = "⌫",
                        modifier = Modifier.weight(1f),
                        isRepeatable = true
                    ) { onKey("BACKSPACE") }

                    KeyButton(
                        primaryText = "SPACE",
                        modifier = Modifier.weight(2f)
                    ) { onKey("SPACE") }

                    KeyButton(
                        primaryText = "ENT",
                        iconRes = R.drawable.ic_enter,
                        modifier = Modifier.weight(1f)
                    ) { onKey("ENTER") }

                    KeyButton(
                        primaryText = "JOY",
                        iconRes = R.drawable.ic_joystick,
                        modifier = Modifier.weight(1f),
                        bgColor = EditorColors.accent
                    ) { onToggleMode(RightMode.JOYSTICK) }
                }
            } else {
                // Mode ABC - Chữ cái
                val transform = { c: String -> if (isShiftMode) c.uppercase() else c.lowercase() }

                // Row 1: y(6), u(7), i(8), o(9), p(0)
                Row(modifier = Modifier.fillMaxWidth()) {
                    val row1 = listOf("y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0")
                    row1.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = transform(pri),
                            secondaryText = sec,
                            modifier = Modifier.weight(1f)
                        ) { onKey(transform(pri)) }
                    }
                }
                // Row 2: h(_), j(+), k(-), l(=), ;(:)
                Row(modifier = Modifier.fillMaxWidth()) {
                    val row2 = listOf("h" to "_", "j" to "+", "k" to "-", "l" to "=", ";" to ":")
                    row2.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f)
                        ) { onKey(pri) }
                    }
                }
                // Row 3: b(\), n(<), m(>), ,(?), .(")
                Row(modifier = Modifier.fillMaxWidth()) {
                    val row3 = listOf("b" to "\\", "n" to "<", "m" to ">", "," to "?", "." to "\"")
                    row3.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f)
                        ) { onKey(pri) }
                    }
                }
                // Row 4: ⌫, SPACE (double width), ENT, JOY
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeyButton(
                        primaryText = "⌫",
                        modifier = Modifier.weight(1f),
                        isRepeatable = true
                    ) { onKey("BACKSPACE") }

                    KeyButton(
                        primaryText = "SPACE",
                        modifier = Modifier.weight(2f)
                    ) { onKey("SPACE") }

                    KeyButton(
                        primaryText = "ENT",
                        iconRes = R.drawable.ic_enter,
                        modifier = Modifier.weight(1f)
                    ) { onKey("ENTER") }

                    KeyButton(
                        primaryText = "JOY",
                        iconRes = R.drawable.ic_joystick,
                        modifier = Modifier.weight(1f),
                        bgColor = EditorColors.accent
                    ) { onToggleMode(RightMode.JOYSTICK) }
                }
            }
        } else {
            // Mode B: Joystick Mode - Fully centered in both width & height
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JoystickController(onDirection = onJoystickDirection)
            }
            KeyButton(
                primaryText = "KEYBOARD",
                modifier = Modifier.fillMaxWidth(),
                bgColor = EditorColors.accent,
                onClick = { onToggleMode(RightMode.TYPING) }
            )
        }
    }
}
