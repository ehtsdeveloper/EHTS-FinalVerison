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
import android.net.Uri
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaRecorder
import java.io.IOException

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
    private lateinit var goBackButton: Button
    private lateinit var stopButton: Button
    private lateinit var startButton: Button
    private lateinit var textHeartRate: TextView
    private lateinit var databaseReference: DatabaseReference
    private var empId: String? = null
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var voiceRecording: VoiceRecording? = null  // Added for voice recording
    private lateinit var audioFilePath: String  // Now 'audioFilePath' is a class-level variable

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

        // Check if body sensors permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_BODY_SENSORS)
        } else {
            registerHeartRateSensorListener()
        }

        //check and get permission for audio recording
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf( Manifest.permission.RECORD_AUDIO), 10)
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

            stopVoiceRecordingAndSendAudio()

            stopHeartRateRecordingService()
            calculateHeartRateMetrics() // Calculate heart rate metrics one final time
            timerHandler.removeCallbacksAndMessages(null) // Stop the timer completely
            stopButton.visibility = Button.INVISIBLE
            Toast.makeText(this, "Test Ended", Toast.LENGTH_SHORT).show() // Display a toast message
        }

        // Set click listener for the "Start" button
        startButton.setOnClickListener {
            recordingStartTime = System.currentTimeMillis() // Get the start timestamp when the button is clicked
            startHeartRateRecording()

            startVoiceRecording()

            startButton.visibility = Button.INVISIBLE // Hide the start button
            Toast.makeText(this, "Test started", Toast.LENGTH_SHORT).show() // Display a toast message

        }
    }

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
                restingHeartRate, lowHeartRate, maxHeartRate, recordingStartTimestamp, recordingStopTimestamp
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

    private fun startVoiceRecording() {
        audioFilePath = getUniqueAudioFilePath()  // Generate a unique file path for the recording
        voiceRecording = VoiceRecording(audioFilePath)  // Initialize VoiceRecording with the file path
        voiceRecording?.startRecording()  // Start voice recording

    }



    private fun stopVoiceRecordingAndSendAudio() {
        voiceRecording?.stopRecording()  // Stop voice recording
        sendAudioFileToMobile()   // Send the audio file to the mobile app
    }

    private fun getUniqueAudioFilePath(): String {
        val directory = getExternalFilesDir(null)  // Use app-specific storage directory
        val fileName = "test_audio_${System.currentTimeMillis()}.3gp"  // Unique file name using timestamp
        return File(directory, fileName).absolutePath  // Return the absolute file path
    }

    private fun sendAudioFileToMobile() {
        val wearableDataClient = Wearable.getDataClient(this)

        // Convert audio file to Asset
        val uri = Uri.fromFile(File(audioFilePath))
        val asset = Asset.createFromUri(uri)

        // Create a DataMap and put the audio Asset in it
        val dataMap = DataMap().apply {
            putAsset("audioAsset", asset)
        }

        // Create a PutDataMapRequest with the DataMap
        val putDataMapReq = PutDataMapRequest.create("/audio").apply {
            dataMap.putAll(dataMap)  // Correctly put the DataMap into the PutDataMapRequest
        }

        // Convert PutDataMapRequest to PutDataRequest and set it as urgent
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()

        // Send the data to the data layer
        val dataItemTask = wearableDataClient.putDataItem(putDataReq)
        dataItemTask.addOnSuccessListener {
            // Handle success
        }.addOnFailureListener {
            // Handle failure
        }
    }



}