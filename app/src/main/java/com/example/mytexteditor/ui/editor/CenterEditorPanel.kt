package com.example.mytexteditor.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.mytexteditor.data.FileUtils
import com.example.mytexteditor.state.EditorState
import com.example.mytexteditor.syntax.TreeSitterVisualTransformation
import com.example.mytexteditor.ui.components.ChevronDownIcon
import com.example.mytexteditor.ui.components.ChevronUpIcon
import com.example.mytexteditor.ui.theme.EditorColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterEditorPanel(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var showPopover by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val visualTransformation = remember(state.syntaxHighlighter.tokens) {
        TreeSitterVisualTransformation(state.syntaxHighlighter.tokens)
    }

    val fullFileName = state.fileName
    val fullPath = remember(state.currentUri, state.fileName) {
        FileUtils.getFilePath(context, state.currentUri)
    }
    val dirPath = remember(state.currentUri) {
        FileUtils.getDirectoryPath(context, state.currentUri)
    }

    val displayFileName = remember(fullFileName) {
        FileUtils.truncateMiddle(fullFileName, 22)
    }
    val displayDirPath = remember(dirPath) {
        FileUtils.truncatePathMiddle(dirPath, 26)
    }
    val dotColor = if (state.isModified) Color(230, 69, 83) else Color(64, 160, 43)

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
                    state.updateTextFieldValue(newValue)
                },
                visualTransformation = visualTransformation,
                onTextLayout = { state.layoutResult = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.None
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (isHeaderExpanded) 68.dp else 26.dp,
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
            Box(
                modifier = Modifier.padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282828)) // GNOME Dark Pill
                        .border(BorderStroke(1.dp, Color(0xFF404040)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Status dot: Green = Saved, Red = Modified / Unsaved
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    // Title & Path Column: Clickable to toggle GNOME Popover
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPopover = !showPopover }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = displayFileName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = displayDirPath,
                            color = Color(0xFF9A9996),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Chevron Up to collapse
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF383838))
                            .clickable {
                                isHeaderExpanded = false
                                showPopover = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronUpIcon(color = Color(0xFFDDDDDD))
                    }
                }

                // GNOME-style Popover displaying full file name and scrollable path with copy button
                if (showPopover) {
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = androidx.compose.ui.unit.IntOffset(0, 160),
                        onDismissRequest = { showPopover = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 280.dp, max = 380.dp)
                                .shadow(16.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C2C2C))
                                .border(BorderStroke(1.dp, Color(0xFF444444)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Full File Name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = fullFileName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Path container: Copy button on the left, horizontally scrollable path on the right
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .border(BorderStroke(1.dp, Color(0xFF363636)), RoundedCornerShape(8.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // GNOME Pill Copy Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EditorColors.accent)
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("path", fullPath))
                                                Toast.makeText(context, "Đã sao chép đường dẫn!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Copy",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Scrollable full path
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = fullPath,
                                            color = Color(0xFFCCCCCC),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
