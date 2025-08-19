package com.voxnet.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoiceService : Service() {
    private val binder = LocalBinder()
    private var audioManager: VoXNetAudioManager? = null
    private var webSocketClient: WebSocketClient? = null
    private var isActive = false

    var onCallStateChange: ((CallState) -> Unit)? = null
    var onSMSResult: ((Boolean, String) -> Unit)? = null
    var gpsListener: ((GpsFix) -> Unit)? = null
    var timelineListener: ((TimelineEvent) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }
    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = VoXNetAudioManager(this)
        enableAudioEffects()
    }

    private fun enableAudioEffects() {
        try {
            val sessionId = 0
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable())
                android.media.audiofx.AcousticEchoCanceler.create(sessionId)?.enabled = true
            if (android.media.audiofx.NoiseSuppressor.isAvailable())
                android.media.audiofx.NoiseSuppressor.create(sessionId)?.enabled = true
            if (android.media.audiofx.AutomaticGainControl.isAvailable())
                android.media.audiofx.AutomaticGainControl.create(sessionId)?.enabled = true
        } catch (_: Exception) {}
    }

    fun startVoiceService(settings: VoXNetSettings) {
        if (isActive) return
        startForeground(1, createNotification())
        isActive = true

        webSocketClient = WebSocketClient(settings).apply {
            onControlMessage = { message ->
                if (message.type == MessageTypes.STATUS) {
                    val state = when (message.state) {
                        StatusStates.DIALING -> CallState.DIALING
                        StatusStates.RINGING -> CallState.RINGING
                        StatusStates.CONNECTED -> CallState.CONNECTED
                        StatusStates.ENDED -> CallState.ENDED
                        StatusStates.SMS_SENT -> { onSMSResult?.invoke(true, "SMS sent successfully"); CallState.DISCONNECTED }
                        StatusStates.SMS_FAIL -> { onSMSResult?.invoke(false, "SMS failed to send"); CallState.DISCONNECTED }
                        else -> CallState.ERROR
                    }
                    onCallStateChange?.invoke(state)
                }
            }
            onConnectionChange = { connected ->
                if (connected) {
                    audioManager?.startAudio()
                } else {
                    audioManager?.stopAudio()
                    onCallStateChange?.invoke(CallState.DISCONNECTED)
                }
            }
            onAudioFrame = { frame -> audioManager?.queueAudioFrame(frame) }
            onGpsFix = { fix -> gpsListener?.invoke(fix) }
            onTimelineEvent = { evt -> timelineListener?.invoke(evt) }
        }

        audioManager?.onAudioFrame = { frame -> webSocketClient?.sendAudioFrame(frame) }
        webSocketClient?.connect()
    }

    fun stopVoiceService() {
        if (!isActive) return
        isActive = false
        audioManager?.stopAudio()
        webSocketClient?.disconnect()
        stopForeground(true)
        stopSelf()
    }

    fun makeCall(number: String) {
        webSocketClient?.sendControlMessage(ControlMessage(MessageTypes.CALL_REQ, number = number))
        onCallStateChange?.invoke(CallState.CONNECTING)
    }
    fun endCall() { webSocketClient?.sendControlMessage(ControlMessage(MessageTypes.END)) }
    fun sendSMS(number: String, text: String) {
        webSocketClient?.sendControlMessage(ControlMessage(MessageTypes.SMS_REQ, number = number, text = text))
    }
    fun sendSOS() { webSocketClient?.sendControlMessage(ControlMessage(MessageTypes.SOS)) }
    fun toggleSpeaker(useSpeaker: Boolean) { audioManager?.toggleSpeaker(useSpeaker) }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("VOICE_SERVICE_CHANNEL", "VoXNet Voice Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    private fun createNotification() =
        NotificationCompat.Builder(this, "VOICE_SERVICE_CHANNEL")
            .setContentTitle("VoXNet Active")
            .setContentText("Voice communication service running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .build()
}