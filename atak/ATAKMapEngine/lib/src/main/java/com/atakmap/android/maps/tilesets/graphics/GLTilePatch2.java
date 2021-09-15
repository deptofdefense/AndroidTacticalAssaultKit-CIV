
package com.atakmap.android.maps.tilesets.graphics;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.android.maps.tilesets.OnlineTilesetSupport;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.android.maps.tilesets.TilesetSupport;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;

public class GLTilePatch2 implements GLMapLayer3, RasterDataAccess2, GLResolvableMapRenderable {

    private final static Set<String> SUPPORTED_TYPES = new HashSet<String>();
    static {
        SUPPORTED_TYPES.add("tileset");
    }
    
    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if(!(surface instanceof GLMapView))
                return null;
            if (!SUPPORTED_TYPES.contains(info.getDatasetType()))
                return null;
            if(!(info instanceof ImageDatasetDescriptor))
                return null;
            // XXX - avoid cast
            final GLBitmapLoader loader = GLRenderGlobals.get(surface).getBitmapLoader();
            final TilesetInfo tsInfo = new TilesetInfo((ImageDatasetDescriptor)info);
            final TilesetSupport support = TilesetSupport.create(tsInfo, loader);
            if (support == null)
                return null;
            return new GLTilePatch2(surface, tsInfo, support);
        }
    };

    public GLTilePatch2(MapRenderer ctx, TilesetInfo info, TilesetSupport support) {
        this.renderContext = ctx;
        this._tsInfo = info;
        this.uriResolver = support;

        final ImageDatasetDescriptor tsInfo = _tsInfo.getInfo();

        levelCount = info.getLevelCount();

        final double zeroHeight = this._tsInfo.getZeroHeight();
        final double zeroWidth = this._tsInfo.getZeroWidth();

        final double gridOriginLat = this._tsInfo.getGridOriginLat();
        final double gridOriginLng = this._tsInfo.getGridOriginLng();

        Envelope mbb = tsInfo.getCoverage(null).getEnvelope();

        double south = mbb.minY;
        double north = mbb.maxY;
        double west = mbb.minX;
        double east = mbb.maxX;

        // Shrink down on the 4 corners as much as we can
        GeoPoint sw = new GeoPoint(mbb.minY, mbb.minX);
        GeoPoint nw = new GeoPoint(mbb.maxY, mbb.minX);
        GeoPoint ne = new GeoPoint(mbb.maxY, mbb.maxX);
        GeoPoint se = new GeoPoint(mbb.minY, mbb.maxX);

        coverageSouth = Math.min(sw.getLatitude(), se.getLatitude());
        coverageNorth = Math.max(nw.getLatitude(), ne.getLatitude());
        coverageEast = Math.max(ne.getLongitude(), se.getLongitude());
        coverageWest = Math.min(nw.getLongitude(), sw.getLongitude());

        south = _alignMin(gridOriginLat, Math.max(coverageSouth, south), zeroHeight);
        west = _alignMin(gridOriginLng, Math.max(coverageWest, west), zeroWidth);
        north = _alignMax(gridOriginLat, Math.min(coverageNorth, north), zeroHeight);
        east = _alignMax(gridOriginLng, Math.min(coverageEast, east), zeroWidth);

        _gridHeight = this._tsInfo.getGridHeight();
        if (_gridHeight < 0)
            _gridHeight = (int) (((north - south) + EPSILON) / zeroHeight);
        _gridWidth = this._tsInfo.getGridWidth();
        if (_gridWidth < 0)
            _gridWidth = (int) (((east - west) + EPSILON) / zeroWidth);
        _gridX = this._tsInfo.getGridOffsetX();
        if (_gridX < 0)
            _gridX = (int) ((west - gridOriginLng + EPSILON) / zeroWidth);
        _gridY = this._tsInfo.getGridOffsetY();
        if (_gridY < 0)
            _gridY = (int) ((south - gridOriginLat + EPSILON) / zeroHeight);

        this.initialized = false;
        
        this.controls = Collections.newSetFromMap(new IdentityHashMap<MapControl, Boolean>());

        this.tileClientControl = (this.uriResolver instanceof OnlineTilesetSupport) ? new TileClientControlImpl() : null;
        this.rasterAccessControl = new RasterDataAccessControlImpl();
        this.colorControl = new ColorControlImpl();
        
        if(this.tileClientControl != null)
            this.controls.add(this.tileClientControl);

        this.controls.add(this.rasterAccessControl);
        this.controls.add(this.colorControl);
        
        if(this.tileClientControl != null)
            this.tileClientControl.setOfflineOnlyMode(false);
        
        this.proj = new DefaultDatasetProjection2(
                tsInfo.getSpatialReferenceID(),
                tsInfo.getWidth(),
                tsInfo.getHeight(),
                tsInfo.getUpperLeft(),
                tsInfo.getUpperRight(),
                tsInfo.getLowerRight(),
                tsInfo.getLowerLeft());
    }

    private void _init() {
        this.uriResolver.init();
        if(this.tileClientControl != null)
            this.tileClientControl.setOfflineOnlyMode(this.tileClientControl.isOfflineOnlyMode());
    }

    private static double _alignMin(double o, double v, double a) {
        double n = (v - o) / a;
        return (Math.floor(n) * a) + o;
    }

    private static double _alignMax(double o, double v, double a) {
        double n = (v - o) / a;
        return (Math.ceil(n) * a) + o;
    }

    @Override
    public void release() {
        synchronized(this) {
            if (_grid != null) {
                for (int lat = 0; lat < _gridHeight; ++lat) {
                    for (int lng = 0; lng < _gridWidth; ++lng) {
                        GLQuadTileNode2 n = _grid[lat][lng];
                        if (n != null) {
                            n.release();
                        }
                    }
                }
                _grid = null;
                _southIndex = 0;
                _westIndex = 0;
                _northIndex = 0;
                _eastIndex = 0;
            }
        }

        this.uriResolver.release();

        this.initialized = false;
    }

    public String toString() {
        return getLayerUri();
    }

    @Override
    public String getLayerUri() {
        return _tsInfo.getInfo().getUri();
    }

    public int getLevelCount() {
        return levelCount;
    }

    TilesetSupport getUriResolver() {
        return this.uriResolver;
    }

    // for layer converters that spit out stuff based on 32bit floats...
    private static final double EPSILON = 0.000000001;

    private synchronized void validateGrid(GLMapView view) {
        if(_grid == null) {
            _grid = new GLQuadTileNode2[_gridHeight][];
            for (int i = 0; i < _gridHeight; ++i) {
                _grid[i] = new GLQuadTileNode2[_gridWidth];
            }
        }

        int southIndex = this.uriResolver.getTileZeroY(view.southBound, _gridY, _gridHeight);
        southIndex = Math.max(0, Math.min(southIndex, _gridHeight - 1));

        int westIndex = this.uriResolver.getTileZeroX(view.westBound, _gridX, _gridWidth);
        westIndex = Math.max(0, Math.min(westIndex, _gridWidth - 1));

        int northIndex = this.uriResolver.getTileZeroY(view.northBound, _gridY, _gridHeight);
        northIndex = Math.max(0, Math.min(northIndex, _gridHeight - 1));

        int eastIndex = this.uriResolver.getTileZeroX(view.eastBound, _gridX, _gridWidth);
        eastIndex = Math.max(0, Math.min(eastIndex, _gridWidth - 1));

        // release out of screen tiles
        for (int lat = _southIndex; lat <= _northIndex; ++lat) {
            // release the row
            for (int lng = _westIndex; lng <= _eastIndex; ++lng) {
                if (lat < southIndex || lat > northIndex || lng < westIndex || lng > eastIndex) {
                    GLQuadTileNode2 n = _grid[lat][lng];

                    _grid[lat][lng] = null;
                    if (n != null) {
                        n.release();
                    }
                }
            }
        }

        // instantiate any root nodes as necessary
        for (int lat = southIndex; lat <= northIndex; ++lat) {
            for (int lng = westIndex; lng <= eastIndex; ++lng) {
                GLQuadTileNode2 n = _grid[lat][lng];
                if (n == null) {
                    n = new GLQuadTileNode2(this, lat + _gridY, lng + _gridX, batchDrawer);
                    n.setTextureCache(GLRenderGlobals.get(view).getTextureCache());
                    n.setColor(this.colorControl.getColor());
                    _grid[lat][lng] = n;
                }
            }
        }

        // update valid indices
        _southIndex = southIndex;
        _westIndex = westIndex;
        _northIndex = northIndex;
        _eastIndex = eastIndex;
    }

    @Override
    public void draw(GLMapView view) {
        if (!this.initialized) {
            _init();
            this.initialized = true;
        }

        validateGrid(view);

        batchDrawer.populateForwardMatrix( view );
        batchDrawer.setColor( colorControl.getColor() );
        this.uriResolver.start();
        try {
            // draw new screen state
            for (int lat = _southIndex; lat <= _northIndex; ++lat)
            {
                for( int lng = _westIndex; lng <= _eastIndex; ++lng )
                {
                    _grid[lat][lng].draw( view );
                }
            }
            batchDrawer.flush( );
        } finally {
            this.uriResolver.stop();
        }
    }

    @Override
    public ImageDatasetDescriptor getInfo() {
        return this._tsInfo.getInfo();
    }

    public TilesetInfo getTilesetInfo() {
        return this._tsInfo;
    }

    /**************************************************************************/
    // RasterDataAccess2

    @Override
    public String getType() {
        return this._tsInfo.getInfo().getDatasetType();
    }

    @Override
    public String getUri() {
        return this._tsInfo.getInfo().getUri();
    }

    @Override
    public boolean groundToImage(GeoPoint g, PointD p, boolean[] precise) {
        if(precise != null) precise[0] = false;
        return this.proj.groundToImage(g, p);
    }

    @Override
    public boolean imageToGround(PointD p, GeoPoint g, boolean[] precise) {
        if(precise != null) precise[0] = false;
        return this.proj.imageToGround(p, g);
    }

    @Override
    public int getSpatialReferenceId() {
        return _tsInfo.getInfo().getSpatialReferenceID();
    }

    @Override
    public boolean hasPreciseCoordinates() {
        return false;
    }

    @Override
    public int getWidth() {
        return _tsInfo.getInfo().getWidth();
    }

    @Override
    public int getHeight() {
        return _tsInfo.getInfo().getHeight();
    }

    /**************************************************************************/
    
    @Override
    public <T extends MapControl> T getControl(Class<T> clazz) {
        for(MapControl ctrl : this.controls)
            if(clazz.isAssignableFrom(ctrl.getClass()))
                return clazz.cast(ctrl);
        return null;
    }

    /**************************************************************************/

    final double coverageSouth, coverageNorth, coverageEast, coverageWest;

    private TilesetInfo _tsInfo;
    private int _gridWidth, _gridHeight;
    private int _gridX, _gridY;
    private int _southIndex = 0, _westIndex = 0, _northIndex = 0, _eastIndex = 0;
    private GLQuadTileNode2[][] _grid;
    private final int levelCount;

    private final TilesetSupport uriResolver;

    private boolean initialized;
    
    protected final TileClientControl tileClientControl;
    protected final RasterDataAccessControl rasterAccessControl;
    protected final ColorControl colorControl;
    
    protected Set<MapControl> controls;
    
    protected final DatasetProjection2 proj;

    protected final MapRenderer renderContext;

    /**
     * This is used to draw all the tiles in a batch
     */
    private final GLBatchTileTextureDrawer batchDrawer = new GLBatchTileTextureDrawer();
    
    @Override
    public synchronized State getState() {
        if(_grid == null)
            return State.UNRESOLVED;

        for (int lat = _southIndex; lat <= _northIndex; ++lat) {
            for (int lng = _westIndex; lng <= _eastIndex; ++lng) {
                switch(_grid[lat][lng].getState()) {
                    case RESOLVING :
                    case UNRESOLVED :
                        return State.RESOLVING;
                    default :
                        break;
                }
            }
        }
        
        return State.RESOLVED;
    }

    @Override
    public synchronized void suspend() {
        if(_grid == null)
            return;

        for (int lat = _southIndex; lat <= _northIndex; ++lat)
            for (int lng = _westIndex; lng <= _eastIndex; ++lng)
                _grid[lat][lng].suspend();
    }

    @Override
    public synchronized void resume() {
        if(_grid == null)
            return;

        for (int lat = _southIndex; lat <= _northIndex; ++lat)
            for (int lng = _westIndex; lng <= _eastIndex; ++lng)
                _grid[lat][lng].resume();
    }
    
    /**************************************************************************/
    
    private class TileClientControlImpl implements TileClientControl {
        private boolean offlineMode;

        @Override
        public void setOfflineOnlyMode(boolean offlineOnly) {
            synchronized(GLTilePatch2.this) {
                this.offlineMode = offlineOnly;
                ((OnlineTilesetSupport)uriResolver).setOfflineMode(offlineOnly);
            }
        }

        @Override
        public boolean isOfflineOnlyMode() {
            synchronized(GLTilePatch2.this) {
                return this.offlineMode;
            }
        }

        @Override
        public void refreshCache() {
        }

        @Override
        public void setCacheAutoRefreshInterval(long milliseconds) {
        }

        @Override
        public long getCacheAutoRefreshInterval() {
            return 0L;
        }
    }
    
    private class RasterDataAccessControlImpl implements RasterDataAccessControl {

        private final RasterDataAccess2 rasterData;

        RasterDataAccessControlImpl() {
            this.rasterData = GLTilePatch2.this; 
        }
        @Override
        public RasterDataAccess2 accessRasterData(GeoPoint point) {
            if (!Rectangle.contains(coverageWest,
                                    coverageSouth,
                                    coverageEast,
                                    coverageNorth,
                                    point.getLongitude(),
                                    point.getLatitude())) {

                return null;
            }

            return this.rasterData;
        }
    }
    
    private class ColorControlImpl implements ColorControl {
        private int color = -1;

        @Override
        public void setColor(final int color) {
            if(renderContext.isRenderThread()) {
                this.color = color;
                if(GLTilePatch2.this._grid != null) {
                    for(int i = 0; i < _grid.length; i++) {
                        for(int j = 0; j < _grid[i].length; j++) {
                            if(_grid[i][j] != null)
                                _grid[i][j].setColor(this.color);
                        }
                    }
                }
            } else {
                GLTilePatch2.this.renderContext.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        ColorControlImpl.this.color = color;
                        if(GLTilePatch2.this._grid != null) {
                            for(int i = 0; i < _grid.length; i++) {
                                for(int j = 0; j < _grid[i].length; j++) {
                                    if(_grid[i][j] != null)
                                        _grid[i][j].setColor(ColorControlImpl.this.color);
                                }
                            }
                        }
                    }
                });
            }
        }
        
        @Override
        public int getColor() {
            return this.color;
        }
    }
}
