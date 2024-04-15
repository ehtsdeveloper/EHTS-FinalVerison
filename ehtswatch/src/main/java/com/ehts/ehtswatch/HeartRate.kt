package com.ehts.ehtswatch


import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.text.SimpleDateFormat
import java.util.*
/*References: 1. https://developer.samsung.com/codelab/health/blood-oxygen.html including Measuring Blood Oxygen Sample Code
2. Samsung Privileged SDK Programming Guide  */
//This class records a users heart rate continuously and evaluates the user low, resting, and max hr and records the timestamp
//User can administer multiple tests
//after test is over stop action sends the test results to the database
//foreground service needs to push recorded data to database.
class HeartRate : Activity(), SensorEventListener {
    private companion object {
        private const val TAG = "HeartRateMonitor"
        private const val PERMISSION_REQUEST_BODY_SENSORS = 1
        private const val NOTIFICATION_CHANNEL_ID = "HeartRateChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_ID = 1
        private const val STOP_ACTION = "StopRecordingAction"
        private const val TIMER_INTERVAL = 1 * 60 * 1000 // 1 minute in milliseconds THIS CAN BE ADJUSTED TO ANY TIME
    }

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var heartRateData: MutableList<Double> = ArrayList()
    private var isTestRunning = false
    private var recordedHeartRateDataList: MutableList<Double> = ArrayList()
    private var recordingStartTime: Long = 0L
    private var recordingStopTime: Long = 0L
    private var restingHeartRate = 0.0
    private var maxHeartRate = 0.0
    private var lowHeartRate = 0.0
    private var OxygenStart = 0.0
    private var OxygenEnd = 0.0
    private lateinit var goBackButton: Button
    private lateinit var stopButton: Button
    private lateinit var startButton: Button
    private lateinit var textHeartRate: TextView
    private lateinit var databaseReference: DatabaseReference
    private var empId: String? = null
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    //RK oxygen start
    var oxygentracker: HealthTracker? =null
    var healthtrackingservice: HealthTrackingService?=null
    private val connectionListen:ConnectionListener= object : ConnectionListener {
        override fun onConnectionSuccess() {
            var c= healthtrackingservice?.trackingCapability?.supportHealthTrackerTypes
            var available:MutableList<HealthTrackerType> = c as MutableList<HealthTrackerType>
            if (!available.contains(HealthTrackerType.SPO2)){
                //spo2 tracker not available
            }
        }

        override fun onConnectionEnded() {
            TODO("Not yet implemented")
        }

        override fun onConnectionFailed(p0: HealthTrackerException?) {
            TODO("Not yet implemented")
        }

    }
    private val trackerListen: HealthTracker.TrackerEventListener=object:
        HealthTracker.TrackerEventListener {
        override fun onDataReceived(p0: MutableList<DataPoint>) { //EY adding data via samsung developer tortorial
            p0.forEach { data ->
                updateSpo2(data)

            }
        }
        private fun updateSpo2(data: DataPoint) {
            val status = data.getValue(ValueKey.SpO2Set.STATUS)
            var spo2Value = 0
            //if (status == MEASUREMENT_COMPLETED) {
                spo2Value = data.getValue(ValueKey.SpO2Set.SPO2)
           // }
          // ObserverUpdater.getObserverUpdater().notifyTrackerObservers(status, spo2Value)
            OxygenStart = spo2Value.toDouble()
        }

        override fun onFlushCompleted() {
            TODO("Not yet implemented")
        }

        override fun onError(p0: HealthTracker.TrackerError?) {
            TODO("Not yet implemented")
        }

    }



