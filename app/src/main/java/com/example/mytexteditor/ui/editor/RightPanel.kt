package com.example.mytexteditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        // Top Row Actions
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
            PadIconButton(iconRes = R.drawable.ic_menu, contentDescription = "Menu", onClick = onMenu)
        }

        if (rightMode == RightMode.TYPING) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val transform = remember(isShiftMode) {
                    { c: String -> if (isShiftMode) c.uppercase() else c.lowercase() }
                }

                // ROW 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r1 = if (isSymbolMode) {
                        listOf("6" to null, "7" to null, "8" to null, "9" to null, "0" to null)
                    } else {
                        listOf("y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0")
                    }
                    r1.forEach { (pri, sec) ->
                        val p = if (isSymbolMode) pri else transform(pri)
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r2 = if (isSymbolMode) {
                        listOf("=" to null, "+" to null, "-" to null, "*" to null, "/" to null)
                    } else {
                        listOf("h" to "_", "j" to "+", "k" to "-", "l" to "=", ";" to ":")
                    }
                    r2.forEach { (pri, sec) ->
                        val p = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 3
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r3 = if (isSymbolMode) {
                        listOf("&" to null, "|" to null, "!" to null, "%" to null, "_" to null)
                    } else {
                        listOf("b" to "\\", "n" to "<", "m" to ">", "," to "?", "." to "\"")
                    }
                    r3.forEach { (pri, sec) ->
                        val p = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 4
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeyButton(primaryText = "⌫", modifier = Modifier.weight(1f), isRepeatable = true) {
                        onKey("BACKSPACE")
                    }
                    KeyButton(primaryText = "SPACE", modifier = Modifier.weight(2f)) {
                        onKey("SPACE")
                    }
                    KeyButton(primaryText = "ENT", iconRes = R.drawable.ic_enter, modifier = Modifier.weight(1f)) {
                        onKey("ENTER")
                    }
                    KeyButton(
                        primaryText = "JOY",
                        iconRes = R.drawable.ic_joystick,
                        modifier = Modifier.weight(1f),
                        bgColor = EditorColors.accent
                    ) { onToggleMode(RightMode.JOYSTICK) }
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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