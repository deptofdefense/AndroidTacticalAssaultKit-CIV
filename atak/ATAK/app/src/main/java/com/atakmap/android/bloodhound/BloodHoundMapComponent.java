
package com.atakmap.android.bloodhound;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.bloodhound.link.BloodHoundLinkManager;
import com.atakmap.android.bloodhound.link.BloodHoundLinkReceiver;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbars.RangeAndBearingToolbar;
import com.atakmap.coremap.log.Log;

public class BloodHoundMapComponent extends AbstractMapComponent {

    public static final String TAG = "BloodHoundMapComponent";

    protected BloodHoundTool _buttonTool;
    private BloodHoundLinkReceiver _linkReceiver;
    private BloodHoundLinkManager _linkManager;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        // Bloodhound info for any given R&B line, displayed on the line label
        _linkManager = new BloodHoundLinkManager(view);
        _linkReceiver = new BloodHoundLinkReceiver(view, _linkManager);

        // Tool that displays bloodhound data for any 2 markers in the
        // bottom-left corner widget
        _buttonTool = new BloodHoundTool(view);
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
        _buttonTool.dispose();
        _linkReceiver.dispose();
        _linkManager.dispose();
        try {
            RangeAndBearingToolbar.dispose();
        } catch (Exception e) {
            Log.e(TAG, "error occurred cleaning up R&B", e);
        }

    }
}
