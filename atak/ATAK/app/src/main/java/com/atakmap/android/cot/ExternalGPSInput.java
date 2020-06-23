
package com.atakmap.android.cot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.detail.PrecisionLocationHandler;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.SocketFactory;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import android.graphics.Color;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

/**
 * This thread will provide an entry point to receive and process externally supplied GPS data. The
 * format of this data is: The required input for this data is in CoT form and was based off the
 * early work to support the MPU.
 * <?xml version='1.0' standalone='yes'?> <event version="2.0"
 * uid="MPU 237" type="a-f-G-I-U-T" time="2013-12-18T17:27:35.94Z" start="2013-12-18T17:27:35.94Z"
 * stale="2013-12-18T17:28:05.94Z" how="m-g"> <point lat="27.885905" lon="-82.538630" hae="9" ce="0"
 * le=" 0"/> <detail><track course="0" speed="0.04" /></detail> </event>
 * Any further work to augment
 * this interface should be documented in this comment.
 */

public class ExternalGPSInput implements Runnable {

    public static final String TAG = "ExternalGPSInput";

    private final static int BUFFERSIZE = 1024 * 64;

    private static boolean _listening = false;
    private boolean closed = false;

    private final MapView _mapView;

    private final int _port;
    private DatagramSocket socket;

    private static ExternalGPSInput _instance;

    ExternalGPSInput(int port, MapView mapView) {
        _port = port;
        _mapView = mapView;
        _instance = this;
    }

    public static ExternalGPSInput getInstance() {
        return _instance;
    }

