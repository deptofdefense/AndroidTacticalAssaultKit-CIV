package com.atakmap.map.layer.raster.nativeimagery;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.controls.ImagerySelectionControl;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2.Frame;
import com.atakmap.map.layer.raster.mosaic.MosaicFrameColorControl;
import com.atakmap.map.layer.raster.mosaic.opengl.GLMosaicMapLayer;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.map.layer.raster.service.SelectionOptionsCallbackExtension;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.util.Filter;

public class GLNativeImageryRasterLayer2 extends GLAbstractLayer
                implements GLResolvableMapRenderable,
                           RasterLayer2.OnSelectionChangedListener,
                           RasterDataStore.OnDataStoreContentChangedListener,
                           RasterLayer2.OnSelectionVisibleChangedListener,
                           RasterLayer2.OnSelectionTransparencyChangedListener,
                           SelectionOptionsCallbackExtension.OnSelectionOptionsChangedListener {

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if(!(object.second instanceof NativeImageryRasterLayer2))
                return null;
            return new GLNativeImageryRasterLayer2(object.first, (NativeImageryRasterLayer2)object.second);
        }
        
    };

    private GLMosaic mosaic;
    private String layerKey;
    private MosaicDatasetDescriptor layerDesc;
    
    private Map<String, TransparencySetting> selectionTransparency;
    private Set<TransparencySetting> addFilters;
    private Set<Filter<MosaicDatabase2.Frame>> removeFilters;

    protected final RasterDataAccessControl rasterAccessControl;

    private final NativeImageryRasterLayer2 subject;

    GLNativeImageryRasterLayer2(MapRenderer surface, NativeImageryRasterLayer2 subject) {
        super(surface, subject);

        this.subject = subject;

        this.mosaic = null;
        this.layerKey = null;
        this.layerDesc = null;
        
        this.selectionTransparency = new HashMap<String, TransparencySetting>();
        this.addFilters = Collections.newSetFromMap(new IdentityHashMap<TransparencySetting, Boolean>());
        this.removeFilters = Collections.newSetFromMap(new IdentityHashMap<Filter<MosaicDatabase2.Frame>, Boolean>());
        
        this.rasterAccessControl = new RasterDataAccessControlImpl();
    }

    /**************************************************************************/
    // GL Abstract Layer

    @Override
    public void start() {
        super.start();

        final NativeImageryRasterLayer2 nativeImageryLayer = this.subject;
        final RasterDataStore dataStore = nativeImageryLayer.getDataStore();
        
        synchronized(this) {
            this.layerKey = NativeImageryMosaicDatabase2.registerLayer(nativeImageryLayer);
            this.layerDesc = new MosaicDatasetDescriptor(this.subject.getName(),
                    "/dev/null",
                    null,
                    null,
                    new File(this.layerKey),
                    NativeImageryMosaicDatabase2.TYPE,
                    Collections.singleton("native"),
                    Collections.singletonMap("native", Pair.create(Double.valueOf(Double.MAX_VALUE), Double.valueOf(0.0d))),
                    Collections.singletonMap("native", DatasetDescriptor.createSimpleCoverage(new GeoPoint(90, -180), new GeoPoint(90, 180), new GeoPoint(-90, 180), new GeoPoint(-90, -180))),
                    4326,
                    false,
                    null,
                    Collections.<String, String>singletonMap("relativePaths", "false"));
        }
        
        dataStore.addOnDataStoreContentChangedListener(this);
        nativeImageryLayer.addOnSelectionChangedListener(this);
        nativeImageryLayer.addOnSelectionVisibleChangedListener(this);

        SelectionOptionsCallbackExtension optionsChangedEx = nativeImageryLayer.getExtension(SelectionOptionsCallbackExtension.class);
        if(optionsChangedEx != null)
            optionsChangedEx.addOnSelectionOptionsChangedListener(this);

        nativeImageryLayer.setPreferredProjection(EquirectangularMapProjection.INSTANCE);
        
        // obtain current transparency values
        this.onTransparencyChanged(this.subject);
        
        this.subject.addOnSelectionTransparencyChangedListener(this);
        
        // raster data access
        this.renderContext.registerControl(this.subject, this.rasterAccessControl);
    }
    
    @Override
    public void stop() {
        final NativeImageryRasterLayer2 nativeImageryLayer = this.subject;
        final RasterDataStore dataStore = nativeImageryLayer.getDataStore();
        
        SelectionOptionsCallbackExtension optionsChangedEx = nativeImageryLayer.getExtension(SelectionOptionsCallbackExtension.class);
        if(optionsChangedEx != null)
            optionsChangedEx.removeOnSelectionOptionsChangedListener(this);
        
        dataStore.removeOnDataStoreContentChangedListener(this);
        nativeImageryLayer.removeOnSelectionChangedListener(this);
        nativeImageryLayer.removeOnSelectionVisibleChangedListener(this);
        nativeImageryLayer.removeOnSelectionTransparencyChangedListener(this);

        // raster data access
        this.renderContext.unregisterControl(this.subject, this.rasterAccessControl);

        synchronized(this) {
            NativeImageryMosaicDatabase2.unregisterLayer(this.subject);
            this.layerKey = null;
            this.layerDesc = null;
        }

        super.stop();
    }

    @Override
    protected void init() {
        super.init();
        
        synchronized(this) {
            if(this.layerDesc != null)
                this.mosaic = new GLMosaic(this.renderContext, this.layerDesc);
        }
    }
    
    @Override
    protected void drawImpl(GLMapView view) {
        if(this.mosaic != null) {
            final boolean transFilterAdd = !this.addFilters.isEmpty();
            final boolean transFilterRemove = !this.removeFilters.isEmpty();
            if(transFilterAdd || transFilterRemove) {
                MosaicFrameColorControl ctrl = this.mosaic.getControl(MosaicFrameColorControl.class);
                if(ctrl != null) {
                    if(transFilterRemove) {
                        for(Filter<MosaicDatabase2.Frame> filter : this.removeFilters)
                            ctrl.removeFilter(filter);
                        this.removeFilters.clear();
                    }
                    if(transFilterAdd) {
                        for(TransparencySetting s : this.addFilters)
                            ctrl.addFilter(s.filter, ((int)(s.value*255)<<24)|0x00FFFFFF);
                        this.addFilters.clear();
                    }
                }
            }
            this.mosaic.draw(view);
        
            // XXX - breaking contract of started/stopped state
            this.subject.setAutoSelectValue(this.mosaic.getAutoSelectValue());
        }
    }

    @Override
    public void release() {
        if(this.mosaic != null) {
            this.mosaic.release();
            this.mosaic = null;
        }
        
        // all transparency settings will need to be reset on init
        this.addFilters.addAll(this.selectionTransparency.values());

        super.release();
    }

    private void refreshImpl(RasterLayer2 layer) {
        final GLMosaic mosaic = this.mosaic;
        if(mosaic == null)
            return;

        Set<String> typeFilter;
        ImagerySelectionControl.Mode resSelectMode;
        if(!layer.isAutoSelect()) {
            final String selection = layer.getSelection();
            if(selection != null) {
                if(layer.isVisible(selection))
                    typeFilter = Collections.singleton(selection);
                else
                    typeFilter = Collections.<String>emptySet();
            } else {
                typeFilter = null;
            }
            resSelectMode = ImagerySelectionControl.Mode.MinimumResolution;
        } else {
            typeFilter = new HashSet<String>();
            boolean allVisible = true;

            final Collection<String> opts = layer.getSelectionOptions();
            for(String opt : opts) {
                if(!layer.isVisible(opt)) {
                    allVisible = false;
                    continue;
                }
                typeFilter.add(opt);
            }
            if(allVisible)
                typeFilter = null;
            resSelectMode = ImagerySelectionControl.Mode.MaximumResolution;
        }

        final ImagerySelectionControl ctrl = mosaic.getControl(ImagerySelectionControl.class);
        if(ctrl == null)
            return;

        ctrl.setFilter(typeFilter);
        ctrl.setResolutionSelectMode(resSelectMode);
    }
    
    /**************************************************************************/
    // Raster Layer On Selection Changed Listener

    @Override
    public void onSelectionChanged(RasterLayer2 layer) {
        renderContext.queueEvent(new Runnable() {
            public void run() {
                refreshImpl(subject);
            }
        });
    }

    /**************************************************************************/
    // Raster Layer On Selection Visible Changed Listener
    
    @Override
    public void onSelectionVisibleChanged(RasterLayer2 layer) {
        renderContext.queueEvent(new Runnable() {
            public void run() {
                refreshImpl(subject);
            }
        });
    }

    /**************************************************************************/
    // OnSelectionOptionsChangedListener
    
    @Override
    public void onSelectionOptionsChanged(RasterLayer2 layer) {
        renderContext.queueEvent(new Runnable() {
            public void run() {
                refreshImpl(subject);
            }
        });
    }
    
    /**************************************************************************/
    // SelectionTransparencyControl On Selection Transparency Changed Listener
    
    @Override
    public void onTransparencyChanged(RasterLayer2 control) {
        // XXX - obtain current transparency values
        final Map<String, TransparencySetting> transparencySettings = new HashMap<String, TransparencySetting>();
        Collection<String> selections = control.getSelectionOptions();
        for(final String selection : selections) {
            TransparencySetting s = new TransparencySetting();
            s.value = this.subject.getTransparency(selection);
            if(s.value < 1f) {
                s.filter = new Filter<MosaicDatabase2.Frame>() {
                    @Override
                    public boolean accept(Frame arg) {
                        return selection.equals(arg.type);
                    }
                };
                transparencySettings.put(selection, s);
            }
        }
        
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                for(TransparencySetting s : selectionTransparency.values())
                    removeFilters.add(s.filter);
                selectionTransparency.clear();
                selectionTransparency.putAll(transparencySettings);
                addFilters.addAll(selectionTransparency.values());
            }
        });
    }

    /**************************************************************************/
    // Raster Data Store On Data Store Content Changed Listener
    
    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        renderContext.queueEvent(new Runnable() {
            public void run() {
                refreshImpl(subject);
            }
        });
    }
    
    /**************************************************************************/
    // GLResolvableMapRenderable
    
    @Override
    public State getState() {
        final GLMosaic mosaic = this.mosaic;
        if(mosaic == null)
            return State.UNRESOLVED;
        
        return mosaic.getState();
    }

    @Override
    public void suspend() {
        final GLMosaic mosaic = this.mosaic;
        if(mosaic != null)
            mosaic.suspend();
    }

    @Override
    public void resume() {
        final GLMosaic mosaic = this.mosaic;
        if(mosaic != null)
            mosaic.resume();
    }

    /**************************************************************************/
    
    // extend to provide public accessible invalidation mechanism
    private final static class GLMosaic extends GLMosaicMapLayer {
        public GLMosaic(MapRenderer surface, MosaicDatasetDescriptor desc) {
            super(surface, desc);
        }

        public synchronized String getAutoSelectValue() {
            if(this.visibleFrames.isEmpty())
                return null;
            else
                return this.visibleFrames.lastKey().type;
        }
    }

    private final static class TransparencySetting {
        public float value;
        public Filter<MosaicDatabase2.Frame> filter;
    }

    /**************************************************************************/
    // RasterDataAccess2
    
    private final class RasterDataAccessControlImpl implements RasterDataAccessControl {
        @Override
        public RasterDataAccess2 accessRasterData(GeoPoint point) {
            final GLMosaic mosaic = GLNativeImageryRasterLayer2.this.mosaic;
            if(mosaic == null)
                return null;
    
            // XXX - CLEAN UP!!!
            final RasterDataAccessControl ctrl = mosaic.getControl(RasterDataAccessControl.class);
            if(ctrl == null)
                return null;
            return ctrl.accessRasterData(point);
        }
    };

} // GLNativeImageryRasterLayer
