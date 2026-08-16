package com.zeus.code.automation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class PhoneOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var stepTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var pauseIconView: ImageView? = null

    var onPauseClicked: (() -> Unit)? = null
    var onStopClicked: (() -> Unit)? = null

    var isPaused: Boolean = false
        set(value) {
            field = value
            updatePauseIcon()
        }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun show(currentStep: Int = 1, maxSteps: Int = 15, statusText: String = "Initializing...") {
        if (!hasOverlayPermission() || overlayView != null) {
            update(currentStep, maxSteps, statusText)
            return
        }

        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(70f)
        }

        // Outer capsule card
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(14f), dp(8f))
            elevation = dp(10f).toFloat()

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24f).toFloat()
                setColor(0xEE1E1B24.toInt()) // Sleek dark surface container
                setStroke(dp(1.2f), 0x33A088FF.toInt())
            }
        }

        // Purple icon container
        val iconContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f).toFloat()
                setColor(0xFF6B4EFF.toInt()) // Vibrant purple
            }
        }
        val phoneIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.stat_sys_phone_call)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f))
        }
        iconContainer.addView(phoneIcon)
        container.addView(iconContainer)

        // Middle Text column (Step Title + Action description)
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), 0, dp(14f), 0)
            layoutParams = LinearLayout.LayoutParams(dp(180f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val stepTitle = TextView(context).apply {
            text = "Phone Agent $currentStep/$maxSteps"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            paint.isFakeBoldText = true
        }
        stepTextView = stepTitle
        textColumn.addView(stepTitle)

        val statusDesc = TextView(context).apply {
            text = statusText
            setTextColor(0xFFB4ACD0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusTextView = statusDesc
        textColumn.addView(statusDesc)
        container.addView(textColumn)

        // Pause/Resume button
        val pauseButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(0xFFCCCCCC.toInt())
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
            setOnClickListener {
                onPauseClicked?.invoke()
            }
        }
        pauseIconView = pauseButton
        container.addView(pauseButton)

        // Close / Stop button
        val stopButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFFF6B6B.toInt())
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
            setOnClickListener {
                onStopClicked?.invoke()
                hide()
            }
        }
        container.addView(stopButton)

        // Drag handling
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY - (event.rawY - initialTouchY).toInt()
                        runCatching { windowManager.updateViewLayout(container, layoutParams) }
                        return false
                    }
                }
                return false
            }
        })

        overlayView = container
        runCatching { windowManager.addView(container, layoutParams) }
    }

    fun update(currentStep: Int, maxSteps: Int, statusText: String) {
        stepTextView?.post {
            stepTextView?.text = "Phone Agent $currentStep/$maxSteps"
        }
        statusTextView?.post {
            statusTextView?.text = statusText
        }
    }

    private fun updatePauseIcon() {
        pauseIconView?.post {
            pauseIconView?.setImageResource(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            )
        }
    }

    fun hide() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            overlayView = null
            stepTextView = null
            statusTextView = null
            pauseIconView = null
        }
    }
}
