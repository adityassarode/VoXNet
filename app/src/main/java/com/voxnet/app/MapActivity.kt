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
    private var marker: Marker? = null
    private val path = Polyline()
    private val points = mutableListOf<GeoPoint>()

    private var sensorManager: SensorManager? = null
    private var voiceService: VoiceService? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as VoiceService.LocalBinder
            voiceService = binder.getService()
            bound = true
            voiceService?.gpsListener = { fix ->
                runOnUiThread {
                    val gp = GeoPoint(fix.lat, fix.lon)
                    if (marker == null) {
                        marker = Marker(map).apply {
                            position = gp
                            title = "Current Position"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(gp)
                    } else {
                        marker?.position = gp
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

        // Configure osmdroid
        Configuration.getInstance().userAgentValue = packageName

        // Create map view
        map = MapView(this)
        setContentView(map)

        // Setup tiles (offline MBTiles if present, otherwise cached tiles)
        setupOfflineMap()

        // Path styling
        path.outlinePaint.strokeWidth = 6f
        path.outlinePaint.color = 0xFF0000FF.toInt() // Blue

        // Sensors for heading
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun setupOfflineMap() {
        try {
            // Avoid any network fetches
            map.setUseDataConnection(false)

            val mbtilesFile = File("/sdcard/osmdroid/area.mbtiles")
            if (mbtilesFile.exists()) {
                // 1) Define an offline tile source (adjust min/max zoom to your mbtiles)
                val offlineSource = XYTileSource(
                    "mbtiles",   // arbitrary name
                    0,           // min zoom
                    19,          // max zoom (adjust if your MBTiles uses different range)
                    256,
                    ".png",
                    null
                )

                // 2) Create archive provider bound to that tile source
                val archive = MBTilesFileArchive.getDatabaseFileArchive(mbtilesFile)
                val archiveProvider = MapTileFileArchiveProvider(
                    SimpleRegisterReceiver(this),
                    offlineSource,                 // ITileSource
                    arrayOf(archive)               // archives to read
                )

                // 3) Build a provider array for the map
                val providerArray = MapTileProviderArray(
                    offlineSource,                 // ITileSource
                    null,
                    arrayOf(archiveProvider)
                )

                map.setTileProvider(providerArray)
                map.setTileSource(offlineSource)
                Toast.makeText(this, "Offline MBTiles loaded", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback: cached tiles only (no internet)
                map.setTileSource(TileSourceFactory.MAPNIK)
                Toast.makeText(this, "Place area.mbtiles in /sdcard/osmdroid/", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            map.setTileSource(TileSourceFactory.MAPNIK)
            Toast.makeText(this, "Map init error: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        map.setMultiTouchControls(true)
        // Default view (change to your operation area if desired)
        map.controller.setZoom(10.0)
        map.controller.setCenter(GeoPoint(28.6139, 77.2090))
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