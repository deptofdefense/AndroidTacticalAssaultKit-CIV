
package com.atakmap.android.image;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

/**
 * Image Edit Receiver Activity is used to receive the intent from image edits largely 
 * from the Image Markup tool but could be from other software applications.  The on
 * receive does make use of Parcelable extraction from the intent, but the type is cast
 * as a Uri immediately and it is unclear how to completely protect against an external 
 * entity passing in something other than a Uri.   Continue to evaluate each release 
 * and see if Android is able to prevent this type of attack at a lower level. 
 */
public class ImageEditReceiver
        extends BroadcastReceiver {
    public static final String TAG = "ImageEditReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent from image edit: " + intent);
        final Intent refreshIntent = new Intent(
                "com.atakmap.maps.images.REFRESH");

        if (intent.hasExtra("uid")) {
            refreshIntent.putExtra("uid", intent.getStringExtra("uid"));
        }
        if (intent.hasExtra("imageURI")) {
            refreshIntent.putExtra("imageURI",
                    intent.getStringExtra("imageURI"));
        } else if (intent.hasExtra("filepath")) {
            refreshIntent.putExtra("imageURI",
                    "file://"
                            + intent.getStringExtra("filepath"));
        }
        MapView mv = MapView.getMapView();
        if (mv != null) // Need to check that ATAK is started - see ATAK-10216
            AtakBroadcast.getInstance().sendBroadcast(refreshIntent);
    }
}
