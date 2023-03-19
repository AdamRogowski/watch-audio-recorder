package com.example.watchrectest

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

class LogManager(private val activity: Activity) {

    private val textViewLog: TextView
        get() = activity.findViewById(R.id.textViewLog)
    private val scrollViewLog: ScrollView
        get() = activity.findViewById(R.id.scrollViewLog)

    @SuppressLint("SetTextI18n")
    fun appendLog(message: String) {
        Log.d("appendLog", message)
        activity.runOnUiThread {
            textViewLog.text = textViewLog.text.toString() + "\n$message"

            // wait for the textView to update
            Handler(Looper.getMainLooper()).postDelayed({
                scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }, 20)
        }
    }

    @SuppressLint("SetTextI18n")
    fun onTapClearLog(view: View) {
        textViewLog.text = "Logs:"
        appendLog("log cleared")
    }
}