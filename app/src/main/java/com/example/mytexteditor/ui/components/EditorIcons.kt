package com.example.mytexteditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun HeaderActionPill(
    text: String,
    isAccent: Boolean = false,
    bgColor: Color? = null,
    onClick: () -> Unit
) {
    val background = bgColor ?: if (isAccent) EditorColors.accent else EditorColors.border
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
