package com.example.watchrectest

import android.app.Activity
import android.media.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val SAMPLING_RATE_IN_HZ = 12600

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT

private const val BUFFER_SIZE_FACTOR = 1

private const val QUEUE_CAPACITY = 1000



class MicManager(private val activity: Activity, private val logManager: LogManager, private val _BLEManager: BLEManager) {

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR

    private val recordingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var sendingThread: Thread? = null


    private val queue: ArrayBlockingQueue<ByteArray> = ArrayBlockingQueue(QUEUE_CAPACITY) //Max capacity ~500KB with ByteArray.size ~ 500B



    private var testThroughput : Boolean = true

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
    fun testBLEThroughputOn(){
        val testByteArray = ByteArray(512) { i ->
            when {
                i < 250 -> ('a' + i % 26).toByte() // fill first 250 bytes with lowercase letters from 'a' to 'z'
                else -> ('A' + i % 26).toByte() // fill the remaining 250 bytes with uppercase letters from 'A' to 'Z'
            }
        }

        while(testThroughput){
            logManager.appendLog(logManager.getCurrentTime() + "test")
            _BLEManager.bleNotify(testByteArray)

        }
    }

    fun testBLEThroughputOff(){
        testThroughput = false
    }

    fun startRecording() {
        logManager.appendLog("Assigning recorder")

        // Start Recording
        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)

        recorder!!.startRecording()

        recordingInProgress.set(true)

        recordingThread = Thread(Runnable { recordIntoQueue() }, "AudioRecorder Thread")
        sendingThread = Thread(Runnable { sendFromQueue() }, "Sending Thread")
        recordingThread?.start()
        sendingThread?.start()
    }

    private fun recordIntoQueue(){
        val buffer = ByteBuffer.allocateDirect(minBufferSize)

        while (recordingInProgress.get()) {
            try {
                buffer.position(0) // Reset the buffer position to zero
                buffer.let { recorder?.read(it, minBufferSize) }

                val arr = ByteArray(buffer.remaining())
                buffer.get(arr)

                queue.add(arr)

                logManager.appendLog(logManager.getCurrentTime() + " " + arr.size.toString())
                //_BLEManager.bleIndicate(testByteArray)
                //logManager.appendLog(arr.size.toString())
                //_BLEManager.bleNotify(arr)

            } catch (e: Exception) {
                logManager.appendLog("Error when recording into queue, e: " + e.message)
            }

        }
    }

    // Method for sending Audio
    private fun sendFromQueue() {
        logManager.appendLog("sendRecording started")

        while (recordingInProgress.get()) {
            try {
                if(!queue.isEmpty()){
                    val arr = queue.take()

                    _BLEManager.bleNotify(arr)
                    logManager.appendLog(logManager.getCurrentTime() + " " + arr.size.toString())
                    //_BLEManager.bleIndicate(testByteArray)
                    //logManager.appendLog(arr.size.toString())
                }
            } catch (e: Exception) {
                logManager.appendLog("Error when sending from queue, e: " + e.message)
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
            sendingThread = null
            logManager.appendLog("recording stopped")
        }
    }
}