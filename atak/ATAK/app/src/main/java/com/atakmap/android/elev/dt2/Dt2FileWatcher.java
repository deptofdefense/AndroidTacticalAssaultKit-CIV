
package com.atakmap.android.elev.dt2;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Used by the DTED outlines overlay to track DTED files on the device
 */
public class Dt2FileWatcher extends Thread {

    private static final String TAG = "Dt2FileWatcher";
    private static final int COVERAGE_SIZE = 360 * 180;
    private static final int SCAN_INTERVAL = 10000;
    private static final int DTED_LEVELS = 4;

    private static final Dt2FileFilter DTED_FILTER = new Dt2FileFilter(-1);
    private static final Dt2FileFilter[] LEVEL_FILTERS = new Dt2FileFilter[DTED_LEVELS];
    static {
        for (int i = 0; i < LEVEL_FILTERS.length; i++)
            LEVEL_FILTERS[i] = new Dt2FileFilter(i);
    }

    /**
     * DTED files updated
     */
    public interface Listener {
        void onDtedFilesUpdated();
    }

    private static class ScanResult {
        BitSet[] coverages = new BitSet[DTED_LEVELS];
        Map<Integer, Map<String, List<String>>> files = new HashMap<>();
        int totalFiles;

        ScanResult() {
            for (int level = 0; level < DTED_LEVELS; level++)
                coverages[level] = new BitSet(COVERAGE_SIZE);
        }

        void add(ScanResult res) {
            for (int level = 0; level < DTED_LEVELS; level++)
                coverages[level].or(res.coverages[level]);
            totalFiles += res.totalFiles;
            for (Map.Entry<Integer, Map<String, List<String>>> e : res.files
                    .entrySet()) {
                int level = e.getKey();
                Map<String, List<String>> fc = files.get(level);
                if (fc == null)
                    files.put(level, fc = new HashMap<>());
                fc.putAll(e.getValue());
            }
        }
    }

    private static Dt2FileWatcher _instance;

    public static Dt2FileWatcher getInstance() {
        return _instance;
    }

    private final MapView _mapView;

    // The root DTED directory (/atak/DTED)
    private final Set<File> _rootDirs = new HashSet<>();

    // List of files mapped by directory
    private final Map<Integer, Map<String, List<String>>> _fileCache = new HashMap<>();

    // DTED coverage map
    private final BitSet[] _coverages = new BitSet[DTED_LEVELS];

    // Total number of DTED files
    private int _totalFiles;

    // Listeners for add and removal events
    private final Set<Listener> _listeners = new HashSet<>();

    // Whether the watcher is active
    private boolean _started = false;

    // About to run the first scan
    private boolean _firstScan = false;

