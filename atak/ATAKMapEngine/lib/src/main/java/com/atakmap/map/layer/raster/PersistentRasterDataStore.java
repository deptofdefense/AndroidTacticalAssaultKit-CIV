package com.atakmap.map.layer.raster;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.util.Pair;

import com.atakmap.content.CatalogCurrency;
import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.lang.Objects;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.spatial.SpatialCalculator;
import com.atakmap.util.WeakValueMap;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;


/**
 * Implementation of {@link LocalRasterDataStore} that persists between runtime
 * invocations of the software.
 * 
 * <P>The content of the working directory specified during instantiation must
 * be guaranteed to remain unmodified by the application so long as the data
 * store database file specified during instantiation. The application is free
 * to delete or otherwise modify the working directory when the database file
 * is deleted.
 * 
 * @author Developer
 */
public class PersistentRasterDataStore extends LocalRasterDataStore implements RasterDataStore {

    private final static boolean RESOLVE_COLLECTION_COVERAGES = true;

    private final static long LARGE_DATASET_RECURSE_LIMIT = 5000;

    private final static String TAG = "PersistentRasterDataStore";

    private final static String CURRENCY_NAME = "PersistentRasterDataSource.Currency";
    private final static int CURRENCY_VERSION = 3;
    
    private final static Map<DatasetQueryParameters.Order, String> ORDER_FIELD_TO_LAYERDB_COL = new HashMap<DatasetQueryParameters.Order, String>();
    static {
        ORDER_FIELD_TO_LAYERDB_COL.put(DatasetQueryParameters.Type.INSTANCE, LayersDatabase.COLUMN_LAYERS_DATASET_TYPE);
        ORDER_FIELD_TO_LAYERDB_COL.put(DatasetQueryParameters.GSD.INSTANCE, LayersDatabase.COLUMN_LAYERS_MAX_GSD);
        ORDER_FIELD_TO_LAYERDB_COL.put(DatasetQueryParameters.Name.INSTANCE, LayersDatabase.COLUMN_LAYERS_NAME);
        ORDER_FIELD_TO_LAYERDB_COL.put(DatasetQueryParameters.Provider.INSTANCE, LayersDatabase.COLUMN_LAYERS_PROVIDER);
    }

    protected final File storeDbFile;

    protected LayersDatabase layersDb;
    
    protected WeakValueMap<Long, DatasetDescriptor> layerRefs; 
    
    private Map<Pair<String, String>, BackgroundCoverageResolver> pendingCoverages;
    
    private Map<Pair<String, String>, QueryInfoSpec> infoCache;


    static final Pair createPair(String a, String b) { 
        if (a == null) a = "NULL";
        if (b == null) b = "NULL";
        return Pair.create(a,b);
    }

    
    /**
     * Creates a new instance.
     * 
     * @param storeDbFile   The database file for the data store. If the
     *                      specified file does not exist it will be newly
     *                      created.
     * @param workingDir    The working directory where dataset private content
     *                      may be stored. The contents of this directory may
     *                      not be modified so long as the specified database
     *                      file is valid.
     */
    public PersistentRasterDataStore(File storeDbFile, File workingDir) {
        super(workingDir);
        
        this.storeDbFile = storeDbFile;
        
        final CatalogCurrencyRegistry currency = new CatalogCurrencyRegistry();
        currency.register(new ValidateCurrency());
        
        this.layersDb = new LayersDatabase(this.storeDbFile, currency);
        this.layerRefs = new WeakValueMap<Long, DatasetDescriptor>(false);
        
        this.pendingCoverages = new HashMap<Pair<String, String>, BackgroundCoverageResolver>();
        
        this.infoCache = new HashMap<Pair<String, String>, QueryInfoSpec>();
    }

    private void geometryResolvedNoSync(String dataset, String type, Geometry coverage, boolean notify) {
        //this.resolvedCoverages.put(createPair(dataset, type), coverage);
        QueryInfoSpec spec = this.infoCache.get(createPair(dataset, type));
        if (spec != null) { 
            spec.coverage = coverage;
        
            // XXX - consider persisting computed coverages

            if(notify)
                this.dispatchDataStoreContentChangedNoSync();
         }
    }

