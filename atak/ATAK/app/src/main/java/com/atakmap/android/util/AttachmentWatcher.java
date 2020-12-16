
package com.atakmap.android.util;

import android.os.FileObserver;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.manager.VideoFileWatcher;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches all attachment directories and allows other tools to listen
 * for additions/removals
 *
 * Intentionally does not use {@link FileObserver} due to its unreliability
 * See {@link VideoFileWatcher} for another example of this implementation
 */
public class AttachmentWatcher extends Thread {

    private static final String TAG = "AttachmentWatcher";
    private static final int SCAN_INTERVAL = 1000;

    public interface Listener {

        /**
         * An attachment has been added
         * @param attFile Attachment file
         */
        void onAttachmentAdded(File attFile);

        /**
         * An attachment has been removed
         * @param attFile Attachment file
         */
        void onAttachmentRemoved(File attFile);
    }

    private static AttachmentWatcher _instance;

    public static AttachmentWatcher getInstance() {
        return _instance;
    }

    private final MapView _mapView;

    // The root attachments directory (/atak/attachments)
    private final File _attDir;

    // Attachment cache: Map item UID -> List of attachments
    // Does not require synchronization because it's only touched on the thread
    private final Set<File> _cache = new HashSet<>();

    // Listeners for add and removal events
    private final Set<Listener> _listeners = new HashSet<>();

    // Whether the watcher is active
    private boolean _started = false;

    // About to run the first scan
    private boolean _firstScan = false;

    public AttachmentWatcher(MapView mapView, File attDir) {
        super(TAG);
        _mapView = mapView;
        _attDir = attDir;
        if (_instance == null)
            _instance = this;
    }

    @Override
    public void start() {
        if (!_started) {
            _started = true;
            _firstScan = true;
            super.start();
        }
    }

    public void dispose() {
        // Thread performs the disposal on its own
        _started = false;
    }

    @Override
    public void run() {
        while (_started) {
            scan();
            try {
                Thread.sleep(SCAN_INTERVAL);
            } catch (Exception ignore) {
            }
        }
        // Thread has been disposed
        synchronized (_cache) {
            _cache.clear();
        }
    }

    /**
     * Get attachments cache
     * @return Cached attachment files
     */
    public Set<File> getCache() {
        synchronized (_cache) {
            return new HashSet<>(_cache);
        }
    }

    /**
     * Add a file to the cache
     * Used to caching outside this class
     * @param f File
     */
    public void cache(File f) {
        if (!_started)
            return;
        synchronized (_cache) {
            _cache.add(f);
        }
    }

    private void scan() {
        // Get all attachment sub-folders
        File[] dirs = IOProviderFactory.listFiles(_attDir);
        if (dirs == null)
            dirs = new File[0];

        final Set<File> added = new HashSet<>();
        for (File dir : dirs) {
            // Ignore the gallery thumbnail cache directory
            if (dir.getName().equals(".cache"))
                continue;

            // Get all the files within each sub-directory
            File[] files = IOProviderFactory.listFiles(dir);
            if (FileSystemUtils.isEmpty(files))
                continue;
            added.addAll(Arrays.asList(files));
        }

        // Determine removed items based on what's in the cache
        // and what exists now
        final Set<File> removed;
        synchronized (_cache) {
            removed = new HashSet<>(_cache);
            removed.removeAll(added);

            // Determine newly added items by removing what's already in the cache
            added.removeAll(_cache);

            // Update the cache
            _cache.addAll(added);
            _cache.removeAll(removed);
        }

        // Do not notify listeners when performing the first scan
        // Since it probably means nothing has actually changed yet
        if (_firstScan) {
            _firstScan = false;
            return;
        }

        // Post results so listeners are called on the UI thread
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : getListeners()) {
                    for (File f : added)
                        l.onAttachmentAdded(f);
                    for (File f : removed)
                        l.onAttachmentRemoved(f);
                }
            }
        });
    }

    public synchronized void addListener(Listener l) {
        _listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        _listeners.remove(l);
    }

    private synchronized List<Listener> getListeners() {
        return new ArrayList<>(_listeners);
    }
}
