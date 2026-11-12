package com.vitals.monitor.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.view.Display

object SystemInfo {

    /** Static device facts. Call off the main thread (GPU query spins up a tiny GL context). */
    fun collect(ctx: Context): List<Pair<String, String>> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val display = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val maxHz = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
        val dpi = ctx.resources.displayMetrics.densityDpi

        return listOf(
            "Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Device / board" to "${Build.DEVICE} / ${Build.BOARD}",
            "SoC" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}",
            "Hardware" to Build.HARDWARE,
            "GPU" to gpu(),
            "CPU cores" to Runtime.getRuntime().availableProcessors().toString(),
            "ABIs" to Build.SUPPORTED_ABIS.joinToString(),
            "RAM" to com.vitals.monitor.util.bytes(mi.totalMem),
            "Display" to (mode?.let { "${it.physicalWidth}×${it.physicalHeight} · ${dpi} dpi" } ?: "—"),
            "Refresh rate" to "now ${display?.refreshRate?.toInt() ?: 0} Hz · max ${maxHz.toInt()} Hz",
            "HDR" to (display?.hdrCapabilities?.supportedHdrTypes?.size?.let { if (it > 0) "Yes ($it types)" else "No" } ?: "—"),
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Security patch" to Build.VERSION.SECURITY_PATCH,
            "Build" to Build.DISPLAY,
            "Kernel" to (System.getProperty("os.version") ?: "—"),
            "Bootloader" to Build.BOOTLOADER,
            "Radio" to (Build.getRadioVersion() ?: "—"),
            "Fingerprint" to Build.FINGERPRINT,
        )
    }

    private fun gpu(): String = try {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(dpy, ver, 0, ver, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(dpy, attribs, 0, configs, 0, 1, num, 0)
        val ctx = EGL14.eglCreateContext(
            dpy, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        val surf = EGL14.eglCreatePbufferSurface(
            dpy, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        val out = "${GLES20.glGetString(GLES20.GL_RENDERER)} · ${GLES20.glGetString(GLES20.GL_VERSION)}"
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(dpy, surf)
        EGL14.eglDestroyContext(dpy, ctx)
        EGL14.eglTerminate(dpy)
        out
    } catch (e: Throwable) {
        "Unknown"
    }
}
