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
    private val dots = ArrayList<View>()
    private val dotParams = ArrayList<WindowManager.LayoutParams>()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var moveMode = false
    private var intervalMs = 5L
    private var dotCount = 10
    private var token = 0

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

    private val tick = Runnable { if (running) fire() }

    private fun fire() {
        val my = ++token
        val builder = GestureDescription.Builder()
        val n = minOf(dotCount, GestureDescription.getMaxStrokeCount())
        for (i in 0 until n) {
            val lp = dotParams[i]
            val path = Path().apply { moveTo(lp.x + lp.width / 2f, lp.y + lp.height / 2f) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 1))
        }
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { advance(my) }
            override fun onCancelled(g: GestureDescription?) { advance(my) }
        }
        val ok = try { dispatchGesture(builder.build(), cb, null) } catch (e: Exception) { false }
        if (!ok) advance(my)
        // Watchdog: if Android never answers, continue anyway so it never stops
        handler.postDelayed({ advance(my) }, 150)
    }

    private fun advance(my: Int) {
        if (!running || my != token) return
        token++
        handler.postDelayed(tick, intervalMs)
    }

    private fun stopClicking() {
        running = false
        token++
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildOverlay() {
        val dm = resources.displayMetrics
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val size = dp(44)

        for (i in 0 until 10) {
            val dotView = TextView(this).apply {
                text = "${i + 1}"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 14f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x99FF0000.toInt())
                    setStroke(dp(2), Color.WHITE)
                }
            }
            val lp = WindowManager.LayoutParams(
                size, size, type,
                baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val col = i % 2
                val row = i / 2
                x = ((if (col == 0) 0.45f else 0.8f) * dm.widthPixels).toInt() - size / 2
                y = ((0.12f + row * 0.16f) * dm.heightPixels).toInt() - size / 2
            }
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
            dotView.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - tx).toInt()
                        lp.y = sy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(dotView, lp)
                    }
                }
                true
            }
            dots.add(dotView)
            dotParams.add(lp)
            wm.addView(dotView, lp)
        }

        val startBtn = Button(this).apply { text = "START" }
        val moveBtn = Button(this).apply { text = "MOVE: OFF" }
        val dotsBtn = Button(this).apply { text = "DOTS: 10" }
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
            for (i in 0 until dots.size) {
                dotParams[i].flags = if (moveMode) baseFlags
                else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                wm.updateViewLayout(dots[i], dotParams[i])
            }
        }

        dotsBtn.setOnClickListener {
            dotCount = if (dotCount >= 10) 1 else dotCount + 1
            dotsBtn.text = "DOTS: $dotCount"
            for (i in 0 until dots.size) {
                dots[i].visibility = if (i < dotCount) View.VISIBLE else View.GONE
            }
        }

        closeBtn.setOnClickListener {
            stopClicking(); removeOverlay(); disableSelf()
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(startBtn); addView(moveBtn); addView(dotsBtn)
            addView(label); addView(seek); addView(closeBtn)
        }
        val handle = TextView(this).apply {
            text = "DRAG HERE"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minBtn = Button(this).apply {
            text = "-"
            minWidth = 0
            minimumWidth = dp(44)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(handle); addView(minBtn)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(8))
            addView(header); addView(body)
        }
        val boxParams = WindowManager.LayoutParams(
            dp(150), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dm.heightPixels / 2 }

        var px = 0; var py = 0; var ptx = 0f; var pty = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { px = boxParams.x; py = boxParams.y; ptx = e.rawX; pty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    boxParams.x = px + (e.rawX - ptx).toInt()
                    boxParams.y = py + (e.rawY - pty).toInt()
                    wm.updateViewLayout(box, boxParams)
                }
            }
            true
        }
        minBtn.setOnClickListener {
            val hide = body.visibility == View.VISIBLE
            body.visibility = if (hide) View.GONE else View.VISIBLE
            minBtn.text = if (hide) "+" else "-"
            boxParams.width = if (hide) dp(130) else dp(150)
            wm.updateViewLayout(box, boxParams)
        }
        panel = box
        wm.addView(box, boxParams)
    }

    private fun removeOverlay() {
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        for (d in dots) { try { wm.removeView(d) } catch (_: Exception) {} }
        dots.clear(); dotParams.clear()
        panel = null
    }
}
