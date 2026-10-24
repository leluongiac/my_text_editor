package com.example.mytexteditor.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode

@Stable
class EditorState {
    var layoutResult by mutableStateOf<TextLayoutResult?>(null)

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
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0))
                            .let { if (it == -1) 0 else it + 1 }
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
                            pushUndoSnapshot(textFieldValue)
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
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0))
                            .let { if (it == -1) 0 else it + 1 }
                        val endOfLine = text.indexOf('\n', selection.start)
                            .let { if (it == -1) text.length else (it + 1).coerceAtMost(text.length) }
                        if (startOfLine < text.length) {
                            val textToCut = text.substring(startOfLine, endOfLine)
                            clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCut))
                            pushUndoSnapshot(textFieldValue)
                            val newText = StringBuilder(text).delete(startOfLine, endOfLine).toString()
                            textFieldValue =
                                TextFieldValue(newText, TextRange(startOfLine.coerceAtMost(newText.length)))
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
        if (key in listOf("SPACE", "ENTER", "ENT", "BACKSPACE", "⌫", "TAB")) {
            pushUndoSnapshot(textFieldValue)
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
            Direction.DOWN, Direction.UP -> {
                val layout = layoutResult
                if (layout == null) {
                    newCursor = if (direction == Direction.UP) {
                        getFallbackUp(text, currentCursor)
                    } else {
                        getFallbackDown(text, currentCursor)
                    }
                } else {
                    val safeCursor = currentCursor.coerceIn(0, text.length)
                    val currentLine = layout.getLineForOffset(safeCursor)
                    val targetLine = if (direction == Direction.UP) currentLine - 1 else currentLine + 1
                    if (targetLine in 0 until layout.lineCount) {
                        val x = layout.getCursorRect(safeCursor).left
                        val y = (layout.getLineTop(targetLine) + layout.getLineBottom(targetLine)) / 2f
                        newCursor = layout.getOffsetForPosition(Offset(x, y))
                    } else {
                        // SỬA TẠI ĐÂY: Nếu vượt biên thì nhảy về kịch đầu hoặc kịch cuối
                        newCursor = if (direction == Direction.UP) 0 else text.length
                    }
                }
            }
        }

        val newSelection = if (isSelectMode) {
            TextRange(anchor, newCursor)
        } else {
            TextRange(newCursor)
        }

        textFieldValue = textFieldValue.copy(selection = newSelection)
    }

    private fun getFallbackUp(text: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, text.length)
        val currentLineStart = if (safeCursor == 0) 0 else {
            text.lastIndexOf('\n', safeCursor - 1) + 1
        }
        if (currentLineStart == 0) return safeCursor

        val previousLineEnd = currentLineStart - 1
        val previousLineStart = if (previousLineEnd == 0) 0 else {
            text.lastIndexOf('\n', previousLineEnd - 1) + 1
        }
        val column = safeCursor - currentLineStart
        return (previousLineStart + column).coerceAtMost(previousLineEnd)
    }

    private fun getFallbackDown(text: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, text.length)
        val currentLineStart = if (safeCursor == 0) 0 else {
            text.lastIndexOf('\n', safeCursor - 1) + 1
        }
        val currentLineEnd = text.indexOf('\n', safeCursor).let {
            if (it == -1) text.length else it
        }
        if (currentLineEnd == text.length) return safeCursor

        val nextLineStart = currentLineEnd + 1
        val nextLineEnd = text.indexOf('\n', nextLineStart).let {
            if (it == -1) text.length else it
        }
        val column = safeCursor - currentLineStart
        return (nextLineStart + column).coerceAtMost(nextLineEnd)
    }
}

@Composable
fun rememberEditorState(): EditorState = remember { EditorState() }
