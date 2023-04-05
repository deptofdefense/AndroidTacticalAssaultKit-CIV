
package com.atakmap.android.location;

import android.os.SystemClock;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Management tool for handling the dead reckoning implementations.
 */
public class DeadReckoningManager implements
        PointMapItem.OnPointChangedListener {

    final static private String TAG = "DeadReckoningManager";
    private final static double CUTOFF_SPEED = 1.0d;

    private final Timer timer;
    private DeadReckoner dr;
    private final PointMapItem self;

    private static DeadReckoningManager _instance;

    /**
     * Interface for a dead reckoner implementation.
     */
    public interface DeadReckoner {

        /**
         * Allows for cleanup of the DeadRecker.
         */
        void dispose();

        /**
         * Provide the algorithm the next sample point.
         * Time for the dead reckoning algorithm should be in SystemClock.elapsedRealtime()
         * @param item the geospatial point that has been sampled from a sensor.
         * @param time the time in elapsedRealtime that described the gp.
         */
        void nextSampled(PointMapItem item, long time);

        /**
         * The position calculated must be marked with GeoPointSource.ESTIMATED
         * @param item the item to set with the next estimated point.
         * @param time the time in SystemClock.elaspedRealtime to estimate.
         */
        void calcEstimate(PointMapItem item, long time);
    }

    private static final DeadReckoner simpleDeadReckoner = new DeadReckoner() {

        final static private String TAG = "SimpleDeadReckoner";

        // point and last point
        GeoPoint last;
        GeoPoint curr;

        // time and last time
        long lastTime;
        long time;

        // distance covered
        double lastdist;
        double dist;

        // current direction and last direction
        double lastdir;
        double dir;

        // elapsed time
        double etime;

        double lastspeed;
        double speed;

        @Override
        public void nextSampled(final PointMapItem item, final long time) {
            final GeoPointMetaData gp = item.getGeoPointMetaData();
            if (gp == null || gp.get() == curr)
                return;
            String src = gp.getGeopointSource();
            if (gp.get() == curr ||
                    src.equals(GeoPointMetaData.ESTIMATED) ||
                    src.equals(GeoPointMetaData.CALCULATED) ||
                    src.equals(GeoPointMetaData.UNKNOWN))
                return;

            //Log.d(TAG, "source: " + src);

            lastTime = this.time;
            this.time = time;

            last = curr;
            curr = gp.get();

            // instead of synchronizing on the last geopoint which could be set 
            // to null when the driving stops, just use local assignment.

            final GeoPoint lastLocal = last;
            if (lastLocal != null) {
                lastdist = dist;
                dist = lastLocal.distanceTo(gp.get());

                lastdir = dir;
                dir = lastLocal.bearingTo(gp.get());

                lastspeed = speed;
                speed = item.getMetaDouble("Speed", 0.0);

                etime = time - lastTime;
                //Log.d(TAG, "dist: " + dist +  " over time: " + time);
            }
        }

        @Override
        public void calcEstimate(PointMapItem item, long currTime) {
            //Log.d(TAG, "driving: " + _locationMarker.getMetaBoolean("driving", false));

            // GPS reporting is 1HZ, do not reckon for longer than 1 missed GPS report
            if (currTime - time > 2000)
                return;

            if (item.getMetaBoolean("driving", false)) {
                if (last != null) {

                    // estimation for next location 
                    if (speed > CUTOFF_SPEED) {

                        double speedCorrection = 1.0;
                        // greater than 1.0 means acceleration, 
                        // less than 1.0 means deceleration, treat it linearly 
                        // capped at 0.75 or 1.25.

                        if (lastspeed > 0) {
                            speedCorrection = (speed / lastspeed);
                            if (speedCorrection < 0.75)
                                speedCorrection = 0.75;
                            else if (speedCorrection > 1.25)
                                speedCorrection = 1.25;
                            //Log.d(TAG, "speed ratio: " + speedCorrection);
                        }

                        final double mult = (currTime - time) / etime;
                        //Log.d(TAG, "dist: " + dist + " mult: " + mult);

                        GeoPoint np = GeoCalculations.pointAtDistance(curr, dir,
                                dist * mult * speedCorrection, 0.0);

                        np = new GeoPoint(np.getLatitude(), np.getLongitude(),
                                curr.getAltitude(), curr.getCE(), curr.getLE());

                        //Log.d(TAG, "calculating estimated position: " + np
                        //        + " moved: " + (dist * mult)  + " " + speed);
                        item.setPoint(GeoPointMetaData.wrap(np,
                                GeoPointMetaData.ESTIMATED,
                                GeoPointMetaData.CALCULATED));
                    }

                    final double estdir = (dir - lastdir) / etime;
                    // estimation for next track
                    if (item instanceof Marker) {
                        ((Marker) item).setTrack(
                                ((Marker) item).getTrackHeading() + estdir,
                                ((Marker) item).getTrackSpeed());
                        //Log.d(TAG, "direction change: " + estdir);

                    }
                }
            } else {
                last = null;
                curr = null;
            }

        }

        @Override
        public void dispose() {

        }

    };

    private DeadReckoningManager() {
        Log.d(TAG, "dead reckoning manager instantiated");

        MapView mapView = MapView.getMapView();
        self = mapView.getSelfMarker();
        timer = new Timer("DeadReckoning");
        dr = simpleDeadReckoner;
        self.addOnPointChangedListener(this);
        timer.schedule(refreshTask, 0, 125);
    }

    public synchronized static DeadReckoningManager getInstance() {
        if (_instance == null) {
            _instance = new DeadReckoningManager();
        }
        return _instance;
    }

    /**
     * Register an externally supplied implementation of a dead reckoner.
     * @param new_dr the new dead reckoner to register for use instead of the default.
     */
    public synchronized void registerDeadReckoner(final DeadReckoner new_dr) {
        final DeadReckoner reckon = dr;

        // swap and then dispose
        dr = new_dr;
        if (reckon != null)
            reckon.dispose();
    }

    /**
     * Unregister an externally supplied implementation of a dead reckoner.
     * @param new_dr the dead reckoner to unregister.
     */
    public synchronized void unregisterDeadReckoner(final DeadReckoner new_dr) {
        final DeadReckoner reckon = dr;

        // swap and then dispose
        dr = simpleDeadReckoner;
        if (reckon == new_dr)
            reckon.dispose();
    }

    public void dispose() {
        synchronized (this) {
            if (dr != null)
                dr.dispose();
            dr = null;
        }
        if (self != null)
            self.removeOnPointChangedListener(this);
        if (timer != null)
            timer.cancel();
    }

    final TimerTask refreshTask = new TimerTask() {
        @Override
        public void run() {
            DeadReckoner reckon = dr;
            if (reckon != null && self.getGroup() != null) {
                reckon.calcEstimate(self, SystemClock.elapsedRealtime());
            }
        }
    };

    @Override
    public void onPointChanged(PointMapItem item) {
        final GeoPointMetaData gp = item.getGeoPointMetaData();

        String src = gp.getGeopointSource();
        if (src.equals(GeoPointMetaData.ESTIMATED) ||
                src.equals(GeoPointMetaData.CALCULATED) ||
                src.equals(GeoPointMetaData.UNKNOWN))
            return;

        DeadReckoner reckon = dr;
        if (reckon != null && item.getGroup() != null) {
            reckon.nextSampled(item, SystemClock.elapsedRealtime());
        }
    }

}
