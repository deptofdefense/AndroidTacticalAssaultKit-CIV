package com.atakmap.map.layer.raster.tilematrix.opengl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.graphics.Point;

import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTileMesh;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLRenderBatch2;

/**
 * Renderer for a given zoom level of a Tiled Map Layer. The zoom level renderer
 * is responsible for subdividing the layer into one or more
 * <I>tile patches</I>, managing the lifetime of those patches and determing
 * which patches are to be drawn during a given render pump.
 * 
 * @author Developer
 *
 */
public class GLZoomLevel implements GLMapRenderable2, GLMapBatchable2 {
    private GLTiledLayerCore core;
    private Map<Integer, GLTilePatch> patches;

    public final TileMatrix.ZoomLevel info;
    private int patchRows;
    private int patchCols;
    private int patchesGridOffsetX;
    private int patchesGridOffsetY;
    private int numPatchesX;
    private int numPatchesY;
    
    /**
     * The previous zoom level.
     */
    public final GLZoomLevel previous;
    
    /**
     * The number of mesh subdivisions, per tile, that should be targeted for
     * tiles at this zoom level.
     */
    public int tileMeshSubdivisions;

    private int lastPumpDrawn;
    
    public GLZoomLevel(GLZoomLevel prev, GLTiledLayerCore core, TileMatrix.ZoomLevel lod) {
        this.previous = prev;
        this.core = core;
        this.info = lod;
        this.patchRows = 4;
        this.patchCols = 4;

        Point minTile = TileMatrix.Util.getTileIndex(core.matrix, info.level, core.fullExtent.minX, core.fullExtent.maxY);
        Point maxTile = TileMatrix.Util.getTileIndex(core.matrix, info.level, core.fullExtent.maxX, core.fullExtent.minY);

        int minPatchGridCol = minTile.x / patchCols;
        int minPatchGridRow = minTile.y / patchRows;
        int maxPatchGridCol = (maxTile.x / patchCols) + 1;
        int maxPatchGridRow = (maxTile.y / patchRows) + 1;
                
        patchesGridOffsetX = minPatchGridCol;
        patchesGridOffsetY = minPatchGridRow;
        numPatchesX = maxPatchGridCol-minPatchGridCol;
        numPatchesY = maxPatchGridRow-minPatchGridRow;
        
        this.patches = new HashMap<Integer, GLTilePatch>();
        
        final int ntx = maxTile.x - minTile.x + 1;
        final int nty = maxTile.y - minTile.y + 1;
        
        this.tileMeshSubdivisions = (int)Math.ceil((double)GLTileMesh.estimateSubdivisions(this.core.fullExtentMaxLat, this.core.fullExtentMinLng, this.core.fullExtentMinLat, this.core.fullExtentMinLng) / (double)Math.max(ntx, nty));

        this.lastPumpDrawn = -1;
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.getRenderPass()))
            return;
        
        // compute patch geo intersect with view
        if(!Rectangle.intersects(core.fullExtentMinLng, core.fullExtentMinLat,
                                 core.fullExtentMaxLng, core.fullExtentMaxLat,
                                 view.westBound, view.southBound,
                                 view.eastBound, view.northBound)) {

            return;
        }

        this.lastPumpDrawn = view.currentPass.renderPump;

        final double isectMinLat = Math.max(core.fullExtentMinLat, view.southBound);
        final double isectMinLng = Math.max(core.fullExtentMinLng, view.westBound);
        final double isectMaxLat = Math.min(core.fullExtentMinLat, view.northBound);
        final double isectMaxLng = Math.min(core.fullExtentMinLng, view.eastBound);
        
        // transform patch intersect to proj
        view.scratch.geo.set(isectMinLat, isectMinLng);
        core.proj.forward(view.scratch.geo, view.scratch.pointD);
        double projIsectMinX = view.scratch.pointD.x;
        double projIsectMinY = view.scratch.pointD.y;
        view.scratch.geo.set(isectMaxLat, isectMaxLng);
        core.proj.forward(view.scratch.geo, view.scratch.pointD);
        double projIsectMaxX = view.scratch.pointD.x;
        double projIsectMaxY = view.scratch.pointD.y;

        // calculate tiles in view
        Point minPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                                                      core.matrix.getOriginY(),
                                                      info,
                                                      projIsectMinX,
                                                      projIsectMaxY);
        minPatch.x /= patchCols;
        minPatch.y /= patchRows;
        Point maxPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                                                      core.matrix.getOriginY(),
                                                      info,
                                                      projIsectMaxX,
                                                      projIsectMinY);
        maxPatch.x /= patchCols;
        maxPatch.y /= patchRows;

        for(int patchY = minPatch.y; patchY <= maxPatch.y; patchY++) {
            if(patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY+numPatchesY))
                continue;
            for(int patchX = minPatch.x; patchX <= maxPatch.x; patchX++) {
                if(patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX+numPatchesX))
                    continue;
                
                int idx = ((patchY-patchesGridOffsetY)*numPatchesX) + (patchX-patchesGridOffsetX);
                GLTilePatch patch = this.patches.get(idx);
                if(patch == null) {
                    patch = new GLTilePatch(core,
                            this,
                            patchesGridOffsetX+patchX*patchCols,
                            patchesGridOffsetY+patchY*patchRows,
                            Math.min(patchCols, (patchesGridOffsetX+numPatchesX)-(patchX*patchCols)),
                            Math.min(patchRows, (patchesGridOffsetY+numPatchesY)-(patchY*patchRows)));
                    patches.put(idx, patch);
                }

                patch.batch(view, batch, renderPass);
            }
        }
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.getRenderPass()))
            return;

        // compute patch geo intersect with view
        if (!Rectangle.intersects(core.fullExtentMinLng, core.fullExtentMinLat,
                core.fullExtentMaxLng, core.fullExtentMaxLat,
                view.westBound, view.southBound,
                view.eastBound, view.northBound)) {

            return;
        }

        this.lastPumpDrawn = view.currentPass.renderPump;

        final double isectMinLat = Math.max(core.fullExtentMinLat, view.southBound);
        final double isectMinLng = Math.max(core.fullExtentMinLng, view.westBound);
        final double isectMaxLat = Math.min(core.fullExtentMaxLat, view.northBound);
        final double isectMaxLng = Math.min(core.fullExtentMaxLng, view.eastBound);

        // transform patch intersect to proj
        view.scratch.geo.set(isectMinLat, isectMinLng);
        core.proj.forward(view.scratch.geo, view.scratch.pointD);
        double projIsectMinX = view.scratch.pointD.x;
        double projIsectMinY = view.scratch.pointD.y;
        view.scratch.geo.set(isectMaxLat, isectMaxLng);
        core.proj.forward(view.scratch.geo, view.scratch.pointD);
        double projIsectMaxX = view.scratch.pointD.x;
        double projIsectMaxY = view.scratch.pointD.y;

        // calculate tiles in view
        Point minPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                core.matrix.getOriginY(),
                info,
                projIsectMinX,
                projIsectMaxY);
        minPatch.x /= patchCols;
        minPatch.y /= patchRows;
        Point maxPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                core.matrix.getOriginY(),
                info,
                projIsectMaxX,
                projIsectMinY);
        maxPatch.x /= patchCols;
        maxPatch.y /= patchRows;

        for (int patchY = minPatch.y; patchY <= maxPatch.y; patchY++) {
            if (patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY + numPatchesY))
                continue;
            for (int patchX = minPatch.x; patchX <= maxPatch.x; patchX++) {
                if (patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX + numPatchesX))
                    continue;

                int idx = ((patchY - patchesGridOffsetY) * numPatchesX) + (patchX - patchesGridOffsetX);
                GLTilePatch patch = this.patches.get(idx);
                if (patch == null) {
                    // XXX - should clamp number of patches against full extent?
                    patch = new GLTilePatch(core,
                            this,
                            patchX * patchCols,
                            patchY * patchRows,
                            patchCols, patchRows);
                    patches.put(idx, patch);
                }

                patch.draw(view, renderPass);
            }
        }
    }

    boolean release(boolean unusedOnly, int renderPump) {
        if(!unusedOnly) {
            for(GLTilePatch patch : this.patches.values()) {
                patch.release();
            }
            patches.clear();
            return true;
        } else {
            Iterator<GLTilePatch> iter = this.patches.values().iterator();
            while(iter.hasNext()) {
                GLTilePatch patch = iter.next();
                if(patch.release(unusedOnly, renderPump))
                    iter.remove();
            }
            return patches.isEmpty();
        }
    }

    @Override
    public void release() {
        this.release(false, -1);
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }
    
    /**
     * Obtains all patches that intersect the specified region that either have
     * live tiles intersecting the region or have tiles whose textures are
     * cached intersecting the region.
     * 
     * @param tiles
     * @param minX
     * @param minY
     * @param maxX
     * @param maxY
     */
    void getTiles(Set<GLTile> tiles, double minX, double minY, double maxX, double maxY) {
        // calculate tiles in view
        Point minPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                                                      core.matrix.getOriginY(),
                                                      info,
                                                      minX,
                                                      maxY);
        minPatch.x /= patchCols;
        minPatch.y /= patchRows;
        Point maxPatch = TileMatrix.Util.getTileIndex(core.matrix.getOriginX(),
                                                      core.matrix.getOriginY(),
                                                      info,
                                                      maxX,
                                                      minY);
        maxPatch.x /= patchCols;
        maxPatch.y /= patchRows;
                
        for(int patchY = minPatch.y; patchY <= maxPatch.y; patchY++) {
            if(patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY+numPatchesY))
                continue;
            for(int patchX = minPatch.x; patchX <= maxPatch.x; patchX++) {
                if(patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX+numPatchesX))
                    continue;
                
                int idx = ((patchY-patchesGridOffsetY)*numPatchesX) + (patchX-patchesGridOffsetX); 
                GLTilePatch patch = this.patches.get(idx);
                if(patch == null) {
                    patch = new GLTilePatch(core,
                            this,
                            patchX*patchCols,
                            patchY*patchRows,
                            patchCols, patchRows);
                    patches.put(idx, patch);
                }
                if(patch != null)
                    patch.getTiles(tiles, minX, minY, maxX, maxY);
            }
        }
    }

}
