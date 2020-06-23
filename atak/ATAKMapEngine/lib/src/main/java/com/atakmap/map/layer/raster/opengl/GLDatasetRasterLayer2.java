package com.atakmap.map.layer.raster.opengl;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.lang.Objects;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.AttributionControl;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.service.OnlineImageryExtension;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Rectangle;

import com.atakmap.coremap.log.Log;

public class GLDatasetRasterLayer2 extends GLAbstractDataStoreRasterLayer2 implements OnlineImageryExtension.CacheRefreshListener, OnlineImageryExtension.OnOfflineOnlyModeChangedListener {

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // DatasetRasterLayer : AbstractDataStoreRasterLayer : RasterLayer : Layer
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if(layer instanceof DatasetRasterLayer2)
                return new GLDatasetRasterLayer2(surface, (DatasetRasterLayer2)layer);
            return null;
        }
    };
    
    private final static Comparator<DatasetDescriptor> MULTIPLEX_DATASET_COMP = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor lhs, DatasetDescriptor rhs) {
            final double res0 = lhs.getMaxResolution(null);
            final double res1 = rhs.getMaxResolution(null);
            if(res0 > res1)
                return -1;
            else if(res0 < res1)
                return 1;
            int comp = lhs.getName().compareTo(rhs.getName());
            if(comp != 0)
                return comp;
            return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    
    private final static Collection<RasterDataStore.DatasetQueryParameters.Order> ORDER_GSD = Collections.<RasterDataStore.DatasetQueryParameters.Order>singleton(RasterDataStore.DatasetQueryParameters.GSD.INSTANCE);

    public static final String TAG = "GLDatasetRasterLayer2";

    private OnlineImageryExtension offlineOnlyService;
    private OfflineOnlyService2State offlineOnly;
    private String autoSelectValue;
    private Projection preferredProjection;
    
    private final AttributionControlImpl attributionControl;
    
    public GLDatasetRasterLayer2(MapRenderer surface, DatasetRasterLayer2 subject) {
        super(surface, subject);
        
        this.autoSelectValue = null;
        this.offlineOnlyService = null;
        this.offlineOnly = new OfflineOnlyService2State();
        
        this.attributionControl = new AttributionControlImpl();
    }

    @Override
    public synchronized void start() {
        super.start();
        
        this.offlineOnlyService = this.subject.getExtension(OnlineImageryExtension.class);
        if(this.offlineOnlyService != null) {
            this.offlineOnlyService.addCacheRefreshListener(this);
            this.offlineOnlyService.addOnOfflineOnlyModeChangedListener(this);

            this.offlineOnly.offlineOnly = this.offlineOnlyService.isOfflineOnlyMode();
            this.offlineOnly.refreshInterval = this.offlineOnlyService.getCacheAutoRefreshInterval();
        }
        
        this.renderContext.registerControl(this.subject, this.attributionControl);
    }
    
    @Override
    public synchronized void stop() {
        this.renderContext.unregisterControl(this.subject, this.attributionControl);
        
        if(this.offlineOnlyService != null) {
            this.offlineOnlyService.removeCacheRefreshListener(this);
            this.offlineOnlyService.removeOnOfflineOnlyModeChangedListener(this);

            this.offlineOnlyService = null;
        }
        super.stop();
    }

    @Override
    protected void updateTransparency(GLMapLayer3 renderer) {
        // apply alpha value on GLMapLayer2
        ColorControl ctrl = renderer.getControl(ColorControl.class);
        if(ctrl != null) {
            int alpha = (int)(this.subject.getTransparency(renderer.getInfo().getName())*255f);
            ctrl.setColor((alpha<<24)|0x00FFFFFF);
        }
    }

    @Override
    protected boolean updateRenderableReleaseLists(LinkedList<DatasetDescriptor> pendingData) {
        final boolean retval = super.updateRenderableReleaseLists(pendingData);
        if(retval) {
            for(GLMapLayer3 renderer : this.renderable) {
                // if the renderer implement offline only services, set the
                // proper mode
                TileClientControl svc = renderer.getControl(TileClientControl.class);
                if(svc != null) {
                    svc.setOfflineOnlyMode(this.offlineOnly.offlineOnly);
                    svc.setCacheAutoRefreshInterval(this.offlineOnly.refreshInterval);
                }                
            }
        }
        return retval;
    }
    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected ViewState newViewStateInstance() {
        return new State();
    }

    @Override
    protected String getBackgroundThreadName() {
        return "Dataset [" + this.subject.getName() + "] GL worker@" + Integer.toString(this.hashCode(), 16);
    }
    
    @Override
    protected void query(ViewState state, LinkedList<DatasetDescriptor> retval) {
        if (state.continuousScrollEnabled) {

            // Based on Google Earth's KML export format, raster datasets which
            // cross the IDL are represented with unwrapped longitude values
            // (i.e. -179 to -181 instead of -179 to 179)
            // In order to ensure we query all visible datasets we'll need to
            // scan both the current unwrapped view state and the unwrapped
            // view state for the other hemisphere
            ViewState hemi = this.newViewStateInstance();
            LinkedList<DatasetDescriptor> sub = new LinkedList<DatasetDescriptor>();
            Collection<DatasetDescriptor> results = new TreeSet<DatasetDescriptor>(MULTIPLEX_DATASET_COMP);

            // Scan current hemisphere
            boolean westHemi = state.drawLng < 0;
            hemi.copy(state);
            if (state.crossesIDL) {
                if (westHemi) {
                    hemi.westBound = state.eastBound - 360;
                    hemi.eastBound = state.westBound;
                } else {
                    hemi.eastBound = state.westBound + 360;
                    hemi.westBound = state.eastBound;
                }
            }
            hemi.upperLeft.set(hemi.northBound, hemi.westBound);
            hemi.upperRight.set(hemi.northBound, hemi.eastBound);
            hemi.lowerRight.set(hemi.southBound, hemi.eastBound);
            hemi.lowerLeft.set(hemi.southBound, hemi.westBound);
            queryImpl(hemi, sub);
            results.addAll(sub);

            // Scan opposing hemisphere
            sub.clear();
            hemi.copy(state);
            if (state.crossesIDL) {
                if (westHemi) {
                    hemi.eastBound = state.westBound + 360;
                    hemi.westBound = state.eastBound;
                } else {
                    hemi.westBound = state.eastBound - 360;
                    hemi.eastBound = state.westBound;
                }
            } else {
                if (westHemi) {
                    hemi.eastBound += 360;
                    hemi.westBound += 360;
                } else {
                    hemi.eastBound -= 360;
                    hemi.westBound -= 360;
                }
            }
            hemi.upperLeft.set(hemi.northBound, hemi.westBound);
            hemi.upperRight.set(hemi.northBound, hemi.eastBound);
            hemi.lowerRight.set(hemi.southBound, hemi.eastBound);
            hemi.lowerLeft.set(hemi.southBound, hemi.westBound);
            queryImpl(hemi, sub);
            results.addAll(sub);

            retval.addAll(results);
        } else {
            queryImpl(state, retval);
        }
        
        Set<android.util.Pair<String, String>> attrs = new HashSet<android.util.Pair<String, String>>();
        for(DatasetDescriptor desc : retval) {
            String attr = desc.getExtraData("attribution");
            if(attr != null)
                attrs.add(android.util.Pair.create("Imagery", attr));
        }
        this.attributionControl.setAttributions(attrs);
    }

    private void queryImpl(ViewState state, LinkedList<DatasetDescriptor> retval) {
        boolean selectionIsStickyAuto = false;
        final int limit = ((DatasetRasterLayer2)this.subject).getDatasetLimit();
        String selection = (!subject.isAutoSelect() || (limit > 0)) ? ((State)state).selection : null;
        
        if(this.checkQueryThreadAbort())
            return;
        
        //System.out.println("GLDatasetRasterLayer [" + this.subject.getName() + "] query " + state.upperLeft + " " + state.lowerRight + " @ " + state.drawMapResolution);

        // if we are in auto-select, "stick" on the current auto-select value if
        // it is still in bounds AND we haven't exceeded the maximum resolution
        if(selection == null && limit > 0 && ((State)state).renderables.size() > 0) {
            double maxRes = Double.NaN;
            double minRes = Double.NaN;
            boolean inBounds = false;
            String autoSelect = null;
            Envelope mbb;
            GeoBounds viewBnds = new GeoBounds(state.northBound,
                                               state.westBound,
                                               state.southBound,
                                               state.eastBound);

            for(DatasetDescriptor info : ((State)state).renderables) {
                if(Double.isNaN(maxRes) || info.getMaxResolution(null) < maxRes) {
                    maxRes = info.getMaxResolution(null);
                    autoSelect = info.getName();
                }
                if(Double.isNaN(minRes) || info.getMinResolution(null) > minRes) {
                    minRes = info.getMinResolution(null);
                }
                mbb = info.getMinimumBoundingBox();
                inBounds |= viewBnds.intersects(mbb.maxY, mbb.minX, mbb.minY, mbb.maxX);
            }

            if(inBounds && maxRes <= state.drawMapResolution && minRes >= state.drawMapResolution) {
                // sticking on the current selection -- no need to fire off a
                // projection change, conditionally fire an auto-select as we
                // may be here as a result of going into auto-select mode.
                selection = autoSelect;
                selectionIsStickyAuto = true;
            }
        }

        Collection<String> select = null;
        if(selection != null)
            select = Collections.<String>singleton(selection);
        else
            select = null;
            
        if(this.checkQueryThreadAbort())
            return;
        
        this.queryImpl(select, (State)state, retval, true);
        
        // if we are in autoselect mode and there is a limit on the selection,
        // re-query using the selected datasets
        if(select == null && (limit > 0 && retval.size() > 0)) {
            select = new HashSet<String>();
            for(DatasetDescriptor info : retval)
                select.add(info.getName());
            retval.clear();
            this.queryImpl(select, (State)state, retval, false);
        }
        

        //System.out.println("[" + this.subject.getName() + "] selection=" + ((State)state).selection + " RESULTS: " + retval.size());
        //for(DatasetDescriptor d : retval)
        //    System.out.println("[" + this.subject.getName() + "] " + d.getName() + " [" + d.getDatasetType() + "] " + d.getMinResolution(null) + " " + d.getMaxResolution(null));

        
        if(selectionIsStickyAuto) {
            if(!Objects.equals(this.autoSelectValue, selection)) {
                Log.d(TAG, "updated autoselect=" + selection);
                this.autoSelectValue = selection;
                ((DatasetRasterLayer2)this.subject).setAutoSelectValue(this.autoSelectValue);
            }
        }
    }
    
    private void queryImpl(Collection<String> selection, State state, LinkedList<DatasetDescriptor> retval, boolean notify) {
        final RasterDataStore dataStore = this.subject.getDataStore();
        
        String autoSelect = null;
        int srid = -1;

        if(this.checkQueryThreadAbort())
            return;
        
        //System.out.println(this.subject.getName() + "@" + Integer.toString(this.hashCode(), 16) + " query for selection: " + select);

        // overwrite the selection field, all other fields should have been
        // set appropriately in State.set/State.copy
        state.queryParams.names = selection;
        this.subject.filterQueryParams(state.queryParams);

        RasterDataStore.DatasetDescriptorCursor result = null;
        DatasetDescriptor layerInfo;
        boolean first = true;
        Envelope bnds = null;
        try {
            result = dataStore.queryDatasets(state.queryParams);
            
            Set<String> datasets = new HashSet<String>();
            final int limit = ((DatasetRasterLayer2)this.subject).getDatasetLimit();
            while(result.moveToNext()) {
                if(this.checkQueryThreadAbort())
                    break;

                layerInfo = result.get();
                if(!this.subject.isVisible(layerInfo.getName()))
                    continue;
                Envelope mbb = layerInfo.getMinimumBoundingBox();
                if(first) {
                    // auto-select is dataset with maximum GSD
                    autoSelect = layerInfo.getName();
                    srid = layerInfo.getSpatialReferenceID();
                    first = false;
                    bnds = mbb;
                } else {
                    if(mbb.minX < bnds.minX)
                        bnds.minX = mbb.minX;
                    if(mbb.minY < bnds.minY)
                        bnds.minY = mbb.minY;
                    if(mbb.maxX > bnds.maxX)
                        bnds.maxX = mbb.maxX;
                    if(mbb.maxY > bnds.maxY)
                        bnds.maxY = mbb.maxY;
                }
                retval.addFirst(layerInfo);
                datasets.add(layerInfo.getName());
                if(selection == null && datasets.size() == limit)
                    break;
            }
            
            //System.out.println(this.subject.getName() + " num results=" + retval.size() + " " + retval);
            //System.out.println("layer min res=" + minRes + " drawRes=" + state.drawMapResolution);
        } finally {
            if(result != null)
                result.close();
        }
        
        if(this.checkQueryThreadAbort())
            return;
        
        if(notify) {
            if(!Objects.equals(this.autoSelectValue, autoSelect)) {
                this.autoSelectValue = autoSelect;
                ((DatasetRasterLayer2)this.subject).setAutoSelectValue(this.autoSelectValue);
            }
            
            if(srid != state.drawSrid &&
                    bnds != null &&
                    Rectangle.contains(bnds.minX,
                                       bnds.minY,
                                       bnds.maxX,
                                       bnds.maxY,
                                       state.westBound,
                                       state.southBound,
                                       state.eastBound,
                                       state.northBound)) {

                Projection preferred = this.preferredProjection;
                if(preferred == null || preferred.getSpatialReferenceID() != srid)
                    preferred = ProjectionFactory.getProjection(srid);
                this.preferredProjection = preferred;
                if(preferred != null)
                    this.subject.setPreferredProjection(preferred);
            }
        }
    }
    
    /**************************************************************************/

    @Override
    public void onOfflineOnlyModeChanged(OnlineImageryExtension ext, boolean offlineOnly) {
        this.offlineOnly.offlineOnly = ext.isOfflineOnlyMode();
        this.invalidate();
    }

    @Override
    public void onManualRefreshRequested(OnlineImageryExtension ext) {
        synchronized(this) {
            Collection<GLMapLayer3> renderables = this.getRenderList();
            for(GLMapLayer3 renderer : renderables) {
                TileClientControl ctrl = renderer.getControl(TileClientControl.class);
                if(ctrl != null)
                    ctrl.refreshCache();
            }
        }
    }

    @Override
    public void onAutoRefreshIntervalChanged(OnlineImageryExtension ext, long millis) {
        this.offlineOnly.refreshInterval = millis;
        this.invalidate();
    }

    /**************************************************************************/

    private static class OfflineOnlyService2State {
        public boolean offlineOnly = false;
        public long refreshInterval = -1L;
    }

    protected class State extends GLAbstractDataStoreRasterLayer2.State {
        protected Collection<DatasetDescriptor> renderables;

        protected State() {
            this.renderables = new LinkedList<DatasetDescriptor>();
        }

        @Override
        public void set(GLMapView view) {
            super.set(view);
            this.renderables.clear();
            if(((DatasetRasterLayer2)GLDatasetRasterLayer2.this.getSubject()).getDatasetLimit() > 0) {
                final Collection<GLMapLayer3> renderable = GLDatasetRasterLayer2.this.getRenderList();
                for(GLMapLayer3 l : renderable)
                    this.renderables.add(l.getInfo());
            }
        }

        @Override
        public void copy(ViewState view) {
            super.copy(view);
            this.renderables.clear();
            this.renderables.addAll(((State)view).renderables);
        }
        
        @Override
        protected void updateQueryParams() {
            super.updateQueryParams();
            this.queryParams.order = ORDER_GSD;
        }
    }
    
    private static class AttributionControlImpl implements AttributionControl {

        private Set<OnAttributionUpdatedListener> listeners;
        private Set<android.util.Pair<String, String>> attributions;
        
        public AttributionControlImpl() {
            this.listeners = Collections.newSetFromMap(new IdentityHashMap<OnAttributionUpdatedListener, Boolean>());
            this.attributions = Collections.<android.util.Pair<String, String>>emptySet();
        }

        synchronized void setAttributions(Set<android.util.Pair<String, String>> attrs) {
            this.attributions = attrs;
            for(OnAttributionUpdatedListener l : this.listeners)
                l.onAttributionUpdated(this);
        }

        @Override
        public synchronized Set<android.util.Pair<String, String>> getContentAttribution() {
            return this.attributions;
        }

        @Override
        public synchronized void addOnAttributionUpdatedListener(OnAttributionUpdatedListener l) {
            this.listeners.add(l);
        }

        @Override
        public synchronized void removeOnAttributionUpdatedListener(OnAttributionUpdatedListener l) {
            this.listeners.remove(l);
        }
        
    }
}
