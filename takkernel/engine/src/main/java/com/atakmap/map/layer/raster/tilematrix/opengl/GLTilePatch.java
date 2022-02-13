package com.atakmap.map.layer.raster.tilematrix.opengl;

import java.util.Set;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLText;

/**
 * A collection of some subset of tile renderers for a contiguous region of a
 * Tiled Map Layer at a given zoom level.
 * 
 * <P>The patch is responsible for managing the lifetime of the tile renderers
 * for the tile region that it covers and selecting which tiles for the region
 * are to be rendered during the draw pump.
 *   
 * @author Developer
 *
 */
public class GLTilePatch implements GLMapRenderable2, GLMapBatchable2{

    private GLTile[] tiles;
    private GLTiledLayerCore core;
    final GLZoomLevel parent;

    /**
     * The column grid offset, in tiles, to the first tile of the patch. 
     */
    public int gridOffsetX;
    /**
     * The row grid offset, in tiles, to the first tile of the patch. 
     */
    public int gridOffsetY;
    /**
     * The number of tile columns contained by the patch.
     */
    public int gridColumns;
    /**
     * The number of tile rows contained by the patch.
     */
    public int gridRows;
    
    private double patchMinLat;
    private double patchMinLng;
    private double patchMaxLat;
    private double patchMaxLng;

    /**
     * The render pump that this patch was last drawn
     */
    int lastPumpDrawn;
    
