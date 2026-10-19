package com.example.mytexteditor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system bars for an immersive editor experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        setContent {
            MaterialTheme {
                EditorScreen()
            }
        }
    }
}

enum class RightMode { TYPING, JOYSTICK }
enum class Direction { UP, DOWN, LEFT, RIGHT }

@Stable
class EditorState {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = """
                fun main() {
                    println("Hello, Experimental Editor!")
                    
                    // Welcome to the Joystick Keyboard MVP
                    val width = 1920
                    val height = 1080
                    
                    for (i in 1..5) {
                        println("Processing row " + i)
                    }
                }
            """.trimIndent(),
            selection = TextRange(13) // Initial cursor position
        )
    )
    var currentUri by mutableStateOf<Uri?>(null)
    var fileName by mutableStateOf("main.kt")
    var isModified by mutableStateOf(false)

    var isSelectMode by mutableStateOf(false)
    var rightMode by mutableStateOf(RightMode.TYPING)
    var isShiftMode by mutableStateOf(false)
    var isSymbolMode by mutableStateOf(false)

    fun onKey(key: String, haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        
        when (key) {
            "SHIFT" -> {
                isShiftMode = !isShiftMode
                return
            }
            "?123" -> {
                isSymbolMode = true
                return
            }
            "ABC" -> {
                isSymbolMode = false
                return
            }
        }

        val text = textFieldValue.text
        val selection = textFieldValue.selection
        
        if (key == "BACKSPACE" || key == "⌫") {
            if (selection.collapsed) {
                if (selection.start > 0) {
                    val start = selection.start
                    val newText = StringBuilder(text).delete(start - 1, start).toString()
                    textFieldValue = TextFieldValue(newText, TextRange(start - 1))
                    isModified = true
                }
            } else {
                val min = selection.min
                val max = selection.max
                val newText = StringBuilder(text).delete(min, max).toString()
                textFieldValue = TextFieldValue(newText, TextRange(min))
                isModified = true
            }
        } else {
            val insertText = when (key) {
                "SPACE" -> " "
                "TAB" -> "    "
                "ENTER" -> "\n"
                else -> key
            }
            val min = selection.min
            val max = selection.max
            val newText = StringBuilder(text).replace(min, max, insertText).toString()
            textFieldValue = TextFieldValue(newText, TextRange(min + insertText.length))
            isModified = true
        }
    }

    fun onJoystickDirection(direction: Direction, haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val currentCursor = selection.end
        val anchor = if (isSelectMode) selection.start else currentCursor
        
        var newCursor = currentCursor
        when (direction) {
            Direction.RIGHT -> newCursor = (currentCursor + 1).coerceAtMost(text.length)
            Direction.LEFT -> newCursor = (currentCursor - 1).coerceAtLeast(0)
            Direction.DOWN -> newCursor = (currentCursor + 30).coerceAtMost(text.length)
            Direction.UP -> newCursor = (currentCursor - 30).coerceAtLeast(0)
        }
        
        val newSelection = if (isSelectMode) {
            TextRange(anchor, newCursor)
        } else {
            TextRange(newCursor)
        }
        
        textFieldValue = textFieldValue.copy(selection = newSelection)
    }
}

// Storage Access Framework Helpers
fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

