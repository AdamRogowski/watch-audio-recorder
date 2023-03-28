package com.example.watchrectest

import android.bluetooth.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.*
import java.util.*

private const val SERVICE_UUID = "25AE1449-05D3-4C5B-8281-93D4E07420CF"
private const val CHAR_FOR_NOTIFY_UUID = "25AE1494-05D3-4C5B-8281-93D4E07420CF"
private const val CCC_DESCRIPTOR_UUID = "00002930-0000-1000-8000-00805f9b34fb"

class BLEManager(private val activity: MainActivity, private val logManager: LogManager) {

    //Throughput test--------------------------------------------------------------------------------------------
    private val doTests: Boolean = false
    private var testIterator: Int = 0
    private val nrOfPackets: Int = 200
    private val packetSizeBytes: Int = 512

    private var start = ""
    private var stop = ""


    //BLE GATT server--------------------------------------------------------------------------------------------
    private var gattServer: BluetoothGattServer? = null
    private val charForNotify get() = gattServer?.getService(UUID.fromString(SERVICE_UUID))?.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID))
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()

    //BLE Advertising
    private val bluetoothManager: BluetoothManager by lazy {
        activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    private val bleAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
    }


    private fun updateSubscribers() {
        logManager.appendLog("Currently subscribers: ${subscribedDevices.count()}")
    }

    private fun anyoneSubscribes(): Boolean{
        return subscribedDevices.isNotEmpty()
    }


    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            //logManager.appendLog("MTU changed to: $mtu")
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            activity.runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    logManager.appendLog("Central device did connect")
                    updateSubscribers()
                }
                else {
                    logManager.appendLog("Central device did disconnect")
                    subscribedDevices.remove(device)
                    updateSubscribers()
                }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            logManager.appendLog(logManager.getCurrentTime() + " notification sent status=$status")

            /*
            testIterator++
            if(testIterator == 1) start = logManager.getCurrentTime()
            else if(testIterator == nrOfPackets){
                stop = logManager.getCurrentTime()
                //HH:mm:ss:SSS
                val period: Double = stop.slice(0..1).toDouble() * 3600 + stop.slice(3..4).toDouble() * 60 + stop.slice(6..7).toDouble() + stop.slice(9..11).toDouble() / 1000 - (start.slice(0..1).toDouble() * 3600 + start.slice(3..4).toDouble() * 60 + start.slice(6..7).toDouble() + start.slice(9..11).toDouble() / 1000)
                val throughput: Int = (nrOfPackets * packetSizeBytes * 8 / 1000 / period).toInt()
                logManager.appendLog("measured throughput: $throughput kbps")
                testIterator = 0
            }

             */

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
            logManager.appendLog(log)
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            var strLog = "onDescriptorWriteRequest"
            if (descriptor.uuid == UUID.fromString(CCC_DESCRIPTOR_UUID)) {
                var status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                if (descriptor.characteristic.uuid == UUID.fromString(CHAR_FOR_NOTIFY_UUID)) {
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
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
            logManager.appendLog(strLog)
        }
    }
    //-----------------------------------------------------------------------------------------------------------


    //BLE advertising--------------------------------------------------------------------------------------------


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

    fun bleStartAdvertising() {
        bleStartGattServer()
        bleAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        //bleAdvertiser.startAdvertisingSet(advertiseParameters.build(), advertiseData, null, null, null, callback)
    }

    fun bleStopAdvertising() {
        bleStopGattServer()
        bleAdvertiser.stopAdvertising(advertiseCallback)
    }

    private fun bleStartGattServer() {
        val gattServer = bluetoothManager.openGattServer(activity, gattServerCallback)
        val service = BluetoothGattService(UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        var charForNotify = BluetoothGattCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)
        var charConfigDescriptor = BluetoothGattDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        charForNotify.addDescriptor(charConfigDescriptor)

        service.addCharacteristic(charForNotify)

        val result = gattServer.addService(service)
        this.gattServer = gattServer

        logManager.appendLog("addService " + when(result) {
            true -> "OK"
            false -> "fail"
        })
    }

    private fun bleStopGattServer() {
        gattServer?.close()
        gattServer = null
        logManager.appendLog("gattServer stopped")
    }

    fun bleNotify(data: ByteArray) {
        charForNotify?.let {
            it.value = data
            for (device in subscribedDevices) {
                gattServer?.notifyCharacteristicChanged(device, it, true)
            }
        }
    }

    private val advertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
        .setConnectable(true)
        .build()

    private val advertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false) // don't include name, because if name size > 8 bytes, ADVERTISE_FAILED_DATA_TOO_LARGE
        .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
        .build()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            logManager.appendLog("Advertise start success\n$SERVICE_UUID")
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
            logManager.appendLog("Advertise start failed: errorCode=$errorCode $desc")
        }
    }
    //-----------------------------------------------------------------------------------------------------------

    fun notifyTest() {
        val data = byteArrayOf(0x48, 101, 108, 108, 111)
        if(anyoneSubscribes()){
            bleNotify(data)
            logManager.appendLog("notify test sent")
        }
        else{
            logManager.appendLog("No one subscribes")
        }
    }

    fun testBLEThroughputOn(){
        if(doTests){
            val testByteArray = ByteArray(packetSizeBytes) { i ->
                when {
                    i < nrOfPackets/2 -> ('a' + i % 26).toByte() // fill first half bytes with lowercase letters from 'a' to 'z'
                    else -> ('A' + i % 26).toByte() // fill the remaining half bytes with uppercase letters from 'A' to 'Z'
                }
            }

            var i = 0

            while(i < nrOfPackets){
                bleNotify(testByteArray)
                i++
            }
        }
        else logManager.appendLog("doTests is false")
    }

}