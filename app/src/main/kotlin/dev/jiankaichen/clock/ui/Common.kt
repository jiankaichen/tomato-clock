package dev.jiankaichen.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jiankaichen.clock.data.Session
import kotlinx.coroutines.delay

fun formatMs(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Remaining time that re-evaluates a few times per second while the session is running. */
@Composable
fun rememberRemaining(session: Session?): Long {
    var remaining by remember(session) { mutableLongStateOf(session?.remainingNow() ?: 0L) }
    LaunchedEffect(session) {
        while (session != null && session.running) {
            remaining = session.remainingNow()
            delay(200)
        }
    }
    return remaining
}

val Tabular: TextStyle
    @Composable get() = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum", fontSize = 72.sp)

@Composable
fun CountdownDial(session: Session, modifier: Modifier = Modifier, subtitle: String = session.label) {
    val remaining = rememberRemaining(session)
    val progress = if (session.totalMs > 0) (remaining.toFloat() / session.totalMs).coerceIn(0f, 1f) else 0f
    val ring = if (session.paused) GitHub.Green1 else GitHub.Green3
    val track = GitHub.Surface

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(ring, -90f, 360f * progress, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelDigits(
                formatMs(remaining),
                Modifier.fillMaxWidth(0.62f),
                lit = if (session.paused) GitHub.Green1 else GitHub.Green3,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (session.paused) "$subtitle · paused" else subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
