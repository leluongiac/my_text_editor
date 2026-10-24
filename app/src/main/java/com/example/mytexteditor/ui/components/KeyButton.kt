package com.example.mytexteditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun KeyButton(
    primaryText: String = "",
    secondaryText: String? = null,
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
    bgColor: Color = EditorColors.keySurface,
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
            if (bgColor == EditorColors.keySurface) Color(0xFF484848) else bgColor.copy(alpha = 0.7f)
        } else {
            bgColor
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(buttonBg)
                .border(BorderStroke(1.dp, EditorColors.border), RoundedCornerShape(6.dp)),
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