    @Override
    public void run() {
        Intent disableGps = new Intent();
        disableGps.setAction("com.atakmap.android.location.ENDGPS");
        AtakBroadcast.getInstance().sendBroadcast(disableGps);

        Log.w(TAG, "mocking UDP listening on port:" + _port);

        _listening = true;
        try {
            socket = SocketFactory.getSocketFactory().createDatagramSocket(_port);
            byte[] buffer = new byte[BUFFERSIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(10000);
            while (!socket.isClosed()) {
                try {
                    packet.setLength(packet.getData().length);
                    socket.receive(packet);

                    if (packet.getLength() > 0) {
                        String data = new String(buffer, 0, packet.getLength(),
                                FileSystemUtils.UTF8_CHARSET);

                        process(data);
                    } else {
                        Log.w(TAG, "Received an empty packet, ignoring.");
                    }
                } catch (SocketTimeoutException e) {
                    //Log.w(TAG, "UDP timed out.  Trying again....");
                    _mapView.getMapData().remove("mockLocationParentUID");
                    _mapView.getMapData().remove("mockLocationParentType");
                }
            }
        } catch (IOException e) {
            if (!closed)
                Log.e(TAG, "error: ", e);
        }
        _listening = false;
        Log.w(TAG, "mocking UDP no longer listening on port: " + _port);
    }

    public void process(String data) {
        CotEvent event;
        try {
            event = CotEvent.parse(data);
            process(event);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
    }

    public void process(CotEvent event) {

        if (event == null ||
                ((event.getCotPoint().getLat() == 0.0 &&
                        event.getCotPoint().getLon() == 0.0) ||
                        event.getCotPoint().getLat() < -90 ||
                        event.getCotPoint().getLat() > 90 ||
                        event.getCotPoint().getLon() < -180 ||
                        event.getCotPoint().getLon() > 180)) {
            Log.w(TAG,
                    "External GPS source provided invalid position: "
                            + ((event != null) ? event.getCotPoint().toString()
                                    : "[null]"));
            _mapView.getMapData().remove(
                    "mockLocationParentUID");
            _mapView.getMapData().remove(
                    "mockLocationParentType");
        } else {
            if (event.getDetail() != null) {
                CotDetail track = event.getDetail()
                        .getFirstChildByName(0, "track");
                if (track != null) {
                    double speedD = Double.NaN;
                    String speed = track.getAttribute("speed");
                    if (speed != null)
                        speedD = Double.parseDouble(speed);
                    _mapView.getMapData().putDouble(
                            "mockLocationSpeed", speedD);

                    double bearingD = Double.NaN;
                    String bearing = track
                            .getAttribute("course");
                    if (bearing != null)
                        bearingD = Double.parseDouble(bearing);
                    _mapView.getMapData()
                            .putDouble("mockLocationBearing",
                                    bearingD);
                }
                CotDetail remarks = event.getDetail()
                        .getFirstChildByName(0,
                                "remarks");

                String source = "External";

                if (remarks != null) {
                    String sourceS = remarks.getInnerText();
                    if (sourceS != null)
                        source = sourceS;
                }

                CotDetail remarksColor = event.getDetail()
                        .getFirstChildByName(0,
                                "remarksColor");
                if (remarksColor != null) {
                    try {
                        String s = remarksColor.getInnerText();
                        if (s != null) {
                            // just attempt to parse the color which if not parsable will trigger
                            // an exception.  The variable c is not used.
                            int c = Color.parseColor(s);
                            _mapView.getMapData()
                                    .putString("mockLocationSourceColor", s);
                        }
                    } catch (Exception e) {
                        Log.d(TAG,
                                "error occurred parsing remarksColor",
                                e);
                    }
                }

                _mapView.getMapData().putString(
                        "mockLocationSource", source);

                CotDetail extendedGPSDetails = event
                        .getDetail().getFirstChildByName(0,
                                "extendedGpsDetails");
                if (extendedGPSDetails != null) {
                    try {
                        String s = extendedGPSDetails.getAttribute("time");
                        if (s != null) {
                            _mapView.getMapData()
                                    .putLong("mockGPSTime", Long.parseLong(s));
                        }
                    } catch (Exception e) {
                        Log.d(TAG,
                                "error occurred parsing gpsTimeMillis",
                                e);
                    }
                    try {
                        String s = extendedGPSDetails
                                .getAttribute("fixQuality");
                        if (s != null) {
                            _mapView.getMapData()
                                    .putInt("mockFixQuality",
                                            Integer.parseInt(s));
                        }
                    } catch (Exception e) {
                        Log.d(TAG,
                                "error occurred parsing fixQuality",
                                e);
                    }
                    try {
                        String s = extendedGPSDetails
                                .getAttribute("numSatellites");
                        if (s != null) {
                            _mapView.getMapData()
                                    .putInt("mockNumSatellites",
                                            Integer.parseInt(s));
                        }
                    } catch (Exception e) {
                        Log.d(TAG,
                                "error occurred parsing numSatellites",
                                e);
                    }
                }
            }

            MapItem oldMarker = _mapView.getRootGroup().deepFindItem("uid",
                    event.getUID());
            if (oldMarker != null)
                oldMarker.removeFromGroup();
            _mapView.getMapData()
                    .putString("mockLocationParentUID",
                            event.getUID());
            _mapView.getMapData().putString(
                    "mockLocationParentType",
                    event.getType());
            _mapView.getMapData().putBoolean(
                    "mockLocationAvailable", true);
            _mapView.getMapData().putString(
                    "locationSourcePrefix", "mock");

            GeoPointMetaData gpm = PrecisionLocationHandler
                    .getPrecisionLocation(event);
            _mapView.getMapData().putParcelable("mockLocation", gpm.get());
            _mapView.getMapData().putString("mockLocationSrc",
                    gpm.getGeopointSource());
            _mapView.getMapData().putString("mockLocationAltSrc",
                    gpm.getAltitudeSource());

            // XXY - need to figure out how to save other information describing the GeoPointSource //
            // 04 MARCH 2019 - for now just just augment the map bundle - see the LocationMapComponent
            // corresponding modfication.

            // All location time is used for to determine when the last GPS pump occurred.
            // should be based on SystemClock which is not prone to error by setting the
            // System Date/Time.
            //
            _mapView.getMapData().putLong("mockLocationTime",
                    SystemClock.elapsedRealtime());
            SharedPreferences locationPrefs = PreferenceManager
                    .getDefaultSharedPreferences(_mapView.getContext());
            if (locationPrefs.getBoolean(
                    "locationUseWRCallsign", false)) {
                String callsign = CotUtils.getCallsign(event);
                _mapView.setDeviceCallsign((callsign == null) ? event
                        .getUID()
                        : callsign);
                // mark the mock callsign as valid
                _mapView.getMapData().putBoolean(
                        "mockLocationCallsignValid", true);
            } else {
                _mapView.setDeviceCallsign(locationPrefs
                        .getString(
                                "locationCallsign",
                                LocationMapComponent
                                        .callsignGen(_mapView.getContext())));
                _mapView.getMapData()
                        .putBoolean(
                                "mockLocationCallsignValid",
                                false);
            }
            Intent gpsReceived = new Intent();
            gpsReceived
                    .setAction("com.atakmap.android.map.WR_GPS_RECEIVED");
            AtakBroadcast.getInstance().sendBroadcast(
                    gpsReceived);
        }
    }

    void interruptSocket() {
        closed = true;
        if (socket != null)
            socket.close();

        _mapView.getMapData().remove("mockLocationParentUID");
        _mapView.getMapData().remove("mockLocationParentType");
        Log.w(TAG, "interrupted thread for listening on port: " + _port);
    }

}
