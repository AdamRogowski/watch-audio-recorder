package com.example.watchrectest

import android.app.Activity
import android.media.*
import android.os.CountDownTimer
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Calendar

//ffmpeg supported freq: 64000 48000 44100 32000 24000 22050 16000 12000 11025 8000 7350
private const val SAMPLING_RATE_IN_HZ = 4000

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT

private const val BUFFER_SIZE_FACTOR = 1

private const val QUEUE_CAPACITY = 10000

private const val BUFFER_DIVIDER = 3



class MicManager(private val activity: Activity, private val logManager: LogManager, private val _BLEManager: BLEManager) {

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR

    private val recordingInProgress = AtomicBoolean(false)
    private val sendingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var sendingThread: Thread? = null


    private val queue: ArrayBlockingQueue<ByteArray> = ArrayBlockingQueue(QUEUE_CAPACITY) //Max capacity ~500KB with ByteArray.size ~ 500B



    fun startRecording() {
        logManager.appendLog("Assigning recorder")

        // Start Recording
        recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)

        recorder!!.startRecording()

        recordingInProgress.set(true)
        sendingInProgress.set(true)

        recordingThread = Thread(Runnable { recordIntoQueue() }, "AudioRecorder Thread")
        sendingThread = Thread(Runnable { sendFromQueue() }, "Sending Thread")
        recordingThread?.start()
        sendingThread?.start()
    }

    private fun recordIntoQueue(){
        val buffer = ByteBuffer.allocateDirect(minBufferSize)
        //val segmentSize = minBufferSize / BUFFER_DIVIDER
        val combArraySize = minBufferSize * BUFFER_DIVIDER

        while (recordingInProgress.get()) {

            var combArr = byteArrayOf()
            for(i in 0 until BUFFER_DIVIDER){
                try {
                    buffer.position(0) // Reset the buffer position to zero
                    buffer.let { recorder?.read(it, minBufferSize) }

                    val arr = ByteArray(buffer.remaining())
                    buffer.get(arr)

                    /*
                    for( i in 0 until BUFFER_DIVIDER){
                        val subarray = arr.copyOfRange(i, i + segmentSize)
                        queue.add(subarray)
                        logManager.appendLog("Added to queue, queue size: " + queue.size.toString())
                    }

                     */

                    combArr += arr


                    //queue.add(arr)

                    //logManager.appendLog(logManager.getCurrentTime() + " " + arr.size.toString())
                    //_BLEManager.bleIndicate(testByteArray)
                    //logManager.appendLog(arr.size.toString())
                    //_BLEManager.bleNotify(arr)

                } catch (e: Exception) {
                    logManager.appendLog("Error when recording into queue, e: " + e.message)
                }
            }

            queue.add(combArr)
            logManager.appendLog(logManager.getCurrentTime() + " Added to queue")
        }
    }

    // Method for sending Audio
    private fun sendFromQueue() {
        logManager.appendLog("sendRecording started")

        while (sendingInProgress.get()) {
            try {
                val arr = queue.take()

                _BLEManager.bleNotify(arr)
                logManager.appendLog(logManager.getCurrentTime() + " sent: " + arr.size.toString() + "B")
                //_BLEManager.bleIndicate(testByteArray)
                //logManager.appendLog(arr.size.toString())

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

    // Stop Recording and free up resources
    fun stopSending() {
        sendingInProgress.set(false)
        sendingThread = null
        logManager.appendLog("sending stopped")

    }
}