package com.vw.carlauncher

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var txtSpeed: TextView
    private lateinit var speedProgress: ProgressBar
    private lateinit var txtRadioFreq: TextView
    private var currentFreq = 94.5
    private var isPlaying = false
    private val themeHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtSpeed = findViewById(R.id.txtSpeed)
        speedProgress = findViewById(R.id.speedProgress)
        txtRadioFreq = findViewById(R.id.txtRadioFreq)
        setupLocation()
        setupGoogleMapsWindow()
        setupRadioAndMedia()
        setupDock()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
        startAutoDayNightTheme()
    }

    private fun setupGoogleMapsWindow() {
        val mapsBox = findViewById<View>(R.id.googleMapsContainer)
        val launchMaps = {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val loc = IntArray(2)
                    mapsBox.getLocationOnScreen(loc)
                    val bounds = Rect(loc[0], loc[1], loc[0] + mapsBox.width, loc[1] + mapsBox.height)
                    try { startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()) } catch (e: Exception) { startActivity(intent) }
                } else startActivity(intent)
            } else Toast.makeText(this, "Google Haritalar yüklü değil", Toast.LENGTH_SHORT).show()
        }
        mapsBox.setOnClickListener { launchMaps() }
        mapsBox.postDelayed({ launchMaps() }, 1200)
    }

    private fun startAutoDayNightTheme() {
        themeHandler.post(object : Runnable {
            override fun run() {
                val isDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18
                findViewById<View>(R.id.rootLayout).setBackgroundColor(Color.parseColor(if (isDay) "#E3EAF2" else "#0F141A"))
                val tc = if (isDay) Color.parseColor("#0F172A") else Color.WHITE
                txtSpeed.setTextColor(tc); txtRadioFreq.setTextColor(tc)
                findViewById<TextView>(R.id.txtMapTitle).setTextColor(tc)
                findViewById<TextView>(R.id.txtMusicTitle).setTextColor(tc)
                themeHandler.postDelayed(this, 60000)
            }
        })
    }
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) } ?: Toast.makeText(this, "Basılı tutup atayın", Toast.LENGTH_SHORT).show()
            }
            slot.setOnLongClickListener {
                val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).sortedBy { it.loadLabel(packageManager).toString() }
                AlertDialog.Builder(this).setTitle("Uygulama Seç").setItems(apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, w ->
                    val p = apps[w].activityInfo.packageName
                    prefs.edit().putString("slot_$idx", p).apply()
                    renderSlot(slot, p)
                }.show()
                true
            }
        }
    }

    private fun renderSlot(slot: LinearLayout, pkg: String) {
        slot.removeAllViews()
        slot.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(68, 68)
            try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) { setImageResource(android.R.drawable.sym_def_app_icon) }
        })
        slot.addView(TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); maxLines = 1
            text = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)) } catch (_: Exception) { "Uygulama" }
        })
    }
}
