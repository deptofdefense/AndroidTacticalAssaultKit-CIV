package com.atakmap.map.layer.raster.tilematrix.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTileMesh;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.opengl.GLText;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.spatial.GeometryTransformer;
import com.atakmap.util.Disposable;

/**
 * Renderer for a single tile for a Tiled Map Layer. The tile renderer is
 * responsible for requesting the loading of the tile, uploading the tile data
 * to a texture, managing the lifetime of the tile texture and rendering the
 * tile data to the screen during the draw pump.
 * 
 * @author Developer
 *
 */
public class GLTile implements GLMapRenderable2, GLMapBatchable2, GLResolvable {

    private final static String TAG = "GLTile";
    
    GLTexture texture;
    private final Matrix proj2uv;
    private final double projMinX;
    private final double projMinY;
    private final double projMaxX;
    private final double projMaxY;
    private State state;
    private Pair<FutureTask<Bitmap>, Integer> pendingTexture;
    
    private FloatBuffer textureCoords;
    private FloatBuffer vertexCoords;
    private long vertexCoordsPtr;

    private final GLTiledLayerCore core;
    private final GLTilePatch patch;
        
    private final GLTileMesh mesh;

    final int tileX;
    final int tileY;
    final int tileZ;
    
    private final String textureKey;
    
    private final Set<GLTile> borrowers;
    private Set<BorrowRecord> borrowRecords;
    private int tileVersion;

    int lastPumpDrawn;

    /**
     * Creates a new tile renderer.
     * 
     * @param core      The core data structure for the tiled layer that the
     *                  tile belongs to
     * @param patch     The patch that the tile is a child of
     * @param url       The URL to the tile
     * @param projMinX  The minimum x-coordinate of the tile, in the projected
     *                  coordinate space
     * @param projMinY  The minimum y-coordinate of the tile, in the projected
     *                  coordinate space
     * @param projMaxX  The maximum x-coordinate of the tile, in the projected
     *                  coordinate space
     * @param projMaxY  The maximum y-coordinate of the tile, in the projected
     *                  coordinate space
     */
    public GLTile(GLTiledLayerCore core, GLTilePatch patch, int tileX, int tileY) {
        this.core = core;
        this.patch = patch;
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileZ = patch.parent.info.level;

        Envelope tileBounds = TileMatrix.Util.getTileBounds(core.matrix, tileZ, tileX, tileY);
        this.projMinX = tileBounds.minX;
        this.projMinY = tileBounds.minY;
        this.projMaxX = tileBounds.maxX;
        this.projMaxY = tileBounds.maxY;
        
        this.state = State.UNRESOLVED;
        this.pendingTexture = null;
        
        this.texture = null;
        this.textureCoords = null;
        this.vertexCoords = null;
        this.vertexCoordsPtr = 0L;

        this.textureKey = getTileTextureKey(core, tileZ, tileX, tileY);

        
        // XXX - better way to do this
        GLTexture scratchTex = new GLTexture(this.patch.parent.info.tileWidth, this.patch.parent.info.tileHeight, 0, 0);
        
        final float u0 = 0f;
        final float v0 = 0f;
        final float u1 = (float)this.patch.parent.info.tileWidth / (float)scratchTex.getTexWidth();
        final float v1 = (float)this.patch.parent.info.tileHeight / (float)scratchTex.getTexHeight();
                
        this.proj2uv = Matrix.mapQuads(projMinX, projMaxY,
                                       projMaxX, projMaxY,
                                       projMaxX, projMinY,
                                       projMinX, projMinY,
                                       u0, v0,
                                       u1, v0,
                                       u1, v1,
                                       u0, v1);
        
        this.mesh = new GLTileMesh(new PointD(projMinX, projMaxY),
                                   new PointD(projMaxX, projMaxY),
                                   new PointD(projMaxX, projMinY),
                                   new PointD(projMinX, projMinY),
                                   this.proj2uv,
                                   this.core.proj2geo,
                                   this.patch.parent.tileMeshSubdivisions);
        
        this.borrowers = Collections.newSetFromMap(new IdentityHashMap<GLTile, Boolean>());
        this.borrowRecords = new HashSet<BorrowRecord>();
        
        this.tileVersion = -1;

        this.lastPumpDrawn = -1;
    }

