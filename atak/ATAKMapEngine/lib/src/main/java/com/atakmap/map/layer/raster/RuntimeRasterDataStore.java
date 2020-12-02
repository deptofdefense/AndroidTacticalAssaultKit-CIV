package com.atakmap.map.layer.raster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.IteratorCursor;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.util.CascadingComparator;
import com.atakmap.util.Collections2;
import com.atakmap.util.Filter;

public class RuntimeRasterDataStore extends AbstractRasterDataStore {

    public final static Comparator<DatasetDescriptor> SORT_IDENTITY = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            return (a.hashCode() - b.hashCode());
        }
    }; 

    public final static Comparator<DatasetDescriptor> SORT_ID = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            if(a.getLayerId() < b.getLayerId())
                return -1;
            else if(a.getLayerId() > b.getLayerId())
                return 1;
            else
                return 0;
        }
    }; 

    public final static Comparator<DatasetDescriptor> SORT_GSD = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            if(a.getMaxResolution(null) < b.getMaxResolution(null))
                return -1;
            else if(a.getMaxResolution(null) > b.getMaxResolution(null))
                return 1;
            else
                return 0;
        }
    };

    public final static Comparator<DatasetDescriptor> SORT_NAME = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            return a.getName().compareTo(b.getName());
        }
    };
    
    public final static Comparator<DatasetDescriptor> SORT_PROVIDER = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            return a.getProvider().compareTo(b.getProvider());
        }
    };
    
    public final static Comparator<DatasetDescriptor> SORT_DATASET_TYPE = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor a, DatasetDescriptor b) {
            return a.getDatasetType().compareTo(b.getDatasetType());
        }
    };

    private int nextFreeId;

    private Map<String, Set<DatasetDescriptor>> nameIndex;
    private Map<String, Set<DatasetDescriptor>> imageryTypeIndex;
    private Map<String, Set<DatasetDescriptor>> datasetTypeIndex;
    private Map<String, Set<DatasetDescriptor>> providerIndex;
    private Map<DatasetQueryParameters.RemoteLocalFlag, Set<DatasetDescriptor>> remoteLocalFlagIndex;
    private Set<DatasetDescriptor> descriptors;

    public RuntimeRasterDataStore() {
        this.nameIndex = new HashMap<String, Set<DatasetDescriptor>>();
        this.imageryTypeIndex = new HashMap<String, Set<DatasetDescriptor>>();
        this.datasetTypeIndex = new HashMap<String, Set<DatasetDescriptor>>();
        this.providerIndex = new HashMap<String, Set<DatasetDescriptor>>();
        this.remoteLocalFlagIndex = new HashMap<DatasetQueryParameters.RemoteLocalFlag, Set<DatasetDescriptor>>();
        this.descriptors = Collections.newSetFromMap(new IdentityHashMap<DatasetDescriptor, Boolean>());
        
        this.nextFreeId = 1;
    }
    
    /**
     * Adds the specified {@link DatasetDescriptor} to the datastore. The
     * returned instance is the specific instance that will be retained by the
     * datastore and will have an updated ID.
     * 
     * @param desc  The descriptor to be added
     * 
     * @return  A new {@link DatasetDescriptor} with an updated ID.
     */
    public synchronized DatasetDescriptor add(DatasetDescriptor desc) {
        try {
            desc = DatasetDescriptor.decode(desc.encode(this.nextFreeId++));
        } catch(IOException e) {
            throw new IllegalStateException(e);
        }

        // populate indices
        put(this.nameIndex, desc.getName(), desc);
        for(String imageryType : desc.getImageryTypes())
            put(this.imageryTypeIndex, imageryType, desc);
        put(this.datasetTypeIndex, desc.getDatasetType(), desc);
        put(this.providerIndex, desc.getProvider(), desc);
        put(this.remoteLocalFlagIndex, desc.isRemote() ? DatasetQueryParameters.RemoteLocalFlag.REMOTE : DatasetQueryParameters.RemoteLocalFlag.LOCAL, desc);
        
        // add the descriptor
        this.descriptors.add(desc);
        
        return desc;
    }
    
    private void dropIndices(DatasetDescriptor desc) {
        // clear the indices
        remove(this.nameIndex, desc.getName(), desc);
        for(String imageryType : desc.getImageryTypes())
            remove(this.imageryTypeIndex, imageryType, desc);
        remove(this.datasetTypeIndex, desc.getDatasetType(), desc);
        remove(this.providerIndex, desc.getProvider(), desc);
        remove(this.remoteLocalFlagIndex, desc.isRemote() ? DatasetQueryParameters.RemoteLocalFlag.REMOTE : DatasetQueryParameters.RemoteLocalFlag.LOCAL, desc);
    }

    /**
     * Removes the specified {@link DatasetDescriptor} from the datastore. The
     * associated working directory is NOT deleted.
     * 
     * @param desc  The {@link DatasetDescriptor}
     */    
    public void remove(DatasetDescriptor desc) {
        this.remove(desc, false);
    }

    /**
     * Removes the specified {@link DatasetDescriptor} from the datastore.
     * 
     * @param desc              The {@link DatasetDescriptor}
     * @param deleteWorkingDir  <code>true</code> to delete the working
     *                          directory associated with the descriptor,
     *                          <code>false</code> otherwise.
     */
    public synchronized void remove(DatasetDescriptor desc, boolean deleteWorkingDir) {
        // clear the indices
        this.dropIndices(desc);

        // remove the descriptor
        this.descriptors.remove(desc);
        
        if(deleteWorkingDir)
            FileSystemUtils.deleteDirectory(desc.getWorkingDirectory(), false);
    }
    
    /**
     * Clears the content but DOES NOT delete the associated working
     * directories.
     */
    public void clear() {
        this.clear(false);
    }

    /**
     * Clears out the current contents of the datastore. Optionally deletes the
     * associated working directories.
     * 
     * @param deleteWorkingDir  <code>true</code> to delete the associated
     *                          working directories, <code>false</code>
     *                          otherwise.
     */
    public synchronized void clear(boolean deleteWorkingDir) {
        for(DatasetDescriptor desc : this.descriptors) {
            // clear the indices
            this.dropIndices(desc);
            if(deleteWorkingDir)
                FileSystemUtils.deleteDirectory(desc.getWorkingDirectory(), false);
        }
        
        this.descriptors.clear();
    }

    private CoverageInfo getCoverageInfo(String dataset, String type) {
        Set<DatasetDescriptor> descs = null;
        if(dataset != null)
            descs = this.nameIndex.get(dataset);
        else if(type != null)
            descs = this.imageryTypeIndex.get(type);
        else
            throw new IllegalArgumentException();
        
        if(descs == null)
            return null;
        
        Iterator<DatasetDescriptor> iter = descs.iterator();
        DatasetDescriptor desc;
        
        desc = iter.next();
        CoverageInfo retval = new CoverageInfo(desc.getCoverage(type),
                                               desc.getMinResolution(type),
                                               desc.getMaxResolution(type));
        
        while(iter.hasNext()) {
            desc = iter.next();
            
            if(desc.getMinResolution(type) > retval.minGsd)
                retval.minGsd = desc.getMinResolution(type);
            if(desc.getMaxResolution(type) < retval.maxGsd)
                retval.maxGsd = desc.getMaxResolution(type);
        }
        
        return retval;
    }
    
    @Override
    public synchronized DatasetDescriptorCursor queryDatasets(DatasetQueryParameters params) {
        if(params == null)
            params = new DatasetQueryParameters();
        
        boolean applyNames = (params.names != null);
        boolean applyProviders = (params.providers != null);
        boolean applyDatasetTypes = (params.datasetTypes != null);
        boolean applyImageryTypes = (params.imageryTypes != null);
        boolean applySpatialFilter = (params.spatialFilter != null);
        boolean applyGsd = (!Double.isNaN(params.minGsd) || !Double.isNaN(params.maxGsd));
        boolean applyRemoteLocal = (params.remoteLocalFlag != null);

        final int numNamesIdxHits = applyNames ? cost(params.names, this.nameIndex) : Integer.MAX_VALUE;
        final int numProvidersIdxHits = applyProviders ? cost(params.providers, this.providerIndex) : Integer.MAX_VALUE;
        final int numDatasetTypesIdxHits = applyDatasetTypes ? cost(params.datasetTypes, this.datasetTypeIndex) : Integer.MAX_VALUE;
        final int numImageryTypesIdxHits = applyImageryTypes ? cost(params.imageryTypes, this.imageryTypeIndex) : Integer.MAX_VALUE;
        final int numSpatialFilterHits = applySpatialFilter ? Integer.MAX_VALUE : Integer.MAX_VALUE;
        final int numRemoteLocalIdxHits = applyRemoteLocal ? cost(params.remoteLocalFlag, this.remoteLocalFlagIndex) : Integer.MAX_VALUE;
        final int numGsdHits = applyGsd ? Integer.MAX_VALUE : Integer.MAX_VALUE;
        
        final int minIndexHits = MathUtils.min(new int[] {numNamesIdxHits,
                                                numProvidersIdxHits,
                                                numDatasetTypesIdxHits,
                                                numImageryTypesIdxHits,
                                                numSpatialFilterHits,
                                                numRemoteLocalIdxHits,
                                                this.descriptors.size()});

        List<DatasetDescriptor> descs = new LinkedList<DatasetDescriptor>();
        if(applyNames && numNamesIdxHits == minIndexHits) {
            for(String key : params.names) {
                Set<DatasetDescriptor> hits = this.nameIndex.get(key);
                if(hits != null)
                    descs.addAll(hits);
            }
            applyNames = false;
        } else if(applyProviders && numProvidersIdxHits == minIndexHits) {
            for(String key : params.providers) {
                Set<DatasetDescriptor> hits = this.providerIndex.get(key);
                if(hits != null)
                    descs.addAll(hits);
            }
            applyProviders = false;
        } else if(applyDatasetTypes && numDatasetTypesIdxHits == minIndexHits) {
            for(String key : params.datasetTypes) {
                Set<DatasetDescriptor> hits = this.datasetTypeIndex.get(key);
                if(hits != null)
                    descs.addAll(hits);
            }
            applyDatasetTypes = false;
        } else if(applyImageryTypes && numImageryTypesIdxHits == minIndexHits) {
            for(String key : params.imageryTypes) {
                Set<DatasetDescriptor> hits = this.imageryTypeIndex.get(key);
                if(hits != null)
                    descs.addAll(hits);
            }
            applyImageryTypes = false;
        } else if(applySpatialFilter && numSpatialFilterHits == minIndexHits) {
            // XXX - 
            descs.addAll(this.descriptors);
        } else if(applyGsd && numGsdHits == minIndexHits) {
            // XXX - 
            descs.addAll(this.descriptors);
        } else if(applyRemoteLocal && numRemoteLocalIdxHits == minIndexHits) {
            Set<DatasetDescriptor> hits = this.remoteLocalFlagIndex.get(params.remoteLocalFlag);
            if(hits != null)
                descs.addAll(hits);
            applyRemoteLocal = false;
        } else {
            // only sort/offset/limit options applied
            descs.addAll(this.descriptors);
        }
        
        // XXX - consider making filters sortable by cost

        // filtering
        if(applyRemoteLocal && !descs.isEmpty()) {
            filter(new RemoteLocalFilter(params.remoteLocalFlag), descs.iterator());
        }
        if(applyNames && !descs.isEmpty()) {
            filter(new NameFilter(params.names), descs.iterator());
        }
        if(applyProviders && !descs.isEmpty()) {
            filter(new ProviderFilter(params.providers), descs.iterator());
        }
        if(applyDatasetTypes && !descs.isEmpty()) {
            filter(new DatasetTypeFilter(params.datasetTypes), descs.iterator());
        }
        if((applyImageryTypes || applySpatialFilter || applyGsd) && !descs.isEmpty()) {
            if(applySpatialFilter || applyGsd) {
                filter(new CoverageFilter(
                            params.imageryTypes,
                            getSpatialFilterGeometry(params.spatialFilter),
                            params.minGsd,
                            params.maxGsd),
                        descs.iterator());
            } else {
                filter(new ImageryTypeFilter(params.imageryTypes), descs.iterator());
            }
        }
        
        
        // sorting
        ArrayList<Comparator<DatasetDescriptor>> sort = new ArrayList<Comparator<DatasetDescriptor>>(params.order.size() + 1);
        for(DatasetQueryParameters.Order order : params.order) {
            if(order == DatasetQueryParameters.GSD.INSTANCE) {
                sort.add(SORT_GSD);
            } else if(order == DatasetQueryParameters.Name.INSTANCE) {
                sort.add(SORT_NAME);
            } else if(order == DatasetQueryParameters.Provider.INSTANCE) {
                sort.add(SORT_PROVIDER);
            } else if(order == DatasetQueryParameters.Type.INSTANCE) {
                sort.add(SORT_DATASET_TYPE);
            } else {
                // XXX - warning
            }
        }
        
        if(!sort.isEmpty()) {
            sort.add(SORT_IDENTITY);

            // move into an ArrayList prior to sort if appropriate
            if(!(descs instanceof ArrayList))
                descs = new ArrayList<DatasetDescriptor>(descs);

            Comparator<DatasetDescriptor> comp;
            if(sort.size() == 1)
                comp = sort.get(0);
            else
                comp = new CascadingComparator<DatasetDescriptor>(sort);
            Collections.sort(descs, comp);
        }

        // apply limit/offset
        if(params.limit != 0) {
            final int limit = params.offset + params.limit;

            // remove off tail first
            if(descs instanceof LinkedList) {
                while(descs.size() > limit) {
                    ((LinkedList<DatasetDescriptor>)descs).removeLast();
                }
            } else {
                while(true) {
                    int size = descs.size();
                    if(size <= limit)
                        break;

                    descs.remove(size-1);
                }
            }
        }
        Iterator<DatasetDescriptor> result = descs.iterator();
        if(params.offset != 0) {
            int offset = 0;
            while(offset < params.offset && result.hasNext()) {
                result.next();
                offset++;
            }
        }
        
        return new CursorImpl(result);
    }

    @Override
    public int queryDatasetsCount(DatasetQueryParameters params) {
        DatasetDescriptorCursor result = null;
        try {
            int retval = 0;
            result = this.queryDatasets(params);
            while(result.moveToNext())
                retval++;
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public synchronized Collection<String> getDatasetNames() {
        return new HashSet<String>(this.nameIndex.keySet());
    }

    @Override
    public synchronized Collection<String> getImageryTypes() {
        return new HashSet<String>(this.imageryTypeIndex.keySet());
    }

    @Override
    public Collection<String> getDatasetTypes() {
        return new HashSet<String>(this.datasetTypeIndex.keySet());
    }

    @Override
    public Collection<String> getProviders() {
        return new HashSet<String>(this.providerIndex.keySet());
    }

    @Override
    public Geometry getCoverage(String dataset, String type) {
        CoverageInfo cov = getCoverageInfo(dataset, type);
        if(cov == null)
            return null;
        return cov.geometry;
    }

    @Override
    public double getMinimumResolution(String dataset, String type) {
        CoverageInfo cov = getCoverageInfo(dataset, type);
        if(cov == null)
            return Double.NaN;
        return cov.minGsd;
    }

    @Override
    public double getMaximumResolution(String dataset, String type) {
        CoverageInfo cov = getCoverageInfo(dataset, type);
        if(cov == null)
            return Double.NaN;
        return cov.maxGsd;
    }

    @Override
    public void refresh() {}

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public synchronized void dispose() {
        this.nameIndex.clear();
        this.imageryTypeIndex.clear();
        this.datasetTypeIndex.clear();
        this.providerIndex.clear();
    }

    /**************************************************************************/

    private static <K> int cost(K key, Map<K, Set<DatasetDescriptor>> index) {
        final Set<DatasetDescriptor> s = index.get(key);
        if(s == null)
            return 0;
        return s.size();
    }
    
    private static <K> int cost(Collection<K> keys, Map<K, Set<DatasetDescriptor>> index) {
        int retval = 0;
        for(K key : keys)
            retval += cost(key, index);
        return retval;
    }
    
    private static <K, V> void put(Map<K, Set<V>> map, K key, V value) {
        Set<V> values = map.get(key);
        if(values == null)
            map.put(key, values=new HashSet<V>());
        values.add(value);
    }
    
    private static <K, V> void remove(Map<K, Set<V>> map, K key, V value) {
        Set<V> values = map.get(key);
        if(values == null)
            return;
        values.remove(value);
        if(values.isEmpty())
            map.remove(key);
    }

    private static class CoverageInfo {
        public Geometry geometry;
        public double minGsd;
        public double maxGsd;
        
        public CoverageInfo() {
            this(null, Double.NaN, Double.NaN);
        }
        
        public CoverageInfo(Geometry geometry, double minGsd, double maxGsd) {
            this.geometry = geometry;
            this.minGsd = minGsd;
            this.maxGsd = maxGsd;
        }
    }
    
    /**************************************************************************/

    private static class CursorImpl extends IteratorCursor<DatasetDescriptor> implements DatasetDescriptorCursor {

        public CursorImpl(Iterator<DatasetDescriptor> iter) {
            super(iter);
        }

        @Override
        public DatasetDescriptor get() {
            return this.getRowData();
        }
    }

    private static void filter(Filter<DatasetDescriptor> filter, Iterator<DatasetDescriptor> iter) {
        while(iter.hasNext())
            if(!filter.accept(iter.next()))
                iter.remove();
    }

    private static abstract class StringFieldFilter implements Filter<DatasetDescriptor> {
        private final Collection<String> test;
        
        public StringFieldFilter(Collection<String> test) {
            if(test == null)
                throw new NullPointerException();
            this.test = test;
        }

        @Override
        public boolean accept(DatasetDescriptor arg) {
            return this.test.contains(this.getValue(arg));
        }
        
        protected abstract String getValue(DatasetDescriptor arg); 
    }

    public final static class NameFilter extends StringFieldFilter {
        public NameFilter(Collection<String> name) {
            super(name);
        }

        @Override
        protected String getValue(DatasetDescriptor arg) {
            return arg.getName();
        }
    }
    
    public final static class ProviderFilter extends StringFieldFilter {
        public ProviderFilter(Collection<String> provider) {
            super(provider);
        }

        @Override
        protected String getValue(DatasetDescriptor arg) {
            return arg.getProvider();
        }
    }
    
    public final static class DatasetTypeFilter extends StringFieldFilter {
        public DatasetTypeFilter(Collection<String> datasetType) {
            super(datasetType);
        }

        @Override
        public String getValue(DatasetDescriptor arg) {
            return arg.getDatasetType();
        }
    }
    
    public final static class ImageryTypeFilter implements Filter<DatasetDescriptor> {
        private final Collection<String> imageryTypes;
        
        public ImageryTypeFilter(Collection<String> imageryTypes) {
            if(imageryTypes == null)
                throw new NullPointerException();
            this.imageryTypes = imageryTypes;
        }

        @Override
        public boolean accept(DatasetDescriptor arg) {
            return Collections2.containsAny(arg.getImageryTypes(), this.imageryTypes);
        }
    }
    
    public final static class CoverageFilter implements Filter<DatasetDescriptor> {
        private final Collection<String> imageryTypes;
        private final Geometry geometry;
        private final double maxGsd;
        private final double minGsd;
        
        public CoverageFilter(Collection<String> imageryTypes, Geometry geometry, double minGsd, double maxGsd) {
            if(geometry == null && Double.isNaN(minGsd) && Double.isNaN(maxGsd))
                throw new IllegalArgumentException();

            this.imageryTypes = (imageryTypes != null) ? imageryTypes : Collections.<String>singleton(null);
            this.geometry = geometry;
            this.minGsd = minGsd;
            this.maxGsd = maxGsd;            
        }
        
        @Override
        public boolean accept(DatasetDescriptor arg) {
            boolean retval = false;
            for(String imageryType : this.imageryTypes)
                retval |= acceptImpl(arg, imageryType);
            return retval;
        }
        
        private boolean acceptImpl(DatasetDescriptor arg, String imageryType) {
            boolean retval = true;
            if(this.geometry != null) {
                Geometry cov = arg.getCoverage(imageryType);
                if(cov == null)
                    return false;
                
                Envelope mbb1 = cov.getEnvelope();
                Envelope mbb2 = this.geometry.getEnvelope();
                
                // XXX - more rigorous intersect?
                retval &= Rectangle.intersects(mbb1.minX,
                                               mbb1.minY,
                                               mbb1.maxX,
                                               mbb1.maxY,
                                               mbb2.minX,
                                               mbb2.minY,
                                               mbb2.maxX,
                                               mbb2.maxY);
            }
            if(!Double.isNaN(this.maxGsd)) {
                retval &= arg.getMaxResolution(imageryType) <= this.maxGsd;
            }
            if(!Double.isNaN(this.minGsd)) {
                retval &= arg.getMinResolution(imageryType) >= this.minGsd;
            }
            
            return retval;
        }
    }
    
    public final static class RemoteLocalFilter implements Filter<DatasetDescriptor> {

        private final DatasetQueryParameters.RemoteLocalFlag test;
        
        public RemoteLocalFilter(DatasetQueryParameters.RemoteLocalFlag test) {
            if(test == null)
                throw new NullPointerException();
            this.test = test;
        }

        @Override
        public boolean accept(DatasetDescriptor arg) {
            switch(this.test) {
                case LOCAL :
                    return !arg.isRemote();
                case REMOTE :
                    return arg.isRemote();
                default :
                    throw new IllegalStateException();
            }
        }        
    }
}
