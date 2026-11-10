package com.nish.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var targetView: TextView
    private lateinit var field: EditText
    private lateinit var lastBtn: Button

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            textSize = 16f
            text = "1. Tap Open Accessibility Settings, turn Cell Clicker ON.\n" +
                "2. Open your game once. The app is picked up automatically.\n" +
                "3. MOVE: ON shows the circles. Drag each to its spot.\n" +
                "4. MOVE: OFF hides them. They still tap.\n" +
                "5. Press START. To close, drag DRAG HERE down to the X.\n"
        })
        targetView = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(targetView)
        root.addView(TextView(this).apply { text = "Target app package (edit if needed):" })
        field = EditText(this)
        root.addView(field)
        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                sp.edit().putString("game", field.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })
        lastBtn = Button(this)
        lastBtn.setOnClickListener {
            val last = (sp.getString("last", "") ?: "").trim()
            if (last.isNotEmpty()) {
                sp.edit().putString("game", last).apply()
                refresh()
            }
        }
        root.addView(lastBtn)
        root.addView(Button(this).apply {
            text = "Forget target (learn again)"
            setOnClickListener {
                sp.edit().putString("game", "").putString("last", "").apply()
                refresh()
                Toast.makeText(this@MainActivity, "Open your game to set it again", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
    }

    private fun refresh() {
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        var game = (sp.getString("game", "") ?: "").trim()
        val last = (sp.getString("last", "") ?: "").trim()
        if (game.isEmpty() && last.isNotEmpty()) {
            game = last
            sp.edit().putString("game", game).apply()
        }
        field.setText(game)
        targetView.text = if (game.isEmpty())
            "Target app: not set yet. Open your game once and it is picked up automatically."
        else "Target app: " + label(game) + "\n(" + game + ")"
        lastBtn.text = if (last.isEmpty()) "Use last app (none yet)" else "Use last app: " + label(last)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