    //RK oxygen end
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)

        val bundle = intent.extras
        if (bundle != null) {
            empId = bundle.getString("Employee ID")
        }
        // Initialize sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        //Rk oxygen start
        healthtrackingservice= HealthTrackingService(connectionListen, this);
        healthtrackingservice!!.connectService()
        //RK oxygen end
        // Check if body sensors permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_BODY_SENSORS)
        } else {
            registerHeartRateSensorListener()
            //RK oxygen start
            trackOxygen()
            //RK oxygen end
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
            unregisterHeartRateSensorListener()
            //RK oxygen start
            stoptrackOxygen()
            //RK oxygen end
            stopHeartRateRecordingService()
            calculateHeartRateMetrics() // Calculate heart rate metrics one final time
            timerHandler.removeCallbacksAndMessages(null) // Stop the timer completely
            stopButton.visibility = Button.INVISIBLE
            Toast.makeText(this, "Test Ended", Toast.LENGTH_SHORT).show() // Display a toast message
        }

        // Set click listener for the "Start" button
        startButton.setOnClickListener {
            //EY: oxygen probably needs to go here
            recordingStartTime = System.currentTimeMillis() // Get the start timestamp when the button is clicked
            startHeartRateRecording()
            startButton.visibility = Button.INVISIBLE // Hide the start button
            Toast.makeText(this, "Test started", Toast.LENGTH_SHORT).show() // Display a toast message

        }
    }
    //RK oxygen start
    protected fun trackOxygen(){
        //var oxygentracker: HealthTracker? =null
        oxygentracker= healthtrackingservice?.getHealthTracker(HealthTrackerType.SPO2)
        oxygentracker?.setEventListener(trackerListen)

    }
    protected fun stoptrackOxygen(){

        oxygentracker?.unsetEventListener()

    }
    //RK oxygen end

    private fun registerHeartRateSensorListener() {
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)

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
        if (isTestRunning && event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRateValue = event.values[0].toDouble()
            heartRateData.add(heartRateValue)
            //recordedHeartRateDataList.add(heartRateValue)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }
    private fun calculateHeartRateMetrics() {
        if (heartRateData.isNotEmpty()) {
            var sum = 0.0
            var min = Double.MAX_VALUE
            var max = heartRateData[0]
            // Calculate the minimum heart rate excluding zero values
            val filteredHeartRateData = heartRateData.filter { it != 0.0 }
            if (filteredHeartRateData.isNotEmpty()) {
                min = filteredHeartRateData.minOrNull() ?: min
            }
            for (heartRate in heartRateData) {
                sum += heartRate
                if (heartRate > max) {
                    max = heartRate
                }
            }
            restingHeartRate = sum / heartRateData.size
            lowHeartRate = min
            maxHeartRate = max


            val recordingStartTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(recordingStartTime))
            val recordingStopTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())


            // Check if lowHeartRate is zero
            if (lowHeartRate != 0.0) {
                // EY add oxygen to database xia this
                val data = SensorsData(restingHeartRate, lowHeartRate, maxHeartRate, recordingStartTimestamp, recordingStopTimestamp)
                // Store the heart rate data in your database
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val myRef = FirebaseDatabase.getInstance().reference.child("users").child(userId ?: "")
                    .child("employees").child(empId ?: "")
                    .child("sensors_record").child(empId ?: "")
                val uniqueKey: String? = myRef.push().key
                userId?.let {
                    FirebaseDatabase.getInstance().reference.child("users").child(it)
                        .child("employees").child(empId ?: "")
                        .child("sensors_record").child(empId ?: "").child(uniqueKey ?: Random().toString())
                        .setValue(data)
                        .addOnSuccessListener {
                            Toast.makeText(this@HeartRate, "Saved", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@HeartRate, "Failed to save data", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                // Only push the other data values to the database
                val data = SensorsData(restingHeartRate, null, maxHeartRate, recordingStartTimestamp, recordingStopTimestamp)
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val myRef = FirebaseDatabase.getInstance().reference.child("users").child(userId ?: "")
                    .child("employees").child(empId ?: "")
                    .child("sensors_record").child(empId ?: "")
                val uniqueKey: String? = myRef.push().key
                userId?.let {
                    FirebaseDatabase.getInstance().reference.child("users").child(it)
                        .child("employees").child(empId ?: "")
                        .child("sensors_record").child(empId ?: "").child(uniqueKey ?: Random().toString())
                        .setValue(data)
                        .addOnSuccessListener {
                            Toast.makeText(this@HeartRate, "Saved", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@HeartRate, "Failed to save data", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            // Print the heart rate metrics and recording timestamp
            val heartRateText = String.format(
                Locale.getDefault(),
                "Heart Rate\nResting: %.1f\nLow: %.1f\nMax: %.1f\nRecording Start Timestamp: %s\nRecording Stop Timestamp: %s",
                restingHeartRate, lowHeartRate, maxHeartRate, recordingStartTimestamp, recordingStopTimestamp, OxygenStart
            )
            textHeartRate.text = heartRateText
            // Clear the heartRateData list after processing its contents
          //  recordedHeartRateDataList.clear()
            heartRateData.clear()


        }



    }


    private fun startHeartRateRecording() {
        val notificationString = "EHTS Recording" // Customize the notification text as needed

        val serviceIntent = Intent(this, HeartRateForegroundService::class.java)
        serviceIntent.putExtra(HeartRateForegroundService.NOTIFICATION_EXTRA, notificationString)
        startService(serviceIntent)

        recordingStartTime = System.currentTimeMillis()

        isTestRunning = true
        recordedHeartRateDataList.clear() // Clear the list before starting a new test

        // Delay the calculation of heart rate metrics
        timerHandler.postDelayed({
            calculateHeartRateMetrics()
            startHeartRateRecording()
        }, TIMER_INTERVAL.toLong())

        startTimer()

    }

    private fun stopHeartRateRecordingService() {
        val serviceIntent = Intent(this, HeartRateForegroundService::class.java)
        serviceIntent.action = HeartRateForegroundService.STOP_ACTION
        isTestRunning = false
        stopService(serviceIntent)
        recordingStopTime = System.currentTimeMillis()
        timerHandler.removeCallbacks(timerRunnable!!)
    }
    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (heartRateData.size > 0) {
                     // calculateHeartRateMetrics()
                }
            }
        }
        timerHandler.postDelayed(timerRunnable!!, TIMER_INTERVAL.toLong())
    }

    /**
     * Foreground service for heart rate recording
     */
/*
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

 */

}