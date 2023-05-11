package com.example.watchrectest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.*
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.example.watchrectest.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*
import android.media.*
import android.view.WindowManager
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val REQUEST_PERMISSIONS_CODE = 200

//-------------Constants for MicManager------------------------
private const val SAMPLING_RATE_IN_HZ = 4000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT
private const val BUFFER_SIZE_FACTOR = 1
private const val QUEUE_CAPACITY = 10000
private const val BUFFER_DIVIDER = 1
//-------------------------------------------------------------

//-------------Constants of Bluetooth--------------------------
private val MY_UUID = UUID.fromString("25AE1489-05D3-4C5B-8281-93D4E07420CF")
private const val REQUEST_ENABLE_BLUETOOTH = 1
private const val APP_NAME = "WatchRecTest"
//-------------------------------------------------------------

class MainActivity : Activity() {
    private lateinit var v: Vibrator
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var logManager: LogManager
    private lateinit var micManager: MicManager

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var streamSender: StreamSender

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize a list of required permissions to request runtime
        val list = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )

        permissionsManager = PermissionsManager(this, list, REQUEST_PERMISSIONS_CODE)
        permissionsManager.checkPermissions()

        logManager = LogManager(this)
        micManager = MicManager()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        }
    }

    private fun fakeSleepModeOn(){
        // Change screen brightness to minimum
        val brightness = 0
        val layoutParam = window.attributes
        layoutParam.screenBrightness = brightness.toFloat()
        window.attributes = layoutParam

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun fakeSleepModeOff(){
        // Change screen brightness back to normal
        val brightness = -1 // -1 means use the system default brightness
        val layoutParam = window.attributes
        layoutParam.screenBrightness = brightness.toFloat()
        window.attributes = layoutParam

        // Allow the screen to turn off automatically
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun vibrate(){
        v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibrateLong(){
        v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun startAdvertising(view: View){
        val serverClass = ServerClass()
        serverClass.start()
        logManager.appendLog("server started")
    }

    fun stopAdvertising(view: View){
    }

    fun onTapStartRec(view: View){
        vibrate()
        fakeSleepModeOn()
        micManager.startRecording()
    }

    fun onTapStopSend(view: View){
        vibrate()
        micManager.stopRecording()
        fakeSleepModeOff()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return run {
            // process bottomKeyPress
            logManager.appendLog("finished sending")
            vibrateLong()
            micManager.stopRecording()
            fakeSleepModeOff()
            true
        }
    }

    private inner class MicManager {

        private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
        private val queue: ArrayBlockingQueue<ByteArray> = ArrayBlockingQueue(QUEUE_CAPACITY)

        private val recordingInProgress = AtomicBoolean(false)
        private val sendingInProgress = AtomicBoolean(false)

        private var recorder: AudioRecord? = null
        private var recordingThread: Thread? = null
        private var sendingThread: Thread? = null


        @SuppressLint("MissingPermission")
        fun startRecording() {
            logManager.appendLog("Assigning recorder")

            // Start Recording
            recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)

            recorder!!.startRecording()

            recordingInProgress.set(true)
            sendingInProgress.set(true)

            recordingThread = Thread({ recordIntoQueue() }, "AudioRecorder Thread")
            sendingThread = Thread({ sendFromQueue() }, "Sending Thread")
            recordingThread?.start()
            sendingThread?.start()
        }

        private fun recordIntoQueue(){
            val buffer = ByteBuffer.allocateDirect(minBufferSize)
            //val segmentSize = minBufferSize / BUFFER_DIVIDER
            val combArraySize = minBufferSize * BUFFER_DIVIDER

            while (recordingInProgress.get()) {

                //var combArr = byteArrayOf()
                //for(i in 0 until BUFFER_DIVIDER){
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
                    //combArr += arr
                    queue.add(arr)
                } catch (e: Exception) {
                    logManager.appendLog("Error when recording into queue, e: " + e.message)
                }
                //}
                //queue.add(combArr)
                //logManager.appendLog(logManager.getCurrentTime() + " Added to queue")
            }
        }

        private fun sendFromQueue() {
            logManager.appendLog("sendRecording started")

            while (sendingInProgress.get()) {
                try {
                    val arr = queue.take()

                    streamSender.write(arr)
                    //logManager.appendLog(logManager.getCurrentTime() + " sent: " + arr.size.toString() + "B")

                } catch (e: Exception) {
                    logManager.appendLog("Error when sending from queue, e: " + e.message)
                }
            }
        }

        fun stopRecording() {
            if (recorder != null) {
                recordingInProgress.set(false)
                recorder!!.stop()
                recorder!!.release()
                recorder = null
                recordingThread = null
                sendingInProgress.set(false)
                sendingThread = null
                logManager.appendLog("recording and sending stopped")
            }
        }

        fun stopSending() {
            sendingInProgress.set(false)
            sendingThread = null
            logManager.appendLog("sending stopped")

        }
    }

    @SuppressLint("MissingPermission")
    private inner class ServerClass : Thread() {
        private var serverSocket: BluetoothServerSocket? = null

        init {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (e: IOException) {
                logManager.appendLog("error in assigning server socket, e: $e")
            }
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (true) {
                try {
                    socket = serverSocket?.accept()
                } catch (e: IOException) {
                    logManager.appendLog("state connection failed, e: $e")
                    break
                }

                if (socket != null) {
                    streamSender = StreamSender(socket)
                    streamSender.start()
                    logManager.appendLog("state connected, stream set")
                    break
                }
            }
        }
    }

    private inner class StreamSender(socket: BluetoothSocket) : Thread() {
        private val outputStream: OutputStream

        init {
            var tempOutput: OutputStream? = null
            try {
                tempOutput = socket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            outputStream = tempOutput!!
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}