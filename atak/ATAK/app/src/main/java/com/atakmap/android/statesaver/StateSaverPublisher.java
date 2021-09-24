
package com.atakmap.android.statesaver;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StateSaverPublisher implements Runnable {
    public static final String TAG = "StateSaverPublisher";
    public static final String FROM_STATESAVER = "StateSaver";
    public static final String STATESAVER_COMPLETE_LOAD = "com.atakmap.android.statesaver.statesaver_complete_load";

    private final ExecutorService pool = Executors.newFixedThreadPool(10,
            new NamedThreadFactory(
                    "StateSaverPublisherPool"));

    private final MapView _mapView;

    private final DatabaseIface stateSaverDatabase;

    private final ArrayList<Pair<CotEvent, Bundle>> deferred = new ArrayList<>();

    private static boolean stateSaverLoaded = false;
    private boolean cancelled = false;

    public StateSaverPublisher(DatabaseIface stateSaverDatabase, MapView view) {
        this.stateSaverDatabase = stateSaverDatabase;
        _mapView = view;
    }

    private void dispatchEvent(final String event, final Bundle extras) {
        try {
            /**
             * XXX: see AbstractInput for a current duplicate of this code.
             */
            final CotEvent cotEvent = CotEvent.parse(event);

            if (cotEvent != null) {
                ImportResult retval = ImportResult.FAILURE;
                final CotMapComponent cmc = CotMapComponent.getInstance();

                if (cmc != null) {
                    //Log.d(TAG, "received for processing: " + cotEvent);
                    retval = cmc.processCotEvent(cotEvent, extras);
                }
                if (retval == ImportResult.DEFERRED) {
                    //Log.d(TAG, "deferred event: " + cotEvent);
                    synchronized (deferred) {
                        deferred.add(
                                new Pair<>(cotEvent, extras));
                    }
                } else if (retval == ImportResult.FAILURE) {
                    Log.d(TAG, "failed to properly process: " + cotEvent);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "XXX bad failed import of: " + event, e);
        } finally {
        }
    }

    public void cancel() {
        cancelled = true;
    }

    void resetStateSaverLoaded() {
        stateSaverLoaded = false;
    }

    @Override
    public void run() {
        long start = SystemClock.elapsedRealtime();
        CursorIface result = null;
        try {
            result = this.stateSaverDatabase.query("SELECT "
                    + StateSaver.COLUMN_EVENT + ", "
                    + StateSaver.COLUMN_JSON + ", "
                    + StateSaver.COLUMN_VISIBLE + ", "
                    + StateSaver.COLUMN_LAST_UPDATE + " FROM "
                    + StateSaver.TABLE_COTEVENTS
                    + " ORDER BY " + StateSaver.COLUMN_QUERY_ORDER + " ASC",
                    null);
            while (result.moveToNext()) {
                if (cancelled) {
                    pool.shutdownNow();
                    return;
                }

                final String res = result.getString(0);
                final String json = result.getString(1);
                final boolean vis = (result.getInt(2) == 1);
                final long lastUpdateTime = result.getLong(3);

                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Bundle extras = new Bundle();
                            extras.putString("from", FROM_STATESAVER);
                            extras.putString("fromClass",
                                    StateSaverPublisher.class.getName());
                            extras.putBoolean("visible", vis);
                            extras.putLong("lastUpdateTime", lastUpdateTime);

                            dispatchEvent(res, extras);
                        } catch (Exception e) {
                            Log.d(TAG,
                                    "unexpected error occurred reading from the database");
                            Log.e(TAG, "error: ", e);
                        }
                    }
                });
            }
        } finally {
            if (result != null)
                result.close();
        }

        // Wait for publish tasks to finish (timeout after 10 seconds)
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Failed to wait for thread pool to terminate", e);
            pool.shutdownNow();
        }

        this.legacy();

        // Handle deferred CoT events
        processDeferredEvents();

        //Let components know we're done
        stateSaverLoaded = true;
        Intent completeIntent = new Intent(STATESAVER_COMPLETE_LOAD);
        AtakBroadcast.getInstance().sendBroadcast(completeIntent);
        Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                + "ms to finish.");
    }

    private void processDeferredEvents() {
        List<Pair<CotEvent, Bundle>> deferred;
        synchronized (this.deferred) {
            deferred = new ArrayList<>(this.deferred);
            this.deferred.clear();
        }
        final CotMapComponent cmc = CotMapComponent.getInstance();
        if (cmc == null)
            return;
        for (int i = 0; i < deferred.size(); ++i) {

            if (cancelled)
                return;

            Log.d(TAG, "processing deferred entry: " + i);
            Pair<CotEvent, Bundle> p = deferred.get(i);
            if (p == null)
                continue; // XXX - had an NPE crash here somehow...
            ImportResult retval = cmc.processCotEvent(p.first, p.second);
            if (retval != ImportResult.SUCCESS)
                Log.d(TAG, "failed to properly process deferred event: "
                        + p.first);
        }
    }

    /**
     * Can be called to check to see if the statesaver has been completely loaded.
     * This can be used in conjunction with the intent 
     * "com.atakmap.android.statesaver.statesaver_complete_load"
     */
    public static boolean isFinished() {
        return stateSaverLoaded;
    }

    public void legacy() {
        final String routes = "Routes"; // directories to load last
        final String surveyObjects = "Survey Objects";
        final String rangeAndBearing = "Range & Bearing";
        final String jumpMaster = "DIPs";

        File parentDirectory = StateSaver.LEGACY_BASE_DIR;

        if (!IOProviderFactory.exists(parentDirectory))
            return;

        File routesDirectory = null;
        File surveyObjectsDirectory = null;
        File rangeAndBearingDirectory = null;
        File jumpMasterDirectory = null;

        File[] subdirs = IOProviderFactory.listFiles(parentDirectory);
        if (subdirs == null) // Check to make sure there is something to iterate
                             // over
        {
            if (!FileSystemUtils.deleteFile(parentDirectory))
                parentDirectory.deleteOnExit();
            return;
        }
        for (File subDirectory : subdirs) {
            if (subDirectory.getName().equals(routes))
                routesDirectory = subDirectory;
            else if (subDirectory.getName().equals(surveyObjects))
                surveyObjectsDirectory = subDirectory;
            else if (subDirectory.getName().equals(rangeAndBearing))
                rangeAndBearingDirectory = subDirectory;
            else if (subDirectory.getName().equals(jumpMaster))
                jumpMasterDirectory = subDirectory;
            else if (IOProviderFactory.isDirectory(subDirectory))
                doDirectoryWork(subDirectory);

        }

        // For jumpmaster, create alternate DIPs if there are any
        if (jumpMasterDirectory != null) {
            File[] files = IOProviderFactory.listFiles(jumpMasterDirectory);
            doDirectoryWork(jumpMasterDirectory);
            if (files != null)
                for (File f : files) {
                    if (IOProviderFactory.isDirectory(f)
                            && f.getName().contentEquals("alternate")) {
                        doDirectoryWork(f);
                        break;
                    }
                }
        }

        if (routesDirectory != null)
            doDirectoryWork(routesDirectory); // Do the routes directory last as per draper
        if (surveyObjectsDirectory != null)
            doDirectoryWork(surveyObjectsDirectory);
        if (rangeAndBearingDirectory != null) // Do the range & bearing work last to link up with
                                              // markers, etc.
            doDirectoryWork(rangeAndBearingDirectory);

        subdirs = IOProviderFactory.listFiles(parentDirectory);
        if (subdirs == null || subdirs.length < 1) {
            if (!FileSystemUtils.deleteFile(parentDirectory))
                parentDirectory.deleteOnExit();
            if (!FileSystemUtils.deleteFile(parentDirectory.getParentFile()))
                parentDirectory.getParentFile().deleteOnExit();
        }
    }

    private void doDirectoryWork(File directory) {
        File[] files = IOProviderFactory.listFiles(directory);
        if (files != null) {
            for (File path : files) {
                String s = null;
                try {
                    s = FileSystemUtils.copyStreamToString(path);
                } catch (IOException ioe) {
                    Log.d(TAG, "error reading file: " + path);
                }
                if (!FileSystemUtils.isEmpty(s)) {
                    Bundle extras = new Bundle();
                    extras.putString("from", "StateSaver");
                    extras.putBoolean("BareBack", true);
                    dispatchEvent(s, extras);
                } else {
                    Log.d(TAG, "empty file (not using): " + path);
                }
                if (!FileSystemUtils.deleteFile(path))
                    path.deleteOnExit();
            }
        }
        if (!FileSystemUtils.deleteFile(directory))
            directory.deleteOnExit();
    }
}
