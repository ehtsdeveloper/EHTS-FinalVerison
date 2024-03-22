package com.ehts.ehtswatch

import android.media.MediaRecorder
import java.io.IOException

// Defines a class for handling voice recording, taking the file path for the recording as a constructor parameter.
class VoiceRecording(private val audioFilePath: String) {
    private var recorder: MediaRecorder? = null  // Holds a reference to the MediaRecorder object.

    // Starts the voice recording process.
    fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)  // Sets the audio source to the device's microphone.
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)  // Sets the output format of the recording.
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)  // Sets the audio encoder for the recording.
            setOutputFile(audioFilePath)  // Sets the file path where the recording will be saved.

            // Prepares the MediaRecorder to start recording.
            try {
                prepare()
                start()  // Starts recording audio.
            } catch (e: IOException) {
                e.printStackTrace()  // Logs an error if the recorder cannot be prepared or started.
            }
        }
    }

    // Stops the voice recording process and releases the MediaRecorder resources.
    fun stopRecording() {
        recorder?.apply {
            stop()  // Stops the recording.
            release()  // Releases the MediaRecorder resources.
            recorder = null  // Clears the reference to the MediaRecorder to allow it to be garbage collected.
        }
    }
}
