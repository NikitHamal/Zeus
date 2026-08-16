package com.zeus.code.automation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var stepTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var pauseIconView: ImageView? = null

    var onPauseClicked: (() -> Unit)? = null
    var onStopClicked: (() -> Unit)? = null

    var isPaused: Boolean = false
        set(value) {
            field = value
            mainHandler.post { updatePauseIcon() }
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

    fun show(currentStep: Int = 1, maxSteps: Int = 25, statusText: String = "Starting task...") {
        mainHandler.post {
            if (!hasOverlayPermission()) return@post

            if (overlayView != null) {
                update(currentStep, maxSteps, statusText)
                return@post
            }

            val density = context.resources.displayMetrics.density
            fun dp(v: Float) = (v * density).toInt()

            val windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(50f)
            }

            // Outer capsule card
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                elevation = dp(12f).toFloat()

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24f).toFloat()
                    setColor(0xEE1E1B24.toInt()) // Sleek dark container
                    setStroke(dp(1.5f), 0x559D7BFF.toInt())
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
                    setColor(0xFF7C4DFF.toInt()) // Vibrant purple
                }
            }
            val phoneIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.stat_sys_phone_call)
                setColorFilter(Color.WHITE)
            }
            iconContainer.addView(phoneIcon, LinearLayout.LayoutParams(dp(16f), dp(16f)))
            container.addView(iconContainer)

            // Middle Text column
            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10f), 0, dp(12f), 0)
            }

            val stepTitle = TextView(context).apply {
                text = "Step $currentStep of $maxSteps"
                setTextColor(0xFFE2DDF5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                paint.isFakeBoldText = true
            }
            stepTextView = stepTitle
            textColumn.addView(stepTitle)

            val statusDesc = TextView(context).apply {
                text = statusText
                setTextColor(0xFFB4ACD0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            statusTextView = statusDesc
            textColumn.addView(statusDesc)
            container.addView(textColumn, LinearLayout.LayoutParams(dp(170f), LinearLayout.LayoutParams.WRAP_CONTENT))

            // Pause/Resume button
            val pauseButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_pause)
                setColorFilter(0xFFCCCCCC.toInt())
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                setOnClickListener {
                    onPauseClicked?.invoke()
                }
            }
            pauseIconView = pauseButton
            container.addView(pauseButton, LinearLayout.LayoutParams(dp(26f), dp(26f)))

            // Stop button
            val stopButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(0xFFFF6B6B.toInt())
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                setOnClickListener {
                    onStopClicked?.invoke()
                    hide()
                }
            }
            container.addView(stopButton, LinearLayout.LayoutParams(dp(26f), dp(26f)))

            // Drag handling
            container.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = windowParams.x
                            initialY = windowParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true
                            }
                            if (isDragging) {
                                windowParams.x = initialX + dx
                                windowParams.y = initialY + dy
                                runCatching { windowManager.updateViewLayout(container, windowParams) }
                            }
                            return false
                        }
                    }
                    return false
                }
            })

            overlayView = container
            runCatching { windowManager.addView(container, windowParams) }
        }
    }

    fun update(currentStep: Int, maxSteps: Int, statusText: String) {
        mainHandler.post {
            stepTextView?.text = "Step $currentStep of $maxSteps"
            statusTextView?.text = statusText
        }
    }

    private fun updatePauseIcon() {
        pauseIconView?.setImageResource(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        )
    }

    fun hide() {
        mainHandler.post {
            overlayView?.let { view ->
                runCatching { windowManager.removeView(view) }
                overlayView = null
                stepTextView = null
                statusTextView = null
                pauseIconView = null
            }
        }
    }
}
