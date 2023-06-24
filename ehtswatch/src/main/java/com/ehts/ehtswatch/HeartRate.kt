package com.ehts.ehtswatch

import android.Manifest
import android.app.*
import androidx.core.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

//This class needs to record heart rate continuously with the actual timestamp (ex: 6:00pm)
//this feature needs to be able to run in the background even when wifi isn't connected without the user knowing whats being recorded
class HeartRate : Activity(), SensorEventListener {
    private companion object {
        private const val TAG = "HeartRateMonitor"
        private const val PERMISSION_REQUEST_BODY_SENSORS = 1
        private const val NOTIFICATION_CHANNEL_ID = "HeartRateChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_ID = 1
        private const val STOP_ACTION = "StopRecordingAction"
    }

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private val heartRateData: MutableList<Double> = ArrayList()
    private var recordingStartTime: Long = 0L
    private var restingHeartRate = 0.0
    private var maxHeartRate = 0.0
    private var lowHeartRate = 0.0
    private lateinit var goBackButton: Button
    private lateinit var stopButton: Button
    private lateinit var textHeartRate: TextView
    private lateinit var databaseReference: DatabaseReference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)

        // Initialize sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // Check if body sensors permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_BODY_SENSORS)
        } else {
            registerHeartRateSensorListener()
        }

        // Find the TextView and buttons in the layout
        textHeartRate = findViewById(R.id.textHeartRate)
        goBackButton = findViewById(R.id.gobackbtn)
        stopButton = findViewById(R.id.stopButton)

        // Set click listener for the "Go Back" button
        goBackButton.setOnClickListener {
            // Navigate back to MainActivity
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Set click listener for the "Stop" button
        stopButton.setOnClickListener {
            unregisterHeartRateSensorListener()
            stopHeartRateRecordingService()
            calculateHeartRateMetrics()
            stopButton.visibility = Button.INVISIBLE
        }
    }

    private fun registerHeartRateSensorListener() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        recordingStartTime = SystemClock.elapsedRealtime()
    }

    private fun unregisterHeartRateSensorListener() {
        sensorManager.unregisterListener(this, heartRateSensor)
    }

    override fun onResume() {
        super.onResume()
        registerHeartRateSensorListener()
    }

    override fun onPause() {
        super.onPause()
        unregisterHeartRateSensorListener()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_BODY_SENSORS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerHeartRateSensorListener()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRateValue = event.values[0].toDouble()
            heartRateData.add(heartRateValue)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    private fun calculateHeartRateMetrics() {
        if (heartRateData.isNotEmpty()) {
            var sum = 0.0
            var min = heartRateData[0]
            var max = heartRateData[0]
            for (heartRate in heartRateData) {
                sum += heartRate
                if (heartRate < min) {
                    min = heartRate
                }
                if (heartRate > max) {
                    max = heartRate
                }
            }
            restingHeartRate = sum / heartRateData.size
            lowHeartRate = min
            maxHeartRate = max

            // Get the timestamp of the recording
            val recordingTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Store the heart rate data in your database


            // Print the heart rate metrics and recording timestamp
            val heartRateText = "Heart Rate\nResting: %.1f\nLow: %.1f\nMax: %.1f\nRecording Timestamp: %s".format(restingHeartRate, lowHeartRate, maxHeartRate, recordingTimestamp)
            textHeartRate.text = heartRateText
        }
    }

    private fun startHeartRateRecordingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the notification channel for Android Oreo and above
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Heart Rate",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Create the notification without content and title
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.loginlogo)
            .build()

        // Start the foreground service
        val serviceIntent = Intent(this, HeartRateRecordingService::class.java)
        serviceIntent.putExtra(HeartRateRecordingService.NOTIFICATION_EXTRA, notification)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopHeartRateRecordingService() {
        val serviceIntent = Intent(this, HeartRateRecordingService::class.java)
        serviceIntent.action = STOP_ACTION
        stopService(serviceIntent)
    }

    /**
     * Foreground service for heart rate recording
     */
    class HeartRateRecordingService : Service() {
        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            if (intent?.action == STOP_ACTION) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val notificationString = intent?.getStringExtra("notification")
                val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.loginlogo)
                    .setContentText(notificationString)
                    .build()
                startForeground(NOTIFICATION_ID, notification)
            }
            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        companion object {
            const val NOTIFICATION_EXTRA = "notification"
        }
    }
}
