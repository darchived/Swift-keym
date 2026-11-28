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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
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
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.utils.ControlUtils
import com.example.input.GyroscopeController
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
 * - Hosts a dedicated Quick-Aim Floating Chip right beside the draggable Floating Eye Button.
 * - Hosts a top pill bar with Quick-Gyro toggle, Undo/Redo, and Cogwheel Settings Menu.
 * - Runs hardware TYPE_GYROSCOPE aiming + pass-through Center Crosshair overlay.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_RELOAD_LAYOUT = "com.example.overlay.ACTION_RELOAD_LAYOUT"
        const val ACTION_UPDATE_SENSITIVITY = "com.example.overlay.ACTION_UPDATE_SENSITIVITY"
        const val ACTION_UPDATE_OPACITY = "com.example.overlay.ACTION_UPDATE_OPACITY"
        const val ACTION_UPDATE_RIPPLE = "com.example.overlay.ACTION_UPDATE_RIPPLE"
        const val ACTION_UPDATE_GYRO = "com.example.overlay.ACTION_UPDATE_GYRO"
        const val ACTION_UPDATE_CROSSHAIR = "com.example.overlay.ACTION_UPDATE_CROSSHAIR"
        const val ACTION_UPDATE_HAPTICS = "com.example.overlay.ACTION_UPDATE_HAPTICS"
        const val ACTION_UPDATE_LOCK = "com.example.overlay.ACTION_UPDATE_LOCK"

        private const val CHANNEL_ID = "swift_keym_overlay_channel"
        private const val NOTIF_ID = 4102

        private const val BASE_WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var windowManager: WindowManager
    private var controlLayout: ControlLayout? = null
    private var gyroController: GyroscopeController? = null

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

    // Top-layer floating bar (Gyro Toggle, Undo, Cogwheel, Redo)
    private var topBarRoot: LinearLayout? = null
    private var topBarParams: WindowManager.LayoutParams? = null
    private var gyroToggleBtn: ImageButton? = null
    private var undoPillBtn: ImageButton? = null
    private var cogToggleBtn: ImageButton? = null
    private var redoPillBtn: ImageButton? = null

    // Dedicated Quick-Aim Floating Chip placed beside the Floating Eye Button
    private var quickAimButton: ImageButton? = null
    private var quickAimParams: WindowManager.LayoutParams? = null

    // Persistent draggable Floating Eye Button (Show / Hide GUI)
    private var eyeButton: ImageButton? = null
    private var eyeParams: WindowManager.LayoutParams? = null

    // Native WindowManager Floating Settings Drawer (no Dialog/AlertDialog)
    private var settingsDrawer: FloatingSettingsDrawerView? = null
    private var keyboardDrawer: FloatingKeyboardDrawerView? = null

    // Visual-only (strictly FLAG_NOT_TOUCHABLE) Touch Ripple & Center Crosshair windows
    private var touchRippleView: TouchRippleOverlayView? = null
    private var touchRippleParams: WindowManager.LayoutParams? = null
    private var crosshairView: CrosshairOverlayView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null

    private var isHudHidden = false

    private var shootPressStartTime = 0L
    private var lastPrimaryMouseDown = false

    private val antiRecoilHandler = Handler(Looper.getMainLooper())
    private val antiRecoilRunnable = object : Runnable {
        override fun run() {
            val isShooting = InputBridge.isPrimaryMouseDown
            val now = SystemClock.uptimeMillis()
            if (isShooting && !lastPrimaryMouseDown) {
                shootPressStartTime = now
            }
            lastPrimaryMouseDown = isShooting

            if (_isRunning.value && !isHudHidden && controlLayout?.isModifiable != true) {
                val recoilY = ControlLayoutManager.getAimAntiRecoilY(this@OverlayService)
                val recoilX = ControlLayoutManager.getAimAntiRecoilX(this@OverlayService)
                if (isShooting && (recoilY > 0f || recoilX != 0f)) {
                    val isKick = (now - shootPressStartTime) <= 350L
                    val kickBoost = ControlLayoutManager.getAimInitialKickBoost(this@OverlayService)
                    val activeAimZone = aimZoneView
                    if (activeAimZone != null && isAimEnabled) {
                        activeAimZone.addRecoilTick(recoilX, recoilY, kickBoost, isKick)
                    } else {
                        val kickMultiplier = if (isKick) (1.0f + kickBoost) else 1.0f
                        InputBridge.sendMouseMove(recoilX, recoilY * kickMultiplier)
                    }
                }
            }
            antiRecoilHandler.postDelayed(this, 16L)
        }
    }

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

        override fun onTouchFeedback(rawX: Float, rawY: Float) {
            spawnTouchRipple(rawX, rawY)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()
        ShizukuHelper.init()
        ShizukuHelper.bindInputService(this)
        antiRecoilHandler.postDelayed(antiRecoilRunnable, 16L)
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
            ACTION_UPDATE_OPACITY -> {
                controlLayout?.globalOpacity = ControlLayoutManager.getGlobalOpacity(this)
                return START_STICKY
            }
            ACTION_UPDATE_RIPPLE -> {
                controlLayout?.isTouchRippleEnabled = ControlLayoutManager.isTouchRippleEnabled(this)
                return START_STICKY
            }
            ACTION_UPDATE_GYRO -> {
                syncGyroSettingsFromPrefs()
                return START_STICKY
            }
            ACTION_UPDATE_CROSSHAIR -> {
                syncCrosshairFromPrefs()
                return START_STICKY
            }
            ACTION_UPDATE_HAPTICS -> {
                controlLayout?.isHapticEnabled = ControlLayoutManager.isHapticEnabled(this)
                return START_STICKY
            }
            ACTION_UPDATE_LOCK -> {
                controlLayout?.isPositionsLocked = ControlLayoutManager.isPositionsLocked(this)
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
            // Layer 3 (Visual-Only Non-Touchable): Center Crosshair & Touch Ripple windows
            setupCrosshairWindow()
            setupTouchRippleWindow()
            // Layer 4 (Top Chrome): Top Pill Bar, Quick-Aim Chip, and Persistent Floating Eye Button
            setupTopFloatingBar()
            setupQuickAimButton()
            setupFloatingEyeButton()
            // Layer 5 (Hardware Sensor): CODM-style Gyroscope controller
            setupGyroscopeController()
        }
        _isRunning.value = true
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        updateAimZoneWindowLayout()
        gyroController?.resetFilters()
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
            isTouchRippleEnabled = ControlLayoutManager.isTouchRippleEnabled(this@OverlayService)
            isHapticEnabled = ControlLayoutManager.isHapticEnabled(this@OverlayService)
            isPositionsLocked = ControlLayoutManager.isPositionsLocked(this@OverlayService)
            setOnHistoryChangedListener { canUndo, canRedo, _ ->
                updateOverlayHistoryButtons(canUndo, canRedo)
            }
            setOnLayoutModifiedListener { modifiedControls ->
                // Auto-persist live overlay edits immediately so changes survive game switches
                ControlLayoutManager.saveActiveLayout(this@OverlayService, modifiedControls)
            }
            loadLayout(ControlLayoutManager.loadActiveLayout(this@OverlayService))
            globalOpacity = ControlLayoutManager.getGlobalOpacity(this@OverlayService)
        }
        controlLayout = layout

        InputBridge.setSpecialActionListener { code, isDown ->
            if (!isDown) return@setSpecialActionListener
            when (code) {
                LwjglGlfwKeycode.SPECIALBTN_TOGGLECTRL -> eyeButton?.post { toggleHudVisibility() }
                LwjglGlfwKeycode.SPECIALBTN_MENU -> topBarRoot?.post { toggleEditMode() }
                LwjglGlfwKeycode.SPECIALBTN_TOGGLEGYRO -> topBarRoot?.post { toggleGyroFromOverlay() }
                LwjglGlfwKeycode.SPECIALBTN_KEYBOARD -> topBarRoot?.post { toggleFloatingKeyboard() }
            }
        }

        InputBridge.setMenuKeyListener { _ ->
            if (ControlLayoutManager.isAutoPauseAimOnMenu(this) && isAimEnabled) {
                quickAimButton?.post {
                    if (isAimEnabled) {
                        setAimEnabled(false)
                        Toast.makeText(
                            this@OverlayService,
                            "Aim Look paused for menu",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupCrosshairWindow() {
        val view = CrosshairOverlayView(this)
        val sizePx = ControlUtils.dpToPx(this, 44f).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        crosshairView = view
        crosshairParams = params
        windowManager.addView(view, params)
        syncCrosshairFromPrefs()
    }

    private fun syncCrosshairFromPrefs() {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        val enabled = ControlLayoutManager.isCrosshairEnabled(this) && !isHudHidden
        view.crosshairStyle = ControlLayoutManager.getCrosshairStyle(this)
        val sizePx = ControlUtils.dpToPx(this, 44f).toInt()
        if (enabled) {
            params.width = sizePx
            params.height = sizePx
            view.visibility = View.VISIBLE
        } else {
            params.width = 0
            params.height = 0
            view.visibility = View.GONE
        }
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun setupGyroscopeController() {
        val controller = GyroscopeController(this)
        gyroController = controller
        syncGyroSettingsFromPrefs()
        if (controller.isHardwareSupported()) {
            controller.start()
        }
    }

    private fun syncGyroSettingsFromPrefs() {
        val controller = gyroController ?: return
        val enabled = ControlLayoutManager.isGyroEnabled(this)
        controller.enabled = enabled
        controller.activationMode = ControlLayoutManager.getGyroMode(this)
        controller.sensitivityX = ControlLayoutManager.getGyroSensX(this)
        controller.sensitivityY = ControlLayoutManager.getGyroSensY(this)
        controller.invertX = ControlLayoutManager.isGyroInvertX(this)
        controller.invertY = ControlLayoutManager.isGyroInvertY(this)
        controller.smoothing = ControlLayoutManager.getGyroSmoothing(this)
        controller.paused = isHudHidden || (controlLayout?.isModifiable == true)
        gyroToggleBtn?.let { styleChipToggleButton(it, enabled) }
    }

    private fun toggleGyroFromOverlay() {
        if (isHudHidden) return
        val controller = gyroController
        if (controller != null && !controller.isHardwareSupported()) {
            Toast.makeText(this, "No gyroscope sensor detected on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        val next = !ControlLayoutManager.isGyroEnabled(this)
        ControlLayoutManager.setGyroEnabled(this, next)
        syncGyroSettingsFromPrefs()
        Toast.makeText(
            this,
            if (next) "Gyroscope ON" else "Gyroscope OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupTouchRippleWindow() {
        val ripple = TouchRippleOverlayView(this)
        val params = WindowManager.LayoutParams(
            0,
            0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        ripple.visibility = View.GONE
        ripple.onActiveStateChanged = { active ->
            updateRippleWindowState(active)
        }
        touchRippleView = ripple
        touchRippleParams = params
        windowManager.addView(ripple, params)
    }

    private fun updateRippleWindowState(active: Boolean) {
        val ripple = touchRippleView ?: return
        val params = touchRippleParams ?: return
        if (active && !isHudHidden) {
            params.width = screenWidthPx
            params.height = screenHeightPx
            params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            ripple.visibility = View.VISIBLE
        } else {
            params.width = 0
            params.height = 0
            params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            ripple.visibility = View.GONE
        }
        if (ripple.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(ripple, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun spawnTouchRipple(rawX: Float, rawY: Float) {
        if (isHudHidden) return
        if (controlLayout?.isTouchRippleEnabled == false) return
        touchRippleView?.spawnRipple(rawX, rawY)
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
            x = -ControlUtils.dpToPx(this@OverlayService, 54f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val btnSz = ControlUtils.dpToPx(this@OverlayService, 34f).toInt()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val dragListener = createDragOrClickTouchListener(bar, params, touchSlop)

        // Quick Gyro Toggle Chip
        val gyroBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_gyro)
            contentDescription = "Toggle Gyroscope Aiming"
            val p = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleGyroFromOverlay() }
            setOnTouchListener(dragListener)
        }
        gyroToggleBtn = gyroBtn
        styleChipToggleButton(gyroBtn, ControlLayoutManager.isGyroEnabled(this))

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
            setOnClickListener { toggleSettingsDrawer() }
            setOnTouchListener(dragListener)
        }
        cogToggleBtn = cogBtn

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

        val keyboardBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Toggle Virtual QWERTY Keyboard"
            val p = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleFloatingKeyboard() }
            setOnTouchListener(dragListener)
        }

        val gyroLp = LinearLayout.LayoutParams(btnSz, btnSz).apply {
            marginEnd = ControlUtils.dpToPx(this@OverlayService, 4f).toInt()
        }
        bar.addView(gyroBtn, gyroLp)
        bar.addView(undoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.addView(cogBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.addView(keyboardBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.addView(redoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        bar.setOnTouchListener(dragListener)

        topBarRoot = bar
        topBarParams = params
        windowManager.addView(bar, params)
    }

    /**
     * Dedicated movable Quick-Aim Floating Chip positioned directly next to the Floating Eye Button.
     * Toggles the right-half 3D Aim Swipe Zone ON/OFF with one tap.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupQuickAimButton() {
        val aimSize = ControlUtils.dpToPx(this, 42f).toInt()
        val aimBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_crosshair)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = ControlUtils.dpToPx(this@OverlayService, 9f).toInt()
            setPadding(p, p, p, p)
            contentDescription = "Quick Aim Swipe Zone Toggle"
            elevation = ControlUtils.dpToPx(this@OverlayService, 12f)
            setOnClickListener { toggleAimMode() }
        }
        styleChipToggleButton(aimBtn, isAimEnabled)

        val params = WindowManager.LayoutParams(
            aimSize,
            aimSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = ControlUtils.dpToPx(this@OverlayService, 16f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        aimBtn.setOnTouchListener(createDragOrClickTouchListener(aimBtn, params, touchSlop))

        quickAimButton = aimBtn
        quickAimParams = params
        windowManager.addView(aimBtn, params)
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
            setOnClickListener { toggleHudVisibility() }
        }

        val params = WindowManager.LayoutParams(
            eyeSize,
            eyeSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = ControlUtils.dpToPx(this@OverlayService, 66f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        eyeBtn.setOnTouchListener(createDragOrClickTouchListener(eyeBtn, params, touchSlop))

        eyeButton = eyeBtn
        eyeParams = params
        windowManager.addView(eyeBtn, params)
    }

    private fun createDragOrClickTouchListener(
        targetWindowView: View,
        params: WindowManager.LayoutParams,
        touchSlop: Int
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var moved = false

            @SuppressLint("ClickableViewAccessibility")
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
                                windowManager.updateViewLayout(targetWindowView, params)
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
        val aim = quickAimButton
        val aParams = quickAimParams
        if (aim != null && aParams != null && aim.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(aim)
                windowManager.addView(aim, aParams)
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

    private fun styleChipToggleButton(btn: ImageButton, enabled: Boolean) {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (enabled) {
                setColor(0xFFFFFFFF.toInt())
                setStroke(2, 0xFFFFFFFF.toInt())
            } else {
                setColor(0xFF2C2C2C.toInt())
                setStroke(1, 0x55FFFFFF)
            }
        }
        btn.background = gd
        btn.setColorFilter(if (enabled) 0xFF1E1E1E.toInt() else 0xFFB8B8B8.toInt())
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
        quickAimButton?.let { styleChipToggleButton(it, isAimEnabled) }
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

    private fun toggleFloatingKeyboard() {
        if (keyboardDrawer == null) {
            keyboardDrawer = FloatingKeyboardDrawerView(
                context = this,
                windowManager = windowManager,
                onClose = {
                    keyboardDrawer = null
                }
            )
        }
        keyboardDrawer?.toggle()
    }

    private fun toggleSettingsDrawer() {
        val hud = controlLayout ?: return
        try {
            val drawer = settingsDrawer ?: FloatingSettingsDrawerView(
                context = this,
                windowManager = windowManager,
                controlLayout = hud,
                callbacks = object : FloatingSettingsDrawerView.Callbacks {
                    override fun isAimEnabled(): Boolean = isAimEnabled

                    override fun onSetAimEnabled(enabled: Boolean) {
                        setAimEnabled(enabled)
                    }

                    override fun onToggleEditMode() {
                        toggleEditMode()
                    }

                    override fun onToggleHideHud() {
                        toggleHudVisibility()
                    }

                    override fun onToggleKeyboard() {
                        toggleFloatingKeyboard()
                    }

                    override fun onAimSensitivitySet(sensitivity: Float) {
                        hud.aimSensitivity = sensitivity
                        ControlLayoutManager.setAimSensitivity(this@OverlayService, sensitivity)
                    }

                    override fun onGyroSettingsChanged() {
                        syncGyroSettingsFromPrefs()
                    }

                    override fun onCrosshairSettingsChanged() {
                        syncCrosshairFromPrefs()
                    }

                    override fun onHapticToggled(enabled: Boolean) {
                        hud.isHapticEnabled = enabled
                    }

                    override fun onPositionsLockToggled(locked: Boolean) {
                        hud.isPositionsLocked = locked
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

                    override fun onStopServiceConfirmed() {
                        stopSelf()
                    }

                    override fun onDrawerVisibilityChanged(visible: Boolean) {
                        cogToggleBtn?.setColorFilter(if (visible) 0xFFE0E0E0.toInt() else 0xFFFFFFFF.toInt())
                    }
                }
            ).also { settingsDrawer = it }

            drawer.toggle()
        } catch (t: Throwable) {
            Toast.makeText(this, "Menu error: ${t.message ?: "failed"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEditMode() {
        val hud = controlLayout ?: return
        if (isHudHidden) toggleHudVisibility()
        hud.isModifiable = !hud.isModifiable
        if (hud.isModifiable && isAimEnabled) {
            setAimEnabled(false)
        }
        gyroController?.paused = isHudHidden || hud.isModifiable
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
            settingsDrawer?.dismiss()
            hud.isModifiable = false
            setAimEnabled(false)
            hud.releaseAllActiveTouches()
            touchRippleView?.clearRipples()
            InputBridge.resetAll()
            gyroController?.paused = true
            syncCrosshairFromPrefs()

            // Hide all individual button/joystick/drawer windows
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            // Hide top floating bar & Quick-Aim chip so only the small Eye Button remains
            setChromeWindowVisible(topBarRoot, topBarParams, false)
            setChromeWindowVisible(quickAimButton, quickAimParams, false)
            eyeButton?.setImageResource(R.drawable.ic_eye_off)
        } else {
            // Restore individual control windows according to their logical visibility
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            setChromeWindowVisible(topBarRoot, topBarParams, true)
            setChromeWindowVisible(quickAimButton, quickAimParams, true)
            eyeButton?.setImageResource(R.drawable.ic_eye_open)
            gyroController?.paused = hud.isModifiable
            syncCrosshairFromPrefs()
            updateOverlayHistoryButtons(hud.canUndo(), hud.canRedo())
        }
    }

    private fun setChromeWindowVisible(view: View?, params: WindowManager.LayoutParams?, visible: Boolean) {
        if (view == null || params == null) return
        params.flags = if (visible) BASE_WINDOW_FLAGS else (BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        view.visibility = if (visible) View.VISIBLE else View.GONE
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        antiRecoilHandler.removeCallbacksAndMessages(null)
        gyroController?.stop()
        gyroController = null
        InputBridge.setSpecialActionListener(null)
        InputBridge.setMenuKeyListener(null)
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

        settingsDrawer?.dismiss()
        settingsDrawer = null

        keyboardDrawer?.dismiss()
        keyboardDrawer = null

        try {
            ShizukuHelper.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        } catch (_: Throwable) {}

        try {
            ShizukuHelper.unbindInputService()
        } catch (_: Throwable) {}

        listOf(aimZoneView, crosshairView, topBarRoot, quickAimButton, eyeButton, touchRippleView).forEach { view ->
            if (view != null && view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }

        aimZoneView = null
        crosshairView = null
        topBarRoot = null
        cogToggleBtn = null
        quickAimButton = null
        eyeButton = null
        touchRippleView = null
        controlLayout = null
    }

    /**
     * Right-half invisible 3D Aim Swipe Zone layered beneath individual buttons.
     * Active only when Aim Look is toggled ON.
     * Uses 120Hz (8ms) coalescing flush loop + sub-pixel remainder accumulator to prevent lag spikes.
     */
    private inner class AimSwipeZoneView(context: Context) : View(context) {
        private var activePointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var isDragging = false

        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            strokeWidth = ControlUtils.dpToPx(context, 1f)
        }

        private val aimLock = Any()
        private var pendingDx = 0f
        private var pendingDy = 0f
        private var isFlushLoopRunning = false
        private val flushHandler = Handler(Looper.getMainLooper())

        private val flushRunnable = object : Runnable {
            override fun run() {
                if (!isFlushLoopRunning) return
                var sendX = 0
                var sendY = 0
                synchronized(aimLock) {
                    val intX = pendingDx.toInt()
                    val intY = pendingDy.toInt()
                    if (intX != 0 || intY != 0) {
                        sendX = intX
                        sendY = intY
                        pendingDx -= intX.toFloat()
                        pendingDy -= intY.toFloat()
                    }
                }
                if (sendX != 0 || sendY != 0) {
                    InputBridge.sendMouseMove(sendX.toFloat(), sendY.toFloat())
                }
                if (isFlushLoopRunning) {
                    flushHandler.postDelayed(this, 8L)
                }
            }
        }

        private fun startFlushLoop() {
            if (!isFlushLoopRunning) {
                isFlushLoopRunning = true
                flushHandler.post(flushRunnable)
            }
        }

        private fun stopFlushLoopAndClear() {
            isFlushLoopRunning = false
            flushHandler.removeCallbacks(flushRunnable)
            synchronized(aimLock) {
                pendingDx = 0f
                pendingDy = 0f
                thumbDownDragAccumulator = 0f
            }
        }

        @Volatile private var thumbDownDragAccumulator = 0f

        fun addRecoilTick(recoilX: Float, baseRecoilY: Float, kickBoost: Float, isInitialKick: Boolean) {
            val kickMultiplier = if (isInitialKick) (1.0f + kickBoost) else 1.0f
            val targetRecoilY = baseRecoilY * kickMultiplier

            // Smart Thumb Override: subtract manual downward thumb drag so crosshair doesn't over-pull
            val manualDrag = synchronized(aimLock) {
                val d = thumbDownDragAccumulator
                thumbDownDragAccumulator = 0f
                d
            }
            val effectiveRecoilY = (targetRecoilY - manualDrag).coerceAtLeast(0f)

            synchronized(aimLock) {
                pendingDx += recoilX
                pendingDy += effectiveRecoilY
            }
            startFlushLoop()
        }

        fun resetPointer() {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            isDragging = false
            stopFlushLoopAndClear()
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
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    spawnTouchRipple(loc[0] + event.getX(idx), loc[1] + event.getY(idx))
                    if (activePointerId == MotionEvent.INVALID_POINTER_ID ||
                        event.findPointerIndex(activePointerId) < 0
                    ) {
                        val px = event.getX(idx)
                        val py = event.getY(idx)
                        activePointerId = event.getPointerId(idx)
                        downX = px
                        downY = py
                        lastX = px
                        lastY = py
                        isDragging = false
                        synchronized(aimLock) {
                            pendingDx = 0f
                            pendingDy = 0f
                            thumbDownDragAccumulator = 0f
                        }
                    }
                    // NEVER compute delta or inject movement on ACTION_DOWN / ACTION_POINTER_DOWN
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val idx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                        event.findPointerIndex(activePointerId)
                    } else {
                        -1
                    }
                    if (idx < 0) {
                        if (event.pointerCount > 0) {
                            val px = event.getX(0)
                            val py = event.getY(0)
                            activePointerId = event.getPointerId(0)
                            downX = px
                            downY = py
                            lastX = px
                            lastY = py
                            isDragging = false
                            synchronized(aimLock) {
                                pendingDx = 0f
                                pendingDy = 0f
                                thumbDownDragAccumulator = 0f
                            }
                        }
                        return true
                    }

                    val currX = event.getX(idx)
                    val currY = event.getY(idx)

                    // 3px touch slop filter so stationary taps move the camera by 0
                    if (!isDragging) {
                        val distFromDown = hypot((currX - downX).toDouble(), (currY - downY).toDouble())
                        if (distFromDown < 3.0) {
                            return true
                        }
                        isDragging = true
                        lastX = currX
                        lastY = currY
                        startFlushLoop()
                        return true
                    }

                    val dx = currX - lastX
                    val dy = currY - lastY
                    lastX = currX
                    lastY = currY
                    val maxStep = screenWidthPx * 0.45f
                    if ((dx != 0f || dy != 0f) && hypot(dx.toDouble(), dy.toDouble()) < maxStep) {
                        var moveX = dx * sensitivity
                        var moveY = dy * sensitivity

                        // Smart Thumb Override: track downward drag so auto-recoil doesn't double-dip
                        if (dy > 0f) {
                            synchronized(aimLock) {
                                thumbDownDragAccumulator += (dy * sensitivity)
                            }
                        }

                        // 1. ADS / Scope Sensitivity Multiplier (when holding MB2 / secondary mouse button)
                        if (InputBridge.isSecondaryMouseDown) {
                            val adsMult = ControlLayoutManager.getAimAdsMultiplier(context)
                            moveX *= adsMult
                            moveY *= adsMult
                        }

                        // 2. Head-Level Horizontal Stabilizer (vertical dampening)
                        val vertDampening = ControlLayoutManager.getAimVertDampening(context)
                        if (vertDampening > 0f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            moveY *= (1.0f - vertDampening)
                        }

                        // 3. CODM-Style 3-Zone Velocity Curve (Sticky Micro-Tracking + Standard + Flick Boost)
                        val rawSpeed = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val stickySlowdown = ControlLayoutManager.getAimStickySlowdown(context)
                        val velocityFactor = if (rawSpeed <= 2.5f) {
                            stickySlowdown
                        } else if (rawSpeed < 6.0f) {
                            val t = (rawSpeed - 2.5f) / 3.5f
                            stickySlowdown + t * (1.0f - stickySlowdown)
                        } else if (rawSpeed <= 12.0f) {
                            1.0f
                        } else {
                            val speedAccelEnabled = ControlLayoutManager.isAimSpeedAccelEnabled(context)
                            if (speedAccelEnabled) {
                                val accelMultiplier = ControlLayoutManager.getAimSpeedAccelMultiplier(context)
                                1.0f + ((rawSpeed - 12f) / 24f).coerceIn(0f, 1.0f) * (accelMultiplier - 1.0f)
                            } else {
                                1.0f
                            }
                        }
                        moveX *= velocityFactor
                        moveY *= velocityFactor

                        synchronized(aimLock) {
                            pendingDx += moveX
                            pendingDy += moveY
                        }
                        startFlushLoop()
                    }
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val idx = event.actionIndex
                    val liftedId = event.getPointerId(idx)
                    if (liftedId == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        isDragging = false
                        stopFlushLoopAndClear()
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    isDragging = false
                    stopFlushLoopAndClear()
                    return true
                }
            }
            return true
        }
    }
}
