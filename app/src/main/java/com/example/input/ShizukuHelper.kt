package com.example.input

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Manages communication with the Shizuku API for binder readiness, permission handling,
 * UserService binding, and privileged shell command execution.
 */
object ShizukuHelper {

    data class ShellResult(
        val exitCode: Int,
        val output: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isShizukuAlive: StateFlow<Boolean> get() = InputBridge.isShizukuAlive
    val hasShizukuPermission: StateFlow<Boolean> get() = InputBridge.hasShizukuPermission
    val isServiceConnected: StateFlow<Boolean> get() = InputBridge.isServiceConnected

    fun init() {
        InputBridge.init()
    }

    fun refreshState() {
        InputBridge.refreshShizukuState()
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun shouldShowPermissionRationale(): Boolean {
        return try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (!isShizukuAvailable()) return
        if (hasPermission()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
        }
    }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    fun bindInputService(context: Context) {
        InputBridge.bindService(context)
    }

    fun destroyUinput() {
        InputBridge.destroyUinput()
    }

    fun unbindInputService() {
        InputBridge.unbindService()
    }

    /**
     * Executes a shell command synchronously through the bound Shizuku UserService.
     * Must be called off the main thread.
     */
    fun executeShellCommand(command: String): ShellResult {
        if (!isShizukuAvailable()) {
            return ShellResult(-1, "Shizuku daemon is not running.")
        }
        if (!hasPermission()) {
            return ShellResult(-1, "Shizuku permission is not granted.")
        }
        val (code, out) = InputBridge.execShell(command)
        return ShellResult(code, out)
    }

    /**
     * Executes a shell command asynchronously through Shizuku and delivers the result on Main.
     */
    fun executeShellCommandAsync(
        context: Context,
        command: String,
        onResult: (ShellResult) -> Unit
    ) {
        bindInputService(context)
        scope.launch {
            val result = executeShellCommand(command)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun unlockRestrictedAndOverlayAsync(
        context: Context,
        onResult: (Boolean) -> Unit
    ) {
        if (!isShizukuAvailable() || !hasPermission()) {
            onResult(false)
            return
        }
        bindInputService(context)
        val pkg = context.packageName
        scope.launch {
            val granted = InputBridge.allowOverlayAppOps(pkg)
            withContext(Dispatchers.Main) {
                onResult(granted)
            }
        }
    }
}