    private void invalidateCacheInfo(Collection<DatasetDescriptor> descs, boolean notify) {
        Iterator<Pair<String, String>> infoCacheKeyIter;
        Iterator<Map.Entry<Pair<String, String>, BackgroundCoverageResolver>> pendingCoverageEntryIter;
        
        Collection<String> types = new HashSet<String>();

        String dataset;
        Map.Entry<Pair<String, String>, BackgroundCoverageResolver> pending;
        for(DatasetDescriptor desc : descs) {
            dataset = desc.getName();
            infoCacheKeyIter = this.infoCache.keySet().iterator();
            while(infoCacheKeyIter.hasNext()) {
                if(Objects.equals(dataset, infoCacheKeyIter.next().first))
                    infoCacheKeyIter.remove();
            }
            
            pendingCoverageEntryIter = this.pendingCoverages.entrySet().iterator();
            while(infoCacheKeyIter.hasNext()) {
                pending = pendingCoverageEntryIter.next();
                if(Objects.equals(dataset, pending.getKey().first)) {
                    pendingCoverageEntryIter.remove();
                    pending.getValue().cancel();
                }
            }

            types.addAll(desc.getImageryTypes());
        }
        
        for(String type : types) {
            infoCacheKeyIter = this.infoCache.keySet().iterator();
            while(infoCacheKeyIter.hasNext()) {
                if(Objects.equals(type, infoCacheKeyIter.next().second))
                    infoCacheKeyIter.remove();
            }
            
            pendingCoverageEntryIter = this.pendingCoverages.entrySet().iterator();
            while(infoCacheKeyIter.hasNext()) {
                pending = pendingCoverageEntryIter.next();
                if(Objects.equals(type, pending.getKey().second)) {
                    pendingCoverageEntryIter.remove();
                    pending.getValue().cancel();
                }
            }
        }
        
        if(notify) {
            Log.d(TAG, "invalidate geometry invalidated");
            this.dispatchDataStoreContentChangedNoSync();
        }
    }

