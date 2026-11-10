package com.nish.autoclicker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var listBox: LinearLayout
    private lateinit var addLastBtn: Button
    private lateinit var learnBtn: Button
    private lateinit var field: EditText
    private lateinit var diag: TextView

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun apps(sp: SharedPreferences): MutableList<String> =
        (sp.getString("apps", "") ?: "").split(",").map { it.trim() }
            .filter { it.isNotEmpty() }.toMutableList()

    private fun lastOrRaw(sp: SharedPreferences): String {
        val l = (sp.getString("last", "") ?: "").trim()
        return if (l.isNotEmpty()) l else (sp.getString("raw", "") ?: "").trim()
    }

    private fun saveApps(sp: SharedPreferences, list: List<String>) {
        sp.edit().putString("apps", list.joinToString(",")).apply()
    }

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
                "2. Open your first app. It is added automatically.\n" +
                "3. For more apps: tap Add next app I open, then open that app.\n" +
                "4. MOVE: ON shows the circles. Drag each to its spot.\n" +
                "5. MOVE: OFF hides them. They still tap.\n" +
                "6. Press START. To close, drag DRAG HERE down to the X.\n"
        })
        diag = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, pad / 2) }
        root.addView(diag)
        root.addView(TextView(this).apply {
            text = "Apps where the clicker works:"
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 4)
        })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        learnBtn = Button(this)
        learnBtn.setOnClickListener {
            sp.edit().putBoolean("learn", !sp.getBoolean("learn", false)).apply()
            refresh()
        }
        root.addView(learnBtn)

        addLastBtn = Button(this)
        addLastBtn.setOnClickListener {
            val last = lastOrRaw(sp)
            if (last.isNotEmpty()) {
                val l = apps(sp)
                if (!l.contains(last)) { l.add(last); saveApps(sp, l) }
                refresh()
            }
        }
        root.addView(addLastBtn)

        root.addView(TextView(this).apply { text = "\nOr type a package name:" })
        field = EditText(this)
        root.addView(field)
        root.addView(Button(this).apply {
            text = "Add package"
            setOnClickListener {
                val p = field.text.toString().trim()
                if (p.isNotEmpty()) {
                    val l = apps(sp)
                    if (!l.contains(p)) { l.add(p); saveApps(sp, l) }
                    field.setText("")
                    refresh()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Remove all apps"
            setOnClickListener {
                sp.edit().putString("apps", "").putString("last", "").putBoolean("learn", false).apply()
                refresh()
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
        val conn = sp.getLong("conn", 0L)
        val connTxt = if (conn == 0L) "NO - turn it on in Accessibility settings"
            else java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(conn))
        val raw = (sp.getString("raw", "") ?: "").trim()
        diag.text = "Service connected: " + connTxt +
            "\nWindow events seen: " + sp.getInt("ev", 0) +
            "\nLast other app seen: " + (if (raw.isEmpty()) "(none)" else label(raw) + " (" + raw + ")")
        val list = apps(sp)
        val last = lastOrRaw(sp)
        listBox.removeAllViews()
        if (list.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No apps yet. Open your first app and it is added automatically."
            })
        }
        for (pkg in list) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = label(pkg) + "\n(" + pkg + ")"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    val l = apps(sp)
                    l.remove(pkg)
                    saveApps(sp, l)
                    refresh()
                }
            })
            listBox.addView(row)
        }
        addLastBtn.text = if (last.isEmpty()) "Add last app (none yet)" else "Add last app: " + label(last)
        learnBtn.text = if (sp.getBoolean("learn", false))
            "Waiting... open the app now (tap to cancel)" else "Add next app I open"
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
