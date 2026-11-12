package com.vitals.monitor.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import java.io.File
import kotlin.math.abs

data class CoreFreq(val index: Int, val curKhz: Long, val maxKhz: Long) {
    val online: Boolean get() = curKhz > 0
    val pct: Float get() = if (maxKhz > 0) curKhz * 100f / maxKhz else 0f
}

data class BatteryInfo(
    val level: Int,
    val status: String,
    val charging: Boolean,
    val plugged: String,
    val health: String,
    val tempC: Float,
    val voltageMv: Int,
    val currentMa: Int,
    val watts: Float,
    val technology: String,
    val cycleCount: Int?,
    val chargeMah: Int?,
    val estFullMah: Int?,
)

data class MemInfo(val totalB: Long, val availB: Long, val low: Boolean, val thresholdB: Long) {
    val usedB: Long get() = totalB - availB
    val usedFrac: Float get() = if (totalB > 0) usedB.toFloat() / totalB else 0f
}

data class StorageInfo(val totalB: Long, val freeB: Long) {
    val usedFrac: Float get() = if (totalB > 0) (totalB - freeB).toFloat() / totalB else 0f
}

data class ThermalInfo(
    val status: String,
    val level: Int,
    val headroom: Float?,
    val zones: List<Pair<String, Float>>,
)

data class NetInfo(
    val transport: String,
    val validated: Boolean,
    val metered: Boolean,
    val downBps: Long,
    val upBps: Long,
    val totalRx: Long,
    val totalTx: Long,
    val linkDownKbps: Int?,
    val linkUpKbps: Int?,
    val wifiRssi: Int?,
    val wifiLinkMbps: Int?,
    val wifiFreqMhz: Int?,
    val cellLevel: Int?,
    val cellDbm: Int?,
)

data class Snapshot(
    val timeMs: Long,
    val battery: BatteryInfo,
    val cores: List<CoreFreq>,
    val mem: MemInfo,
    val storage: StorageInfo,
    val thermal: ThermalInfo,
    val net: NetInfo,
) {
    /** Average clock as % of each core's max. Android blocks /proc/stat, so this is the best public proxy for load. */
    val cpuPct: Float
        get() = cores.filter { it.maxKhz > 0 }.map { it.pct }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    val topCoreGhz: Float get() = (cores.maxOfOrNull { it.curKhz } ?: 0L) / 1_000_000f
}

/** Collects one snapshot of the phone's state. Keep one instance per poll loop (it tracks deltas). */
class Sampler(context: Context) {
    private val ctx = context.applicationContext
    private val bm = ctx.getSystemService(BatteryManager::class.java)
    private val am = ctx.getSystemService(ActivityManager::class.java)
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val wm = ctx.getSystemService(WifiManager::class.java)
    private val tm = ctx.getSystemService(TelephonyManager::class.java)

    private val coreCount = Runtime.getRuntime().availableProcessors()
    private val maxFreqs = LongArray(coreCount) {
        readLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")
    }

    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastT = SystemClock.elapsedRealtime()
    private var lastHeadroom: Float? = null
    private var lastHeadroomAt = 0L

    private val zoneFiles: List<Pair<String, File>> by lazy {
        try {
            File("/sys/class/thermal").listFiles()
                ?.filter { it.name.startsWith("thermal_zone") }
                ?.mapNotNull { z ->
                    val type = runCatching { File(z, "type").readText().trim() }.getOrNull()
                    val temp = File(z, "temp")
                    if (type != null && runCatching { temp.readText() }.isSuccess) type to temp else null
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun sample(): Snapshot = Snapshot(
        timeMs = System.currentTimeMillis(),
        battery = battery(),
        cores = cores(),
        mem = memory(),
        storage = storage(),
        thermal = thermal(),
        net = network(),
    )

    private fun battery(): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLevel = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) rawLevel * 100 / scale else -1
        val statusCode = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 -> "Dock"
            else -> "Unplugged"
        }
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            (i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1).takeIf { it >= 0 }
        } else null

        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = when {
            rawCurrent == Int.MIN_VALUE -> 0
            abs(rawCurrent) > 20_000 -> rawCurrent / 1000 // µA (Pixel) -> mA
            else -> rawCurrent
        }
        val watts = abs(currentMa) * voltage / 1_000_000f

        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeMah = if (counter > 0 && counter != Int.MIN_VALUE) counter / 1000 else null
        val estFull = if (chargeMah != null && level > 0) chargeMah * 100 / level else null

        return BatteryInfo(
            level, status,
            statusCode == BatteryManager.BATTERY_STATUS_CHARGING,
            plugged, health, tempC, voltage, currentMa, watts, tech, cycles, chargeMah, estFull,
        )
    }

    private fun cores(): List<CoreFreq> = (0 until coreCount).map {
        CoreFreq(it, readLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq"), maxFreqs[it])
    }

    private fun memory(): MemInfo {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemInfo(mi.totalMem, mi.availMem, mi.lowMemory, mi.threshold)
    }

    private fun storage(): StorageInfo {
        val s = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(s.totalBytes, s.availableBytes)
    }

    private fun thermal(): ThermalInfo {
        val level = pm.currentThermalStatus
        val name = when (level) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe (throttling)"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
        // getThermalHeadroom is rate-limited; ask at most every 2s and keep the last good value.
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeadroomAt > 2000) {
            lastHeadroomAt = now
            val h = runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
            if (!h.isNaN()) lastHeadroom = h
        }
        val zones = zoneFiles.mapNotNull { (type, f) ->
            val raw = runCatching { f.readText().trim().toFloat() }.getOrNull() ?: return@mapNotNull null
            val c = if (abs(raw) > 1000) raw / 1000f else raw
            if (c in 1f..150f) type to c else null
        }.sortedByDescending { it.second }
        return ThermalInfo(name, level, lastHeadroom, zones)
    }

    @Suppress("DEPRECATION")
    private fun network(): NetInfo {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dt = (now - lastT).coerceAtLeast(1)
        val down = (rx - lastRx).coerceAtLeast(0) * 1000 / dt
        val up = (tx - lastTx).coerceAtLeast(0) * 1000 / dt
        lastRx = rx; lastTx = tx; lastT = now

        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wi = if (isWifi) runCatching { wm.connectionInfo }.getOrNull() else null

        val ss = runCatching { tm.signalStrength }.getOrNull()
        val cellDbm = ss?.cellSignalStrengths?.firstOrNull()?.dbm?.takeIf { it != Int.MAX_VALUE }

        return NetInfo(
            transport = transport,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            downBps = down,
            upBps = up,
            totalRx = rx,
            totalTx = tx,
            linkDownKbps = caps?.linkDownstreamBandwidthKbps,
            linkUpKbps = caps?.linkUpstreamBandwidthKbps,
            wifiRssi = wi?.rssi?.takeIf { it > -127 },
            wifiLinkMbps = wi?.linkSpeed?.takeIf { it > 0 },
            wifiFreqMhz = wi?.frequency?.takeIf { it > 0 },
            cellLevel = ss?.level,
            cellDbm = cellDbm,
        )
    }

    private fun readLong(path: String): Long =
        try { File(path).readText().trim().toLong() } catch (e: Exception) { 0L }
}
