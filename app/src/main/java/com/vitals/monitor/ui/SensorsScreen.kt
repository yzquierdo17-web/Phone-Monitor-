package com.vitals.monitor.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

private fun unitFor(type: Int) = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY,
    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "m/s²"
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    Sensor.TYPE_STEP_COUNTER -> "steps"
    Sensor.TYPE_HEART_RATE -> "bpm"
    else -> ""
}

private fun key(s: Sensor) = "${s.type}|${s.name}|${s.vendor}"

@Composable
fun SensorsScreen() {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(SensorManager::class.java) }
    val sensors = remember { sm.getSensorList(Sensor.TYPE_ALL).sortedBy { it.type } }
    val latest = remember { HashMap<String, FloatArray>() }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                latest[key(e.sensor)] = e.values.clone()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sensors
            .filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT }
            .forEach { runCatching { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) } }
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250); tick++
        }
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "${sensors.size} sensors · live",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        itemsIndexed(sensors) { _, s ->
            val v = if (tick >= 0) latest[key(s)] else null // reading tick refreshes values
            val unit = unitFor(s.type)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.stringType.substringAfterLast('.')} · ${s.vendor}" +
                        (if (s.isWakeUpSensor) " · wake‑up" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        s.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT -> "one‑shot trigger sensor"
                        v == null -> "waiting for data…"
                        else -> v.take(6).joinToString("  ") { String.format(Locale.US, "%.3f", it) } + " $unit"
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "range ${s.maximumRange} · res ${s.resolution} · ${s.power} mA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}
