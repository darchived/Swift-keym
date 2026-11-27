package com.example.input;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.example.BuildConfig;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.asStateFlow;
import rikka.shizuku.Shizuku;

/**
 * Singleton bridge connecting MojoLauncher custom controls to the privileged
 * ShizukuInputService UserService.
 */
public object InputBridge {
    private const val TAG = "InputBridge"

    private val _isShizukuAlive = MutableStateFlow(false)
    val isShizukuAlive: StateFlow<Boolean> = _isShizukuAlive.asStateFlow()

    private val _hasShizukuPermission = MutableStateFlow(false)
    val hasShizukuPermission: StateFlow<Boolean> = _hasShizukuPermission.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    @Volatile
    private var inputService: IInputService? = null

    @Volatile
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    private var listenersRegistered = false

    @Volatile
    private var specialActionListener: ((Int, Boolean) -> Unit)? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        inputService = null
        _isServiceConnected.value = false
        _isShizukuAlive.value = false
        _hasShizukuPermission.value = false
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                inputService = IInputService.Stub.asInterface(service)
                _isServiceConnected.value = true
                Log.i(TAG, "Connected to ShizukuInputService")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inputService = null
            _isServiceConnected.value = false
            Log.w(TAG, "Disconnected from ShizukuInputService")
        }
    }

    fun init() {
        if (!listenersRegistered) {
            listenersRegistered = true
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        refreshShizukuState()
    }

    fun refreshShizukuState() {
        val alive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        _isShizukuAlive.value = alive
        val granted = if (alive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }
        _hasShizukuPermission.value = granted
    }

    fun bindService(context: Context) {
        refreshShizukuState()
        if (!_isShizukuAlive.value || !_hasShizukuPermission.value) return
        if (inputService != null && _isServiceConnected.value) return

        val appContext = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuInputService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("input_service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

        userServiceArgs = args
        try {
            Shizuku.bindUserService(args, serviceConnection)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind ShizukuInputService", t)
        }
    }

    fun unbindService() {
        resetAll()
        val args = userServiceArgs
        if (args != null) {
            try {
                Shizuku.unbindUserService(args, serviceConnection, true)
            } catch (t: Throwable) {
                Log.w(TAG, "Error unbinding ShizukuInputService", t)
            }
        }
        inputService = null
        _isServiceConnected.value = false
    }

    fun setSpecialActionListener(listener: ((Int, Boolean) -> Unit)?) {
        specialActionListener = listener
    }

    @JvmStatic
    fun sendKey(glfwKeycode: Int, down: Boolean) {
        if (glfwKeycode == LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) return

        when (glfwKeycode) {
            LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI -> {
                sendMouseButton(MotionEvent.BUTTON_PRIMARY, down)
                return
            }
            LwjglGlfwKeycode.SPECIALBTN_MOUSESEC -> {
                sendMouseButton(MotionEvent.BUTTON_SECONDARY, down)
                return
            }
            LwjglGlfwKeycode.SPECIALBTN_MOUSEMID -> {
                sendMouseButton(MotionEvent.BUTTON_TERTIARY, down)
                return
            }
            LwjglGlfwKeycode.SPECIALBTN_SCROLLUP -> {
                if (down) sendMouseScroll(1.0f, 0f)
                return
            }
            LwjglGlfwKeycode.SPECIALBTN_SCROLLDOWN -> {
                if (down) sendMouseScroll(-1.0f, 0f)
                return
            }
            LwjglGlfwKeycode.SPECIALBTN_TOGGLECTRL,
            LwjglGlfwKeycode.SPECIALBTN_KEYBOARD,
            LwjglGlfwKeycode.SPECIALBTN_MENU -> {
                specialActionListener?.invoke(glfwKeycode, down)
                return
            }
        }

        val androidKey = LwjglGlfwKeycode.toAndroidKeyCode(glfwKeycode)
        if (androidKey <= 0) return
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val svc = inputService ?: return
        try {
            svc.injectKey(androidKey, action, 0)
        } catch (e: RemoteException) {
            Log.e(TAG, "injectKey failed", e)
        }
    }

    @JvmStatic
    fun sendMouseButton(button: Int, down: Boolean) {
        val svc = inputService ?: return
        try {
            svc.injectMouseButton(button, down)
        } catch (e: RemoteException) {
            Log.e(TAG, "injectMouseButton failed", e)
        }
    }

    @JvmStatic
    fun sendMouseMove(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val svc = inputService ?: return
        try {
            svc.injectMouseMove(dx, dy)
        } catch (e: RemoteException) {
            Log.e(TAG, "injectMouseMove failed", e)
        }
    }

    @JvmStatic
    fun sendMouseScroll(vScroll: Float, hScroll: Float) {
        val svc = inputService ?: return
        try {
            svc.injectMouseScroll(vScroll, hScroll)
        } catch (e: RemoteException) {
            Log.e(TAG, "injectMouseScroll failed", e)
        }
    }

    @JvmStatic
    fun resetAll() {
        val svc = inputService ?: return
        try {
            svc.resetAllInputs()
        } catch (e: RemoteException) {
            Log.w(TAG, "resetAllInputs failed", e)
        }
    }
}
