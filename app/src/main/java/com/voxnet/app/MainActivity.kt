package com.voxnet.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voxnet.app.databinding.ActivityMainBinding
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepo: SettingsRepository
    private var voiceService: VoiceService? = null
    private var serviceBound = false
    private var currentCallState = CallState.DISCONNECTED
    private var useSpeaker = false
    private val timeline = mutableListOf<TimelineEvent>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VoiceService.LocalBinder
            voiceService = binder.getService()
            serviceBound = true
            setupServiceCallbacks()
        }
        override fun onServiceDisconnected(arg0: ComponentName) { serviceBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsRepo = SettingsRepository(this)
        setupUI()
        requestPermissions()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener { connect() }
        binding.btnSettings.setOnClickListener { openSettings() }
        binding.btnCall.setOnClickListener { makeCall() }
        binding.btnEnd.setOnClickListener { endCall() }
        binding.btnSendSms.setOnClickListener { sendSMS() }
        binding.btnSos.setOnClickListener { sendSOS() }
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        binding.btnWifiSettings.setOnClickListener { openWifiSettings() }
        binding.btnMap.setOnClickListener { startActivity(Intent(this, MapActivity::class.java)) }
        binding.btnTimeline.setOnClickListener { showTimelineSheet() }
        updateUI()
    }

    private fun connect() {
        val settings = settingsRepo.getSettings()
        Intent(this, VoiceService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        voiceService?.startVoiceService(settings)
        binding.txtStatus.text = "Connecting..."
    }
    private fun makeCall() {
        val number = binding.edtNumber.text.toString().trim()
        if (number.isEmpty()) { Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show(); return }
        voiceService?.makeCall(number)
    }
    private fun endCall() { voiceService?.endCall() }
    private fun sendSMS() {
        val number = binding.edtNumber.text.toString().trim()
        val message = binding.edtMessage.text.toString().trim()
        if (number.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Please enter number and message", Toast.LENGTH_SHORT).show(); return
        }
        voiceService?.sendSMS(number, message)
        Toast.makeText(this, "Sending SMS...", Toast.LENGTH_SHORT).show()
    }
    private fun sendSOS() { voiceService?.sendSOS(); Toast.makeText(this, "SOS signal sent", Toast.LENGTH_SHORT).show() }
    private fun toggleSpeaker() {
        useSpeaker = !useSpeaker
        voiceService?.toggleSpeaker(useSpeaker)
        binding.btnSpeaker.text = if (useSpeaker) "Earpiece" else "Speaker"
    }
    private fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }
    private fun openWifiSettings() { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }

    private fun setupServiceCallbacks() {
        voiceService?.onCallStateChange = { state ->
            runOnUiThread {
                currentCallState = state
                updateUI()
                binding.txtStatus.text = when (state) {
                    CallState.DISCONNECTED -> "Disconnected"
                    CallState.CONNECTING -> "Connecting..."
                    CallState.DIALING -> "Dialing..."
                    CallState.RINGING -> "Ringing..."
                    CallState.CONNECTED -> "Connected - You can talk now"
                    CallState.ENDED -> "Call ended"
                    CallState.ERROR -> "Error occurred"
                }
            }
        }
        voiceService?.onSMSResult = { _, message -> runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() } }
        voiceService?.timelineListener = { evt -> runOnUiThread { timeline.add(evt) } }
    }

    private fun updateUI() {
        val connected = currentCallState != CallState.DISCONNECTED
        val inCall = currentCallState in listOf(CallState.DIALING, CallState.RINGING, CallState.CONNECTED)
        binding.btnCall.isEnabled = connected && !inCall
        binding.btnEnd.isEnabled = inCall
        binding.btnSendSms.isEnabled = connected && !inCall
        binding.btnSos.isEnabled = connected
        binding.btnSpeaker.isEnabled = connected
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && !grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Audio permissions required", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTimelineSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottomsheet_timeline, null)
        val container = view.findViewById<LinearLayout>(R.id.listContainer)
        container.removeAllViews()
        timeline.sortedBy { it.ts }.takeLast(100).forEach {
            val tv = TextView(this)
            tv.text = "${it.flow} • ${it.stage} • ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(it.ts))}"
            tv.setPadding(0, 8, 0, 8)
            container.addView(tv)
        }
        dialog.setContentView(view)
        dialog.show()
    }
}