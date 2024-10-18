
package com.atakmap.android.missionpackage.file;

import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Delete from specified directories which are older than a specified amount of time Task runs
 * periodically
 * 
 * 
 */
public class DirectoryCleanup {
    private static final String TAG = "DirectoryCleanup";

    /**
     * Default age, delete files not modified in this amount of time 2 hours
     */
    public static final long DEFAULT_AGE_MILLIS = 2 * 60 * 60 * 1000;

    /**
     * Run after 5 seconds, and then every 2 hours
     */
    public static final long DEFAULT_INTIAL_DELAY_SECONDS = 5;
    public static final long DEFAULT_PERIOD_SECONDS = 2 * 60 * 60;

    /**
     * A single thread to manage all directories should suffice
     */
    private final ScheduledExecutorService scheduler = Executors
            .newScheduledThreadPool(1,
                    new NamedThreadFactory("DirectoryCleanup-Pool"));

    private final ArrayList<ScheduledFuture> futureTasks = new ArrayList<>();

    public void add(String path) {
        add(path, DEFAULT_AGE_MILLIS, DEFAULT_INTIAL_DELAY_SECONDS,
                DEFAULT_PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    public void add(String path, long age, long delay, long period,
            TimeUnit units) {
        DirectoryCleanupTask task = new DirectoryCleanupTask(path, age);
        Log.d(TAG,
                "Adding task every " + period + " " + units.toString()
                        + " for " + task);

        futureTasks.add(scheduler.scheduleWithFixedDelay(task, delay, period,
                units));
    }

    public void shutdown() {
        Log.d(TAG, "shutdown");

        for (ScheduledFuture task : futureTasks) {
            try {
                Log.d(TAG, "Cancelling task " + task.toString());
                task.cancel(true);
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling task", e);
            }
        }

        futureTasks.clear();

        // TODO ensure this shuts down quickly, not later/after all scheduled tasks complete
        scheduler.shutdownNow();
    }

    /**
     * Runnable task to clean up a single directory
     * 
     * 
     */
    private static class DirectoryCleanupTask implements Runnable {
        /**
         * Directory to cleanup
         */
        private final String _path;

        /**
         * Delete files which have not been modified in this amount of time
         */
        private final long _age;

        public DirectoryCleanupTask(String path) {
            this(path, DEFAULT_AGE_MILLIS);
        }

        public DirectoryCleanupTask(String path, long ageMillis) {
            _path = path;
            _age = ageMillis;
        }

        @Override
        public void run() {
            Log.d(TAG, "Running: " + this);
            long cutoff = android.os.SystemClock.elapsedRealtime() - _age;
            // delete all "old" contents in _path, but not _path itself
            delete(_path, cutoff);
        }

        /**
         * Recursively delete all files/directories in specified path
         * 
         * @param path the path to delete
         * @param cutoff the age of the file based on the lastModified flag to delete
         */
        private static void delete(String path, long cutoff) {
            Log.d(TAG, "Deleting: " + path);
            File dir = new File(path);
            if (!IOProviderFactory.exists(dir)
                    || !IOProviderFactory.isDirectory(dir)) {
                Log.w(TAG, "Path does not exist: " + path);
                return;
            }

            File[] files = IOProviderFactory.listFiles(dir);
            if (files == null || files.length < 1)
                return;

            for (File file : files) {
                if (file == null || !IOProviderFactory.exists(file))
                    continue;

                if (IOProviderFactory.isDirectory(file)) {
                    delete(file.getAbsolutePath(), cutoff);
                    FileSystemUtils.delete(file);
                } else {
                    if (IOProviderFactory.lastModified(file) < cutoff) {
                        Log.d(TAG, "Deleting: " + file.getAbsolutePath());
                        FileSystemUtils.deleteFile(file);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return String.format(LocaleUtil.getCurrent(), "%s %d millis",
                    _path, _age);
        }
    }
}
