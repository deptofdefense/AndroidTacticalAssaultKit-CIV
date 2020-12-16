
package com.atakmap.spatial.wkt;

import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore;
import com.atakmap.spatial.file.FileDatabase;
import com.atakmap.spatial.file.SpatialDbContentSource;
import com.atakmap.util.DirectExecutorService;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class OverlaysScanner {

    private final static String TAG = "OverlaysScanner";

    private OverlaysScanner() {
    }

    /**
     * Recursively scans against the specified file and adds the content.
     *   
     * @param dataStore The feature data store
     * @param file      The file or directory
     * @param sources   The sources
     */
    public static void scan(DataSourceFeatureDataStore dataStore, File file,
            Collection<SpatialDbContentSource> sources) {
        scan(dataStore, file, sources, Collections.<FileDatabase> emptySet(),
                new AtomicBoolean(false));
    }

    /**
     * Recursively scans against the specified file and adds the content.
     *   
     * @param dataStore The feature data store
     * @param file      The file or directory
     * @param sources   The sources
     * @param filedbs   The file databases
     */
    public static void scan(DataSourceFeatureDataStore dataStore, File file,
            Collection<SpatialDbContentSource> sources,
            Collection<FileDatabase> filedbs,
            AtomicBoolean cancelToken) {

        // XXX - PersistentDataSourceFeatureDataStore requires that all inserts
        //       occur on the originating thread if currently in a transaction.
        //       Utilize an appropriate ExecutorService to support
        ExecutorService ex;
        if ((dataStore instanceof PersistentDataSourceFeatureDataStore) &&
                dataStore.isInBulkModification()) {

            ex = new DirectExecutorService();
        } else
            ex = Executors.newFixedThreadPool(5,
                    new NamedThreadFactory("OverlaysScanner-Pool"));

        scanImpl(ex, dataStore, file, 0, sources, filedbs, cancelToken);
        ex.shutdown();
        try {
            ex.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

    }

    private static void scanImpl(Executor ex,
            DataSourceFeatureDataStore dataStore,
            final File file, int depth,
            Collection<SpatialDbContentSource> sources,
            Collection<FileDatabase> filedbs,
            final AtomicBoolean cancelToken) {
        LinkedList<SpatialDbContentSource> recurseSources = new LinkedList<>();
        for (final SpatialDbContentSource src : sources) {
            if (cancelToken.get())
                return;
            switch (src.processAccept(file, depth)) {
                case SpatialDbContentSource.PROCESS_ACCEPT: {
                    if (!dataStore.contains(file)) {
                        ex.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (cancelToken.get())
                                    return;
                                try {
                                    if (src.processFile(file)) {
                                    }
                                } catch (java.lang.Exception e) {
                                    Log.w(TAG,
                                            "Problem adding " + src.getType()
                                                    + " file "
                                                    + file.getAbsolutePath()
                                                    + " to Spatial DB",
                                            e);
                                }
                            }
                        });
                    }
                    break;
                }
                case SpatialDbContentSource.PROCESS_REJECT:
                    break;
                case SpatialDbContentSource.PROCESS_RECURSE:
                    recurseSources.add(src);
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        // file DBs
        if (IOProviderFactory.isFile(file)) {
            for (FileDatabase ugh : filedbs) {
                if (cancelToken.get())
                    return;
                if (ugh.accept(file) && ugh.processFile(file))
                    return;
            }
        }

        if (cancelToken.get())
            return;
        // if we have anything to recurse on, drop into the directory
        if (!recurseSources.isEmpty()) {
            File[] children = IOProviderFactory.listFiles(file);
            if (children != null) {
                for (File aChildren : children) {
                    if (cancelToken.get())
                        return;
                    scanImpl(ex, dataStore, aChildren, depth + 1,
                            recurseSources,
                            filedbs, cancelToken);
                }
            }
        }
    }
}
