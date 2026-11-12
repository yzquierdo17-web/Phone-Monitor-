package com.vitals.monitor.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitals.monitor.data.Snapshot
import com.vitals.monitor.overlay.OverlayService
import com.vitals.monitor.util.bytes
import com.vitals.monitor.util.f1
import com.vitals.monitor.util.f2

@Composable
fun OverviewScreen(s: Snapshot, h: History) {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        item {
            Section("Floating HUD") {
                Text(
                    "Live stats on top of any app or game. Drag to move, tap to resize.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                val running = OverlayService.running
                Button(onClick = {
                    when {
                        !Settings.canDrawOverlays(ctx) -> ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}"),
                            ),
                        )
                        running -> ctx.stopService(Intent(ctx, OverlayService::class.java))
                        else -> ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                    }
                }) {
                    Text(
                        when {
                            !Settings.canDrawOverlays(ctx) -> "Allow display over apps"
                            running -> "Stop HUD"
                            else -> "Start HUD"
                        },
                    )
                }
            }
        }

        item {
            val b = s.battery
            Section("Battery") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("${b.level}%", b.status)
                    BigStat("${if (b.charging) "+" else "−"}${f2(b.watts)} W", "power")
                    BigStat("${f1(b.tempC)}°", "temp", tempColor(b.tempC))
                }
                Sparkline(h.watts, minY = 0f)
                KV("Current", "${b.currentMa} mA")
                KV("Voltage", "${f2(b.voltageMv / 1000f)} V")
                KV("Source", b.plugged)
                KV("Health", b.health)
                KV("Cycle count", b.cycleCount?.toString() ?: "Not reported")
                KV("Charge now", b.chargeMah?.let { "$it mAh" } ?: "—")
                KV("Est. full capacity", b.estFullMah?.let { "$it mAh" } ?: "—")
                KV("Chemistry", b.technology)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                }) { Text("Per‑app battery use (Android)") }
            }
        }

        item {
            Section("CPU") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("${s.cpuPct.toInt()}%", "avg clock vs max", loadColor(s.cpuPct / 100f))
                    BigStat("${f2(s.topCoreGhz)}", "top core GHz")
                }
                Sparkline(h.cpu, color = loadColor(s.cpuPct / 100f), minY = 0f)
                s.cores.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("cpu${c.index}", Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.weight(1f).padding(top = 6.dp)) {
                            Meter(c.pct / 100f, loadColor(c.pct / 100f))
                        }
                        Text(
                            if (c.online) "${c.curKhz / 1000} MHz" else "offline",
                            Modifier.width(84.dp).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    "Android doesn't let apps read true CPU % since Android 8, so this shows clock speed relative to each core's max — the best public proxy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            val t = s.thermal
            Section("Thermals") {
                KV("Status", t.status, if (t.level >= 3) Hot else if (t.level >= 1) Warn else Good)
                KV(
                    "Headroom",
                    t.headroom?.let { "${(it * 100).toInt()}% of throttle limit" } ?: "—",
                )
                Sparkline(h.temp, color = tempColor(s.battery.tempC))
                if (t.zones.isEmpty()) {
                    Text(
                        "Per‑sensor zones are hidden on this build; battery temp + thermal status shown above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    t.zones.take(12).forEach { (name, c) -> KV(name, "${f1(c)} °C", tempColor(c)) }
                }
            }
        }

        item {
            val m = s.mem
            Section("Memory") {
                KV("Used", "${bytes(m.usedB)} / ${bytes(m.totalB)}")
                Meter(m.usedFrac, loadColor(m.usedFrac))
                Sparkline(h.ram, color = loadColor(m.usedFrac), minY = 0f)
                KV("Available", bytes(m.availB))
                KV("Low‑memory flag", if (m.low) "Yes" else "No", if (m.low) Hot else Good)
                KV("Kill threshold", bytes(m.thresholdB))
            }
        }

        item {
            val st = s.storage
            Section("Storage") {
                KV("Used", "${bytes(st.totalB - st.freeB)} / ${bytes(st.totalB)}")
                Meter(st.usedFrac, loadColor(st.usedFrac))
                KV("Free", bytes(st.freeB))
            }
        }
    }
}
