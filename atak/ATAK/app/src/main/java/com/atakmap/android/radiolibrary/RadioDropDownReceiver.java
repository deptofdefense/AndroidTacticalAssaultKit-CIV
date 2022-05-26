
package com.atakmap.android.radiolibrary;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.NetworkManagerLite;
import com.atakmap.comms.NetworkManagerLite.NetworkDevice;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.StreamManagementUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RadioDropDownReceiver extends DropDownReceiver {

    public static final String TAG = "RadioDropDownReceiver";

    private final HarrisSA hsv;
    private final LayoutInflater inflater;
    private final LinearLayout listView;
    private final View _layout;
    private final Context context;
    private final Rover rover;
    private View softKdu = null;

    public RadioDropDownReceiver(MapView mapView) {
        super(mapView);
        context = mapView.getContext();
        inflater = LayoutInflater.from(getMapView().getContext());
        _layout = inflater.inflate(R.layout.radio_main, null);
        listView = _layout.findViewById(R.id.radioList);
        hsv = new HarrisSA(getMapView());
        registerControl(hsv.getView());

        if (exists("com.harris.rfcd.android.kdu")) {
            softKdu = inflater.inflate(R.layout.radio_item_harris_skdu, null);
            View b = softKdu.findViewById(R.id.harris_skdu);
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean success = false;
                    try {
                        success = launchThirdParty(
                                "com.harris.rfcd.android.kdu",
                                "com.harris.rfcd.android.kdu.KDUActivity");
                    } catch (Exception e) {
                        success = false;
                    }
                    if (!success)
                        Toast.makeText(
                                context,
                                R.string.radio_could_not_start_configuration,
                                Toast.LENGTH_SHORT).show();

                }
            });
            registerControl(softKdu);

        }

        rover = new Rover(getMapView());
        registerControl(rover.getView());

    }

    @Override
    public void onReceive(final Context context, Intent intent) {

        setRetain(true);

        if (isPortrait())
            showDropDown(_layout, FIVE_TWELFTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    FIVE_TWELFTHS_HEIGHT);
        else
            showDropDown(_layout, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT);
    }

    // provide a mapping between a registered radio control and the separator line 
    // so that if it is removed, the line will be removed as well.
    private final Map<View, View> controlLines = new HashMap<>();

    /**
     * Allows for external addition of control view for a radio.
     * @param view the control as an Android View.
     */
    public void registerControl(final View view) {
        if (view == null)
            return;

        // if the view is already being displayed, unregister it before continuing
        if (controlLines.containsKey(view))
            unregisterControl(view);

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                final View line = inflater.inflate(R.layout.radio_item_line,
                        null);
                listView.addView(line);
                listView.addView(view);
                controlLines.put(view, line);
            }
        });
    }

    /**
     * Allows for external remove of control view for a radio.
     * @param view the registered control as an Android View.
     */
    public void unregisterControl(final View view) {
        if (view == null)
            return;

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                final View line = controlLines.remove(view);
                if (line != null)
                    listView.removeView(line);
                listView.removeView(view);
            }
        });

    }

    public void close() {
        closeDropDown();
    }

    @Override
    public void disposeImpl() {
        unregisterControl(hsv.getView());
        hsv.dispose();

        unregisterControl(rover.getView());
        rover.dispose();

        unregisterControl(softKdu);
    }

    private boolean launchThirdParty(final String pkg, final String act) {

        if (!exists(pkg))
            return false;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, act));
        //intent.setAction("");
        //intent.putExtra("uri", uri);
        context.startActivity(intent);
        return true;

    }

    private boolean exists(final String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(pkg, PackageManager.GET_META_DATA);
            Log.d(TAG, "found " + pkg + " on the device");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "could not find " + pkg + " on the device");
            return false;
        }
    }

    /**
     * Broadcasts the video intent to watch a video.
     * @param external true will launch the external video player.
     */
    private void watchPDDLVideo(boolean external) {

        final String url = "udp://:49410";

        Log.d(TAG, "connecting to: " + url);
        ConnectionEntry ce = StreamManagementUtils
                .createConnectionEntryFromUrl("pddl", url);
        NetworkDevice nd = getPDDLDevice();
        if (nd != null)
            ce.setPreferredInterfaceAddress(
                    NetworkManagerLite.getAddress(nd.getInterface()));

        Toast.makeText(context, R.string.radio_initiating_connection,
                Toast.LENGTH_SHORT).show();

        Intent i = new Intent("com.atakmap.maps.video.DISPLAY");
        i.putExtra("CONNECTION_ENTRY", ce);
        if (external) {
            i.putExtra("standalone", true);
        }
        i.putExtra("cancelClose", true);
        AtakBroadcast.getInstance().sendBroadcast(i);

    }

    /**
     * Search the network map and see if there is a specific interface
     * with the type set to POCKET_DDL
     */
    private NetworkDevice getPDDLDevice() {
        List<NetworkDevice> devices = NetworkManagerLite.getNetworkDevices();
        for (NetworkDevice nd : devices) {
            if (nd.isSupported(NetworkDevice.Type.POCKET_DDL)) {
                Log.d(TAG, "found PocketDDL entry in network.map file: " + nd);
                if (nd.getInterface() != null) {
                    Log.d(TAG, "interface is up, returning: " + nd);
                    return nd;
                }
            }
        }

        return null;

    }

}
