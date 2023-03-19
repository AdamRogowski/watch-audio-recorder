package com.example.watchrectest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.example.watchrectest.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_PERMISSIONS_CODE = 200
private const val SERVICE_UUID = "25AE1449-05D3-4C5B-8281-93D4E07420CF"
private const val CHAR_FOR_INDICATE_UUID = "25AE1494-05D3-4C5B-8281-93D4E07420CF"
private const val CCC_DESCRIPTOR_UUID = "00002930-0000-1000-8000-00805f9b34fb"

class MainActivity : Activity() {

    private var fileName: String = ""

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    private val textViewLog: TextView
        get() = findViewById(R.id.textViewLog)
    private val scrollViewLog: ScrollView
        get() = findViewById(R.id.scrollViewLog)

    private lateinit var v: Vibrator

    private lateinit var binding: ActivityMainBinding

    private lateinit var managePermissions: ManagePermissions


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

    @SuppressLint("SetTextI18n")
    private fun appendLog(message: String) {
        Log.d("appendLog", message)
        runOnUiThread {
            textViewLog.text = textViewLog.text.toString() + "\n$message"

            // wait for the textView to update
            Handler(Looper.getMainLooper()).postDelayed({
                scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }, 20)
        }
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
        startRecording()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onTapStopRec(view: View){
        vibrateLong()
        stopRecording()
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
            appendLog("udalo sie")
            vibrateLong()
            true
        }
    }


    private fun updateSubscribers() {
        appendLog("Currently subscribers: ${subscribedDevices.count()}")
    }

    private fun anyoneSubscribes(): Boolean{
        return subscribedDevices.isNotEmpty()
    }

    fun onTapTest(view: View) {
        val data = "[${getCurrentTime()}] test"
        if(anyoneSubscribes()){
            bleIndicate(data)
            appendLog("indication test sent")
        }
        else{
            appendLog("No one subscribes")
        }
    }

    @SuppressLint("SetTextI18n")
    fun onTapClearLog(view: View) {
        textViewLog.text = "Logs:"
        appendLog("log cleared")
    }

    //BLE GATT server--------------------------------------------------------------------------------------------
    private var gattServer: BluetoothGattServer? = null
    private val charForIndicate get() = gattServer?.getService(UUID.fromString(SERVICE_UUID))?.getCharacteristic(UUID.fromString(CHAR_FOR_INDICATE_UUID))
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()


    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    appendLog("Central device did connect")
                    updateSubscribers()
                }
                else {
                    appendLog("Central device did disconnect")
                    subscribedDevices.remove(device)
                    updateSubscribers()
                }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            appendLog("onNotificationSent status=$status")
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            var log = "onDescriptorReadRequest"
            if (descriptor.uuid == UUID.fromString(CCC_DESCRIPTOR_UUID)) {
                val returnValue = if (subscribedDevices.contains(device)) {
                    log += " CCCD response=ENABLE_NOTIFICATION"
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    log += " CCCD response=DISABLE_NOTIFICATION"
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue)
            } else {
                log += " unknown uuid=${descriptor.uuid}"
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
            appendLog(log)
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            var strLog = "onDescriptorWriteRequest"
            if (descriptor.uuid == UUID.fromString(CCC_DESCRIPTOR_UUID)) {
                var status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                if (descriptor.characteristic.uuid == UUID.fromString(CHAR_FOR_INDICATE_UUID)) {
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        subscribedDevices.add(device)
                        status = BluetoothGatt.GATT_SUCCESS
                        strLog += ", subscribed"
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        subscribedDevices.remove(device)
                        status = BluetoothGatt.GATT_SUCCESS
                        strLog += ", unsubscribed"
                    }
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, status, 0, null)
                }
                updateSubscribers()
            } else {
                strLog += " unknown uuid=${descriptor.uuid}"
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
            appendLog(strLog)
        }
    }
    //-----------------------------------------------------------------------------------------------------------


    //BLE advertising--------------------------------------------------------------------------------------------
    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private val bleAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
    }

    /*
    private fun prepareAndStartAdvertising() {
        ensureBluetoothCanBeUsed { isSuccess, message ->
            runOnUiThread {
                appendLog(message)
                if (isSuccess) bleStartAdvertising()
            }
        }
    }

     */

    fun bleStartAdvertising(view: View) {
        bleStartGattServer()
        bleAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    fun bleStopAdvertising(view: View) {
        bleStopGattServer()
        bleAdvertiser.stopAdvertising(advertiseCallback)
    }

    private fun bleStartGattServer() {
        val gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        val service = BluetoothGattService(UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        var charForIndicate = BluetoothGattCharacteristic(UUID.fromString(CHAR_FOR_INDICATE_UUID),
            BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ)
        var charConfigDescriptor = BluetoothGattDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        charForIndicate.addDescriptor(charConfigDescriptor)

        service.addCharacteristic(charForIndicate)

        val result = gattServer.addService(service)
        this.gattServer = gattServer

        appendLog("addService " + when(result) {
            true -> "OK"
            false -> "fail"
        })
    }

    private fun bleStopGattServer() {
        gattServer?.close()
        gattServer = null
        appendLog("gattServer stopped")
    }

    private fun bleIndicate(data: String) {
        charForIndicate?.let {
            it.value = data.toByteArray(Charsets.UTF_8)
            for (device in subscribedDevices) {
                gattServer?.notifyCharacteristicChanged(device, it, true)
            }
        }
    }

    private val advertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        .setConnectable(true)
        .build()

    private val advertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false) // don't include name, because if name size > 8 bytes, ADVERTISE_FAILED_DATA_TOO_LARGE
        .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
        .build()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            appendLog("Advertise start success\n$SERVICE_UUID")
        }

        override fun onStartFailure(errorCode: Int) {
            val desc = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "\nADVERTISE_FAILED_DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "\nADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "\nADVERTISE_FAILED_ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "\nADVERTISE_FAILED_INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "\nADVERTISE_FAILED_FEATURE_UNSUPPORTED"
                else -> ""
            }
            appendLog("Advertise start failed: errorCode=$errorCode $desc")
        }
    }
    //-----------------------------------------------------------------------------------------------------------




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
        managePermissions = ManagePermissions(this, list, REQUEST_PERMISSIONS_CODE)
        managePermissions.checkPermissions()

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