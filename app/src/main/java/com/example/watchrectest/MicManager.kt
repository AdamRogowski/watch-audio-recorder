package com.example.watchrectest

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

private const val SAMPLING_RATE_IN_HZ = 44100

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

private const val BUFFER_SIZE_FACTOR = 2



class MicManager(private val activity: Activity, private val logManager: LogManager, private val _BLEManager: BLEManager) {

    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR

    private val recordingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    private var buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)

    /*
    private var playThread: Thread? = null
    private var track: AudioTrack? = null
    private var am: AudioManager? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var buffer: ByteArray? = null
    private val minSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var bufferSize = minSize
    private var playBuffer: ByteArray = ByteArray(bufferSize)
    private var isRecording = false


     */

    fun startRecording() {
        logManager.appendLog("Assigning recorder")

        // Start Recording
        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)

        recorder!!.startRecording()
        logManager.appendLog("huj1")
        //_BLEManager.bleIndicate(recorder?.recordingState.toString())
        recordingInProgress.set(true)
        // Start a thread
        recordingThread = Thread(Runnable { sendRecording() }, "AudioRecorder Thread")
        recordingThread?.start()
    }

    // Method for sending Audio
    private fun sendRecording() {
        // Infinite loop until microphone button is released
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        logManager.appendLog("huj2")

        while (recordingInProgress.get()) {
            try {
                if(recorder?.read(buffer, BUFFER_SIZE)!! < 0 ){
                    logManager.appendLog("Reading of audio buffer failed")
                    return
                }
                buffer.position(0) // Reset the buffer position to zero
                buffer.let { recorder?.read(it, BUFFER_SIZE) }

                //_BLEManager.bleIndicate("huj")
                //buffer.let { _BLEManager.bleIndicate(StandardCharsets.UTF_8.decode(buffer).toString()) }
                _BLEManager.bleIndicate(StandardCharsets.UTF_8.decode(buffer).toString())
                //outStream?.write(buffer)
                //_BLEManager.bleIndicate(recorder?.recordingState.toString() + " huj")
            } catch (e: IOException) {
                logManager.appendLog("Error when sending recording")
            }

        }
    }

    // Stop Recording and free up resources
    fun stopRecording() {
        if (recorder != null) {
            recordingInProgress.set(false)
            recorder!!.stop()
            recorder!!.release()
            recorder = null
            recordingThread = null
            logManager.appendLog("recording stopped")
        }
    }
}