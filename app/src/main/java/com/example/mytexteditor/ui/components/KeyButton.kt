package com.example.mytexteditor.ui.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.ui.utils.EditorHaptics
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
    onFlick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    // Giữ trạng thái mới nhất bằng rememberUpdatedState để pointerInput(Unit) không bao giờ bị restart
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFlick by rememberUpdatedState(onFlick)
    val currentIsRepeatable by rememberUpdatedState(isRepeatable)
    val currentSecondaryText by rememberUpdatedState(secondaryText)

    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current

    // Ngưỡng vuốt: giảm xuống 16dp để nhạy hơn, thời gian 280ms
    val flickDistanceThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val flickTimeThresholdMs = 280L

    Box(
        modifier = modifier
            .height(54.dp)
            // QUAN TRỌNG: pointerInput(Unit) giúp KHÔNG BAO GIỜ bị restart coroutine khi ấn ?123
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true

                    // 1. PHẢN HỒI XÚC GIÁC TỨC THÌ (ZERO LATENCY HAPTIC) NGAY KHI CHẠM
                    EditorHaptics.keyPress(view, context)

                    val startY = down.position.y
                    val startTime = SystemClock.uptimeMillis()

                    var repeatJob: Job? = null
                    if (currentIsRepeatable) {
                        currentOnClick()
                        repeatJob = scope.launch {
                            delay(350)
                            while (isActive) {
                                EditorHaptics.keyPress(view, context)
                                currentOnClick()
                                delay(45)
                            }
                        }
                    }

                    var endY = startY
                    var wasReleased = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                endY = change.position.y
                                wasReleased = true
                                break
                            }
                        }
                    } finally {
                        repeatJob?.cancel()
                        isPressed = false
                    }

                    // 2. XỬ LÝ NHẢ TAY (RELEASE)
                    if (wasReleased && !currentIsRepeatable) {
                        val deltaY = endY - startY
                        val elapsedTime = SystemClock.uptimeMillis() - startTime

                        // Kiểm tra nếu là thao tác gạt lên (Flick Up)
                        if (currentSecondaryText != null &&
                            currentOnFlick != null &&
                            deltaY < -flickDistanceThresholdPx &&
                            elapsedTime < flickTimeThresholdMs
                        ) {
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