    /**
     * Returns <code>true</code> if any tiles are borrowing this tile's
     * texture.
     *  
     * @return  <code>true</code> if any tiles are borrowing this tile's
     *          texture, <code>false</code> otherwise.
     */
    public boolean hasBorrowers() {
        return !this.borrowers.isEmpty();
    }
    
    /**
     * Tries to borrow textures from other tiles.
     */
    private void tryBorrow() {
        // the candidates we will try to borrow from
        Set<GLTile> candidates = new HashSet<GLTile>();
        
        // a list of the "holes" that need to be filled via texture borrowing
        LinkedList<Rectangle> holes = new LinkedList<Rectangle>();
        // seed with the bounds of this tile
        holes.add(new Rectangle(this.projMinX,
                                this.projMinY,
                                this.projMaxX-this.projMinX,
                                this.projMaxY-this.projMinY));
        
        // a list of "bits" left over from holes that we have subtracted the
        // intersection with a candidate from
        LinkedList<Rectangle> bits = new LinkedList<Rectangle>();

        // the remainders of subtracting a candidates bounds from a hole
        Rectangle[] remainders = new Rectangle[4];
        
        // we will start borrowing from the previous zoom level
        GLZoomLevel borrowLvl = this.patch.parent.previous;
        
        // while there are holes to be filled and we have a level to gather
        // candidates from, search for textures to borrow
        while(!holes.isEmpty() && borrowLvl != null) {
            // discover all tiles in the level that intersect this tile's bounds
            borrowLvl.getTiles(candidates, projMinX, projMinY, projMaxX, projMaxY);
            
            for(GLTile candidate : candidates) {
                // ensure we can borrow from the tile
                if(candidate.state == State.UNRESOLVABLE)
                    continue;

                bits.clear();

                Iterator<Rectangle> iter = holes.iterator();
                while(iter.hasNext()) {
                    Rectangle hole = iter.next();
                    if(Rectangle.intersects(hole.X, hole.Y, hole.X+hole.Width, hole.Y+hole.Height, candidate.projMinX, candidate.projMinY, candidate.projMaxX, candidate.projMaxY)) {
                        // compute the intersection
                        final double isectMinX = Math.max(hole.X, candidate.projMinX);
                        final double isectMinY = Math.max(hole.Y, candidate.projMinY);
                        final double isectMaxX = Math.min(hole.X+hole.Width, candidate.projMaxX);
                        final double isectMaxY = Math.min(hole.Y+hole.Height, candidate.projMaxY);
                        
                        // if the intersection is less than a pixel in either
                        // dimension, ignore it
                        if((isectMaxX-isectMinX) <= candidate.patch.parent.info.pixelSizeX || (isectMaxY-isectMinY) < candidate.patch.parent.info.pixelSizeY)
                            continue;

                        // subtract the coverage of the candidate tile from the
                        // hole. each remainder will become a new hole.
                        int num = Rectangle.subtract(hole.X,
                                                     hole.Y, hole.X+hole.Width,
                                                     hole.Y+hole.Height,
                                                     candidate.projMinX,
                                                     candidate.projMinY,
                                                     candidate.projMaxX,
                                                     candidate.projMaxY,
                                                     remainders);
                        for(int i = 0; i < num; i++) {
                            // ignore any remainders less than a pixel in either
                            // dimension
                            if(remainders[i].Width <= candidate.patch.parent.info.pixelSizeX || remainders[i].Height <= candidate.patch.parent.info.pixelSizeY)
                                continue;
                            bits.add(remainders[i]);
                        }
                        
                        // add a borrow record for the intersection
                        borrowRecords.add(new BorrowRecord(candidate,
                                                           isectMinX,
                                                           isectMinY,
                                                           isectMaxX,
                                                           isectMaxY));
                        
                        // remove the old hole
                        iter.remove();
                    }
                }
                
                // add all the remainders
                holes.addAll(bits);
            }
            
            // clear the candidates list for the current level and move to the
            // previous level
            candidates.clear();
            borrowLvl = borrowLvl.previous;
        }
    }

