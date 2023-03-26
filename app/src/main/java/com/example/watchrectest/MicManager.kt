package com.example.watchrectest

import android.app.Activity
import android.media.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

private const val SAMPLING_RATE_IN_HZ = 12600

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT

private const val BUFFER_SIZE_FACTOR = 1



class MicManager(private val activity: Activity, private val logManager: LogManager, private val _BLEManager: BLEManager) {

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR

    private val recordingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    private var buffer = ByteBuffer.allocateDirect(minBufferSize)



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
        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)

        /*
        // To get preferred buffer size and sampling rate.
        // To get preferred buffer size and sampling rate.
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        logManager.appendLog("Buffer Size :$size & Rate: $rate")
         */

        recorder!!.startRecording()
        //_BLEManager.bleIndicate(recorder?.recordingState.toString())
        recordingInProgress.set(true)
        // Start a thread
        recordingThread = Thread(Runnable { sendRecording() }, "AudioRecorder Thread")
        recordingThread?.start()
    }

    // Method for sending Audio
    private fun sendRecording() {
        // Infinite loop until microphone button is released
        logManager.appendLog("sendRecording started")
        buffer = ByteBuffer.allocateDirect(minBufferSize)

        while (recordingInProgress.get()) {
            try {
                if(recorder?.read(buffer, minBufferSize)!! < 0 ){
                    logManager.appendLog("Reading of audio buffer failed")
                    return
                }
                buffer.position(0) // Reset the buffer position to zero
                buffer.let { recorder?.read(it, minBufferSize) }

                if (buffer.remaining()==0) logManager.appendLog("buffer empty, nothing recorded")
                else{
                    val arr = ByteArray(buffer.remaining())
                    buffer.get(arr)

                    //_BLEManager.bleIndicate(testByteArray)
                    //logManager.appendLog(arr.size.toString())
                    _BLEManager.bleIndicate(arr)
                }
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