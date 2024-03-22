package com.EHTS.ehts_v1;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class DataLayerListenerService extends WearableListenerService {

    private static final String TAG = "DataLayerService";
    public static final String AUDIO_FILE_PATH = "com.EHTS.ehts_v1.AUDIO_FILE_PATH";
    public static final String AUDIO_FILE_READY_ACTION = "com.EHTS.ehts_v1.AUDIO_FILE_READY_ACTION";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                Uri uri = event.getDataItem().getUri();
                if ("/audio".equals(uri.getPath())) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    Asset audioAsset = dataMapItem.getDataMap().getAsset("audioAsset");
                    saveAudioFile(audioAsset);
                }
            }
        }
    }

    private void saveAudioFile(Asset asset) {
        if (asset == null) {
            Log.w(TAG, "Asset is null, nothing to process.");
            return;
        }

        // Convert asset into a file
        try {
            InputStream assetInputStream = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset)).getInputStream();
            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return;
            }

            // Read data from the Asset and write it to a file on internal storage
            File audioFile = new File(getFilesDir(), "audio_file.3gp");
            try (FileOutputStream outputStream = new FileOutputStream(audioFile)) {
                byte[] buffer = new byte[4 * 1024];
                int bytesRead;
                while ((bytesRead = assetInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Broadcast or use some other method to notify your activity that the audio file is ready
            // For example, you could use a LocalBroadcastManager, EventBus, or similar.
            // Here's a simple broadcast intent example:
            Intent intent = new Intent(AUDIO_FILE_READY_ACTION);
            intent.putExtra(AUDIO_FILE_PATH, audioFile.getAbsolutePath());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        } catch (Exception e) {
            Log.e(TAG, "Saving audio file failed", e);
        }
    }
}

