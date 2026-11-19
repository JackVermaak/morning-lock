package com.morninglock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

/** The lock screen: today's tasks, the allowed apps, and a way to call for help. */
class LockActivity : Activity() {
    private lateinit var state: LockState
    private var headline: TextView? = null
    private var detail: TextView? = null
    private var shownLocked = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateHeader()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = LockState(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.postDelayed(ticker, 15_000)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    @Deprecated("Still called when predictive back is off, which is the default for this app.")
    override fun onBackPressed() {
        if (!state.isLocked()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun render(): Unit = showColumn {
        shownLocked = state.isLocked()
        headline = text("", 30f, bold = true, topDp = 24)
        detail = text("", 17f, topDp = 8)
        updateHeader()
        if (!state.hasSessionToday) return@showColumn

        val active = state.isSessionActive()
        val done = state.doneTasks()
        heading("Tasks")
        state.tasks.forEachIndexed { index, task ->
            add(CheckBox(context).apply {
                text = task
                textSize = 19f
                isChecked = !active || index in done
                isEnabled = active
                setOnCheckedChangeListener { _, checked ->
                    state.setTaskDone(index, checked)
                    if (!state.isSessionActive()) render()
                }
            })
        }

        if (!active) {
            button("Continue") { goHome() }
            return@showColumn
        }
        if (!shownLocked) return@showColumn

        val apps = allowedApps()
        if (apps.isNotEmpty()) {
            heading("Allowed apps")
            apps.forEach { (label, launch) -> button(label) { startActivity(launch) } }
        }
        heading("Emergency")
        button("Phone") { startActivity(Intent(Intent.ACTION_DIAL)) }
        button("Emergency unlock…") { confirmEmergencyUnlock() }
    }

    private fun updateHeader() {
        val now = System.currentTimeMillis()
        if (state.isLocked(now) != shownLocked) {
            render()
            return
        }
        val (title, body) = when {
            !state.hasSessionToday ->
                "No session yet" to "Today's lock starts the first time you use your phone after %02d:00.".format(state.startHour)
            !state.isSessionActive() ->
                "All done ☀️" to "Your phone is unlocked for the rest of the day."
            state.isLocked(now) ->
                "Phone locked 🔒" to "Finish your morning tasks to unlock it."
            else -> {
                val minutes = ceil(state.graceRemaining(now) / 60_000.0).toInt()
                "$minutes min of free time" to "After that your phone locks until these are done. You can tick them off early."
            }
        }
        headline?.text = title
        detail?.text = body
    }

    private fun allowedApps(): List<Pair<String, Intent>> = state.allowedApps.mapNotNull { pkg ->
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            return@mapNotNull null
        }
        label to launch
    }.sortedBy { it.first.lowercase() }

    private fun confirmEmergencyUnlock() {
        val input = EditText(this)
        val frame = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Emergency unlock")
            .setMessage("This skips today's tasks. To confirm, type:\n\n$EMERGENCY_PHRASE")
            .setView(frame)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString().trim().trimEnd('.').equals(EMERGENCY_PHRASE, ignoreCase = true)) {
                    state.skipToday()
                    render()
                } else {
                    Toast.makeText(this, "That didn't match, so the phone stays locked.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private companion object {
        const val EMERGENCY_PHRASE = "I am choosing to skip my morning tasks today"
    }
}
