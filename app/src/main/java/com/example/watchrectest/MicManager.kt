package com.example.watchrectest

import android.annotation.SuppressLint
import android.media.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

//ffmpeg supported freq: 64000 48000 44100 32000 24000 22050 16000 12000 11025 8000 7350
//https://developer.android.com/reference/android/media/AudioFormat
private const val SAMPLING_RATE_IN_HZ = 4000
private const val SAMPLING_RATE_QUALITY = 12000

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT

private const val QUEUE_CAPACITY = 10000

private const val BUFFER_DIVIDER = 3



class MicManager(private val _BLEManager: BLEManager) {

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT)
    private val qualityBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_QUALITY,
        CHANNEL_CONFIG, AUDIO_FORMAT)

    private val recordingInProgress = AtomicBoolean(false)
    private val sendingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var sendingThread: Thread? = null

    private var worstQuality: Boolean = true


    private val queue: ArrayBlockingQueue<ByteArray> = ArrayBlockingQueue(QUEUE_CAPACITY)



    @SuppressLint("MissingPermission")
    fun startRecording() {
        LogManager.appendLog("Assigning recorder")

        // Start Recording
        recorder = if(worstQuality){
            AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)}
        else AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_QUALITY, CHANNEL_CONFIG, AUDIO_FORMAT, qualityBufferSize)

        recorder!!.startRecording()

        recordingInProgress.set(true)
        sendingInProgress.set(true)

        recordingThread = if(worstQuality){
            Thread({ recordIntoQueue() }, "AudioRecorder Thread")
        } else Thread({ recordQualityIntoQueue() }, "AudioRecorder Thread")
        sendingThread = Thread({ sendFromQueue() }, "Sending Thread")
        recordingThread?.start()
        sendingThread?.start()
    }

    private fun recordIntoQueue(){
        val buffer =  ByteBuffer.allocateDirect(minBufferSize)
        //val segmentSize = minBufferSize / BUFFER_DIVIDER
        //val combArraySize = minBufferSize * BUFFER_DIVIDER

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
                        val subArray = arr.copyOfRange(i, i + segmentSize)
                        queue.add(subArray)
                        LogManager.appendLog("Added to queue, queue size: " + queue.size.toString())
                    }
                     */

                    combArr += arr

                } catch (e: Exception) {
                    LogManager.appendLog("Error when recording into queue, e: " + e.message)
                }
            }
            queue.add(combArr)
            LogManager.appendLog(LogManager.getCurrentTime() + " Added to queue")
        }
    }

    private fun recordQualityIntoQueue(){
        val buffer =  ByteBuffer.allocateDirect(qualityBufferSize)

        while (recordingInProgress.get()) {

            try {
                buffer.position(0) // Reset the buffer position to zero
                buffer.let { recorder?.read(it, qualityBufferSize) }

                val arr = ByteArray(buffer.remaining())

                buffer.get(arr)
                queue.add(arr)
                LogManager.appendLog(LogManager.getCurrentTime() + " Added to queue")
            } catch (e: Exception) {
                LogManager.appendLog("Error when recording into queue, e: " + e.message)
            }
        }
    }

    private fun sendFromQueue() {
        LogManager.appendLog("sendRecording started")

        while (sendingInProgress.get()) {
            try {
                val arr = queue.take()

                _BLEManager.bleNotify(arr)
                LogManager.appendLog(LogManager.getCurrentTime() + " sent: " + arr.size.toString() + "B")

            } catch (e: Exception) {
                LogManager.appendLog("Error when sending from queue, e: " + e.message)
            }
        }
    }

    // Stop Recording and sending and free up resources
    fun stopAction() {
        if (recorder != null) {
            recordingInProgress.set(false)
            sendingInProgress.set(false)
            recorder!!.stop()
            recorder!!.release()
            recorder = null
            recordingThread = null
            sendingThread = null
            queue.clear()
            LogManager.appendLog("recording and sending stopped")
        }
    }

    // Stop sending and free up resources
    fun stopSending() {
        sendingInProgress.set(false)
        sendingThread = null
        LogManager.appendLog("sending stopped")
    }

    fun setBetterQuality(value: Boolean){
        worstQuality = value
    }

    fun getRecordingInProgress(): Boolean{
        return recordingInProgress.get()
    }

    fun getSendingInProgress(): Boolean{
        return sendingInProgress.get()
    }


}