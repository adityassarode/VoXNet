package com.voxnet.app

import com.google.gson.Gson
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.Timer
import kotlin.concurrent.schedule

class WebSocketClient(private val settings: VoXNetSettings) {
    private var client = buildClient()
    private var ctrlWebSocket: WebSocket? = null
    private var voiceWebSocket: WebSocket? = null
    private val gson = Gson()

    var onControlMessage: ((ControlMessage) -> Unit)? = null
    var onConnectionChange: ((Boolean) -> Unit)? = null
    var onAudioFrame: ((AudioFrame) -> Unit)? = null
    var onGpsFix: ((GpsFix) -> Unit)? = null
    var onTimelineEvent: ((TimelineEvent) -> Unit)? = null

    private var hbTimer: Timer? = null
    private var missed = 0
    private var reconnectAttempts = 0

    private fun buildClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    fun connect() {
        reconnectAttempts = 0
        openControl()
        openVoice()
    }

    fun disconnect() {
        hbStop()
        ctrlWebSocket?.close(1000, "Closing")
        voiceWebSocket?.close(1000, "Closing")
        ctrlWebSocket = null
        voiceWebSocket = null
    }

    fun sendControlMessage(message: ControlMessage) {
        val json = gson.toJson(message)
        ctrlWebSocket?.send(json)
        when (message.type) {
            MessageTypes.CALL_REQ -> onTimelineEvent?.invoke(TimelineEvent("CALL", "CALL_REQ_SENT"))
            MessageTypes.SMS_REQ -> onTimelineEvent?.invoke(TimelineEvent("SMS", "SMS_REQ_SENT"))
            MessageTypes.SOS     -> onTimelineEvent?.invoke(TimelineEvent("SOS", "SOS_SENT"))
        }
    }

    fun sendAudioFrame(frame: AudioFrame) {
        val buffer = ByteArray(12 + frame.data.size)
        buffer[0] = (frame.seq shr 24).toByte()
        buffer[1] = (frame.seq shr 16).toByte()
        buffer[2] = (frame.seq shr 8).toByte()
        buffer[3] = frame.seq.toByte()
        var ts = frame.timestamp
        for (i in 4..11) {
            buffer[11 - i + 4] = ts.toByte()
            ts = ts shr 8
        }
        System.arraycopy(frame.data, 0, buffer, 12, frame.data.size)
        voiceWebSocket?.send(ByteString.of(*buffer))
    }

    private fun openControl() {
        val url = "ws://${settings.host}:${settings.port}${settings.ctrlPath}"
        val request = Request.Builder().url(url).build()
        ctrlWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, resp: Response) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_CTRL_OPEN"))
                if (voiceWebSocket != null) {
                    onConnectionChange?.invoke(true)
                    hbStart()
                }
            }
            override fun onMessage(ws: WebSocket, text: String) {
                missed = 0
                try {
                    val map = gson.fromJson(text, Map::class.java) as Map<*, *>
                    when (map["type"]) {
                        "STATUS" -> {
                            val state = (map["state"] as? String) ?: ""
                            onControlMessage?.invoke(ControlMessage("STATUS", state = state))
                            onTimelineEvent?.invoke(TimelineEvent("CALL", state))
                        }
                        "GPS" -> {
                            val fix = GpsFix(
                                lat  = (map["lat"] as Number).toDouble(),
                                lon  = (map["lon"] as Number).toDouble(),
                                alt  = (map["alt"] as? Number)?.toDouble(),
                                acc  = (map["acc"] as? Number)?.toDouble(),
                                spd  = (map["spd"] as? Number)?.toDouble(),
                                head = (map["head"] as? Number)?.toDouble(),
                                ts   = (map["ts"] as? Number)?.toLong()
                            )
                            onGpsFix?.invoke(fix)
                        }
                        "EVENT" -> {
                            val stage = (map["stage"] as? String) ?: "UNKNOWN"
                            val flow = (map["flow"] as? String) ?: "CALL"
                            onTimelineEvent?.invoke(TimelineEvent(flow, stage))
                        }
                        else -> {
                            val msg = gson.fromJson(text, ControlMessage::class.java)
                            onControlMessage?.invoke(msg)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_CTRL_CLOSED"))
                onConnectionChange?.invoke(false)
                hbStop()
                scheduleReconnect()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_CTRL_FAIL"))
                onConnectionChange?.invoke(false)
                hbStop()
                scheduleReconnect()
            }
        })
    }

    private fun openVoice() {
        val url = "ws://${settings.host}:${settings.port}${settings.voicePath}"
        val request = Request.Builder().url(url).build()
        voiceWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, resp: Response) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_VOICE_OPEN"))
                if (ctrlWebSocket != null) {
                    onConnectionChange?.invoke(true)
                    hbStart()
                }
            }
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (bytes.size >= 12) {
                    val b = bytes.toByteArray()
                    val seq = ((b[0].toInt() and 0xFF) shl 24) or
                              ((b[1].toInt() and 0xFF) shl 16) or
                              ((b[2].toInt() and 0xFF) shl 8) or
                              (b[3].toInt() and 0xFF)
                    var ts = 0L
                    for (i in 4..11) ts = (ts shl 8) or (b[i].toLong() and 0xFF)
                    val audio = ByteArray(b.size - 12)
                    System.arraycopy(b, 12, audio, 0, audio.size)
                    onAudioFrame?.invoke(AudioFrame(seq, ts, audio))
                }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_VOICE_CLOSED"))
                onConnectionChange?.invoke(false)
                hbStop()
                scheduleReconnect()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                onTimelineEvent?.invoke(TimelineEvent("CONNECT", "WS_VOICE_FAIL"))
                onConnectionChange?.invoke(false)
                hbStop()
                scheduleReconnect()
            }
        })
    }

    private fun hbStart() {
        hbStop()
        missed = 0
        hbTimer = Timer()
        hbTimer?.schedule(delay = 0L, period = 5000L) {
            try {
                ctrlWebSocket?.send("{\"type\":\"PING\",\"ts\":${System.currentTimeMillis()}}")
                missed++
                if (missed >= 2) {
                    disconnect()
                    scheduleReconnect()
                }
            } catch (_: Exception) {
                disconnect()
                scheduleReconnect()
            }
        }
    }

    private fun hbStop() {
        hbTimer?.cancel()
        hbTimer = null
    }

    private fun scheduleReconnect() {
        val delay = (1000L * (1 shl reconnectAttempts).coerceAtMost(10))
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(10)
        Timer().schedule(delay) {
            client = buildClient()
            openControl()
            openVoice()
        }
    }
}