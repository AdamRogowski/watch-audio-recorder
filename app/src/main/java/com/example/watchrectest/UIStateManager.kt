package com.example.watchrectest

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import java.lang.ref.WeakReference


enum class UIState {
    DISCONNECTED,
    NO_SUBSCRIBER,
    CONNECTED_READY,
    SENDING,
    UNKNOWN
}

object UIStateManager{

    private var activityRef: WeakReference<AppCompatActivity>? = null
    private val uiStateText: TextView?
        get() = activityRef?.get()?.findViewById(R.id.uiState)

    private var vibrator: Vibrator? = null

    fun setActivity(activity: AppCompatActivity) {
        activityRef = WeakReference(activity)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        }}

    fun setUIState(state: UIState){
        val stateString = state.toString()
        when (stateString){
            "DISCONNECTED" -> vibrateOnce()
            "NO_SUBSCRIBER" -> vibrateTwice()
            "CONNECTED_READY" -> vibrateThrice()
            "SENDING" -> vibrateFourTimes()
            else -> vibrateLong()
        }
        Log.d("UIStateChanged", "UI state changed to $stateString")
        activityRef?.get()?.runOnUiThread {
            uiStateText?.text = stateString
        }
    }

    private fun vibrateLong(){
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    private fun vibrateOnce(){
        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    private fun vibrateTwice(){
        vibrateMultiple(2, 100, 100)
    }
    private fun vibrateThrice(){
        vibrateMultiple(3, 100, 100)
    }
    private fun vibrateFourTimes(){
        vibrateMultiple(4, 100, 100)
    }
    private fun vibrateMultiple(times: Int, duration: Long, delay: Long){
        val timings = LongArray(times * 2 - 1)
        for (i in 0 until times){
            timings[i * 2] = duration
            if (i < times - 1){
                timings[i * 2 + 1] = delay
            }
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}