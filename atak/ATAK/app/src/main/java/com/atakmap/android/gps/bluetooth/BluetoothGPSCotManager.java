
package com.atakmap.android.gps.bluetooth;

import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.maps.MapView;

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

    private static final byte[] address = new byte[] {
            (byte) 127, (byte) 0, (byte) 0, (byte) 1
    };
    private static final int port = 4349;
    private static final int ttl = 1;

    public BluetoothGPSCotManager(final BluetoothGPSNMEAReader reader,
            final MapView mapView,
            final String cotUID, final String name) {
        super(reader, mapView, cotUID, name);
        reader.register(this);
    }

    public void publish(String xml) {
        publish(xml, address, port, ttl);
    }
}
