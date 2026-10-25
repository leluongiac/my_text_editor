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

private val NUM_ROW_RIGHT = listOf("6", "7", "8", "9", "0")
private val SYM_ROW_2_RIGHT = listOf("=", "+", "-", "*", "/")
private val SYM_ROW_3_RIGHT = listOf("&", "|", "!", "%", "_")
private val ABC_ROW_1_RIGHT = listOf("y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0")
private val ABC_ROW_2_RIGHT = listOf("h" to "_", "j" to "+", "k" to "-", "l" to "=", ";" to ":")
private val ABC_ROW_3_RIGHT = listOf("b" to "\\", "n" to "<", "m" to ">", "," to "?", "." to "\"")

@Composable
fun RightPanel(
    rightMode: RightMode,
    isShiftMode: Boolean,
    isSymbolMode: Boolean,
    onKey: (String) -> Unit,
    onJoystickDirection: (Direction) -> Unit,
    onToggleMode: (RightMode) -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Row: Save / Save As & Hamburger Menu buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadIconButton(
                iconRes = if (isShiftMode) R.drawable.ic_save_as else R.drawable.ic_save,
                contentDescription = if (isShiftMode) "Save As" else "Save",
                isAccent = isShiftMode,
                onClick = if (isShiftMode) onSaveAs else onSave
            )
            Spacer(modifier = Modifier.width(8.dp))
            PadIconButton(
                iconRes = R.drawable.ic_menu,
                contentDescription = "Menu",
                onClick = onMenu
            )
        }

        if (rightMode == RightMode.TYPING) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSymbolMode) {
                // Mode ?123 - Cụm tay phải: Toán tử
                // Row 1: 6, 7, 8, 9, 0
                Row(modifier = Modifier.fillMaxWidth()) {
                    NUM_ROW_RIGHT.forEach { key ->
                        KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                    }
                }
                // Row 2: =, +, -, *, /
                Row(modifier = Modifier.fillMaxWidth()) {
                    SYM_ROW_2_RIGHT.forEach { key ->
                        KeyButton(primaryText = key, modifier = Modifier.weight(1f)) { onKey(key) }
                    }
                }
                // Row 3: &, |, !, %, _
                Row(modifier = Modifier.fillMaxWidth()) {
                    SYM_ROW_3_RIGHT.forEach { key ->
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
                    ABC_ROW_1_RIGHT.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = transform(pri),
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = { onKey(sec) },
                            onClick = { onKey(transform(pri)) }
                        )
                    }
                }
                // Row 2: h(_), j(+), k(-), l(=), ;(:)
                Row(modifier = Modifier.fillMaxWidth()) {
                    ABC_ROW_2_RIGHT.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = { onKey(sec) },
                            onClick = { onKey(pri) }
                        )
                    }
                }
                // Row 3: b(\), n(<), m(>), ,(?), .(")
                Row(modifier = Modifier.fillMaxWidth()) {
                    ABC_ROW_3_RIGHT.forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = { onKey(sec) },
                            onClick = { onKey(pri) }
                        )
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
