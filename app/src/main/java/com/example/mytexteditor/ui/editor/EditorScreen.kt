package com.example.mytexteditor.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.state.*
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun EditorScreen() {
    val state = rememberEditorState()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val onKeyAction = remember(state, context, view) {
        { key: String ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            state.onKey(key, context)
        }
    }
    val onJoystickAction = remember(state, haptic) {
        { direction: Direction -> state.onJoystickDirection(direction, haptic) }
    }
    val onToggleModeAction = remember(state, haptic) {
        { mode: RightMode ->
            state.rightMode = mode
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorColors.workspace)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- LEFT PANEL (27%) ---
        LeftPanel(
            isShiftMode = state.isShiftMode,
            isSymbolMode = state.isSymbolMode,
            isCtrlPressed = state.isCtrlPressed,
            onKey = onKeyAction,
            modifier = Modifier
                .weight(0.27f)
                .fillMaxHeight()
        )

        // --- CENTER PANEL (46%) ---
        CenterEditorPanel(
            state = state,
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
        )

        // --- RIGHT PANEL (27%) ---
        RightPanel(
            rightMode = state.rightMode,
            isShiftMode = state.isShiftMode,
            isSymbolMode = state.isSymbolMode,
            onKey = onKeyAction,
            onJoystickDirection = onJoystickAction,
            onToggleMode = onToggleModeAction,
            modifier = Modifier
                .weight(0.27f)
                .fillMaxHeight()
        )
    }
}
