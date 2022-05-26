
package com.atakmap.android.helloworld.speechtotext;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.bloodhound.BloodHoundTool;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.helloworld.speechtotext.SpeechActivity;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.Route;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.user.geocode.GeocodingTask;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;

import java.util.UUID;

/**
 * This class broadcasts an intent to the bloodhound in ATAK
 * The idea is to say "Bloodhound to callsign AVALON" or "Bloodhound to route route1"
 * or "Bloodhound to Taco Bell" and have it bloodhound to it.
 */
public class SpeechBloodHound extends SpeechActivity {
    private final String TAG = "SPEECH_BLOODHOUND";
    private final String[] routeArray;
    private final String[] callsignArray;

    /**
     * Constructor.
     * Loads comparison arrays, then starts to analyze the speech.
     *
     * @param view  - Can't get views from within plugin, so needs to be passed in from receiver.
     * @param input - This is where the bloodhound will end. Should contain the name of a route/callsign
     * @param context - This is the plugin context needed so we can load resources.
     */
    public SpeechBloodHound(MapView view, String input, Context context) {
        super(view, context);
        Log.d(TAG, "============INSIDE SPEECH BLOOD HOUND==========");
        routeArray = context.getResources().getStringArray(R.array.route_array);
        callsignArray = context.getResources()
                .getStringArray(R.array.callsign_array);
        analyzeSpeech(input);
    }

    /**
     * Decides if a user is looking for a Route, callsign, or address.
     * If they say route, they're looking for a route and so on.
     * Separates the name of the item from the type.
     * Then it goes into the UID finder.
     * If not a callsign or route, treats input as an address.
     * Plots a point and bloodhounds to it.
     *
     * @param input - This contains the name of the route/callsign/etc
     */
    @Override
    void analyzeSpeech(final String input) {
        String mapGroupType;
        String temp = input.replace("call sign", "callsign");
        String[] destArr = temp.split(" ");
        StringBuilder destName = new StringBuilder();
        int indexR = -1, indexC = -1;
        for (int i = 0; i < destArr.length; i++) {
            for (String s : routeArray) {
                if (destArr[i].equalsIgnoreCase(s)) {
                    indexR = i;
                    break;
                }
            }
            for (String s : callsignArray) {
                if (destArr[i].equalsIgnoreCase(s)) {
                    indexC = i;
                    break;
                }
            }
        }
        if (indexR != -1) {
            for (int i = indexR + 1; i < destArr.length; i++) {
                destName.append(destArr[i]);
                destName.append(" ");
            }
            String routeName = destName.toString().trim();
            Log.d(TAG, "=========Route name ========" + routeName);
            mapGroupType = "Route";
            UIDFinder(routeName, mapGroupType);
        } else if (indexC != -1) {
            for (int i = indexC + 1; i < destArr.length; i++) {
                destName.append(destArr[i]);
                destName.append(" ");
            }
            String callsign = destName.toString().trim();
            Log.d(TAG, "=======callsign ===== " + callsign);
            mapGroupType = "Cursor on Target";
            UIDFinder(callsign, mapGroupType);
        } else {
            //If no callsign or route, plots a point at the address and BloodHounds to it.
            GeoBounds gb = getView().getBounds();
            final GeocodingTask gt = new GeocodingTask(getView().getContext(),
                    gb.getSouth(), gb.getWest(), gb.getNorth(),
                    gb.getEast(), false);
            gt.setOnResultListener(new GeocodingTask.ResultListener() {
                @Override
                public void onResult() {
                    if (gt.getPoint() != null) {
                        Log.d(TAG, "Inside GeocodingTask result listener");
                        PlacePointTool.MarkerCreator marker = new PlacePointTool.MarkerCreator(
                                gt.getPoint());
                        String randomUID = UUID.randomUUID().toString();
                        marker.setCallsign(input).setUid(randomUID)
                                .setType("b-m-p-w-GOTO");
                        marker.placePoint();
                        startActivity(randomUID);
                    } else {
                        Toast.makeText(getView().getContext(),
                                "Address not found, Try moving map",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
            gt.execute(input);
        }
    }

    /**
     * This puts the item's UID on an intent and fires it to the bloodhound tool
     * @param UID - the items UID
     */
    private void startActivity(String UID) {
        Intent returnIntent = new Intent();
        returnIntent.setAction(BloodHoundTool.BLOOD_HOUND);
        returnIntent.putExtra("uid", UID);
        AtakBroadcast.getInstance().sendBroadcast(returnIntent);
    }

    /**
     * unused in this class
     */
    @Override
    void startActivity() {
    }

    /**
     * This gets all the markers on the map and searches for the UID with the callsign/title
     *
     * @param title - the marker the user is looking for
     */
    private void UIDFinder(String title, String mapGroupType) {
        MapGroup cotGroup = getView().getRootGroup().findMapGroup(mapGroupType);
        if (mapGroupType.equalsIgnoreCase("route")) {
            MapItem item = cotGroup.deepFindItem("title", title);
            if (item != null) {
                Route route = (Route) item;
                startActivity(route.getMarker(0).getUID());
            } else
                Toast.makeText(getView().getContext(), "Route not found",
                        Toast.LENGTH_SHORT).show();

        } else {
            MapItem item = cotGroup.deepFindItem("callsign", title);
            if (item != null)
                startActivity(item.getUID());
            else
                Toast.makeText(getView().getContext(), "Callsign not found",
                        Toast.LENGTH_SHORT).show();
        }
    }

}
