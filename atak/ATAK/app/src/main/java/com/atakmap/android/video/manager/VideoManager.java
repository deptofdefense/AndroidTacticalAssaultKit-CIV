
package com.atakmap.android.video.manager;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class responsible for managing video aliases persisted to the filesystem
 */
public class VideoManager {

    private static final String TAG = "VideoManager";

    public static final File DIR = new File(FileSystemUtils.getItem(
            FileSystemUtils.TOOL_DATA_DIRECTORY), "videos");
    public static final File ENTRIES_DIR = new File(DIR, ".entries");

    private static VideoManager _instance;

    public static VideoManager getInstance() {
        return _instance;
    }

    // Handles file scanning for videos under /atak/tools/videos
    private final VideoFileWatcher _scanner;

    // Handles reading and writing connection entries to and from XML
    private final VideoXMLHandler _xmlHandler;

    // Content resolver for mapping file paths to entries
    private final VideoContentResolver _contentResolver;

    // UID -> Connection entry cache
    private final Map<String, ConnectionEntry> _entries = new HashMap<>();

    // Event listeners
    private final Set<Listener> _listeners = new HashSet<>();

    public VideoManager(MapView mapView) {
        _contentResolver = new VideoContentResolver(mapView);
        _xmlHandler = new VideoXMLHandler();
        _scanner = new VideoFileWatcher(mapView, this);
        File[] roots = FileSystemUtils.getDeviceRoots();
        for (File root : roots) {
            root = new File(root, "atak/tools/videos");
            _scanner.addDirectory(root);
        }
    }

    public void init() {
        if (_instance == this)
            return;

        _instance = this;
        if (!IOProviderFactory.exists(ENTRIES_DIR)
                && !IOProviderFactory.mkdirs(ENTRIES_DIR))
            Log.w(TAG, "Failed to create entries dir");

        // Register the content resolver
        URIContentManager.getInstance().registerResolver(_contentResolver);
        addListener(_contentResolver);

        // Start scanning for videos
        _scanner.start();

        // Add any video files or folders under /videos directory
        List<ConnectionEntry> entries = new ArrayList<>();

        // Read from the entries directory
        File[] files = IOProviderFactory.listFiles(ENTRIES_DIR,
                VideoFileWatcher.XML_FILTER);
        if (!FileSystemUtils.isEmpty(files)) {
            for (File f : files)
                entries.addAll(_xmlHandler.parse(f));
        }

        // Cache entries
        addEntries(entries, false);
    }

    /**
     * Dispose the video manager
     */
    public void dispose() {
        URIContentManager.getInstance().unregisterResolver(_contentResolver);
        removeListener(_contentResolver);
        _contentResolver.dispose();
        _scanner.dispose();
        _entries.clear();
        _instance = null;
    }

    /**
     * Get a connection entry by its UID
     *
     * @param uid Connection entry UID
     * @return Connection entry or null if not found
     */
    public synchronized ConnectionEntry getEntry(String uid) {
        return _entries.get(uid);
    }

    /**
     * Get the full list of registered connection entries
     *
     * @return List of connection entries (unsorted)
     */
    public synchronized List<ConnectionEntry> getEntries() {
        return new ArrayList<>(_entries.values());
    }

    /**
     * Get a list of registered connection entries
     *
     * @param uids Entry UIDs
     * @return Connection entries
     */
    public synchronized List<ConnectionEntry> getEntries(Set<String> uids) {
        List<ConnectionEntry> ret = new ArrayList<>();
        for (String uid : uids) {
            ConnectionEntry entry = _entries.get(uid);
            if (entry != null)
                ret.add(entry);
        }
        return ret;
    }

    /**
     * Get a list of all remote connection entries
     *
     * @return List of remote connection entries (unsorted)
     */
    public synchronized List<ConnectionEntry> getRemoteEntries() {
        List<ConnectionEntry> ret = new ArrayList<>();
        for (ConnectionEntry entry : _entries.values()) {
            if (entry.isRemote())
                ret.add(entry);
        }
        return ret;
    }

    /**
     * Add an entry to the manager (which is then persisted to the filesystem)
     *
     * @param entry Connection entry
     */
    public void addEntry(ConnectionEntry entry) {
        synchronized (this) {
            entry = addEntryNoSync(entry);
        }
        if (entry != null) {
            persist(entry);
            for (Listener l : getListeners())
                l.onEntryAdded(entry);
        }
    }

    /**
     * Add a list of connection entries to the manager
     *
     * @param entries List of connection entries
     */
    public void addEntries(List<ConnectionEntry> entries, boolean persist) {
        if (FileSystemUtils.isEmpty(entries))
            return;
        List<ConnectionEntry> added = new ArrayList<>();
        synchronized (this) {
            for (ConnectionEntry entry : entries) {
                if ((entry = addEntryNoSync(entry)) != null)
                    added.add(entry);
            }
        }
        for (ConnectionEntry entry : added) {
            if (persist)
                persist(entry);
            for (Listener l : getListeners())
                l.onEntryAdded(entry);
        }
    }

