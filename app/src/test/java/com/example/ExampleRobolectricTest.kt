package com.example

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.customcontrols.ControlData
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.CustomControls
import com.example.input.LwjglGlfwKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name and credits from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Swift KeyM", context.getString(R.string.app_name))
        assertEquals("Created by St3rlight • Team StarDevs", context.getString(R.string.app_credits))
    }

    @Test
    fun `glfw keycodes map accurately to android key events`() {
        assertEquals(KeyEvent.KEYCODE_W, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_W))
        assertEquals(KeyEvent.KEYCODE_A, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_A))
        assertEquals(KeyEvent.KEYCODE_S, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_S))
        assertEquals(KeyEvent.KEYCODE_D, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_D))
        assertEquals(KeyEvent.KEYCODE_SPACE, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_SPACE))
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT))
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL))
        assertEquals(KeyEvent.KEYCODE_TAB, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_TAB))
        assertEquals(KeyEvent.KEYCODE_ESCAPE, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_ESCAPE))
        assertEquals(KeyEvent.KEYCODE_F, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_F))
        assertEquals(KeyEvent.KEYCODE_G, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_G))
        assertEquals(KeyEvent.KEYCODE_M, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_M))
        assertEquals(KeyEvent.KEYCODE_R, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_R))
        assertEquals(KeyEvent.KEYCODE_1, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_1))
        assertEquals(KeyEvent.KEYCODE_4, LwjglGlfwKeycode.toAndroidKeyCode(LwjglGlfwKeycode.GLFW_KEY_4))
    }

    @Test
    fun `json layout round trip preserves controls drawers and joysticks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = ControlLayoutManager.createDefaultFpsPreset()
        val json = original.toJson()
        val importedName = ControlLayoutManager.importLayoutJson(context, "custom_warzone.json", json)
        val reloaded = CustomControls.fromJson(ControlLayoutManager.exportLayoutJson(context, importedName))

        assertEquals(original.mControlDataList.size, reloaded.mControlDataList.size)
        assertEquals(original.mDrawerDataList.size, reloaded.mDrawerDataList.size)
        assertEquals(original.mJoystickDataList.size, reloaded.mJoystickDataList.size)
        assertEquals("Shoot", reloaded.mControlDataList.first().name)
    }

    @Test
    fun `undo redo and unsaved changes track layout mutations accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = ControlLayout(context)
        layout.layout(0, 0, 1920, 1080)
        layout.loadLayout(ControlLayoutManager.createDefaultFpsPreset())
        layout.isModifiable = true

        val initialCount = layout.layout.mControlDataList.size
        assertFalse(layout.hasUnsavedChanges())
        assertFalse(layout.canUndo())
        assertFalse(layout.canRedo())

        // Add a button -> dirty + undoable
        val extra = ControlData("Extra", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, 0, 0, 0), "0.5 * \${screen_width}", "0.5 * \${screen_height}", 56f, 56f)
        layout.addControlButton(extra)
        layout.notifyLayoutModified()

        assertEquals(initialCount + 1, layout.layout.mControlDataList.size)
        assertTrue(layout.hasUnsavedChanges())
        assertTrue(layout.canUndo())
        assertFalse(layout.canRedo())

        // Undo -> restored to clean state
        assertTrue(layout.undo())
        assertEquals(initialCount, layout.layout.mControlDataList.size)
        assertFalse(layout.hasUnsavedChanges())
        assertFalse(layout.canUndo())
        assertTrue(layout.canRedo())

        // Redo -> button restored & dirty again
        assertTrue(layout.redo())
        assertEquals(initialCount + 1, layout.layout.mControlDataList.size)
        assertTrue(layout.hasUnsavedChanges())

        // Mark saved -> clean again
        layout.markSaved()
        assertFalse(layout.hasUnsavedChanges())
    }

    @Test
    fun `control layout loads preset switches modes and handles play touch without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = ControlLayout(context)
        layout.layout(0, 0, 1920, 1080)
        layout.loadLayout(ControlLayoutManager.createDefaultFpsPreset())

        assertFalse(layout.isModifiable)
        layout.isModifiable = true
        assertTrue(layout.isModifiable)
        layout.isModifiable = false

        val down = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, 960f, 540f, 0)
        val move = MotionEvent.obtain(100L, 116L, MotionEvent.ACTION_MOVE, 1000f, 560f, 0)
        val up = MotionEvent.obtain(100L, 132L, MotionEvent.ACTION_UP, 1000f, 560f, 0)
        assertTrue(layout.dispatchTouchEvent(down))
        assertTrue(layout.dispatchTouchEvent(move))
        assertTrue(layout.dispatchTouchEvent(up))
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `detached window overlay host attaches individual control windows without adding children to root`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val attached = LinkedHashMap<android.view.View, IntArray>()
        val host = object : ControlLayout.WindowOverlayHost {
            override fun getOverlayScreenWidth(): Int = 1920
            override fun getOverlayScreenHeight(): Int = 1080
            override fun attachControlWindow(
                view: android.view.View,
                x: Int,
                y: Int,
                width: Int,
                height: Int,
                visible: Boolean
            ) {
                attached[view] = intArrayOf(x, y, width, height, if (visible) 1 else 0)
            }

            override fun updateControlWindow(
                view: android.view.View,
                x: Int,
                y: Int,
                width: Int,
                height: Int,
                visible: Boolean
            ) {
                val cur = attached[view] ?: intArrayOf(0, 0, 0, 0, 0)
                if (x != Int.MIN_VALUE) cur[0] = x
                if (y != Int.MIN_VALUE) cur[1] = y
                if (width > 0) cur[2] = width
                if (height > 0) cur[3] = height
                cur[4] = if (visible) 1 else 0
                attached[view] = cur
            }

            override fun detachControlWindow(view: android.view.View) {
                attached.remove(view)
            }

            override fun getControlWindowX(view: android.view.View): Int = attached[view]?.get(0) ?: 0
            override fun getControlWindowY(view: android.view.View): Int = attached[view]?.get(1) ?: 0
        }

        val layout = ControlLayout(context)
        layout.setWindowOverlayHost(host)
        val preset = ControlLayoutManager.createDefaultFpsPreset()
        layout.loadLayout(preset)

        // ControlLayout itself should only contain the 2 editor chrome views (resize handle + quick bar), not buttons
        assertEquals(2, layout.childCount)
        assertTrue(layout.isDetachedWindowMode)
        val expectedControls = preset.mControlDataList.size +
            preset.mDrawerDataList.size +
            preset.mDrawerDataList.sumOf { it.buttonProperties.size } +
            preset.mJoystickDataList.size
        assertEquals(expectedControls, attached.size)
        // Each individual window is strictly sized to its control bounds, never full-screen
        assertTrue(attached.values.all { it[2] < 1920 && it[3] < 1080 })
    }

    @Test
    fun `shizuku helper handles unrunning shizuku daemon safely`() {
        com.example.input.ShizukuHelper.init()
        assertFalse(com.example.input.ShizukuHelper.isShizukuAvailable())
        assertFalse(com.example.input.ShizukuHelper.hasPermission())
        val result = com.example.input.ShizukuHelper.executeShellCommand("echo hello")
        assertFalse(result.isSuccess)
        assertTrue(result.output.isNotBlank())
    }

    @Test
    fun `global opacity scales controls and touch ripple dispatches feedback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var rippleHits = 0
        val attached = LinkedHashMap<android.view.View, IntArray>()
        val host = object : ControlLayout.WindowOverlayHost {
            override fun getOverlayScreenWidth(): Int = 1920
            override fun getOverlayScreenHeight(): Int = 1080
            override fun attachControlWindow(view: android.view.View, x: Int, y: Int, width: Int, height: Int, visible: Boolean) {
                attached[view] = intArrayOf(x, y, width, height)
            }
            override fun updateControlWindow(view: android.view.View, x: Int, y: Int, width: Int, height: Int, visible: Boolean) {}
            override fun detachControlWindow(view: android.view.View) {
                attached.remove(view)
            }
            override fun getControlWindowX(view: android.view.View): Int = 0
            override fun getControlWindowY(view: android.view.View): Int = 0
            override fun onTouchFeedback(rawX: Float, rawY: Float) {
                rippleHits++
            }
        }

        val layout = ControlLayout(context)
        layout.setWindowOverlayHost(host)
        layout.loadLayout(ControlLayoutManager.createDefaultFpsPreset())

        layout.globalOpacity = 0.5f
        ControlLayoutManager.setGlobalOpacity(context, 0.5f)
        assertEquals(0.5f, ControlLayoutManager.getGlobalOpacity(context), 0.01f)
        assertTrue(attached.keys.all { it.alpha <= 0.51f })

        layout.isTouchRippleEnabled = true
        layout.notifyTouchFeedback(500f, 300f)
        assertEquals(1, rippleHits)

        layout.isTouchRippleEnabled = false
        layout.notifyTouchFeedback(500f, 300f)
        assertEquals(1, rippleHits)
    }

    @Test
    fun `preset manager creates duplicates renames and deletes game layouts in local storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val created = ControlLayoutManager.createNewPreset(
            context,
            "apex_mobile",
            "Apex Legends",
            ControlLayoutManager.createArenaShooterPreset()
        )
        assertTrue(ControlLayoutManager.listLayouts(context).any { it.fileName == created && it.gameName == "Apex Legends" })

        val duplicated = ControlLayoutManager.duplicatePreset(context, created, "apex_custom")
        assertTrue(ControlLayoutManager.listLayouts(context).any { it.fileName == duplicated })

        val renamed = ControlLayoutManager.renamePreset(context, duplicated, "apex_ranked")
        assertTrue(ControlLayoutManager.listLayouts(context).any { it.fileName == renamed })
        assertFalse(ControlLayoutManager.listLayouts(context).any { it.fileName == duplicated })

        assertTrue(ControlLayoutManager.deletePreset(context, renamed))
        assertTrue(ControlLayoutManager.deletePreset(context, created))
    }
}