    /**
     * Stops borrow textures from all other tiles.
     */
    private void unborrow() {
        for(BorrowRecord record : this.borrowRecords)
            record.dispose();
        this.borrowRecords.clear();
    }
    
    private boolean checkForCachedTexture() {
        if(this.core.textureCache == null)
            return false;
        GLTextureCache.Entry entry = this.core.textureCache.remove(this.textureKey);
        if(entry == null)
            return false;
        this.texture = entry.texture;
        this.textureCoords = (FloatBuffer)entry.textureCoordinates;
        this.vertexCoords = (FloatBuffer)entry.vertexCoordinates;
        this.vertexCoordsPtr = Unsafe.getBufferPointer(this.vertexCoords);
        if(entry.opaque instanceof Integer) {
            this.tileVersion = ((Integer)entry.opaque).intValue();
        } else {
            this.tileVersion = -1;
        }
        this.state = State.RESOLVED;
        return true;
    }

    private boolean renderCommon(final GLMapView view, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.getRenderPass()))
            return false;

        this.lastPumpDrawn = view.currentPass.renderPump;

        do {
            if(this.state == State.UNRESOLVED) {
                // check cache
                if(this.checkForCachedTexture())
                    break;
                
                // kick off tile load
                final Callable<Bitmap> bitmapLoader = new Callable<Bitmap>() {
                    @Override
                    public Bitmap call() throws Exception {
                        Exception thrown = null;
                        Bitmap result = null;
                        try {
                            result = core.matrix.getTile(tileZ, tileX, tileY, null);
                        } catch(Exception t) {
                            thrown = t;
                        }

                        // post a refresh
                        view.requestRefresh();
                        if(thrown != null)
                            throw thrown;
                        SurfaceRendererControl ctrl = view.getControl(SurfaceRendererControl.class);
                        if(ctrl != null) {
                            ctrl.markDirty(GeometryTransformer.transform(new Envelope(projMinX, projMinY, 0, projMaxX, projMaxY, 0), core.proj, EquirectangularMapProjection.INSTANCE), false);
                        }
                        return result;
                    }
                };
                pendingTexture = Pair.<FutureTask<Bitmap>, Integer>create(new FutureTask<Bitmap>(bitmapLoader), Integer.valueOf(core.tileDrawVersion));
                core.bitmapLoader.loadBitmap(pendingTexture.first, GLBitmapLoader.QueueType.REMOTE);

                this.state = State.RESOLVING;
                
                this.tryBorrow();
                continue;
            } else if(this.state == State.RESOLVING) {
                // check if loading is completed, transition to RESOLVED or
                // UNRESOLVABLE as appropriate
                if(this.pendingTexture.first.isDone()) {
                    Bitmap bitmap = null;
                    try {
                        bitmap = this.pendingTexture.first.get();
                        if(bitmap != null) {
                            if(this.texture != null) {
                                this.texture.release();
                                this.texture = null;
                            }

                            this.texture = new GLTexture(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
                            this.texture.load(bitmap);
                            this.tileVersion = this.pendingTexture.second;
                            
                            float u0 = 0f;
                            float v0 = 0f;
                            float u1 = (float)bitmap.getWidth() / this.texture.getTexWidth();
                            float v1 = (float)bitmap.getHeight() / this.texture.getTexHeight();

                            ByteBuffer buf;
                            
                            buf = Unsafe.allocateDirect(8 * 4);
                            buf.order(ByteOrder.nativeOrder());
                            this.textureCoords = buf.asFloatBuffer();
                            this.textureCoords.put(0, u0); // lower-left
                            this.textureCoords.put(1, v1);
                            this.textureCoords.put(2, u0); // upper-left
                            this.textureCoords.put(3, v0);
                            this.textureCoords.put(4, u1); // lower-right
                            this.textureCoords.put(5, v1);
                            this.textureCoords.put(6, u1); // upper-right
                            this.textureCoords.put(7, v0);
                            
                            buf = Unsafe.allocateDirect(12 * 4);
                            buf.order(ByteOrder.nativeOrder());
                            this.vertexCoords = buf.asFloatBuffer();
                            this.vertexCoordsPtr = Unsafe.getBufferPointer(this.vertexCoords);

                            this.unborrow();

                            this.state = State.RESOLVED;
                        } else {
                            this.state = this.pendingTexture.first.isCancelled() ?
                                    State.UNRESOLVED : State.UNRESOLVABLE;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to load tile [" + tileZ + "," + tileX + "," + tileY + "] from " + core.matrix.getName(), t);
                        this.state = State.UNRESOLVABLE;
                        this.tileVersion = this.pendingTexture.second;
                    } finally {
                        if(bitmap != null)
                            bitmap.recycle();
                        this.pendingTexture = null;
                    }
                }
            }
            break;
        } while(true);
        
        // XXX - allow for texture borrowing
        if((this.state == State.UNRESOLVABLE || this.state == State.RESOLVED) && this.tileVersion != core.tileDrawVersion) {
            // kick off tile load
            final Callable<Bitmap> bitmapLoader = new Callable<Bitmap>() {
                @Override
                public Bitmap call() throws Exception {
                    return core.matrix.getTile(tileZ, tileX, tileY, null);
                }
            };
            pendingTexture = Pair.<FutureTask<Bitmap>, Integer>create(new FutureTask<Bitmap>(bitmapLoader), Integer.valueOf(core.tileDrawVersion));
            core.bitmapLoader.loadBitmap(pendingTexture.first, GLBitmapLoader.QueueType.REMOTE);

            this.state = State.RESOLVING;
        }
        
        if(this.state != State.RESOLVED) {
            return (this.texture != null);
        }

        // XXX -  as necessary
        // XXX - mesh

        // update vertex coords
        view.scratch.pointD.x = projMinX; // lower-left
        view.scratch.pointD.y = projMinY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.scene.forward(view.scratch.geo, view.scratch.pointD);
        Unsafe.setFloats(vertexCoordsPtr,
                            (float)view.scratch.pointD.x,
                            (float)view.scratch.pointD.y,
                            (float)view.scratch.pointD.z);
        view.scratch.pointD.x = projMinX; // upper-left
        view.scratch.pointD.y = projMaxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.scene.forward(view.scratch.geo, view.scratch.pointD);
        Unsafe.setFloats(vertexCoordsPtr + 12,
                            (float)view.scratch.pointD.x,
                            (float)view.scratch.pointD.y,
                            (float)view.scratch.pointD.z);
        view.scratch.pointD.x = projMaxX; // lower-right
        view.scratch.pointD.y = projMinY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.scene.forward(view.scratch.geo, view.scratch.pointD);
        Unsafe.setFloats(vertexCoordsPtr + 24,
                            (float)view.scratch.pointD.x,
                            (float)view.scratch.pointD.y,
                            (float)view.scratch.pointD.z);
        view.scratch.pointD.x = projMaxX; // upper-right
        view.scratch.pointD.y = projMaxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.scene.forward(view.scratch.geo, view.scratch.pointD);
        Unsafe.setFloats(vertexCoordsPtr + 36,
                            (float)view.scratch.pointD.x,
                            (float)view.scratch.pointD.y,
                            (float)view.scratch.pointD.z);        

        return true;
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!this.renderCommon(view, renderPass)) {
            // XXX - borrowed texture support
            
            return;
        }
        
        // XXX - mesh
        
        batch.batch(this.texture.getTexId(),
                    GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                    3,
                    0, this.vertexCoords,
                    0, this.textureCoords,
                    core.r, core.g, core.b, core.a);
    }

    private void debugDraw(GLMapView view) {
        java.nio.ByteBuffer  dbg = com.atakmap.lang.Unsafe.allocateDirect(32);
        dbg.order(java.nio.ByteOrder.nativeOrder());
        view.scratch.pointD.x = projMinX;
        view.scratch.pointD.y = projMinY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(0, view.scratch.pointF.x);
        dbg.putFloat(4, view.scratch.pointF.y);
        view.scratch.pointD.x = projMinX;
        view.scratch.pointD.y = projMaxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(8, view.scratch.pointF.x);
        dbg.putFloat(12, view.scratch.pointF.y);
        view.scratch.pointD.x = projMaxX;
        view.scratch.pointD.y = projMaxY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(16, view.scratch.pointF.x);
        dbg.putFloat(20, view.scratch.pointF.y);
        view.scratch.pointD.x = projMaxX;
        view.scratch.pointD.y = projMinY;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        dbg.putFloat(24, view.scratch.pointF.x);
        dbg.putFloat(28, view.scratch.pointF.y);        
        
        GLES20FixedPipeline.glColor4f(0f, 1f, 0f, 1f);
        
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glLineWidth(2f);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, dbg);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0, 4);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        
        view.scratch.pointD.x = (projMinX+projMaxX)/2d;
        view.scratch.pointD.y = (projMinY+projMaxY)/2d;
        view.scratch.pointD.z = 0d;
        core.proj.inverse(view.scratch.pointD, view.scratch.geo);
        view.forward(view.scratch.geo, view.scratch.pointF);
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(view.scratch.pointF.x+5, view.scratch.pointF.y+20, 0f);
        GLText.getInstance(view.getRenderContext(), AtakMapView.getDefaultTextFormat()).draw("tile " + tileZ + "," + tileX + "," + tileY, 0f, 1f, 0f, 1f);
        GLES20FixedPipeline.glPopMatrix();
        
        Unsafe.free(dbg);
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if(!this.renderCommon(view, renderPass)) {
            if(MathUtils.hasBits(renderPass, getRenderPass())) {
                do {
                    boolean rebuildBorrowList = false;
                    for(BorrowRecord record : this.borrowRecords) {
                        if(record.from.texture == null) {
                            if(record.from.state != State.UNRESOLVABLE) {
                                // check for a cached texture, and kick off a
                                // tile load if necessary
                                if(!record.from.checkForCachedTexture() &&
                                   (this.state == State.UNRESOLVABLE &&
                                    record.from.state == State.UNRESOLVED)) {

                                    record.from.renderCommon(view, renderPass);
                                }
                            } else {
                                // if the tile we are borrowing from is
                                // unresolvable, we need to rebuild the list
                                rebuildBorrowList = true;
                                break;
                            }
                            if(record.from.texture == null)
                                continue;
                        }
                        
                        record.mesh.drawMesh(view, record.from.texture.getTexId(), core.r, core.g, core.b, core.a);
                    }
                    if(rebuildBorrowList) {
                        // rebuild the list and loop through again
                        unborrow();
                        tryBorrow();
                    } else {
                        // list is current; all borrowed textures have been
                        // drawn
                        break;
                    }
                } while(true);

                if(core.debugDraw)
                    debugDraw(view);
            }
            return;
        }

        // draw the textured mesh
        this.mesh.drawMesh(view,
                           this.texture.getTexId(),
                           this.core.r,
                           this.core.g,
                           this.core.b,
                           this.core.a);
        if(core.debugDraw)
            debugDraw(view);
    }

    @Override
    public void release() {
        if(this.texture != null) {
            if(this.core.textureCache != null) {
                this.core.textureCache.put(this.textureKey, this.texture, 4, this.textureCoords, this.vertexCoords, 0, Integer.valueOf(this.tileVersion));
                this.texture = null;
                this.textureCoords = null;
                this.vertexCoords = null;
                this.vertexCoordsPtr = 0L;
            } else {
                this.texture.release();
            }
            
            this.texture = null;
        }
        
        if(this.pendingTexture != null) {
            this.pendingTexture.first.cancel(true);
            this.pendingTexture = null;
        }

        // XXX - do we want to make unresolvable sticky?
        //if(this.state != State.UNRESOLVABLE)
            this.state = State.UNRESOLVED;

        if(this.textureCoords != null) {
            Unsafe.free(this.textureCoords);
            this.textureCoords = null;
        }
        if(this.vertexCoords != null) {
            Unsafe.free(this.vertexCoords);
            this.vertexCoords = null;
            this.vertexCoordsPtr = 0L;
        }
        
        this.mesh.release();
        
        this.unborrow();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }

    @Override
    public State getState() {
        return this.state;
    }

    @Override
    public void suspend() {
        if(this.state == State.UNRESOLVED)
            this.state = State.SUSPENDED;
    }

    @Override
    public void resume() {
        if(this.state == State.SUSPENDED)
            this.state = State.UNRESOLVED;
    }
    
    static String getTileTextureKey(GLTiledLayerCore core, int zoom, int tileX, int tileY) {
        return core.clientSourceUri + "{" + zoom + "," + tileX + "," + tileY + "}";
    }
    
    private final class BorrowRecord implements Disposable {
        /** the texture being borrowed from */
        final GLTile from;
        /** the minimum x-coordinate of the borrowed region */
        final double projMinX;
        /** the minimum y-coordinate of the borrowed region */
        final double projMinY;
        /** the maximum x-coordinate of the borrowed region */
        final double projMaxX;
        /** the maximum y-coordinate of the borrowed region */
        final double projMaxY;
        /** the texture mesh */
        final GLTileMesh mesh;
        
        public BorrowRecord(GLTile from, double borrowMinX, double borrowMinY, double borrowMaxX, double borrowMaxY) {
            this.from = from;
            if(from.texture == null)
                from.checkForCachedTexture();
            
            projMinX = borrowMinX;
            projMinY = borrowMinY;
            projMaxX = borrowMaxX;
            projMaxY = borrowMaxY;
            
            PointD p = new PointD(0d, 0d);
            
            p.x = projMinX;
            p.y = projMaxY;
            from.proj2uv.transform(p, p);
            final float u0 = (float)p.x;
            final float v0 = (float)p.y;
            p.x = projMaxX;
            p.y = projMinY;
            from.proj2uv.transform(p, p);
            final float u1 = (float)p.x;
            final float v1 = (float)p.y;
                    
            Matrix img2uv = Matrix.mapQuads(projMinX, projMaxY,
                                            projMaxX, projMaxY,
                                            projMaxX, projMinY,
                                            projMinX, projMinY,
                                            u0, v0,
                                            u1, v0,
                                            u1, v1,
                                            u0, v1);
            
            this.mesh = new GLTileMesh(new PointD(projMinX, projMaxY),
                                       new PointD(projMaxX, projMaxY),
                                       new PointD(projMaxX, projMinY),
                                       new PointD(projMinX, projMinY),
                                       img2uv,
                                       GLTile.this.core.proj2geo,
                                       GLTile.this.patch.parent.tileMeshSubdivisions);
            
            from.borrowers.add(GLTile.this);
        }

        @Override
        public void dispose() {
            from.borrowers.remove(GLTile.this);
        }
    }
}
