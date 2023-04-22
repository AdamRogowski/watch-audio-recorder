package com.example.watchrectest

import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    fun setActivity(activity: AppCompatActivity) {
        activityRef = WeakReference(activity)
    }

    fun setUIState(state: UIState){
        val stateString = state.toString()
        Log.d("UIStateChanged", "UI state changed to $stateString")
        activityRef?.get()?.runOnUiThread {
            uiStateText?.text = stateString
        }
    }
}