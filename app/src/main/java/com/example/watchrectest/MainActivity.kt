package com.example.watchrectest

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.*
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.watchrectest.databinding.ActivityMainBinding
import java.util.*
import android.util.Log
import android.widget.CompoundButton
import com.google.android.material.switchmaterial.SwitchMaterial

private const val REQUEST_PERMISSIONS_CODE = 200

class MainActivity : AppCompatActivity() {

    private lateinit var v: Vibrator

    private lateinit var binding: ActivityMainBinding

    private lateinit var permissionsManager: PermissionsManager

    private lateinit var _BLEManager: BLEManager

    private lateinit var micManager: MicManager

    private lateinit var switchGatt: SwitchMaterial

    private lateinit var switchRec: SwitchMaterial

    private val switchGattChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            _BLEManager.bleStartAdvertising()
        } else {
            _BLEManager.bleStopAdvertising()
            if(switchRec.isChecked){
                micManager.stopAction()
                switchRec.isChecked = false
            }
        }
    }

    private val switchRecChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if(switchGatt.isChecked && _BLEManager.anyoneSubscribes()){
                vibrate()
                micManager.startRecording()
            }
            else{
                LogManager.appendLog("No one subscribes")
                switchRec.isChecked = false
            }
        } else {
            vibrate()
            micManager.stopAction()
            _BLEManager.bleStopAdvertising()
            switchGatt.isChecked = false
        }
    }

    private fun tapSwitch(){
        switchRec.isChecked = true
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
            micManager.stopAction()
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
        Log.d("TAG", "message")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_main)
        switchGatt = findViewById(R.id.switch_Gatt)
        switchRec = findViewById(R.id.switch_Rec)

        switchGatt.setOnCheckedChangeListener(switchGattChangeListener)
        switchRec.setOnCheckedChangeListener(switchRecChangeListener)


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
    }

    override fun onStop() {
        super.onStop()
    }
}