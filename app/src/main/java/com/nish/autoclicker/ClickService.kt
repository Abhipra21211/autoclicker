package com.nish.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
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

    companion object {
        const val DEF_GAME = "com.computerlunch.evolution"
    }

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private lateinit var panelParams: WindowManager.LayoutParams
    private var closeView: TextView? = null
    private val dots = ArrayList<View>()
    private val dotParams = ArrayList<WindowManager.LayoutParams>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var startBtn: Button
    private lateinit var moveBtn: Button
    private var running = false
    private var moveMode = false
    private var gameVisible = false
    private var gamePkg = ""
    private var lastSeen = ""
    private var intervalMs = 25L
    private var dotCount = 10
    private var token = 0
    private var closeNear = false

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private var homePkg: String? = null
    private var evCount = 0

    private fun looksSystem(p: String): Boolean {
        val pre = arrayOf("com.android.", "com.google.android.", "android.", "com.miui.", "com.xiaomi.",
            "com.coloros.", "com.oplus.", "com.oppo.", "com.heytap.", "com.vivo.", "com.samsung.",
            "com.sec.android.", "com.realme.", "com.huawei.", "com.mediatek.", "com.qualcomm.")
        return pre.any { p.startsWith(it) } || p.contains("launcher")
    }

    private fun isUserApp(p: String): Boolean {
        return try {
            val ai = packageManager.getApplicationInfo(p, 0)
            val sys = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            (ai.flags and sys) == 0
        } catch (e: Exception) { !looksSystem(p) }
    }

    private fun isHome(p: String): Boolean {
        if (homePkg == null) {
            homePkg = try {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""
            } catch (e: Exception) { "" }
        }
        return p == homePkg || p.contains("launcher")
    }

    private fun appLabel(p: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
    } catch (e: Exception) { p }

    private fun targets(sp: SharedPreferences): List<String> =
        (sp.getString("apps", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = event.packageName?.toString() ?: return
        val sp = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        evCount++
        val ed = sp.edit()
        ed.putInt("ev", evCount)
        if (p != packageName) ed.putString("raw", p)
        ed.apply()
        if (p == packageName || p == "com.android.systemui" || p.contains("inputmethod")) return
        var list = targets(sp)
        val user = isUserApp(p) && !isHome(p)
        if (user && p != lastSeen) {
            lastSeen = p
            sp.edit().putString("last", p).apply()
        }
        val learn = sp.getBoolean("learn", false)
        if (user && !list.contains(p) && (list.isEmpty() || learn)) {
            list = list + p
            sp.edit().putString("apps", list.joinToString(",")).putBoolean("learn", false).apply()
            Toast.makeText(this, "Clicker added for " + appLabel(p), Toast.LENGTH_LONG).show()
        }
        val inGame = list.contains(p)
        if (inGame != gameVisible) {
            gameVisible = inGame
            applyVisibility()
        }
    }

    override fun onInterrupt() { stopClicking() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putLong("conn", System.currentTimeMillis()).apply()
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
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 10))
        }
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { advance(my) }
            override fun onCancelled(g: GestureDescription?) { advance(my) }
        }
        val ok = try { dispatchGesture(builder.build(), cb, null) } catch (e: Exception) { false }
        if (!ok) advance(my)
        handler.postDelayed({ advance(my) }, 1000)
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
        if (::startBtn.isInitialized) startBtn.text = "START"
    }
