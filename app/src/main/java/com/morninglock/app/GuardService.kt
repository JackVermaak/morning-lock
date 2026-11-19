package com.morninglock.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlin.math.ceil

/**
 * Watches which app is in front. While locked, anything that isn't allowed is covered by [LockActivity].
 */
class GuardService : AccessibilityService() {
    private lateinit var state: LockState
    private val handler = Handler(Looper.getMainLooper())
    private var systemAllowed: Set<String> = emptySet()
    private val activityCache = mutableMapOf<ComponentName, Boolean>()

    private var foregroundPackage: String? = null
    private var wasLocked: Boolean? = null
    private var lastLockLaunch = 0L
    private var warningsFor = 0L
    private val warningsShown = mutableSetOf<Int>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Just unlocked: whatever app comes back must not slip past the lock.
            tick(forceLockScreen = true)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        state = LockState(this)
        systemAllowed = buildSystemAllowed()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Only full-screen activities count; ignore popups, dialogs and the notification shade.
        val cls = event.className?.toString() ?: return
        if (!isActivity(ComponentName(pkg, cls))) return
        foregroundPackage = pkg
        tick()
    }

    override fun onInterrupt() {}

    private fun tick(forceLockScreen: Boolean = false) {
        if (!::state.isInitialized || !screenUsable()) return
        state.onUserActive()
        val now = System.currentTimeMillis()
        val locked = state.isLocked(now)
        if (locked != wasLocked) applyRestrictions(locked)
        if (!locked) warnIfFreeTimeEnding(now)
        if (locked && (forceLockScreen || wasLocked != true || !isAllowed(foregroundPackage))) showLockScreen()
        wasLocked = locked
    }

    private fun warnIfFreeTimeEnding(now: Long) {
        val remaining = state.graceRemaining(now)
        if (remaining <= 0) return
        if (warningsFor != state.graceEnd) {
            warningsFor = state.graceEnd
            warningsShown.clear()
        }
        val due = WARN_AT_MINUTES.filter { remaining <= it * 60_000L && it !in warningsShown }
        if (due.isEmpty()) return
        warningsShown += due
        val minutes = ceil(remaining / 60_000.0).toInt()
        val text = if (minutes == 1) "1 minute" else "$minutes minutes"
        Toast.makeText(this, "Morning Lock: $text of free time left", Toast.LENGTH_LONG).show()
    }

    private fun showLockScreen() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLockLaunch < 800) return
        lastLockLaunch = now
        startActivity(
            Intent(this, LockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    /** Optional Battery Saver + grayscale while locked. Needs WRITE_SECURE_SETTINGS granted over adb. */
    private fun applyRestrictions(locked: Boolean) {
        if (!canWriteSecureSettings()) return
        val resolver = contentResolver
        try {
            if (locked) {
                if (state.batterySaverWhileLocked && Settings.Global.getInt(resolver, LOW_POWER, 0) == 0) {
                    Settings.Global.putInt(resolver, LOW_POWER, 1)
                    state.appliedBatterySaver = true
                }
                if (state.grayscaleWhileLocked && Settings.Secure.getInt(resolver, DALTONIZER_ENABLED, 0) == 0) {
                    Settings.Secure.putInt(resolver, DALTONIZER, DALTONIZER_MONOCHROME)
                    Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, 1)
                    state.appliedGrayscale = true
                }
            } else {
                if (state.appliedBatterySaver) {
                    Settings.Global.putInt(resolver, LOW_POWER, 0)
                    state.appliedBatterySaver = false
                }
                if (state.appliedGrayscale) {
                    Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, 0)
                    state.appliedGrayscale = false
                }
            }
        } catch (e: SecurityException) {
            // Permission was revoked; the lock itself still works.
        }
    }

    private fun isAllowed(pkg: String?): Boolean =
        pkg != null && (pkg == packageName || pkg in systemAllowed || pkg in state.allowedApps)

    private fun isActivity(component: ComponentName): Boolean = activityCache.getOrPut(component) {
        try {
            packageManager.getActivityInfo(component, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun screenUsable(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /** Phone, emergency calls, permission prompts, keyboards and system UI always work. */
    private fun buildSystemAllowed(): Set<String> {
        val set = mutableSetOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.emergency",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { set += it.packageName }
        return set
    }

    private companion object {
        const val TICK_MS = 10_000L
        val WARN_AT_MINUTES = listOf(5, 1)
        const val LOW_POWER = "low_power"
        const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val DALTONIZER = "accessibility_display_daltonizer"
        const val DALTONIZER_MONOCHROME = 0
    }
}
