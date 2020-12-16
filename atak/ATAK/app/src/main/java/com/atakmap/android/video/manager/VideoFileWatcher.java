
package com.atakmap.android.video.manager;

import com.atakmap.android.importfiles.sort.ImportVideoSort;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread which watches for changes to the /atak/tools/videos directory
 *
 * XXX - I chose not to use FileObserver here because they generally aren't
 * reliable across systems and like to fire tons of events without much
 * rhyme or reason, which would end up impacting performance
 */
public class VideoFileWatcher implements Runnable {

    private static final String TAG = "VideoFileWatcher";
    private static final int SCAN_INTERVAL = 5000;

    // Filter for XML files
    public static final FileFilter XML_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase(LocaleUtil.getCurrent())
                    .endsWith(".xml");
        }
    };

    // Filter for video files
    public static final Set<String> VIDEO_EXTS = new HashSet<>(
            ImportVideoSort.VIDEO_EXTENSIONS);
    static {
        VIDEO_EXTS.add("xml");
    }

    public static final FileFilter VIDEO_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            String ext = f.getName().toLowerCase(LocaleUtil.getCurrent());
            if (!ext.contains("."))
                return false;
            ext = ext.substring(ext.lastIndexOf(".") + 1);
            return VIDEO_EXTS.contains(ext);
        }
    };

    private final MapView _mapView;
    private final List<File> _dirs = new ArrayList<>();
    private final VideoManager _manager;
    private final VideoXMLHandler _xmlHandler;
    private final Set<String> _cached = new HashSet<>();
    private final Map<File, Boolean> _canWrite = new HashMap<>();
    private boolean _started = false;
    private Thread _thread;

    VideoFileWatcher(MapView mapView, VideoManager manager) {
        _mapView = mapView;
        _manager = manager;
        _xmlHandler = manager.getXMLHandler();
    }

    public void addDirectory(File dir) {
        synchronized (_dirs) {
            _dirs.add(dir);
        }
    }

    public void start() {
        if (!_started) {
            _started = true;
            _thread = new Thread(this, TAG);
            _thread.start();
        }
    }

    public void dispose() {
        if (_started) {
            _started = false;
            try {
                _thread.interrupt();
                _thread.join();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void run() {
        while (_started) {
            // Scan for new videos and track removed entries
            List<File> dirs;
            synchronized (_dirs) {
                dirs = new ArrayList<>(_dirs);
            }
            final Set<String> removed = new HashSet<>(_cached);
            final List<ConnectionEntry> entries = new ArrayList<>();
            for (File dir : dirs)
                entries.addAll(scan(dir));
            for (ConnectionEntry ce : entries) {
                if (ce.isRemote())
                    continue; // Do not cache remote entries that will be moved
                String uid = ce.getUID();
                _cached.add(uid);
                removed.remove(uid);
            }
            _cached.removeAll(removed);
            // Post results so listeners are called on the UI thread
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    _manager.addEntries(entries);
                    _manager.removeEntries(removed);
                }
            });
            try {
                Thread.sleep(SCAN_INTERVAL);
            } catch (Exception ignore) {
            }
        }
        // Thread has been disposed
        _cached.clear();
    }

    /**
     * Recursively scans the filesystem for video files and folders
     *
     * @param root The root directory to start scanning from
     * @return List of video files and sub-directories
     */
    private List<ConnectionEntry> scan(File root) {
        List<ConnectionEntry> entries = new ArrayList<>();
        File[] files = IOProviderFactory.listFiles(root);
        if (FileSystemUtils.isEmpty(files))
            return entries;
        for (File f : files) {
            if (f.equals(VideoManager.ENTRIES_DIR))
                continue;
            if (XML_FILTER.accept(f)) {
                // Video aliases outside of the .entries directory
                List<ConnectionEntry> parsed = _xmlHandler.parse(f);
                File dir = f.getParentFile();

                // Cache the write state of this directory so we don't have to
                // can canWrite every refresh
                if (!_canWrite.containsKey(dir))
                    _canWrite.put(dir, FileSystemUtils.canWrite(dir));
                if (dir != null && _canWrite.get(dir)) {
                    // Remove the source XML
                    FileSystemUtils.delete(f);
                } else {
                    // Read-only video XML - only copy over to main storage
                    // if it doesn't already exist there so we don't squash
                    // any edits made by the user
                    for (int i = 0; i < parsed.size(); i++) {
                        ConnectionEntry ce = parsed.get(i);
                        File xml = new File(VideoManager.ENTRIES_DIR,
                                ce.getUID() + ".xml");
                        if (IOProviderFactory.exists(xml))
                            parsed.remove(i--);
                        else
                            ce.setLocalFile(xml);
                    }
                }
                entries.addAll(parsed);
                continue;
            }
            if (!IOProviderFactory.isDirectory(f) && !VIDEO_FILTER.accept(f))
                continue;
            ConnectionEntry entry = new ConnectionEntry(f);
            entry.setLocalFile(f);
            entries.add(entry);
            if (IOProviderFactory.isDirectory(f)) {
                List<ConnectionEntry> ret = scan(f);
                entry.setChildren(ret);
                entries.addAll(ret);
            }
        }
        return entries;
    }
}
