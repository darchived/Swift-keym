package com.example.vision

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.customcontrols.ControlLayoutManager
import com.example.input.InputBridge
import kotlin.math.roundToInt

/**
 * Dedicated foreground MediaProjection service (Android 14+ compliant) for
 * real-time AI screen reading & enemy red nametag tracking.
 */
class ScreenAssistService : Service() {

    companion object {
        const val ACTION_START_CAPTURE = "com.example.vision.ACTION_START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.vision.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "swift_keym_ai_vision_channel"
        private const val NOTIF_ID = 4109

        @Volatile
        var steerSink: ((Float, Float) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var analyzer: AiAimVisionAnalyzer? = null
    private var reusableBitmap: Bitmap? = null

    private var captureWidth = 960
    private var captureHeight = 540
    private var screenDensityDpi = 320

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            AiVisionTerminalBus.appendLog("SYSTEM: Screen capture session ended.")
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            super.onCapturedContentResize(width, height)
            if (width > 0 && height > 0) {
                val aspect = width.toFloat() / height.toFloat()
                val nextW = 960
                val nextH = (nextW / aspect).roundToInt().coerceIn(360, 1080)
                if (nextW != captureWidth || nextH != captureHeight) {
                    captureWidth = nextW
                    captureHeight = nextH
                    recreateReaderSurface()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPTURE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startMediaProjectionForeground()
        startCapturePipeline(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startMediaProjectionForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Swift KeyM AI Vision",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, ScreenAssistService::class.java).apply { action = ACTION_STOP_CAPTURE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Swift KeyM AI Vision Active")
            .setContentText("Scanning center crosshair for hostile red nametags (teammates filtered).")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "Stop Vision", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startCapturePipeline(resultCode: Int, data: Intent) {
        teardownCapture()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (fullW, fullH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width().coerceAtLeast(1) to b.height().coerceAtLeast(1)
        } else {
            resources.displayMetrics.widthPixels.coerceAtLeast(1) to resources.displayMetrics.heightPixels.coerceAtLeast(1)
        }
        screenDensityDpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)

        // Downscale capture to max 960px wide for ultra-fast 30fps on-device analysis
        val scale = (960f / maxOf(fullW, fullH).toFloat()).coerceAtMost(1.0f)
        captureWidth = (fullW * scale).roundToInt().coerceAtLeast(320)
        captureHeight = (fullH * scale).roundToInt().coerceAtLeast(240)

        val thread = HandlerThread("SwiftKeyM-AiVision").apply { start() }
        captureThread = thread
        val handler = Handler(thread.looper)
        captureHandler = handler

        analyzer = AiAimVisionAnalyzer(this) { dx, dy ->
            val sink = steerSink
            if (sink != null) {
                sink.invoke(dx, dy)
            } else {
                InputBridge.sendMouseMove(dx, dy)
            }
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            AiVisionTerminalBus.appendLog("ERROR: MediaProjection permission returned null.")
            stopSelf()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, handler)

        createReaderAndVirtualDisplay(projection, handler)
        AiVisionTerminalBus.setScreenVisionActive(true)
        val str = ControlLayoutManager.getAiAimStrength(this)
        AiVisionTerminalBus.appendLog(
            "AI VISION ARMED (${captureWidth}x$captureHeight) • Strength=${if (str < 0.5f) "OFF" else String.format("%.1f", str)} • TeamFilter=${if (ControlLayoutManager.isAiTeamFilterEnabled(this)) "ON" else "OFF"}"
        )
    }

    private fun createReaderAndVirtualDisplay(projection: MediaProjection, handler: Handler) {
        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val image = try {
                r.acquireLatestImage()
            } catch (_: Throwable) {
                null
            } ?: return@setOnImageAvailableListener

            try {
                val planes = image.planes
                if (planes.isNotEmpty()) {
                    val plane = planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth
                    val bmpWidth = captureWidth + (rowPadding / pixelStride.coerceAtLeast(1))

                    var bmp = reusableBitmap
                    if (bmp == null || bmp.width != bmpWidth || bmp.height != captureHeight || bmp.isRecycled) {
                        bmp?.recycle()
                        bmp = Bitmap.createBitmap(bmpWidth, captureHeight, Bitmap.Config.ARGB_8888)
                        reusableBitmap = bmp
                    }
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = if (bmpWidth == captureWidth) {
                        bmp
                    } else {
                        Bitmap.createBitmap(bmp, 0, 0, captureWidth, captureHeight)
                    }
                    analyzer?.processBitmap(cropped)
                    if (cropped !== bmp) {
                        cropped.recycle()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                image.close()
            }
        }, handler)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "SwiftKeyM-AiVision",
            captureWidth,
            captureHeight,
            screenDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    private fun recreateReaderSurface() {
        val proj = mediaProjection ?: return
        val handler = captureHandler ?: return
        try {
            virtualDisplay?.release()
            imageReader?.close()
        } catch (_: Throwable) {
        }
        createReaderAndVirtualDisplay(proj, handler)
    }

    private fun teardownCapture() {
        AiVisionTerminalBus.setScreenVisionActive(false)
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Throwable) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        mediaProjection = null

        analyzer?.close()
        analyzer = null

        reusableBitmap?.recycle()
        reusableBitmap = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        teardownCapture()
    }
}
