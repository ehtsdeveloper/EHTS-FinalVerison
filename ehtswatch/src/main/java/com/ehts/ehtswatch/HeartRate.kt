package com.ehts.ehtswatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.*

//This class records a users heart rate continuously and evaluates the user low, resting, and max hr and records the timestamp
//User can administer multiple tests
//after test is over stop action sends the test results to the database
//foreground service needs to push recorded data to database.
class HeartRate : Activity() {
    private companion object {
        private const val PERMISSION_REQUEST_BODY_SENSORS = 1
    }

    private var empId: String? = null

    private lateinit var goBackButton: Button
    private lateinit var stopButton: Button
    private lateinit var startButton: Button
    private lateinit var textHeartRate: TextView

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val min = intent.extras?.getDouble(HeartRateForegroundService.INTENT_EXTRA_MIN)
            val max = intent.extras?.getDouble(HeartRateForegroundService.INTENT_EXTRA_MAX)
            val average = intent.extras?.getDouble(HeartRateForegroundService.INTENT_EXTRA_AVERAGE) ?: return
            val startTime = intent.extras?.getString(HeartRateForegroundService.INTENT_EXTRA_START) ?: return
            val endTime = intent.extras?.getString(HeartRateForegroundService.INTENT_EXTRA_STOP) ?: return

            Handler(Looper.getMainLooper()).post {
                updateMetrics(min, max, average, startTime, endTime)
            }
        }
    }

    private fun updateMetrics(min: Double?, max: Double?, average: Double, startTime: String, endTime: String) {
        val heartRateText = String.format(
                Locale.getDefault(),
                "Heart Rate\nResting: %.1f\nLow: %.1f\nMax: %.1f\nRecording Start Timestamp: %s\nRecording Stop Timestamp: %s",
                average, min, max, startTime, endTime
        )

        textHeartRate.text = heartRateText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)

        val bundle = intent.extras
        if (bundle != null) {
            empId = bundle.getString("Employee ID")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_BODY_SENSORS)
        }

        // Find the TextView and buttons in the layout
        textHeartRate = findViewById(R.id.textHeartRate)
        goBackButton = findViewById(R.id.gobackbtn)
        stopButton = findViewById(R.id.stopButton)
        startButton = findViewById(R.id.startButton)

        // Set click listener for the "Go Back" button
        goBackButton.setOnClickListener {
            // Navigate back to MainActivity
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Set click listener for the "Stop" button
        stopButton.setOnClickListener {
            stopHeartRateRecordingService()
            stopButton.visibility = Button.INVISIBLE
            Toast.makeText(this, "Test Ended", Toast.LENGTH_SHORT).show() // Display a toast message
        }

        // Set click listener for the "Start" button
        startButton.setOnClickListener {
            startHeartRateRecording()
            startButton.visibility = Button.INVISIBLE // Hide the start button
            Toast.makeText(this, "Test started", Toast.LENGTH_SHORT).show() // Display a toast message
        }

        ContextCompat.registerReceiver(this, receiver, IntentFilter(HeartRateForegroundService.INTENT_ACTION_HEART_RATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun startHeartRateRecording() {
        val notificationString = "EHTS Recording" // Customize the notification text as needed

        val serviceIntent = Intent(this, HeartRateForegroundService::class.java)
        serviceIntent.putExtra(HeartRateForegroundService.NOTIFICATION_EXTRA, notificationString)
        serviceIntent.putExtra(HeartRateForegroundService.EMP_ID_EXTRA, empId);
        startService(serviceIntent)
    }

    private fun stopHeartRateRecordingService() {
        val serviceIntent = Intent(this, HeartRateForegroundService::class.java)
        serviceIntent.action = HeartRateForegroundService.STOP_ACTION
        stopService(serviceIntent)
    }

}