    private QueryInfoSpec validateQueryInfoSpecNoSync(String dataset, String type) {
        DatasetDescriptorCursor result = null;
        try {
            Collection<String> nameArg = null;
            Collection<String> typeArg = null;
            if(dataset == null) {
                typeArg = Collections.singleton(type);
            } else if(type == null) {
                nameArg = Collections.singleton(dataset);
            } else {
                nameArg = Collections.singleton(dataset);
                typeArg = Collections.singleton(type);
            }
            
            DatasetQueryParameters params = new DatasetQueryParameters();
            params.names = nameArg;
            params.imageryTypes = typeArg;

            result = this.queryDatasets(params);
            
            if(!result.moveToNext())
                return null;
                
            QueryInfoSpec spec = new QueryInfoSpec();
            final Pair<String, String> infoSpecKey = createPair(dataset, type);

            DatasetDescriptor desc;
            
            desc = result.get();
            
            spec.minResolution = desc.getMinResolution(type);
            spec.maxResolution = desc.getMaxResolution(type);
            spec.coverage = desc.getCoverage(type);
            spec.hasRemote |= desc.isRemote();
            spec.count++;
            
            if(result.moveToNext()) {
                GeometryCollection scratch = new GeometryCollection(2);
                scratch.addGeometry(spec.coverage);
                spec.coverage = scratch;
                
                double minRes;
                double maxRes;
                do {
                    desc = result.get();
                    
                    minRes = desc.getMinResolution(type);
                    maxRes = desc.getMaxResolution(type);
                    
                    if(minRes > spec.minResolution)
                        spec.minResolution = minRes;
                    if(maxRes < spec.maxResolution)
                        spec.maxResolution = maxRes;
                    ((GeometryCollection)spec.coverage).addGeometry(desc.getCoverage(type));
                    spec.hasRemote |= desc.isRemote();
                    spec.count++;
                } while(result.moveToNext());
                
                if(RESOLVE_COLLECTION_COVERAGES && !spec.hasRemote) {
                    if(!this.pendingCoverages.containsKey(infoSpecKey)) {
                        BackgroundCoverageResolver resolver = new BackgroundCoverageResolver(dataset, type, (GeometryCollection)spec.coverage);
                        this.pendingCoverages.put(infoSpecKey, resolver);
                        
                        Thread t = new Thread(resolver, TAG + "-Resolver");
                        t.setPriority(Thread.MIN_PRIORITY);
                        t.start();
                    }
                }
            }

            this.infoCache.put(infoSpecKey, spec);
            return spec;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**************************************************************************/
    // Local Raster Data Store

    @Override
    protected boolean isModifiable() {
        return true;
    }

    @Override
    protected void addImpl(File file, Set<DatasetDescriptor> layers, File layerDir) throws IOException {
        this.layersDb.addLayers(file, layers, layerDir, new GenerateCurrency(layers));
        
        this.invalidateCacheInfo(layers, false);
    }

    @Override
    protected void removeImpl(File file) {
        try {
            Collection<DatasetDescriptor> descs = this.layersDb.getLayers(file);
            this.invalidateCacheInfo(descs, false);
        } catch(IOException e) {
            // XXX - ouch -- we failed to get the descriptors so we'll just nuke
            //       the whole cache!
            this.infoCache.clear();
            this.layerRefs.clear();
            
            for(BackgroundCoverageResolver resolver : this.pendingCoverages.values())
                resolver.cancel();
            this.pendingCoverages.clear();
        }
        
        this.layersDb.deleteCatalog(file);
        // XXX - really only need to remove the one...
        this.layerRefs.clear();
    }
    
    @Override
    protected void clearImpl() {
        this.layersDb.deleteAll();
        this.infoCache.clear();
        this.layerRefs.clear();
        
        for(BackgroundCoverageResolver resolver : this.pendingCoverages.values())
            resolver.cancel();
        this.pendingCoverages.clear();
    }
    
    @Override
    protected boolean containsImpl(File file) {
        if(this.layersDb == null)
            return false;

        LayersDatabase.CatalogCursor result = null;
        try {
            result = this.layersDb.queryCatalog(file);
            return result.moveToNext();
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    protected File getFileNoSync(DatasetDescriptor info) {
        if(this.layersDb == null)
            return null;

        LayersDatabase.LayerCursor result = null;
        try {
            result = this.layersDb.queryLayers(info.getName());
            DatasetDescriptor layerInfo;
            while(result.moveToNext()) {
                layerInfo = result.getLayerInfo();
                if(layerInfo != null && layerInfo.equals(info))
                    return new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(result.getPath()));
            }
            return null;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public synchronized FileCursor queryFiles() {
        if(this.layersDb == null)
            return new FileCursor(CursorIface.EMPTY) {
                @Override
                public File getFile() { return null; }
            };

        return new FileCursorImpl(this.layersDb.queryCatalog());
    }

    /**************************************************************************/
    // Disposable

    @Override
    public synchronized void dispose() {
        for(BackgroundCoverageResolver resolver : this.pendingCoverages.values())
            resolver.cancel();
        this.pendingCoverages.clear();

        this.layerRefs.clear();

        if(this.layersDb != null) {
            this.layersDb.close();
            this.layersDb = null;
        }
    }

    /**************************************************************************/
    // Raster Data Store

    private SelectionBuilder buildSelection(DatasetQueryParameters params) {
        // build selection
        SelectionBuilder selection = new SelectionBuilder();

        if(params.names != null) {
            selection.beginCondition();
            selection.appendIn(LayersDatabase.COLUMN_LAYERS_NAME, params.names);
        }
        if(params.providers != null) {
            selection.beginCondition();
            selection.appendIn(LayersDatabase.COLUMN_LAYERS_PROVIDER, params.providers);
        }
        if(params.datasetTypes != null) {
            selection.beginCondition();
            selection.appendIn(LayersDatabase.COLUMN_LAYERS_DATASET_TYPE, params.datasetTypes);
        }
        
        if(params.imageryTypes != null) {
            selection.beginCondition();
            selection.append(LayersDatabase.COLUMN_LAYERS_ID);
            selection.append(" IN (SELECT DISTINCT ");
            selection.append(LayersDatabase.COLUMN_IMAGERY_TYPES_LAYER_ID);
            selection.append(" FROM ");
            selection.append(LayersDatabase.TABLE_IMAGERY_TYPES);
            selection.append(" WHERE ");
            selection.appendIn(LayersDatabase.COLUMN_IMAGERY_TYPES_NAME, params.imageryTypes);
            selection.append(") ");
        }

        if(params.spatialFilter != null) {
            if(params.spatialFilter instanceof DatasetQueryParameters.RegionSpatialFilter) {
                DatasetQueryParameters.RegionSpatialFilter roi = (DatasetQueryParameters.RegionSpatialFilter)params.spatialFilter;

                if(roi.isValid()) {
                    selection.beginCondition();
/*                    
                    selection.append("(Intersects(" + LayersDatabase.COLUMN_LAYERS_COVERAGE + ", BuildMBR(?, ?, ?, ?, 4326)) = 1)");
        
                    selection.addArg(String.valueOf(roi.upperLeft.getLongitude()));
                    selection.addArg(String.valueOf(roi.lowerRight.getLatitude()));
                    selection.addArg(String.valueOf(roi.lowerRight.getLongitude()));
                    selection.addArg(String.valueOf(roi.upperLeft.getLatitude()));
*/
                    selection.append("(Intersects(" + LayersDatabase.COLUMN_LAYERS_COVERAGE + ", BuildMBR(" + String.valueOf(roi.upperLeft.getLongitude()) + ", " + String.valueOf(roi.lowerRight.getLatitude()) + ", " + String.valueOf(roi.lowerRight.getLongitude()) + ", " + String.valueOf(roi.upperLeft.getLatitude()) + ", 4326)) = 1)");
                    
                } else {
                    throw new IllegalArgumentException();
                }
            } else if(params.spatialFilter instanceof DatasetQueryParameters.PointSpatialFilter) {
                DatasetQueryParameters.PointSpatialFilter point = (DatasetQueryParameters.PointSpatialFilter)params.spatialFilter;

                if(point.isValid()) {
                    selection.beginCondition();
                    selection.append(LayersDatabase.COLUMN_LAYERS_MIN_LAT);
                    selection.append(" <= ? AND ");
                    selection.append(LayersDatabase.COLUMN_LAYERS_MAX_LAT);
                    selection.append(" >= ? AND ");
                    selection.append(LayersDatabase.COLUMN_LAYERS_MIN_LON);
                    selection.append(" <= ? AND ");
                    selection.append(LayersDatabase.COLUMN_LAYERS_MAX_LON);
                    selection.append(" >= ?");
        
                    selection.addArg(String.valueOf(point.point.getLatitude()));
                    selection.addArg(String.valueOf(point.point.getLatitude()));
                    selection.addArg(String.valueOf(point.point.getLongitude()));
                    selection.addArg(String.valueOf(point.point.getLongitude()));
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
        
        if(!Double.isNaN(params.minGsd)) {
            selection.beginCondition();
            selection.append(LayersDatabase.COLUMN_LAYERS_MIN_GSD);
            selection.append(" >= ?");
            selection.addArg(String.valueOf(params.minGsd));
        }
        if(!Double.isNaN(params.maxGsd)) {
            selection.beginCondition();
            selection.append(LayersDatabase.COLUMN_LAYERS_MAX_GSD);
            selection.append(" <= ?");
            selection.addArg(String.valueOf(params.maxGsd));
        }
        
        if(params.remoteLocalFlag != null) {
            selection.beginCondition();
            selection.append(LayersDatabase.COLUMN_LAYERS_REMOTE);
            selection.append(" = ?");
            switch(params.remoteLocalFlag) {
                case REMOTE :
                    selection.addArg("1");
                    break;
                case LOCAL :
                    selection.addArg("0");
                    break;
                default :
                    throw new IllegalStateException();
            }
        }
        return selection;
    }

    @Override
    public DatasetDescriptorCursor queryDatasets(DatasetQueryParameters params) {
        if(params == null)
            params = new DatasetQueryParameters();
        
        // build selection
        SelectionBuilder selectionBuilder = buildSelection(params);

        CursorIface result;
        if(selectionBuilder == null) {
            result = null;
        } else {
            // orderby
            String orderBy = null;
            if (params.order != null) {
                if (params.order.size() < 1)
                    throw new IllegalArgumentException();

                StringBuilder stringBuilder = new StringBuilder();
                Iterator<DatasetQueryParameters.Order> iter = params.order.iterator();
                stringBuilder.append(ORDER_FIELD_TO_LAYERDB_COL.get(iter.next()));

                while (iter.hasNext()) {
                    stringBuilder.append(", ");
                    stringBuilder.append(ORDER_FIELD_TO_LAYERDB_COL.get(iter.next()));
                }
                orderBy = stringBuilder.toString();
            }

            String limitArg = null;
            if (params.limit > 0)
                limitArg = String.valueOf(params.limit + " OFFSET " + params.offset);

            // execute query

            synchronized (this) {
                if (this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    result = CursorIface.EMPTY;
                } else {
                    result = this.layersDb.query(LayersDatabase.TABLE_LAYERS,
                            new String[]{
                                    LayersDatabase.COLUMN_LAYERS_ID,
                                    LayersDatabase.COLUMN_LAYERS_INFO},
                            selectionBuilder.getSelection(),
                            selectionBuilder.getArgs(),
                            null,
                            null,
                            orderBy,
                            limitArg);
                }
            }
        }
        return new LayerCursorImpl(result, 1, 0);
    }
    
    @Override
    public int queryDatasetsCount(DatasetQueryParameters params) {
        // build selection
        SelectionBuilder selectionBuilder = buildSelection(params);
        if(selectionBuilder == null)
            return 0;

        // execute query
        CursorIface result = null;
        try {
            synchronized(this) {
                if(this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    return 0;
                }
                result = this.layersDb.query(LayersDatabase.TABLE_LAYERS,
                        new String[]{"Count(1)"},
                        selectionBuilder.getSelection(),
                        selectionBuilder.getArgs(),
                        null,
                        null,
                        null,
                        null);
            }
            
            if(!result.moveToNext())
                return 0;
            return result.getInt(0);
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public Collection<String> getDatasetNames() {
        LayersDatabase.LayerCursor result = null;
        try {
            synchronized(this) {
                if(this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    return Collections.<String>emptySet();
                }
                result = this.layersDb.queryLayers();
            }

            Set<String> retval = new TreeSet<String>();
            while(result.moveToNext())
                retval.add(result.getName());
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public Collection<String> getImageryTypes() {
        LayersDatabase.LayerCursor result = null;
        try {
            synchronized(this) {
                if(this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    return Collections.<String>emptySet();
                }
                result = this.layersDb.queryLayers();
            }
            DatasetDescriptor layerInfo;

            Set<String> retval = new TreeSet<String>();
            while(result.moveToNext()) {
                layerInfo = result.getLayerInfo();
                if(layerInfo != null)
                    retval.addAll(layerInfo.getImageryTypes());
            }
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public Collection<String> getDatasetTypes() {
        LayersDatabase.LayerCursor result = null;
        try {
            synchronized(this) {
                if(this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    return Collections.<String>emptySet();
                }
                result = this.layersDb.queryLayers();
            }

            Set<String> retval = new TreeSet<String>();
            while(result.moveToNext())
                retval.add(result.getDatasetType());
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public Collection<String> getProviders() {
        LayersDatabase.LayerCursor result = null;
        try {
            synchronized(this) {
                if(this.layersDb == null) {
                    Log.w(TAG, "Warning: Data store has been disposed");
                    return Collections.<String>emptySet();
                }
                result = this.layersDb.queryLayers();
            }

            Set<String> retval = new TreeSet<String>();
            while(result.moveToNext())
                retval.add(result.getProvider());
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public synchronized Geometry getCoverage(String dataset, String type) {
        if(dataset == null && type == null)
            throw new IllegalArgumentException();

        QueryInfoSpec spec = this.infoCache.get(createPair(dataset, type));
        
        if(spec == null)
            spec = this.validateQueryInfoSpecNoSync(dataset, type);
        if(spec == null)
            return null;
        return spec.coverage;
    }

    @Override
    public synchronized double getMinimumResolution(String dataset, String type) {
        if(dataset == null && type == null)
            throw new IllegalArgumentException();
        
        QueryInfoSpec spec = this.infoCache.get(createPair(dataset, type));
        if(spec == null)
            spec = this.validateQueryInfoSpecNoSync(dataset, type);
        if(spec == null)
            return Double.NaN;
        return spec.minResolution;
    }
    
    @Override
    public synchronized double getMaximumResolution(String dataset, String type) {
        if(dataset == null && type == null)
            throw new IllegalArgumentException();
        
        QueryInfoSpec spec = this.infoCache.get(createPair(dataset, type));
        if(spec == null)
            spec = this.validateQueryInfoSpecNoSync(dataset, type);
        if(spec == null)
            return Double.NaN;
        return spec.maxResolution;
    }

    @Override
    public synchronized void refresh() {
        if(this.layersDb != null)
            this.layersDb.validateCatalog();
        this.layerRefs.clear();

        Log.d(TAG, "invalidate geometry refresh");
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    public synchronized boolean isAvailable() {
        return (this.layersDb != null);
    }

    /**************************************************************************/
    // Currency
    
    private static int getCodedStringLength(String s) {
        return 4 + (2*s.length());
    }

    private static ByteBuffer putString(ByteBuffer buffer, String s) {
        buffer.putInt(s.length());
        for(int i = 0; i < s.length(); i++)
            buffer.putChar(s.charAt(i));
        return buffer;
    }

    private static String getString(ByteBuffer buffer) {
        final int len = buffer.getInt();
        StringBuilder retval = new StringBuilder(len);
        for(int i = 0; i < len; i++)
            retval.append(buffer.getChar());
        return retval.toString();
    }

    static private final class ValidateCurrency implements CatalogCurrency {

        private ValidateCurrency() {}

        @Override
        public String getName() {
            return CURRENCY_NAME;
        }

        @Override
        public int getAppVersion() {
            return CURRENCY_VERSION;
        }

        @Override
        public byte[] getAppData(File file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValidApp(File f, int appVersion, byte[] appData) {
            if(appVersion != this.getAppVersion())
                return false;
            
            if(!IOProviderFactory.exists(f))
                return false;

            ByteBuffer parse = ByteBuffer.wrap(appData);
            parse.order(ByteOrder.LITTLE_ENDIAN);

            final int numSpis = parse.getInt();
            int parseVersion;
            DatasetDescriptorSpi spi;
            for(int i = 0; i < numSpis; i++) {
                parseVersion = parse.getShort()&0xFFFF;
                spi = DatasetDescriptorFactory2.getRegisteredSpi(getString(parse));
                if(spi != null && spi.parseVersion() != parseVersion)
                    return false;
            }
            
            final boolean isDirectory = ((parse.get()&0x01) == 0x01);
            if(IOProviderFactory.isDirectory(f) != isDirectory)
                return false;
            
            final long length = parse.getLong();
            final long lastModified = parse.getLong();
            final boolean currencyLargeDataset = (parse.get() != 0x00);
            final long numFiles = parse.getLong();
            
            // XXX - completely short-circuit if it's a "large dataset". Should
            //       revisit at some point to decide if this is really what we
            //       want to do
            if(currencyLargeDataset)
                return true;

            final FileSystemUtils.FileTreeData actual = new FileSystemUtils.FileTreeData();
            final boolean actualLargeDataset = !getFileData(f, actual, LARGE_DATASET_RECURSE_LIMIT);

            if(actualLargeDataset != currencyLargeDataset)
                return false;
            else if(currencyLargeDataset)
                return true;

            if(length > 0L && length != actual.size)
                return false;
            if(lastModified > 0L && lastModified != actual.lastModified)
                return false;
            if(numFiles > 0L && numFiles != actual.numFiles)
                return false;

            return true;
        }
    }
    
    private static final class GenerateCurrency implements CatalogCurrency {

        private final Collection<DatasetDescriptorSpi> infoSpis;
        private final boolean assumeLargeDataset;

        GenerateCurrency(Set<DatasetDescriptor> infos) {
            this.infoSpis = new LinkedList<DatasetDescriptorSpi>();
            DatasetDescriptorSpi spi;
            boolean largeDataset = true;
            for(DatasetDescriptor info : infos) {
                spi = DatasetDescriptorFactory2.getRegisteredSpi(info.getProvider());
                if(spi == null)
                    throw new IllegalStateException();
                this.infoSpis.add(spi);
                
                if(info instanceof MosaicDatasetDescriptor) {
                    final String numFrames = info.getExtraData("numFrames");
                    largeDataset &= ((numFrames != null) && (Integer.parseInt(numFrames) > LARGE_DATASET_RECURSE_LIMIT));
                } else {
                    largeDataset = false;
                }
            }
            
            this.assumeLargeDataset = (largeDataset && !infos.isEmpty());
        }

        @Override
        public String getName() {
            return CURRENCY_NAME;
        }

        @Override
        public int getAppVersion() {
            return CURRENCY_VERSION;
        }

        @Override
        public byte[] getAppData(File file) {
            int len = 4 + (2*this.infoSpis.size()) + 1 + 25;
            for(DatasetDescriptorSpi spi : this.infoSpis)
                len += getCodedStringLength(spi.getType());
            
            ByteBuffer retval = ByteBuffer.wrap(new byte[len]);
            retval.order(ByteOrder.LITTLE_ENDIAN);
            
            retval.putInt(this.infoSpis.size());
            for(DatasetDescriptorSpi spi : this.infoSpis) {
                retval.putShort((short)spi.parseVersion());
                putString(retval, spi.getType());
            }
            retval.put(IOProviderFactory.isDirectory(file) ? (byte)0x01 : (byte)0x00);
            
            final FileSystemUtils.FileTreeData fdt = new FileSystemUtils.FileTreeData();
            final boolean largeDataset = (this.assumeLargeDataset || !getFileData(file, fdt, LARGE_DATASET_RECURSE_LIMIT));

            retval.putLong(fdt.size);
            retval.putLong(fdt.lastModified);
            retval.put(largeDataset ? (byte)0x01 : (byte)0x00);
            retval.putLong(fdt.numFiles);

            if(retval.remaining() > 0)
                throw new IllegalStateException("remaining=" + retval.remaining());
            return retval.array();
        }

        @Override
        public boolean isValidApp(File f, int appVersion, byte[] appData) {
            throw new UnsupportedOperationException();
        }
    }
    
    /**************************************************************************/
    
    private final class LayerCursorImpl extends CursorWrapper implements DatasetDescriptorCursor {

        private final int infoCol;
        private final int idCol; 

        LayerCursorImpl(CursorIface impl, int infoCol, int idCol) {
            super(impl);
            
            this.infoCol = infoCol;
            this.idCol = idCol;
        }

        @Override
        public DatasetDescriptor get() {
            final long id = this.getLong(this.idCol);
            DatasetDescriptor info = PersistentRasterDataStore.this.layerRefs.get(Long.valueOf(id));
            if(info == null) {
                try {
                    info = DatasetDescriptor.decode(this.getBlob(this.infoCol));
                    PersistentRasterDataStore.this.layerRefs.put(Long.valueOf(id), info);
                } catch (IOException e) {
                    Log.d(TAG, "exception occurred: ", e);
                }
            }
            return info;
        }
    }

    private final static class FileCursorImpl extends FileCursor {

        protected FileCursorImpl(LayersDatabase.CatalogCursor filter) {
            super(filter);
        }

        @Override
        public File getFile() {
            return new File(
                 FileSystemUtils.sanitizeWithSpacesAndSlashes(((LayersDatabase.CatalogCursor)this.filter).getPath()));
        }
        
    }
    
    private final static class SelectionBuilder {
        private StringBuilder selection;
        private LinkedList<String> args;

        public SelectionBuilder() {
            this.selection = new StringBuilder();
            this.args = new LinkedList<String>();
        }

        public void beginCondition() {
            if (this.selection.length() > 0)
                this.selection.append(" AND ");
        }

        public void append(String s) {
            this.selection.append(s);
        }

        public void appendIn(String col, Collection<String> vals) {
            if(vals.size() == 1) {
                selection.append(col + " = ?");
            } else {
                selection.append(col + " IN (?");
                for(int i = 1; i < vals.size(); i++)
                    selection.append(", ?");
                selection.append(")");
            }
            this.args.addAll(vals);
        }

        public void addArg(String arg) {
            this.args.add(arg);
        }
        
        public void addArgs(Collection<String> args) {
            this.args.addAll(args);
        }

        public String getSelection() {
            if (this.selection.length() < 1)
                return null;
            return this.selection.toString();
        }
        
        public String[] getArgs() {
            if(this.args.size() < 1)
                return null;
            return this.args.toArray(new String[0]);
        }
    }

    private static boolean getFileData(File file, FileSystemUtils.FileTreeData data, long limit) {
        final long numFilesPtr = Unsafe.allocate(8);
        final long fileSizePtr = Unsafe.allocate(8);
        final long lastModifiedPtr = Unsafe.allocate(8);
        
        try {
            final boolean retval = getFileTreeData(file.getAbsolutePath(), numFilesPtr, fileSizePtr, lastModifiedPtr, LARGE_DATASET_RECURSE_LIMIT);
            data.numFiles = Unsafe.getLong(numFilesPtr);
            data.size = Unsafe.getLong(fileSizePtr);
            data.lastModified = Unsafe.getLong(lastModifiedPtr);
            return retval;
        } finally {
            Unsafe.free(numFilesPtr);
            Unsafe.free(fileSizePtr);
            Unsafe.free(lastModifiedPtr);
        }
    }

    private static native boolean getFileTreeData(String path, long numFilesPtr, long fileSizePtr, long lastModifiedPtr, long limit);

    /**************************************************************************/

    private static class QueryInfoSpec {
        public boolean hasRemote;
        public double minResolution;
        public double maxResolution;
        public Geometry coverage;
        public int count;
    }

    private class BackgroundCoverageResolver implements Runnable {
        public final String dataset;
        public final String type;
        private final GeometryCollection geom;
        private boolean canceled;

        public BackgroundCoverageResolver(String dataset, String type, GeometryCollection geom) {
            this.dataset = dataset;
            this.type = type;
            this.geom = geom;
            
            this.canceled = false;
        }
        
        @Override
        public void run() {
            SpatialCalculator calc = null;
            try {
                if(this.canceled)
                    return;

                calc = new SpatialCalculator();
                calc.beginBatch();
                try {
                    if(this.canceled)
                        return;

                    
                    final Geometry union = calc.getGeometry(calc.unaryUnion(calc.createGeometry(this.geom)));
                    synchronized(PersistentRasterDataStore.this) {
                        if(this.canceled)
                            return;
                        PersistentRasterDataStore.this.geometryResolvedNoSync(this.dataset, this.type, union, true);
                        
                        // if we aren't marked 'canceled', then we must still be
                        // the current resolver -- no need to check instance
                        PersistentRasterDataStore.this.pendingCoverages.remove(createPair(this.dataset, this.type));
                    }
                } finally {
                    calc.endBatch(false);
                }
            } finally {
                if(calc != null)
                    calc.dispose();
            }
        }
        
        public void cancel() {
            this.canceled = true;
        }
    }
}
