package com.voxnet.app

data class VoXNetSettings(
    val ssid: String = "VoXNet_A",
    val password: String = "",
    val host: String = "192.168.4.1",
    val port: Int = 80,
    val ctrlPath: String = "/ctrl",
    val voicePath: String = "/voice"
)

data class ControlMessage(
    val type: String,
    val number: String? = null,
    val text: String? = null,
    val state: String? = null
)

data class AudioFrame(
    val seq: Int,
    val timestamp: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioFrame
        if (seq != other.seq) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

enum class CallState {
    DISCONNECTED, CONNECTING, DIALING, RINGING, CONNECTED, ENDED, ERROR
}

object MessageTypes {
    const val CALL_REQ = "CALL_REQ"
    const val SMS_REQ = "SMS_REQ"
    const val SOS = "SOS"
    const val END = "END"
    const val STATUS = "STATUS"
}

object StatusStates {
    const val DIALING = "DIALING"
    const val RINGING = "RINGING"
    const val CONNECTED = "CONNECTED"
    const val ENDED = "ENDED"
    const val SMS_SENT = "SMS_SENT"
    const val SMS_FAIL = "SMS_FAIL"
    const val ERROR = "ERROR"
}