    public Dt2FileWatcher(MapView mapView, List<File> rootDirs) {
        super(TAG);
        _mapView = mapView;
        _rootDirs.addAll(rootDirs);
        for (int level = 0; level < _coverages.length; level++)
            _coverages[level] = new BitSet(COVERAGE_SIZE);
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
            long start = System.currentTimeMillis();
            scan();
            Log.d(TAG, "Took " + (System.currentTimeMillis() - start)
                    + "ms to scan DTED directory for " + _totalFiles
                    + " files.");
            try {
                Thread.sleep(SCAN_INTERVAL);
            } catch (Exception ignore) {
            }
        }
        // Thread has been disposed
        synchronized (_coverages) {
            _totalFiles = 0;
            _fileCache.clear();
            for (BitSet coverage : _coverages)
                coverage.clear();
        }
    }

    /**
     * Get a list of all DTED root directories
     * @return Root directories
     */
    public List<File> getRootDirs() {
        return new ArrayList<>(_rootDirs);
    }

    /**
     * Get corresponding file on each root given a relative path
     * @param path Relative path
     * @return List of files on each path
     */
    public List<File> getFiles(String path) {
        List<File> ret = new ArrayList<>();
        for (File root : getRootDirs())
            ret.add(new File(root.getParentFile(), path));
        return ret;
    }

    /**
     * Get coverage set for a specific DTED level
     * @param level DTED level (0 thru 3)
     * @return Bit set
     */
    public BitSet getCoverage(int level) {
        synchronized (_coverages) {
            return (BitSet) _coverages[level].clone();
        }
    }

    /**
     * Get all coverage sets
     * @return Bit sets
     */
    public BitSet[] getCoverages() {
        synchronized (_coverages) {
            BitSet[] coverages = new BitSet[_coverages.length];
            for (int level = 0; level < _coverages.length; level++)
                coverages[level] = (BitSet) _coverages[level].clone();
            return coverages;
        }
    }

    /**
     * Get a coverage set for all DTED levels
     * @return Bit set
     */
    public BitSet getFullCoverage() {
        BitSet coverage = new BitSet(COVERAGE_SIZE);
        synchronized (_coverages) {
            for (BitSet bs : _coverages)
                coverage.or(bs);
        }
        return coverage;
    }

    /**
     * Get the coverage set index given a latitude and longitude
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Index
     */
    public static int getCoverageIndex(int latitude, int longitude) {
        if (longitude > 180)
            longitude -= 360;
        else if (longitude < -180)
            longitude += 360;
        if (latitude >= 90)
            latitude = 90 - (latitude - 90);
        else if (latitude < -90)
            latitude = -90 - (latitude + 90);
        latitude += 90;
        longitude += 180;
        return (longitude * 180) + latitude;
    }

    /**
     * Get the file path relative to the "DTED" directory for a given
     * latitude and longitude
     * i.e. getRelativePath(2, 30, -73) = "w073/n30.dt2"
     * @param level DTED resolution level
     * @param lat Latitude in degrees
     * @param lng Longitude in degrees
     * @return Relative file path
     */
    public static String getRelativePath(int level, double lat, double lng) {
        StringBuilder p = new StringBuilder();

        int lngIndex = (int) Math.abs(Math.floor(lng));
        p.append(lng < 0 ? "w" : "e");
        if (lngIndex < 10)
            p.append("00");
        else if (lngIndex < 100)
            p.append("0");
        p.append(lngIndex);

        p.append(File.separator);

        int latIndex = (int) Math.abs(Math.floor(lat));
        p.append(lat < 0 ? "s" : "n");
        if (latIndex < 10)
            p.append("0");
        p.append(latIndex);

        p.append(".dt").append(level);

        return p.toString();
    }

    /**
     * Get a relative path given a file (excludes /atak/ directory)
     * @param file File
     * @return Path relative to /atak (i.e. "/atak/DTED/w039" -> "DTED/w039")
     */
    public static String getRelativePath(File file) {
        String path = file.getAbsolutePath();
        int dtedIdx = path.indexOf("/DTED");
        if (dtedIdx == -1)
            return file.getName();
        return path.substring(dtedIdx + 1);
    }

    /**
     * Refresh the cache for a specific DTED file
     * @param path Relative path to DTED file
     * @param exists True if the file exists
     */
    public void refreshCache(String path, boolean exists) {
        Dt2File d = new Dt2File(path);
        if (d.parent == null)
            return;
        final boolean updated;
        synchronized (_coverages) {
            List<String> files = getFilesRef(d.level, d.parent);
            if (files != null) {
                if (exists && !files.contains(path)) {
                    files.add(path);
                    _totalFiles++;
                } else if (!exists) {
                    files.remove(path);
                    _totalFiles--;
                }
            }
            final int coverageIndex = getCoverageIndex(d.latitude, d.longitude);
            final boolean existed = _coverages[d.level].get(coverageIndex);
            updated = (existed != exists);
            if (updated)
                _coverages[d.level].set(coverageIndex, exists);
        }
        // Dispatch update
        if (updated)
            onDtedFilesUpdated();
    }

    /**
     * Refresh the cache for a specific DTED file
     * @param file DTED file
     */
    public void refreshCache(File file) {
        refreshCache(getRelativePath(file), IOProviderFactory.exists(file));
    }

    /**
     * Get the total file count in all DTED directories
     * @return File count
     */
    public int getFileCount() {
        synchronized (_coverages) {
            return _totalFiles;
        }
    }

    private List<String> getFilesRef(int level, String path) {
        Map<String, List<String>> fileCache = _fileCache.get(level);
        return fileCache != null ? fileCache.get(path) : null;
    }

    private List<String> getFilesRef(int level, File dir) {
        return getFilesRef(level, getRelativePath(dir));
    }

    /**
     * Get a list of files within a cached directory
     * This is faster than calling {@link File#listFiles()}
     * @param level DTED level
     * @param dir File directory
     * @return List of files in the directory
     */
    public List<String> getFiles(int level, String dir) {
        synchronized (_coverages) {
            List<String> files = getFilesRef(level, dir);
            if (files != null)
                return new ArrayList<>(files);
        }
        return new ArrayList<>();
    }

    /**
     * Get file count in directory
     * @param level DTED level
     * @param dir Directory
     * @return Number of files
     */
    public int getFileCount(int level, String dir) {
        synchronized (_coverages) {
            List<String> files = getFilesRef(level, dir);
            return files != null ? files.size() : 0;
        }
    }

    /**
     * Delete all DTED files given a level
     * @param level Level (-1 to delete all)
     * @param file File/directory to delete from
     * @return True if all files deleted
     */
    public boolean delete(int level, File file) {
        // File doesn't exist - return true anyway
        if (!file.exists())
            return true;

        // Make sure we can remove the original file first
        if (file.isFile() && !IOProviderFactory.delete(file))
            return false;

        // Delete files within directory that match a certain DTED level
        boolean ret = true;
        boolean allLevels = level < 0 || level >= DTED_LEVELS;
        Dt2FileFilter filter = allLevels ? DTED_FILTER : LEVEL_FILTERS[level];
        File[] subFiles = IOProviderFactory.listFiles(file, filter);
        File parent = !_rootDirs.contains(file) ? file.getParentFile() : null;
        synchronized (_coverages) {
            if (parent != null) {
                for (int l = 0; l < DTED_LEVELS; l++) {
                    if (allLevels || level == l) {
                        List<String> files = getFilesRef(l, parent);
                        if (files != null)
                            files.remove(getRelativePath(file));
                    }
                }
            }
            if (subFiles != null) {
                // Remove files in directory
                for (File f : subFiles) {
                    if (f.isDirectory()) {
                        ret &= delete(level, f);
                    } else if (IOProviderFactory.delete(f)) {
                        _totalFiles--;
                        Dt2File d = new Dt2File(f);
                        _coverages[d.level].set(
                                getCoverageIndex(d.latitude, d.longitude),
                                false);
                    } else
                        ret = false;
                }
            } else {
                // Remove file from cache
                _totalFiles--;
                String path = file.getAbsolutePath();
                path = path.substring(0, path.length() - 1);
                boolean exists = false;
                for (int i = 0; i < DTED_LEVELS; i++) {
                    if (IOProviderFactory.exists(new File(path))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    Dt2File d = new Dt2File(file);
                    _coverages[d.level].set(
                            getCoverageIndex(d.latitude, d.longitude), false);
                }
            }
        }

        return ret;
    }

    /**
     * Delete all DTED files given a level
     * @param level Level (-1 to delete all)
     * @param path Relative file/directory path to delete from
     * @return True if all files deleted
     */
    public boolean delete(int level, String path) {
        boolean ret = true;

        // Remove files from each directory
        for (File file : getFiles(path))
            ret &= delete(level, file);

        // Signal files updated for listeners
        onDtedFilesUpdated();

        return ret;
    }

    /**
     * Perform a DTED file scan
     * NOTE: Do NOT call this on the main thread - this method can take several
     * seconds to finish
     */
    public void scan() {
        // Recursively scan DTED directory
        ScanResult res = new ScanResult();
        for (File root : _rootDirs)
            res.add(scan(root));

        boolean updated = false;
        synchronized (_coverages) {
            _totalFiles = res.totalFiles;
            _fileCache.clear();
            _fileCache.putAll(res.files);
            for (int level = 0; level < DTED_LEVELS; level++) {
                // Update coverage if changed
                if (!_coverages[level].equals(res.coverages[level])) {
                    _coverages[level].clear();
                    _coverages[level].or(res.coverages[level]);
                    updated |= true;
                }
            }
        }

        // Do not notify listeners when performing the first scan
        // Since it probably means nothing has actually changed yet
        if (_firstScan) {
            _firstScan = false;
            return;
        }

        // Post results so listeners are called on the UI thread
        if (updated)
            onDtedFilesUpdated();
    }

    private void onDtedFilesUpdated() {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : getListeners())
                    l.onDtedFilesUpdated();
            }
        });
    }

    private ScanResult scan(File directory) {
        ScanResult res = new ScanResult();
        File[] fileArr = IOProviderFactory.listFiles(directory, DTED_FILTER);

        // Empty/non-directory
        if (FileSystemUtils.isEmpty(fileArr))
            return res;

        Map<Integer, List<String>> fileCache = new HashMap<>();

        // Recursively scan for files
        for (File f : fileArr) {
            if (f.isDirectory()) {
                res.add(scan(f));
            } else {
                try {
                    Dt2File dt = new Dt2File(f);
                    res.coverages[dt.level]
                            .set(getCoverageIndex(dt.latitude, dt.longitude));
                    res.totalFiles++;
                    List<String> levelToFile = fileCache.get(dt.level);
                    if (levelToFile == null) {
                        levelToFile = new ArrayList<>();
                        fileCache.put(dt.level, levelToFile);
                    }
                    levelToFile.add(getRelativePath(f));
                } catch (Exception e) {
                    Log.d(TAG, "invalid dted file encountered " + f);
                }
            }
        }

        String path = getRelativePath(directory);
        if (_rootDirs.contains(directory)) {
            for (Map.Entry<Integer, Map<String, List<String>>> e : res.files
                    .entrySet()) {
                int level = e.getKey();
                Map<String, List<String>> nfc = e.getValue();
                Map<String, List<String>> efc = res.files.get(level);
                if (efc == null)
                    res.files.put(level, efc = new HashMap<>());

                efc.put(path, new ArrayList<>(nfc.keySet()));
            }
        } else {
            for (Map.Entry<Integer, List<String>> e : fileCache.entrySet()) {
                int level = e.getKey();
                Map<String, List<String>> fc = res.files.get(level);
                if (fc == null)
                    res.files.put(level, fc = new HashMap<>());
                fc.put(path, e.getValue());
            }
        }

        return res;
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
