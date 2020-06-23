package com.atakmap.map.layer.raster.mobileimagery;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Objects;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.AttributionControl;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.opengl.GLAbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.service.OnlineImageryExtension;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Rectangle;

public class GLMobileImageryRasterLayer2 extends GLAbstractDataStoreRasterLayer2 implements OnlineImageryExtension.CacheRefreshListener, OnlineImageryExtension.OnOfflineOnlyModeChangedListener {

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // MobileImageryRasterLayer : AbstractDataStoreRasterLayer : RasterLayer : Layer
            return 3;
        }

        @Override
        public GLLayer2 create(android.util.Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if(layer instanceof MobileImageryRasterLayer2)
                return new GLMobileImageryRasterLayer2(surface, (MobileImageryRasterLayer2)layer);
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

    // register our TileReader here since it is completely internal
    static {
        TileReaderFactory.registerSpi(MobileImageryTileReader.SPI);
    }

    private final static Collection<RasterDataStore.DatasetQueryParameters.Order> ORDER_GSD = Collections.<RasterDataStore.DatasetQueryParameters.Order>singleton(RasterDataStore.DatasetQueryParameters.GSD.INSTANCE);

    private final static String TAG = "GLMobileImageryRasterLayer";

    private String autoSelectValue;
    private AtomicBoolean contentChanged;
    private Map<DatasetDescriptor, Pair<DatasetDescriptor, Collection<DatasetDescriptor>>> componentToAggregate;
    private OfflineOnlyService2State offlineOnly;
    
    private OnlineImageryExtension onlineImageryExtension;
    
    private final AttributionControlImpl attributionControl;

    private GLMobileImageryRasterLayer2(MapRenderer surface, MobileImageryRasterLayer2 subject) {
        super(surface, subject);
        
        this.autoSelectValue = null;
        this.contentChanged = new AtomicBoolean(true);
        this.componentToAggregate = new HashMap<DatasetDescriptor, Pair<DatasetDescriptor, Collection<DatasetDescriptor>>>();
        this.offlineOnly = new OfflineOnlyService2State();
        
        this.onlineImageryExtension = null;
        
        this.attributionControl = new AttributionControlImpl();
    }

    Collection<GLMapLayer3> getRenderables() {
        return this.renderable;
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
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        // flag that the content has changed -- this will force a rebuild of the
        // aggregates on the next query
        this.contentChanged.set(true);

        super.onDataStoreContentChanged(null);
    }

    private void refreshAggregates() {
        this.invalidateAggregates();
        
        this.componentToAggregate.clear();

        //System.out.println("**********************************************");
        //System.out.println("REBUILD AGGREGATES");

        Map<String, Collection<DatasetDescriptor>> aggregates = new HashMap<String, Collection<DatasetDescriptor>>();
        Collection<DatasetDescriptor> c;

        RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
        this.subject.filterQueryParams(params);
        
        RasterDataStore.DatasetDescriptorCursor cursor = null;
        try {
            cursor = this.subject.getDataStore().queryDatasets(params);
            DatasetDescriptor desc;
            String type;
            
            while(cursor.moveToNext()) {
                desc = cursor.get();
                if(isMobileAggregate(desc)) {
                    type = getImageryType(desc);
                    c = aggregates.get(type);
                    if(c == null)
                        aggregates.put(type, c=new LinkedList<DatasetDescriptor>());
                    c.add(desc);
                }
            }
        } finally {
            if(cursor != null)
                cursor.close();
        }
        
        DatasetDescriptor aggregate;
        for(Map.Entry<String, Collection<DatasetDescriptor>> entry : aggregates.entrySet()) {
            c = entry.getValue();
            // don't aggregate when we only have a single item
            if(c.size() == 1)
                continue;

            aggregate = MobileImageryTilesetSupport.reserveAggregateDescriptor(entry.getKey(), c);
            if(aggregate != null) {
                Pair<DatasetDescriptor, Collection<DatasetDescriptor>> value = Pair.create(aggregate, c); 
                for(DatasetDescriptor desc : c)
                    this.componentToAggregate.put(desc, value);
            }
        }
    }

    private void invalidateAggregates() {
        for(Pair<DatasetDescriptor, Collection<DatasetDescriptor>> aggregate : this.componentToAggregate.values()) {
            MobileImageryTilesetSupport.releaseAggregateDescriptor(aggregate.first);
        }        
    }

    /**************************************************************************/
    // GL Asynchronous Layer

    @Override
    public synchronized void start() {
        super.start();
        
        this.onlineImageryExtension = this.subject.getExtension(OnlineImageryExtension.class);
        if(this.onlineImageryExtension != null) {
            this.onlineImageryExtension.addOnOfflineOnlyModeChangedListener(this);
            this.onlineImageryExtension.addCacheRefreshListener(this);

            this.offlineOnly.offlineOnly = this.onlineImageryExtension.isOfflineOnlyMode();
            this.offlineOnly.refreshInterval = this.onlineImageryExtension.getCacheAutoRefreshInterval();
        }
        
        this.renderContext.registerControl(this.subject, this.attributionControl);
        
        this.contentChanged.set(true);
    }

    @Override
    public synchronized void stop() {
        
        this.renderContext.unregisterControl(this.subject, this.attributionControl);
        
        if(this.onlineImageryExtension != null) {
            this.onlineImageryExtension.removeOnOfflineOnlyModeChangedListener(this);
            this.onlineImageryExtension.removeCacheRefreshListener(this);
        }

        super.stop();
    }
    
    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected void initImpl(GLMapView view) {
        super.initImpl(view);

        this.contentChanged.set(true);
    }

    @Override
    protected LinkedList<DatasetDescriptor> createPendingData() {
        return super.createPendingData();        
    }
    
    @Override
    protected void releasePendingData(LinkedList<DatasetDescriptor> pending) {
        this.invalidateAggregates();

        super.releasePendingData(pending);        
    }
    
    @Override
    protected ViewState newViewStateInstance() {
        return new State();
    }
    
    @Override
    protected String getBackgroundThreadName() {
        return "Mobile [" + this.subject.getName() + "] GL worker@" + Integer.toString(this.hashCode(), 16);
    }

    @Override
    protected void query(ViewState state, LinkedList<DatasetDescriptor> retval) {
        if(state.crossesIDL) {
            ViewState scratch = this.newViewStateInstance();
            scratch.copy(state);
            try {
                ViewState hemi = this.newViewStateInstance();
                
                LinkedList<DatasetDescriptor> sub = new LinkedList<DatasetDescriptor>();
                Collection<DatasetDescriptor> results = new TreeSet<DatasetDescriptor>(MULTIPLEX_DATASET_COMP);
                
                // west of IDL
                state.eastBound = 180d;
                state.upperLeft.set(state.northBound, state.westBound);
                state.upperRight.set(state.northBound, state.eastBound);
                state.lowerRight.set(state.southBound, state.eastBound);
                state.lowerLeft.set(state.southBound, state.westBound);
                hemi.copy(state);
                queryImpl(hemi, sub);
                
                results.addAll(sub);
                
                // reset
                state.copy(scratch);
                sub.clear();
                
                // east of IDL
                state.westBound = -180d;
                state.upperLeft.set(state.northBound, state.westBound);
                state.upperRight.set(state.northBound, state.eastBound);
                state.lowerRight.set(state.southBound, state.eastBound);
                state.lowerLeft.set(state.southBound, state.westBound);
                hemi.copy(state);
                queryImpl(hemi, sub);
                
                results.addAll(sub);
                
                retval.addAll(results);
            } finally {
                state.copy(scratch);
            }
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
        String selection = ((State)state).selection;
        boolean selectionIsStickyAuto = false;
        
        if(this.checkQueryThreadAbort())
            return;

        //System.out.println("GLDatasetRasterLayer [" + this.subject.getName() + "] query " + state.upperLeft + " " + state.lowerRight + " @ " + state.drawMapResolution);

        // if we are in auto-select, "stick" on the current auto-select value if
        // it is still in bounds AND we haven't exceeded the maximum resolution
        
        // XXX - 
        if(selection == null && ((State)state).renderables.size() > 0) {
            double maxRes = Double.NaN;
            double minRes = Double.NaN;
            boolean inBounds = false;
            String autoSelect = null;
            Envelope viewBnds = new Envelope(state.westBound,
                                             state.southBound,
                                             0d,
                                             state.eastBound,
                                             state.northBound,
                                             0d);

            for(DatasetDescriptor info : ((State)state).renderables) {
                if(Double.isNaN(maxRes) || info.getMaxResolution(null) < maxRes) {
                    maxRes = info.getMaxResolution(null);
                    // XXX - could contain more than one type
                    autoSelect = info.getImageryTypes().iterator().next();
                }
                if(Double.isNaN(minRes) || info.getMinResolution(null) > minRes) {
                    minRes = info.getMinResolution(null);
                }
                inBounds |= intersects(viewBnds, info.getCoverage(null));
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

        // if we are in autoselect mode re-query using the selected datasets
        if(select == null && retval.size() > 0) {
            select = new HashSet<String>();
            for(DatasetDescriptor info : retval)
                // XXX - multiple types
                select.add(info.getImageryTypes().iterator().next());
            retval.clear();
            this.queryImpl(select, (State)state, retval, false);
        }
        
        //System.out.println("[" + this.subject.getName() + "] selection=" + ((State)state).selection + " RESULTS: " + retval.size());
        //for(DatasetDescriptor d : retval)
        //    System.out.println("[" + this.subject.getName() + "] " + d.getName() + " [" + d.getDatasetType() + "] " + d.getMinResolution(null) + " " + d.getMaxResolution(null));

        
        if(selectionIsStickyAuto) {
            if(!Objects.equals(this.autoSelectValue, selection)) {
                //System.out.println("updated autoselect=" + selection);
                this.autoSelectValue = selection;
                ((MobileImageryRasterLayer2)this.subject).setAutoSelectValue(this.autoSelectValue);
            }
        }
        
        if(this.checkQueryThreadAbort())
            return;
        
        // if the content has changed, query on all filter-match datasets and
        // rebuild the aggregates. this ensures that the aggregate renderable
        // always contains ALL the components so we should never have to rebuild
        // during pan/zoom operations
        if(this.contentChanged.getAndSet(false)) {
            this.refreshAggregates();
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
        state.queryParams.imageryTypes = selection;
        this.subject.filterQueryParams(state.queryParams);

        RasterDataStore.DatasetDescriptorCursor result = null;
        DatasetDescriptor layerInfo;
        Envelope bnds = null;
        try {
            result = dataStore.queryDatasets(state.queryParams);
            
            while(result.moveToNext()) {
                if(this.checkQueryThreadAbort())
                    break;

                layerInfo = result.get();
                if(!this.subject.isVisible(getImageryType(layerInfo)))
                    continue;

                // auto-select is dataset with maximum GSD
                // XXX - could contain more than one type
                autoSelect = layerInfo.getImageryTypes().iterator().next();
                srid = layerInfo.getSpatialReferenceID();

                retval.addFirst(layerInfo);
                bnds = layerInfo.getMinimumBoundingBox();
                if(selection == null)
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
                ((MobileImageryRasterLayer2)this.subject).setAutoSelectValue(this.autoSelectValue);
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

                Projection proj = ProjectionFactory.getProjection(srid);
                if(proj != null)
                    this.subject.setPreferredProjection(proj);
                else
                    this.subject.setPreferredProjection(EquirectangularMapProjection.INSTANCE);
            }
        }
    }
    
    private static boolean isMobileAggregate(DatasetDescriptor desc) {
        return (Integer.parseInt(DatasetDescriptor.getExtraData(desc, "mobileimagery.aggregate", String.valueOf(0))) != 0);
    }
    
    private static String getImageryType(DatasetDescriptor desc) {
        if(desc instanceof ImageDatasetDescriptor)
            return ((ImageDatasetDescriptor) desc).getImageryType();
        else
            return desc.getImageryTypes().iterator().next();
    }

    @Override
    protected boolean updateRenderableReleaseLists(LinkedList<DatasetDescriptor> pendingData) {
        Map<DatasetDescriptor, GLMapLayer3> swap = new HashMap<DatasetDescriptor, GLMapLayer3>();
        for(GLMapLayer3 l : this.renderable)
            swap.put(l.getInfo(), l);
        this.renderable.clear();

        Set<DatasetDescriptor> consumedAggregates = Collections.newSetFromMap(new IdentityHashMap<DatasetDescriptor, Boolean>());
        
        //System.out.println("UPDATE RENDERABLES");
        //System.out.println("swap=" + swap.keySet());
        //System.out.println("pending=" + pendingData);
        //System.out.println("C2A=" + componentToAggregate);

        GLMapLayer3 renderer;
        Pair<DatasetDescriptor, Collection<DatasetDescriptor>> aggregate;
        for(DatasetDescriptor info : pendingData) {
            aggregate = this.componentToAggregate.get(info);
            if(aggregate != null) {
                // if the aggregate is already in the render list, continue
                if(consumedAggregates.contains(aggregate.first))
                    continue;
                
                // the aggregate is the desired representation
                info = aggregate.first;
            }
            renderer = swap.remove(info);

            if(renderer == null) {
                //if(aggregate != null) {
                //    // XXX - go through GLMapLayerFactory
                //    renderer = new GLMobileImageryMapLayer(this.surface, (ImageDatasetDescriptor)aggregate.first, aggregate.second);
                //} else
                renderer = GLMapLayerFactory.create3(this.renderContext, info);
            } 
            
            // at the point we either re-used or created the aggregate renderer
            if(aggregate != null)
                consumedAggregates.add(aggregate.first);

            if(renderer != null) {
                // if the renderer implement offline only services, set the
                // proper mode
                TileClientControl ctrl = renderer.getControl(TileClientControl.class);
                if(ctrl != null) {
                    ctrl.setOfflineOnlyMode(this.offlineOnly.offlineOnly);
                    ctrl.setCacheAutoRefreshInterval(this.offlineOnly.refreshInterval);
                }

                this.renderable.add(renderer);
            } else {
                Log.w(TAG, "Failed to create renderer for " + info.getName());
            }
        }
        
        //System.out.println("\n\n");

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

    /**************************************************************************/

    @Override
    public synchronized void onManualRefreshRequested(OnlineImageryExtension svc) {
        Collection<GLMapLayer3> renderables = this.getRenderables();
        for(GLMapLayer3 renderer : renderables) {
            TileClientControl ctrl = null;
            ctrl = renderer.getControl(TileClientControl.class);
            if(ctrl != null)
                ctrl.refreshCache();
        }
    }

    @Override
    public void onAutoRefreshIntervalChanged(OnlineImageryExtension svc, long millis) {
        this.offlineOnly.refreshInterval = millis;
        this.invalidate();
    }

    @Override
    public void onOfflineOnlyModeChanged(OnlineImageryExtension service, boolean offlineOnly) {
        this.offlineOnly.offlineOnly = offlineOnly;
        this.invalidate();
    }

    /**************************************************************************/
    
    protected class State extends GLAbstractDataStoreRasterLayer2.State {
        protected Collection<DatasetDescriptor> renderables;

        protected State() {
            this.renderables = new LinkedList<DatasetDescriptor>();
        }

        @Override
        public void set(GLMapView view) {
            super.set(view);
            this.renderables.clear();

            final Collection<GLMapLayer3> renderable = GLMobileImageryRasterLayer2.this.getRenderables();
            for(GLMapLayer3 l : renderable)
                this.renderables.add(l.getInfo());
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
    
    private static class Pair<A, B> {
        public final A first;
        public final B second;
        
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
        
        public static <A, B> Pair<A, B> create(A first, B second) {
            return new Pair<A, B>(first, second);
        }
        
        public String toString() {
            return "Pair {" + this.first + ", " + this.second + "}";
        }
    }
    
    private static class OfflineOnlyService2State {
        public boolean offlineOnly = false;
        public long refreshInterval = -1L;
    }

    private static boolean intersects(Envelope aoi, Geometry geom) {
        Envelope mbb = geom.getEnvelope();
        if(!Rectangle.intersects(aoi.minX, aoi.minY,
                                 aoi.maxX, aoi.maxY,
                                 mbb.minX, mbb.minY,
                                 mbb.maxX, mbb.maxY)) {
            
            return false;
        }
        
        if(geom instanceof GeometryCollection) {
            Collection<Geometry> children = ((GeometryCollection)geom).getGeometries();
            for(Geometry child : children)
                if(intersects(aoi, child))
                    return true;
            return false;
        } else {
            // XXX - perform linestring/polygon intersection
            return true;
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