    /**
     * Creates a new tile patch.
     * 
     * @param core          The core data structure for the tiled layer that the
     *                      tile belongs to
     * @param parent        The zoom level that the patch belongs to
     * @param gridOffsetX   The column grid offset, in tiles, to the first tile
     *                      of the patch.
     * @param gridOffsetY   The row grid offset, in tiles, to the first tile of
     *                      the patch.
     * @param gridColumns   The number of tile columns contained by the patch.
     * @param gridRows      The number of tile rows contained by the patch.
     */
    public GLTilePatch(GLTiledLayerCore core, GLZoomLevel parent, int gridOffsetX, int gridOffsetY, int gridColumns, int gridRows) {
        this.tiles = new GLTile[gridColumns*gridRows];
        this.core = core;
        this.parent = parent;
        this.gridOffsetX = gridOffsetX;
        this.gridOffsetY = gridOffsetY;
        this.gridColumns = gridColumns;
        this.gridRows = gridRows;
        
        final double lodProjTileWidth = parent.info.pixelSizeX*parent.info.tileWidth;
        final double lodProjTileHeight = parent.info.pixelSizeY*parent.info.tileHeight;
        
        final double patchProjMinX = core.matrix.getOriginX() + (gridOffsetX*lodProjTileWidth);
        final double patchProjMinY = core.matrix.getOriginY() - ((gridOffsetY*lodProjTileWidth) + (lodProjTileHeight*gridRows));
        final double patchProjMaxX = core.matrix.getOriginX() + ((gridOffsetX*lodProjTileHeight) + (lodProjTileWidth*gridColumns));
        final double patchProjMaxY = core.matrix.getOriginY() - (gridOffsetY*lodProjTileHeight);
        
        PointD scratchD = new PointD(0d, 0d, 0d);
        GeoPoint scratchG = GeoPoint.createMutable();
        
        scratchD.x = patchProjMinX;
        scratchD.y = patchProjMinY;
        core.proj.inverse(scratchD, scratchG);
        double lat0 = scratchG.getLatitude();
        double lng0 = scratchG.getLongitude();
        scratchD.x = patchProjMinX;
        scratchD.y = patchProjMaxY;
        core.proj.inverse(scratchD, scratchG);
        double lat1 = scratchG.getLatitude();
        double lng1 = scratchG.getLongitude();
        scratchD.x = patchProjMaxX;
        scratchD.y = patchProjMaxY;
        core.proj.inverse(scratchD, scratchG);
        double lat2 = scratchG.getLatitude();
        double lng2 = scratchG.getLongitude();
        scratchD.x = patchProjMaxX;
        scratchD.y = patchProjMinY;
        core.proj.inverse(scratchD, scratchG);
        double lat3 = scratchG.getLatitude();
        double lng3 = scratchG.getLongitude();
        
        patchMinLat = MathUtils.min(lat0, lat1, lat2, lat3);
        patchMinLng = MathUtils.min(lng0, lng1, lng2, lng3);
        patchMaxLat = MathUtils.max(lat0, lat1, lat2, lat3);
        patchMaxLng = MathUtils.max(lng0, lng1, lng2, lng3);

        lastPumpDrawn = -1;
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.getRenderPass()))
            return;
        
        final double lodProjTileWidth = parent.info.pixelSizeX*parent.info.tileWidth;
        final double lodProjTileHeight = parent.info.pixelSizeY*parent.info.tileHeight;
        
        // compute patch geo intersect with view
        if(!Rectangle.intersects(patchMinLng, patchMinLat,
                                 patchMaxLng, patchMaxLat,
                                 view.westBound, view.southBound,
                                 view.eastBound, view.northBound)) {

            return;
        }

        this.lastPumpDrawn = view.currentPass.renderPump;

        final double isectMinLat = Math.max(patchMinLat, view.southBound);
        final double isectMinLng = Math.max(patchMinLng, view.westBound);
        final double isectMaxLat = Math.min(patchMinLat, view.northBound);
        final double isectMaxLng = Math.min(patchMinLng, view.eastBound);
        
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
        int minTileDrawCol = (int)((projIsectMinX-core.matrix.getOriginX()) / lodProjTileWidth);
        int minTileDrawRow = (int)((core.matrix.getOriginY()-projIsectMinY) / lodProjTileHeight);
        int maxTileDrawCol = (int)((projIsectMaxX-core.matrix.getOriginX()) / lodProjTileWidth);
        int maxTileDrawRow = (int)((core.matrix.getOriginY()-projIsectMaxY) / lodProjTileHeight);
        
        for(int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
            if(row < gridOffsetY || row >= (gridOffsetY + gridRows))
                continue;
            for(int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
                if(col < gridOffsetX || col >= (gridOffsetX + gridColumns))
                    continue;
                
                int idx = ((row-gridOffsetY)*gridColumns) + (col-gridOffsetX);
                if(tiles[idx] == null) {
                    // init tile
                    tiles[idx] = new GLTile(core, this, col, row);
                }
                
                tiles[idx].batch(view, batch, renderPass);
            }
        }
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.getRenderPass()))
            return;
        
        final double lodProjTileWidth = parent.info.pixelSizeX*parent.info.tileWidth;
        final double lodProjTileHeight = parent.info.pixelSizeY*parent.info.tileHeight;
        
        // compute patch geo intersect with view
        if(!Rectangle.intersects(patchMinLng, patchMinLat,
                                 patchMaxLng, patchMaxLat,
                                 view.westBound, view.southBound,
                                 view.eastBound, view.northBound)) {

            return;
        }

        this.lastPumpDrawn = view.currentPass.renderPump;

        if(core.debugDraw)
            this.debugDraw(view);

        final double isectMinLat = Math.max(patchMinLat, view.southBound);
        final double isectMinLng = Math.max(patchMinLng, view.westBound);
        final double isectMaxLat = Math.min(patchMaxLat, view.northBound);
        final double isectMaxLng = Math.min(patchMaxLng, view.eastBound);
        
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
        int minTileDrawCol = (int)((projIsectMinX-core.matrix.getOriginX()) / lodProjTileWidth);
        int minTileDrawRow = (int)((core.matrix.getOriginY()-projIsectMaxY) / lodProjTileHeight);
        int maxTileDrawCol = (int)((projIsectMaxX-core.matrix.getOriginX()) / lodProjTileWidth);
        int maxTileDrawRow = (int)((core.matrix.getOriginY()-projIsectMinY) / lodProjTileHeight);
        
        for(int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
            if(row < gridOffsetY || row >= (gridOffsetY + gridRows))
                continue;
            for(int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
                if(col < gridOffsetX || col >= (gridOffsetX + gridColumns))
                    continue;
                
                int idx = ((row-gridOffsetY)*gridColumns) + (col-gridOffsetX);
                if(tiles[idx] == null) {
                    // init tile
                    tiles[idx] = new GLTile(core, this, col, row);
                }
                
                tiles[idx].draw(view, renderPass);
            }
        }
    }

    private void debugDraw(GLMapView view) {
        java.nio.ByteBuffer  dbg = com.atakmap.lang.Unsafe.allocateDirect(32);
        dbg.order(java.nio.ByteOrder.nativeOrder());
        view.scratch.geo.set(patchMinLat, patchMinLng);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(0, view.scratch.pointF.x);
        dbg.putFloat(4, view.scratch.pointF.y);
        view.scratch.geo.set(patchMaxLat, patchMinLng);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(8, view.scratch.pointF.x);
        dbg.putFloat(12, view.scratch.pointF.y);
        view.scratch.geo.set(patchMaxLat, patchMaxLng);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(16, view.scratch.pointF.x);
        dbg.putFloat(20, view.scratch.pointF.y);
        view.scratch.geo.set(patchMinLat, patchMaxLng);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(24, view.scratch.pointF.x);
        dbg.putFloat(28, view.scratch.pointF.y);        
        
        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
        
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glLineWidth(3f);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, dbg);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0, 4);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        view.scratch.geo.set(patchMaxLat, patchMinLng);
        view.forward(view.scratch.geo, view.scratch.pointF);
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(view.scratch.pointF.x+5, view.scratch.pointF.y-40, 0f);
        GLText.getInstance(view.getRenderContext(), AtakMapView.getDefaultTextFormat()).draw("patch " + parent.info.level + "," + gridOffsetX + "," + gridOffsetY, 1f, 0f, 0f, 1f);
        GLES20FixedPipeline.glPopMatrix();
        
        Unsafe.free(dbg);
    }

    boolean release(boolean unusedOnly, int renderPump) {
        boolean cleared = true;
        for(int i = 0; i < this.tiles.length; i++) {
            if(this.tiles[i] != null && (!unusedOnly || !this.tiles[i].hasBorrowers()) && this.tiles[i].lastPumpDrawn != renderPump) {
                this.tiles[i].release();
                this.tiles[i] = null;
            } else if(this.tiles[i] != null) {
                cleared = false;
            }
        }
        return cleared;
    }

    @Override
    public void release() {
        this.release(false, -1);
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }
    
    void getTiles(Set<GLTile> tiles, double minX, double minY, double maxX, double maxY) {
        final double lodProjTileWidth = parent.info.pixelSizeX*parent.info.tileWidth;
        final double lodProjTileHeight = parent.info.pixelSizeY*parent.info.tileHeight;

        // calculate tiles in view
        int minTileDrawCol = (int)((minX-core.matrix.getOriginX()) / lodProjTileWidth);
        int minTileDrawRow = (int)((core.matrix.getOriginY()-maxY) / lodProjTileHeight);
        int maxTileDrawCol = (int)((maxX-core.matrix.getOriginX()) / lodProjTileWidth);
        int maxTileDrawRow = (int)((core.matrix.getOriginY()-minY) / lodProjTileHeight);
        
        for(int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
            if(row < gridOffsetY || row >= (gridOffsetY + gridRows))
                continue;
            for(int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
                if(col < gridOffsetX || col >= (gridOffsetX + gridColumns))
                    continue;
                
                int idx = ((row-gridOffsetY)*gridColumns) + (col-gridOffsetX);
                if(this.tiles[idx] == null) {
                    // XXX - check if texture is cached
                    final String textureKey = GLTile.getTileTextureKey(core, this.parent.info.level, col, row);
                    if(core.textureCache.get(textureKey) != null) {
                        // init tile
                        this.tiles[idx] = new GLTile(core, this, col, row);
                        tiles.add(this.tiles[idx]);
                    }
                } else if(this.tiles[idx].texture != null) {
                    tiles.add(this.tiles[idx]);
                }
            }
        }
    }
}
