package com.example.watchrectest

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val SOCKET_UUID = "25AE1489-05D3-4C5B-8281-93D4E07420CF"

class BluClassicManager(private var mBluetoothAdapter: BluetoothAdapter, private var logManager: LogManager) {

    private var bSocket: BluetoothSocket? = null

    private var listenThread = ListenThread(logManager)

    private val socketUUID = UUID.fromString(SOCKET_UUID)


    private var outputStream: OutputStream? = null

    var streamSetSuccess: Boolean = false

    fun listen(): Boolean{

        var connectSuccess = listenThread.acceptConnect(mBluetoothAdapter, socketUUID)

        if(connectSuccess){
            bSocket = listenThread.getSocket()

            logManager.appendLog("Connection successful")

            try {
                outputStream = bSocket!!.outputStream
                streamSetSuccess = true
                return true
            } catch (e: IOException) {
                logManager.appendLog("Error when creating input stream $e")
            }
        }
        else{
            logManager.appendLog("Connection unsuccessful")
            return false
        }
        return false
    }

    fun writeOutputStream(arr: ByteArray) {
        try{
            outputStream?.write(arr)
            logManager.appendLog("written: " + arr.size)
        }
        catch (e: Exception){
            logManager.appendLog("error in writting $e")
        }
    }

    fun stopSocket(){
        listenThread.closeConnect()
    }
}