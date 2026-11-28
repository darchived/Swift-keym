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
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.customcontrols.ControlData
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.utils.ControlUtils
import com.example.input.GyroscopeController
import com.example.input.InputBridge
import com.example.input.LwjglGlfwKeycode
import com.example.input.MacroEngine
import com.example.input.ShizukuHelper
import com.example.vision.AiVisionTerminalBus
import com.example.vision.ScreenAssistService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
        const val ACTION_UPDATE_AIM_PROFILE = "com.example.overlay.ACTION_UPDATE_AIM_PROFILE"
        const val EXTRA_PRESET_FILE = "extra_preset_file"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"

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

    // Virtual Mouse Pad widget (user-controlled touch trackpad)
    private var virtualMousePadView: VirtualMousePadView? = null
    private var virtualMousePadParams: WindowManager.LayoutParams? = null
    private var isVirtualMousePadEnabled = false
    private var virtualMouseToggleBtn: ImageButton? = null

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

    // Native WindowManager Floating Settings Drawer & Keyboard & Radial Wheel
    private var settingsDrawer: FloatingSettingsDrawerView? = null
    private var keyboardDrawer: FloatingKeyboardDrawerView? = null
    private var radialWheelView: RadialWheelOverlayView? = null

    // Live Input HUD Pill (Shizuku state, weapon slot G1/G2, aim/gyro mode, panic Release All)
    private var liveHudPillRoot: LinearLayout? = null
    private var liveHudPillParams: WindowManager.LayoutParams? = null
    private var liveHudStatusText: TextView? = null

    // Visual-only (strictly FLAG_NOT_TOUCHABLE) Touch Ripple & Center Crosshair windows
    private var touchRippleView: TouchRippleOverlayView? = null
    private var touchRippleParams: WindowManager.LayoutParams? = null
    private var crosshairView: CrosshairOverlayView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var fovCircleView: FovCircleOverlayView? = null
    private var fovCircleParams: WindowManager.LayoutParams? = null

    private var isHudHidden = false
    private var isFreeLookActive = false
    private var targetGamePackage: String? = null
    private var autoHiddenByWatcher = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gameWatcherJob: Job? = null

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

            if (_isRunning.value && !isHudHidden && controlLayout?.isModifiable != true && !isFreeLookActive) {
                val recoilAdsOnly = ControlLayoutManager.isRecoilAdsOnly(this@OverlayService)
                if (isShooting && (!recoilAdsOnly || InputBridge.isSecondaryMouseDown)) {
                    val slot = ControlLayoutManager.getActiveRecoilSlot(this@OverlayService)
                    val elapsed = now - shootPressStartTime
                    val stageRecoilY = when {
                        elapsed <= 350L -> ControlLayoutManager.getRecoilEarlyY(this@OverlayService, slot)
                        elapsed <= 1200L -> ControlLayoutManager.getRecoilMidY(this@OverlayService, slot)
                        else -> ControlLayoutManager.getRecoilLateY(this@OverlayService, slot)
                    }
                    val recoilX = ControlLayoutManager.getRecoilDriftX(this@OverlayService, slot)
                    if (stageRecoilY > 0f || recoilX != 0f) {
                        val isKick = elapsed <= 350L
                        val kickBoost = ControlLayoutManager.getAimInitialKickBoost(this@OverlayService)
                        val activeAimZone = aimZoneView
                        if (activeAimZone != null && isAimEnabled) {
                            activeAimZone.addRecoilTick(recoilX, stageRecoilY, kickBoost, isKick)
                        } else {
                            val kickMultiplier = if (isKick) (1.0f + kickBoost) else 1.0f
                            InputBridge.sendMouseMove(recoilX, stageRecoilY * kickMultiplier)
                        }
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
            if (isAimEnabled) {
                aimZoneView?.postKeepAlive()
            }
        }

        override fun onQuickTurnRequested(degrees: Int) {
            aimZoneView?.performSmoothQuickTurn(degrees)
        }

        override fun onFreeLookChanged(active: Boolean) {
            isFreeLookActive = active
            liveHudPillRoot?.post { updateLiveHudPill() }
        }

        override fun onPassThruAimDelta(dx: Float, dy: Float) {
            aimZoneView?.addPassThruDelta(dx, dy)
        }

        override fun onVirtualMouseTouchActivity() {
            if (isAimEnabled) {
                aimZoneView?.postKeepAlive()
            }
        }

        override fun onShowRadialWheel(data: ControlData, rawX: Float, rawY: Float) {
            val wheel = radialWheelView ?: RadialWheelOverlayView(this@OverlayService, windowManager).also {
                radialWheelView = it
            }
            wheel.showWheel(data, rawX, rawY)
        }

        override fun onUpdateRadialWheel(rawX: Float, rawY: Float) {
            radialWheelView?.updatePointer(rawX, rawY)
        }

        override fun onReleaseRadialWheel() {
            radialWheelView?.releaseAndDismiss()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()
        ShizukuHelper.init()
        ShizukuHelper.bindInputService(this)
        ScreenAssistService.steerSink = { dx, dy ->
            aimZoneView?.addAiAssistDelta(dx, dy)
        }
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
                syncVirtualMousePadFromPrefs()
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
            ACTION_UPDATE_AIM_PROFILE -> {
                val unified = ControlLayoutManager.getAimSensX(this)
                controlLayout?.aimSensitivity = unified
                controlLayout?.isSnapToGridEnabled = ControlLayoutManager.isSnapToGridEnabled(this)
                syncFovCircleFromPrefs()
                updateLiveHudPill()
                startOrStopGameWatcher()
                return START_STICKY
            }
        }

        intent?.getStringExtra(EXTRA_PRESET_FILE)?.takeIf { it.isNotBlank() }?.let { presetFile ->
            ControlLayoutManager.setActiveLayoutName(this, presetFile)
            controlLayout?.loadLayout(ControlLayoutManager.loadActiveLayout(this))
        }
        intent?.getStringExtra(EXTRA_TARGET_PACKAGE)?.takeIf { it.isNotBlank() }?.let { pkg ->
            targetGamePackage = pkg
            autoHiddenByWatcher = false
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
            setupVirtualMousePadWindow()
            // Layer 2 (Middle): Individual Control Windows via ControlLayout WindowOverlayHost
            setupDetachedControlLayout()
            // Layer 3 (Visual-Only Non-Touchable): Center Crosshair, Touch Ripple & FOV Circle windows
            setupCrosshairWindow()
            setupTouchRippleWindow()
            setupFovCircleWindow()
            // Layer 4 (Top Chrome): Top Pill Bar, Quick-Aim Chip, Persistent Eye Button & Live HUD Pill
            setupTopFloatingBar()
            setupQuickAimButton()
            setupFloatingEyeButton()
            setupLiveHudPill()
            // Layer 5 (Hardware Sensor): CODM-style Gyroscope controller
            setupGyroscopeController()
        }
        startOrStopGameWatcher()
        updateLiveHudPill()
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

    private fun setupVirtualMousePadWindow() {
        val padWidth = ControlUtils.dpToPx(this, 220f).toInt()
        val padHeight = ControlUtils.dpToPx(this, 180f).toInt()
        val params = WindowManager.LayoutParams(
            padWidth,
            padHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ControlUtils.dpToPx(this@OverlayService, 40f).toInt()
            y = screenHeightPx / 2 - padHeight / 2
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val pad = VirtualMousePadView(this, windowManager, params).apply {
            onKeepAlive = {
                aimZoneView?.postKeepAlive()
            }
        }
        pad.visibility = View.GONE
        virtualMousePadView = pad
        virtualMousePadParams = params
        windowManager.addView(pad, params)
        syncVirtualMousePadFromPrefs()
    }

    private fun syncVirtualMousePadFromPrefs() {
        val enabled = ControlLayoutManager.isVirtualMousePadEnabled(this)
        isVirtualMousePadEnabled = enabled
        val pad = virtualMousePadView ?: return
        val params = virtualMousePadParams ?: return
        val actuallyVisible = enabled && !isHudHidden
        val padWidth = ControlUtils.dpToPx(this, 220f).toInt()
        val padHeight = ControlUtils.dpToPx(this, 180f).toInt()
        if (actuallyVisible) {
            params.width = padWidth
            params.height = padHeight
            params.flags = BASE_WINDOW_FLAGS
            pad.visibility = View.VISIBLE
        } else {
            params.width = 0
            params.height = 0
            params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            pad.visibility = View.GONE
        }
        if (pad.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(pad, params)
            } catch (_: Throwable) {}
        }
        virtualMouseToggleBtn?.let { styleChipToggleButton(it, enabled) }
        updateLiveHudPill()
    }

    private fun toggleVirtualMousePad() {
        if (isHudHidden) return
        val next = !ControlLayoutManager.isVirtualMousePadEnabled(this)
        ControlLayoutManager.setVirtualMousePadEnabled(this, next)
        syncVirtualMousePadFromPrefs()
        Toast.makeText(
            this,
            if (next) "Virtual Mouse Pad ON" else "Virtual Mouse Pad OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupDetachedControlLayout() {
        val layout = ControlLayout(this).apply {
            setOverlayWindowMode(true)
            setWindowOverlayHost(windowOverlayHost)
            aimSensitivity = ControlLayoutManager.getAimSensX(this@OverlayService)
            isTouchRippleEnabled = ControlLayoutManager.isTouchRippleEnabled(this@OverlayService)
            isHapticEnabled = ControlLayoutManager.isHapticEnabled(this@OverlayService)
            isPositionsLocked = ControlLayoutManager.isPositionsLocked(this@OverlayService)
            isSnapToGridEnabled = ControlLayoutManager.isSnapToGridEnabled(this@OverlayService)
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

        InputBridge.setWeaponSlotListener { slot ->
            ControlLayoutManager.setActiveRecoilSlot(this@OverlayService, slot)
            liveHudPillRoot?.post {
                updateLiveHudPill()
                settingsDrawer?.refreshRecoilSlotUi()
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

    private fun setupFovCircleWindow() {
        val view = FovCircleOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        fovCircleView = view
        fovCircleParams = params
        windowManager.addView(view, params)
        syncFovCircleFromPrefs()
    }

    private fun syncFovCircleFromPrefs() {
        val view = fovCircleView ?: return
        val params = fovCircleParams ?: return
        val aiStrength = ControlLayoutManager.getAiAimStrength(this)
        val enabled = aiStrength >= 0.5f && !isHudHidden
        view.visibility = if (enabled) View.VISIBLE else View.GONE
        params.flags = BASE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
                view.invalidate()
            } catch (_: Throwable) {}
        }
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
        controller.onDeltaListener = { dx, dy ->
            aimZoneView?.addGyroDelta(dx, dy)
        }
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
        updateLiveHudPill()
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
        val vMouseBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_crosshair)
            contentDescription = "Toggle Virtual Mouse Pad"
            val p = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleVirtualMousePad() }
            setOnTouchListener(dragListener)
        }
        virtualMouseToggleBtn = vMouseBtn
        styleChipToggleButton(vMouseBtn, ControlLayoutManager.isVirtualMousePadEnabled(this))

        bar.addView(gyroBtn, gyroLp)
        bar.addView(vMouseBtn, gyroLp)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLiveHudPill() {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_pill_bar)
            val hp = ControlUtils.dpToPx(this@OverlayService, 8f).toInt()
            val vp = ControlUtils.dpToPx(this@OverlayService, 4f).toInt()
            setPadding(hp, vp, hp, vp)
            elevation = ControlUtils.dpToPx(this@OverlayService, 10f)
        }

        val statusTv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        liveHudStatusText = statusTv

        val panicBtn = TextView(this).apply {
            text = "⚡"
            setTextColor(0xFF1E1E1E.toInt())
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
            val sz = ControlUtils.dpToPx(this@OverlayService, 22f).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                marginStart = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
            }
            contentDescription = "Emergency Release All Keys"
            setOnClickListener {
                releaseAllKeysPanic()
            }
        }

        pill.addView(statusTv)
        pill.addView(panicBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ControlUtils.dpToPx(this@OverlayService, 12f).toInt()
            y = ControlUtils.dpToPx(this@OverlayService, 8f).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val dragListener = createDragOrClickTouchListener(pill, params, touchSlop)
        pill.setOnTouchListener(dragListener)
        statusTv.setOnTouchListener(dragListener)

        liveHudPillRoot = pill
        liveHudPillParams = params
        windowManager.addView(pill, params)
        updateLiveHudPill()
    }

    private fun updateLiveHudPill() {
        val pill = liveHudPillRoot ?: return
        val tv = liveHudStatusText ?: return
        val enabled = ControlLayoutManager.isLiveHudPillEnabled(this) && !isHudHidden
        setChromeWindowVisible(pill, liveHudPillParams, enabled)
        if (!enabled) return

        val shizukuDot = if (InputBridge.isServiceConnected.value) "●" else "○"
        val slot = "G${ControlLayoutManager.getActiveRecoilSlot(this)}"
        val zoom = ControlLayoutManager.getZoomLabel(ControlLayoutManager.getAimZoomLevel(this))
        val mode = when {
            isFreeLookActive -> "FREE"
            isAimEnabled -> "AIM"
            else -> "PASS"
        }
        val gyroTag = if (ControlLayoutManager.isGyroEnabled(this)) " • GYRO" else ""
        val aiStr = ControlLayoutManager.getAiAimStrength(this)
        val aiTag = if (AiVisionTerminalBus.isScreenVisionActive.value && aiStr >= 0.5f) {
            String.format(java.util.Locale.US, " • AI:%.1f", aiStr)
        } else {
            ""
        }
        tv.text = "$shizukuDot $slot • $mode($zoom)$gyroTag$aiTag"
    }

    private fun releaseAllKeysPanic() {
        InputBridge.resetAll()
        MacroEngine.cancelAll()
        controlLayout?.releaseAllActiveTouches()
        aimZoneView?.resetPointer()
        radialWheelView?.dismissImmediate()
        isFreeLookActive = false
        updateLiveHudPill()
        Toast.makeText(this, "⚡ Released all keys & macros", Toast.LENGTH_SHORT).show()
    }

    private fun startOrStopGameWatcher() {
        val shouldWatch = ControlLayoutManager.isAutoHideOutsideGame(this) && !targetGamePackage.isNullOrBlank()
        if (!shouldWatch) {
            gameWatcherJob?.cancel()
            gameWatcherJob = null
            return
        }
        if (gameWatcherJob?.isActive == true) return
        gameWatcherJob = serviceScope.launch {
            while (isActive) {
                delay(1200L)
                val target = targetGamePackage ?: continue
                if (!ControlLayoutManager.isAutoHideOutsideGame(this@OverlayService)) continue
                val fg = InputBridge.getForegroundPackageViaShizuku() ?: continue
                withContext(Dispatchers.Main) {
                    if (fg != target && !isHudHidden) {
                        autoHiddenByWatcher = true
                        toggleHudVisibility()
                    } else if (fg == target && autoHiddenByWatcher && isHudHidden) {
                        autoHiddenByWatcher = false
                        toggleHudVisibility()
                    }
                }
            }
        }
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
        val pill = liveHudPillRoot
        val pParams = liveHudPillParams
        if (pill != null && pParams != null && pill.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(pill)
                windowManager.addView(pill, pParams)
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
        aimZoneView?.postVirtualMouseActive(isAimEnabled)
        updateLiveHudPill()
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
                        ControlLayoutManager.setAimSensX(this@OverlayService, sensitivity)
                        ControlLayoutManager.setAimSensY(this@OverlayService, sensitivity)
                    }

                    override fun onAimProfileUpdated() {
                        hud.aimSensitivity = ControlLayoutManager.getAimSensX(this@OverlayService)
                        hud.isSnapToGridEnabled = ControlLayoutManager.isSnapToGridEnabled(this@OverlayService)
                        updateLiveHudPill()
                    }

                    override fun onReleaseAllKeysPanic() {
                        releaseAllKeysPanic()
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
            radialWheelView?.dismissImmediate()
            hud.isModifiable = false
            setAimEnabled(false)
            hud.releaseAllActiveTouches()
            touchRippleView?.clearRipples()
            InputBridge.resetAll()
            MacroEngine.cancelAll()
            isFreeLookActive = false
            gyroController?.paused = true
            syncCrosshairFromPrefs()

            // Hide all individual button/joystick/drawer windows
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            // Hide top floating bar, Quick-Aim chip & Live HUD Pill so only the small Eye Button remains
            setChromeWindowVisible(topBarRoot, topBarParams, false)
            setChromeWindowVisible(quickAimButton, quickAimParams, false)
            setChromeWindowVisible(liveHudPillRoot, liveHudPillParams, false)
            setChromeWindowVisible(virtualMousePadView, virtualMousePadParams, false)
            setChromeWindowVisible(fovCircleView, fovCircleParams, false)
            eyeButton?.setImageResource(R.drawable.ic_eye_off)
        } else {
            // Restore individual control windows according to their logical visibility
            for ((view, _) in controlWindows) {
                val logical = controlLogicalVisible[view] ?: true
                windowOverlayHost.updateControlWindow(view, Int.MIN_VALUE, Int.MIN_VALUE, -1, -1, logical)
            }

            setChromeWindowVisible(topBarRoot, topBarParams, true)
            setChromeWindowVisible(quickAimButton, quickAimParams, true)
            syncVirtualMousePadFromPrefs()
            syncFovCircleFromPrefs()
            updateLiveHudPill()
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
        gameWatcherJob?.cancel()
        serviceScope.cancel()
        antiRecoilHandler.removeCallbacksAndMessages(null)
        gyroController?.onDeltaListener = null
        gyroController?.stop()
        gyroController = null
        ScreenAssistService.steerSink = null
        InputBridge.setSpecialActionListener(null)
        InputBridge.setMenuKeyListener(null)
        InputBridge.setWeaponSlotListener(null)
        MacroEngine.cancelAll()
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

        radialWheelView?.dismissImmediate()
        radialWheelView = null

        try {
            InputBridge.setVirtualMouseActive(false)
            aimZoneView?.release()
        } catch (_: Throwable) {}

        try {
            ShizukuHelper.destroyUinput()
        } catch (_: Throwable) {}

        try {
            ShizukuHelper.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        } catch (_: Throwable) {}

        try {
            ShizukuHelper.unbindInputService()
        } catch (_: Throwable) {}

        listOf(aimZoneView, crosshairView, topBarRoot, quickAimButton, eyeButton, touchRippleView, liveHudPillRoot).forEach { view ->
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
        liveHudPillRoot = null
        liveHudStatusText = null
        controlLayout = null
    }

    /**
     * Right-half invisible 3D Aim Swipe Zone layered beneath individual buttons.
     * Active only when Aim Look is toggled ON.
     * Uses 120Hz (8ms) coalescing flush loop + sub-pixel remainder accumulator, Per-Axis X/Y,
     * ADS Transition Ramp, Zoom scaling, Edge-Guard bezel rejection, Inertia Glide, Quick Turn,
     * and Touch-to-Gyro Hybrid Blending.
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
        private var smoothedVelX = 0f
        private var smoothedVelY = 0f
        private var inertiaVelX = 0f
        private var inertiaVelY = 0f
        private var isInertiaGliding = false

        // Quick 90/180 turn burst state
        private var quickTurnRemainingTicks = 0
        private var quickTurnPerTickDx = 0f

        // Smooth ADS transition ramp tracking
        private val adsRampLock = Any()
        private var lastAdsDown = false
        private var adsRampStartMs = 0L
        private var adsRampFrom = 1.0f

        @Volatile private var isFlushLoopRunning = false
        private val flushThread = HandlerThread(
            "SwiftKeyM-AimFlush",
            Process.THREAD_PRIORITY_URGENT_DISPLAY
        ).apply { start() }
        private val flushHandler = Handler(flushThread.looper)

        private val keepAliveRunnable = Runnable {
            if (isAimEnabled && !isHudHidden) {
                InputBridge.sendMouseKeepAlive()
            }
        }

        private val flushRunnable = object : Runnable {
            override fun run() {
                if (!isFlushLoopRunning) return
                var sendX = 0
                var sendY = 0
                var keepRunning = false
                synchronized(aimLock) {
                    if (quickTurnRemainingTicks > 0) {
                        pendingDx += quickTurnPerTickDx
                        quickTurnRemainingTicks--
                        keepRunning = true
                    }
                    if (isInertiaGliding && activePointerId == MotionEvent.INVALID_POINTER_ID) {
                        val decay = ControlLayoutManager.getAimInertia(context)
                        if (decay > 0.01f && (abs(inertiaVelX) > 0.15f || abs(inertiaVelY) > 0.15f)) {
                            pendingDx += inertiaVelX
                            pendingDy += inertiaVelY
                            inertiaVelX *= decay
                            inertiaVelY *= decay
                            keepRunning = true
                        } else {
                            inertiaVelX = 0f
                            inertiaVelY = 0f
                            isInertiaGliding = false
                        }
                    }

                    val intX = pendingDx.toInt()
                    val intY = pendingDy.toInt()
                    if (intX != 0 || intY != 0) {
                        sendX = intX
                        sendY = intY
                        pendingDx -= intX.toFloat()
                        pendingDy -= intY.toFloat()
                    }
                    if (activePointerId != MotionEvent.INVALID_POINTER_ID || abs(pendingDx) >= 1f || abs(pendingDy) >= 1f) {
                        keepRunning = true
                    }
                    if (!keepRunning) {
                        isFlushLoopRunning = false
                    }
                }
                if (sendX != 0 || sendY != 0) {
                    InputBridge.sendMouseMove(sendX.toFloat(), sendY.toFloat())
                }
                // Update Touch-to-Gyro hybrid zoom scale continuously
                gyroController?.adsZoomScale = computeCurrentAdsZoomMultiplier()

                if (keepRunning && isFlushLoopRunning) {
                    flushHandler.postDelayed(this, 8L)
                }
            }
        }

        private fun computeCurrentAdsZoomMultiplier(): Float {
            synchronized(adsRampLock) {
                val isAds = InputBridge.isSecondaryMouseDown
                val now = SystemClock.uptimeMillis()
                val adsBase = ControlLayoutManager.getAimAdsMultiplier(context)
                val zoomFactor = ControlLayoutManager.getZoomFactorForLevel(ControlLayoutManager.getAimZoomLevel(context))
                val target = if (isAds) adsBase * zoomFactor else 1.0f

                if (isAds != lastAdsDown) {
                    adsRampFrom = if (lastAdsDown) adsBase * zoomFactor else 1.0f
                    adsRampStartMs = now
                    lastAdsDown = isAds
                }
                val rampMs = ControlLayoutManager.getAimAdsRampMs(context)
                if (rampMs <= 0) return target
                val progress = ((now - adsRampStartMs).toFloat() / rampMs.toFloat()).coerceIn(0f, 1f)
                return adsRampFrom + (target - adsRampFrom) * progress
            }
        }

        private fun startFlushLoop() {
            val shouldPost = synchronized(aimLock) {
                if (!isFlushLoopRunning) {
                    isFlushLoopRunning = true
                    true
                } else {
                    false
                }
            }
            if (shouldPost) {
                flushHandler.post(flushRunnable)
            }
        }

        private fun stopFlushLoopAndClear() {
            synchronized(aimLock) {
                isFlushLoopRunning = false
                pendingDx = 0f
                pendingDy = 0f
                smoothedVelX = 0f
                smoothedVelY = 0f
                inertiaVelX = 0f
                inertiaVelY = 0f
                isInertiaGliding = false
                quickTurnRemainingTicks = 0
                quickTurnPerTickDx = 0f
                thumbDownDragAccumulator = 0f
            }
            flushHandler.removeCallbacks(flushRunnable)
            gyroController?.isTouchSwiping = false
        }

        fun postVirtualMouseActive(active: Boolean) {
            flushHandler.post {
                InputBridge.setVirtualMouseActive(active)
            }
        }

        fun postKeepAlive() {
            flushHandler.removeCallbacks(keepAliveRunnable)
            flushHandler.post(keepAliveRunnable)
        }

        fun release() {
            stopFlushLoopAndClear()
            flushHandler.removeCallbacksAndMessages(null)
            try {
                flushThread.quitSafely()
            } catch (_: Throwable) {
            }
        }

        @Volatile private var thumbDownDragAccumulator = 0f

        fun addGyroDelta(gyroDx: Float, gyroDy: Float) {
            if (isHudHidden || controlLayout?.isModifiable == true) return
            synchronized(aimLock) {
                pendingDx += gyroDx
                pendingDy += gyroDy
            }
            startFlushLoop()
        }

        fun addPassThruDelta(dx: Float, dy: Float) {
            if (isHudHidden || controlLayout?.isModifiable == true) return
            synchronized(aimLock) {
                pendingDx += dx
                pendingDy += dy
            }
            startFlushLoop()
        }

        fun addAiAssistDelta(aiDx: Float, aiDy: Float) {
            if (isHudHidden || controlLayout?.isModifiable == true || isFreeLookActive) return
            synchronized(aimLock) {
                pendingDx += aiDx
                pendingDy += aiDy
            }
            startFlushLoop()
        }

        fun performSmoothQuickTurn(degrees: Int) {
            val sensX = ControlLayoutManager.getAimSensX(context)
            val totalCounts = degrees * 3.5f * sensX
            val ticks = 10
            synchronized(aimLock) {
                quickTurnPerTickDx = totalCounts / ticks.toFloat()
                quickTurnRemainingTicks = ticks
            }
            startFlushLoop()
        }

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

            val sensX = ControlLayoutManager.getAimSensX(context)
            val sensY = ControlLayoutManager.getAimSensY(context)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = event.actionIndex
                    val px = event.getX(idx)
                    val py = event.getY(idx)

                    // Edge-Guard Margin on outer bezel edges to reject palm accidental touches
                    val guardPx = ControlUtils.dpToPx(context, ControlLayoutManager.getAimEdgeGuardDp(context).toFloat())
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && guardPx > 0f) {
                        if (px > width - guardPx || py < guardPx || py > height - guardPx) {
                            return false
                        }
                    }

                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    spawnTouchRipple(loc[0] + px, loc[1] + py)
                    if (activePointerId == MotionEvent.INVALID_POINTER_ID ||
                        event.findPointerIndex(activePointerId) < 0
                    ) {
                        activePointerId = event.getPointerId(idx)
                        downX = px
                        downY = py
                        lastX = px
                        lastY = py
                        isDragging = false
                        gyroController?.isTouchSwiping = true
                        synchronized(aimLock) {
                            if (isInertiaGliding || !ControlLayoutManager.isGyroEnabled(context)) {
                                pendingDx = 0f
                                pendingDy = 0f
                            }
                            smoothedVelX = 0f
                            smoothedVelY = 0f
                            inertiaVelX = 0f
                            inertiaVelY = 0f
                            isInertiaGliding = false
                            thumbDownDragAccumulator = 0f
                        }
                    }
                    // Immediately pulse hardware mouse keep-alive on ACTION_DOWN so cursor never hides
                    postKeepAlive()
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
                                smoothedVelX = 0f
                                smoothedVelY = 0f
                                inertiaVelX = 0f
                                inertiaVelY = 0f
                                isInertiaGliding = false
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
                        var moveX = dx * sensX
                        var moveY = dy * sensY

                        // Smart Thumb Override: track downward drag so auto-recoil doesn't double-dip
                        if (dy > 0f) {
                            synchronized(aimLock) {
                                thumbDownDragAccumulator += (dy * sensY)
                            }
                        }

                        // 1. Smooth ADS Transition Ramp & Scope Zoom Multiplier
                        val adsZoomMult = computeCurrentAdsZoomMultiplier()
                        moveX *= adsZoomMult
                        moveY *= adsZoomMult

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
                            smoothedVelX = smoothedVelX * 0.55f + moveX * 0.45f
                            smoothedVelY = smoothedVelY * 0.55f + moveY * 0.45f
                        }
                        startFlushLoop()
                    }
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val idx = event.actionIndex
                    val liftedId = event.getPointerId(idx)
                    if (liftedId == activePointerId) {
                        handleSwipePointerLifted(cancel = false)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    handleSwipePointerLifted(cancel = false)
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handleSwipePointerLifted(cancel = true)
                    return true
                }
            }
            return true
        }

        private fun handleSwipePointerLifted(cancel: Boolean) {
            val wasDragging = isDragging
            activePointerId = MotionEvent.INVALID_POINTER_ID
            isDragging = false
            gyroController?.isTouchSwiping = false

            val inertia = ControlLayoutManager.getAimInertia(context)
            if (!cancel && wasDragging && inertia > 0.01f &&
                (abs(smoothedVelX) > 0.35f || abs(smoothedVelY) > 0.35f)
            ) {
                synchronized(aimLock) {
                    inertiaVelX = smoothedVelX
                    inertiaVelY = smoothedVelY
                    isInertiaGliding = true
                }
                startFlushLoop()
            } else {
                stopFlushLoopAndClear()
            }
            // Immediately pulse zero-delta keep-alive on ACTION_UP / ACTION_CANCEL so Android keeps cursor visible
            postKeepAlive()
        }
    }
}
