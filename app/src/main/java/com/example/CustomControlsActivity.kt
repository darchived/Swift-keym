package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.customcontrols.CogwheelMenuDialog
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.ExitConfirmDialog
import com.example.customcontrols.utils.ControlUtils
import com.example.overlay.OverlayService

/**
 * Full-screen landscape HUD & Bind Editor locked to sensorLandscape in AndroidManifest.xml.
 * Hosts ControlLayout, top-center Undo / Cogwheel / Redo pill bar, Android Photo Picker,
 * and Exit / Unsaved Changes confirmation protection.
 */
class CustomControlsActivity : ComponentActivity() {

    private lateinit var controlLayout: ControlLayout
    private lateinit var hintBox: LinearLayout
    private lateinit var undoBtn: ImageButton
    private lateinit var redoBtn: ImageButton
    private var pendingImageCallback: ControlLayout.ImagePickCallback? = null

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingImageCallback
        pendingImageCallback = null
        if (uri != null && callback != null) {
            val savedPath = ControlLayoutManager.copyPickedImageToIcons(this, uri)
            if (savedPath != null) {
                callback.onImageSaved(savedPath)
                Toast.makeText(this, "Button icon updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not load selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val activeName = ControlLayoutManager.getActiveLayoutName(this)
                val json = controlLayout.layout.toJson()
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                Toast.makeText(this, "Exported $activeName", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(this, "Export failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive full-screen landscape editing canvas
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        controlLayout = ControlLayout(this).apply {
            setOverlayWindowMode(false)
            isModifiable = true
            aimSensitivity = ControlLayoutManager.getAimSensitivity(this@CustomControlsActivity)
            loadLayout(ControlLayoutManager.loadActiveLayout(this@CustomControlsActivity))
            setImagePickListener { callback ->
                pendingImageCallback = callback
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
        root.addView(
            controlLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Top-right instructions hint
        hintBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            val pad = ControlUtils.dpToPx(this@CustomControlsActivity, 10f).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val hint1 = TextView(this).apply {
            text = getString(R.string.editor_hint_line1)
            setTextColor(0x88FFFFFF.toInt())
            textSize = 11f
        }
        val hint2 = TextView(this).apply {
            text = getString(R.string.editor_hint_line2)
            setTextColor(0x88FFFFFF.toInt())
            textSize = 11f
        }
        hintBox.addView(hint1)
        hintBox.addView(hint2)
        root.addView(
            hintBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        // Top-center Undo | Cogwheel (⚙) | Redo pill bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_pill_bar)
            val hp = ControlUtils.dpToPx(this@CustomControlsActivity, 6f).toInt()
            val vp = ControlUtils.dpToPx(this@CustomControlsActivity, 2f).toInt()
            setPadding(hp, vp, hp, vp)
            elevation = ControlUtils.dpToPx(this@CustomControlsActivity, 8f)
        }

        val btnSize = ControlUtils.dpToPx(this, 42f).toInt()

        undoBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_undo)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.editor_undo)
            setOnClickListener { controlLayout.undo() }
        }

        val cogBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Open context menu"
            setOnClickListener { openCogwheelMenu() }
        }

        redoBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_redo)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.editor_redo)
            setOnClickListener { controlLayout.redo() }
        }

        topBar.addView(undoBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        topBar.addView(cogBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        topBar.addView(redoBtn, LinearLayout.LayoutParams(btnSize, btnSize))

        val barLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = ControlUtils.dpToPx(this@CustomControlsActivity, 8f).toInt()
        }
        root.addView(topBar, barLp)

        controlLayout.setOnHistoryChangedListener { canUndo, canRedo, _ ->
            updateHistoryButtons(canUndo, canRedo)
        }

        setContentView(root)
    }

    private fun updateHistoryButtons(canUndo: Boolean, canRedo: Boolean) {
        val editing = controlLayout.isModifiable
        undoBtn.visibility = if (editing) View.VISIBLE else View.GONE
        redoBtn.visibility = if (editing) View.VISIBLE else View.GONE
        undoBtn.isEnabled = canUndo
        redoBtn.isEnabled = canRedo
        undoBtn.alpha = if (canUndo) 1.0f else 0.32f
        redoBtn.alpha = if (canRedo) 1.0f else 0.32f
    }

    private fun saveCurrentLayoutToDisk() {
        ControlLayoutManager.saveActiveLayout(this, controlLayout.layout)
        controlLayout.markSaved()
        notifyOverlayReloadIfRunning()
    }

    private fun notifyOverlayReloadIfRunning() {
        if (OverlayService.isRunning.value) {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_RELOAD_LAYOUT
                }
            )
        }
    }

    private fun handleExitRequest() {
        ExitConfirmDialog.show(
            this,
            controlLayout.hasUnsavedChanges(),
            false,
            object : ExitConfirmDialog.Callback {
                override fun onSaveAndExit() {
                    saveCurrentLayoutToDisk()
                    Toast.makeText(
                        this@CustomControlsActivity,
                        "Saved ${ControlLayoutManager.getActiveLayoutName(this@CustomControlsActivity)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }

                override fun onContinueExit() {
                    finish()
                }
            }
        )
    }

    private fun openCogwheelMenu() {
        CogwheelMenuDialog(
            this,
            controlLayout,
            false,
            object : CogwheelMenuDialog.HostActions {
                override fun onToggleEditMode() {
                    val next = !controlLayout.isModifiable
                    controlLayout.isModifiable = next
                    hintBox.visibility = if (next) View.VISIBLE else View.GONE
                    updateHistoryButtons(controlLayout.canUndo(), controlLayout.canRedo())
                    Toast.makeText(
                        this@CustomControlsActivity,
                        if (next) "Edit Mode" else "Test Play Mode",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onToggleHideHud() {}

                override fun onAdjustAimSensitivity(delta: Float) {
                    val next = ControlUtils.clamp(controlLayout.aimSensitivity + delta, 0.2f, 5.0f)
                    controlLayout.aimSensitivity = next
                    ControlLayoutManager.setAimSensitivity(this@CustomControlsActivity, next)
                }

                override fun onExportControlMap() {
                    val activeName = ControlLayoutManager.getActiveLayoutName(this@CustomControlsActivity)
                    exportJsonLauncher.launch(activeName)
                }

                override fun onLayoutSaved() {
                    notifyOverlayReloadIfRunning()
                }

                override fun onExitHost() {
                    handleExitRequest()
                }

                override fun isOverlayHost(): Boolean = false

                override fun isHudHidden(): Boolean = false
            }
        ).show()
    }
}
