package com.voxnet.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.voxnet.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsRepo = SettingsRepository(this)
        loadSettings()
        setupUI()
    }
    private fun setupUI() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnBack.setOnClickListener { finish() }
    }
    private fun loadSettings() {
        val s = settingsRepo.getSettings()
        binding.edtSsid.setText(s.ssid)
        binding.edtPassword.setText(s.password)
        binding.edtHost.setText(s.host)
        binding.edtPort.setText(s.port.toString())
        binding.edtCtrlPath.setText(s.ctrlPath)
        binding.edtVoicePath.setText(s.voicePath)
    }
    private fun saveSettings() {
        try {
            val s = VoXNetSettings(
                ssid = binding.edtSsid.text.toString().trim(),
                password = binding.edtPassword.text.toString(),
                host = binding.edtHost.text.toString().trim(),
                port = binding.edtPort.text.toString().toIntOrNull() ?: 80,
                ctrlPath = binding.edtCtrlPath.text.toString().trim(),
                voicePath = binding.edtVoicePath.text.toString().trim()
            )
            if (s.ssid.isEmpty() || s.host.isEmpty()) {
                Toast.makeText(this, "SSID and Host required", Toast.LENGTH_SHORT).show(); return
            }
            settingsRepo.saveSettings(s)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun testConnection() {
        val s = VoXNetSettings(
            ssid = binding.edtSsid.text.toString().trim(),
            password = binding.edtPassword.text.toString(),
            host = binding.edtHost.text.toString().trim(),
            port = binding.edtPort.text.toString().toIntOrNull() ?: 80,
            ctrlPath = binding.edtCtrlPath.text.toString().trim(),
            voicePath = binding.edtVoicePath.text.toString().trim()
        )
        binding.btnTest.isEnabled = false
        binding.btnTest.text = "Testing..."
        settingsRepo.testConnection(s) { _, message ->
            runOnUiThread {
                binding.btnTest.isEnabled = true
                binding.btnTest.text = "Test Connection"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}