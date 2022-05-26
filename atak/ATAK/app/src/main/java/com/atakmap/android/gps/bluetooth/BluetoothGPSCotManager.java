
package com.atakmap.android.gps.bluetooth;

import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.cot.ExternalGPSInput;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

/**
 * Manages the actual Bluetooth channel and socket reading for NMEA string. Also contains glue code
 * for providing the GPS as an External GPS input (via CoT)
 *  Uses NMEA RMC: location, time, course, speed
 *  Uses NMEA GGA: altitude
 * 
 * 
 */
class BluetoothGPSCotManager extends BluetoothCotManager {

    private static final String TAG = "BluetoothGPSCotManager";

    public BluetoothGPSCotManager(final BluetoothGPSNMEAReader reader,
            final MapView mapView,
            final String cotUID, final String name) {
        super(reader, mapView, cotUID, name);
        reader.register(this);
    }

    public void publish(String xml) {
        try {
            ExternalGPSInput.getInstance().process(CotEvent.parse(xml));
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
    }
}
