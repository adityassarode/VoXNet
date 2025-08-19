package com.voxnet.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class VoXNetAudioManager(private val context: Context) {
    private val systemAudio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val sampleRate = 8000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val frameSize = 160 // 20ms at 8kHz

    private var isRecording = false
    private var isPlaying = false
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val playbackQueue = ConcurrentLinkedQueue<AudioFrame>()
    private var sequenceNumber = 0

    var onAudioFrame: ((AudioFrame) -> Unit)? = null

    fun startAudio() {
        setupAudioSettings()
        startRecording()
        startPlayback()
    }

    fun stopAudio() {
        stopRecording()
        stopPlayback()
        releaseAudioSettings()
    }

    fun queueAudioFrame(frame: AudioFrame) {
        playbackQueue.offer(frame)
    }

    fun toggleSpeaker(useSpeaker: Boolean) {
        systemAudio.isSpeakerphoneOn = useSpeaker
        systemAudio.mode = if (useSpeaker) AudioManager.MODE_NORMAL else AudioManager.MODE_IN_COMMUNICATION
    }

    private fun setupAudioSettings() {
        systemAudio.mode = AudioManager.MODE_IN_COMMUNICATION
        systemAudio.isSpeakerphoneOn = false
        systemAudio.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    }

    private fun releaseAudioSettings() {
        systemAudio.mode = AudioManager.MODE_NORMAL
        systemAudio.abandonAudioFocus(null)
    }

    private fun startRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelIn,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(frameSize * 2)
                while (isRecording && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val frame = AudioFrame(
                            seq = sequenceNumber++,
                            timestamp = System.currentTimeMillis(),
                            data = buffer.copyOf(bytesRead)
                        )
                        onAudioFrame?.invoke(frame)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlayback() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelOut, audioFormat)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
            audioTrack?.play()
            isPlaying = true

            playbackJob = CoroutineScope(Dispatchers.IO).launch {
                while (isPlaying && isActive) {
                    val frame = playbackQueue.poll()
                    if (frame != null) {
                        audioTrack?.write(frame.data, 0, frame.data.size)
                    } else delay(5)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        playbackQueue.clear()
    }
}