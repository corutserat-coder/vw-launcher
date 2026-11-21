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
        checkPermissions()
        startAutoDayNightTheme()
    }

    private fun setupGoogleMapsWindow() {
        val mapsBox = findViewById<View>(R.id.googleMapsContainer)
        val launchMapsInCenter = {
            val mapsIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (mapsIntent != null) {
                mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val loc = IntArray(2)
                    mapsBox.getLocationOnScreen(loc)
                    val bounds = Rect(loc[0], loc[1], loc[0] + mapsBox.width, loc[1] + mapsBox.height)
                    val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                    try {
                        startActivity(mapsIntent, options.toBundle())
                    } catch (e: Exception) {
                        startActivity(mapsIntent)
                    }
                } else {
                    startActivity(mapsIntent)
                }
            } else {
                Toast.makeText(this, "Cihazda Google Haritalar yüklü değil", Toast.LENGTH_SHORT).show()
            }
        }
        mapsBox.setOnClickListener { launchMapsInCenter() }
        mapsBox.postDelayed({ launchMapsInCenter() }, 1200)
    }

    private fun startAutoDayNightTheme() {
        val runnable = object : Runnable {
            override fun run() {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isDay = hour in 7..18
                val root = findViewById<View>(R.id.rootLayout)
                val mapTitle = findViewById<TextView>(R.id.txtMapTitle)
                val musicTitle = findViewById<TextView>(R.id.txtMusicTitle)
                if (isDay) {
                    root.setBackgroundColor(Color.parseColor("#E3EAF2"))
                    txtSpeed.setTextColor(Color.parseColor("#0F172A"))
                    txtRadioFreq.setTextColor(Color.parseColor("#0F172A"))
                    mapTitle.setTextColor(Color.parseColor("#0F172A"))
                    musicTitle.setTextColor(Color.parseColor("#0F172A"))
                } else {
                    root.setBackgroundColor(Color.parseColor("#0F141A"))
                    txtSpeed.setTextColor(Color.WHITE)
                    txtRadioFreq.setTextColor(Color.WHITE)
                    mapTitle.setTextColor(Color.WHITE)
                    musicTitle.setTextColor(Color.WHITE)
                }
                themeHandler.postDelayed(this, 60000)
            }
        }
        themeHandler.post(runnable)
    }

    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(req, object : LocationCallback() {
                override fun onLocationResult(res: LocationResult) {
                    val loc = res.lastLocation ?: return
                    val kmh = (loc.speed * 3.6).toInt()
                    txtSpeed.text = kmh.toString()
                    speedProgress.progress = kmh.coerceAtMost(220)
                }
            }, Looper.getMainLooper())
        }
    }

    private fun setupRadioAndMedia() {
        val btnRadioPrev = findViewById<ImageButton>(R.id.btnRadioPrev)
        val btnRadioNext = findViewById<ImageButton>(R.id.btnRadioNext)
        val btnOpenRadio = findViewById<TextView>(R.id.btnOpenRadioApp)
        val radioCard = findViewById<View>(R.id.radioCard)

        btnRadioPrev.setOnClickListener {
            currentFreq = if (currentFreq <= 87.5) 108.0 else currentFreq - 0.1
            txtRadioFreq.text = String.format(Locale.US, "%.1f MHz", currentFreq)
            sendBroadcast(Intent("com.android.fmradio.PREV"))
        }

        btnRadioNext.setOnClickListener {
            currentFreq = if (currentFreq >= 108.0) 87.5 else currentFreq + 0.1
            txtRadioFreq.text = String.format(Locale.US, "%.1f MHz", currentFreq)
            sendBroadcast(Intent("com.android.fmradio.NEXT"))
        }

        val openRadioAction = View.OnClickListener { openRadioApp() }
        btnOpenRadio.setOnClickListener(openRadioAction)
        radioCard.setOnClickListener(openRadioAction)

        val btnMusicPrev = findViewById<ImageButton>(R.id.btnMusicPrev)
        val btnMusicPlay = findViewById<ImageButton>(R.id.btnMusicPlayPause)
        val btnMusicNext = findViewById<ImageButton>(R.id.btnMusicNext)
        val musicCard = findViewById<View>(R.id.musicCard)

        btnMusicPrev.setOnClickListener { sendMediaControl(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnMusicNext.setOnClickListener { sendMediaControl(KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnMusicPlay.setOnClickListener {
            isPlaying = !isPlaying
            btnMusicPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            sendMediaControl(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        musicCard.setOnClickListener { openMusicApp() }
    }

    private fun openRadioApp() {
        val pm = packageManager
        val knownRadios = listOf("com.ts.mainui", "com.xyauto.fm", "com.atc.radio", "com.android.fmradio", "com.qf.radio", "com.tw.radio")
        for (pkg in knownRadios) {
            pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it); return }
        }
        for (app in pm.getInstalledApplications(0)) {
            if (app.packageName.contains("radio", true) || app.packageName.contains(".fm", true)) {
                pm.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it); return }
            }
        }
        Toast.makeText(this, "Radyo uygulaması bulunamadı, alttaki slota basılı tutup atayın", Toast.LENGTH_SHORT).show()
    }

    private fun openMusicApp() {
        val pm = packageManager
        val knownPlayers = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.android.music", "com.maxmpz.audioplayer")
        for (pkg in knownPlayers) {
            pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it); return }
        }
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
        } catch (e: Exception) {
            Toast.makeText(this, "Müzik uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMediaControl(keyCode: Int) {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(null)
            if (controllers.isNotEmpty()) {
                val tc = controllers[0].transportControls
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (isPlaying) tc.play() else tc.pause()
                }
                return
            }
        } catch (_: Exception) {}
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun setupDock() {
        val prefs = getSharedPreferences("dock_prefs", MODE_PRIVATE)
        val slotIds = listOf(R.id.dockSlot0, R.id.dockSlot1, R.id.dockSlot2, R.id.dockSlot3, R.id.dockSlot4, R.id.dockSlot5)
        val defaultPkgs = listOf("com.google.android.apps.maps", "com.spotify.music", "com.android.settings", "com.android.chrome", "com.google.android.youtube", "com.android.dialer")

        slotIds.forEachIndexed { index, id ->
            val slot = findViewById<LinearLayout>(id)
            val savedPkg = prefs.getString("slot_$index", defaultPkgs[index]) ?: defaultPkgs[index]
            renderDockSlot(slot, savedPkg)

            slot.setOnClickListener {
                val pkg = prefs.getString("slot_$index", defaultPkgs[index]) ?: ""
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    ?: Toast.makeText(this, "Basılı tutup uygulama atayın", Toast.LENGTH_SHORT).show()
            }
            slot.setOnLongClickListener {
                showAppPicker(index, slot, prefs)
                true
            }
        }
    }

    private fun renderDockSlot(slot: LinearLayout, pkg: String) {
        slot.removeAllViews()
        val pm = packageManager
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(68, 68)
            try { setImageDrawable(pm.getApplicationIcon(pkg)) } catch (e: Exception) { setImageResource(android.R.drawable.sym_def_app_icon) }
        }
        val labelView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            maxLines = 1
            text = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) } catch (e: Exception) { "Uygulama" }
        }
        slot.addView(iconView)
        slot.addView(labelView)
    }

    private fun showAppPicker(index: Int, slot: LinearLayout, prefs: android.content.SharedPreferences) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(intent, 0).sortedBy { it.loadLabel(pm).toString() }
        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Slot ${index + 1} İçin Uygulama Seç")
            .setItems(names) { _, which ->
                val selectedPkg = apps[which].activityInfo.packageName
                prefs.edit().putString("slot_$index", selectedPkg).apply()
                renderDockSlot(slot, selectedPkg)
            }
            .show()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
    }
}
