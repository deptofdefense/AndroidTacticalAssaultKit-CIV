
package com.atakmap.android.lrf;

import com.atakmap.android.lrf.reader.LRFReader;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.UUID;

/**
 * Generic Callback implementation for a laser range finder.
 */
public class GenericLRFCallback implements LRFReader.Callback {

    private final String uid;

    public GenericLRFCallback() {
        uid = UUID.randomUUID().toString();
    }

    @Override
    public void onCompassError() {
        LocalRangeFinderInput.getInstance()
                .process("1," + uid + "," + new CoordinatedTime() + ","
                        + "COMPASS_ERROR");
    }

    @Override
    public void onRangeError() {
        LocalRangeFinderInput.getInstance().process(
                "1," + uid + "," + new CoordinatedTime() + ","
                        + "RANGE_ERROR");
    }

    @Override
    public void onComputerError() {
        LocalRangeFinderInput.getInstance().process(
                "1," + uid + "," + new CoordinatedTime() + ","
                        + "MAINBOARD_ERROR");
    }

    @Override
    public void onRangeFinderInfo(final double azimuth, final double elRad,
            final double meters) {

        LocalRangeFinderInput.getInstance().process(
                "1," + uid + "," + new CoordinatedTime() + "," + meters
                        + "," + azimuth + "," + elRad);
    }
}
