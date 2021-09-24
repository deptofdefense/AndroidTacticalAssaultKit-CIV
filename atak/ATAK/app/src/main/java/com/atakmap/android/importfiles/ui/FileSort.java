
package com.atakmap.android.importfiles.ui;

import com.atakmap.coremap.io.IOProvider;

import java.io.File;
import java.util.Comparator;

/**
 * File sort mode
 */
public class FileSort implements Comparator<File> {

    private boolean _ascending = true;
    private Comparator<File> _ascComp;

    public FileSort(Comparator<File> comp) {
        _ascComp = comp;
    }

    public FileSort(FileSort other) {
        _ascending = other._ascending;
        _ascComp = other._ascComp;
    }

    /**
     * Set the comparator for this sort (ascending)
     * @param comp File comparator
     */
    public void setComparator(Comparator<File> comp) {
        _ascComp = comp;
    }

    /**
     * Set whether the sort order should be ascending or descending
     * @param ascending True for ascending sort order
     */
    public void setAscending(boolean ascending) {
        _ascending = ascending;
    }

    /**
     * Check if this file sort is ascending
     * @return True if the sort is ascending
     */
    public boolean isAscending() {
        return _ascending;
    }

    @Override
    public final int compare(File f1, File f2) {
        int res = compareImpl(f1, f2);
        return _ascending ? res : -res;
    }

    /**
     * Compare two files for sorting in ascending order
     *
     * @param f1 File 1
     * @param f2 File 2
     * @return -1 = File 1 should come before File 2
     *          0 = File 1 and 2 have the same order
     *          1 = File 1 should come after File 2
     */
    protected int compareImpl(File f1, File f2) {
        if (_ascComp != null) {
            int comp = _ascComp.compare(f1, f2);
            if (comp == 0 && !(_ascComp instanceof NameSort))
                comp = f1.getName().compareTo(f2.getName());
            return comp;
        }
        return 0;
    }

    /* Default comparators */

    public static final class NameSort implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            return f1.getName().compareTo(f2.getName());
        }
    }

    public static final class DateSort implements Comparator<File> {

        private final IOProvider _ioProvider;

        public DateSort(IOProvider ioProvider) {
            _ioProvider = ioProvider;
        }

        @Override
        public int compare(File f1, File f2) {
            return Long.compare(_ioProvider.lastModified(f1),
                    _ioProvider.lastModified(f2));
        }
    }

    public static final class SizeSort implements Comparator<File> {

        private final IOProvider _ioProvider;

        public SizeSort(IOProvider ioProvider) {
            _ioProvider = ioProvider;
        }

        @Override
        public int compare(File f1, File f2) {
            return Long.compare(_ioProvider.length(f1), _ioProvider.length(f2));
        }
    }
}
