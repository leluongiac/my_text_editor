package com.example.mytexteditor.syntax

import androidx.compose.ui.graphics.Color

data class SyntaxColorScheme(
    val keywordColor: Color = Color(0xFFFF7B72),   // Keyword (Red / Coral)
    val typeColor: Color = Color(0xFF79C0FF),      // Type (Light Blue)
    val stringColor: Color = Color(0xFFA5D6FF),    // String (Soft Blue/Cyan)
    val commentColor: Color = Color(0xFF8B949E),   // Comment (Muted Grey)
    val functionColor: Color = Color(0xFFD2A8FF),  // Function / Operator (Purple)
    val numberColor: Color = Color(0xFF7EE787)     // Number (Light Green)
)
