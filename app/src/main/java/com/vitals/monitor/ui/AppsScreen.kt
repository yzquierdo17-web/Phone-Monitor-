package com.vitals.monitor.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vitals.monitor.data.Apps
import com.vitals.monitor.data.AppsReport
import com.vitals.monitor.util.bytes
import com.vitals.monitor.util.duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf(Apps.hasUsageAccess(ctx)) }
    var report by remember { mutableStateOf<AppsReport?>(null) }
    var sort by remember { mutableIntStateOf(0) }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            access = Apps.hasUsageAccess(ctx)
            if (access) report = withContext(Dispatchers.IO) { Apps.load(ctx) }
        }
    }

    if (!access) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Usage access needed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "To show screen time, unlocks and data used per app, allow Vitals in Usage access. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Text("Open Usage access")
            }
        }
        return
    }

    val r = report
    if (r == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    val list = r.apps
        .filter { showSystem || !it.system || it.foregroundMs > 0 }
        .let { l ->
            when (sort) {
                0 -> l.sortedByDescending { it.foregroundMs }
                1 -> l.sortedByDescending { it.dataBytes }
                else -> l.sortedByDescending { it.sensitivePerms.size }
            }
        }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Section("Today") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat(duration(r.screenTimeMs), "screen time")
                    BigStat("${r.unlocks}", "unlocks")
                    BigStat(bytes(r.apps.sumOf { it.dataBytes }), "data")
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("Screen time") })
                FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("Data") })
                FilterChip(selected = sort == 2, onClick = { sort = 2 }, label = { Text("Permissions") })
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("System") })
            }
        }
        items(list, key = { it.pkg }) { a ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.label,
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(duration(a.foregroundMs), style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text(
                        a.pkg,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "↓${bytes(a.rxBytes)} ↑${bytes(a.txBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (a.sensitivePerms.isNotEmpty()) {
                    Text(
                        a.sensitivePerms.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Warn,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