// end s1

    private fun buildOverlay() {
        val dm = resources.displayMetrics
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val size = dp(44)

        // X target, visible only while the panel is being dragged
        val cv = TextView(this).apply {
            text = "X"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC444444.toInt())
            }
            visibility = View.GONE
        }
        val cp = WindowManager.LayoutParams(
            dp(64), dp(64), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(60) }
        closeView = cv
        wm.addView(cv, cp)

        // tap circles (invisible unless MOVE is ON)
        for (i in 0 until 10) {
            val dotView = TextView(this).apply {
                text = "${i + 1}"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 14f
                alpha = 0f
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

        startBtn = Button(this).apply { text = "START" }
        moveBtn = Button(this).apply { text = "MOVE: OFF" }
        val dotsBtn = Button(this).apply { text = "DOTS: 10" }
        val label = TextView(this).apply { text = "25 ms"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val seek = SeekBar(this).apply { max = 175; progress = 0 }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                intervalMs = (p + 25).toLong()
                label.text = "$intervalMs ms"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        startBtn.setOnClickListener {
            if (running) {
                stopClicking()
            } else {
                if (moveMode) moveBtn.performClick()
                running = true; startBtn.text = "STOP"; handler.post(tick)
            }
        }

        moveBtn.setOnClickListener {
            moveMode = !moveMode
            if (moveMode) stopClicking()
            moveBtn.text = if (moveMode) "MOVE: ON" else "MOVE: OFF"
            for (i in 0 until dots.size) {
                dotParams[i].flags = if (moveMode) baseFlags
                else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                dots[i].alpha = if (moveMode) 1f else 0f
                wm.updateViewLayout(dots[i], dotParams[i])
            }
        }

        dotsBtn.setOnClickListener {
            dotCount = if (dotCount >= 10) 1 else dotCount + 1
            dotsBtn.text = "DOTS: $dotCount"
            applyVisibility()
        }
// end s2

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(startBtn); addView(moveBtn); addView(dotsBtn)
            addView(label); addView(seek)
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
        panelParams = WindowManager.LayoutParams(
            dp(150), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(4); y = dm.heightPixels / 2 }

        var px = 0; var py = 0; var ptx = 0f; var pty = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    px = panelParams.x; py = panelParams.y; ptx = e.rawX; pty = e.rawY
                    closeNear = false
                    closeView?.visibility = View.VISIBLE
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = px + (e.rawX - ptx).toInt()
                    panelParams.y = py + (e.rawY - pty).toInt()
                    wm.updateViewLayout(box, panelParams)
                    val near = nearClose(e.rawX, e.rawY)
                    if (near != closeNear) { closeNear = near; setCloseLook(near) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    closeView?.visibility = View.GONE
                    setCloseLook(false)
                    if (closeNear) { closeNear = false; shutDown() }
                }
            }
            true
        }
        minBtn.setOnClickListener {
            val hide = body.visibility == View.VISIBLE
            body.visibility = if (hide) View.GONE else View.VISIBLE
            minBtn.text = if (hide) "+" else "-"
            panelParams.width = if (hide) dp(130) else dp(150)
            wm.updateViewLayout(box, panelParams)
        }
        panel = box
        wm.addView(box, panelParams)
        applyVisibility()
    }

    private fun nearClose(x: Float, y: Float): Boolean {
        val cv = closeView ?: return false
        if (cv.width == 0) return false
        val loc = IntArray(2)
        cv.getLocationOnScreen(loc)
        val dx = x - (loc[0] + cv.width / 2f)
        val dy = y - (loc[1] + cv.height / 2f)
        val r = dp(80).toFloat()
        return dx * dx + dy * dy < r * r
    }

    private fun setCloseLook(near: Boolean) {
        val cv = closeView ?: return
        (cv.background as GradientDrawable).setColor(if (near) 0xFFE53935.toInt() else 0xCC444444.toInt())
        cv.scaleX = if (near) 1.3f else 1f
        cv.scaleY = if (near) 1.3f else 1f
    }

    // overlay is shown only while the game is in front
    private fun applyVisibility() {
        panel?.visibility = if (gameVisible) View.VISIBLE else View.GONE
        for (i in 0 until dots.size) {
            dots[i].visibility = if (gameVisible && i < dotCount) View.VISIBLE else View.GONE
        }
        if (!gameVisible) stopClicking()
    }

    private fun shutDown() {
        stopClicking(); removeOverlay(); disableSelf()
    }

    private fun removeOverlay() {
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        for (d in dots) { try { wm.removeView(d) } catch (_: Exception) {} }
        try { closeView?.let { wm.removeView(it) } } catch (_: Exception) {}
        dots.clear(); dotParams.clear()
        panel = null; closeView = null
    }
}
// end s3
