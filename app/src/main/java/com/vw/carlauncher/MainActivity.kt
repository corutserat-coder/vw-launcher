package com.vw.carlauncher
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.AudioManager
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {
    private lateinit var osmMap: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var txtSpeed: TextView
    private lateinit var speedProgress: ProgressBar
    private lateinit var txtNavDistance: TextView
    private lateinit var txtNavStreet: TextView
    private lateinit var txtRadioFreq: TextView
    private var currentFreq = 94.5
    private val slotIds = listOf(R.id.dockSlot0, R.id.dockSlot1, R.id.dockSlot2, R.id.dockSlot3, R.id.dockSlot4, R.id.dockSlot5)

    private val navReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dist = intent?.getStringExtra("distance") ?: return
            val street = intent?.getStringExtra("street") ?: ""
            if (dist.isNotEmpty()) { txtNavDistance.text = dist; txtNavStreet.text = street }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)
        txtSpeed = findViewById(R.id.txtSpeed)
        speedProgress = findViewById(R.id.speedProgress)
        txtNavDistance = findViewById(R.id.txtNavDistance)
        txtNavStreet = findViewById(R.id.txtNavStreet)
        txtRadioFreq = findViewById(R.id.txtRadioFreq)
        setupOsmMap()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupRadioControls(); setupMusicControls(); setupBottomDock(); checkPermissions()
    }

    private fun setupOsmMap() {
        osmMap = findViewById(R.id.osmMap)
        osmMap.setTileSource(TileSourceFactory.MAPNIK)
        osmMap.setMultiTouchControls(true)
        osmMap.setBuiltInZoomControls(false)
        osmMap.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), osmMap)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        osmMap.overlays.add(locationOverlay)
        osmMap.controller.setZoom(17.5)
    }

    override fun onResume() { super.onResume(); osmMap.onResume(); registerReceiver(navReceiver, IntentFilter("com.vw.carlauncher.NAV_UPDATE")) }
    override fun onPause() { super.onPause(); osmMap.onPause(); unregisterReceiver(navReceiver) }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                val speedKmh = (loc.speed * 3.6).toInt()
                txtSpeed.text = speedKmh.toString()
                speedProgress.progress = speedKmh.coerceAtMost(220)
                osmMap.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                if (loc.hasBearing() && loc.speed > 1.5f) osmMap.mapOrientation = -loc.bearing
            }
        }, Looper.getMainLooper())
    }

    private fun setupRadioControls() {
        findViewById<ImageButton>(R.id.btnRadioPrev).setOnClickListener {
            currentFreq = ((currentFreq - 0.1) * 10).toInt() / 10.0
            if (currentFreq < 87.5) currentFreq = 108.0
            txtRadioFreq.text = "$currentFreq MHz"
        }
        findViewById<ImageButton>(R.id.btnRadioNext).setOnClickListener {
            currentFreq = ((currentFreq + 0.1) * 10).toInt() / 10.0
            if (currentFreq > 108.0) currentFreq = 87.5
            txtRadioFreq.text = "$currentFreq MHz"
        }
        findViewById<TextView>(R.id.btnOpenRadioApp).setOnClickListener {
            val radioPackages = listOf("com.ts.MainUI", "com.atc.radio", "com.xyauto.radio", "com.qf.radio")
            for (pkg in radioPackages) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { startActivity(intent); return@setOnClickListener }
            }
            Toast.makeText(this, "Radyo uygulamasını alt bara basılı tutup atayabilirsiniz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMusicControls() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun sendMediaKey(keyCode: Int) {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        findViewById<ImageButton>(R.id.btnMusicPrev).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        findViewById<ImageButton>(R.id.btnMusicPlayPause).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        findViewById<ImageButton>(R.id.btnMusicNext).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
    }

    private fun setupBottomDock() {
        val prefs = getSharedPreferences("dock_prefs", Context.MODE_PRIVATE)
        val defaultPackages = listOf("com.google.android.apps.maps", "com.spotify.music", "com.android.settings", "com.android.chrome", "com.google.android.youtube", "com.android.dialer")
        slotIds.forEachIndexed { index, viewId ->
            val container = findViewById<LinearLayout>(viewId)
            val savedPkg = prefs.getString("slot_$index", defaultPackages[index]) ?: defaultPackages[index]
            renderDockSlot(container, index, savedPkg)
        }
    }

    private fun renderDockSlot(container: LinearLayout, slotIndex: Int, packageName: String) {
        container.removeAllViews()
        val pm = packageManager
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(54, 54)
            try { setImageDrawable(pm.getApplicationIcon(packageName)) } catch (e: Exception) { setImageResource(android.R.drawable.sym_def_app_icon) }
        }
        val labelView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; maxLines = 1
            text = try { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() } catch (e: Exception) { "Uygulama Seç" }
        }
        container.addView(iconView); container.addView(labelView)
        container.setOnClickListener {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) startActivity(launchIntent) else showAppPickerDialog(container, slotIndex)
        }
        container.setOnLongClickListener { showAppPickerDialog(container, slotIndex); true }
    }

    private fun showAppPickerDialog(container: LinearLayout, slotIndex: Int) {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }, 0).sortedBy { it.loadLabel(pm).toString() }
        val appNames = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Slot ${slotIndex + 1} İçin Uygulama Seç").setItems(appNames) { _, which ->
            val selectedPkg = apps[which].activityInfo.packageName
            getSharedPreferences("dock_prefs", Context.MODE_PRIVATE).edit().putString("slot_$slotIndex", selectedPkg).apply()
            renderDockSlot(container, slotIndex, selectedPkg)
        }.show()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        } else { startLocationUpdates() }
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            Toast.makeText(this, "Navigasyon yönleri için Bildirim Erişimi izni verin", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
    }
}
