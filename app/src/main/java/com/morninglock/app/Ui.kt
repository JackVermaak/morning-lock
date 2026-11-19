package com.morninglock.app

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

// Tiny helpers for building screens in code, so the app needs no extra libraries.

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** Replaces the activity's content with a padded, scrollable column that stays clear of the system bars. */
fun Activity.showColumn(build: LinearLayout.() -> Unit) {
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(32))
        build()
    }
    val scroll = ScrollView(this).apply {
        clipToPadding = false
        addView(column)
        setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }
    setContentView(scroll)
}

fun <T : View> LinearLayout.add(view: T, topDp: Int = 8): T {
    addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(topDp) })
    return view
}

fun LinearLayout.text(value: CharSequence, sizeSp: Float = 16f, bold: Boolean = false, topDp: Int = 4): TextView =
    add(TextView(context).apply {
        text = value
        textSize = sizeSp
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }, topDp)

fun LinearLayout.heading(value: CharSequence): TextView = text(value, 20f, bold = true, topDp = 24)

fun LinearLayout.button(label: CharSequence, onClick: () -> Unit): Button =
    add(Button(context).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    })
