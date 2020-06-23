
package com.atakmap.android.lrf;

import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.lrf.reader.LRFReader;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.time.CoordinatedTime;

/**
 * Manages the actual Bluetooth channel and socket reading for LRF packets. Also contains glue code
 * for displaying icons on the map.
 *
 */
public class LRFCotManager extends BluetoothCotManager implements
        LRFReader.Callback {

    private static final byte[] address = new byte[] {
            (byte) 127, (byte) 0, (byte) 0, (byte) 1
    };
    private static final int port = 17211;
    private static final int ttl = 1;

    public LRFCotManager(final LRFReader reader, final MapView mapView,
            final String cotUID, final String name) {
        super(reader, mapView, cotUID, name);
        reader.setCallback(this);
    }

    @Override
    public void onCompassError() {
        publish("1," + cotUID + "," + new CoordinatedTime() + ","
                + "COMPASS_ERROR", address, port, ttl);
    }

    @Override
    public void onRangeError() {
        publish("1," + cotUID + "," + new CoordinatedTime() + ","
                + "RANGE_ERROR", address, port, ttl);
    }

    @Override
    public void onComputerError() {
        publish("1," + cotUID + "," + new CoordinatedTime() + ","
                + "MAINBOARD_ERROR", address, port, ttl);
    }

    @Override
    public void onRangeFinderInfo(final double azimuth, final double elRad,
            final double meters) {

        publish("1," + cotUID + "," + new CoordinatedTime() + "," + meters
                + "," + azimuth + "," + elRad,
                address,
                port,
                ttl);
    }
}
