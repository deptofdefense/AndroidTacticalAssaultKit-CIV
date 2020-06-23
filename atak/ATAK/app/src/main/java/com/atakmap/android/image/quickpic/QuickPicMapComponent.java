
package com.atakmap.android.image.quickpic;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.toolbar.tools.MovePointTool;

/**
 * Map Component to quickly capture a picture to save and/or send.
 * This launches the default Android camera or prompts the user for 
 * the camera to use when taking a quick pic.
 * 
 * 
 */
public class QuickPicMapComponent extends AbstractMapComponent {
    static public final String TAG = "QuickPicMapComponent";

    private QuickPicReceiver _quickPicReceiver;

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView mapView) {
        _quickPicReceiver = new QuickPicReceiver(context, mapView);

        MovePointTool.addPromptType("b-i-x");

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(QuickPicReceiver.QUICK_PIC,
                "Start quick-pic request");
        f.addAction(QuickPicReceiver.QUICK_PIC_CAPTURED,
                "Fired when quick-pic has been captured.",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "Newly created marker UID",
                                false, String.class),
                        new DocumentedExtra("path", "Path to the new image",
                                false, String.class)
                });
        f.addAction(QuickPicReceiver.QUICK_PIC_RECEIVED,
                "Fired after the quick-pic has been received by the image drop-down",
                new DocumentedExtra[] {
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDERCALLSIGN,
                                "The callsign of the sender",
                                false, String.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                "Mission package manifest containing quick-pic",
                                false, MissionPackageManifest.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                                "ID of MP received notification",
                                false, Integer.class)
                });
        f.addAction(QuickPicReceiver.QUICK_PIC_VIEW,
                "Display image in drop-down and focus on its marker",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid",
                                "Quick-pic marker UID",
                                false, String.class),
                        new DocumentedExtra("focusmap",
                                "True to focus on the marker",
                                true, Boolean.class)
                });
        f.addAction(QuickPicReceiver.QUICK_PIC_MOVE,
                "Move a quick-pic marker and associated images",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid",
                                "Quick-pic marker UID",
                                false, String.class),
                        new DocumentedExtra("point",
                                "New position geo point string",
                                false, String.class)
                });
        AtakBroadcast.getInstance().registerReceiver(_quickPicReceiver, f);

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_quickPicReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_quickPicReceiver);
            _quickPicReceiver.dispose();
            _quickPicReceiver = null;
        }
    }
}
