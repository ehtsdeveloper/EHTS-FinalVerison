package com.ehts.ehtswatch
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import java.io.File  // Added for file handling
import com.google.android.gms.wearable.Asset
import android.net.Uri  // Import for Uri class
import com.google.android.gms.wearable.DataMap  // Import for DataMap
import com.google.android.gms.wearable.PutDataMapRequest  // Import for PutDataMapRequest
import com.google.android.gms.wearable.Wearable

//didn't work instead it was trying to overwrite data - you can delete any activity you don't end up using
//kept these just in case next team decides they want to add them or work on imporving the wearOS UI
class EMPStartTest : Activity() {
    private var auth: FirebaseAuth? = null

    private lateinit var startTest: Button
    private lateinit var goBackButton: Button
    private var logoutbtn: Button? = null
    private var voiceRecording: VoiceRecording? = null  // Added for voice recording
    private lateinit var audioFilePath: String  // Now 'audioFilePath' is a class-level variable
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emp_start_test)

        // Find the buttons in the layout
        auth = FirebaseAuth.getInstance()
        startTest = findViewById(R.id.startTest)
        goBackButton = findViewById(R.id.gobackbtn)
        logoutbtn = findViewById(R.id.logoutbtn)

        // Set click listener for the "Start Test" button
        startTest.setOnClickListener {
            // Start the HeartRate activity
            val intent = Intent(this, HeartRate::class.java)
            startActivity(intent)

            // Display a toast message
            Toast.makeText(this, "Test started", Toast.LENGTH_SHORT).show()

            startVoiceRecording()  // Added to start voice recording when the test starts
        }

        // Set click listener for the "Go Back" button
        goBackButton.setOnClickListener {
            // Finish the current activity and go back to the previous activity
            stopVoiceRecordingAndSendAudio()  // Added to stop voice recording when going back
            finish()
        }

        logoutbtn?.setOnClickListener {
            stopVoiceRecordingAndSendAudio()  // Added to stop voice recording when logging out
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(applicationContext, loginwatch::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun startVoiceRecording() {
        val audioFilePath = getUniqueAudioFilePath()  // Generate a unique file path for the recording
        voiceRecording = VoiceRecording(audioFilePath)  // Initialize VoiceRecording with the file path
        voiceRecording?.startRecording()  // Start voice recording
    }

    private fun stopVoiceRecordingAndSendAudio() {
        voiceRecording?.stopRecording()  // Stop voice recording
        audioFilePath?.let { sendAudioFileToMobile() }  // Send the audio file to the mobile app
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
