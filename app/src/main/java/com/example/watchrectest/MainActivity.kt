package com.example.watchrectest

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.watchrectest.databinding.ActivityMainBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

private const val REQUEST_PERMISSIONS_CODE = 200

class MainActivity : AppCompatActivity() {

    //private lateinit var v: Vibrator

    private lateinit var binding: ActivityMainBinding

    private lateinit var permissionsManager: PermissionsManager

    private lateinit var _BLEManager: BLEManager

    private lateinit var micManager: MicManager

    private lateinit var switchGatt: SwitchMaterial

    private lateinit var switchRec: SwitchMaterial

    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakeLock")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private val switchGattChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            _BLEManager.bleStartAdvertising()
            UIStateManager.setUIState(UIState.NO_SUBSCRIBER)
        } else {
            _BLEManager.bleStopAdvertising()
            if(switchRec.isChecked) switchRec.isChecked = false
            UIStateManager.setUIState(UIState.DISCONNECTED)
        }
    }

    private val switchRecChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if(switchGatt.isChecked && _BLEManager.anyoneSubscribes()){

                UIStateManager.setUIState(UIState.SENDING)
                startMic()
            }
            else{
                LogManager.appendLog("No one subscribes")
                switchRec.isChecked = false
            }
        } else {
            stopMic()
            if(switchGatt.isChecked) switchGatt.isChecked = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if(!switchGatt.isChecked){
            return run {
                switchGatt.isChecked = true
                true
            }
        }
        else if(switchGatt.isChecked && _BLEManager.anyoneSubscribes()){

            return if(!switchRec.isChecked && !micManager.getRecordingInProgress() && !micManager.getSendingInProgress()){
                run {
                    switchRec.isChecked = true
                    true
                }
            } else {
                run {
                    switchRec.isChecked = false
                    true
                }
            }
        }
        else{
            LogManager.appendLog("No one subscribes")
            return false
        }
    }

    private fun startMic(){
        micManager.startRecording()
        fakeSleepModeOn()
        //acquireWakeLock()
    }

    private fun stopMic(){
        micManager.stopAction()
        fakeSleepModeOff()
        //releaseWakeLock()
    }

    fun onTapTest(view: View){
        _BLEManager.notifyTest()
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


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled, show a dialog window
            AlertDialog.Builder(this)
                .setMessage("\n\nTo use this app, please enable Bluetooth on your device.")
                .setPositiveButton("Close app") { _, _ ->
                    // Close the app when the button is clicked
                    finish()
                }
                .setCancelable(false)
                .show()
        } else {
            // Bluetooth is enabled
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(R.layout.activity_main)
            switchGatt = findViewById(R.id.switch_Gatt)
            switchRec = findViewById(R.id.switch_Rec)

            switchGatt.setOnCheckedChangeListener(switchGattChangeListener)
            switchRec.setOnCheckedChangeListener(switchRecChangeListener)

            /*
            v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
             */

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

            //LogManager.setActivity(this)
            UIStateManager.setActivity(this)

            _BLEManager = BLEManager(this)

            micManager = MicManager(_BLEManager)
        }
    }

    override fun onStop() {
        super.onStop()
    }

    /*
    override fun onResume() {
        super.onResume()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
    }*/
}