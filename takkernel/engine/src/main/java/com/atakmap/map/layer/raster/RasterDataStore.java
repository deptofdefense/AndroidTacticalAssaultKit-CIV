package com.atakmap.map.layer.raster;

import java.util.ArrayList;
import java.util.Collection;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.RowIterator;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.util.Disposable;

/**
 * A data store for raster data.
 * 
 * @author Developer
 */
public interface RasterDataStore extends Disposable {

    /**
     * Callback interface providing notification when the content of the
     * data store has changed.
     * 
     * @author Developer
     */
    public static interface OnDataStoreContentChangedListener {
        /**
         * This method is invoked when the content in the data store has
         * changed.
         * 
         * @param dataStore The data store
         */
        public void onDataStoreContentChanged(RasterDataStore dataStore);
    }
    
    /**
     * Queries all layers in the data store.
     * 
     * @return  A cursor to all layers in the data store.
     */
    public DatasetDescriptorCursor queryDatasets();
    
    /**
     * Queries the datastore for all datasets matching the specified criteria.
     * 
     * @param params    The query parameters.
     *
     * @return  The results matching the specified query criteria.
     * 
     * @throws NullPointerException If <code>params</code> is <code>null</code>
     */
    public DatasetDescriptorCursor queryDatasets(DatasetQueryParameters params);
    
    /**
     * Returns the number of results for the specified query. Adheres to the
     * same basic contract as
     * {@link #queryDatasets(DatasetQueryParameters)}.
     * 
     * @param params    The query parameters. If <code>null</code> all datasets
     *                  are returned, else, only those satisfying the criteria
     *                  of the specified parameters are returned.
     *
     * @return  The number of results matching the query criteria.
     */
    public int queryDatasetsCount(DatasetQueryParameters params);

    /**
     * Returns the names of all datasets available in the data store.
     * 
     * @return  The names of all datasets available in the data store.
     */
    public Collection<String> getDatasetNames();
    
    /**
     * Returns the imagery types available across all datasets in the data
     * store.
     * @return  the imagery types available across all datasets in the data
     *          store.
     */
    public Collection<String> getImageryTypes();
    
    /**
     * Returns the data types available across all datasets in the data store.
     * 
     * @return  the data types available across all datasets in the data store.
     */
    public Collection<String> getDatasetTypes();
    
    /**
     * Returns the providers available across all datasets in the data store.
     * 
     * @return  the providers available across all datasets in the data store.
     */
    public Collection<String> getProviders();
    
    /**
     * Returns the coverage for the specified dataset and imagery type
     * combination.
     * 
     * <P>If <code>dataset</code> is non-<code>null</code> and <code>type</code>
     * is non-<code>null</code>, the returned
     * {@link com.atakmap.map.layer.feature.geometry.Geometry Geometry} will be
     * the coverage for that imagery type for the specified dataset. If
     * <code>dataset</code> is non-<code>null</code> and <code>type</code> is
     * <code>null</code>, the returned
     * {@link com.atakmap.map.layer.feature.geometry.Geometry Geometry} will be
     * the union of the coverages for all imagery types for that dataset. If
     * <code>dataset</code> is <code>null</code> and <code>type</code> is
     * non-<code>null</code>, the returned
     * {@link com.atakmap.map.layer.feature.geometry.Geometry Geometry} will be
     * the union of the coverage for that imagery type across all datasets.
     *  
     * 
     * @param dataset   The dataset name (may be <code>null</code>)
     * @param type      The imagery type (may be <code>null</code>)
     * 
     * @return  The coverage for the specified dataset and imagery type
     *          combination
     *          
     * @throws IllegalArgumentException if both <code>dataset</code> and
     *                                  <code>type</code> are <code>null</code>
     */
    public Geometry getCoverage(String dataset, String type);
    
