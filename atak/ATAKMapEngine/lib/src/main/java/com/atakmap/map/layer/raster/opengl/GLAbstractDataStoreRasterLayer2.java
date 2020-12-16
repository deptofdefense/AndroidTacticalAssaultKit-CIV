package com.atakmap.map.layer.raster.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.map.opengl.GLMapView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public abstract class GLAbstractDataStoreRasterLayer2 extends GLAsynchronousLayer<LinkedList<DatasetDescriptor>> implements RasterLayer2.OnSelectionChangedListener, RasterDataStore.OnDataStoreContentChangedListener, RasterDataAccessControl, RasterLayer2.OnSelectionVisibleChangedListener, RasterLayer2.OnSelectionTransparencyChangedListener {

    protected AbstractDataStoreRasterLayer2 subject;
    protected String selection;

    protected Collection<GLMapLayer3> renderable;
    
    protected GLAbstractDataStoreRasterLayer2(MapRenderer surface, AbstractDataStoreRasterLayer2 subject) {
        super(surface, subject);

        this.subject = subject;
        
        // XXX - sort ???
        this.renderable = new LinkedList<GLMapLayer3>();
    }
    
    protected abstract void updateTransparency(GLMapLayer3 layer);

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected void releaseImpl() {
        super.releaseImpl();

        this.renderable.clear();;
    }

    @Override
    protected Collection<GLMapLayer3> getRenderList() {
        return this.renderable;
    }

    @Override
    protected void resetPendingData(LinkedList<DatasetDescriptor> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(LinkedList<DatasetDescriptor> pendingData) {
        pendingData.clear();
    }

    @Override
    protected LinkedList<DatasetDescriptor> createPendingData() {
        // XXX - sorted lowest GSD to highest GSD (back to front render order)
        return new LinkedList<DatasetDescriptor>();
    }

    @Override
    protected boolean updateRenderableReleaseLists(LinkedList<DatasetDescriptor> pendingData) {
        Map<DatasetDescriptor, GLMapLayer3> swap = new HashMap<DatasetDescriptor, GLMapLayer3>();
        for(GLMapLayer3 l : this.renderable)
            swap.put(l.getInfo(), l);
        this.renderable.clear();

        GLMapLayer3 renderer;
        for(DatasetDescriptor info : pendingData) {
            renderer = swap.remove(info);
            if(renderer == null)
                renderer = GLMapLayerFactory.create3(this.renderContext, info);
            if(renderer != null) {
                this.renderable.add(renderer); 
                this.updateTransparency(renderer);
            }
        }
        
        // queue the release
        final Collection<GLMapLayer3> released = swap.values();
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                for(GLMapLayer3 renderable : released)
                    renderable.release();
            }
        });

        return true;
    }

    @Override
    protected ViewState newViewStateInstance() {
        return new State();
    }
    
    /**************************************************************************/
    // GL Layer

    @Override
    public synchronized void start() {
        super.start();
        
        this.subject.addOnSelectionChangedListener(this);
        this.selection = this.subject.getSelection();

        this.subject.getDataStore().addOnDataStoreContentChangedListener(this);
        this.subject.addOnSelectionVisibleChangedListener(this);
        this.subject.addOnSelectionTransparencyChangedListener(this);
        
        // raster data access
        this.renderContext.registerControl(this.subject, this);
    }
    
    @Override
    public synchronized void stop() {
        super.stop();
        
        this.subject.removeOnSelectionChangedListener(this);
        this.subject.getDataStore().removeOnDataStoreContentChangedListener(this);
        this.subject.removeOnSelectionVisibleChangedListener(this);
        this.subject.removeOnSelectionTransparencyChangedListener(this);

        // raster data access
        this.renderContext.unregisterControl(this.subject, this);
    }

    /**************************************************************************/
    // On Selection Changed Listener

    @Override
    public void onSelectionChanged(RasterLayer2 layer) {
        final String selection = layer.isAutoSelect() ? null : layer.getSelection();
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLAbstractDataStoreRasterLayer2.this.selection = selection;
                GLAbstractDataStoreRasterLayer2.this.invalidate();
            }
        });
    }

    /**************************************************************************/
    // On Data Store Content Changed Listener
    
    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLAbstractDataStoreRasterLayer2.this.invalidate();
            }
        });
    }

    /**************************************************************************/
    // Raster Layer On Selection Visible Changed Listener
    
    @Override
    public void onSelectionVisibleChanged(RasterLayer2 layer) {
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLAbstractDataStoreRasterLayer2.this.invalidate();
            }
        });
    }
    
    /**************************************************************************/

    @Override
    public synchronized RasterDataAccess2 accessRasterData(GeoPoint point) {
        Iterator<GLMapLayer3> iter = this.renderable.iterator();
        GLMapLayer3 layer;
        RasterDataAccessControl ctrl;
        RasterDataAccess2 dataAccess;
        while(iter.hasNext()) {
            layer = iter.next();
            ctrl = layer.getControl(RasterDataAccessControl.class);
            if(ctrl == null)
                continue;
            dataAccess = ctrl.accessRasterData(point);
            if(dataAccess != null)
                return dataAccess;
        }

        return null;
    }
    
    /**************************************************************************/
    
    @Override
    public void onTransparencyChanged(final RasterLayer2 ctrl) {
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized(GLAbstractDataStoreRasterLayer2.this) {
                    for(GLMapLayer3 r : GLAbstractDataStoreRasterLayer2.this.renderable) {
                        updateTransparency(r);
                    }
                }
            }
        });
    }

    /**************************************************************************/
    
    protected class State extends ViewState {
        public String selection;
        public RasterDataStore.DatasetQueryParameters queryParams = new RasterDataStore.DatasetQueryParameters();

        @Override
        public void set(GLMapView view) {
            super.set(view);
            this.selection = GLAbstractDataStoreRasterLayer2.this.selection;
            this.upperLeft.set(this.northBound, this.westBound);
            this.upperRight.set(this.northBound, this.eastBound);
            this.lowerRight.set(this.southBound, this.eastBound);
            this.lowerLeft.set(this.southBound, this.westBound);
            this.updateQueryParams();
        }

        @Override
        public void copy(ViewState view) {
            super.copy(view);
            this.selection = ((State)view).selection;
            this.updateQueryParams();
        }
        
        /**
         * Updates the dataset query parameters to reflect the current state.
         */
        protected void updateQueryParams() {
            RasterDataStore.DatasetQueryParameters.clear(this.queryParams);
            if(this.upperLeft != null && this.lowerRight != null) {
                if(this.queryParams.spatialFilter instanceof RasterDataStore.DatasetQueryParameters.RegionSpatialFilter) {
                    RasterDataStore.DatasetQueryParameters.RegionSpatialFilter roi = (RasterDataStore.DatasetQueryParameters.RegionSpatialFilter)this.queryParams.spatialFilter;
                    roi.upperLeft = this.upperLeft;
                    roi.lowerRight = this.lowerRight;
                } else {
                    this.queryParams.spatialFilter = new RasterDataStore.DatasetQueryParameters.RegionSpatialFilter(this.upperLeft, this.lowerRight);
                }
            }
            this.queryParams.minGsd = this.drawMapResolution;
        }
    }
}
