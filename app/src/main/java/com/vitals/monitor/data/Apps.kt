package com.vitals.monitor.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar

data class AppStat(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val foregroundMs: Long,
    val lastUsed: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val sensitivePerms: List<String>,
) {
    val dataBytes: Long get() = rxBytes + txBytes
}

data class AppsReport(
    val apps: List<AppStat>,
    val screenTimeMs: Long,
    val unlocks: Int,
)

object Apps {
    private val SENSITIVE = linkedMapOf(
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Mic",
        Manifest.permission.ACCESS_FINE_LOCATION to "Location",
        Manifest.permission.ACCESS_BACKGROUND_LOCATION to "BG location",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.READ_SMS to "SMS",
        Manifest.permission.READ_CALL_LOG to "Call log",
        Manifest.permission.READ_MEDIA_IMAGES to "Photos",
        Manifest.permission.BODY_SENSORS to "Body sensors",
        Manifest.permission.ACTIVITY_RECOGNITION to "Activity",
        Manifest.permission.READ_CALENDAR to "Calendar",
    )

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Heavy: call off the main thread. */
    @Suppress("DEPRECATION")
    fun load(ctx: Context): AppsReport {
        val pm = ctx.packageManager
        val start = startOfToday()
        val end = System.currentTimeMillis()
        val usm = ctx.getSystemService(UsageStatsManager::class.java)

        val usage = runCatching { usm.queryAndAggregateUsageStats(start, end) }.getOrDefault(emptyMap())

        var unlocks = 0
        runCatching {
            val events = usm.queryEvents(start, end)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
            }
        }

        val rxByUid = HashMap<Int, Long>()
        val txByUid = HashMap<Int, Long>()
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            runCatching {
                val stats = nsm.querySummary(type, null, start, end)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    rxByUid[b.uid] = (rxByUid[b.uid] ?: 0L) + b.rxBytes
                    txByUid[b.uid] = (txByUid[b.uid] ?: 0L) + b.txBytes
                }
                stats.close()
            }
        }

        val pkgs: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val launchable = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.toSet()

        val apps = pkgs.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val u = usage[p.packageName]
            val fg = u?.totalTimeInForeground ?: 0L
            val uid = ai.uid
            val rx = rxByUid[uid] ?: 0L
            val tx = txByUid[uid] ?: 0L
            if (p.packageName !in launchable && fg == 0L && rx + tx == 0L) return@mapNotNull null

            val granted = buildList {
                val req = p.requestedPermissions ?: return@buildList
                val flags = p.requestedPermissionsFlags
                req.forEachIndexed { idx, perm ->
                    val isGranted = flags != null &&
                        (flags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    SENSITIVE[perm]?.let { if (isGranted) add(it) }
                }
            }
            AppStat(
                pkg = p.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(p.packageName),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                foregroundMs = fg,
                lastUsed = u?.lastTimeUsed ?: 0L,
                rxBytes = rx,
                txBytes = tx,
                sensitivePerms = granted,
            )
        }

        return AppsReport(apps, apps.sumOf { it.foregroundMs }, unlocks)
    }
}
