
package com.atakmap.spatial.file;

import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.features.FeatureDataStoreUtils;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore.FileCursor;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureSetCursor;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class SpatialDbContentResolver extends FileContentResolver {

    private static final String TAG = "SpatialDbContentResolver";

    protected final MapView _mapView;
    protected final DataSourceFeatureDataStore _database;

    protected SpatialDbContentResolver(final MapView mv,
            final DataSourceFeatureDataStore db, final Set<String> validExts) {
        super(validExts);
        _mapView = mv;
        _database = db;
    }

    /**
     * Perform a scan for existing files in the database and create handlers
     * Only to be called during initialization
     *
     * @param source Content source used for checking if a file matches
     */
    public void scan(SpatialDbContentSource source) {
        FileCursor c = null;
        try {
            c = _database.queryFiles();
            if (c != null) {
                while (c.moveToNext()) {
                    final File f = c.getFile();
                    if (f != null) {
                        if (source.processAccept(f,
                                0) == SpatialDbContentSource.PROCESS_ACCEPT)
                            addHandler(f);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to scan files", e);
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * Add or update a content handler
     *
     * @param f Content file
     */
    public void addHandler(File f) {
        Envelope.Builder bounds = new Envelope.Builder();
        List<Long> ids = new ArrayList<>();
        FeatureSetCursor fsc = null;
        try {
            // Get all feature sets for this file
            if (_database instanceof PersistentDataSourceFeatureDataStore2)
                fsc = ((PersistentDataSourceFeatureDataStore2) _database)
                        .queryFeatureSets(f);
            else
                fsc = _database.queryFeatureSets(null);
            while (fsc != null && fsc.moveToNext()) {
                FeatureSet fs = fsc.get();
                if (fs == null)
                    continue;
                ids.add(fs.getId());

                // Add feature bounds
                FeatureDataStoreUtils.addToEnvelope(_database, fs, bounds);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add handler for " + f, e);
        } finally {
            if (fsc != null)
                fsc.close();
        }

        // Add the new handler
        addHandler(createHandler(f, ids, bounds.build()));
    }

    /**
     * Create a new content handler
     *
     * @param f Content file
     * @param featureSetIds List of associated feature set IDs
     * @param bounds Complete bounds (null if N/A)
     * @return New content handler or null if failed
     */
    protected abstract SpatialDbContentHandler createHandler(File f,
            List<Long> featureSetIds, Envelope bounds);
}
