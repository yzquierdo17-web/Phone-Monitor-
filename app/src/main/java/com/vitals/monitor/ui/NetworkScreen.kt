package com.vitals.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitals.monitor.data.Snapshot
import com.vitals.monitor.util.bytes
import com.vitals.monitor.util.speed

private fun bars(level: Int?) = level?.let { "▮".repeat(it) + "▯".repeat((4 - it).coerceAtLeast(0)) } ?: "—"

private fun rssiColor(r: Int) = when {
    r >= -60 -> Good
    r >= -72 -> Warn
    else -> Hot
}

@Composable
fun NetworkScreen(s: Snapshot, h: History) {
    val n = s.net
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Section("Live traffic") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("↓ ${speed(n.downBps)}", "download")
                    BigStat("↑ ${speed(n.upBps)}", "upload")
                }
                Text("Download", style = MaterialTheme.typography.labelSmall)
                Sparkline(h.down, color = Good, minY = 0f)
                Text("Upload", style = MaterialTheme.typography.labelSmall)
                Sparkline(h.up, color = Warn, minY = 0f)
                KV("Since boot ↓", bytes(n.totalRx))
                KV("Since boot ↑", bytes(n.totalTx))
            }
        }
        item {
            Section("Connection") {
                KV("Type", n.transport)
                KV("Internet", if (n.validated) "Working" else "Not validated", if (n.validated) Good else Hot)
                KV("Metered", if (n.metered) "Yes" else "No")
                KV("Est. link down", n.linkDownKbps?.let { "${it / 1000} Mbps" } ?: "—")
                KV("Est. link up", n.linkUpKbps?.let { "${it / 1000} Mbps" } ?: "—")
            }
        }
        if (n.wifiRssi != null || n.wifiLinkMbps != null) {
            item {
                Section("Wi‑Fi") {
                    n.wifiRssi?.let { KV("Signal", "$it dBm", rssiColor(it)) }
                    KV("Link speed", n.wifiLinkMbps?.let { "$it Mbps" } ?: "—")
                    KV(
                        "Band",
                        n.wifiFreqMhz?.let {
                            val band = when {
                                it >= 5925 -> "6 GHz"
                                it >= 4900 -> "5 GHz"
                                else -> "2.4 GHz"
                            }
                            "$band ($it MHz)"
                        } ?: "—",
                    )
                }
            }
        }
        item {
            Section("Cellular") {
                KV("Bars", bars(n.cellLevel))
                KV("Signal", n.cellDbm?.let { "$it dBm" } ?: "—")
            }
        }
        item {
            Text(
                "Per‑app data usage is in the Apps tab.",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
