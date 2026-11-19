package com.morninglock.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persisted settings plus the rules for when the phone is locked.
 *
 * A "session" starts the first time the phone is used on or after [startHour] each day.
 * It gives [graceMinutes] of free time, then the phone is locked until every task is ticked off.
 * Once the tasks are done (or skipped), the phone stays unlocked until the next day's session.
 */
class LockState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("morning_lock", Context.MODE_PRIVATE)

    init {
        // On the day the app is installed, don't lock straight away: the first session is tomorrow.
        if (!prefs.contains(KEY_SESSION_DATE)) {
            prefs.edit().putString(KEY_SESSION_DATE, today()).putString(KEY_COMPLETED_DATE, today()).apply()
        }
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var startHour: Int
        get() = prefs.getInt(KEY_START_HOUR, 5)
        set(value) = prefs.edit().putInt(KEY_START_HOUR, value).apply()

    var graceMinutes: Int
        get() = prefs.getInt(KEY_GRACE_MINUTES, 20)
        set(value) = prefs.edit().putInt(KEY_GRACE_MINUTES, value).apply()

    var tasks: List<String>
        get() = (prefs.getString(KEY_TASKS, null) ?: DEFAULT_TASKS)
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(value) = prefs.edit()
            .putString(KEY_TASKS, value.joinToString("\n"))
            .putStringSet(KEY_DONE, emptySet())
            .apply()

    /** Package names that may be opened while locked. */
    var allowedApps: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED, value).apply()

    var batterySaverWhileLocked: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SAVER, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()

    var grayscaleWhileLocked: Boolean
        get() = prefs.getBoolean(KEY_GRAYSCALE, false)
        set(value) = prefs.edit().putBoolean(KEY_GRAYSCALE, value).apply()

    /** Whether we turned these on, so that unlocking only undoes our own changes. */
    var appliedBatterySaver: Boolean
        get() = prefs.getBoolean(KEY_APPLIED_SAVER, false)
        set(value) = prefs.edit().putBoolean(KEY_APPLIED_SAVER, value).apply()

    var appliedGrayscale: Boolean
        get() = prefs.getBoolean(KEY_APPLIED_GRAYSCALE, false)
        set(value) = prefs.edit().putBoolean(KEY_APPLIED_GRAYSCALE, value).apply()

    /** When today's free time ends (epoch millis). */
    val graceEnd: Long
        get() = prefs.getLong(KEY_GRACE_END, 0)

    val hasSessionToday: Boolean
        get() = enabled && prefs.getString(KEY_SESSION_DATE, null) == today()

    fun doneTasks(): Set<Int> =
        prefs.getStringSet(KEY_DONE, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun setTaskDone(index: Int, done: Boolean) {
        val set = doneTasks().toMutableSet().apply { if (done) add(index) else remove(index) }
        val edit = prefs.edit().putStringSet(KEY_DONE, set.map { it.toString() }.toSet())
        if (tasks.indices.all { it in set }) edit.putString(KEY_COMPLETED_DATE, today())
        edit.apply()
    }

    /** Emergency escape hatch: treat today's tasks as done. */
    fun skipToday() = prefs.edit().putString(KEY_COMPLETED_DATE, today()).apply()

    /** Call whenever the phone is in active use. Starts today's session on the first use after [startHour]. */
    fun onUserActive() {
        if (!enabled || prefs.getString(KEY_SESSION_DATE, null) == today()) return
        if (LocalTime.now().hour < startHour) return
        startSession(System.currentTimeMillis() + graceMinutes * 60_000L)
    }

    fun startSession(graceEndMillis: Long) {
        prefs.edit()
            .putString(KEY_SESSION_DATE, today())
            .putLong(KEY_GRACE_END, graceEndMillis)
            .putStringSet(KEY_DONE, emptySet())
            .remove(KEY_COMPLETED_DATE)
            .apply()
    }

    /** Today's session has started and the tasks aren't done yet (free time or locked). Settings are frozen meanwhile. */
    fun isSessionActive(): Boolean =
        hasSessionToday && prefs.getString(KEY_COMPLETED_DATE, null) != today() && tasks.isNotEmpty()

    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = isSessionActive() && now >= graceEnd

    fun graceRemaining(now: Long = System.currentTimeMillis()): Long =
        if (isSessionActive() && now < graceEnd) graceEnd - now else 0L

    private fun today() = LocalDate.now().toString()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_START_HOUR = "start_hour"
        const val KEY_GRACE_MINUTES = "grace_minutes"
        const val KEY_TASKS = "tasks"
        const val KEY_DONE = "done"
        const val KEY_ALLOWED = "allowed_apps"
        const val KEY_BATTERY_SAVER = "battery_saver"
        const val KEY_GRAYSCALE = "grayscale"
        const val KEY_APPLIED_SAVER = "applied_battery_saver"
        const val KEY_APPLIED_GRAYSCALE = "applied_grayscale"
        const val KEY_SESSION_DATE = "session_date"
        const val KEY_GRACE_END = "grace_end"
        const val KEY_COMPLETED_DATE = "completed_date"

        const val DEFAULT_TASKS = "Make the bed\nDrink a glass of water\n10 minutes of stretching\nRead 10 pages"
    }
}

fun Context.canWriteSecureSettings(): Boolean =
    checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
