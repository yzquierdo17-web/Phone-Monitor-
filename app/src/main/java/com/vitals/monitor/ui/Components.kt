package com.vitals.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitals.monitor.data.Snapshot
import kotlin.math.abs

val Good = Color(0xFF4ADE80)
val Warn = Color(0xFFFBBF24)
val Hot = Color(0xFFF87171)

fun tempColor(c: Float) = when {
    c >= 42f -> Hot
    c >= 37f -> Warn
    else -> Good
}

fun loadColor(frac: Float) = when {
    frac >= 0.85f -> Hot
    frac >= 0.6f -> Warn
    else -> Good
}

/** Rolling 2-minute history for graphs. */
class History(private val cap: Int = 120) {
    val watts = mutableStateListOf<Float>()
    val temp = mutableStateListOf<Float>()
    val cpu = mutableStateListOf<Float>()
    val ram = mutableStateListOf<Float>()
    val down = mutableStateListOf<Float>()
    val up = mutableStateListOf<Float>()

    fun add(s: Snapshot) {
        push(watts, s.battery.watts)
        push(temp, s.battery.tempC)
        push(cpu, s.cpuPct)
        push(ram, s.mem.usedFrac * 100f)
        push(down, s.net.downBps.toFloat())
        push(up, s.net.upBps.toFloat())
    }

    private fun push(l: SnapshotStateList<Float>, v: Float) {
        l.add(v)
        if (l.size > cap) l.removeAt(0)
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun KV(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
fun BigStat(value: String, caption: String, color: Color = Color.Unspecified) {
    Column {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Meter(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun Sparkline(values: List<Float>, color: Color = MaterialTheme.colorScheme.primary, minY: Float? = null) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp),
    ) {
        if (values.size < 2) return@Canvas
        val lo = minY ?: values.min()
        val rawHi = values.max()
        val span = (rawHi - lo).let { if (it < 1e-6f) maxOf(abs(rawHi), 1f) else it }
        val hi = rawHi + span * 0.15f // headroom so flat/max values don't hug the top edge
        val step = size.width / (values.size - 1)
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v - lo) / (hi - lo) * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(width = 2.dp.toPx()))
    }
}
