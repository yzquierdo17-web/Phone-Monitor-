package com.vitals.monitor.util

import java.util.Locale

fun bytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

fun speed(bps: Long): String = bytes(bps) + "/s"

fun duration(ms: Long): String {
    val s = ms / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s % 60}s"
        else -> "${s}s"
    }
}

fun f1(x: Float): String = String.format(Locale.US, "%.1f", x)
fun f2(x: Float): String = String.format(Locale.US, "%.2f", x)
