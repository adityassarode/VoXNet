package com.voxnet.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

class MapActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var map: MapView
    private lateinit var overlayStatus: TextView
    private var marker: Marker? = null
    private val path = Polyline()
    private val points = mutableListOf<GeoPoint>()

    private var sensorManager: SensorManager? = null
    private var voiceService: VoiceService? = null
    private var bound = false
    private var gotFirstFix = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as VoiceService.LocalBinder
            voiceService = binder.getService()
            bound = true
            voiceService?.gpsListener = { fix ->
                runOnUiThread {
                    overlayStatus.visibility = TextView.GONE
                    val gp = GeoPoint(fix.lat, fix.lon)
                    if (marker == null) {
                        marker = Marker(map).apply {
                            position = gp
                            title = "Current Position"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                    } else {
                        marker?.position = gp
                    }
                    if (!gotFirstFix) {
                        gotFirstFix = true
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(gp)
                    }
                    points.add(gp)
                    path.setPoints(points)
                    if (!map.overlays.contains(path)) map.overlays.add(path)
                    map.invalidate()
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        overlayStatus = findViewById(R.id.overlayStatus)

        setupOfflineMap()

        path.outlinePaint.strokeWidth = 6f
        path.outlinePaint.color = 0xFF0000FF.toInt()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun setupOfflineMap() {
        try {
            map.setUseDataConnection(false)

            val mbtilesFile = File("/sdcard/osmdroid/area.mbtiles")
            if (mbtilesFile.exists()) {
                val offlineSource = XYTileSource(
                    "mbtiles",
                    0,     // min zoom in your MBTiles
                    19,    // max zoom (adjust to your MBTiles)
                    256,
                    ".png", // change to ".jpg" if your MBTiles uses JPEG tiles
                    null
                )
                val archive = MBTilesFileArchive.getDatabaseFileArchive(mbtilesFile)
                val archiveProvider = MapTileFileArchiveProvider(
                    SimpleRegisterReceiver(this),
                    offlineSource,
                    arrayOf(archive)
                )
                val providerArray = MapTileProviderArray(
                    offlineSource,
                    null,
                    arrayOf(archiveProvider)
                )
                map.setTileProvider(providerArray)
                map.setTileSource(offlineSource)
            } else {
                map.setTileSource(TileSourceFactory.MAPNIK) // cached-only fallback
                Toast.makeText(this, "Offline tiles not found. Copy area.mbtiles to /sdcard/osmdroid/", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            map.setTileSource(TileSourceFactory.MAPNIK)
            Toast.makeText(this, "Map init error: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        map.setMultiTouchControls(true)
        // Neutral initial view until GPS arrives
        map.controller.setZoom(3.0)
        map.controller.setCenter(GeoPoint(0.0, 0.0))
        overlayStatus.visibility = TextView.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, VoiceService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (bound) unbindService(conn)
        bound = false
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR && marker != null) {
            val rot = FloatArray(9)
            val orient = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rot, event.values)
            SensorManager.getOrientation(rot, orient)
            val headingDeg = Math.toDegrees(orient[0].toDouble()).toFloat()
            marker?.rotation = headingDeg
            map.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}