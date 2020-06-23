
package com.atakmap.android.lrf;

import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.lrf.reader.LRFReader;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.UUID;

/**
 * Generic Callback implementation for a laser range finder.
 */
public class GenericLRFCallback implements LRFReader.Callback {

    private static final byte[] address = new byte[] {
            (byte) 127, (byte) 0, (byte) 0, (byte) 1
    };
    private static final int port = 17211;
    private static final int ttl = 1;
    private final String uid;

    public GenericLRFCallback() {
        uid = UUID.randomUUID().toString();
    }

    @Override
    public void onCompassError() {
        BluetoothCotManager
                .publish("1," + uid + "," + new CoordinatedTime() + ","
                        + "COMPASS_ERROR", address, port, ttl);
    }

    @Override
    public void onRangeError() {
        BluetoothCotManager
                .publish("1," + uid + "," + new CoordinatedTime() + ","
                        + "RANGE_ERROR", address, port, ttl);
    }

    @Override
    public void onComputerError() {
        BluetoothCotManager
                .publish("1," + uid + "," + new CoordinatedTime() + ","
                        + "MAINBOARD_ERROR", address, port, ttl);
    }

    @Override
    public void onRangeFinderInfo(final double azimuth, final double elRad,
            final double meters) {

        BluetoothCotManager.publish(
                "1," + uid + "," + new CoordinatedTime() + "," + meters
                        + "," + azimuth + "," + elRad,
                address,
                port,
                ttl);
    }
}
