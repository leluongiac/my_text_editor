package com.example.mytexteditor.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun KeyButton(
    primaryText: String = "",
    secondaryText: String? = null,
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF323232),
    textColor: Color = Color.White,
    isRepeatable: Boolean = false,
    onFlick: (() -> Unit)? = null, // Callback khi gạt lên
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFlick by rememberUpdatedState(onFlick)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current

    // Ngưỡng: Vuốt lên trên ít nhất 20dp và thời gian dưới 250ms
    val flickDistanceThresholdPx = with(density) { 20.dp.toPx() }
    val flickTimeThresholdMs = 250L

    Box(
        modifier = modifier
            .height(54.dp)
            .pointerInput(isRepeatable, secondaryText) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true

                    val startY = down.position.y
                    val startTime = SystemClock.uptimeMillis()

                    var repeatJob: Job? = null
                    if (isRepeatable) {
                        currentOnClick()
                        repeatJob = scope.launch {
                            delay(350)
                            while (isActive) {
                                currentOnClick()
                                delay(50)
                            }
                        }
                    }

                    // Theo dõi chuyển động ngón tay đến khi nhấc lên
                    var upEvent = waitForUpOrCancellation()

                    repeatJob?.cancel()
                    isPressed = false

                    if (upEvent != null && !isRepeatable) {
                        val deltaY = upEvent.position.y - startY
                        val elapsedTime = SystemClock.uptimeMillis() - startTime

                        // Kiểm tra điều kiện Flick Up: deltaY < 0 (hướng lên trên)
                        if (secondaryText != null && currentOnFlick != null &&
                            deltaY < -flickDistanceThresholdPx && elapsedTime < flickTimeThresholdMs
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentOnFlick?.invoke()
                        } else {
                            currentOnClick()
                        }
                    }
                }
            }
            .padding(horizontal = 1.5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
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

                val display = if (primaryText == "BACKSPACE") "⌫" else primaryText
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