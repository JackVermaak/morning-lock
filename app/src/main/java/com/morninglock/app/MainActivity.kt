package com.morninglock.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Setup and settings. Redirects to the lock screen while locked. */
class MainActivity : Activity() {
    private lateinit var state: LockState
    private var pendingAllowed = mutableSetOf<String>()

    private lateinit var enabledSwitch: Switch
    private lateinit var hourField: EditText
    private lateinit var graceField: EditText
    private lateinit var tasksField: EditText
    private lateinit var saverBox: CheckBox
    private lateinit var grayBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = LockState(this)
    }

    override fun onResume() {
        super.onResume()
        if (state.isLocked()) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
            return
        }
        pendingAllowed = state.allowedApps.toMutableSet()
        render()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun render(): Unit = showColumn {
        text(getString(R.string.app_name), 30f, bold = true, topDp = 24)

        heading("Status")
        val guardOn = isGuardEnabled()
        if (guardOn) {
            text("✅ Guard is on.")
        } else {
            text("⚠️ Guard is off. The lock can't work until you turn on \"Morning Lock guard\" in Accessibility settings.")
            button("Turn on guard") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            text(
                "If Android says it's a restricted setting: open App info, tap ⋮ in the top right → " +
                    "\"Allow restricted settings\", then try again.",
                14f,
            )
            button("Open app info") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            text("⚠️ OxygenOS may stop the guard to save battery.", topDp = 12)
            @SuppressLint("BatteryLife")
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            button("Let it run in the background") { startActivity(request) }
        }
        text(todayStatus(), topDp = 12)
        button("Open today's tasks") { startActivity(Intent(this@MainActivity, LockActivity::class.java)) }

        heading("Settings")
        val frozen = state.isSessionActive()
        if (frozen) text("Settings are locked until today's tasks are done.", bold = true)

        enabledSwitch = add(Switch(context).apply {
            text = "Morning Lock on"
            textSize = 16f
            isChecked = state.enabled
        })
        text("Start at the first unlock on or after this hour (0–23)", topDp = 16)
        hourField = add(numberField(state.startHour), topDp = 0)
        text("Free minutes before the lock", topDp = 12)
        graceField = add(numberField(state.graceMinutes), topDp = 0)
        text("Tasks to unlock (one per line)", topDp = 12)
        tasksField = add(EditText(context).apply {
            setText(state.tasks.joinToString("\n"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }, topDp = 0)

        lateinit var appsButton: Button
        appsButton = button(appsLabel()) { pickApps { appsButton.text = appsLabel() } }

        saverBox = add(CheckBox(context).apply {
            text = "Turn on Battery Saver while locked"
            isChecked = state.batterySaverWhileLocked
        }, topDp = 12)
        grayBox = add(CheckBox(context).apply {
            text = "Grayscale screen while locked"
            isChecked = state.grayscaleWhileLocked
        }, topDp = 0)
        if (!canWriteSecureSettings()) {
            text(
                "These two need a one-time permission from a computer with adb:\n" +
                    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
                13f,
            ).setTextIsSelectable(true)
        }

        val save = button("Save") { save() }
        val test = button("Test it now (30 seconds of free time)") { confirmTest() }
        test.isEnabled = guardOn

        if (frozen) {
            listOf(enabledSwitch, hourField, graceField, tasksField, appsButton, saverBox, grayBox, save, test)
                .forEach { it.isEnabled = false }
        }
    }

    private fun numberField(value: Int) = EditText(this).apply {
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun save(): Boolean {
        val hour = hourField.text.toString().toIntOrNull()
        val grace = graceField.text.toString().toIntOrNull()
        if (hour == null || hour !in 0..23 || grace == null || grace !in 0..240) {
            Toast.makeText(this, "The hour must be 0–23 and the minutes 0–240.", Toast.LENGTH_LONG).show()
            return false
        }
        state.enabled = enabledSwitch.isChecked
        state.startHour = hour
        state.graceMinutes = grace
        val tasks = tasksField.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (tasks != state.tasks) state.tasks = tasks
        state.allowedApps = pendingAllowed.toSet()
        state.batterySaverWhileLocked = saverBox.isChecked
        state.grayscaleWhileLocked = grayBox.isChecked
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        render()
        return true
    }

    private fun appsLabel() = "Apps allowed while locked: ${pendingAllowed.size} selected"

    private fun pickApps(onDone: () -> Unit) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val checked = BooleanArray(apps.size) { apps[it].first in pendingAllowed }
        AlertDialog.Builder(this)
            .setTitle("Apps allowed while locked")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) pendingAllowed += apps[which].first else pendingAllowed -= apps[which].first
            }
            .setPositiveButton("Done") { _, _ -> onDone() }
            .setOnCancelListener { onDone() }
            .show()
    }

    private fun confirmTest() {
        AlertDialog.Builder(this)
            .setTitle("Test the lock?")
            .setMessage(
                "This saves your settings and starts today's session now, with 30 seconds of free time. " +
                    "After that your phone locks until you tick off your tasks (or use the emergency unlock). " +
                    "It replaces today's real morning session."
            )
            .setPositiveButton("Start test") { _, _ ->
                if (save()) {
                    state.startSession(System.currentTimeMillis() + 30_000)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun todayStatus(): String {
        val now = System.currentTimeMillis()
        return when {
            !state.enabled -> "Morning Lock is off."
            !state.hasSessionToday ->
                "Today's lock starts the first time you use your phone after %02d:00.".format(state.startHour)
            !state.isSessionActive() -> "Today's tasks are done. Your phone is unlocked until tomorrow."
            else -> {
                val end = Instant.ofEpochMilli(state.graceEnd).atZone(ZoneId.systemDefault()).toLocalTime()
                val minutes = ceil(state.graceRemaining(now) / 60_000.0).toInt()
                "Free time ends at ${end.format(DateTimeFormatter.ofPattern("HH:mm"))} ($minutes min left)."
            }
        }
    }

    private fun isGuardEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, GuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
