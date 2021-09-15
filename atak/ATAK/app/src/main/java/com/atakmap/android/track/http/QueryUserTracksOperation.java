
package com.atakmap.android.track.http;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.routes.RouteKmlIO;
import com.atakmap.android.track.BreadcrumbReceiver;
import com.atakmap.app.R;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.MultiTrack;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Track;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * REST Operation to GET user tracks from a server
 * And insert into Crumbs DB
 * 
 * 
 */
public final class QueryUserTracksOperation extends HTTPOperation {
    private static final String TAG = "QueryUserTracksOperation";

    public static final String PARAM_QUERY = QueryUserTracksOperation.class
            .getName()
            + ".PARAM_QUERY";
    public static final String PARAM_TRACKDBIDS = QueryUserTracksOperation.class
            .getName()
            + ".PARAM_TRACKDBIDS";
    public static final String PARAM_TRACKNOTFOUND = QueryUserTracksOperation.class
            .getName()
            + ".PARAM_TRACKNOTFOUND";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        QueryUserTracksRequest queryRequest = (QueryUserTracksRequest) request
                .getParcelable(QueryUserTracksOperation.PARAM_QUERY);
        if (queryRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!queryRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return queryUserTracks(context, queryRequest);
    }

    private static Bundle queryUserTracks(Context context,
            QueryUserTracksRequest queryRequest)
            throws ConnectionException,
            DataException {

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        int statusCode = NetworkOperation.STATUSCODE_UNKNOWN;
        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(queryRequest.getBaseUrl());
            NotificationManager notifyManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            Notification.Builder builder;
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context);
            } else {
                builder = new Notification.Builder(context,
                        "com.atakmap.app.def");
            }

            builder.setContentTitle(
                    context.getString(R.string.user_track_download))
                    .setContentText(
                            context.getString(R.string.downloading_track)
                                    + queryRequest.getCallsign())
                    .setSmallIcon(R.drawable.ongoing_download);

            //TODO if we can get TAK Server to provide a "Content-Length" header, then we can display progress during download...
            // DownloadProgressTracker progressTracker = null;

            int multiTrackThresholdMin = 10;
            try {
                multiTrackThresholdMin = Integer.parseInt(prefs.getString(
                        "bread_track_timegap_threshold", String.valueOf(10)));
            } catch (NumberFormatException nfe) {
                Log.w(TAG, "bread_track_timegap_threshold error", nfe);
            }

            String queryUrl = client.getUrl("/ExportMissionKML?startTime=")
                    + KMLUtil.KMLDateTimeFormatterMillis.get().format(
                            queryRequest.getStartTime())
                    +
                    "&endTime="
                    + KMLUtil.KMLDateTimeFormatterMillis.get().format(
                            queryRequest.getEndTime())
                    +
                    "&uid=" + queryRequest.getUid() +
                    "&multiTrackThreshold="
                    + multiTrackThresholdMin +
                    "&extendedData=true&format=kmz&optimizeExport=true";

            long startTime = SystemClock.elapsedRealtime();

            //Note tracks packages are KMZ/Zipped, no need for GZip
            HttpGet httpget = new HttpGet(queryUrl);
            TakHttpResponse response = client.execute(httpget);
            statusCode = response.getStatusCode();

            HttpEntity resEntity = response.getEntity();
            response.verifyOk();

            // open up for writing
            String tempFileName = UUID.randomUUID().toString() + ".kmz";
            File temp = new File(
                    FileSystemUtils.getItem(FileSystemUtils.TMP_DIRECTORY),
                    tempFileName);
            Log.d(TAG,
                    "processing response into file: " + temp.getAbsolutePath());

            try (OutputStream fos = IOProviderFactory.getOutputStream(temp);
                    InputStream in = resEntity.getContent()) {
                // stream in content, keep user notified on progress
                builder.setProgress(100, 1, false);
                if (notifyManager != null) {
                    notifyManager.notify(queryRequest.getNotificationId(),
                            builder.build());
                }

                FileSystemUtils.copy(in, fos);
            }

            // Now verify we got download correctly
            if (!FileSystemUtils.isFile(temp)) {
                FileSystemUtils.deleteFile(temp);
                throw new ConnectionException("Failed to download data");
            }

            long downloadSize = IOProviderFactory.length(temp);
            Log.d(TAG, "Parsing downloaded file: " + temp.getAbsolutePath());

            // update notification
            builder.setProgress(100, 60, false);
            builder.setContentText(context
                    .getString(R.string.processing_tracks)
                    + queryRequest.getCallsign());
            if (notifyManager != null) {
                notifyManager.notify(queryRequest.getNotificationId(),
                        builder.build());
            }

            //unzip and parse
            Kml kml = RouteKmlIO.read(temp, context);
            if (kml == null) {
                FileSystemUtils.deleteFile(temp);
                throw new ConnectionException("Failed to parse user track");
            }

            //clean up HTTP connection and delete temp file
            FileSystemUtils.deleteFile(temp);

            builder.setProgress(100, 80, false);
            builder.setContentText(context.getString(R.string.storing_tracks)
                    + queryRequest.getCallsign());
            if (notifyManager != null) {
                notifyManager.notify(queryRequest.getNotificationId(),
                        builder.build());
            }

            // Pull first KML Placemark
            Log.d(TAG, "Processing KML track");
            TrackHandler trackHandler = new TrackHandler();
            KMLUtil.deepFeatures(kml, trackHandler, Placemark.class);
            if (FileSystemUtils.isEmpty(trackHandler._tracks)) {

                //if no match, notify user rather than error out
                Bundle output = new Bundle();
                output.putParcelable(PARAM_QUERY, queryRequest);
                output.putBoolean(PARAM_TRACKNOTFOUND, true);
                output.putInt(NetworkOperation.PARAM_STATUSCODE, statusCode);
                return output;
            }

            List<Integer> trackIds = new ArrayList<>();
            for (Track track : trackHandler._tracks) {

                //store server queried track in local db
                Log.d(TAG,
                        "Storing server track in local DB: "
                                + queryRequest.getCallsign());
                long trackStartTime = getStartTime(track);
                if (trackStartTime < 0) {
                    Log.w(TAG,
                            "Failed to store track in local DB without start time");
                    continue;
                }

                int trackDbId = BreadcrumbReceiver.setServerTrack(
                        queryRequest.getCallsign(),
                        queryRequest.getUid(), trackStartTime, track, prefs);
                if (trackDbId < 0) {
                    Log.w(TAG, "Failed to store track in local DB");
                    continue;
                }

                Log.d(TAG,
                        "Stored server track for " + queryRequest.getCallsign()
                                + " and time " +
                                track.getWhen().get(0) + " with track ID: "
                                + trackDbId);
                trackIds.add(trackDbId);
            }

            if (FileSystemUtils.isEmpty(trackIds)) {
                Log.w(TAG, "Failed to store user tracks in local DB");
                throw new ConnectionException("Failed to store user tracks");
            }

            //bundle for return
            int[] toReturn = new int[trackIds.size()];
            for (int i = 0; i < trackIds.size(); i++) {
                toReturn[i] = trackIds.get(i);
            }

            builder.setProgress(100, 99, false);
            builder.setContentText(context
                    .getString(R.string.finalizing_tracks)
                    + queryRequest.getCallsign());
            if (notifyManager != null) {
                notifyManager.notify(queryRequest.getNotificationId(),
                        builder.build());
            }

            long stopTime = SystemClock.elapsedRealtime();
            Log.d(TAG,
                    String.format(LocaleUtil.getCurrent(),
                            "User Track %s downloaded %d bytes in %f seconds",
                            queryRequest.toString(), downloadSize,
                            (stopTime - startTime) / 1000F));

            //return track segment db IDs
            Bundle output = new Bundle();
            output.putParcelable(PARAM_QUERY, queryRequest);
            output.putIntArray(PARAM_TRACKDBIDS, toReturn);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, statusCode);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to query user tracks", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to query user tracks", e);
            throw new ConnectionException(e.getMessage(), statusCode);
        } finally {
            try {
                if (client != null)
                    client.shutdown();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Get the timestamp of first point in the track
     * @param track the track.
     * @return the starting time for the track
     */
    private static long getStartTime(final Track track) {
        if (track == null || FileSystemUtils.isEmpty(track.getWhen())) {
            Log.w(TAG, "No times for track");
            return -1;
        }

        Date date = KMLUtil.parseKMLDate(track.getWhen().get(0));
        if (date == null) {
            Log.w(TAG,
                    "Skipping invalid track time: " + track.getWhen().get(0));
            return -1;
        }

        return date.getTime();
    }

    /**
     * KML Feature Handler to extract a list of tracks from KML
     * Prcoesses only a single valid Placemark
     */
    static class TrackHandler implements FeatureHandler<Placemark> {

        static final String TAG = "TrackHandler";
        final List<Track> _tracks;

        TrackHandler() {
            _tracks = new ArrayList<>();
        }

        @Override
        public boolean process(Placemark placemark) {
            Log.d(TAG, "Processing placemark: " + placemark.getName()); //TODO tone down some logging after testing

            List<Track> tracks = KMLUtil.getGeometries(placemark, Track.class);
            if (!FileSystemUtils.isEmpty(tracks)) {
                Log.d(TAG, "Processing tracks: " + tracks.size());
                for (Track track : tracks) {
                    if (FileSystemUtils.isEmpty(track.getWhen())
                            || FileSystemUtils.isEmpty(track.getCoord())) {
                        Log.w(TAG, "No Track details found");
                        return false;
                    }

                    if (track.getWhen().size() != track.getCoord().size()) {
                        Log.w(TAG, "Track size mismatch");
                        return false;
                    }

                    //set track and stop iterating
                    _tracks.add(track);
                }
            }

            List<MultiTrack> multitracks = KMLUtil.getGeometries(placemark,
                    MultiTrack.class);
            if (!FileSystemUtils.isEmpty(multitracks)) {
                Log.d(TAG, "Processing MultiTracks: " + multitracks.size());
                for (MultiTrack multitrack : multitracks) {
                    List<Track> mtracks = multitrack.getTrackList();
                    if (!FileSystemUtils.isEmpty(mtracks)) {
                        Log.d(TAG,
                                "Processing MultiTrack/Tracks: "
                                        + mtracks.size());
                        for (Track mtrack : mtracks) {
                            if (FileSystemUtils.isEmpty(mtrack.getWhen())
                                    || FileSystemUtils.isEmpty(mtrack
                                            .getCoord())) {
                                Log.w(TAG, "No Track details found");
                                return false;
                            }

                            if (mtrack.getWhen().size() != mtrack.getCoord()
                                    .size()) {
                                Log.w(TAG, "Track size mismatch");
                                return false;
                            }

                            //set track and stop iterating
                            _tracks.add(mtrack);
                        }
                    }
                }
            }

            //process all placemarks
            return false;
        }
    }
}
