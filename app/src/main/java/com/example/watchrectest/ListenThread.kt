package com.example.watchrectest

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.*

class ListenThread (private val logManager: LogManager) {

    private var listenSocket: BluetoothSocket? = null

    // Accept connection and create socket object
    fun acceptConnect(adapter: BluetoothAdapter, mUUID: UUID?): Boolean {
        var temp: BluetoothServerSocket? = null
        try {
            temp = adapter.listenUsingRfcommWithServiceRecord("BTService", mUUID)
        } catch (e: IOException) {
            logManager.appendLog("Error at listen using RFCOMM")
        }
        try {
            listenSocket = temp!!.accept()
        } catch (e: IOException) {
            logManager.appendLog("Error at accept connection")
        }
        if (listenSocket != null) {
            try {
                temp!!.close()
            } catch (e: IOException) {
                logManager.appendLog("Error at socket close")
            }
            return true
        }
        return false
    }

    // Close connection
    fun closeConnect(): Boolean {
        try {
            listenSocket!!.close()
        } catch (e: IOException) {
            logManager.appendLog("Failed at socket close")
            return false
        }
        return true
    }

    // Return socket object
    fun getSocket(): BluetoothSocket? {
        return listenSocket
    }

}