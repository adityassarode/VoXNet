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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
        map = MapView(this)
        setContentView(map)

        // Setup offline map support
        setupOfflineMap()

        // Configure path appearance
        path.outlinePaint.strokeWidth = 6f
        path.outlinePaint.color = 0xFF0000FF.toInt() // Blue path

        // Initialize sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun setupOfflineMap() {
        try {
            // Disable internet connection for maps
            map.setUseDataConnection(false)
            
            // Try to load offline MBTiles if available
            val mbtilesFile = File("/sdcard/osmdroid/area.mbtiles")
            if (mbtilesFile.exists()) {
                // Use MBTiles for completely offline operation
                val archive = org.osmdroid.tileprovider.modules.MBTilesFileArchive.getDatabaseFileArchive(mbtilesFile)
                val provider = org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider(
                    org.osmdroid.tileprovider.util.SimpleRegisterReceiver(this),
                    arrayOf(archive)
                )
                val tileProvider = org.osmdroid.tileprovider.MapTileProviderArray(
                    TileSourceFactory.DEFAULT_TILE_SOURCE,
                    null,
                    arrayOf(provider)
                )
                map.setTileProvider(tileProvider)
                Toast.makeText(this, "Offline map tiles loaded from MBTiles", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback to cached tiles only (no internet requests)
                map.setTileSource(TileSourceFactory.MAPNIK)
                Toast.makeText(this, "Using cached tiles only - Place area.mbtiles in /sdcard/osmdroid/", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Fallback to basic mode
            map.setTileSource(TileSourceFactory.MAPNIK)
            Toast.makeText(this, "Map initialization error: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        // Enable touch controls
        map.setMultiTouchControls(true)
        
        // Set default view to Delhi, India (change as needed for your area)
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