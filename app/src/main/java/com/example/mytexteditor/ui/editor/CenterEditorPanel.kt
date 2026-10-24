package com.example.mytexteditor.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.example.mytexteditor.state.*
import com.example.mytexteditor.data.*
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.ui.theme.EditorColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterEditorPanel(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var pendingNewFileAfterSave by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                state.textFieldValue = TextFieldValue(content, TextRange(0))
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
                    state.textFieldValue = TextFieldValue("", TextRange(0))
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

    val onNewFileAction = {
        if (state.isModified) {
            showNewFileDialog = true
        } else {
            state.currentUri = null
            state.fileName = "untitled.kt"
            state.textFieldValue = TextFieldValue("", TextRange(0))
            state.clearHistory()
            state.isModified = false
            Toast.makeText(context, "Đã tạo file mới", Toast.LENGTH_SHORT).show()
        }
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
                                state.textFieldValue = TextFieldValue("", TextRange(0))
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
                                        state.textFieldValue = TextFieldValue("", TextRange(0))
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EditorColors.editorSurface)
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(14.dp))
    ) {
        // Suppress the system soft keyboard completely
        @Suppress("DEPRECATION")
        CompositionLocalProvider(
            LocalTextInputService provides null
        ) {
            BasicTextField(
                value = state.textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text != state.textFieldValue.text) {
                        state.isModified = true
                    }
                    state.textFieldValue = newValue
                },
                onTextLayout = { state.layoutResult = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.None
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (isHeaderExpanded) 62.dp else 26.dp,
                        bottom = 16.dp
                    ),
                textStyle = TextStyle(
                    color = Color(0xFFEAEAEA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(EditorColors.accent)
            )
        }

        // --- GNOME MINIMALIST HEADERBAR MECHANISM ---
        // 1. When Collapsed: Minimal Chevron-down handle at top edge
        if (!isHeaderExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(
                        BorderStroke(1.dp, Color(0xFF3D3D3D)),
                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    .clickable { isHeaderExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                ChevronDownIcon(color = Color(0xFF9A9996))
            }
        }

        // 2. When Expanded: Floating Pill HeaderBar (GNOME Libadwaita style)
        AnimatedVisibility(
            visible = isHeaderExpanded,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2C)) // GNOME Dark Pill
                    .border(BorderStroke(1.dp, Color(0xFF444444)), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // New File button
                HeaderActionPill(
                    text = "New",
                    onClick = onNewFileAction
                )

                // Open File button
                HeaderActionPill(
                    text = "Open",
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("*/*"))
                    }
                )

                // File Name with Status Dot Indicator:
                // Green rgb(64, 160, 43) = Saved; Red rgb(230, 69, 83) = Modified / Unsaved
                val dotColor = if (state.isModified) Color(230, 69, 83) else Color(64, 160, 43)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = state.fileName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Save button
                HeaderActionPill(
                    text = "Save",
                    isAccent = true,
                    onClick = onSaveAction
                )

                // Chevron Up to collapse
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(EditorColors.border)
                        .clickable { isHeaderExpanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    ChevronUpIcon(color = Color(0xFFDDDDDD))
                }
            }
        }
    }
}
