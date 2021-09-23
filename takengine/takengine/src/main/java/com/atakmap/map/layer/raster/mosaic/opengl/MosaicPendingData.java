package com.atakmap.map.layer.raster.mosaic.opengl;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.spatial.SpatialCalculator;

class MosaicPendingData {

    /** holds the results of the query */
    public final Set<MosaicDatabase2.Frame> frames;
    /** calculates occlusion */
    public final SpatialCalculator spatialCalc;
    /** list of currently rendered frame URIs */
    public final Set<String> loaded;
    /**
     * May hold renderables that are <I>instantiated</I> (not
     * <I>initialized</I>) during the query. The renderables should not contain
     * any of the currently rendered frames as per {@link #loaded}.
     */
    public final Map<MosaicDatabase2.Frame, GLResolvableMapRenderable> renderablePreload;
    
    public MosaicDatabase2 database;
    
    public MosaicPendingData() {
        this.frames = new HashSet<>();
        this.spatialCalc = new SpatialCalculator(true);
        this.loaded = new HashSet<String>();
        this.renderablePreload = new IdentityHashMap<MosaicDatabase2.Frame, GLResolvableMapRenderable>();
        this.database = null;
    }
}
