package com.atakmap.map.layer.feature;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;

public final class PersistentDataSourceFeatureDataStore2 implements DataSourceFeatureDataStore {
    NativeFeatureDataStore2 impl;
    Pointer pointer;
    Map<FeatureDataStore.OnDataStoreContentChangedListener, FeatureDataStore2.OnDataStoreContentChangedListener> listeners;

    public PersistentDataSourceFeatureDataStore2(File database) {
        this.pointer = create(database != null ? database.getAbsolutePath() : null);

        this.impl = new NativeFeatureDataStore2(asBase(this.pointer.raw));

        this.listeners = new IdentityHashMap<>();
    }

    // DataSourceFeatureDataStore

    @Override
    public boolean contains(File file) {
        impl.rwlock.acquireRead();
        try {
            return contains(this.pointer.raw, file.getAbsolutePath());
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public File getFile(FeatureSet info) {
        impl.rwlock.acquireRead();
        try {
            final String retval = getFile(this.pointer.raw, info.getId());
            if(retval == null)
                return null;
            return new File(retval);
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public boolean add(File file) throws IOException {
        impl.rwlock.acquireRead();
        try {
            return add(this.pointer.raw, file.getAbsolutePath());
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public boolean add(File file, String hint) throws IOException {
        impl.rwlock.acquireRead();
        try {
            return add(this.pointer.raw, file.getAbsolutePath(), hint);
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public void remove(File file) {
        impl.rwlock.acquireRead();
        try {
            remove(this.pointer.raw, file.getAbsolutePath());
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public boolean update(File file) throws IOException {
        impl.rwlock.acquireRead();
        try {
            return update(this.pointer.raw, file.getAbsolutePath());
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public boolean update(FeatureSet featureSet) throws IOException {
        File f = getFile(featureSet);
        if(f == null)
            return false;
        return update(f);
    }

    @Override
    public FileCursor queryFiles() {
        impl.rwlock.acquireRead();
        try {
            final Pointer retval = queryFiles(this.pointer.raw);
            return (retval != null) ? new NativeFileCursor(retval, this) : null;
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    // FeatureDataStore

    @Override
    public void addOnDataStoreContentChangedListener(final OnDataStoreContentChangedListener l) {
        FeatureDataStore2.OnDataStoreContentChangedListener l2;
        synchronized(this.listeners) {
            if(this.listeners.containsKey(l))
                return;
            l2 = new FeatureDataStore2.OnDataStoreContentChangedListener() {
                @Override
                public void onDataStoreContentChanged(FeatureDataStore2 dataStore) {
                    l.onDataStoreContentChanged(PersistentDataSourceFeatureDataStore2.this);
                }

                @Override
                public void onFeatureInserted(FeatureDataStore2 dataStore, long fid, FeatureDefinition2 def, long version) {
                    l.onDataStoreContentChanged(PersistentDataSourceFeatureDataStore2.this);
                }

                @Override
                public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid, int modificationMask, String name, Geometry geom, Style style, AttributeSet attribs, int attribsUpdateType) {
                    l.onDataStoreContentChanged(PersistentDataSourceFeatureDataStore2.this);
                }

                @Override
                public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid) {
                    l.onDataStoreContentChanged(PersistentDataSourceFeatureDataStore2.this);
                }

                @Override
                public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore, long fid, boolean visible) {
                    l.onDataStoreContentChanged(PersistentDataSourceFeatureDataStore2.this);
                }
            };
            this.listeners.put(l, l2);
        }

        this.impl.addOnDataStoreContentChangedListener(l2);
    }

    @Override
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        FeatureDataStore2.OnDataStoreContentChangedListener l2;
        synchronized(this.listeners) {
            l2 = this.listeners.remove(l);
            if(l2 == null)
                return;
        }
        this.impl.removeOnDataStoreContentChangedListener(l2);
    }

    @Override
    public Feature getFeature(long fid) {
        try {
            FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
            params.ids = Collections.singleton(Long.valueOf(fid));
            params.limit = 1;
            FeatureCursor result = null;
            try {
                result = this.impl.queryFeatures(params);
                return result.moveToNext() ? result.get() : null;
            } finally {
                if(result != null)
                    result.close();
            }
        } catch(DataStoreException e) {
            // XXX -
            return null;
        }
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) {
        try {
            return impl.queryFeatures(Adapters.adapt(params, null));
        } catch(DataStoreException e) {
            return FeatureCursor.EMPTY;
        }
    }

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) {
        try {
            return impl.queryFeaturesCount(Adapters.adapt(params, null));
        } catch(DataStoreException ignored) {
            return 0;
        }
    }

    @Override
    public FeatureSet getFeatureSet(long fsid) {
        try {
            FeatureDataStore2.FeatureSetQueryParameters params = new FeatureDataStore2.FeatureSetQueryParameters();
            params.ids = Collections.singleton(Long.valueOf(fsid));
            params.limit = 1;
            com.atakmap.map.layer.feature.FeatureSetCursor result = null;
            try {
                result = impl.queryFeatureSets(params);
                return result.moveToNext() ? result.get() : null;
            } finally {
                if(result != null)
                    result.close();
            }
        } catch(DataStoreException ignored) {
            return null;
        }
    }

    @Override
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        try {
            return new FeatureSetCursorAdapter(impl.queryFeatureSets(Adapters.adapt(params, null)));
        } catch(DataStoreException e) {
            return FeatureSetCursor.EMPTY;
        }
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        try {
            return impl.queryFeatureSetsCount(Adapters.adapt(params, null));
        } catch(DataStoreException e) {
            return 0;
        }
    }

    /**
     * Get all feature sets for a given source file
     *
     * @param f Source file
     * @return Feature set cursor
     */
    public FeatureSetCursor queryFeatureSets(File f) {
        impl.rwlock.acquireRead();
        try {
            final Pointer retval = queryFeatureSets(pointer.raw, f.getAbsolutePath());
            if(retval == null)
                return null;
            return new FeatureSetCursorAdapter(new NativeFeatureSetCursor(retval, this));
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    /**************************************************************************/

    @Override
    public int getModificationFlags() {
        return Adapters.adaptModificationFlags(impl.getModificationFlags(), 2, 1);
    }

    @Override
    public void beginBulkModification() {
        // XXX - bulk modify is handled via write lock on FSD2
    }

    @Override
    public void endBulkModification(boolean successful) {
        // XXX - bulk modify is handled via write lock on FSD2
    }

    @Override
    public boolean isInBulkModification() {
        // XXX - bulk modify is handled via write lock on FSD2
        return false;
    }

    @Override
    public FeatureSet insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution, boolean returnRef) {
        try {
            final long fsid = impl.insertFeatureSet(new FeatureSet(provider, type, name, minResolution, maxResolution));
            return returnRef ? getFeatureSet(fsid) : null;
        } catch(DataStoreException e) {
            return null;
        }
    }

    @Override
    public void updateFeatureSet(long fsid, String name) {
        try {
            impl.updateFeatureSet(fsid, name);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution) {
        try {
            impl.updateFeatureSet(fsid, minResolution, maxResolution);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) {
        try {
            impl.updateFeatureSet(fsid, name, minResolution, maxResolution);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void deleteFeatureSet(long fsid) {
        try {
            this.impl.deleteFeatureSet(fsid);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void deleteAllFeatureSets() {
        try {
            this.impl.deleteFeatureSets(null);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public Feature insertFeature(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, boolean returnRef) {
        try {
            Feature f = new Feature(fsid, FeatureDataStore2.FEATURE_ID_NONE, name, geom, style, attributes, FeatureDataStore2.TIMESTAMP_NONE, FeatureDataStore2.FEATURE_VERSION_NONE);
            final long fid = impl.insertFeature(f);
            return returnRef ? getFeature(fid) : null;
        } catch(DataStoreException e) {
            return null;
        }
    }

    @Override
    public Feature insertFeature(long fsid, FeatureDefinition def, boolean returnRef) {
        try {
            final long fid = impl.insertFeature(fsid, FeatureDataStore2.FEATURE_ID_NONE, Adapters.adapt(def), FeatureDataStore2.FEATURE_VERSION_NONE);
            return returnRef ? getFeature(fid) : null;
        } catch(DataStoreException e) {
            return null;
        }
    }

    @Override
    public void updateFeature(long fid, String name) {
        try {
            impl.updateFeature(fid, FeatureDataStore2.PROPERTY_FEATURE_NAME, name, null, null, null, 0);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeature(long fid, Geometry geom) {
        try {
            impl.updateFeature(fid, FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY, null, geom, null, null, 0);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeature(long fid, Style style) {
        try {
            impl.updateFeature(fid, FeatureDataStore2.PROPERTY_FEATURE_STYLE, null, null, style, null, 0);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeature(long fid, AttributeSet attributes) {
        try {
            impl.updateFeature(fid, FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES, null, null, null, attributes, 0);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void updateFeature(long fid, String name, Geometry geom, Style style, AttributeSet attributes) {
        try {
            impl.updateFeature(fid, FeatureDataStore2.PROPERTY_FEATURE_NAME|FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY|FeatureDataStore2.PROPERTY_FEATURE_STYLE|FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES, name, geom, style, attributes, FeatureDataStore2.UPDATE_ATTRIBUTES_SET);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void deleteFeature(long fid) {
        try {
            this.impl.deleteFeature(fid);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void deleteAllFeatures(long fsid) {
        try {
            FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
            params.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
            params.featureSetFilter.ids = Collections.singleton(Long.valueOf(fsid));
            this.impl.deleteFeatures(params);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public int getVisibilitySettingsFlags() {
        return Adapters.adaptVisibilityFlags(impl.getVisibilityFlags(), 2, 1);
    }

    @Override
    public void setFeatureVisible(long fid, boolean visible) {
        try {
            this.impl.setFeatureVisible(fid, visible);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) {
        try {
            this.impl.setFeaturesVisible(Adapters.adapt(params, null), visible);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public boolean isFeatureVisible(long fid) {
        try {
            FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
            params.ids = Collections.singleton(Long.valueOf(fid));
            params.visibleOnly = true;
            params.limit = 1;
            return this.impl.queryFeaturesCount(params)>0;
        } catch(DataStoreException e) {
            return false;
        }
    }

    @Override
    public void setFeatureSetVisible(long fsid, boolean visible) {
        try {
            this.impl.setFeatureSetVisible(fsid, visible);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) {
        try {
            this.impl.setFeatureSetsVisible(Adapters.adapt(params, null), visible);
        } catch(DataStoreException ignored) {}
    }

    @Override
    public boolean isFeatureSetVisible(long fsid) {
        try {
            FeatureDataStore2.FeatureSetQueryParameters params = new FeatureDataStore2.FeatureSetQueryParameters();
            params.ids = Collections.singleton(Long.valueOf(fsid));
            params.visibleOnly = true;
            params.limit = 1;
            return this.impl.queryFeatureSetsCount(params)>0;
        } catch(DataStoreException e) {
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        // no availability on API
        return true;
    }

    @Override
    public void refresh() {
        impl.rwlock.acquireRead();
        try {
            refresh(this.pointer.raw);
        } finally {
            impl.rwlock.releaseRead();
        }
    }

    @Override
    public String getUri() {
        return impl.getUri();
    }

    @Override
    public void dispose() {
        this.impl.rwlock.acquireWrite();
        try {
            if(this.pointer.raw != 0L)
                destruct(this.pointer);
            this.impl.finalize();
        } finally {
            this.impl.rwlock.releaseWrite();
        }
    }

    @Override
    public void finalize() {
        if(this.pointer.raw != 0L)
            Log.w("PersistentDataSourceFeatureDataStore2", "Leaking native cursor");
    }

    final static class FeatureSetCursorAdapter implements FeatureSetCursor {

        final com.atakmap.map.layer.feature.FeatureSetCursor impl;

        FeatureSetCursorAdapter(com.atakmap.map.layer.feature.FeatureSetCursor impl) {
            this.impl = impl;
        }

        @Override
        public FeatureSet get() {
            return impl.get();
        }

        @Override
        public boolean moveToNext() {
            return impl.moveToNext();
        }

        @Override
        public void close() {
            impl.close();
        }

        @Override
        public boolean isClosed() {
            return impl.isClosed();
        }
    }

    static native Pointer create(String path);
    static native void destruct(Pointer pointer);
    static native Pointer asBase(long pointer);
    static native boolean contains(long pointer, String path);
    static native String getFile(long pointer, long fsid);
    static native boolean add(long pointer, String file);
    static native boolean add(long pointer, String file, String hint);
    static native void remove(long pointer, String path);
    static native boolean update(long pointer, String path);
    static native Pointer queryFiles(long pointer);
    static native void refresh(long pointer);
    static native Pointer queryFeatureSets(long pointer, String path);
}
