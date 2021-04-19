
package com.atakmap.android.elev.graphics;

import android.os.CancellationSignal;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.MathUtils;

class HeatMapParams {
    final GeoPoint upperLeft = GeoPoint.createMutable();
    final GeoPoint upperRight = GeoPoint.createMutable();
    final GeoPoint lowerRight = GeoPoint.createMutable();
    final GeoPoint lowerLeft = GeoPoint.createMutable();

    int xSampleResolution;
    int ySampleResolution;

    byte[] rgbaData;

    float[] elevationData;
    float minElev;
    float maxElev;
    int numSamples;

    int drawVersion;
    boolean needsRefresh;
    boolean quick;

    boolean valid;

    int[] hsvLut;
    float lutAlpha;
    float lutSaturation;
    float lutValue;

    final CancellationSignal querySignal = new CancellationSignal();

    double getMaxLatitude() {
        return MathUtils.max(
                upperLeft.getLatitude(),
                upperRight.getLatitude(),
                lowerRight.getLatitude(),
                lowerLeft.getLatitude());
    }

    double getMinLatitude() {
        return MathUtils.min(
                upperLeft.getLatitude(),
                upperRight.getLatitude(),
                lowerRight.getLatitude(),
                lowerLeft.getLatitude());
    }

    double getMaxLongitude() {
        return MathUtils.max(
                upperLeft.getLongitude(),
                upperRight.getLongitude(),
                lowerRight.getLongitude(),
                lowerLeft.getLongitude());
    }

    double getMinLongitude() {
        return MathUtils.min(
                upperLeft.getLongitude(),
                upperRight.getLongitude(),
                lowerRight.getLongitude(),
                lowerLeft.getLongitude());
    }
}
