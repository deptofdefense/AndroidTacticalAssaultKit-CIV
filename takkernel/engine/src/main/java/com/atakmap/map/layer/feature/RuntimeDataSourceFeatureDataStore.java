package com.atakmap.map.layer.feature;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.atakmap.database.IteratorCursor;
import com.atakmap.map.layer.feature.FeatureDataSource.Content;
import com.atakmap.map.layer.feature.FeatureDataSource.FeatureDefinition;

public final class RuntimeDataSourceFeatureDataStore extends AbstractDataSourceFeatureDataStore {

    private final RuntimeFeatureDataStore impl;
    private Map<Long, File> fsidToCatalogEntry;
    private Map<File, Set<Long>> catalogEntryToFsid;
    private Map<File, ParseInfo> currency;
    
    public RuntimeDataSourceFeatureDataStore() {
        this.impl = new RuntimeFeatureDataStore();
        
        this.fsidToCatalogEntry = new HashMap<Long, File>();
        this.catalogEntryToFsid = new HashMap<File, Set<Long>>();
        this.currency = new HashMap<File, ParseInfo>();
    }

    @Override
    public synchronized Feature getFeature(long fid) {
        return this.impl.getFeature(fid);
    }

    @Override
    public synchronized FeatureCursor queryFeatures(FeatureQueryParameters params) {
        return this.impl.queryFeatures(params);
    }

    @Override
    public synchronized int queryFeaturesCount(FeatureQueryParameters params) {
        return this.impl.queryFeaturesCount(params);
    }

    @Override
    public synchronized FeatureSet getFeatureSet(long featureSetId) {
        return this.impl.getFeatureSet(featureSetId);
    }

    @Override
    public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        return this.impl.queryFeatureSets(params);
    }

    @Override
    public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        return this.impl.queryFeatureSetsCount(params);
    }

    @Override
    public synchronized boolean isInBulkModification() {
        return false;
    }

    @Override
    public synchronized boolean isFeatureSetVisible(long setId) {
        return this.impl.isFeatureSetVisible(setId);
    }

    @Override
    public synchronized boolean isAvailable() {
        return this.impl.isAvailable();
    }

    @Override
    public synchronized void refresh() {
        List<File> invalid = new LinkedList<File>();

        FeatureDataSource dataSource;
        for(Map.Entry<File, ParseInfo> entry : this.currency.entrySet()) {
            dataSource = FeatureDataSourceContentFactory.getProvider(entry.getValue().provider);
            if(dataSource == null || dataSource.parseVersion() != entry.getValue().parseVersion)
                invalid.add(entry.getKey());
        }
        
        for(File f : invalid)
            this.removeNoSync(f, false);
        if(invalid.size() > 0)
            this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    public String getUri() {
        return this.impl.getUri();
    }

    @Override
    public void dispose() {
        this.impl.dispose();
    }

    @Override
    protected boolean containsImpl(File file) {
        return this.catalogEntryToFsid.containsKey(file);
    }

    @Override
    protected File getFileNoSync(long fsid) {
        return this.fsidToCatalogEntry.get(Long.valueOf(fsid));
    }

    @Override
    protected void addImpl(File file, Content content) throws IOException {
        this.impl.beginBulkModification();
        boolean success = false;
        try {
            int i = 0;
            FeatureSet set;
            Feature feature;
            while(content.moveToNext(Content.ContentPointer.FEATURE_SET)) {
                set = this.impl.insertFeatureSet(content.getProvider(),
                                                 content.getType(),
                                                 content.getFeatureSetName(),
                                                 content.getMinResolution(),
                                                 content.getMaxResolution(),
                                                 true);

                while(content.moveToNext(Content.ContentPointer.FEATURE)) {
                    final FeatureDefinition fd = content.get();
                    if (fd != null) { 
                        feature = fd.getFeature();
                        this.impl.insertFeature(set.getId(),
                                                feature.getName(),
                                                feature.getGeometry(),
                                                feature.getStyle(),
                                                feature.getAttributes(),
                                                false);
                        i++;
                    }
                }
                
                this.catalogEntryToFsid.put(file, Collections.singleton(Long.valueOf(set.getId())));
                this.fsidToCatalogEntry.put(Long.valueOf(set.getId()), file);
            }
            
            //System.out.println("ADDED " + i + " FEATURES from " + file.getName());

            int parseVersion = -1;
            FeatureDataSource provider = FeatureDataSourceContentFactory.getProvider(content.getProvider());
            if(provider != null)
                parseVersion = provider.parseVersion();
            this.currency.put(file, new ParseInfo(content.getProvider(), parseVersion));
            success = true;
        } finally {
            this.impl.endBulkModification(success);
        }
    }

    @Override
    protected void removeImpl(File file) {
        Set<Long> ids = this.catalogEntryToFsid.remove(file);
        if(ids == null)
            throw new IllegalArgumentException();
        for(Long fsid : ids)
            this.impl.deleteFeatureSet(fsid.longValue());
    }

    @Override
    public FileCursor queryFiles() {
        List<File> files = new LinkedList<File>(this.catalogEntryToFsid.keySet());
        return new FileIteratorCursor(files.iterator());
    }

    @Override
    protected void setFeatureVisibleImpl(long fid, boolean visible) {
        this.impl.setFeatureVisible(fid, visible);
    }
    
    @Override
    protected void setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
        this.impl.setFeaturesVisible(params, visible);
    }

    @Override
    protected void setFeatureSetVisibleImpl(long setId, boolean visible) {
        this.impl.setFeatureSetVisible(setId, visible);
    }
    
    @Override
    protected void setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
        this.impl.setFeatureSetsVisible(params, visible);
    }

    @Override
    protected void beginBulkModificationImpl() {}

    @Override
    protected void endBulkModificationImpl(boolean successful) {}

    @Override
    protected void deleteAllFeatureSetsImpl() {
        this.impl.deleteAllFeatureSetsImpl();
    }

    /**************************************************************************/
    
    private static class ParseInfo {
        public final String provider;
        public final int parseVersion;
        
        public ParseInfo(String provider, int parseVersion) {
            this.provider = provider;
            this.parseVersion = parseVersion;
        }
        
    }
    
    private final static class FileIteratorCursor extends IteratorCursor<File> implements FileCursor {
        public FileIteratorCursor(Iterator<File> recordIterator) {
            super(recordIterator);
        }
        
        @Override
        public File getFile() {
            return this.getRowData();
        }

    }
}
