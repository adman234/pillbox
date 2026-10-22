package com.adman234.pillbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adman234.pillbox.data.PillShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val PillColors = listOf(
    0xFFFFFFFF, 0xFFF5E6B8, 0xFFFFC857, 0xFFFF7A59, 0xFFE5484D, 0xFFE87BB0,
    0xFFA06CD5, 0xFF5B6FD6, 0xFF3FA7D6, 0xFF1F6F78, 0xFF4CAF50, 0xFF8D6E63,
).map { it.toInt() }

/** A drawn pill, bottle, drop and so on in the chosen color. */
@Composable
fun PillIcon(shape: PillShape, color: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawPill(shape, Color(color)) }
}

private fun DrawScope.drawPill(shape: PillShape, base: Color) {
    val w = size.width
    val h = size.height
    val outline = Color(0x33000000)
    val dark = lerp(base, Color.Black, 0.15f)
    val shine = Color(0x55FFFFFF)

    fun finish(path: Path) {
        drawPath(path, outline, style = Stroke(width = w * 0.05f))
    }

    when (shape) {
        PillShape.ROUND -> {
            val r = w * 0.42f
            drawCircle(base, r, Offset(w / 2, h / 2))
            drawCircle(outline, r, Offset(w / 2, h / 2), style = Stroke(w * 0.05f))
            drawLine(dark, Offset(w * 0.2f, h / 2), Offset(w * 0.8f, h / 2), strokeWidth = w * 0.04f)
            drawCircle(shine, r * 0.28f, Offset(w * 0.36f, h * 0.34f))
        }
        PillShape.OVAL -> rotate(-35f) {
            val pw = w * 0.9f
            val ph = h * 0.5f
            val tl = Offset((w - pw) / 2, (h - ph) / 2)
            drawOval(base, tl, Size(pw, ph))
            drawOval(outline, tl, Size(pw, ph), style = Stroke(w * 0.05f))
            drawLine(dark, Offset(w / 2, tl.y + ph * 0.15f), Offset(w / 2, tl.y + ph * 0.85f), strokeWidth = w * 0.04f)
        }
        PillShape.CAPSULE -> rotate(-45f) {
            val pw = w * 0.92f
            val ph = h * 0.44f
            val tl = Offset((w - pw) / 2, (h - ph) / 2)
            val corner = CornerRadius(ph / 2)
            val half = pw / 2
            drawRoundRect(Color.White, tl, Size(pw, ph), corner)
            clipRect(left = tl.x + half, top = tl.y, right = tl.x + pw, bottom = tl.y + ph) {
                drawRoundRect(base, tl, Size(pw, ph), corner)
            }
            drawRoundRect(outline, tl, Size(pw, ph), corner, style = Stroke(w * 0.05f))
            drawLine(shine, Offset(tl.x + ph * 0.5f, tl.y + ph * 0.25f), Offset(tl.x + pw - ph * 0.5f, tl.y + ph * 0.25f), strokeWidth = w * 0.04f)
        }
        PillShape.SQUARE -> {
            val s = w * 0.7f
            val tl = Offset((w - s) / 2, (h - s) / 2)
            drawRoundRect(base, tl, Size(s, s), CornerRadius(s * 0.18f))
            drawRoundRect(outline, tl, Size(s, s), CornerRadius(s * 0.18f), style = Stroke(w * 0.05f))
            drawLine(dark, Offset(tl.x + s * 0.15f, h / 2), Offset(tl.x + s * 0.85f, h / 2), strokeWidth = w * 0.04f)
        }
        PillShape.DIAMOND -> {
            val p = Path().apply {
                moveTo(w / 2, h * 0.08f); lineTo(w * 0.92f, h / 2); lineTo(w / 2, h * 0.92f); lineTo(w * 0.08f, h / 2); close()
            }
            drawPath(p, base)
            finish(p)
            drawLine(dark, Offset(w * 0.28f, h / 2), Offset(w * 0.72f, h / 2), strokeWidth = w * 0.04f)
        }
        PillShape.HEXAGON -> {
            val p = Path()
            for (i in 0 until 6) {
                val a = PI / 3 * i + PI / 6
                val x = w / 2 + (w * 0.44f) * cos(a).toFloat()
                val y = h / 2 + (h * 0.44f) * sin(a).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, base)
            finish(p)
            drawCircle(shine, w * 0.07f, Offset(w * 0.4f, h * 0.38f))
        }
        PillShape.DROP -> {
            val p = Path().apply {
                moveTo(w / 2, h * 0.06f)
                cubicTo(w * 0.95f, h * 0.5f, w * 0.85f, h * 0.94f, w / 2, h * 0.94f)
                cubicTo(w * 0.15f, h * 0.94f, w * 0.05f, h * 0.5f, w / 2, h * 0.06f)
                close()
            }
            drawPath(p, base)
            finish(p)
            drawCircle(shine, w * 0.07f, Offset(w * 0.38f, h * 0.62f))
        }
        PillShape.BOTTLE -> {
            val body = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.2f, h * 0.3f, w * 0.8f, h * 0.94f, CornerRadius(w * 0.1f)))
            }
            drawPath(body, base)
            finish(body)
            val cap = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.26f, h * 0.06f, w * 0.74f, h * 0.3f, CornerRadius(w * 0.06f)))
            }
            drawPath(cap, dark)
            drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.28f, h * 0.5f), Size(w * 0.44f, h * 0.3f))
        }
    }
}
