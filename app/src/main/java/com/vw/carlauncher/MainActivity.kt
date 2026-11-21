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
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.*

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
//cat << 'EOF' >> app/src/main/java/com/vw/carlauncher/MainActivity.kt

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
                    try { startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()) } catch (_: Exception) { startActivity(intent) }
                } else startActivity(intent)
            } else Toast.makeText(this, "Google Haritalar yüklü değil", Toast.LENGTH_SHORT).show()
        }
        mapsBox.setOnClickListener { launchMaps() }
        mapsBox.postDelayed({ launchMaps() }, 1200)
    }

    private fun startAutoDayNightTheme()
    } {
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
    
    private fun setupRadioAndMedia() {
        findViewById<ImageButton>(R.id.btnRadioPrev).setOnClickListener {
            currentFreq = if (currentFreq <= 87.5) 108.0 else currentFreq - 0.1
            txtRadioFreq.text = String.format(Locale.US, "%.1f MHz", currentFreq)
            sendBroadcast(Intent("com.android.fmradio.PREV"))
        }
        findViewById<ImageButton>(R.id.btnRadioNext).setOnClickListener {
            currentFreq = if (currentFreq >= 108.0) 87.5 else currentFreq + 0.1
            txtRadioFreq.text = String.format(Locale.US, "%.1f MHz", currentFreq)
            sendBroadcast(Intent("com.android.fmradio.NEXT"))
        }
        val openRadio = View.OnClickListener {
            val radios = listOf("com.ts.mainui", "com.xyauto.fm", "com.atc.radio", "com.android.fmradio", "com.qf.radio", "com.tw.radio")
            val found = radios.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                ?: packageManager.getInstalledApplications(0).firstOrNull { it.packageName.contains("radio", true) || it.packageName.contains(".fm", true) }?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
            if (found != null) startActivity(found) else Toast.makeText(this, "Radyo bulunamadı", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnOpenRadioApp).setOnClickListener(openRadio)
        findViewById<View>(R.id.radioCard).setOnClickListener(openRadio)

        val btnPlay = findViewById<ImageButton>(R.id.btnMusicPlayPause)
        findViewById<ImageButton>(R.id.btnMusicPrev).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        findViewById<ImageButton>(R.id.btnMusicNext).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnPlay.setOnClickListener {
            isPlaying = !isPlaying
            btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        findViewById<View>(R.id.musicCard).setOnClickListener {
            val players = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.android.music")
            val found = players.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            if (found != null) startActivity(found) else try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) } catch (_: Exception) {}
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val controllers = (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).getActiveSessions(null)
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
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    
    private fun setupDock() {
        val prefs = getSharedPreferences("dock_prefs", MODE_PRIVATE)
        val ids = listOf(R.id.dockSlot0, R.id.dockSlot1, R.id.dockSlot2, R.id.dockSlot3, R.id.dockSlot4, R.id.dockSlot5)
        val defaults = listOf("com.google.android.apps.maps", "com.spotify.music", "com.android.settings", "com.android.chrome", "com.android.youtube", "com.android.dialer")
        ids.forEachIndexed { idx, id ->
            val slot = findViewById<LinearLayout>(id)
            renderSlot(slot, prefs.getString("slot_$idx", defaults[idx]) ?: defaults[idx])
            slot.setOnClickListener {
                val pkg = prefs.getString("slot_$idx", defaults[idx]) ?: ""
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
