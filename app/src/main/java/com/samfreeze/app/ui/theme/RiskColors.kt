package com.samfreeze.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.samfreeze.app.model.FreezeLevel

/** Green = recommended/safe, yellow = advanced, orange = expert, red = unsafe. */
fun riskColorFor(level: FreezeLevel): Color = when (level) {
    FreezeLevel.RECOMMENDED -> Color(0xFF4CAF50)
    FreezeLevel.ADVANCED -> Color(0xFFFFC107)
    FreezeLevel.EXPERT -> Color(0xFFFF9800)
    FreezeLevel.UNSAFE -> Color(0xFFF44336)
}