fun saveToFile(context: Context, uri: Uri, content: String): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(content.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@Composable
fun EditorScreen() {
    val state = remember { EditorState() }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818)) // GNOME Adwaita Dark Workspace
            .padding(8.dp)
            .systemBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- LEFT PANEL (26%) ---
        LeftPanel(
            state = state,
            onKey = { state.onKey(it, haptic) },
            onToggleSelect = {
                state.isSelectMode = !state.isSelectMode
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier
                .weight(0.26f)
                .fillMaxHeight()
        )

        // --- CENTER PANEL (48%) ---
        CenterEditorPanel(
            state = state,
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight()
        )

        // --- RIGHT PANEL (26%) ---
        RightPanel(
            state = state,
            onKey = { state.onKey(it, haptic) },
            onJoystickDirection = { state.onJoystickDirection(it, haptic) },
            onToggleMode = { mode ->
                state.rightMode = mode
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier
                .weight(0.26f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun LeftPanel(
    state: EditorState,
    onKey: (String) -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transform: (String) -> String = {
        if (it.length == 1 && it.first().isLetter()) {
            if (state.isShiftMode) it.uppercase() else it.lowercase()
        } else {
            it
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isSymbolMode) {
            KeyboardRow(listOf("1", "2", "3", "4", "5"), onKey)
            KeyboardRow(listOf("{", "}", "(", ")", "[", "@"), onKey)
            KeyboardRow(listOf("]", "<", ">", "=", ";", "#"), onKey)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                KeyButton("ABC", modifier = Modifier.weight(1.2f), bgColor = Color(0xFF3584E4)) { onKey("ABC") }
                KeyButton("TAB", modifier = Modifier.weight(1.5f), bgColor = Color(0xFF323232)) { onKey("TAB") }
                KeyButton(
                    text = "SELECT", 
                    iconRes = R.drawable.ic_select,
                    modifier = Modifier.weight(1.3f),
                    bgColor = if (state.isSelectMode) Color(0xFFE5A50A) else Color(0xFF323232),
                    textColor = if (state.isSelectMode) Color.Black else Color.White
                ) { onToggleSelect() }
            }
        } else {
            KeyboardRow(listOf("Q", "W", "E", "R", "T").map(transform), onKey)
            KeyboardRow(listOf("A", "S", "D", "F", "G").map(transform), onKey)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf("Z", "X", "C", "V").forEach { key ->
                    KeyButton(transform(key), modifier = Modifier.weight(1f)) { onKey(transform(key)) }
                }
                KeyButton(
                    text = "SHIFT", 
                    modifier = Modifier.weight(1.3f), 
                    bgColor = if (state.isShiftMode) Color.White else Color(0xFF323232),
                    textColor = if (state.isShiftMode) Color.Black else Color.White
                ) { onKey("SHIFT") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                KeyButton("?123", modifier = Modifier.weight(1.2f), bgColor = Color(0xFF3584E4)) { onKey("?123") }
                KeyButton("TAB", modifier = Modifier.weight(1.5f), bgColor = Color(0xFF323232)) { onKey("TAB") }
                KeyButton(
                    text = "SELECT", 
                    iconRes = R.drawable.ic_select,
                    modifier = Modifier.weight(1.3f),
                    bgColor = if (state.isSelectMode) Color(0xFFE5A50A) else Color(0xFF323232),
                    textColor = if (state.isSelectMode) Color.Black else Color.White
                ) { onToggleSelect() }
            }
        }
    }
}

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
                val name = getFileName(context, uri) ?: "untitled.kt"
                state.currentUri = uri
                state.fileName = name
                state.textFieldValue = TextFieldValue(content, TextRange(0))
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
            val success = saveToFile(context, uri, state.textFieldValue.text)
            if (success) {
                val name = getFileName(context, uri) ?: state.fileName
                if (pendingNewFileAfterSave) {
                    pendingNewFileAfterSave = false
                    state.currentUri = null
                    state.fileName = "untitled.kt"
                    state.textFieldValue = TextFieldValue("", TextRange(0))
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
            val success = saveToFile(context, state.currentUri!!, state.textFieldValue.text)
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
                            bgColor = Color(0xFF552222), // Subtle red for discard
                            onClick = {
                                showNewFileDialog = false
                                state.currentUri = null
                                state.fileName = "untitled.kt"
                                state.textFieldValue = TextFieldValue("", TextRange(0))
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
                                    val success = saveToFile(context, state.currentUri!!, state.textFieldValue.text)
                                    if (success) {
                                        state.currentUri = null
                                        state.fileName = "untitled.kt"
                                        state.textFieldValue = TextFieldValue("", TextRange(0))
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
            .background(Color(0xFF222222)) // GNOME Adwaita Dark Editor
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
                cursorBrush = SolidColor(Color(0xFF3584E4)) // GNOME Blue cursor
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
                        .background(Color(0xFF383838))
                        .clickable { isHeaderExpanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    ChevronUpIcon(color = Color(0xFFDDDDDD))
                }
            }
        }
    }
}

@Composable
fun HeaderActionPill(
    text: String,
    isAccent: Boolean = false,
    bgColor: Color? = null,
    onClick: () -> Unit
) {
    val background = bgColor ?: if (isAccent) Color(0xFF3584E4) else Color(0xFF383838)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChevronDownIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFAAAAAA)
) {
    Canvas(modifier = modifier.size(12.dp, 6.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2f, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ChevronUpIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFAAAAAA)
) {
    Canvas(modifier = modifier.size(12.dp, 6.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun RightPanel(
    state: EditorState,
    onKey: (String) -> Unit,
    onJoystickDirection: (Direction) -> Unit,
    onToggleMode: (RightMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val transform: (String) -> String = {
        if (it.length == 1 && it.first().isLetter()) {
            if (state.isShiftMode) it.uppercase() else it.lowercase()
        } else {
            it
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.rightMode == RightMode.TYPING) {
            // Mode A: Typing Mode
            if (state.isSymbolMode) {
                KeyboardRow(listOf("6", "7", "8", "9", "0"), onKey)
                KeyboardRow(listOf(":", "\"", "'", "/", "\\"), onKey)
                KeyboardRow(listOf("_", "-", "+", "!", "?", "BACKSPACE"), onKey)
            } else {
                KeyboardRow(listOf("Y", "U", "I", "O", "P").map(transform), onKey)
                KeyboardRow(listOf("H", "J", "K", "L").map(transform), onKey)
                KeyboardRow(listOf("B", "N", "M", "BACKSPACE").map(transform), onKey)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                KeyButton("ENTER", modifier = Modifier.weight(1.2f), bgColor = Color(0xFF323232)) { onKey("ENTER") }
                KeyButton("SPACE", modifier = Modifier.weight(1.6f), bgColor = Color(0xFF323232)) { onKey("SPACE") }
                KeyButton(
                    text = "JOYSTICK", 
                    iconRes = R.drawable.ic_joystick,
                    modifier = Modifier.weight(1.2f), 
                    bgColor = Color(0xFF3584E4)
                ) { 
                    onToggleMode(RightMode.JOYSTICK)
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
                text = "KEYBOARD", 
                modifier = Modifier.fillMaxWidth(), 
                bgColor = Color(0xFF3584E4),
                onClick = { onToggleMode(RightMode.TYPING) }
            )
        }
    }
}

@Composable
fun KeyboardRow(keys: List<String>, onKey: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        keys.forEach { key ->
            val isBackspace = (key == "BACKSPACE" || key == "⌫")
            KeyButton(
                text = key, 
                modifier = Modifier.weight(if (isBackspace) 1.5f else 1f),
                isRepeatable = isBackspace,
                onClick = { onKey(key) }
            )
        }
    }
}

@Composable
fun KeyButton(
    text: String = "",
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF323232),
    textColor: Color = Color.White,
    isRepeatable: Boolean = false,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()

    // Outer Box: The Extended Touch Target (42dp x 54dp effective touch bounds)
    // 3dp horizontal padding + 2dp vertical padding creates exact 6dp gap between visible buttons!
    Box(
        modifier = modifier
            .height(54.dp)
            .pointerInput(isRepeatable) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    currentOnClick()

                    var repeatJob: Job? = null
                    if (isRepeatable) {
                        repeatJob = scope.launch {
                            delay(350) // Initial hold threshold
                            while (isActive) {
                                currentOnClick()
                                delay(50) // Spam tick every 50ms
                            }
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeatJob?.cancel()
                        isPressed = false
                    }
                }
            }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner Box: Visual Key (36dp minimum / proportional width x 50dp height)
        val buttonBg = if (isPressed) {
            if (bgColor == Color(0xFF323232)) Color(0xFF484848) else bgColor.copy(alpha = 0.7f)
        } else {
            bgColor
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(buttonBg)
                .border(BorderStroke(1.dp, Color(0xFF3D3D3D)), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = text,
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                val display = when (text) {
                    "BACKSPACE" -> "⌫"
                    "ENTER" -> "↵"
                    "SHIFT" -> "⇧"
                    else -> text
                }
                Text(
                    text = display, 
                    color = textColor, 
                    fontSize = 15.sp, 
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun JoystickController(onDirection: (Direction) -> Unit) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { 60.dp.toPx() } // Limit thumb movement
    val deadZonePx = with(density) { 20.dp.toPx() }  // Deadzone to prevent micro-drifts and accidental activation
    
    // Keyed ONLY on isDragging so touch moves don't constantly cancel and restart the coroutine
    LaunchedEffect(isDragging) {
        while (isDragging) {
            val offset = dragOffset
            val distance = offset.getDistance()
            if (distance > deadZonePx) {
                // 1. Initial micro-step
                val angle = atan2(offset.y, offset.x)
                val degrees = Math.toDegrees(angle.toDouble()).let { if (it < 0) it + 360 else it }
                val initialDirection = when {
                    degrees >= 315 || degrees < 45 -> Direction.RIGHT
                    degrees >= 45 && degrees < 135 -> Direction.DOWN
                    degrees >= 135 && degrees < 225 -> Direction.LEFT
                    else -> Direction.UP
                }
                onDirection(initialDirection)

                // 2. Initial hold delay: allows precise single-step nudge before continuous repeat begins
                delay(320)

                // 3. Repeat loop with velocity-sensitive analog scaling
                while (isDragging && dragOffset.getDistance() > deadZonePx) {
                    val currentOff = dragOffset
                    val curDist = currentOff.getDistance()
                    val curAngle = atan2(currentOff.y, currentOff.x)
                    val curDeg = Math.toDegrees(curAngle.toDouble()).let { if (it < 0) it + 360 else it }
                    val curDir = when {
                        curDeg >= 315 || curDeg < 45 -> Direction.RIGHT
                        curDeg >= 45 && curDeg < 135 -> Direction.DOWN
                        curDeg >= 135 && curDeg < 225 -> Direction.LEFT
                        else -> Direction.UP
                    }
                    onDirection(curDir)

                    // Analog rate scaling:
                    // Near deadzone (micro-adjustment) -> 240ms (slow, highly controllable)
                    // Full deflection (rapid movement) -> 80ms
                    val deflectionRatio = ((curDist - deadZonePx) / (maxRadiusPx - deadZonePx)).coerceIn(0f, 1f)
                    val dynamicDelay = (240 - (deflectionRatio * 160)).toLong()
                    delay(dynamicDelay)
                }
            } else {
                // Polling interval while finger is resting in deadzone
                delay(30)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(Color(0xFF262626)) // GNOME Dark Base
            .border(BorderStroke(1.dp, Color(0xFF383838)), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        dragOffset = Offset.Zero // Snap back to center
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = dragOffset + dragAmount
                        val distance = newOffset.getDistance()
                        
                        // Constrain knob inside the base
                        dragOffset = if (distance > maxRadiusPx) {
                            newOffset / distance * maxRadiusPx
                        } else {
                            newOffset
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Movable Thumb Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF484848)) // GNOME Adwaita Knob
                .border(BorderStroke(1.dp, Color(0xFF5A5A5A)), CircleShape)
        )
    }
}