    /**
     * Returns the minimum resolution for the specified dataset and imagery type
     * combination.
     * 
     * <P>If <code>dataset</code> is non-<code>null</code> and <code>type</code>
     * is non-<code>null</code>, the returned
     * resolution will be the minimum resolution for that imagery type for the
     * specified dataset. If <code>dataset</code> is non-<code>null</code> and
     * <code>type</code> is <code>null</code>, the returned resolution will be
     * the minimum of the resolutions for all imagery types for that dataset. If
     * <code>dataset</code> is <code>null</code> and <code>type</code> is
     * non-<code>null</code>, the returned resolution will be the minimum
     * resolution for that imagery type across all datasets.
     *  
     * 
     * @param dataset   The dataset name (may be <code>null</code>)
     * @param type      The imagery type (may be <code>null</code>)
     * 
     * @return  The minimum resolution for the specified dataset and imagery
     *          type combination
     *          
     * @throws IllegalArgumentException if both <code>dataset</code> and
     *                                  <code>type</code> are <code>null</code>
     */
    public double getMinimumResolution(String dataset, String type);
    
    /**
     * Returns the maximum resolution for the specified dataset and imagery type
     * combination.
     * 
     * <P>If <code>dataset</code> is non-<code>null</code> and <code>type</code>
     * is non-<code>null</code>, the returned
     * resolution will be the maximum resolution for that imagery type for the
     * specified dataset. If <code>dataset</code> is non-<code>null</code> and
     * <code>type</code> is <code>null</code>, the returned resolution will be
     * the maximum of the resolutions for all imagery types for that dataset. If
     * <code>dataset</code> is <code>null</code> and <code>type</code> is
     * non-<code>null</code>, the returned resolution will be the maximum
     * resolution for that imagery type across all datasets.
     *  
     * 
     * @param dataset   The dataset name (may be <code>null</code>)
     * @param type      The imagery type (may be <code>null</code>)
     * 
     * @return  The maximum resolution for the specified dataset and imagery
     *          type combination
     *          
     * @throws IllegalArgumentException if both <code>dataset</code> and
     *                                  <code>type</code> are <code>null</code>
     */
    public double getMaximumResolution(String dataset, String type);

    /**
     * Refreshes the data store. Any invalid entries are dropped.
     */
    public void refresh();
    
    /**
     * Returns a flag indicating whether or not the data store is currently
     * available. In the event that the data store resides on a remote server,
     * this method may return <code>false</code> when no connection with that
     * server can be established.
     * 
     * <P>This method should always return <code>false</code> after the
     * {@link #dispose()} method has been invoked.
     * 
     * @return  <code>true</code> if the data store is available,
     *          <code>false</code> otherwise.
     */
    public boolean isAvailable();

