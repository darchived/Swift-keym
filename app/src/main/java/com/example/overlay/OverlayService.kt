package com.example.overlay

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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Floating TYPE_APPLICATION_OVERLAY foreground service hosting Swift KeyM's ControlLayout
 * and a top-center Cogwheel trigger pill that opens the MojoLauncher-style Context Menu.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_RELOAD_LAYOUT = "com.example.overlay.ACTION_RELOAD_LAYOUT"
        const val ACTION_UPDATE_SENSITIVITY = "com.example.overlay.ACTION_UPDATE_SENSITIVITY"

        private const val CHANNEL_ID = "swift_keym_overlay_channel"
        private const val NOTIF_ID = 4102

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var windowManager: WindowManager
    private var controlLayout: ControlLayout? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var cogPillRoot: LinearLayout? = null
    private var cogPillParams: WindowManager.LayoutParams? = null
    private var hudHiddenBadge: TextView? = null
    private var undoPillBtn: ImageButton? = null
    private var redoPillBtn: ImageButton? = null

    private var isHudHidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        InputBridge.init()
        InputBridge.bindService(this)
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
            setupHudOverlay()
            setupTopCenterCogwheelPill()
        }
        _isRunning.value = true
        return START_STICKY
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

    private fun setupHudOverlay() {
        val layout = ControlLayout(this).apply {
            setOverlayWindowMode(true)
            aimSensitivity = ControlLayoutManager.getAimSensitivity(this@OverlayService)
            loadLayout(ControlLayoutManager.loadActiveLayout(this@OverlayService))
            setOnHistoryChangedListener { canUndo, canRedo, _ ->
                updateOverlayHistoryButtons(canUndo, canRedo)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        controlLayout = layout
        hudParams = params
        windowManager.addView(layout, params)

        InputBridge.setSpecialActionListener { code, isDown ->
            if (!isDown) return@setSpecialActionListener
            when (code) {
                LwjglGlfwKeycode.SPECIALBTN_TOGGLECTRL -> cogPillRoot?.post { toggleHudVisibility() }
                LwjglGlfwKeycode.SPECIALBTN_MENU -> cogPillRoot?.post { toggleEditMode() }
            }
        }
    }

    private fun setupTopCenterCogwheelPill() {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_pill_bar)
            val hp = ControlUtils.dpToPx(this@OverlayService, 8f).toInt()
            val vp = ControlUtils.dpToPx(this@OverlayService, 4f).toInt()
            setPadding(hp, vp, hp, vp)
            elevation = ControlUtils.dpToPx(this@OverlayService, 10f)
        }

        val pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = ControlUtils.dpToPx(this@OverlayService, 6f).toInt()
        }

        val sz = ControlUtils.dpToPx(this@OverlayService, 24f).toInt()
        val btnSz = ControlUtils.dpToPx(this@OverlayService, 32f).toInt()

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

        val cogIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(0xFFFFFFFF.toInt())
            contentDescription = "Open Swift KeyM Cogwheel Menu"
            layoutParams = LinearLayout.LayoutParams(sz, sz)
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

        val hiddenLabel = TextView(this).apply {
            text = "  Show HUD"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            setOnClickListener { toggleHudVisibility() }
        }
        hudHiddenBadge = hiddenLabel

        pill.addView(undoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        pill.addView(cogIcon)
        pill.addView(redoBtn, LinearLayout.LayoutParams(btnSz, btnSz))
        pill.addView(hiddenLabel)

        // Tap to open Cogwheel Menu; Drag to reposition pill
        pill.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var moved = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = pillParams.x
                        startY = pillParams.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!moved && Math.hypot(dx.toDouble(), dy.toDouble()) > 12.0) {
                            moved = true
                        }
                        if (moved) {
                            pillParams.x = startX + dx.toInt()
                            pillParams.y = startY + dy.toInt()
                            windowManager.updateViewLayout(pill, pillParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            openCogwheelMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })

        cogPillRoot = pill
        cogPillParams = pillParams
        windowManager.addView(pill, pillParams)
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
        val params = hudParams ?: return
        isHudHidden = !isHudHidden

        if (isHudHidden) {
            hud.isModifiable = false
            hud.releaseAllActiveTouches()
            InputBridge.resetAll()
            hud.visibility = View.GONE
            params.width = 0
            params.height = 0
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            hudHiddenBadge?.visibility = View.VISIBLE
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            hud.visibility = View.VISIBLE
            hudHiddenBadge?.visibility = View.GONE
        }
        windowManager.updateViewLayout(hud, params)
        updateOverlayHistoryButtons(hud.canUndo(), hud.canRedo())
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        InputBridge.setSpecialActionListener(null)
        InputBridge.resetAll()

        controlLayout?.let { view ->
            view.releaseAllActiveTouches()
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }
        cogPillRoot?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
            }
        }
        controlLayout = null
        cogPillRoot = null
    }
}