    /**
     * Calls addEntries with the list and persists the entry to a file automatically
     * @param entries the list of entries
     */
    public void addEntries(List<ConnectionEntry> entries) {
        addEntries(entries, true);
    }

    /**
     * Add a connection entry to the local cache
     *
     * @param entry Connection entry
     * @return True if this entry was added/modified, false if no change
     */
    private ConnectionEntry addEntryNoSync(ConnectionEntry entry) {
        if (entry == null)
            return null;
        String uid = entry.getUID();
        if (FileSystemUtils.isEmpty(uid))
            return null; // Invalid UID
        ConnectionEntry existing = _entries.get(uid);
        if (entry.equals(existing))
            return null; // No change
        if (existing != null) {
            existing.copy(entry);
            return existing;
        } else {
            _entries.put(uid, entry);
            return entry;
        }
    }

    /**
     * Remove an entry from the cache and file system
     * Note that this will remove non-remote videos as well
     *
     * @param uid Connection entry UID
     */
    public void removeEntry(String uid) {
        ConnectionEntry removed;
        synchronized (this) {
            removed = removeEntryNoSync(uid);
        }

        // Notify listeners
        if (removed != null) {
            for (Listener l : getListeners())
                l.onEntryRemoved(removed);
        }
    }

    /**
     * Removes an entry from both the filesystem and the local in memory list.
     * @param entry the entry to be removed.
     */
    public void removeEntry(ConnectionEntry entry) {
        if (entry == null)
            return;

        // Delete local content
        if (!entry.isRemote()) {
            File file = new File(entry.getPath());
            List<ConnectionEntry> children = entry.getChildren();
            if (!FileSystemUtils.isEmpty(children)) {
                for (ConnectionEntry c : children)
                    removeEntry(c);
            }
            if (!IOProviderFactory.delete(file, IOProvider.SECURE_DELETE))
                return;
        }

        removeEntry(entry.getUID());
    }

    /**
     * Remove a set of entries by their UIDs
     *
     * @param uids Set of entry UIDs
     */
    public void removeEntries(Set<String> uids) {
        if (uids == null || uids.isEmpty())
            return;
        List<ConnectionEntry> removed = new ArrayList<>();
        synchronized (this) {
            for (String uid : uids) {
                ConnectionEntry entry = removeEntryNoSync(uid);
                if (entry != null)
                    removed.add(entry);
            }
        }
        if (removed.isEmpty())
            return;
        List<Listener> listeners = getListeners();
        for (ConnectionEntry entry : removed) {
            for (Listener l : listeners)
                l.onEntryRemoved(entry);
        }
    }

    private ConnectionEntry removeEntryNoSync(String uid) {
        ConnectionEntry removed = _entries.remove(uid);
        if (removed == null)
            return null;
        if (removed.isRemote()) {
            File file = new File(ENTRIES_DIR, uid + ".xml");
            if (IOProviderFactory.exists(file))
                FileSystemUtils.delete(file);
        }
        removed.dispose();
        return removed;
    }

    /**
     * Persist a connection entry to the file system
     * Entries use their UID for the file name
     *
     * @param entry Connection entry
     */
    public void persist(ConnectionEntry entry) {
        if (!entry.isRemote() || entry.isTemporary())
            return;
        String uid = entry.getUID();
        if (FileSystemUtils.isEmpty(uid))
            return;
        File file = entry.getLocalFile();
        if (!FileSystemUtils.isFile(file)) {
            file = new File(ENTRIES_DIR, entry.getUID() + ".xml");
            entry.setLocalFile(file);
        }
        _xmlHandler.write(entry, file);
    }

    /**
     * Get the video XML handler used by the manager
     * Using this handler over a newly created one prevents sync issues
     * when reading and writing entries
     *
     * @return XML handler
     */
    public VideoXMLHandler getXMLHandler() {
        return _xmlHandler;
    }

    /**
     * Add a listener for manager events
     *
     * @param l Listener
     */
    public synchronized void addListener(Listener l) {
        _listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        _listeners.remove(l);
    }

    private synchronized List<Listener> getListeners() {
        return new ArrayList<>(_listeners);
    }

    /**
     * Listener for add/remove events
     */
    public interface Listener {

        /**
         * Connection entry was added or updated to the video manager
         *
         * @param entry Connection entry
         */
        void onEntryAdded(ConnectionEntry entry);

        /**
         * Connection entry was removed from the video manager
         *
         * @param entry Connection entry
         */
        void onEntryRemoved(ConnectionEntry entry);
    }
}
