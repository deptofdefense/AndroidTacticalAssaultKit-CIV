package com.atakmap.map.layer.feature;

import com.atakmap.util.Disposable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {


    /**
     * Package Visible utility method used by AbstractFeatureDataStore3 and Utils to provide a
     * locking mechanism for bulk modification.
     * @param dataStore the datastore
     * @param bulkModify true if the lock is for bulk modification.
     * @param allowInterrupt if the modification allows for an interrupt to occur which would
     *                       throw a DataStoreException. If false, no interrupt is allowed and
     *                       the lock is reacquired
     * @throws DataStoreException if there an issue using equiring a lock on the data store
     */
    static void internalAcquireModifyLock(FeatureDataStore2 dataStore, boolean bulkModify, boolean allowInterrupt) throws DataStoreException {
        while(true) {
            try {
                dataStore.acquireModifyLock(bulkModify);
            } catch(InterruptedException e) {
                if(allowInterrupt)
                    throw new DataStoreException("Interrupted while waiting to acquire modify lock", e);
                else
                    continue;
            }
            break;
        }
    }

    /**
     * Return the number of features in a datastore that match the query
     * @param dataStore the datastore to use
     * @param params the query to use
     * @throws DataStoreException
     */
    public static int queryFeaturesCount(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params) throws DataStoreException {
        FeatureCursor result = null;
        try {
            int retval = 0;

            // XXX - set ignore fields on params

            result = dataStore.queryFeatures(params);
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Return the number of featuresets in a datastore that match the query
     * @param dataStore the datastore to use
     * @param params the query to use
     * @throws DataStoreException
     */
    public static int queryFeatureSetsCount(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params) throws DataStoreException {
        FeatureSetCursor result = null;
        try {
            int retval = 0;

            // XXX - set ignore fields on params

            result = dataStore.queryFeatureSets(params);
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Bulk copy features represented by a feature cursor into the provided datastore.
     * @param dataStore the datastore to use
     * @param features the cursor to use when copying features over.
     * @throws DataStoreException
     */
    public static void insertFeatures(FeatureDataStore2 dataStore, FeatureCursor features) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            final FeatureDefinition2 def = Adapters.adapt(features);
            while(features.moveToNext())
                dataStore.insertFeature(features.getFsid(), features.getId(), def, features.getVersion());
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    public static void insertFeatureSets(FeatureDataStore2 dataStore, FeatureSetCursor featureSets) throws DataStoreException {
       internalAcquireModifyLock(dataStore, true, true);
        try {
            while(featureSets.moveToNext())
                dataStore.insertFeatureSet(featureSets.get());
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    /**
     * Given a query, delete the matching features
     * @param dataStore the datastore to use
     * @param params the query
     * @throws DataStoreException
     */
    public static void deleteFeatures(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureCursor result = null;
            try {
                // XXX - set ignore fields on params

                result = dataStore.queryFeatures(params);
                while(result.moveToNext())
                    dataStore.deleteFeature(result.getId());
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    /**
     * Given a query, delete the matching feature sets
     * @param dataStore the datastore to use
     * @param params the query
     * @throws DataStoreException
     */
    public static void deleteFeatureSets(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureSetCursor result = null;
            try {
                // XXX - set ignore fields on params

                result = dataStore.queryFeatureSets(params);
                while(result.moveToNext())
                    dataStore.deleteFeatureSet(result.getId());
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    /**
     * Given a query, set the matching features visibility
     * @param dataStore the datastore to use
     * @param params the query
     * @param visible the visibility state to use for the feature visibility.
     * @throws DataStoreException
     */
    public static void setFeaturesVisible(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureQueryParameters params, boolean visible) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureCursor result = null;
            try {
                // XXX - set ignore fields on params

                result = dataStore.queryFeatures(params);
                while(result.moveToNext())
                    dataStore.setFeatureVisible(result.getId(), visible);
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    /**
     * Given a query, set the matching feature sets visibility
     * @param dataStore the datastore to use
     * @param params the query
     * @param visible the visibility state to use for the feature set visibility.
     * @throws DataStoreException
     */
    public static void setFeatureSetsVisible(FeatureDataStore2 dataStore, FeatureDataStore2.FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        internalAcquireModifyLock(dataStore, true, true);
        try {
            FeatureSetCursor result = null;
            try {
                // XXX - set ignore fields on params

                result = dataStore.queryFeatureSets(params);
                while(result.moveToNext())
                    dataStore.setFeatureSetVisible(result.getId(), visible);
            } finally {
                if(result != null)
                    result.close();
            }
        } finally {
            dataStore.releaseModifyLock();
        }
    }

    /**
     * Method to simplify the retrieval of a Feature from a FeatureDataStore.
     * @param dataStore the data store to use for the query.
     * @param fid the feature identifier.
     * @return the Feature.
     * @throws DataStoreException a datastore exception if there was an issue retrieving the feature.
     */
    public static Feature getFeature(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.ids = Collections.<Long>singleton(fid);
        params.limit = 1;
        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(params);
            if(!result.moveToNext())
                return null;
            return result.get();
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Method to simplify the retrieval of a FeatureSet from a FeatureDataStore.
     * @param dataStore the data store to use for the query.
     * @param fid the featureset identifier.
     * @return the FeatureSet.
     * @throws DataStoreException a datastore exception if there was an issue retrieving the featureset.
     */
    public static FeatureSet getFeatureSet(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        FeatureDataStore2.FeatureSetQueryParameters params = new FeatureDataStore2.FeatureSetQueryParameters();
        params.ids = Collections.<Long>singleton(fid);
        params.limit = 1;
        FeatureSetCursor result = null;
        try {
            result = dataStore.queryFeatureSets(params);
            if(!result.moveToNext())
                return null;
            return result.get();
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Method to simplify the deletion of all FeatureSets from the datastore.
     * @param dataStore the datastore to delete from
     * @throws DataStoreException an exception if deletion of the datasets is not successful.
     */
    public static void deleteAllFeatureSets(FeatureDataStore2 dataStore) throws DataStoreException {
        FeatureDataStore2.FeatureSetQueryParameters params = new FeatureDataStore2.FeatureSetQueryParameters();
        FeatureSetCursor result = null;
        List<Long> ids = new ArrayList<>();
        internalAcquireModifyLock(dataStore, true, true);
        try {
            try {
                result = dataStore.queryFeatureSets(params);
                if (!result.moveToNext())
                    ids.add(result.get().id);
            } finally {
                if (result != null)
                    result.close();
            }
            for (long id : ids) {
                dataStore.deleteFeatureSet(id);
            }
        } finally {
            dataStore.releaseModifyLock();
        }

    }

    /**
     * Method to simplify the retrieval of the visibility of a featureset
     * @param dataStore the datastore to use
     * @param fid the featureset id;
     * @throws DataStoreException an exception if it could not query the feature datastore
     */
    public static boolean isFeatureSetVisible(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        FeatureDataStore2.FeatureSetQueryParameters params = new FeatureDataStore2.FeatureSetQueryParameters();
        params.ids = Collections.<Long>singleton(fid);
        params.visibleOnly = true;
        params.limit = 1;
        FeatureSetCursor result = null;
        try {
            result = dataStore.queryFeatureSets(params);
            if(!result.moveToNext())
                return false;
            return true;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Method to simplify the retrieval of the visibility of a feature
     * @param dataStore the datastore to use
     * @param fid the feature id;
     * @throws DataStoreException an exception if it could not query the feature datastore
     */
    public static boolean isFeatureVisible(FeatureDataStore2 dataStore, long fid) throws DataStoreException {
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.ids = Collections.<Long>singleton(fid);
        params.visibleOnly = true;
        params.limit = 1;
        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(params);
            if(!result.moveToNext())
                return false;
            return true;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Get the associated source file for this feature set (if applicable)
     * Only works if the feature data store implements
     * {@link DataSourceFeatureDataStore}
     *
     * XXX - It would be very useful and productive if FeatureSet had a custom
     * metadata field which could provide stuff like this, but alas...
     *
     * @param db Feature data store v1
     * @param fs Feature set
     * @return File or null if N/A
     */
    public static File getSourceFile(FeatureDataStore db, FeatureSet fs) {
        if (db instanceof DataSourceFeatureDataStore)
            return ((DataSourceFeatureDataStore) db).getFile(fs);
        return null;
    }

    /**
     * Get the associated source file for this feature set (if applicable)
     *
     * @param db Feature data store v2
     * @param fs Feature set
     * @return File or null if N/A
     */
    public static File getSourceFile(FeatureDataStore2 db, FeatureSet fs) {
        if (db instanceof Adapters.FeatureDataStoreAdapter)
            return getSourceFile(((Adapters.FeatureDataStoreAdapter) db).impl, fs);
        return null;
    }

    /**
     * Get the associated source file for this feature set (if applicable)
     *
     * @param db Feature data store v1 or v2
     * @param fs Feature set
     * @return File or null if N/A
     */
    public static File getSourceFile(Disposable db, FeatureSet fs) {
        if (db instanceof FeatureDataStore)
            return getSourceFile((FeatureDataStore) db, fs);
        else if (db instanceof FeatureDataStore2)
            return getSourceFile((FeatureDataStore2) db, fs);
        return null;
    }
}
