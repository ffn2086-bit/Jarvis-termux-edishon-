package com.jarvis.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

data class Pal(val accent: Color, val bg: Color, val card: Color, val line: Color, val dim: Color)

val NormalPal = Pal(Color(0xFF22E5FF), Color(0xFF05090F), Color(0xFF0B151C), Color(0xFF12485A), Color(0xFF8FA3AD))
val HybridPal = Pal(Color(0xFFFF2D3D), Color(0xFF0D0305), Color(0xFF1A080B), Color(0xFF6B1624), Color(0xFFB59096))
val LocalPal = compositionLocalOf { NormalPal }

@Composable
fun animPal(hybrid: Boolean): Pal {
    val t = if (hybrid) HybridPal else NormalPal
    val a by animateColorAsState(t.accent, tween(700), label = "a")
    val b by animateColorAsState(t.bg, tween(700), label = "b")
    val c by animateColorAsState(t.card, tween(700), label = "c")
    val l by animateColorAsState(t.line, tween(700), label = "l")
    val d by animateColorAsState(t.dim, tween(700), label = "d")
    return Pal(a, b, c, l, d)
}
