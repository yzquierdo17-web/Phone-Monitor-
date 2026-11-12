package com.vitals.monitor.overlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vitals.monitor.MainActivity
import com.vitals.monitor.R
import com.vitals.monitor.data.Sampler
import com.vitals.monitor.data.Snapshot
import com.vitals.monitor.util.f1
import com.vitals.monitor.util.speed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Floating, draggable stats HUD. Tap it to switch compact/full. */
class OverlayService : Service() {

    companion object {
        var running by mutableStateOf(false)
        const val ACTION_STOP = "com.vitals.monitor.STOP_HUD"
        private const val CHANNEL = "hud"
        private const val NOTIF_ID = 7
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wm: WindowManager? = null
    private var view: TextView? = null
    private var compact = false
    private var last: Snapshot? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Stats HUD", NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Vitals HUD is on")
            .setContentText("Drag to move · tap to resize")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        running = true

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addOverlay()

        scope.launch {
            val sampler = Sampler(this@OverlayService)
            while (isActive) {
                val snap = withContext(Dispatchers.IO) { sampler.sample() }
                last = snap
                render()
                delay(1000)
            }
        }
    }

    private fun render() {
        val s = last ?: return
        val b = s.battery
        val bolt = if (b.charging) "+" else "−"
        view?.text = if (compact) {
            "${s.cpuPct.toInt()}% · ${f1(b.tempC)}° · $bolt${f1(b.watts)}W"
        } else buildString {
            appendLine("CPU  ${s.cpuPct.toInt()}%  ${f1(s.topCoreGhz)} GHz")
            appendLine("BAT  ${b.level}%  ${f1(b.tempC)}°C  $bolt${f1(b.watts)} W")
            appendLine("RAM  ${(s.mem.usedFrac * 100).toInt()}%  ${s.mem.usedB shr 20} MB")
            appendLine("NET  ↓${speed(s.net.downBps)}  ↑${speed(s.net.upBps)}")
            append("HEAT ${s.thermal.status}")
            s.thermal.headroom?.let { append("  ${(it * 100).toInt()}%") }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val wm = getSystemService(WindowManager::class.java).also { this.wm = it }
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.WHITE)
            val pad = (8 * density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(0xB3000000.toInt())
            }
            text = "Vitals…"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (120 * density).toInt()
        }

        var startX = 0; var startY = 0
        var downX = 0f; var downY = 0f
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; downX = e.rawX; downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(e.rawX - downX) < 10 && abs(e.rawY - downY) < 10) {
                        compact = !compact
                        render()
                    }
                }
            }
            true
        }
        wm.addView(tv, lp)
        view = tv
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        view?.let { runCatching { wm?.removeView(it) } }
        view = null
        super.onDestroy()
    }
}
