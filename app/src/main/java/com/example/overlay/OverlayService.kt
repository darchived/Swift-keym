package com.example.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.customcontrols.CogwheelMenuDialog
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.ExitConfirmDialog
import com.example.customcontrols.utils.ControlUtils
import com.example.input.InputBridge
import com.example.input.LwjglGlfwKeycode
import com.example.input.ShizukuHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * Non-blocking TYPE_APPLICATION_OVERLAY service:
 * - Attaches each button, drawer, sub-button, and joystick as its own individual small
 *   WindowManager window (no full-screen root container, 100% pass-through on empty space).
 * - Hosts a toggleable right-half 3D Aim Swipe Zone (OFF by default) layered below buttons.
 * - Hosts a persistent draggable Floating Eye Button to hide/show the entire HUD with one tap.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_RELOAD_LAYOUT = "com.example.overlay.ACTION_RELOAD_LAYOUT"
        const val ACTION_UPDATE_SENSITIVITY = "com.example.overlay.ACTION_UPDATE_SENSITIVITY"

        private const val CHANNEL_ID = "swift_keym_overlay_channel"
        private const val NOTIF_ID = 4102

        private const val BASE_WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var windowManager: WindowManager
    private var controlLayout: ControlLayout? = null

    private var screenWidthPx = 1920
    private var screenHeightPx = 1080

    // Individual control windows & their desired sizes when visible
    private val controlWindows = LinkedHashMap<View, WindowManager.LayoutParams>()
    private val controlDesiredSizes = HashMap<View, Pair<Int, Int>>()
    private val controlLogicalVisible = HashMap<View, Boolean>()

    // Bottom-layer 3D Aim Swipe Zone (right half, OFF by default)
    private var aimZoneView: AimSwipeZoneView? = null
    private var aimZoneParams: WindowManager.LayoutParams? = null
    private var isAimEnabled = false

    // Top-layer floating bar (Aim Toggle, Undo, Cogwheel, Redo)
    private var topBarRoot: LinearLayout? = null
    private var topBarParams: WindowManager.LayoutParams? = null
    private var aimToggleBtn: ImageButton? = null
    private var undoPillBtn: ImageButton? = null
    private var redoPillBtn: ImageButton? = null

    // Persistent draggable Floating Eye Button (Show / Hide GUI)
    private var eyeButton: ImageButton? = null
    private var eyeParams: WindowManager.LayoutParams? = null

    private var isHudHidden = false

    private val windowOverlayHost = object : ControlLayout.WindowOverlayHost {
        override fun getOverlayScreenWidth(): Int = screenWidthPx

        override fun getOverlayScreenHeight(): Int = screenHeightPx

        override fun attachControlWindow(
            view: View,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            visible: Boolean
        ) {
            val safeW = width.coerceAtLeast(24)
            val safeH = height.coerceAtLeast(24)
            controlDesiredSizes[view] = safeW to safeH
            controlLogicalVisible[view] = visible

            val actuallyVisible = visible && !isHudHidden
            val params = WindowManager.LayoutParams(
                if (actuallyVisible) safeW else 0,
                if (actuallyVisible) safeH else 0,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                if (actuallyVisible) BASE_WINDOW_FLAGS else (BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            view.visibility = if (actuallyVisible) View.VISIBLE else View.GONE
            if (controlWindows.containsKey(view)) {
                controlWindows[view] = params
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Throwable) {
                }
            } else {
                controlWindows[view] = params
                try {
                    windowManager.addView(view, params)
                } catch (_: Throwable) {
                }
                bringFloatingChromeToFront()
            }
        }

        override fun updateControlWindow(
            view: View,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            visible: Boolean
        ) {
            val params = controlWindows[view] ?: return
            if (x != Int.MIN_VALUE) params.x = x
            if (y != Int.MIN_VALUE) params.y = y
            if (width > 0 && height > 0) {
                controlDesiredSizes[view] = width to height
            }
            controlLogicalVisible[view] = visible

            val actuallyVisible = visible && !isHudHidden
            val desired = controlDesiredSizes[view] ?: (params.width.coerceAtLeast(24) to params.height.coerceAtLeast(24))
            if (actuallyVisible) {
                params.width = desired.first
                params.height = desired.second
                params.flags = BASE_WINDOW_FLAGS
                view.visibility = View.VISIBLE
            } else {
                params.width = 0
                params.height = 0
                params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                view.visibility = View.GONE
            }

            if (view.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Throwable) {
                }
            }
        }

        override fun detachControlWindow(view: View) {
            controlWindows.remove(view)
            controlDesiredSizes.remove(view)
            controlLogicalVisible.remove(view)
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }

        override fun getControlWindowX(view: View): Int = controlWindows[view]?.x ?: 0

        override fun getControlWindowY(view: View): Int = controlWindows[view]?.y ?: 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()
        ShizukuHelper.init()
        ShizukuHelper.bindInputService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD_LAYOUT -> {
                controlLayout?.loadLayout(ControlLayoutManager.loadActiveLayout(this))
                return START_STICKY
            }
            ACTION_UPDATE_SENSITIVITY -> {
                controlLayout?.aimSensitivity = ControlLayoutManager.getAimSensitivity(this)
                return START_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        if (controlLayout == null) {
            updateScreenDimensions()
            // Layer 1 (Bottom): Toggleable Aim Swipe Zone (OFF by default)
            setupAimSwipeZoneWindow()
            // Layer 2 (Middle): Individual Control Windows via ControlLayout WindowOverlayHost
            setupDetachedControlLayout()
            // Layer 3 (Top): Top Floating Bar & Persistent Floating Eye Button
            setupTopFloatingBar()
            setupFloatingEyeButton()
        }
        _isRunning.value = true
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        updateAimZoneWindowLayout()
        controlLayout?.relayoutAllControls()
    }

    private fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidthPx = bounds.width().coerceAtLeast(1)
            screenHeightPx = bounds.height().coerceAtLeast(1)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            screenWidthPx = dm.widthPixels.coerceAtLeast(1)
            screenHeightPx = dm.heightPixels.coerceAtLeast(1)
        }
    }

    private fun startForegroundServiceNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.overlay_action_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun setupAimSwipeZoneWindow() {
        val zone = AimSwipeZoneView(this)
        val params = WindowManager.LayoutParams(
            0,
            0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidthPx / 2
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        zone.visibility = View.GONE
        aimZoneView = zone
        aimZoneParams = params
        windowManager.addView(zone, params)
    }

    private fun setupDetachedControlLayout() {
        val layout = ControlLayout(this).apply {
            setOverlayWindowMode(true)
            setWindowOverlayHost(windowOverlayHost)
            aimSensitivity = ControlLayoutManager.getAimSensitivity(this@OverlayService)
            setOnHistoryChangedListener { canUndo, canRedo, _ ->
                updateOverlayHistoryButtons(canUndo, canRedo)
            }
            loadLayout(ControlLayoutManager.loadActiveLayout(this@OverlayService))
        }
        controlLayout = layout

        InputBridge.setSpecialActionListener { code, isDown ->
            if (!isDown) return@setSpecialActionListener
            when (code) {
                LwjglGlfwKeycode.SPECIALBTN_TOGGLECTRL -> eyeButton?.post { toggleHudVisibility() }
                LwjglGlfwKeycode.SPECIALBTN_MENU -> topBarRoot?.post { toggleEditMode() }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTopFloatingBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_pill_bar)
            val hp = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
            val vp = ControlUtils.dpToPx(this@OverlayService, 3f).toInt()
            setPadding(hp, vp, hp, vp)
            elevation = ControlUtils.dpToPx(this@OverlayService, 10f)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = -ControlUtils.dpToPx(this@OverlayService, 26f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val btnSz = ControlUtils.dpToPx(this@OverlayService, 34f).toInt()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val dragListener = object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            moved = true
                        }
                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            try {
                                windowManager.updateViewLayout(bar, params)
                            } catch (_: Throwable) {
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        }

        // Aim Look Toggle Button (OFF by default)
        val aimBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_crosshair)
            contentDescription = "Toggle Aim Look Swipe Zone"
            setPadding(6, 6, 6, 6)
            setOnClickListener { toggleAimMode() }
            setOnTouchListener(dragListener)
        }
        aimToggleBtn = aimBtn
        styleAimToggleButton(aimBtn, isAimEnabled)

        val undoBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_undo)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.editor_undo)
            visibility = View.GONE
            setPadding(4, 4, 4, 4)
            setOnClickListener { controlLayout?.undo() }
        }
        undoPillBtn = undoBtn

        val cogBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Open Swift KeyM Cogwheel Menu"
            setPadding(4, 4, 4, 4)
            setOnClickListener { openCogwheelMenu() }
            setOnTouchListener(dragListener)
        }

        val redoBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_redo)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.editor_redo)
            visibility = View.GONE
            setPadding(4, 4, 4, 4)
            setOnClickListener { controlLayout?.redo() }
        }
        redoPillBtn = redoBtn

        val aimLp = LinearLayout.LayoutParams(btnSz, btnSz).apply {
            marginEnd = ControlUtils.dpToPx(this@OverlayService, 4f).toInt()
        }
        bar.addView(aimBtn, aimLp)
        bar.addView(undoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.addView(cogBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.addView(redoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.setOnTouchListener(dragListener)

        topBarRoot = bar
        topBarParams = params
        windowManager.addView(bar, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingEyeButton() {
        val eyeSize = ControlUtils.dpToPx(this, 42f).toInt()
        val eyeBtn = ImageButton(this).apply {
            setBackgroundResource(R.drawable.bg_eye_circle)
            setImageResource(R.drawable.ic_eye_open)
            setColorFilter(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = ControlUtils.dpToPx(this@OverlayService, 9f).toInt()
            setPadding(p, p, p, p)
            contentDescription = "Hide or show Swift KeyM overlay"
            elevation = ControlUtils.dpToPx(this@OverlayService, 12f)
        }

        val params = WindowManager.LayoutParams(
            eyeSize,
            eyeSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = ControlUtils.dpToPx(this@OverlayService, 56f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        eyeBtn.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            moved = true
                        }
                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            try {
                                windowManager.updateViewLayout(eyeBtn, params)
                            } catch (_: Throwable) {
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            toggleHudVisibility()
                        }
                        return true
                    }
                }
                return false
            }
        })

        eyeButton = eyeBtn
        eyeParams = params
        windowManager.addView(eyeBtn, params)
    }

    private fun bringFloatingChromeToFront() {
        val bar = topBarRoot
        val bParams = topBarParams
        if (bar != null && bParams != null && bar.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(bar)
                windowManager.addView(bar, bParams)
            } catch (_: Throwable) {
            }
        }
        val eye = eyeButton
        val eParams = eyeParams
        if (eye != null && eParams != null && eye.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(eye)
                windowManager.addView(eye, eParams)
            } catch (_: Throwable) {
            }
        }
    }

    private fun styleAimToggleButton(btn: ImageButton, enabled: Boolean) {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (enabled) {
                setColor(0xFFFFFFFF.toInt())
                setStroke(1, 0xFFFFFFFF.toInt())
            } else {
                setColor(0xFF2C2C2C.toInt())
                setStroke(1, 0x44FFFFFF)
            }
        }
        btn.background = gd
        btn.setColorFilter(if (enabled) 0xFF1E1E1E.toInt() else 0xFFA8A8A8.toInt())
    }

    private fun toggleAimMode() {
        if (isHudHidden) return
        setAimEnabled(!isAimEnabled)
        Toast.makeText(
            this,
            if (isAimEnabled) "Aim Look ON (Right Swipe Zone)" else "Aim Look OFF (Menu Touch Pass-Thru)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setAimEnabled(enabled: Boolean) {
        isAimEnabled = enabled && !isHudHidden
        aimToggleBtn?.let { styleAimToggleButton(it, isAimEnabled) }
        updateAimZoneWindowLayout()
    }

    private fun updateAimZoneWindowLayout() {
        val zone = aimZoneView ?: return
        val params = aimZoneParams ?: return
        val active = isAimEnabled && !isHudHidden
        if (active) {
            params.x = screenWidthPx / 2
            params.y = 0
            params.width = (screenWidthPx - params.x).coerceAtLeast(1)
            params.height = screenHeightPx
            params.flags = BASE_WINDOW_FLAGS
            zone.visibility = View.VISIBLE
        } else {
            zone.resetPointer()
            params.width = 0
            params.height = 0
            params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            zone.visibility = View.GONE
        }
        if (zone.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(zone, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun openCogwheelMenu() {
        val hud = controlLayout ?: return
        CogwheelMenuDialog(
            this,
            hud,
            true,
            object : CogwheelMenuDialog.HostActions {
                override fun onToggleEditMode() = toggleEditMode()

                override fun onToggleHideHud() = toggleHudVisibility()

                override fun onAdjustAimSensitivity(delta: Float) {
                    val next = ControlUtils.clamp(hud.aimSensitivity + delta, 0.2f, 5.0f)
                    hud.aimSensitivity = next
                    ControlLayoutManager.setAimSensitivity(this@OverlayService, next)
                }

                override fun onExportControlMap() {
                    val activeName = ControlLayoutManager.getActiveLayoutName(this@OverlayService)
                    val json = hud.layout.toJson()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(activeName, json))
                    ControlLayoutManager.saveActiveLayout(this@OverlayService, hud.layout)
                    hud.markSaved()
                    Toast.makeText(
                        this@OverlayService,
                        "Saved to controlmap/$activeName & copied JSON",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onExitHost() {
                    ExitConfirmDialog.show(
                        this@OverlayService,
                        hud.hasUnsavedChanges(),
                        true,
                        object : ExitConfirmDialog.Callback {
                            override fun onSaveAndExit() {
                                ControlLayoutManager.saveActiveLayout(this@OverlayService, hud.layout)
                                hud.markSaved()
                                stopSelf()
                            }

                            override fun onContinueExit() {
                                stopSelf()
                            }
                        }
                    )
                }

                override fun isOverlayHost(): Boolean = true

                override fun isHudHidden(): Boolean = isHudHidden
            }
        ).show()
    }

    private fun toggleEditMode() {
        val hud = controlLayout ?: return
        if (isHudHidden) toggleHudVisibility()
        hud.isModifiable = !hud.isModifiable
        if (hud.isModifiable && isAimEnabled) {
            setAimEnabled(false)
        }
        updateOverlayHistoryButtons(hud.canUndo(), hud.canRedo())
    }

    private fun updateOverlayHistoryButtons(canUndo: Boolean, canRedo: Boolean) {
        val editing = controlLayout?.isModifiable == true && !isHudHidden
        undoPillBtn?.apply {
            visibility = if (editing) View.VISIBLE else View.GONE
            isEnabled = canUndo
            alpha = if (canUndo) 1.0f else 0.32f
        }
        redoPillBtn?.apply {
            visibility = if (editing) View.VISIBLE else View.GONE
            isEnabled = canRedo
            alpha = if (canRedo) 1.0f else 0.32f
        }
    }

    private fun toggleHudVisibility() {
        val hud = controlLayout ?: return
        isHudHidden = !isHudHidden

        if (isHudHidden) {
            hud.isModifiable = false
            setAimEnabled(false)
            hud.releaseAllActiveTouches()
            InputBridge.resetAll()

            // Hide all individual button/joystick/drawer windows
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            // Hide top floating bar so only the small Eye Button remains
            topBarRoot?.let { bar ->
                topBarParams?.let { bp ->
                    bp.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    bar.visibility = View.GONE
                    if (bar.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(bar, bp)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            eyeButton?.setImageResource(R.drawable.ic_eye_off)
        } else {
            // Restore individual control windows according to their logical visibility
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            topBarRoot?.let { bar ->
                topBarParams?.let { bp ->
                    bp.flags = BASE_WINDOW_FLAGS
                    bar.visibility = View.VISIBLE
                    if (bar.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(bar, bp)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            eyeButton?.setImageResource(R.drawable.ic_eye_open)
            updateOverlayHistoryButtons(hud.canUndo(), hud.canRedo())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        InputBridge.setSpecialActionListener(null)
        InputBridge.resetAll()

        controlLayout?.releaseAllActiveTouches()

        for (view in ArrayList(controlWindows.keys)) {
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }
        controlWindows.clear()
        controlDesiredSizes.clear()
        controlLogicalVisible.clear()

        aimZoneView?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }
        topBarRoot?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }
        eyeButton?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }

        aimZoneView = null
        topBarRoot = null
        eyeButton = null
        controlLayout = null
    }

    /**
     * Right-half invisible 3D Aim Swipe Zone layered beneath individual buttons.
     * Active only when Aim Look is toggled ON.
     */
    private inner class AimSwipeZoneView(context: Context) : View(context) {
        private var activePointerId = MotionEvent.INVALID_POINTER_ID
        private var lastX = 0f
        private var lastY = 0f
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            strokeWidth = ControlUtils.dpToPx(context, 1f)
        }

        fun resetPointer() {
            activePointerId = MotionEvent.INVALID_POINTER_ID
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Subtle hairline along the left edge of the aim zone when Aim Look is ON
            canvas.drawLine(0f, 0f, 0f, height.toFloat(), edgePaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isAimEnabled || isHudHidden) return false

            val sensitivity = controlLayout?.aimSensitivity ?: 1.2f
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = event.actionIndex
                    if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                        activePointerId = event.getPointerId(idx)
                        lastX = event.getX(idx)
                        lastY = event.getY(idx)
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                        val idx = event.findPointerIndex(activePointerId)
                        if (idx >= 0) {
                            val currX = event.getX(idx)
                            val currY = event.getY(idx)
                            val dx = currX - lastX
                            val dy = currY - lastY
                            lastX = currX
                            lastY = currY
                            if (dx != 0f || dy != 0f) {
                                InputBridge.sendMouseMove(dx * sensitivity, dy * sensitivity)
                            }
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val idx = event.actionIndex
                    if (event.getPointerId(idx) == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }
            }
            return true
        }
    }
}
