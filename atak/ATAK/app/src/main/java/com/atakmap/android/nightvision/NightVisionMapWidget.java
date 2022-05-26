
package com.atakmap.android.nightvision;

import android.content.Intent;
import android.graphics.Color;
import android.view.MotionEvent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

/**
 *
 * Creates and assigns the map widget using the Icon class
 *
 */
public class NightVisionMapWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener {

    public final static String TAG = "NightVision";

    public final String imageUri;

    NightVisionMapWidget(MapView mapView) {
        this.imageUri = "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + R.drawable.nvg_icon_50;
    }

    /**Constructs a Icon object from supplied parameters
     * @return and Icon object
     */
    Icon createIcon(int size) {
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(size, size);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);
        return builder.build();
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        Log.d(NightVisionMapWidgetComponent.TAG, "widget clicked");
        AtakBroadcast.getInstance().sendSystemBroadcast(
                new Intent("nightvision.com.atak.NVG_MODE"));
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(NightVisionReceiver.ADJUST_NIGHT_VISION_VALUE));
    }
}