    /**
     * Adds the specified {@link OnDataStoreContentChangedListener}.
     * 
     * @param l The listener
     */
    public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);
    
    /**
     * Removes the specified {@link OnDataStoreContentChangedListener}.
     * 
     * @param l The listener
     */
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l);
    

    /**************************************************************************/
    
    /**
     * {@link CursorWrapper} subclass that provides direct access to the
     * {@link DatasetDescriptor} object described by the results.
     * 
     * @author Developer
     */
    public static interface DatasetDescriptorCursor extends RowIterator {

        /**
         * Returns the {@link DatasetDescriptor} corresponding to the current row.
         * 
         * @return  The {@link DatasetDescriptor} described by the current row.
         */
        public abstract DatasetDescriptor get();
    }

    /**************************************************************************/

    /**
     * Dataset query parameters. Specifies the common criteria datasets may be
     * queried against.
     * 
     * @author Developer
     */
    public static class DatasetQueryParameters {
        public enum RemoteLocalFlag {
            REMOTE,
            LOCAL,
        };

        /**
         * The accepted dataset names. If <code>null</code> all dataset names
         * are accepted.
         */
        public Collection<String> names;
        /**
         * The accepted dataset providers. If <code>null</code> all providers
         * are accepted.
         */
        public Collection<String> providers;
        /**
         * The accepted dataset types. If <code>null</code> all types are
         * accepted.
         */
        public Collection<String> datasetTypes;
        /**
         * The accepted imagery types. If <code>null</code> all types are
         * accepted. If non-<code>null</code> only those datasets that contain
         * one or more of the specified imagery types are accepted.
         */
        public Collection<String> imageryTypes;
        /**
         * The spatial filter to be applied to the results. If
         * non-<code>null</code>, only those datasets that satisfy the criteria
         * of the spatial filter will be returned.
         */
        public SpatialFilter spatialFilter;
        /**
         * The minimum GSD (resolution in meters-per-pixel). Any datasets with
         * a maximum resolution that is lower than the specified value will be
         * excluded (e.g. 10m data has a lower resolution than 5m data). A value
         * of {@link Double#NaN} may be specified to indicate that there is no
         * minimum GSD constraint.
         */
        public double minGsd;
        /**
         * The maximum GSD (resolution in meters-per-pixel). Any datasets with
         * a minimum resolution that is greater than the specified value will be
         * excluded (e.g. 5m data has a higher resolution than 10m data). A
         * value of {@link Double#NaN} may be specified to indicate that there
         * is no maximum GSD constraint.
         */
        public double maxGsd;
        /**
         * A flag indicating whether or not remote or local datasets should be
         * selected. If <code>null</code> all datasets may be selected.
         */
        public RemoteLocalFlag remoteLocalFlag; 
        /**
         * The ordering parameters, may be <code>null</code> to indicate no
         * ordering. An argument containing {@link GSD} and {@link Name} would
         * order the results first by resolution, and, in the event that the
         * resolution was equal, by dataset name. Note that the order of the
         * fields will be important.
         */
        public Collection<Order> order;
        /**
         * The limit on the number of results returned. If <code>0</code> there
         * shall be no limit.
         */
        public int limit;
        /**
         * The offset into the result list. This argument is only applicable if
         * {@link #limit} is greater-than <code>0</code>. 
         */
        public int offset;

        Object priv;
        
        /**
         * Creates a new instance. All fields are initialized to values such
         * that there will be no constraints on the results.
         */
        public DatasetQueryParameters() {
            this(null,
                 null,
                 null,
                 null,
                 null,
                 Double.NaN,
                 Double.NaN,
                 null,
                 null,
                 0,
                 0);
        }
        
        public DatasetQueryParameters(DatasetQueryParameters other) {
            this(other.names != null ? new ArrayList<String>(other.names) : null,
                 other.providers != null ? new ArrayList<String>(other.providers) : null,
                 other.datasetTypes != null ? new ArrayList<String>(other.datasetTypes) : null,
                 other.imageryTypes != null ? new ArrayList<String>(other.imageryTypes) : null,
                 other.spatialFilter,
                 other.minGsd,
                 other.maxGsd,
                 other.remoteLocalFlag,
                 other.order != null ? new ArrayList<Order>(other.order) : null,
                 other.limit,
                 other.offset);
        }
        
        private DatasetQueryParameters(Collection<String> names,
                                       Collection<String> providers,
                                       Collection<String> datasetTypes,
                                       Collection<String> imageryTypes,
                                       SpatialFilter spatialFilter,
                                       double minResolution,
                                       double maxResolution,
                                       RemoteLocalFlag remoteLocalFlag,
                                       Collection<Order> order,
                                       int limit,
                                       int offset) {

            this.names = names;
            this.providers = providers;
            this.datasetTypes = datasetTypes;
            this.imageryTypes = imageryTypes;
            this.spatialFilter = spatialFilter;
            this.minGsd = minResolution;
            this.maxGsd = maxResolution;
            this.remoteLocalFlag = remoteLocalFlag;
            this.order = order;
            this.limit = limit;
            this.offset = offset;
        }
        
        /**
         * Intersects this {@link DatasetQueryParameters} with another instance.
         * Intersection will be performed such that if a particular field is
         * defined by only of of <code>this</code> or <code>b</code>, the
         * defined field will be accepted. If a field is defined by both
         * <code>this</code> and <code>b</code>, the field will be modified such
         * that it is the intersection or more restrictive of the two. In this
         * manner, <code>this</code> can be used as a filter on <code>b</code>. 
         * 
         * @param other A {@link DatasetQueryParameters}
         * 
         * @return  The intersection of <code>a</code> and <code>b</code>.
         */
        public void intersect(DatasetQueryParameters other) {
            this.names = intersect(this.names, other.names);
            this.providers = intersect(this.providers, other.providers);
            this.datasetTypes = intersect(this.datasetTypes, other.datasetTypes);
            this.imageryTypes = intersect(this.imageryTypes, other.imageryTypes);

            if(this.spatialFilter != null && other.spatialFilter != null) {
                // XXX - 
            } else if(other.spatialFilter != null){
                this.spatialFilter = other.spatialFilter;
            }

            if(!Double.isNaN(this.minGsd) && !Double.isNaN(other.minGsd))
                this.minGsd = Math.min(this.minGsd, other.minGsd);
            else if(Double.isNaN(this.minGsd) && !Double.isNaN(other.minGsd))
                this.minGsd = other.minGsd;
            if(!Double.isNaN(this.maxGsd) && !Double.isNaN(other.maxGsd))
                this.maxGsd = Math.max(this.maxGsd, other.maxGsd);
            else if(Double.isNaN(this.maxGsd) && !Double.isNaN(other.maxGsd))
                this.maxGsd = other.maxGsd;

            this.remoteLocalFlag = null;
            // XXX - no meaningful way to make more restrictive outside of
            //       excluding both local and remote if 'a' and 'b' both specify
            //       different flags
            if(this.remoteLocalFlag == null && other.remoteLocalFlag != null)
                this.remoteLocalFlag = other.remoteLocalFlag;
            
            this.order = intersect(this.order, other.order);

            // XXX - we will always the limit/offset for the non-filter
            if(other.limit != 0) {
                this.limit = other.limit;
                this.offset = other.offset;
            }
        }

        /**********************************************************************/

        public static void clear(DatasetQueryParameters params) {
            params.names = null;
            params.providers = null;
            params.datasetTypes = null;
            params.imageryTypes = null;
            params.spatialFilter = null;
            params.minGsd = Double.NaN;
            params.maxGsd = Double.NaN;
            params.remoteLocalFlag = null;
            params.order = null;
            params.limit = 0;
            params.offset = 0;
        }
        
        public static DatasetQueryParameters intersect(DatasetQueryParameters a, DatasetQueryParameters b) {
            DatasetQueryParameters retval = new DatasetQueryParameters(a);
            retval.intersect(b);
            return retval;
        }

        private static <T> Collection<T> intersect(Collection<T> a, Collection<T> b) {
            if(a != null && b != null)
                a.retainAll(b);
            else if(b != null)
                a = b;
            return a;
        }

        /**********************************************************************/

        /**
         * A spatial filter.
         * 
         * @author Developer
         */
        public abstract static class SpatialFilter {
            SpatialFilter() {}
            
            abstract boolean isValid();
        }
        
        /**
         * All datasets intersecting the region of interest are accepted.
         *  
         * @author Developer
         */
        public final static class RegionSpatialFilter extends SpatialFilter {
            /** The upper-left corner of the region. */
            public GeoPoint upperLeft;
            /** The lower-right corner of the region. */
            public GeoPoint lowerRight;
            
            public RegionSpatialFilter(GeoPoint upperLeft, GeoPoint lowerRight) {
                this.upperLeft = upperLeft;
                this.lowerRight = lowerRight;
            }

            @Override
            boolean isValid() {
                return (this.upperLeft != null && this.lowerRight != null);
            }
        }
        
        /**
         * All datasets that contain the specified point will be selected.
         * 
         * @author Developer
         */
        public final static class PointSpatialFilter extends SpatialFilter {
            /** The point of interest. */
            public GeoPoint point;
            
            public PointSpatialFilter(GeoPoint point) {
                this.point = point;
            }

            @Override
            boolean isValid() {
                return (this.point != null);
            }
        }
        
        /**********************************************************************/
        
        /** Defines the ordering to be applied to the results. */
        public abstract static class Order {
            Order() {}
            
            boolean isValid() {
                return true;
            }
        }
        
        /** Orders the results by maximum GSD (resolution). */
        public final static class GSD extends Order {
            public final static GSD INSTANCE = new GSD();
            
            private GSD() {}
        }

        /** Orders the results by dataset name. */
        public final static class Name extends Order {
            public final static Name INSTANCE = new Name();
            
            private Name() {}
        }

        /** Orders the results by dataset provider. */
        public final static class Provider extends Order {
            public final static Provider INSTANCE = new Provider();
            
            private Provider() {}
        }
        
        /** Orders the results by dataset type */
        public final static class Type extends Order {
            public final static Type INSTANCE = new Type();
            
            private Type() {}
        }
    } // DatasetQueryParameters
    
} // RasterDataStore
