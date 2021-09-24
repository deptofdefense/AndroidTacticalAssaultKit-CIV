package com.atakmap.map.layer.raster.nativeimagery;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseFactory2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseSpi2;
import com.atakmap.map.layer.raster.mosaic.MultiplexingMosaicDatabaseCursor2;
import com.atakmap.map.layer.raster.service.SelectionOptionsCallbackExtension;
import com.atakmap.spatial.SpatialCalculator;
import com.atakmap.util.ReferenceCount;

public final class NativeImageryMosaicDatabase2 implements MosaicDatabase2, RasterDataStore.OnDataStoreContentChangedListener, SelectionOptionsCallbackExtension.OnSelectionOptionsChangedListener {
    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return TYPE;
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return new NativeImageryMosaicDatabase2();
        }
        
    };

    private final static Comparator<DatasetDescriptor> DATASET_COMPARATOR = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor d1, DatasetDescriptor d2) {
            final long diff = d1.getLayerId()-d2.getLayerId();
            if(diff < 0)
                return -1;
            else if(diff > 0)
                return 1;
            else
                return 0;
        }
    };
    
    public final static String TAG = "NativeImageryMosaicDatabase";
    public final static String TYPE = "native-imagery-layer";
    
    private final static Map<String, ReferenceCount<WeakReference<NativeImageryRasterLayer2>>> registeredDataStores = new HashMap<String, ReferenceCount<WeakReference<NativeImageryRasterLayer2>>>(); 

    private RasterDataStore dataStore;
    private NativeImageryRasterLayer2 rasterLayer;
    private boolean coveragesValid;
    private Coverage coverage;
    private boolean open = false;

    private final Map<DatasetDescriptor, MosaicDatabase2> databases;
    private final Map<String, Coverage> coverages;
    
    NativeImageryMosaicDatabase2() {
        this.databases = new TreeMap<DatasetDescriptor, MosaicDatabase2>(DATASET_COMPARATOR);
        this.coverages = new HashMap<String, Coverage>();
    }
    
    private void refreshCoveragesNoSync() {
        if(this.coveragesValid)
            return;
        
        this.coverages.clear();
        
        Map<String, Collection<Coverage>> dbcoverages = new HashMap<String, Collection<Coverage>>();
        Map<String, Coverage> dbcoverage = new HashMap<String, Coverage>();
        Collection<Coverage> covList;
        for(MosaicDatabase2 mosaicdb : this.databases.values()) {
            // aggregate coverage
            covList = dbcoverages.get(null);
            if(covList == null)
                dbcoverages.put(null, covList=new ArrayList<Coverage>(this.databases.size()));
            covList.add(mosaicdb.getCoverage());
            
            mosaicdb.getCoverages(dbcoverage);
            
            // individual coverages
            for(Map.Entry<String, Coverage> entry : dbcoverage.entrySet()) {
                covList = dbcoverages.get(entry.getKey());
                if(covList == null)
                    dbcoverages.put(entry.getKey(), covList=new LinkedList<Coverage>());
                covList.add(entry.getValue());
            }
        }

        // if there's no content, set global coverage to the world and skip the
        // type section
        if(dbcoverages.isEmpty()) {
            this.coverage = new Coverage(DatasetDescriptor.createSimpleCoverage(new GeoPoint(90, -180), new GeoPoint(90, 180), new GeoPoint(-90, 180), new GeoPoint(-90, -180)), Double.MAX_VALUE, 0.0d);
            return;
        }

        // aggregate the coverages
        SpatialCalculator calc = null;
        try {
            calc = new SpatialCalculator();
            
            for(Map.Entry<String, Collection<Coverage>> entry : dbcoverages.entrySet()) {
                if(entry.getValue().size() == 1) {
                    this.coverages.put(entry.getKey(), entry.getValue().iterator().next());
                } else { // must have at least one entru
                    this.coverages.put(entry.getKey(), aggregateCoverages(calc, entry.getValue()));
                }
            }
            
            this.coverage = this.coverages.remove(null);
        } finally {
            if(calc != null)
                calc.dispose();
        }
        
        this.coveragesValid = true;
    }

    /**************************************************************************/
    // Mosaic Database

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void open(File f) {
        ReferenceCount<WeakReference<NativeImageryRasterLayer2>> ref;
        synchronized(NativeImageryMosaicDatabase2.this) {
            ref = registeredDataStores.get(f.getPath());
        }
        if(ref != null)
            this.rasterLayer = ref.value.get();
        if(this.rasterLayer == null)
            throw new IllegalStateException();
        
        this.dataStore = this.rasterLayer.getDataStore();

        this.dataStore.addOnDataStoreContentChangedListener(this);
        
        SelectionOptionsCallbackExtension optsChangedEx = this.rasterLayer.getExtension(SelectionOptionsCallbackExtension.class);
        if(optsChangedEx != null) {
            optsChangedEx.addOnSelectionOptionsChangedListener(this);
        }
        
        this.onDataStoreContentChanged(this.dataStore);
        this.open = true;
    }

    @Override
    public void close() {
        if(this.open) {
            this.open = false;
            SelectionOptionsCallbackExtension optsChangedEx = this.rasterLayer.getExtension(SelectionOptionsCallbackExtension.class);
            if(optsChangedEx != null) {
                optsChangedEx.removeOnSelectionOptionsChangedListener(this);
            }
            
            this.dataStore.removeOnDataStoreContentChangedListener(this);
    
            synchronized(this.databases) {
                for(MosaicDatabase2 db : this.databases.values())
                    db.close();
                this.databases.clear();
            }
        }
    }

    @Override
    public Coverage getCoverage() {
        synchronized(this.databases) {
            this.refreshCoveragesNoSync();

            return this.coverage;
        }
    }

    @Override
    public void getCoverages(Map<String, Coverage> retval) {
        synchronized(this.databases) {
            this.refreshCoveragesNoSync();

            retval.clear();
            retval.putAll(this.coverages);
        }
    }

    @Override
    public Coverage getCoverage(String type) {
        synchronized(this.databases) {
            this.refreshCoveragesNoSync();

            return this.coverages.get(type);
        }
    }

    @Override
    public Cursor query(QueryParameters params) {
        Collection<MosaicDatabase2.Cursor> cursors;
        synchronized(this.databases) {
            cursors = new ArrayList<MosaicDatabase2.Cursor>(this.databases.size());
            for(MosaicDatabase2 db : this.databases.values())
                cursors.add(db.query(params));
        }
        return new MultiplexingMosaicDatabaseCursor2(cursors, params != null ? params.order : QueryParameters.Order.MaxGsdDesc);
    }


    /**************************************************************************/
    // Raster Data Store On Data Store Content Changed Listener
    
    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        this.onSelectionOptionsChanged(this.rasterLayer);
    }
    
    @Override
    public void onSelectionOptionsChanged(RasterLayer2 layer) {
        RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
        this.rasterLayer.filterQueryParams(params);
        
        final boolean refreshVisible;
        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            result = dataStore.queryDatasets(params);
            DatasetDescriptor desc;
            MosaicDatabase2 mosaicdb;
            synchronized(this.databases) {
                Map<DatasetDescriptor, MosaicDatabase2> invalid = new TreeMap<DatasetDescriptor, MosaicDatabase2>(DATASET_COMPARATOR);
                invalid.putAll(this.databases);
                this.databases.clear();

                boolean changed = false;
                while(result.moveToNext()) {
                    desc = result.get();
                    // XXX - exclude tile pyramids -- it would be much more
                    //       practical if we had a dedicated tile pyramid
                    //       DatasetDescriptor derivative
                    if(desc.getDatasetType().equals("tileset"))
                        continue;

                    mosaicdb = invalid.remove(desc);
                    if(mosaicdb != null) {
                        this.databases.put(desc, mosaicdb);
                    } else {
                        mosaicdb = createMosaicDatabase(desc);
                        if(mosaicdb != null) {
                            this.databases.put(desc, mosaicdb);
                            changed = true;
                        }
                    }
                }
                
                changed |= !invalid.isEmpty();
                
                // coverages are invalid if 'databases' content has changed
                this.coveragesValid &= !changed;

                // Refresh visible layers if changed and open (or else ATAK hangs)
                refreshVisible = (changed && this.open);
            }
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**************************************************************************/
    
    public synchronized static String registerLayer(NativeImageryRasterLayer2 layer) {
        final String retval = generateKey(layer);
        registeredDataStores.put(retval, new ReferenceCount<WeakReference<NativeImageryRasterLayer2>>(new WeakReference<NativeImageryRasterLayer2>(layer)));
        return retval;
    }
    
    public synchronized static void unregisterLayer(NativeImageryRasterLayer2 layer) {
        final String key = generateKey(layer);
        ReferenceCount<WeakReference<NativeImageryRasterLayer2>> ref = registeredDataStores.get(key);
        if(ref == null)
            return;
        ref.dereference();
        if(ref.value.get() == null || !ref.isReferenced()) {
            registeredDataStores.remove(key);
        }
    }
    
    private static String generateKey(NativeImageryRasterLayer2 layer) {
        return "$NativeImageryRasterLayer/@" + layer.hashCode();
    }

    private static MosaicDatabase2 createMosaicDatabase(DatasetDescriptor desc) {
        MosaicDatabase2 retval = null;
        if(desc instanceof MosaicDatasetDescriptor) {
            final String mosaicDbType = ((MosaicDatasetDescriptor)desc).getMosaicDatabaseProvider();
            final File mosaicDbFile = ((MosaicDatasetDescriptor)desc).getMosaicDatabaseFile();
            
            retval = MosaicDatabaseFactory2.create(mosaicDbType);
            if(retval != null)
                try {
                    retval.open(mosaicDbFile);
                    if(DatasetDescriptor.getExtraData(desc, "relativePaths", "true").equals("true")) {
                        String baseUri = GdalLayerInfo.getGdalFriendlyUri(desc);
                        if (baseUri.length() > 0 && baseUri.charAt(baseUri.length() - 1) == File.separatorChar)
                            baseUri = baseUri.substring(0, baseUri.length() - 1);
                        retval = new ResolvedPathMosaicDatabase(retval, baseUri);
                    }
                } catch(Throwable t) {
                    Log.d(TAG, "Problem opening file: " + mosaicDbFile.getAbsolutePath(), t);
                    retval = null;
                }
        } else if(desc instanceof ImageDatasetDescriptor) {
            // check to see if there is a mosaic provider for the dataset type
            retval = MosaicDatabaseFactory2.create(desc.getDatasetType());
            if(retval != null) {
                try {
                    retval.open(new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(desc.getUri())));
                } catch(Throwable t) {
                    Log.d(TAG, "Problem opening file: " + desc.getUri(), t);
                    retval = null;
                }
            }
            // create a single image mosaic DB
            if(retval == null)
                retval = new ImageDatasetDescriptorMosaicDatabase((ImageDatasetDescriptor)desc);
        }
        
        if(retval == null)
            Log.d(TAG, "Failed to create mosaic database for " + desc.getName());
        
        return retval;
    }
    
    private static Coverage aggregateCoverages(SpatialCalculator calc, Collection<Coverage> coverages) {
        Iterator<Coverage> iter = coverages.iterator();
        
        Coverage cov;
        long result;
        double minGsd;
        double maxGsd;
        long handle;
        
        cov = iter.next();
        handle = calc.createGeometry(cov.geometry);
        minGsd = cov.minGSD;
        maxGsd = cov.maxGSD;

        result = handle;
        
        while(iter.hasNext()) {
            cov = iter.next();
            handle = calc.createGeometry(cov.geometry);
            minGsd = Math.max(cov.minGSD, minGsd);
            maxGsd = Math.min(cov.maxGSD, maxGsd);
            
            calc.union(result, handle, result);
        }
        
        return new Coverage(calc.getGeometry(result), minGsd, maxGsd);
    }
}
