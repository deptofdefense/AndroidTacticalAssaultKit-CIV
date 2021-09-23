package com.atakmap.map.layer.raster;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.Point;

public abstract class AbstractRasterDataStore implements RasterDataStore {

    protected Set<OnDataStoreContentChangedListener> contentChangedListeners;

    protected AbstractRasterDataStore() {
        this.contentChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnDataStoreContentChangedListener, Boolean>());
    }

    /**************************************************************************/
    // Raster Data Store
    
    @Override
    public DatasetDescriptorCursor queryDatasets() {
        return this.queryDatasets(new DatasetQueryParameters());
    }

    @Override
    public synchronized void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        this.contentChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        this.contentChangedListeners.remove(l);
    }
    
    protected void dispatchDataStoreContentChangedNoSync() {
        for(OnDataStoreContentChangedListener l : this.contentChangedListeners)
            l.onDataStoreContentChanged(this);
    }
    
    @Override
    public Geometry getCoverage(String dataset, String type) {
        if(dataset == null && type == null)
            throw new IllegalArgumentException();
        
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
            
            Geometry retval = null;
            if(result.moveToNext())
                retval = result.get().getCoverage(type);
                
            if(result.moveToNext()) {
                GeometryCollection c = new GeometryCollection(2);
                c.addGeometry(retval);
                retval = c;
                
                do {
                    ((GeometryCollection)retval).addGeometry(result.get().getCoverage(type));
                } while(result.moveToNext());
            }

            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static Geometry getSpatialFilterGeometry(DatasetQueryParameters.SpatialFilter filter) {
        if(filter instanceof DatasetQueryParameters.RegionSpatialFilter) {
            DatasetQueryParameters.RegionSpatialFilter region = (DatasetQueryParameters.RegionSpatialFilter)filter;
            return DatasetDescriptor.createSimpleCoverage(
                    region.upperLeft,
                    new GeoPoint(region.upperLeft.getLatitude(), region.lowerRight.getLongitude()),
                    region.lowerRight,
                    new GeoPoint(region.lowerRight.getLatitude(), region.upperLeft.getLongitude()));
        } else if(filter instanceof DatasetQueryParameters.PointSpatialFilter) {
            DatasetQueryParameters.PointSpatialFilter point = (DatasetQueryParameters.PointSpatialFilter)filter;
            return new Point(point.point.getLongitude(), point.point.getLatitude());
        } else {
            return null;
        }
    }
}
