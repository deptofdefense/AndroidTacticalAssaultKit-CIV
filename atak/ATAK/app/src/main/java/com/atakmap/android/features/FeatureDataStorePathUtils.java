
package com.atakmap.android.features;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.atakmap.map.layer.feature.FeatureDataStore;

public final class FeatureDataStorePathUtils {
    private FeatureDataStorePathUtils() {
    }

    /** returns the depth of the path */
    public static int getPathDepth(String path) {
        int depth = 1;

        // use length-1; don't count trailing separator
        for (int i = 0; i < path.length() - 1; i++)
            if (path.charAt(i) == '/' && (i == 0 || path.charAt(i - 1) != '\\'))
                depth++;
        return depth;
    }

    /** returns the name of the folder at the specified depth */
    public static String getFolder(String path, int depth) {
        if (depth == 0) {
            final int sepIdx = path.indexOf('/');
            if (sepIdx > 0)
                path = path.substring(0, sepIdx);
            return path;
        } else {
            int d;
            int startSepIdx = -1;
            for (d = 0; d < depth; d++) {
                do {
                    startSepIdx = path.indexOf('/', startSepIdx + 1);
                    if (startSepIdx < 0)
                        break;
                } while (startSepIdx > 0
                        && path.charAt(startSepIdx - 1) == '\\');
            }
            if (d != depth || startSepIdx < 0)
                return null;
            final int endSepIdx = path.indexOf('/', startSepIdx + 1);
            if (endSepIdx < 0)
                return path.substring(startSepIdx + 1);
            else
                return path.substring(startSepIdx + 1, endSepIdx);
        }
    }

    /** returns the path up to the specified depth (inclusive) */
    public static String getAbsolutePath(String path, int depth) {
        if (depth == 0) {
            int sepIdx = -1;
            do {
                sepIdx = path.indexOf('/', sepIdx + 1);
            } while (sepIdx > 0 && path.charAt(sepIdx) == '\\');
            if (sepIdx > 0)
                path = path.substring(0, sepIdx);
            return path;
        } else {
            int d;
            int startSepIdx = -1;
            for (d = 0; d < depth; d++) {
                do {
                    startSepIdx = path.indexOf('/', startSepIdx + 1);
                    if (startSepIdx < 0)
                        break;
                } while (startSepIdx > 0
                        && path.charAt(startSepIdx - 1) == '\\');
            }
            if (d != depth || startSepIdx < 0)
                return null;
            final int endSepIdx = path.indexOf('/', startSepIdx + 1);
            if (endSepIdx < 0)
                return path;
            else
                return path.substring(0, endSepIdx);
        }
    }

    /**************************************************************************/

    public static class PathEntry {
        public String folder;
        /** 0 means virtual */
        public long fsid;
        public Set<Long> childFsids;

        public Map<String, PathEntry> children = new TreeMap<>(
                new java.util.Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        return s1.compareToIgnoreCase(s2);
                    }
                });

        public PathEntry(String folder) {
            this(FeatureDataStore.FEATURESET_ID_NONE, folder);
        }

        public PathEntry(long fsid, String folder) {
            this.fsid = fsid;
            this.folder = folder;
            this.childFsids = new HashSet<>();
        }

        public int size() {
            int retval = 0;
            for (PathEntry child : children.values())
                retval += child.size() + 1;
            return retval;
        }
    }

    public static void processPath(PathEntry root, long fsid, String path,
            int pathDepth, int recurseDepth) {
        String folder = getFolder(path, recurseDepth++);
        PathEntry child = root.children.get(folder);
        if (child == null)
            root.children.put(folder, child = new PathEntry(folder));
        if (recurseDepth < pathDepth) {
            processPath(child, fsid, path, pathDepth, recurseDepth);
            child.childFsids.add(fsid);
        } else {
            child.fsid = fsid;
        }
    }
}
