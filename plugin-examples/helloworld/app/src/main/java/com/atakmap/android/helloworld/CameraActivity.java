
package com.atakmap.android.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class CameraActivity extends Activity {
    private static final int CAMERA_REQUEST = 8888;
    private static final String CAMERA_INFO = "com.atakmap.android.helloworld.PHOTO";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent cameraIntent = new Intent(
                android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST);
    }

    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        Intent i = new Intent(CAMERA_INFO);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap photo = (Bitmap) extras.get("data");
                i.putExtra("image", photo);
            }
        }
        sendBroadcast(i);
        finish();
    }

    public interface CameraDataReceiver {
        void onCameraDataReceived(Bitmap b);
    }

    /**
     * Broadcast Receiver that is responsible for getting the data back to the 
     * plugin.
     */
    static class CameraDataListener extends BroadcastReceiver {
        private boolean registered = false;
        private CameraDataReceiver cdr = null;

        synchronized public void register(Context context,
                CameraDataReceiver cdr) {
            if (!registered)
                context.registerReceiver(this, new IntentFilter(CAMERA_INFO));

            this.cdr = cdr;
            registered = true;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                try {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Bitmap bm = (Bitmap) extras.get("image");
                        if (bm != null && cdr != null)
                            cdr.onCameraDataReceived(bm);
                    }
                } catch (Exception ignored) {
                }
                if (registered) {
                    context.unregisterReceiver(this);
                    registered = false;
                }
            }
        }

    }
}
