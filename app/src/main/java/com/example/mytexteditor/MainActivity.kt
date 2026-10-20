package com.example.mytexteditor

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import android.content.ClipData
import android.content.ClipboardManager
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
    var isCtrlPressed by mutableStateOf(false)

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    fun pushUndoSnapshot(snapshot: TextFieldValue) {
        if (undoStack.isEmpty() || undoStack.last() != snapshot) {
            undoStack.addLast(snapshot)
            if (undoStack.size > 100) {
                undoStack.removeFirst()
            }
        }
        redoStack.clear()
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    fun onKey(key: String, context: Context) {
        // haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        
        when (key) {
            "SHIFT" -> {
                isShiftMode = !isShiftMode
                isSelectMode = isShiftMode // Merged Shift & Text Selection
                return
            }
            "?123", "123" -> {
                isSymbolMode = true
                return
            }
            "ABC" -> {
                isSymbolMode = false
                return
            }
            "CTRL" -> {
                isCtrlPressed = !isCtrlPressed
                return
            }
        }

        // --- Handle CTRL Shortcuts ---
        if (isCtrlPressed) {
            val lower = key.lowercase()
            when (lower) {
                "z" -> {
                    // Ctrl + Z: Undo
                    if (undoStack.isNotEmpty()) {
                        redoStack.addLast(textFieldValue)
                        textFieldValue = undoStack.removeLast()
                        isModified = true
                        Toast.makeText(context, "Hoàn tác (Undo)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không còn thao tác để hoàn tác", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }
                "u" -> {
                    // Ctrl + U: Redo
                    if (redoStack.isNotEmpty()) {
                        undoStack.addLast(textFieldValue)
                        textFieldValue = redoStack.removeLast()
                        isModified = true
                        Toast.makeText(context, "Làm lại (Redo)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không còn thao tác để làm lại", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }
                "c" -> {
                    // Ctrl + C: Copy
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = textFieldValue.text
                    val selection = textFieldValue.selection
                    val textToCopy = if (!selection.collapsed) {
                        text.substring(selection.min, selection.max)
                    } else {
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
                        val endOfLine = text.indexOf('\n', selection.start).let { if (it == -1) text.length else it }
                        text.substring(startOfLine, endOfLine)
                    }
                    if (textToCopy.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCopy))
                        Toast.makeText(context, "Đã chép (Copy)", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }
                "v" -> {
                    // Ctrl + V: Paste
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        if (pasteText.isNotEmpty()) {
                            if (key in listOf("SPACE", "ENTER", "ENT", "BACKSPACE", "⌫", "TAB")) {
                                pushUndoSnapshot(textFieldValue)
                            }
                            val text = textFieldValue.text
                            val selection = textFieldValue.selection
                            val min = selection.min
                            val max = selection.max
                            val newText = StringBuilder(text).replace(min, max, pasteText).toString()
                            textFieldValue = TextFieldValue(newText, TextRange(min + pasteText.length))
                            isModified = true
                            Toast.makeText(context, "Đã dán (Paste)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCtrlPressed = false
                    return
                }
                "k" -> {
                    // Ctrl + K: Cut
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = textFieldValue.text
                    val selection = textFieldValue.selection
                    if (!selection.collapsed) {
                        val min = selection.min
                        val max = selection.max
                        val textToCut = text.substring(min, max)
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCut))
                        pushUndoSnapshot(textFieldValue)
                        val newText = StringBuilder(text).delete(min, max).toString()
                        textFieldValue = TextFieldValue(newText, TextRange(min))
                        isModified = true
                        Toast.makeText(context, "Đã cắt (Cut)", Toast.LENGTH_SHORT).show()
                    } else {
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
                        val endOfLine = text.indexOf('\n', selection.start).let { if (it == -1) text.length else (it + 1).coerceAtMost(text.length) }
                        if (startOfLine < text.length) {
                            val textToCut = text.substring(startOfLine, endOfLine)
                            clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCut))
                            pushUndoSnapshot(textFieldValue)
                            val newText = StringBuilder(text).delete(startOfLine, endOfLine).toString()
                            textFieldValue = TextFieldValue(newText, TextRange(startOfLine.coerceAtMost(newText.length)))
                            isModified = true
                            Toast.makeText(context, "Đã cắt dòng (Cut)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCtrlPressed = false
                    return
                }
                else -> {
                    // Other key clears CTRL latch and processes normally
                    isCtrlPressed = false
                }
            }
        }

        // Before normal typing/backspacing, record undo state
        pushUndoSnapshot(textFieldValue)

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
                "ENTER", "ENT" -> "\n"
                else -> {
                    if (key.length == 1 && key.first().isLetter()) {
                        if (isShiftMode) key.uppercase() else key.lowercase()
                    } else {
                        key
                    }
                }
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
    val context = LocalContext.current
    val view = LocalView.current
    // val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818)) // GNOME Adwaita Dark Workspace
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- LEFT PANEL (27%) ---
        LeftPanel(
            state = state,
            onKey = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                state.onKey(it, context) 
            },
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
            state = state,
            onKey = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                state.onKey(it, context) 
            },
            onJoystickDirection = { state.onJoystickDirection(it, haptic) },
            onToggleMode = { mode ->
                state.rightMode = mode
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier
                .weight(0.27f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun LeftPanel(
    state: EditorState,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isSymbolMode) {
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
                    bgColor = if (state.isCtrlPressed) Color(0xFF3584E4) else Color(0xFF323232)
                ) { onKey("CTRL") }
                KeyButton(primaryText = "TAB", modifier = Modifier.weight(1f)) { onKey("TAB") }
                KeyButton(primaryText = "ABC", modifier = Modifier.weight(1f), bgColor = Color(0xFF3584E4)) { onKey("ABC") }
            }
        } else {
            // Mode ABC - Chữ cái
            val transform = { c: String -> if (state.isShiftMode) c.uppercase() else c.lowercase() }
            
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
                    bgColor = if (state.isShiftMode) Color(0xFFE5A50A) else Color(0xFF323232),
                    textColor = if (state.isShiftMode) Color.Black else Color.White
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
                    bgColor = if (state.isCtrlPressed) Color(0xFF3584E4) else Color(0xFF323232)
                ) { onKey("CTRL") }
                KeyButton(primaryText = "TAB", modifier = Modifier.weight(1f)) { onKey("TAB") }
                KeyButton(primaryText = "?123", modifier = Modifier.weight(1f), bgColor = Color(0xFF3584E4)) { onKey("?123") }
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
            val success = saveToFile(context, uri, state.textFieldValue.text)
            if (success) {
                val name = getFileName(context, uri) ?: state.fileName
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
                                    val success = saveToFile(context, state.currentUri!!, state.textFieldValue.text)
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
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.rightMode == RightMode.TYPING) {
            if (state.isSymbolMode) {
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
                        bgColor = Color(0xFF3584E4)
                    ) { onToggleMode(RightMode.JOYSTICK) }
                }
            } else {
                // Mode ABC - Chữ cái
                val transform = { c: String -> if (state.isShiftMode) c.uppercase() else c.lowercase() }
                
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
                        bgColor = Color(0xFF3584E4)
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
                bgColor = Color(0xFF3584E4),
                onClick = { onToggleMode(RightMode.TYPING) }
            )
        }
    }
}

@Composable
fun KeyButton(
    primaryText: String = "",
    secondaryText: String? = null,
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
            .padding(horizontal = 1.5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner Box: Visual Key (wider keys, tight 3dp gap matching Gboard layout)
        val buttonBg = if (isPressed) {
            if (bgColor == Color(0xFF323232)) Color(0xFF484848) else bgColor.copy(alpha = 0.7f)
        } else {
            bgColor
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(buttonBg)
                .border(BorderStroke(1.dp, Color(0xFF383838)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = primaryText,
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                // Secondary key in top right corner (Muted font 11sp, color #8A8A8E)
                if (!secondaryText.isNullOrEmpty()) {
                    Text(
                        text = secondaryText,
                        color = Color(0xFF8A8A8E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 4.dp)
                    )
                }

                val display = when (primaryText) {
                    "BACKSPACE" -> "⌫"
                    else -> primaryText
                }
                
                // Primary key in center (or slightly offset down if secondary key is present)
                val isFunctionKey = display.length > 1
                Text(
                    text = display, 
                    color = textColor, 
                    fontSize = if (isFunctionKey) 12.sp else 17.sp, 
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isFunctionKey) FontWeight.SemiBold else FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = if (!secondaryText.isNullOrEmpty()) 2.dp else 0.dp)
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
