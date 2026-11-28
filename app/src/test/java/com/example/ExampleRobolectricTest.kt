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
        assertEquals("Swift KeyM v1.0.2-fixed • Created by St3rlight • Team StarDevs", context.getString(R.string.app_credits))
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

    @Test
    fun `gyroscope crosshair haptics and changelog preferences persist and clamp properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ControlLayoutManager.setGyroEnabled(context, true)
        ControlLayoutManager.setGyroMode(context, 99)
        ControlLayoutManager.setGyroSensX(context, 12.5f)
        ControlLayoutManager.setGyroSensY(context, 0.05f)
        ControlLayoutManager.setGyroInvertX(context, true)
        ControlLayoutManager.setGyroInvertY(context, false)
        ControlLayoutManager.setGyroSmoothing(context, 1.8f)

        assertTrue(ControlLayoutManager.isGyroEnabled(context))
        assertEquals(2, ControlLayoutManager.getGyroMode(context))
        assertEquals(8.0f, ControlLayoutManager.getGyroSensX(context), 0.01f)
        assertEquals(0.2f, ControlLayoutManager.getGyroSensY(context), 0.01f)
        assertTrue(ControlLayoutManager.isGyroInvertX(context))
        assertFalse(ControlLayoutManager.isGyroInvertY(context))
        assertEquals(1.0f, ControlLayoutManager.getGyroSmoothing(context), 0.01f)

        ControlLayoutManager.setCrosshairEnabled(context, true)
        ControlLayoutManager.setCrosshairStyle(context, 3)
        assertTrue(ControlLayoutManager.isCrosshairEnabled(context))
        assertEquals(3, ControlLayoutManager.getCrosshairStyle(context))

        ControlLayoutManager.setHapticEnabled(context, false)
        assertFalse(ControlLayoutManager.isHapticEnabled(context))

        ControlLayoutManager.setAutoPauseAimOnMenu(context, false)
        assertFalse(ControlLayoutManager.isAutoPauseAimOnMenu(context))

        ControlLayoutManager.setPositionsLocked(context, true)
        assertTrue(ControlLayoutManager.isPositionsLocked(context))

        ControlLayoutManager.setLastSeenChangelogVersionCode(context, BuildConfig.VERSION_CODE)
        assertEquals(BuildConfig.VERSION_CODE, ControlLayoutManager.getLastSeenChangelogVersionCode(context))
    }

    @Test
    fun `minecraft java and blank canvas presets plus sequential macro serialize accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layouts = ControlLayoutManager.listLayouts(context)
        assertTrue(layouts.any { it.fileName == ControlLayoutManager.DEFAULT_MINECRAFT_FILE && it.gameName == "Minecraft Java" })
        assertTrue(layouts.any { it.fileName == ControlLayoutManager.DEFAULT_BLANK_FILE && it.gameName == "Blank Canvas" && it.buttonCount == 0 })

        val custom = ControlLayoutManager.createBlankCanvasPreset()
        custom.mControlDataList.add(
            ControlData(
                "Combo",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, LwjglGlfwKeycode.GLFW_KEY_F, LwjglGlfwKeycode.GLFW_KEY_R, 0),
                "0.5 * \${screen_width}",
                "0.5 * \${screen_height}",
                60f,
                50f
            ).apply {
                macroSequential = true
            }
        )
        val roundTripped = CustomControls.fromJson(custom.toJson())
        assertEquals(1, roundTripped.mControlDataList.size)
        assertTrue(roundTripped.mControlDataList.first().macroSequential)
    }

    @Test
    fun `input bridge dispatches toggle gyro special action and menu key auto-pause triggers`() {
        var gyroToggleHits = 0
        var menuKeyHits = 0
        com.example.input.InputBridge.setSpecialActionListener { code, isDown ->
            if (code == LwjglGlfwKeycode.SPECIALBTN_TOGGLEGYRO && isDown) {
                gyroToggleHits++
            }
        }
        com.example.input.InputBridge.setMenuKeyListener { code ->
            if (code == LwjglGlfwKeycode.GLFW_KEY_ESCAPE || code == LwjglGlfwKeycode.GLFW_KEY_TAB || code == LwjglGlfwKeycode.GLFW_KEY_M) {
                menuKeyHits++
            }
        }

        com.example.input.InputBridge.sendKey(LwjglGlfwKeycode.SPECIALBTN_TOGGLEGYRO, true)
        com.example.input.InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, true)
        com.example.input.InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_TAB, true)
        com.example.input.InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_M, true)

        assertEquals(1, gyroToggleHits)
        assertEquals(3, menuKeyHits)

        com.example.input.InputBridge.setSpecialActionListener(null)
        com.example.input.InputBridge.setMenuKeyListener(null)
    }

    @Test
    fun `uinput bridge maps mouse buttons and keyboard events to linux input codes`() {
        assertEquals(0x110, com.example.input.UinputBridge.toLinuxMouseButton(MotionEvent.BUTTON_PRIMARY))
        assertEquals(0x111, com.example.input.UinputBridge.toLinuxMouseButton(MotionEvent.BUTTON_SECONDARY))
        assertEquals(0x112, com.example.input.UinputBridge.toLinuxMouseButton(MotionEvent.BUTTON_TERTIARY))
        assertEquals(0, com.example.input.UinputBridge.toLinuxMouseButton(0))

        assertEquals(17, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_W))
        assertEquals(30, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_A))
        assertEquals(31, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_S))
        assertEquals(32, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_D))
        assertEquals(57, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_SPACE))
        assertEquals(1, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(42, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(29, com.example.input.UinputBridge.toLinuxKeyCode(KeyEvent.KEYCODE_CTRL_LEFT))
    }

    @Test
    fun `control button multi-pointer touch holds primary press until owning pointer releases`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = ControlLayout(context)
        layout.layout(0, 0, 1920, 1080)
        val shootData = ControlData(
            "Shoot",
            intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0),
            "0.8 * \${screen_width}",
            "0.5 * \${screen_height}",
            80f,
            80f
        )
        val btn = com.example.customcontrols.ControlButton(context, layout, shootData)
        com.example.input.InputBridge.resetAll()
        assertFalse(com.example.input.InputBridge.isPrimaryMouseDown)

        // Pointer 0 presses button
        val p0Props = MotionEvent.PointerProperties().apply { id = 0 }
        val p0Coords = MotionEvent.PointerCoords().apply { x = 40f; y = 40f }
        val down0 = MotionEvent.obtain(
            100L, 100L, MotionEvent.ACTION_DOWN, 1,
            arrayOf(p0Props), arrayOf(p0Coords), 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        btn.onTouchEvent(down0)
        down0.recycle()
        assertTrue(com.example.input.InputBridge.isPrimaryMouseDown)

        // Pointer 1 also touches and then lifts via ACTION_POINTER_UP -> Shoot must stay pressed!
        val p1Props = MotionEvent.PointerProperties().apply { id = 1 }
        val p1Coords = MotionEvent.PointerCoords().apply { x = 50f; y = 50f }
        val down1 = MotionEvent.obtain(
            100L, 110L,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, arrayOf(p0Props, p1Props), arrayOf(p0Coords, p1Coords), 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        btn.onTouchEvent(down1)
        down1.recycle()
        assertTrue(com.example.input.InputBridge.isPrimaryMouseDown)

        val up1 = MotionEvent.obtain(
            100L, 120L,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, arrayOf(p0Props, p1Props), arrayOf(p0Coords, p1Coords), 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        btn.onTouchEvent(up1)
        up1.recycle()
        assertTrue(com.example.input.InputBridge.isPrimaryMouseDown)

        // Pointer 0 lifts via ACTION_UP -> now Shoot releases cleanly
        val up0 = MotionEvent.obtain(
            100L, 130L, MotionEvent.ACTION_UP, 1,
            arrayOf(p0Props), arrayOf(p0Coords), 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        btn.onTouchEvent(up0)
        up0.recycle()
        assertFalse(com.example.input.InputBridge.isPrimaryMouseDown)
    }

    @Test
    fun `ai aim assist preferences clamp strength between off and 0_5 to 5_0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ControlLayoutManager.setAiAimStrength(context, 0.0f)
        assertEquals(0.0f, ControlLayoutManager.getAiAimStrength(context), 0.01f)

        ControlLayoutManager.setAiAimStrength(context, 0.2f)
        assertEquals(0.0f, ControlLayoutManager.getAiAimStrength(context), 0.01f)

        ControlLayoutManager.setAiAimStrength(context, 2.5f)
        assertEquals(2.5f, ControlLayoutManager.getAiAimStrength(context), 0.01f)

        ControlLayoutManager.setAiAimStrength(context, 9.5f)
        assertEquals(5.0f, ControlLayoutManager.getAiAimStrength(context), 0.01f)

        ControlLayoutManager.setAiCrosshairGatePct(context, 0.80f)
        assertEquals(0.40f, ControlLayoutManager.getAiCrosshairGatePct(context), 0.01f)

        ControlLayoutManager.setAiCrosshairGatePct(context, 0.02f)
        assertEquals(0.10f, ControlLayoutManager.getAiCrosshairGatePct(context), 0.01f)

        ControlLayoutManager.setAiTeamFilterEnabled(context, false)
        assertFalse(ControlLayoutManager.isAiTeamFilterEnabled(context))
        ControlLayoutManager.setAiTeamFilterEnabled(context, true)
        assertTrue(ControlLayoutManager.isAiTeamFilterEnabled(context))
    }

    @Test
    fun `ai vision analyzer locks enemy red tag near crosshair skips teammates and respects gate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ControlLayoutManager.setAiAimStrength(context, 2.5f)
        ControlLayoutManager.setAiCrosshairGatePct(context, 0.22f)
        ControlLayoutManager.setAiTeamFilterEnabled(context, true)
        ControlLayoutManager.setAiOcrNamesEnabled(context, false)
        com.example.vision.AiVisionTerminalBus.clearLogs()

        val w = 320
        val h = 180
        // 1. Enemy red nametag just right of center crosshair -> MUST steer right (+dx)
        val enemyBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        enemyBmp.eraseColor(android.graphics.Color.BLACK)
        val canvas1 = android.graphics.Canvas(enemyBmp)
        val redPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(235, 35, 35) }
        // Center is (160, 90). Place enemy red nametag at x=175..205, y=75..81 (inside 22% gate = 39.6px)
        canvas1.drawRect(175f, 75f, 205f, 82f, redPaint)

        var steeredDx = 0f
        var steeredDy = 0f
        var steerCount = 0
        val analyzerEnemy = com.example.vision.AiAimVisionAnalyzer(context) { dx, dy ->
            steeredDx += dx
            steeredDy += dy
            steerCount++
        }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50))
        analyzerEnemy.processBitmap(enemyBmp)
        enemyBmp.recycle()

        assertTrue("Expected steering toward nearby red enemy nametag", steerCount == 1 && steeredDx > 0.5f)
        assertTrue(com.example.vision.AiVisionTerminalBus.activeTarget.value?.isLocked == true)

        // 2. Red nametag surrounded by Warzone Cyan/Blue squad teammate guide -> MUST skip & NOT steer
        val teamBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        teamBmp.eraseColor(android.graphics.Color.BLACK)
        val canvas2 = android.graphics.Canvas(teamBmp)
        val cyanPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(20, 195, 245) }
        canvas2.drawRect(175f, 75f, 205f, 82f, redPaint)
        canvas2.drawRect(160f, 68f, 174f, 84f, cyanPaint)

        var teamSteerCount = 0
        val analyzerTeam = com.example.vision.AiAimVisionAnalyzer(context) { _, _ ->
            teamSteerCount++
        }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50))
        analyzerTeam.processBitmap(teamBmp)
        teamBmp.recycle()

        assertEquals(0, teamSteerCount)
        assertTrue((com.example.vision.AiVisionTerminalBus.activeTarget.value?.teammatesSkipped ?: 0) >= 1)

        // 3. Enemy red nametag far outside crosshair gate (x=65..95, y=40..46) -> MUST NOT steer
        val farBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        farBmp.eraseColor(android.graphics.Color.BLACK)
        val canvas3 = android.graphics.Canvas(farBmp)
        canvas3.drawRect(65f, 40f, 95f, 46f, redPaint)

        var farSteerCount = 0
        val analyzerFar = com.example.vision.AiAimVisionAnalyzer(context) { _, _ ->
            farSteerCount++
        }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50))
        analyzerFar.processBitmap(farBmp)
        farBmp.recycle()

        assertEquals(0, farSteerCount)
        assertFalse(com.example.vision.AiVisionTerminalBus.activeTarget.value?.isLocked == true)
    }

    @Test
    fun `virtual mouse state and pass through aim delta coalescing hook work cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.input.InputBridge.setVirtualMouseActive(true)
        assertTrue(com.example.input.InputBridge.isVirtualMouseActive)
        com.example.input.InputBridge.sendMouseKeepAlive()

        var coalescedDx = 0f
        var coalescedDy = 0f
        var touchActivityPulses = 0
        val host = object : ControlLayout.WindowOverlayHost {
            override fun getOverlayScreenWidth(): Int = 1920
            override fun getOverlayScreenHeight(): Int = 1080
            override fun attachControlWindow(view: android.view.View, x: Int, y: Int, width: Int, height: Int, visible: Boolean) {}
            override fun updateControlWindow(view: android.view.View, x: Int, y: Int, width: Int, height: Int, visible: Boolean) {}
            override fun detachControlWindow(view: android.view.View) {}
            override fun getControlWindowX(view: android.view.View): Int = 0
            override fun getControlWindowY(view: android.view.View): Int = 0
            override fun onPassThruAimDelta(dx: Float, dy: Float) {
                coalescedDx += dx
                coalescedDy += dy
            }
            override fun onVirtualMouseTouchActivity() {
                touchActivityPulses++
            }
        }
        val layout = ControlLayout(context)
        layout.setWindowOverlayHost(host)
        layout.notifyPassThruAimDelta(4.5f, -2.25f)
        layout.notifyVirtualMouseTouchActivity()

        assertEquals(4.5f, coalescedDx, 0.01f)
        assertEquals(-2.25f, coalescedDy, 0.01f)
        assertEquals(1, touchActivityPulses)

        com.example.input.InputBridge.setVirtualMouseActive(false)
        assertFalse(com.example.input.InputBridge.isVirtualMouseActive)
    }
}
