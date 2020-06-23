
package com.atakmap.android.bloodhound;

import android.os.SystemClock;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Arrays;

public class SimpleSpeedBearingComputer {

    private static final String TAG = "SimpleSpeedBearingComputer";

    private final double[] vlist;
    private int idx = 0;
    private boolean reset;

    private GeoPoint lastPoint;
    private long lastTime;
    private double bearing;

    public SimpleSpeedBearingComputer(final int size) {
        vlist = new double[size];
    }

    private void queue(double v) {
        if (idx == 0)
            idx = vlist.length;
        --idx;
        vlist[idx] = v;
    }

    public void add(GeoPoint currPoint) {
        reset = false;
        long currTime = SystemClock.elapsedRealtime();

        if (lastPoint != null && currPoint != null) {
            double dist = lastPoint.distanceTo(currPoint);
            bearing = lastPoint.bearingTo(currPoint);
            double v = (Math.abs(dist) / ((currTime - lastTime) / 1000d));
            Log.d(TAG, "time difference: "
                    + ((currTime - lastTime) / 1000d));
            Log.d(TAG, "dist difference: " + dist);
            queue(v);
        }

        lastPoint = currPoint;
        lastTime = currTime;
    }

    public void reset() {
        if (reset)
            return;

        lastPoint = null;
        bearing = Double.NaN;

        Arrays.fill(vlist, Double.NaN);

        reset = true;
    }

    public double getBearing() {
        return bearing;
    }

    public double getAverageSpeed() {

        if (reset)
            return 0.0;

        double avgV = 0.0;
        int count = 0;

        for (double aVlist : vlist) {
            if (!Double.isNaN(aVlist)) {
                avgV = aVlist + avgV;
                count++;
            }
        }
        if (count == 0)
            return 0.0;
        else
            return avgV / count;
    }
}
