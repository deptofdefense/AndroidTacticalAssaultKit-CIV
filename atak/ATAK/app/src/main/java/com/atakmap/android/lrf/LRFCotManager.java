
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

    public LRFCotManager(final LRFReader reader, final MapView mapView,
            final String cotUID, final String name) {
        super(reader, mapView, cotUID, name);
        reader.setCallback(this);
    }

    @Override
    public void onCompassError() {
        LocalRangeFinderInput.getInstance()
                .process("1," + cotUID + "," + new CoordinatedTime() + ","
                        + "COMPASS_ERROR");
    }

    @Override
    public void onRangeError() {
        LocalRangeFinderInput.getInstance()
                .process("1," + cotUID + "," + new CoordinatedTime() + ","
                        + "RANGE_ERROR");
    }

    @Override
    public void onComputerError() {
        LocalRangeFinderInput.getInstance()
                .process("1," + cotUID + "," + new CoordinatedTime() + ","
                        + "MAINBOARD_ERROR");
    }

    @Override
    public void onRangeFinderInfo(final double azimuth, final double elRad,
            final double meters) {

        LocalRangeFinderInput.getInstance()
                .process("1," + cotUID + "," + new CoordinatedTime() + ","
                        + meters
                        + "," + azimuth + "," + elRad);
    }
}
