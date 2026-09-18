package dev.jiankaichen.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jiankaichen.clock.data.SessionKind

/** GitHub dark palette (contribution graph). */
object GitHub {
    val Bg = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val Border = Color(0xFF30363D)
    val Text = Color(0xFFE6EDF3)
    val Muted = Color(0xFF8B949E)
    val EmptyCell = Color(0xFF161B22)
    val Green0 = Color(0xFF0E4429)
    val Green1 = Color(0xFF006D32)
    val Green2 = Color(0xFF26A641)
    val Green3 = Color(0xFF39D353)
    val Blue = Color(0xFF58A6FF)
    val Red = Color(0xFFF85149)
    val Orange = Color(0xFFD29922)
}

/** 5x7 cell patterns. Must match tools/gen_pixel_font.py, which builds the widget font. */
private val PATTERNS: Map<Char, List<String>> = mapOf(
    '0' to listOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "),
    '1' to listOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
    '2' to listOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"),
    '3' to listOf("#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "),
    '4' to listOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "),
    '5' to listOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "),
    '6' to listOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "),
    '7' to listOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "),
    '8' to listOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "),
    '9' to listOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "),
    ':' to listOf(" ", " ", "#", " ", "#", " ", " "),
    '.' to listOf(" ", " ", " ", " ", " ", " ", "#"),
    ' ' to listOf("   ", "   ", "   ", "   ", "   ", "   ", "   "),
)
private const val ROWS = 7
private const val GAP_RATIO = 0.25f

/** Total grid columns for [text], including one empty column between glyphs. */
fun pixelColumns(text: String): Int {
    var cols = 0
    text.forEachIndexed { i, ch ->
        cols += (PATTERNS[ch] ?: PATTERNS.getValue(' '))[0].length
        if (i < text.lastIndex) cols += 1
    }
    return cols
}

/**
 * Draws [text] as a GitHub-style contribution grid: every cell of the grid is painted in
 * [dim], and the cells that make up the glyphs in [lit]. The caller sets the width; height
 * follows from the grid shape. Only digits, ':', '.' and space are supported.
 */
@Composable
fun PixelDigits(
    text: String,
    modifier: Modifier = Modifier,
    lit: Color = GitHub.Green3,
    dim: Color = GitHub.EmptyCell,
) {
    val cols = pixelColumns(text).coerceAtLeast(1)
    val ratio = (cols + (cols - 1) * GAP_RATIO) / (ROWS + (ROWS - 1) * GAP_RATIO)

    Canvas(modifier.aspectRatio(ratio)) {
        val cell = size.width / (cols + (cols - 1) * GAP_RATIO)
        val gap = cell * GAP_RATIO
        val pitch = cell + gap
        val radius = CornerRadius(cell * 0.18f)
        val cellSize = Size(cell, cell)

        for (r in 0 until ROWS) for (c in 0 until cols) {
            drawRoundRect(dim, Offset(c * pitch, r * pitch), cellSize, radius)
        }

        var col = 0
        text.forEachIndexed { i, ch ->
            val pattern = PATTERNS[ch] ?: PATTERNS.getValue(' ')
            pattern.forEachIndexed { r, line ->
                line.forEachIndexed { c, px ->
                    if (px == '#') drawRoundRect(lit, Offset((col + c) * pitch, r * pitch), cellSize, radius)
                }
            }
            col += pattern[0].length
            if (i < text.lastIndex) col += 1
        }
    }
}

/** Short code tag for a Pomodoro phase, used where the app used to show an emoji. */
fun phaseTag(kind: SessionKind): String = when (kind) {
    SessionKind.WORK -> "FOCUS"
    SessionKind.SHORT_BREAK -> "BREAK"
    SessionKind.LONG_BREAK -> "LONG"
    SessionKind.TIMER -> "TIMER"
}

/**
 * Terminal-style badge: monospace green code in a dark bordered box, e.g. `TEA` or `FOCUS`.
 * This is the Matrix-flavoured replacement for emoji throughout the app.
 */
@Composable
fun MatrixTag(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text.uppercase(),
        modifier
            .background(GitHub.Bg, RoundedCornerShape(4.dp))
            .border(1.dp, GitHub.Green1, RoundedCornerShape(4.dp))
            .padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 4.dp else 2.dp),
        color = GitHub.Green3,
        fontFamily = FontFamily.Monospace,
        fontSize = if (large) 16.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.12.em,
        maxLines = 1,
    )
}

/**
 * Renders [text] as the same cell grid into an android.graphics.Bitmap that fits inside
 * [maxWidthPx] x [maxHeightPx]. Used by the home screen widget, which cannot run Compose or
 * load custom fonts reliably, so it shows this image instead.
 */
fun renderPixelBitmap(text: String, maxWidthPx: Int, maxHeightPx: Int, lit: Int, dim: Int): android.graphics.Bitmap {
    val cols = pixelColumns(text).coerceAtLeast(1)
    val cell = minOf(
        maxWidthPx / (cols + (cols - 1) * GAP_RATIO),
        maxHeightPx / (ROWS + (ROWS - 1) * GAP_RATIO),
    ).coerceAtLeast(1f)
    val gap = cell * GAP_RATIO
    val pitch = cell + gap
    val w = kotlin.math.ceil(cols * cell + (cols - 1) * gap).toInt().coerceAtLeast(1)
    val h = kotlin.math.ceil(ROWS * cell + (ROWS - 1) * gap).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val radius = cell * 0.18f
    val rect = android.graphics.RectF()

    fun square(c: Int, r: Int, color: Int) {
        rect.set(c * pitch, r * pitch, c * pitch + cell, r * pitch + cell)
        paint.color = color
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    for (r in 0 until ROWS) for (c in 0 until cols) square(c, r, dim)
    var col = 0
    text.forEachIndexed { i, ch ->
        val pattern = PATTERNS[ch] ?: PATTERNS.getValue(' ')
        pattern.forEachIndexed { r, line ->
            line.forEachIndexed { c, px -> if (px == '#') square(col + c, r, lit) }
        }
        col += pattern[0].length
        if (i < text.lastIndex) col += 1
    }
    return bitmap
}
