
package com.atakmap.android.importfiles.task;

import android.app.Activity;
import android.util.Pair;

import com.atakmap.android.importfiles.http.KMLNetworkLinkDownloader;
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
public class KMLNetworkLinkRefresh {
    private static final String TAG = "KMLNetworkLinkRefresh";

    /**
     * Start off with 2 threads TODO test optimal thread count here
     */
    private final ScheduledExecutorService scheduler;
    private final ArrayList<Pair<Runnable, ScheduledFuture>> futureTasks;

    private final KMLNetworkLinkDownloader _downloader;
    private final Activity _activity;

    public KMLNetworkLinkRefresh(Activity activity) {
        _activity = activity;
        scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory(
                "KMLNetworkListRefresher"));
        futureTasks = new ArrayList<>();
        _downloader = new KMLNetworkLinkDownloader(activity);
    }

    /**
     * Schedule task to refresh URL after 'interval' from now, and then every 'interval'
     * 
     * @param url
     * @param filename (not filepath)
     * @param interval
     * @param units
     */
    public void add(String url, String filename, long interval,
            TimeUnit units) {
        Pair<Runnable, ScheduledFuture> taskPair = findTask(filename);
        if (taskPair != null) {
            Log.d(TAG, "Removing existing task task for file: " + filename);
            remove(filename);
        }

        if (interval < KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS) {
            Log.w(TAG, "Updating task interval from " + interval
                    + " secondsto minimum "
                    + KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS);
            interval = KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS;
        }

        KMLNetworkLinkRefreshTask task = new KMLNetworkLinkRefreshTask(url,
                filename);
        Log.d(TAG,
                "Adding task every " + interval + " " + units.toString()
                        + " for "
                        + task.toString());

        synchronized (this) {
            futureTasks.add(new Pair<Runnable, ScheduledFuture>(task, scheduler
                    .scheduleWithFixedDelay(
                            task,
                            interval, interval,
                            units)));
        }
    }

    /**
     * Remove task for the specified filename (not path)
     * 
     * @param filename the filename to used to remove the task
     */
    public void remove(String filename) {

        Pair<Runnable, ScheduledFuture> task = findTask(filename);
        if (task == null) {

            Log.d(TAG, "Failed to find/cancel task for file: " + filename);
            return;
        }

        try {
            if (task.first instanceof KMLNetworkLinkRefreshTask
                    &&
                    ((KMLNetworkLinkRefreshTask) task.first).getFilename()
                            .equals(filename)) {
                Log.d(TAG, "Cancelling task " + task.first.toString());
                task.second.cancel(true);

            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling task", e);
        }
    }

    private Pair<Runnable, ScheduledFuture> findTask(String filename) {
        /**
         * TODO, do I really have to keep a reference to runnable to support finding/canceling a
         * task? Was hoping I could iterate the ScheduledFuture list and cast/find the one I wanted
         * to cancel
         */
        synchronized (this) {
            for (Pair<Runnable, ScheduledFuture> taskPair : futureTasks) {
                if (taskPair.first instanceof KMLNetworkLinkRefreshTask
                        &&
                        ((KMLNetworkLinkRefreshTask) taskPair.first)
                                .getFilename().equals(filename)) {
                    return taskPair;
                }
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
            for (Pair<Runnable, ScheduledFuture> taskPair : futureTasks) {
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
    private class KMLNetworkLinkRefreshTask implements Runnable {
        /**
         * Remote URL
         */
        private final String _url;

        /**
         * Local filename
         */
        private final String _filename;

        /**
         * Any notifications for this NetworkLink update previous, avoid notification overload
         */
        private final int _notificationId;

        KMLNetworkLinkRefreshTask(String url, String filename) {
            _url = url;
            _filename = filename;
            _notificationId = _downloader.getNotificationId();
        }

        public String getFilename() {
            return _filename;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(
                    "KMLNetworkLinkRefreshTask: " + _filename);
            Log.d(TAG, "Running: " + toString());

            // download file & move to proper location.
            // Note ImportManagerResourceAdapter matches on url/filename
            // so OK to send a partial RemoteResource
            RemoteResource resource = new RemoteResource();
            resource.setUrl(_url);
            resource.setName(_filename);
            // set defaults for parameters unused in this flow. Note these ultimately
            // get set by ImportRemoteFileTask once the file has been downloaded
            // and moved into ATAK directory for processing by Spatial DB. ImportRemoteFileTask
            // sets the local path, MD5, and last refreshed time, for example
            resource.setType(RemoteResource.Type.KML.toString());
            resource.setDeleteOnExit(false);
            resource.setLocalPath("");
            resource.setRefreshSeconds(-1);
            resource.setLastRefreshed(0);
            resource.setMd5("");
            resource.setSource(RemoteResource.Source.LOCAL_STORAGE);
            requestDownload(resource);
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
                        _downloader.download(resource); //, _notificationId);
                    }
                });
            } catch (ClassCastException e) {
                Log.e(TAG, "Failed to redraw adapter", e);
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s", _filename, _url);
        }
    }
}
