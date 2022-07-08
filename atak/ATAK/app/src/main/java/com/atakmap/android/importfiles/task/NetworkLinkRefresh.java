
package com.atakmap.android.importfiles.task;

import android.app.Activity;
import android.util.Pair;

import com.atakmap.android.importfiles.http.NetworkLinkDownloader;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.KMLUtil;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Timers to auto refresh KML Network Links Task runs periodically
 * 
 * 
 */
public class NetworkLinkRefresh {
    private static final String TAG = "KMLNetworkLinkRefresh";

    /**
     * Start off with 2 threads TODO test optimal thread count here
     */
    private final ScheduledExecutorService scheduler;
    private final ArrayList<Pair<NetworkLinkRefreshTask, ScheduledFuture>> futureTasks;

    private final NetworkLinkDownloader _downloader;
    private final Activity _activity;

    public NetworkLinkRefresh(Activity activity,
            NetworkLinkDownloader downloader) {
        _activity = activity;
        scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory(
                "KMLNetworkListRefresher"));
        futureTasks = new ArrayList<>();
        _downloader = downloader;
    }

    /**
     * Schedule task to refresh URL after 'interval' from now, and then every 'interval'
     * 
     * @param res Remote resource
     */
    public void add(RemoteResource res) {
        String filename = res.getName();
        Pair<NetworkLinkRefreshTask, ScheduledFuture> taskPair = findTask(
                filename);
        if (taskPair != null) {
            // Skip update if nothing has changed
            NetworkLinkRefreshTask task = taskPair.first;
            if (task._seconds == res.getRefreshSeconds())
                return;

            Log.d(TAG, "Removing existing task task for file: " + filename);
            remove(res);
        }

        long interval = res.getRefreshSeconds();
        if (interval < KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS) {
            Log.w(TAG, "Updating task interval from " + interval
                    + " secondsto minimum "
                    + KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS);
            interval = KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS;
        }

        NetworkLinkRefreshTask task = new NetworkLinkRefreshTask(
                _downloader, res);
        Log.d(TAG,
                "Adding task every " + interval + " seconds "
                        + " for "
                        + task);

        synchronized (this) {
            futureTasks
                    .add(new Pair<NetworkLinkRefreshTask, ScheduledFuture>(task,
                            scheduler
                                    .scheduleWithFixedDelay(
                                            task,
                                            interval, interval,
                                            TimeUnit.SECONDS)));
        }
    }

    /**
     * Remove task for the specified filename (not path)
     * 
     * @param res Remote resource
     */
    public void remove(RemoteResource res) {

        String filename = res.getName();
        Pair<NetworkLinkRefreshTask, ScheduledFuture> task = findTask(filename);
        if (task == null) {

            Log.d(TAG, "Failed to find/cancel task for file: " + filename);
            return;
        }

        try {
            if (task.first.getFilename().equals(filename)) {
                Log.d(TAG, "Cancelling task " + task.first);
                task.second.cancel(true);

            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling task", e);
        }
    }

    private Pair<NetworkLinkRefreshTask, ScheduledFuture> findTask(
            String filename) {
        /**
         * TODO, do I really have to keep a reference to runnable to support finding/canceling a
         * task? Was hoping I could iterate the ScheduledFuture list and cast/find the one I wanted
         * to cancel
         */
        synchronized (this) {
            for (Pair<NetworkLinkRefreshTask, ScheduledFuture> taskPair : futureTasks) {
                if (taskPair.first.getFilename().equals(filename))
                    return taskPair;
            }
        }

        return null;
    }

    /**
     * Cancel all scheduled tasks
     */
    public void shutdown() {
        Log.d(TAG, "shutdown");

        synchronized (this) {
            for (Pair<NetworkLinkRefreshTask, ScheduledFuture> taskPair : futureTasks) {
                try {
                    Log.d(TAG, "Cancelling task " + taskPair.first.toString());
                    taskPair.second.cancel(true);
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling task", e);
                }
            }

            futureTasks.clear();
        }

        scheduler.shutdownNow();
    }

    /**
     * Runnable task to refresh a Network Link. Note for efficiency we do not parse the KML and walk
     * out all child Network Links, rather we have a task per child Network Link. Therefore, this
     * task just initiates the download via the Request Service, which downloads the file and passes
     * it back here, at which point an import task moves it to proper ATAK KML directory to be
     * processed. By the time this task is scheduled the Network Link has already been downloaded
     * and validated once, so we have some confidence that the link is "good to go" TODO Bug 1141:
     * is this optimal use of threads? We use a thread (pool) just to queue the operation for the UI
     * threa, but it is short lived since the DataDroid RequestService is started, and it has a
     * thread pool to do the work.
     * 
     * 
     */
    private class NetworkLinkRefreshTask implements Runnable {

        private final NetworkLinkDownloader _downloader;

        private final RemoteResource _resource;

        /**
         * Download interval
         */
        private final long _seconds;

        NetworkLinkRefreshTask(NetworkLinkDownloader downloader,
                RemoteResource res) {
            _downloader = downloader;
            _resource = res;
            _seconds = res.getRefreshSeconds();
        }

        public String getFilename() {
            return _resource.getName();
        }

        @Override
        public void run() {
            Thread.currentThread().setName(
                    "KMLNetworkLinkRefreshTask: " + _resource.getName());
            Log.d(TAG, "Running: " + this);
            requestDownload(_resource);
        }

        /**
         * The Download will indirectly start the DataDroid RequestService. The service must be
         * started from the UI thread
         * 
         * @param resource
         */
        private void requestDownload(final RemoteResource resource) {
            try {
                _activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!_downloader.isDownloading(resource.getUrl())) {
                            _downloader.download(resource, false); //, _notificationId);
                        } else {
                            Log.d(TAG,
                                    "network link download still in progress (skipping): "
                                            + resource.getUrl());
                        }
                    }
                });
            } catch (ClassCastException e) {
                Log.e(TAG, "Failed to redraw adapter", e);
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s", _resource.getName(),
                    _resource.getUrl());
        }
    }
}
