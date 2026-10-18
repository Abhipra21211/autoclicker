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
    private lateinit var lastView: TextView

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
                "2. Open the game. The panel appears only inside the game.\n" +
                "3. MOVE: ON shows the circles. Drag each one to its spot.\n" +
                "4. MOVE: OFF hides the circles. They still tap.\n" +
                "5. Press START. Set speed with the slider.\n" +
                "6. To close: drag the DRAG HERE bar down to the X.\n"
        })
        lastView = TextView(this).apply { textSize = 14f }
        root.addView(lastView)
        root.addView(TextView(this).apply { text = "\nGame package name (overlay shows only in this app):" })
        val game = EditText(this).apply { setText(sp.getString("game", ClickService.DEF_GAME)) }
        root.addView(game)
        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                sp.edit().putString("game", game.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        lastView.text = "Last other app seen: " + sp.getString("last", "(none yet, open the game first)")
    }
}
