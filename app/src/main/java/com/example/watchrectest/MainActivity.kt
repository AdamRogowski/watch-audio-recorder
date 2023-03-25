package com.example.watchrectest

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.example.watchrectest.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_PERMISSIONS_CODE = 200


class MainActivity : Activity() {

    private var fileName: String = ""

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null



    private lateinit var v: Vibrator

    private lateinit var binding: ActivityMainBinding

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var logManager: LogManager

    private lateinit var _BLEManager: BLEManager

    private lateinit var micManager: MicManager


    /*
    // Requesting permission to RECORD_AUDIO
    private var permissionsAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsAccepted = if (requestCode == REQUEST_PERMISSIONS_CODE) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionsAccepted) finish()
    }

    */

    private fun getCurrentTime(): String{
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }



    private fun vibrate(){
        v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibrateLong(){
        v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }


    fun onTapStartRec(view: View){
        vibrate()
        //startRecording()
        micManager.startRecording()
        //_BLEManager.indicateTest()
    }

    fun onTapStopRec(view: View){
        vibrate()
        //stopRecording()
        micManager.stopRecording()
    }

    fun onTapStartPlay(view: View){
        vibrate()
        startPlaying()
    }

    fun onTapStopPlay(view: View){
        vibrate()
        stopPlaying()
    }


    private fun startPlaying() {
        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun stopPlaying() {
        player?.release()
        player = null
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }

            start()
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return run {
            // process bottomKeyPress
            logManager.appendLog("udalo sie")
            vibrateLong()
            micManager.stopRecording()
            true
        }
    }

    fun startAdvertising(view: View){
        _BLEManager.bleStartAdvertising()
    }

    fun stopAdvertising(view: View){
        _BLEManager.bleStopAdvertising()
    }

    fun onTapTest(view: View){
        _BLEManager.indicateTest()
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the Wear screen always on (for testing only!)
        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Record to the external cache directory for visibility
        fileName = "${externalCacheDir?.absolutePath}/audiorecordtest.3gp"

        // Initialize a list of required permissions to request runtime
        val list = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )

        // Initialize a new instance of ManagePermissions class
        permissionsManager = PermissionsManager(this, list, REQUEST_PERMISSIONS_CODE)
        permissionsManager.checkPermissions()

        logManager = LogManager(this)

        _BLEManager = BLEManager(this, logManager)

        micManager = MicManager(this, logManager, _BLEManager)


        //ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_main)
    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
        player?.release()
        player = null
    }
}