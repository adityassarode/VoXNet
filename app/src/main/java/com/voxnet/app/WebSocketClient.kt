package com.voxnet.app

import com.google.gson.Gson
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketClient(private val settings: VoXNetSettings) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ctrlWebSocket: WebSocket? = null
    private var voiceWebSocket: WebSocket? = null
    private val gson = Gson()

    var onControlMessage: ((ControlMessage) -> Unit)? = null
    var onConnectionChange: ((Boolean) -> Unit)? = null
    var onAudioFrame: ((AudioFrame) -> Unit)? = null

    fun connect() {
        connectControl()
        connectVoice()
    }

    fun disconnect() {
        ctrlWebSocket?.close(1000, "Closing")
        voiceWebSocket?.close(1000, "Closing")
    }

    fun sendControlMessage(message: ControlMessage) {
        val json = gson.toJson(message)
        ctrlWebSocket?.send(json)
    }

    fun sendAudioFrame(frame: AudioFrame) {
        val buffer = ByteArray(12 + frame.data.size)
        // Pack header: seq (4 bytes) + timestamp (8 bytes) + data
        buffer[0] = (frame.seq shr 24).toByte()
        buffer[1] = (frame.seq shr 16).toByte()
        buffer[2] = (frame.seq shr 8).toByte()
        buffer[3] = frame.seq.toByte()

        var ts = frame.timestamp
        for (i in 4..11) {
            buffer[i] = ((ts shr (8 * (11 - i))) and 0xFF).toByte()
        }

        System.arraycopy(frame.data, 0, buffer, 12, frame.data.size)
        voiceWebSocket?.send(ByteString.of(*buffer))
    }

    private fun connectControl() {
        val url = "ws://${settings.host}:${settings.port}${settings.ctrlPath}"
        val request = Request.Builder().url(url).build()

        ctrlWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionChange?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, ControlMessage::class.java)
                    onControlMessage?.invoke(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionChange?.invoke(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionChange?.invoke(false)
                t.printStackTrace()
            }
        })
    }

    private fun connectVoice() {
        val url = "ws://${settings.host}:${settings.port}${settings.voicePath}"
        val request = Request.Builder().url(url).build()

        voiceWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size >= 12) {
                    val buffer = bytes.toByteArray()

                    // Unpack header
                    val seq = ((buffer[0].toInt() and 0xFF) shl 24) or
                              ((buffer[1].toInt() and 0xFF) shl 16) or
                              ((buffer[2].toInt() and 0xFF) shl 8) or
                              (buffer[3].toInt() and 0xFF)

                    var timestamp = 0L
                    for (i in 4..11) {
                        timestamp = (timestamp shl 8) or (buffer[i].toLong() and 0xFF)
                    }

                    val audioData = ByteArray(buffer.size - 12)
                    System.arraycopy(buffer, 12, audioData, 0, audioData.size)

                    val frame = AudioFrame(seq, timestamp, audioData)
                    onAudioFrame?.invoke(frame)
                }
            }
        })
    }
}