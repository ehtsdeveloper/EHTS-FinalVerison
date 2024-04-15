package com.ehts.ehtswatch

// HeartRateForegroundService.kt
import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class HeartRateForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private var heartRateSensor: Sensor? = null
    private var heartRateData: MutableList<Double> = ArrayList()

    private val timerHandler: Handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var empId: String? = null

    private var isTestRunning: Boolean = false
    private var recordingStartTime: Long = 0L
    private var recordingStopTile: Long = 0L

    // Implement the necessary methods for SensorEventListener
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRateValue = event.values[0].toDouble()
            println("Receive heart rate: ${heartRateValue}")
            // Add the heart rate value to the heartRateData list
            heartRateData.add(heartRateValue)
            // You can also push the heart rate value to your database here if needed
        }
    }

    private fun startListening() {
        recordingStartTime = System.currentTimeMillis()
        isTestRunning = true

        timerRunnable = Runnable {
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        timerHandler.postDelayed(timerRunnable!!, TRACKING_INTERVAL.toLong())
    }

    private fun stopListening() {
        isTestRunning = false
        recordingStopTile = System.currentTimeMillis()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        saveMetrics()
    }

    private fun saveMetrics() {
        println("Saving metrics")

        val filteredData = heartRateData.filter { it != 0.0 }
        if (filteredData.isEmpty()) return

        val min = filteredData.minOrNull()
        val max = filteredData.maxOrNull()
        val average = filteredData.average()

        val startTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(recordingStartTime))
        val endTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

        val data = SensorsData(average, min, max, startTime, endTime)
        val empId = this.empId ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val myRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)
                .child("employees").child(empId)
                .child("sensors_record").child(empId)
        val uniqueKey = myRef.push().key ?: Random().toString()

        FirebaseDatabase.getInstance().reference.child("users").child(userId)
                .child("employees").child(empId)
                .child("sensors_record").child(empId).child(uniqueKey)
                .setValue(data)
                .addOnSuccessListener {
                    Toast.makeText(this@HeartRateForegroundService, "Saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this@HeartRateForegroundService, "Failed to save data", Toast.LENGTH_SHORT).show()
                }

        heartRateData.clear()

        val intent = Intent(INTENT_ACTION_HEART_RATE)
        intent.putExtra(INTENT_EXTRA_MIN, min)
        intent.putExtra(INTENT_EXTRA_MAX, max)
        intent.putExtra(INTENT_EXTRA_AVERAGE, average)
        intent.putExtra(INTENT_EXTRA_START, startTime)
        intent.putExtra(INTENT_EXTRA_STOP, endTime)
        sendBroadcast(intent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_ACTION) {
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            empId = intent?.getStringExtra(EMP_ID_EXTRA)

            val notificationString = intent?.getStringExtra(NOTIFICATION_EXTRA)
            val notification = createNotification(notificationString)
            startForeground(NOTIFICATION_ID, notification)

            // Initialize sensor manager
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

            // Check if body sensors permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                // Register the heart rate sensor listener
                registerHeartRateSensorListener()
            } else {
                // Request the body sensors permission
                requestBodySensorsPermission()
            }
        }
        return START_STICKY
    }

    private fun registerHeartRateSensorListener() {
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        startListening()
    }

    private fun unregisterHeartRateSensorListener() {
        sensorManager.unregisterListener(this, heartRateSensor)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterHeartRateSensorListener()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(notificationString: String?): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "EHTS Test",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.loginlogo)
            .setContentText(notificationString)
            .build()
    }

    private fun requestBodySensorsPermission() {
        // Request body sensors permission here
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "HeartRateChannel"
        private const val NOTIFICATION_ID = 1

        const val INTENT_ACTION_HEART_RATE = "receive_heart_rate"
        const val INTENT_EXTRA_MIN = "min"
        const val INTENT_EXTRA_MAX = "max"
        const val INTENT_EXTRA_AVERAGE = "average"
        const val INTENT_EXTRA_START = "start"
        const val INTENT_EXTRA_STOP = "stop"

        const val STOP_ACTION = "StopRecordingAction"
        const val NOTIFICATION_EXTRA = "notification"
        const val EMP_ID_EXTRA = "emp_id"
        const val TRACKING_INTERVAL = 60 * 1000
    }
}
