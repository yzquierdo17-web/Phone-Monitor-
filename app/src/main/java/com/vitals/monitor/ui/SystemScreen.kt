package com.vitals.monitor.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitals.monitor.data.Snapshot
import com.vitals.monitor.data.SystemInfo
import com.vitals.monitor.util.duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SystemScreen(s: Snapshot) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { SystemInfo.collect(ctx) } }

    // s.timeMs changes every second, which keeps these live values ticking.
    val total = SystemClock.elapsedRealtime()
    val awake = SystemClock.uptimeMillis()
    val asleepPct = if (total > 0) ((total - awake) * 100 / total) else 0

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Section("Uptime") {
                KV("Since boot", duration(total))
                KV("CPU awake", duration(awake))
                KV("Deep sleep", "$asleepPct%", if (asleepPct >= 70) Good else Warn)
                KV("Sampled", java.text.DateFormat.getTimeInstance().format(s.timeMs))
            }
        }
        item {
            Section("Device") {
                info.forEach { (k, v) -> KV(k, v) }
            }
        }
    }
}
