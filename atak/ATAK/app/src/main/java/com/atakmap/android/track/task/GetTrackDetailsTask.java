
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Pair;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.android.routes.elevation.service.AnalyticsElevationService;
import com.atakmap.android.routes.elevation.service.RouteElevationService;
import com.atakmap.android.track.TrackDetails;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.crumb.CrumbPoint;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.List;

/**
 * Simple background task to query track points from DB and perform some analysis
 *
 * 
 */
public class GetTrackDetailsTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "GetTrackDetailsTask";

    private final Callback callback;
    private TrackDetails track;
    private int trackDbId;
    private ProgressDialog _progressDialog;
    private final MapView _mapView;
    private final Context _context;
    private final boolean bInterpolateAltitudes;

    public GetTrackDetailsTask(MapView mapView, TrackDetails details,
            int trackDbId, Callback cb,
            boolean bInterpolateAltitudes) {
        this.track = details;
        this.trackDbId = trackDbId;
        this.callback = cb;
        _mapView = mapView;
        _context = mapView.getContext();
        this.bInterpolateAltitudes = bInterpolateAltitudes;
    }

    public GetTrackDetailsTask(MapView mapView, TrackDetails details,
            Callback cb, boolean bInterpolateAltitudes) {
        this(mapView, details, details.getTrackDbId(), cb,
                bInterpolateAltitudes);
    }

    @Override
    protected void onPreExecute() {
        // Before running code in background/worker thread
        _progressDialog = new ProgressDialog(_context);
        _progressDialog.setTitle(_context.getString(R.string.analyzing));
        _progressDialog.setIcon(R.drawable.ic_track_search);
        _progressDialog.setMessage(_context
                .getString(R.string.processing_user_track));
        _progressDialog.setIndeterminate(true);
        _progressDialog.setCancelable(true);
        //_progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Thread.currentThread().setName("GetTrackPointsTask");

        Log.d(TAG, "Executing GetTrackPointsTask: " + (track == null
                ? "Current"
                : track.getTrackDbId()));

        //TODO threading issues with DB access? this done from an async task
        CrumbDatabase db = CrumbDatabase.instance();
        if (trackDbId == -1) {
            if (track == null) {
                // If no track specified, query the current self track
                trackDbId = db.getCurrentSegmentId(MapView.getDeviceUid(),
                        CrumbDatabase.SEG_COLUMN_TIMESTAMP);
            } else
                trackDbId = track.getTrackDbId();
        }

        List<CrumbPoint> crumbs = db.getCrumbPoints(trackDbId);
        if (FileSystemUtils.isEmpty(crumbs)) {
            Log.w(TAG, "Cannot create tracks w/out crumbs for id: "
                    + trackDbId);
            return false;
        }

        TrackPolyline dbPolyline = db.getTrack(trackDbId, crumbs);
        if (dbPolyline == null) {
            Log.w(TAG,
                    "Unable to load Track points: " + trackDbId);
            return false;
        }

        if (track == null)
            track = new TrackDetails(_mapView, dbPolyline);

        //TODO are we loosing data fidelity with this code reuse...?
        if (crumbs.size() == 1) {
            CrumbPoint c = crumbs.get(0);
            //handle case of single point
            Log.d(TAG, "Track has a single point");
            track.setPolyline(dbPolyline);
            track.setMaxSlope(0);
            if (c.gp.isAltitudeValid()) {
                track.setMinAlt(c.gpm);
                track.setMaxAlt(c.gpm);
            } else {
                track.setMinAlt(null);
                track.setMaxAlt(null);
            }

            if (c.speed < 0 || Double.isNaN(c.speed)
                    || Double.compare(Double.MAX_VALUE, c.speed) == 0) {
                track.setMaxSpeed(-1, null);
                track.setAvgSpeed(-1);
            } else {
                track.setMaxSpeed(c.speed, c.gpm);
                track.setAvgSpeed(c.speed);
            }

            track.setGain(0);
            track.setLoss(0);
        } else {
            //analyze route
            Log.d(TAG,
                    "Analyzing track with crumb count: " + crumbs.size());
            RouteData result = RouteElevationService.expandRoute(dbPolyline
                    .getMetaDataPoints(),
                    RouteElevationService
                            .computeRelativeFrequency(dbPolyline
                                    .getTotalDistance()),
                    bInterpolateAltitudes);
            if (result == null) {
                Log.w(TAG,
                        "Unable to analyze Track points: " + trackDbId);
                return false;
            }

            Pair<GeoPointMetaData, GeoPointMetaData> minmax = AnalyticsElevationService
                    .findMinMax(result.getGeoPoints());
            result.setMinAlt(minmax.first);
            result.setMaxAlt(minmax.second);
            track.setPolyline(dbPolyline);
            track.setMaxSlope(AnalyticsElevationService
                    .findRouteMaximumSlope(result.getDistances(),
                            result.getGeoPoints()));
            track.setMinAlt(result.getMinAlt());
            track.setMaxAlt(result.getMaxAlt());
            track.setGain(AnalyticsElevationService
                    .findRouteTotalElevation(result, true, -1));
            track.setLoss(AnalyticsElevationService
                    .findRouteTotalElevation(result, false, -1));

            //route analysis code does not currently account for speed, so compute that now
            Pair<Pair<Double, GeoPointMetaData>, Double> maxavgspeed = findMaxAvgSpeed(
                    crumbs);
            if (maxavgspeed != null) {
                track.setMaxSpeed(maxavgspeed.first.first,
                        maxavgspeed.first.second);
                track.setAvgSpeed(maxavgspeed.second);
            }
        }

        return true;
    }

    private Pair<Pair<Double, GeoPointMetaData>, Double> findMaxAvgSpeed(
            List<CrumbPoint> crumbs) {
        if (FileSystemUtils.isEmpty(crumbs)) {
            Log.w(TAG, "Cannot find speed values without crumbs");
            return null;
        }

        GeoPointMetaData maxSpeedLocation = null;
        double maxSpeed = 0;
        double speedCumulative = 0; //additive of all crumbs' speed
        int speedCount = 0; //# crumbs with speed information
        for (CrumbPoint c : crumbs) {
            if (c.speed < 0 ||
                    Double.compare(c.speed, CrumbDatabase.VALUE_UNKNOWN) == 0 ||
                    Double.isNaN(c.speed) || Double.MAX_VALUE == c.speed) {

                //Log.d(TAG, "skipping speed=" + crumbSpeed);
                continue;
            }

            speedCount++;
            speedCumulative += c.speed;
            if (c.speed > maxSpeed) {
                maxSpeed = c.speed;
                maxSpeedLocation = c.gpm;
            }
        }

        if (speedCount < 1) {
            Log.w(TAG, "No crumbs had speed information");
            return null;
        }

        return new Pair<>(
                new Pair<>(maxSpeed, maxSpeedLocation),
                (speedCumulative / speedCount));
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (!result) {
            Toast.makeText(_context, R.string.no_logged_points,
                    Toast.LENGTH_LONG).show();
            if (callback != null)
                callback.onComplete(null);
        } else {
            //callback with results
            if (callback != null)
                callback.onComplete(track);
        }

        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }
    }

    public interface Callback {
        void onComplete(TrackDetails track);
    }
}
