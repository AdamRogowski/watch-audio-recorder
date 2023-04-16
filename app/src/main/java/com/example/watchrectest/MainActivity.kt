package com.example.watchrectest

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.os.*
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.example.watchrectest.databinding.ActivityMainBinding
import java.util.*

private const val REQUEST_PERMISSIONS_CODE = 200

class MainActivity : Activity() {

    private lateinit var v: Vibrator

    private lateinit var binding: ActivityMainBinding

    private lateinit var permissionsManager: PermissionsManager

    private lateinit var _BLEManager: BLEManager

    private lateinit var micManager: MicManager



    private fun vibrate(){
        v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibrateLong(){
        v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }


    fun onTapStartRec(view: View){
        vibrate()

        micManager.startRecording()
    }

    fun onTapStopSend(view: View){
        vibrate()

        micManager.stopSending()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return run {
            // process bottomKeyPress
            LogManager.appendLog("udalo sie")
            vibrateLong()
            micManager.stopRecording()
            true
        }
    }

    fun startAdvertising(view: View){
        runOnUiThread {
            _BLEManager.bleStartAdvertising()
        }
    }

    fun stopAdvertising(view: View){
        _BLEManager.bleStopAdvertising()
    }

    fun onTapTest(view: View){
        _BLEManager.notifyTest()
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // Initialize a new instance of ManagePermissions class
        permissionsManager = PermissionsManager(this, list, REQUEST_PERMISSIONS_CODE)
        permissionsManager.checkPermissions()

        LogManager.setActivity(this)

        _BLEManager = BLEManager(this)

        micManager = MicManager(_BLEManager)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_main)
    }

    override fun onStop() {
        super.onStop()
    }
}