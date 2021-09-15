package com.atakmap.map.layer.raster.tilematrix.opengl;

import java.util.Collection;
import java.util.HashSet;

import android.graphics.Color;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.layer.raster.tilematrix.opengl.GLTiledLayerCore;
import com.atakmap.map.layer.raster.tilematrix.opengl.GLZoomLevel;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.ConfigOptions;

/**
 * 
 * @author Developer
 *
 */
public class GLTileMatrixLayer implements GLMapLayer3 {
    
    private final MapRenderer renderer;
    private final DatasetDescriptor info;
    private final GLTiledLayerCore core;
    private GLZoomLevel[] zoomLevels;
    private Collection<MapControl> controls;
    
    /**
     * Creates a new <code>GLArcGISTiledMapService</code>
     * 
     * @param renderer  The renderer context
     * @param info      The dataset descriptor
     * @param baseUrl   The base URL of the map server
     * @param service   The data structure for the map service
     */
    public GLTileMatrixLayer(MapRenderer renderer, DatasetDescriptor info, TileMatrix matrix) {
        this.renderer = renderer;
        this.info = info;
        
        this.core = new GLTiledLayerCore(matrix, info.getUri());

        this.core.debugDraw = (ConfigOptions.getOption("imagery.debug-draw-enabled", 0) != 0);
        this.core.textureCache = GLRenderGlobals.get(renderer).getTextureCache();
        this.core.bitmapLoader = GLRenderGlobals.get(renderer).getBitmapLoader();
        
        this.core.r = 1f;
        this.core.g = 1f;
        this.core.b = 1f;
        this.core.a = 1f;      

        // controls
        //  - tile client control
        //  - color control
        //  - others ???

        this.controls = new HashSet<MapControl>();
        this.controls.add(new ColorControlImpl());
        this.controls.add(new TileClientControlImpl());
    }

    private void init() {
        final TileMatrix.ZoomLevel[] zLevels = this.core.matrix.getZoomLevel();
        this.zoomLevels = new GLZoomLevel[zLevels.length];
        GLZoomLevel prev = null;
        for(int i = 0; i < this.zoomLevels.length; i++) {
            this.zoomLevels[i] = new GLZoomLevel(prev, this.core, zLevels[i]);
            prev = this.zoomLevels[i];
        }
    }

    @Override
    public void draw(GLMapView view) {
        if(this.zoomLevels == null) {
            this.init();
        }

        this.core.drawPump();

        // compute the selection scale
        
        // account for difference in the tiling DPI and device display
/*
        final double dpiAdjust = core.service.tileInfo.dpi / (AtakMapView.DENSITY * 240d);
        final double selectScale = (1d / view.drawMapScale) * dpiAdjust * fudge;
*/
        // XXX - fudge select scale???
        final double fudge = 1.667d;
        final double selectRes = view.drawMapResolution*fudge; 

        GLZoomLevel toDraw = null;
        for(int i = 0; i < this.zoomLevels.length; i++) {
            if(zoomLevels[i].info.resolution >= selectRes)
                toDraw = zoomLevels[i];
            else if(zoomLevels[i].info.resolution < selectRes)
                break;
        }
        if(toDraw != null)
            toDraw.draw(view, GLMapView.RENDER_PASS_SURFACE);
        if(this.core.debugDraw)
            debugDraw(view);

        // release unused on end of pump
        if(!view.multiPartPass) {
            for (int i = this.zoomLevels.length - 1; i >= 0; i--) {
                if (zoomLevels[i] == toDraw)
                    continue;
                zoomLevels[i].release(true, view.currentPass.renderPump);
            }
        }
    }
    
    private void debugDraw(GLMapView view) {
        java.nio.ByteBuffer  dbg = com.atakmap.lang.Unsafe.allocateDirect(32);
        dbg.order(java.nio.ByteOrder.nativeOrder());
        view.scratch.pointD.x = core.fullExtent.minX;
        view.scratch.pointD.y = core.fullExtent.minY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(0, view.scratch.pointF.x);
        dbg.putFloat(4, view.scratch.pointF.y);
        view.scratch.pointD.x = core.fullExtent.minX;
        view.scratch.pointD.y = core.fullExtent.maxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(8, view.scratch.pointF.x);
        dbg.putFloat(12, view.scratch.pointF.y);
        view.scratch.pointD.x = core.fullExtent.maxX;
        view.scratch.pointD.y = core.fullExtent.maxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(16, view.scratch.pointF.x);
        dbg.putFloat(20, view.scratch.pointF.y);
        view.scratch.pointD.x = core.fullExtent.maxX;
        view.scratch.pointD.y = core.fullExtent.minY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(24, view.scratch.pointF.x);
        dbg.putFloat(28, view.scratch.pointF.y);        
        
        GLES20FixedPipeline.glColor4f(0f, 0f, 1f, 1f);
        
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glLineWidth(2f);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, dbg);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0, 4);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        Unsafe.free(dbg);
    }

    @Override
    public void release() {
        if(this.zoomLevels != null) {
            for(int i = 0; i < this.zoomLevels.length; i++)
                this.zoomLevels[i].release();
            this.zoomLevels = null;
        }
    }

    @Override
    public String getLayerUri() {
        return this.info.getUri();
    }

    @Override
    public DatasetDescriptor getInfo() {
        return this.info;
    }

    @Override
    public <T extends MapControl> T getControl(Class<T> clazz) {
        if(this.core.matrix instanceof Controls) {
            T ctrl = ((Controls)this.core.matrix).getControl(clazz);
            if(ctrl != null)
                return ctrl;
        }
        
        for(MapControl ctrl : this.controls) {
            if(clazz.isAssignableFrom(ctrl.getClass()))
                return clazz.cast(ctrl);
        }

        return null;
    }
    
    private class TileClientControlImpl implements TileClientControl {
        private long refreshInterval;
        private boolean manualRefreshRequested;
        private boolean offlineOnlyMode;
        private boolean dirty;

        TileClientControlImpl() {
            this.refreshInterval = 0L;
            this.manualRefreshRequested = false;
            this.offlineOnlyMode = false;
            this.dirty = false;
        }

        @Override
        public void setOfflineOnlyMode(boolean offlineOnly) {
            
        }

        @Override
        public boolean isOfflineOnlyMode() {
            return false;
        }

        @Override
        public void refreshCache() {
            core.requestRefresh();
        }

        @Override
        public void setCacheAutoRefreshInterval(long milliseconds) {
            core.refreshInterval = milliseconds;
        }

        @Override
        public long getCacheAutoRefreshInterval() {
            return core.refreshInterval;
        }        
    }
    
    private class ColorControlImpl implements ColorControl {
        @Override
        public void setColor(final int color) {
            if(GLTileMatrixLayer.this.renderer.isRenderThread()) {
                core.r = Color.red(color)/255f;
                core.g = Color.green(color)/255f;
                core.b = Color.blue(color)/255f;
                core.a = Color.alpha(color)/255f;
            } else {
                GLTileMatrixLayer.this.renderer.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        core.r = Color.red(color)/255f;
                        core.g = Color.green(color)/255f;
                        core.b = Color.blue(color)/255f;
                        core.a = Color.alpha(color)/255f;
                    }
                });
            }
        }
        
        @Override
        public int getColor() {
            return MathUtils.clamp((int)(core.a*255f), 0, 255) << 24 |
                   MathUtils.clamp((int)(core.r*255f), 0, 255) << 16 |
                   MathUtils.clamp((int)(core.g*255f), 0, 255) << 8 |
                   MathUtils.clamp((int)(core.b*255f), 0, 255);
        }
    }

}
