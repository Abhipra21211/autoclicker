package com.nish.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class ClickService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var dot: View? = null
    private lateinit var dotParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var moveMode = false
    private var intervalMs = 5L
    private val BATCH = 10

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopClicking() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (panel == null) buildOverlay()
    }

    override fun onDestroy() {
        stopClicking()
        removeOverlay()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val tick = Runnable {
        if (running) click(dotParams.x + dotParams.width / 2f, dotParams.y + dotParams.height / 2f)
    }

    private fun click(x: Float, y: Float) {
        val builder = GestureDescription.Builder()
        val count = minOf(BATCH, GestureDescription.getMaxStrokeCount())
        for (i in 0 until count) {
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, i * intervalMs, 1))
        }
        val gesture = builder.build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { next() }
            override fun onCancelled(g: GestureDescription?) { next() }
        }
        if (!dispatchGesture(gesture, cb, null)) next()
    }

    private fun next() {
        if (running) handler.post(tick)
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun buildOverlay() {
        val dm = resources.displayMetrics
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        // Red target dot
        val dotView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x88FF0000.toInt())
                setStroke(dp(2), Color.WHITE)
            }
        }
        dotParams = WindowManager.LayoutParams(
            dp(56), dp(56), type,
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels / 2 - dp(28)
            y = dm.heightPixels / 2 - dp(28)
        }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        dotView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = dotParams.x; sy = dotParams.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    dotParams.x = sx + (e.rawX - tx).toInt()
                    dotParams.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(dotView, dotParams)
                }
            }
            true
        }
        dot = dotView
        wm.addView(dotView, dotParams)

        // Control panel
        val startBtn = Button(this).apply { text = "START" }
        val moveBtn = Button(this).apply { text = "MOVE: OFF" }
        val label = TextView(this).apply { text = "5 ms"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val seek = SeekBar(this).apply { max = 95; progress = 0 }
        val closeBtn = Button(this).apply { text = "CLOSE" }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                intervalMs = (p + 5).toLong()
                label.text = "$intervalMs ms"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        startBtn.setOnClickListener {
            if (running) {
                stopClicking(); startBtn.text = "START"
            } else {
                if (moveMode) moveBtn.performClick()
                running = true; startBtn.text = "STOP"; handler.post(tick)
            }
        }
        moveBtn.setOnClickListener {
            moveMode = !moveMode
            if (moveMode) { stopClicking(); startBtn.text = "START" }
            moveBtn.text = if (moveMode) "MOVE: ON" else "MOVE: OFF"
            dotParams.flags = if (moveMode) baseFlags
            else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(dotView, dotParams)
        }
        closeBtn.setOnClickListener {
            stopClicking(); removeOverlay(); disableSelf()
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(startBtn); addView(moveBtn); addView(label); addView(seek); addView(closeBtn)
        }
        val boxParams = WindowManager.LayoutParams(
            dp(170), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(100) }
        panel = box
        wm.addView(box, boxParams)
    }

    private fun removeOverlay() {
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { dot?.let { wm.removeView(it) } } catch (_: Exception) {}
        panel = null; dot = null
    }
}
