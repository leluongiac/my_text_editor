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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
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

private val EditorLineHeight = 24.sp
private val GnomeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterEditorPanel(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var showPopover by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current

    // 1. Syntax tokens
    val tokens = state.syntaxHighlighter.tokens.value
    val visualTransformation = remember(tokens) {
        TreeSitterVisualTransformation(tokens)
    }

    val fullFileName = state.fileName
    val fullPath = remember(state.currentUri, state.fileName) {
        FileUtils.getFilePath(context, state.currentUri)
    }
    val dirPath = remember(state.currentUri) {
        FileUtils.getDirectoryPath(context, state.currentUri)
    }
    val displayFileName = remember(fullFileName) { FileUtils.truncateMiddle(fullFileName, 22) }
    val displayDirPath = remember(dirPath) { FileUtils.truncatePathMiddle(dirPath, 26) }
    val dotColor = if (state.isModified) Color(230, 69, 83) else Color(64, 160, 43)

    val verticalScrollState = rememberScrollState()
    val codeHorizontalScrollState = rememberScrollState()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val editorText = state.textFieldValue.text
    val cursorPosition = state.textFieldValue.selection.end.coerceIn(0, editorText.length)

    // TỐI ƯU CỰC MẠNH: Dùng layoutResult tính toán line trong O(1) thay vì loop scan text
    val activeLineNumber = remember(cursorPosition, state.layoutResult) {
        val layout = state.layoutResult
        if (layout != null && cursorPosition <= layout.layoutInput.text.length) {
            layout.getLineForOffset(cursorPosition) + 1
        } else {
            1
        }
    }

    val totalLines = remember(editorText) {
        editorText.count { it == '\n' } + 1
    }

    val lineNumbers = remember(totalLines, activeLineNumber) {
        buildAnnotatedString {
            for (i in 1..totalLines) {
                if (i > 1) append('\n')
                val isActive = (i == activeLineNumber)
                withStyle(
                    SpanStyle(
                        color = if (isActive) Color(0xFFF6F5F4) else Color.White.copy(alpha = 0.28f),
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                ) {
                    append(i.toString())
                }
            }
        }
    }

    // --- AUTO-SCROLL VIEWPORT KHI JOYSTICK / CON TRỎ DI CHUYỂN ---
    LaunchedEffect(cursorPosition, state.layoutResult, viewportSize) {
        val layout = state.layoutResult ?: return@LaunchedEffect
        if (viewportSize.height == 0 || viewportSize.width == 0) return@LaunchedEffect
        if (cursorPosition !in 0..layout.layoutInput.text.length) return@LaunchedEffect

        val cursorRect = layout.getCursorRect(cursorPosition)
        val lineHeightPx = with(density) { EditorLineHeight.toPx() }

        // Cuộn dọc
        val currentScrollY = verticalScrollState.value
        val viewHeight = viewportSize.height
        if (cursorRect.top < currentScrollY) {
            verticalScrollState.scrollTo(cursorRect.top.toInt())
        } else if (cursorRect.bottom > currentScrollY + viewHeight - lineHeightPx) {
            verticalScrollState.scrollTo((cursorRect.bottom - viewHeight + lineHeightPx).toInt())
        }

        // Cuộn ngang
        val currentScrollX = codeHorizontalScrollState.value
        val viewWidth = viewportSize.width
        if (cursorRect.left < currentScrollX) {
            codeHorizontalScrollState.scrollTo(cursorRect.left.toInt().coerceAtLeast(0))
        } else if (cursorRect.right > currentScrollX + viewWidth - 60) {
            codeHorizontalScrollState.scrollTo((cursorRect.right - viewWidth + 80).toInt())
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EditorColors.editorSurface)
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(14.dp))
            .onSizeChanged { viewportSize = it }
    ) {
        @Suppress("DEPRECATION")
        CompositionLocalProvider(LocalTextInputService provides null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (isHeaderExpanded) 68.dp else 26.dp,
                        bottom = 16.dp
                    )
                    .verticalScroll(verticalScrollState)
            ) {
                // CỘT SỐ DÒNG
                GnomeGutter(lineNumbers = lineNumbers)
                Spacer(modifier = Modifier.width(6.dp))

                // VÙNG CODE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(codeHorizontalScrollState)
                ) {
                    BasicTextField(
                        value = state.textFieldValue,
                        onValueChange = { newValue -> state.updateTextFieldValue(newValue) },
                        visualTransformation = visualTransformation,
                        onTextLayout = { state.layoutResult = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.None
                        ),
                        textStyle = TextStyle(
                            color = Color(0xFFEAEAEA),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = EditorLineHeight,
                            lineHeightStyle = GnomeLineHeightStyle
                        ),
                        cursorBrush = SolidColor(EditorColors.accent),
                        modifier = Modifier.width(IntrinsicSize.Max)
                    )
                }
            }
        }

        // GNOME Minimalist HeaderBar
        if (!isHeaderExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(BorderStroke(1.dp, Color(0xFF3D3D3D)), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .clickable { isHeaderExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                ChevronDownIcon(color = Color(0xFF9A9996))
            }
        }

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
                        .background(Color(0xFF282828))
                        .border(BorderStroke(1.dp, Color(0xFF404040)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPopover = !showPopover }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = displayFileName, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(text = displayDirPath, color = Color(0xFF9A9996), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
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
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                                    Text(text = fullFileName, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .border(BorderStroke(1.dp, Color(0xFF363636)), RoundedCornerShape(8.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                        Text(text = "Copy", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                                        Text(text = fullPath, color = Color(0xFFCCCCCC), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
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

@Composable
private fun GnomeGutter(
    lineNumbers: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 10.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val x = size.width
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth
                )
            }
            .padding(end = 8.dp)
    ) {
        Text(
            text = lineNumbers,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = EditorLineHeight,
                lineHeightStyle = GnomeLineHeightStyle,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.widthIn(min = 20.dp),
            softWrap = false
        )
    }
}