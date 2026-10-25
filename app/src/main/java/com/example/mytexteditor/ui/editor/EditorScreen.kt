package com.example.mytexteditor.ui.editor

import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mytexteditor.data.FileUtils
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.state.rememberEditorState
import com.example.mytexteditor.ui.components.HeaderActionPill
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun EditorScreen() {
    val state = rememberEditorState()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    var showNewFileDialog by remember { mutableStateOf(false) }
    var pendingNewFileAfterSave by remember { mutableStateOf(false) }

    // SAF Launchers
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val name = FileUtils.getFileName(context, uri) ?: "untitled.kt"
                state.currentUri = uri
                state.fileName = name
                state.updateTextFieldValue(TextFieldValue(content, TextRange(0)), markModified = false)
                state.clearHistory()
                state.isModified = false
                Toast.makeText(context, "Đã mở $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi mở file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = FileUtils.saveToFile(context, uri, state.textFieldValue.text)
            if (success) {
                val name = FileUtils.getFileName(context, uri) ?: state.fileName
                if (pendingNewFileAfterSave) {
                    pendingNewFileAfterSave = false
                    state.currentUri = null
                    state.fileName = "untitled.kt"
                    state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                    state.clearHistory()
                    state.isModified = false
                    Toast.makeText(context, "Đã lưu $name & tạo file mới", Toast.LENGTH_SHORT).show()
                } else {
                    state.currentUri = uri
                    state.fileName = name
                    state.isModified = false
                    Toast.makeText(context, "Đã lưu $name!", Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingNewFileAfterSave = false
                Toast.makeText(context, "Lỗi lưu file!", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingNewFileAfterSave = false
        }
    }

    val onSaveAction = {
        if (state.currentUri != null) {
            val success = FileUtils.saveToFile(context, state.currentUri!!, state.textFieldValue.text)
            if (success) {
                state.isModified = false
                Toast.makeText(context, "Đã lưu!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Lỗi lưu file!", Toast.LENGTH_SHORT).show()
            }
        } else {
            createDocumentLauncher.launch(state.fileName)
        }
    }

    val onSaveAsAction = {
        createDocumentLauncher.launch(state.fileName)
    }

    val onNewFileAction = {
        if (state.isModified) {
            showNewFileDialog = true
        } else {
            state.currentUri = null
            state.fileName = "untitled.kt"
            state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
            state.clearHistory()
            state.isModified = false
            Toast.makeText(context, "Đã tạo file mới", Toast.LENGTH_SHORT).show()
        }
    }

    val onOpenFileAction = {
        openDocumentLauncher.launch(arrayOf("*/*"))
    }

    val onMenuAction = {
        Toast.makeText(context, "Menu (Tính năng sẽ cập nhật)", Toast.LENGTH_SHORT).show()
    }

    // GNOME Style Popup for Unsaved Changes on New File
    if (showNewFileDialog) {
        Dialog(
            onDismissRequest = { showNewFileDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF282828))
                    .border(BorderStroke(1.dp, Color(0xFF3E3E3E)), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Tạo file mới",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bạn có muốn lưu file hiện tại trước khi tạo mới không?",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderActionPill(
                            text = "Hủy",
                            onClick = { showNewFileDialog = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HeaderActionPill(
                            text = "Không lưu",
                            bgColor = Color(0xFF552222),
                            onClick = {
                                showNewFileDialog = false
                                state.currentUri = null
                                state.fileName = "untitled.kt"
                                state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                                state.clearHistory()
                                state.isModified = false
                                Toast.makeText(context, "Đã tạo file mới", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HeaderActionPill(
                            text = "Lưu",
                            isAccent = true,
                            onClick = {
                                showNewFileDialog = false
                                if (state.currentUri != null) {
                                    val success = FileUtils.saveToFile(context, state.currentUri!!, state.textFieldValue.text)
                                    if (success) {
                                        state.currentUri = null
                                        state.fileName = "untitled.kt"
                                        state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                                        state.clearHistory()
                                        state.isModified = false
                                        Toast.makeText(context, "Đã lưu & tạo file mới", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi lưu file!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    pendingNewFileAfterSave = true
                                    createDocumentLauncher.launch(state.fileName)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

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
            onNewFile = onNewFileAction,
            onOpenFile = onOpenFileAction,
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
            onSave = onSaveAction,
            onSaveAs = onSaveAsAction,
            onMenu = onMenuAction,
            modifier = Modifier
                .weight(0.27f)
                .fillMaxHeight()
        )
    }
}
