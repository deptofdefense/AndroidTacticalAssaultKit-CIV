
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * @deprecated use MarkerCreator to create and place markers
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = false)
public class PlaceBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = "PlaceBroadcastReceiver";

    private final MapView _mapView;

    public PlaceBroadcastReceiver(MapView mapView) {
        _mapView = mapView;
    }

    /**
     *
     * @param context the context
     * @param intent the intent
     *
     * DO NOT use "com.atakmap.android.maps.PLACE" instead use  MarkerCreator to create 
     * and place your markers.  This should only be used by the radial menu actions.
     * @deprecated use MarkerCreator to create and place markers
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.atakmap.android.maps.PLACE".equals(intent.getAction())) {
            String pointString = intent.getStringExtra("point");
            GeoPoint location = GeoPoint.parseGeoPoint(pointString);
            PlacePointTool.MarkerCreator markerCreator = new PlacePointTool.MarkerCreator(
                    location);
            if (intent.hasExtra("atself")) {
                markerCreator = new PlacePointTool.MarkerCreator(
                        _mapView.getSelfMarker().getUID());
            }
            if (intent.hasExtra("atUID")) {
                markerCreator = new PlacePointTool.MarkerCreator(
                        intent.getStringExtra("atUID"));
            }
            String uid = intent.getStringExtra("uid");
            if (uid != null)
                markerCreator.setUid(uid);
            markerCreator.setReadiness(intent
                    .getBooleanExtra("readiness", true));
            if (intent.hasExtra("type"))
                markerCreator.setType(intent.getStringExtra("type"));
            if (intent.hasExtra("color"))
                markerCreator
                        .setColor(intent.getIntExtra("color", Color.WHITE));
            markerCreator.setIconPath(intent
                    .getStringExtra(UserIcon.IconsetPath));
            if (intent.hasExtra("callsign"))
                markerCreator.setCallsign(intent.getStringExtra("callsign"));
            if (intent.hasExtra("prefix"))
                markerCreator.setPrefix(intent.getStringExtra("prefix"));
            if (intent.hasExtra("show_cot_details")) {
                String details = intent.getStringExtra("show_cot_details");
                if (details.equals("false")) {
                    markerCreator.showCotDetails(false);
                }
            }
            if (intent.getStringExtra("nine_line") != null &&
                    intent.getStringExtra("nine_line").equals("true")) {
                markerCreator.setShowNineLine(true);
            }

            if (intent.getStringExtra("five_line") != null &&
                    intent.getStringExtra("five_line").equals("true")) {
                markerCreator.setShowFiveLine(true);
            }

            if (intent.getStringExtra("action") != null) {
                markerCreator.setAction(intent.getStringExtra("action"));
            }

            if (intent.hasExtra("show_new_radial") &&
                    intent.getStringExtra("show_new_radial").equals("true")) {
                markerCreator.setShowNewRadial(true);
            }
            markerCreator.placePoint();

            // Added by Tim Lopes: Send out an intent when placement is completed so others may
            // do
            // things with the new object
            /*
            ANDREW WANTS TO REMOVE THIS, BUT NEEDS TO DOUBLE CHECK IMPACT BEFORE DOING SO
            NEW PlacePointTool SHOULD TAKE CARE OF THIS PROBLEM.
             */
            Intent new_cot_intent = new Intent();
            new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
            new_cot_intent.putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(new_cot_intent);

        }
    }

}
