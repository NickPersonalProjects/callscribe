package com.nicholaston.callscribe.shizuku

import android.content.Context
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuHealth {
    RUNNING,
    PERMISSION_REQUIRED,
    NOT_RUNNING,
}

object ShizukuHealthPolicy {
    fun map(isAvailable: Boolean, hasPermission: Boolean): ShizukuHealth = when {
        !isAvailable -> ShizukuHealth.NOT_RUNNING
        !hasPermission -> ShizukuHealth.PERMISSION_REQUIRED
        else -> ShizukuHealth.RUNNING
    }
}

class ShizukuHealthMonitor(private val context: Context) {
    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<ShizukuHealth> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        refresh()
    }

    fun stop() {
        if (!listening) return
        listening = false
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    fun refresh() {
        _status.value = currentStatus()
    }

    private fun currentStatus(): ShizukuHealth {
        val available = ShizukuConnectionManager.isAvailable()
        return ShizukuHealthPolicy.map(
            isAvailable = available,
            hasPermission = available && ShizukuConnectionManager.hasPermission(context),
        )
    }
}
