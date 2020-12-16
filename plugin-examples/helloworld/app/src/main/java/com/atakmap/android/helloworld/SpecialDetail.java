
package com.atakmap.android.helloworld;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import android.view.View;
import android.widget.TextView;
import android.view.LayoutInflater;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.helloworld.plugin.R;
import android.content.Context;
import android.content.Intent;

public class SpecialDetail extends DropDownReceiver implements OnStateListener {
    final String TAG = "SpecialDetail";
    final Context pluginContext;

    public SpecialDetail(final MapView mapView, final Context pluginContext) {
        super(mapView);
        this.pluginContext = pluginContext;
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        if (action != null && action
                .equals("com.atakmap.android.helloworld.myspecialdetail")) {
            PointMapItem temp = findTarget(intent.getStringExtra("targetUID"));
            if (temp != null) {
                LayoutInflater inflater = LayoutInflater.from(pluginContext);
                View v = inflater.inflate(R.layout.specialdetail, null);
                TextView tv = v.findViewById(R.id.callsign);
                tv.setText(temp.getMetaString("callsign", "[no callsign]"));
                if (!isVisible()) {
                    setRetain(true);
                    showDropDown(v, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                            FULL_WIDTH,
                            HALF_HEIGHT, this);
                }
            }
        }
    }

    private PointMapItem findTarget(final String targetUID) {
        PointMapItem pointItem = null;
        if (targetUID != null) {
            MapItem item = getMapView().getMapItem(targetUID);
            if (item instanceof PointMapItem) {
                pointItem = (PointMapItem) item;
            }
        }
        return pointItem;
    }

    @Override
    protected boolean onBackButtonPressed() {
        Log.d(TAG,
                "back button pressed, but returning false because it is not handled");
        return false;
    }

    @Override
    public void onDropDownSelectionRemoved() {
        // the selected item was removed while the drop down was open, close the drop down and
        // do not recreate the marker.
        Log.d(TAG, "item removed from the screen");
    }

    @Override
    public void onDropDownVisible(boolean v) {
        Log.d(TAG, "drop down visible: " + v);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
        Log.d(TAG, "drop down size changed");
    }

    @Override
    public void onDropDownClose() {
        Log.d(TAG, "drop down closed");
    }

    public void disposeImpl() {
        Log.d(TAG, "disposeImpl");
    }

}
