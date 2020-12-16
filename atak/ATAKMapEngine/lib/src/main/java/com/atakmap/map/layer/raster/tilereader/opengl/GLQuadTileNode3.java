
package com.atakmap.map.layer.raster.tilereader.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


import com.atakmap.coremap.maps.coords.DistanceCalculations;


import android.graphics.Color;
import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.Interop;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.gdal.GdalGraphicUtils;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.map.projection.PointL;
import com.atakmap.math.Matrix;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.opengl.GLWireFrame;
import com.atakmap.opengl.Shader;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Disposable;
import com.atakmap.util.Releasable;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.ResourcePool;

public class GLQuadTileNode3 implements
                                    GLMapRenderable2,
                                    GLResolvableMapRenderable,
                                    TileReader.AsynchronousReadRequestListener,
                                    RasterDataAccess2,
                                    Disposable {

    private final static Interop<Matrix> Matrix_interop = Interop.findInterop(Matrix.class);

    final static int STATE_RESOLVED = 0x01;
    final static int STATE_RESOLVING = 0x02;
    final static int STATE_UNRESOLVED = 0x04;
    final static int STATE_UNRESOLVABLE = 0x08;
    final static int STATE_SUSPENDED = 0x10;

    final static int[] POI_ITERATION_BIAS =
        {
            1, 3, 0, 2, // 0
            1, 0, 2, 3, // 1
            0, 1, 2, 3, // 2
            0, 2, 1, 3, // 3
            2, 0, 3, 1, // 4
            2, 3, 0, 1, // 5
            3, 2, 1, 0, // 6
            3, 1, 2, 0, // 7
        };

    static boolean offscreenFboFailed = false;

    private static final String TAG = "GLQuadTileNode3";

    final static double POLE_LATITUDE_LIMIT_EPISLON = 0.00001d;

    final static Comparator<PointL> POINT_COMPARATOR = new Comparator<PointL>() {
        @Override
        public int compare(PointL p0, PointL p1) {
            long retval = p0.y - p1.y;
            if (retval == 0L)
                retval = p0.x - p1.x;
            if(retval > 0L)
                return 1;
            else if(retval < 0L)
                return -1;
            else
                return 0;
        }
    };

    static boolean mipmapEnabled = false;

    /*************************************************************************/

    final static boolean DEBUG = false;

    static {
        GdalTileReader.setPaletteRgbaFormat(GdalTileReader.Format.RGBA);
    }

    final static int TEXTURE_CACHE_HINT_RESOLVED = 0x00000001;

    final static int MAX_GRID_SIZE = 32;

    // 8MB reserved transfer buffer
    final static ResourcePool<ByteBuffer> transferBuffers = new ResourcePool<>(32);

    private NodeCore core;

    /**
     * The read request currently being serviced. Valid access to the request is only guaranteed on
     * the render thread.
     */
    private TileReader.ReadRequest currentRequest;

    private GLTexture texture;

    /*private*/ GLMapView view;

    private long tileRow;
    private long tileColumn;
    private int level;

    private State state;

    // source coordinate space (unscaled)
    private long tileSrcX;
    private long tileSrcY;
    private long tileSrcWidth;
    private long tileSrcHeight;
    private long tileSrcMidX;
    private long tileSrcMidY;

    private int tileWidth;
    private int tileHeight;

    /**
     * The texture coordinate for the texture in the order lower-left,
     * lower-right, upper-right, upper-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     */
    private int textureCoordinates;
    private boolean texCoordsShared;

    private int borrowTextureCoordinates;

    /**
     * The texture coordinate for the texture in the order upper-left,
     * upper-right, lower-right, lower-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     */
    private int vertexCoordinates2;

    private int glTexCoordIndices2;
    private boolean indicesShared;

    private boolean textureCoordsValid;
    private boolean receivedUpdate;

    private int glTexType;
    private int glTexFormat;

    private int glTexGridWidth;
    private int glTexGridHeight;
    private int glTexGridVertCount;
    private int glTexGridIdxCount;

    private int vertexCoordSrid;
    private boolean vertexCoordsValid;
    private GLQuadTileNode2.GridVertex[] gridVertices;
    private GeoPoint centroid;
    private GeoPoint centroidHemi2;
    private int primaryHemi;
    private PointD centroidProj;
    private PointD centroidProjHemi2;

    private double minLat;
    private double minLng;
    private double maxLat;
    private double maxLng;

    private boolean touched;

    private long tileVersion;

    /**
     * This node's children, in the order: upper-left, upper-right, lower-left, lower-right.
     */
    private GLQuadTileNode3[] children;

    private int halfTileWidth;
    private int halfTileHeight;

    private GLQuadTileNode3 parent;
    private final GLQuadTileNode3 root;

    private GLQuadTileNode3 borrowingFrom;

    /* OWNED BY ROOT */

    private boolean verticesInvalid;

    private boolean derivedUnresolvableData;

    private long lastTouch;

    private long fadeTimer;

    private long readRequestStart;
    private long readRequestComplete;
    private long readRequestElapsed;

    private Buffer debugDrawIndices;

    private boolean descendantsRequestDraw;

    /**************************************************************************/

    /**
     * Creates a new root node.
     *
     * @param info          The image information
     * @param readerOpts    The {@link TileReaderFactory.Options} to be used
     *                      when creating the {@link TileReader} associated with
     *                      this node. May be <code>null</code>
     * @param opts          The render configuration {@link GLQuadTileNode2.Options}. May be
     *                      <code>null</code>.
     * @param init          The initializer for this node.
     */
    public GLQuadTileNode3(RenderContext ctx, ImageInfo info, TileReaderFactory.Options readerOpts, Options opts, GLQuadTileNode2.Initializer init) {
        this(null, -1, NodeCore.create(ctx, info, readerOpts, opts, true, init));
    }

    /**
     * Creates a new child node.
     *
     * @param parent The parent node
     * @param idx The index; <code>0</code> for upper-left, <code>1</code> for upper-right,
     *            <code>2</code> for lower-left and <code>3</code> for lower-right.
     */
    private GLQuadTileNode3(GLQuadTileNode3 parent, int idx) {
        this(parent, idx, parent.core);
    }

    private GLQuadTileNode3(GLQuadTileNode3 parent, int idx, NodeCore core) {
        this.parent = parent;
        this.core = core;
        this.root = (parent == null) ? this : parent.root;

        this.textureCoordsValid = false;
        this.currentRequest = null;

        this.state = State.UNRESOLVED;
        this.receivedUpdate = false;

        this.tileColumn = -1;
        this.tileRow = -1;
        this.level = -1;

        // XXX - would really like to make this statically initializable
        if(parent == null && core.vertexResolver == null) {
            if(this.core.precise != null)
                core.vertexResolver = new PreciseVertexResolver(this.root, null);
            else
                core.vertexResolver = new DefaultVertexResolver(this.root);

            // vertex resolver is to be released prior to I2G functions
            core.releasables.addFirst(core.vertexResolver);
        }

        this.vertexCoordSrid = -1;
        this.vertexCoordsValid = false;

        this.touched = false;

        this.tileVersion = -1L;

        // super

        this.children = new GLQuadTileNode3[4];
        this.halfTileWidth = (this.core.tileReader.getTileWidth() / 2);
        this.halfTileHeight = (this.core.tileReader.getTileHeight() / 2);

        if (this.parent != null) {
            this.set((this.parent.tileColumn * 2) + (idx % 2), (this.parent.tileRow * 2)
                    + (idx / 2), this.parent.level - 1);
        } else {
            // XXX - BUG: should be specifiying maxLevels-1 but then no data is
            // rendered????

            this.set(0, 0, this.core.tileReader.getMaxNumResolutionLevels());
        }

        this.derivedUnresolvableData = false;

        this.lastTouch = -1L;

        this.readRequestStart = 0L;
        this.readRequestComplete = 0L;
        this.readRequestElapsed = 0L;
    }

    public void setColor(int color) {
        this.core.color = color;
        this.core.colorR = ((color>>16)&0xFF)/255f;
        this.core.colorG = ((color>>8)&0xFF)/255f;
        this.core.colorB = (color&0xFF)/255f;
        this.core.colorA = ((color>>24)&0xFF)/255f;
    }

    public DatasetProjection2 getDatasetProjection() {
        return this.core.imprecise;
    }

    public TileReader getReader() {
        return this.core.tileReader;
    }

    /**
     * <P>
     * <B>IMPORTANT:</B> Must be invoked on the render thread.
     *
     * @param tileColumn
     * @param tileRow
     * @param level
     */
    private void set(long tileColumn, long tileRow, int level) {
        if (this.tileColumn == tileColumn && this.tileRow == tileRow && this.level == level)
            return;

        if (DEBUG)
            Log.d(TAG, toString(false) + " set(tileColumn=" + tileColumn + ",tileRow=" + tileRow
                    + ",level=" + level + ")");

        if (this.tileColumn != tileColumn || this.tileRow != tileRow || this.level != level) {
            for (int i = 0; i < 4; i++)
                if (this.children[i] != null)
                    this.children[i].set((tileColumn * 2) + (i % 2), (tileRow * 2) + (i / 2),
                            level - 1);
        }

        if (this.currentRequest != null) {
            this.currentRequest.cancel();
            this.currentRequest = null;
        }

        this.releaseTexture();

        this.state = State.UNRESOLVED;
        this.receivedUpdate = false;

        this.tileSrcX = this.core.tileReader.getTileSourceX(level, tileColumn);
        this.tileSrcY = this.core.tileReader.getTileSourceY(level, tileRow);
        this.tileSrcWidth = this.core.tileReader.getTileSourceWidth(level, tileColumn);
        this.tileSrcHeight = this.core.tileReader.getTileSourceHeight(level, tileRow);

        this.tileWidth = this.core.tileReader.getTileWidth(level, tileColumn);
        this.tileHeight = this.core.tileReader.getTileHeight(level, tileRow);

        this.textureCoordsValid = false;
        this.vertexCoordsValid = false;

        this.tileColumn = tileColumn;
        this.tileRow = tileRow;
        this.level = level;
        this.tileVersion = -1L;

        this.glTexFormat = GdalGraphicUtils.getBufferFormat(this.core.tileReader.getFormat());
        this.glTexType = GdalGraphicUtils.getBufferType(this.core.tileReader.getFormat());

        if (this.glTexFormat != GLES30.GL_RGBA
                && !(MathUtils.isPowerOf2(this.tileWidth) && MathUtils.isPowerOf2(this.tileHeight))) {
            this.glTexFormat = GLES30.GL_RGBA;
            this.glTexType = GLES30.GL_UNSIGNED_BYTE;
        } else if (this.level >= this.core.tileReader.getMinCacheLevel()) {
            // if we are pulling from the cache, we want alpha since the tile
            // will be delivered as parts rather than as a whole
            if (this.glTexFormat == GLES30.GL_LUMINANCE) {
                this.glTexFormat = GLES30.GL_LUMINANCE_ALPHA;
                this.glTexType = GLES30.GL_UNSIGNED_BYTE;
            } else if (this.glTexFormat == GLES30.GL_RGB) {
                this.glTexFormat = GLES30.GL_RGBA;
                // if(this.glTexType == GLES30.GL_UNSIGNED_SHORT_5_6_5)
                // this.glTexType = GLES30.GL_UNSIGNED_SHORT_5_5_5_1;
                this.glTexType = GLES30.GL_UNSIGNED_BYTE;
            }
        }

        PointD scratchP = new PointD(0, 0);
        GeoPoint scratchG = GeoPoint.createMutable();

        minLat = 90;
        maxLat = -90;
        minLng = 180;
        maxLng = -180;

        scratchP.x = this.tileSrcX;
        scratchP.y = this.tileSrcY;
        this.core.imprecise.imageToGround(scratchP, scratchG);
        if(scratchG.getLatitude() < minLat) minLat = scratchG.getLatitude();
        if(scratchG.getLatitude() > maxLat) maxLat = scratchG.getLatitude();
        if(scratchG.getLongitude() < minLng) minLng = scratchG.getLongitude();
        if(scratchG.getLongitude() > maxLng) maxLng = scratchG.getLongitude();

        scratchP.x = this.tileSrcX+this.tileSrcWidth;
        scratchP.y = this.tileSrcY;
        this.core.imprecise.imageToGround(scratchP, scratchG);
        if(scratchG.getLatitude() < minLat) minLat = scratchG.getLatitude();
        if(scratchG.getLatitude() > maxLat) maxLat = scratchG.getLatitude();
        if(scratchG.getLongitude() < minLng) minLng = scratchG.getLongitude();
        if(scratchG.getLongitude() > maxLng) maxLng = scratchG.getLongitude();

        scratchP.x = this.tileSrcX+this.tileSrcWidth;
        scratchP.y = this.tileSrcY+this.tileSrcHeight;
        this.core.imprecise.imageToGround(scratchP, scratchG);
        if(scratchG.getLatitude() < minLat) minLat = scratchG.getLatitude();
        if(scratchG.getLatitude() > maxLat) maxLat = scratchG.getLatitude();
        if(scratchG.getLongitude() < minLng) minLng = scratchG.getLongitude();
        if(scratchG.getLongitude() > maxLng) maxLng = scratchG.getLongitude();

        scratchP.x = this.tileSrcX;
        scratchP.y = this.tileSrcY+this.tileSrcHeight;
        this.core.imprecise.imageToGround(scratchP, scratchG);
        if(scratchG.getLatitude() < minLat) minLat = scratchG.getLatitude();
        if(scratchG.getLatitude() > maxLat) maxLat = scratchG.getLatitude();
        if(scratchG.getLongitude() < minLng) minLng = scratchG.getLongitude();
        if(scratchG.getLongitude() > maxLng) maxLng = scratchG.getLongitude();

        final int minGridSize = ConfigOptions.getOption("glquadtilenode2.minimum-grid-size", 1);
        final int maxGridSize = ConfigOptions.getOption("glquadtilenode2.maximum-grid-size", 32);

        // XXX - needs to be based off of the full image to prevent seams
        //       between adjacent tiles which may have different local spatial
        //       resolutions
        final int subsX = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((maxLat-minLat) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);
        final int subsY = MathUtils.clamp(MathUtils.nextPowerOf2((int)Math.ceil((maxLng-minLng) / GLMapView.recommendedGridSampleDistance)), minGridSize, maxGridSize);

        // XXX - rendering issues if grid is not square...
        this.glTexGridWidth = Math.max(subsX, subsY);
        this.glTexGridHeight = Math.max(subsX, subsY);

        this.gridVertices = new GLQuadTileNode2.GridVertex[(this.glTexGridWidth+1)*(this.glTexGridHeight+1)];

        this.centroid = GeoPoint.createMutable();

        final int halfTileWidth = this.core.tileReader.getTileWidth() / 2;
        final int halfTileHeight = this.core.tileReader.getTileHeight() / 2;

        if(this.core.tileReader.getTileWidth(this.level, this.tileColumn) <= halfTileWidth)
            this.tileSrcMidX = this.tileSrcX+(this.tileSrcWidth/2);
        else
            this.tileSrcMidX = (long)(tileSrcX+(core.tileReader.getTileWidth()*Math.pow(2, (this.level-1))));
        if(this.core.tileReader.getTileHeight(this.level, this.tileRow) <= halfTileHeight)
            this.tileSrcMidY = this.tileSrcY+(this.tileSrcHeight/2);
        else
            this.tileSrcMidY = (long)(tileSrcY+(core.tileReader.getTileHeight()*Math.pow(2, (this.level-1))));
        this.centroidProj = new PointD(tileSrcMidX, tileSrcMidY, 0d);
        if(!this.core.imprecise.imageToGround(this.centroidProj, this.centroid))
            this.centroid.set((minLat+maxLat)/2d, (minLng+maxLng)/2d);
        this.centroidProj.x = 0d;
        this.centroidProj.y = 0d;

        this.centroidHemi2 = GeoPoint.createMutable();
        this.centroidProjHemi2 = new PointD(0d, 0d, 0d);
    }

    private void releaseTexture() {
        if(this.texture != null) {
            if(!this.core.tileReader.isMultiResolution() && this.core.textureCache != null) {
                final String key = core.uri+"&x=" + tileColumn + "&y=" + tileRow + "&z=" + level;
                core.textureCache.put(key, this.texture, (this.state == State.RESOLVED) ? TEXTURE_CACHE_HINT_RESOLVED : 0, Long.valueOf(tileVersion));
            } else {
                this.texture.release();
                this.texture = null;
            }
            this.touched = false;
        }

        if(!texCoordsShared)
            core.resources.discardBuffer(textureCoordinates);
        this.textureCoordinates = GLES30.GL_NONE;
        this.texCoordsShared = false;
        vertexCoordinates2 = core.resources.discardBuffer(vertexCoordinates2);
        if(!this.indicesShared)
            core.resources.discardBuffer(glTexCoordIndices2);
        this.glTexCoordIndices2 = GLES30.GL_NONE;
        this.indicesShared = false;

        this.textureCoordsValid = false;
        this.vertexCoordsValid = false;

        if(this.state != State.UNRESOLVABLE)
            this.state = State.UNRESOLVED;

        this.receivedUpdate = false;
        this.derivedUnresolvableData = false;
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            this.draw(view);
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }


    @Override
    public void release() {
        if (this.children != null)
            this.abandon();
        if(this == this.root) {
            for(Releasable r : this.core.releasables)
                r.release();
            core.suspended = false;
        } else {
            this.parent = null;
        }

        this.touched = false;

        this.fadeTimer = 0L;

        if (this.currentRequest != null) {
            this.currentRequest.cancel();
            this.currentRequest = null;
        }
        if (this.texture != null)
            this.releaseTexture();

        vertexCoordinates2 = core.resources.discardBuffer(this.vertexCoordinates2);
        if(!this.indicesShared)
            core.resources.discardBuffer(glTexCoordIndices2);
        this.glTexCoordIndices2 = GLES30.GL_NONE;
        this.indicesShared = false;
        if(!this.texCoordsShared)
            core.resources.discardBuffer(textureCoordinates);
        this.textureCoordinates = GLES30.GL_NONE;
        this.texCoordsShared = false;
        borrowTextureCoordinates = core.resources.discardBuffer(borrowTextureCoordinates);
        this.borrowingFrom = null;

        this.tileColumn = -1;
        this.tileRow = -1;
        this.level = -1;

        this.textureCoordsValid = false;
        this.vertexCoordsValid = false;

        this.state = State.UNRESOLVED;
        this.receivedUpdate = false;

        if(this.debugDrawIndices != null) {
            Unsafe.free(this.debugDrawIndices);
            this.debugDrawIndices = null;
        }
    }

    private void validateTexture() {
        if (this.texture == null ||
                this.texture.getTexWidth() < this.tileWidth
                || this.texture.getTexHeight() < this.tileHeight ||
                this.texture.getFormat() != this.glTexFormat
                || this.texture.getType() != this.glTexType) {

            if (this.texture != null)
                this.texture.release();

            this.texture = new GLTexture(this.tileWidth, this.tileHeight, this.glTexFormat,
                    this.glTexType);

            // mark all coords as invalid
            this.textureCoordsValid = false;
            this.vertexCoordsValid = false;

            if(!texCoordsShared)
                core.resources.discardBuffer(this.textureCoordinates);
            this.textureCoordinates = GLES30.GL_NONE;
            this.texCoordsShared = false;
            this.vertexCoordinates2 = core.resources.discardBuffer(this.vertexCoordinates2);
        }

        this.validateTexVerts();
    }

    private void validateTexCoordIndices() {
        final int vertCount = GLTexture.getNumQuadMeshVertices(this.glTexGridWidth,
                this.glTexGridHeight);
        final int idxCount = GLTexture.getNumQuadMeshIndices(this.glTexGridWidth,
                this.glTexGridHeight);
        if (vertCount != this.glTexGridVertCount ||
            idxCount != glTexGridIdxCount ||
             (this.glTexGridVertCount <= 4 && this.glTexCoordIndices2 != GLES30.GL_NONE) ||
             (this.glTexGridVertCount > 4 && this.glTexCoordIndices2 == GLES30.GL_NONE)) {

            this.glTexGridIdxCount = idxCount;
            this.glTexGridVertCount = vertCount;
            if (this.glTexGridVertCount > 4) {
                if(core.resources.isUniformGrid(this.glTexGridWidth, this.glTexGridHeight)) {
                    this.glTexCoordIndices2 = core.resources.getUniformGriddedIndices(this.glTexGridWidth);
                    indicesShared = true;
                } else {
                    if (this.indicesShared || this.glTexCoordIndices2 == GLES30.GL_NONE)
                        this.glTexCoordIndices2 = core.resources.genBuffer();
                    core.resources.coordStreamBufferS.clear();
                    GLTexture.createQuadMeshIndexBuffer(this.glTexGridWidth,
                            this.glTexGridHeight,
                            core.resources.coordStreamBufferS);
                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, this.glTexCoordIndices2);
                    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, this.glTexGridIdxCount * 2, core.resources.coordStreamBuffer, GLES30.GL_STATIC_DRAW);
                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GLES30.GL_NONE);
                    indicesShared = false;
                }
                //this.debugDrawIndices = GLWireFrame.deriveIndices(glTexCoordIndices.asShortBuffer(), GLES30.GL_TRIANGLE_STRIP, glTexGridIdxCount, GLES30.GL_UNSIGNED_SHORT);
            } else {
                if(!indicesShared)
                    core.resources.discardBuffer(this.glTexCoordIndices2);
                this.glTexCoordIndices2 = GLES30.GL_NONE;
                indicesShared = false;
                this.debugDrawIndices = GLWireFrame.deriveIndices(GLES30.GL_TRIANGLE_STRIP,
                        this.glTexGridVertCount, GLES30.GL_UNSIGNED_SHORT);
            }
            this.vertexCoordsValid = false;
        }
    }

    private void validateTexVerts() {
        if (!this.textureCoordsValid) {
            validateTexCoordIndices();

            final float x = ((float) this.tileWidth / (float) this.texture.getTexWidth());
            final float y = ((float) this.tileHeight / (float) this.texture.getTexHeight());

            // if the texture mesh is uniform, the cell count is a power of two
            // and the texture data fills the full texture, utilize one of the
            // shared texture coordinate meshes, otherwise allocate a per-node
            // texture coordinate buffer
            if(core.resources.isUniformGrid(glTexGridWidth, glTexGridHeight) &&
               (x == 1f && y == 1f)) {

                this.textureCoordinates = core.resources.getUniformGriddedTexCoords(glTexGridWidth);
                texCoordsShared = true;
            } else {
                if (texCoordsShared ||
                    this.textureCoordinates == GLES30.GL_NONE) {

                    this.textureCoordinates = core.resources.genBuffer();
                }

                core.resources.coordStreamBufferF.clear();
                GLTexture.createQuadMeshTexCoords(new PointF(0.0f, 0.0f),
                                                  new PointF(x, 0.0f),
                                                  new PointF(x, y),
                                                  new PointF(0.0f, y),
                                                  this.glTexGridWidth,
                                                  this.glTexGridHeight,
                                                  core.resources.coordStreamBufferF);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, this.textureCoordinates);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, this.glTexGridVertCount*2*4, core.resources.coordStreamBuffer, GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
                texCoordsShared = false;
            }
        }
        this.textureCoordsValid = true;
    }

    private void validateVertexCoords(GLMapView view) {
        if(!this.vertexCoordsValid || (this.vertexCoordSrid != view.drawSrid))
        {
            if(this.vertexCoordSrid != view.drawSrid)
                view.scene.mapProjection.forward(this.centroid, this.centroidProj);

            if(this.vertexCoordinates2 == GLES30.GL_NONE)
                this.vertexCoordinates2 = core.resources.genBuffer();

            core.resources.coordStreamBuffer.clear();
            final long vertexCoordinatesPtr = Unsafe.getBufferPointer(core.resources.coordStreamBuffer);

            // recompute vertex coordinates as necessary
            this.core.vertexResolver.beginNode(this);
            try {
                int idx = 0;
                for (int i = 0; i <= this.glTexGridHeight; i++) {
                    for (int j = 0; j <= this.glTexGridWidth; j++) {
                        final int gridVertsIdx = (i*(this.glTexGridWidth+1))+j;
                        if(this.gridVertices[gridVertsIdx] == null)
                            this.gridVertices[gridVertsIdx] = new GLQuadTileNode2.GridVertex();
                        if(!this.gridVertices[gridVertsIdx].resolved) {
                            this.core.vertexResolver.project(view,
                                    this.tileSrcX + ((this.tileSrcWidth * j) / this.glTexGridWidth),
                                    this.tileSrcY + ((this.tileSrcHeight * i) / this.glTexGridHeight),
                                    this.gridVertices[gridVertsIdx]);
                            // force LLA reprojection
                            this.gridVertices[gridVertsIdx].projectedSrid = -1;
                        }
                        GeoPoint v = this.gridVertices[gridVertsIdx].value;

                        // reproject LLA to the current map projection
                        if(this.gridVertices[gridVertsIdx].projectedSrid != view.drawSrid) {
                            view.scene.mapProjection.forward(v, this.gridVertices[gridVertsIdx].projected);
                            this.gridVertices[gridVertsIdx].projectedSrid = view.drawSrid;
                        }

                        view.scratch.pointD.x = this.gridVertices[gridVertsIdx].projected.x-centroidProj.x;
                        view.scratch.pointD.y = this.gridVertices[gridVertsIdx].projected.y-centroidProj.y;
                        view.scratch.pointD.z = this.gridVertices[gridVertsIdx].projected.z-centroidProj.z;

                        Unsafe.setFloats(vertexCoordinatesPtr+idx,
                                         (float)view.scratch.pointD.x,
                                         (float)view.scratch.pointD.y,
                                         (float)view.scratch.pointD.z);
                        idx += 12;
                    }
                }
            } finally {
                this.core.vertexResolver.endNode(this);
            }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, this.vertexCoordinates2);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, this.glTexGridVertCount*3*4, core.resources.coordStreamBuffer, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);

            this.vertexCoordsValid = true;

            this.vertexCoordSrid = view.drawSrid;

            // force secondary hemi centroid reprojection
            this.primaryHemi = -1;
        }

        // if the view crosses the IDL and the centroid does not correspond to
        // the primary hemisphere, reconstruct the centroid for the secondary
        // hemisphere
        int primaryHemi = view.idlHelper.getPrimaryHemisphere();
        if((view.crossesIDL || this.core.unwrap != 0d) && this.primaryHemi != primaryHemi) {
            int secondaryHemisphere;
            if(primaryHemi == GLAntiMeridianHelper.HEMISPHERE_EAST)
                secondaryHemisphere = GLAntiMeridianHelper.HEMISPHERE_WEST;
            else if(primaryHemi == GLAntiMeridianHelper.HEMISPHERE_WEST)
                secondaryHemisphere = GLAntiMeridianHelper.HEMISPHERE_EAST;
            else
                throw new IllegalStateException();

            // obtain the centroid in the secondary hemisphere
            if (this.core.unwrap > 0d && centroid.getLongitude() < 0d
                    || this.core.unwrap < 0d && centroid.getLongitude() > 0d) {
                centroidHemi2.set(centroid.getLatitude(),
                        centroid.getLongitude() + this.core.unwrap,
                        centroid.getAltitude(),
                        centroid.getAltitudeReference(),
                        centroid.getCE(),
                        centroid.getLE());
            } else if (this.core.unwrap == 0d) {
                view.idlHelper.wrapLongitude(secondaryHemisphere, centroid, centroidHemi2);
            } else {
                centroidHemi2.set(centroid);
            }
            // project the secondary hemisphere centroid
            view.scene.mapProjection.forward(centroidHemi2, centroidProjHemi2);
            this.primaryHemi = primaryHemi;
        }
    }

    @Override
    public void draw(GLMapView view) {
        if (this.parent != null)
            throw new IllegalStateException(
                    "External draw method should only be invoked on root node!");

        if(this.core.shader == null)
            this.core.shader = Shader.get(view.getRenderContext());

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glUseProgram(this.core.shader.handle);
        GLES30.glVertexAttrib3f(this.core.shader.aNormals, 0f, 0f, 0f);
        GLES30.glVertexAttrib4f(this.core.shader.aColors, 1f, 1f, 1f, 1f);

        GLES30.glUniform1ui(this.core.shader.uTexture, 0);
        GLES30.glUniform4f(this.core.shader.uColor, this.core.colorR, this.core.colorG, this.core.colorB, this.core.colorA);
        GLES30.glEnableVertexAttribArray(this.core.shader.aTexCoords);
        GLES30.glEnableVertexAttribArray(this.core.shader.aVertexCoords);

        GLES30.glUniform4f(core.shader.uColor, core.colorR, core.colorG, core.colorB, core.colorA);
        core.renderState.r = core.colorR;
        core.renderState.g = core.colorG;
        core.renderState.b = core.colorB;
        core.renderState.a = core.colorA;

        core.mvp.setToIdentity();
        // XXX -
        com.atakmap.opengl.GLES20FixedPipeline.glGetFloatv(com.atakmap.opengl.GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        for(int i = 0; i < 16; i++)
            core.mvp.set(i%4, i/4, view.scratch.matrixF[i]);
        core.mvp.concatenate(view.scene.forward);

        core.tilesThisFrame = 0;
        int numRois = GLTiledMapLayer2.getRasterROI2(
                this.core.drawROI,
                view,
                this.core.tileReader.getWidth(),
                this.core.tileReader.getHeight(),
                this.core.imprecise,
                this.core.upperLeft,
                this.core.upperRight,
                this.core.lowerRight,
                this.core.lowerLeft,
                this.core.unwrap,
                this.core.options.adaptiveTileLod ?
                        0d :
                        360d / (1L<< OSMUtils.mapnikTileLevel(view.drawMapResolution)));

        if (numRois < 1) // no intersection
            return;

        final int lastStateMask = this.core.stateMask;
        this.core.stateMask = 0;
        final double scale = (this.core.gsd / view.drawMapResolution);

        // XXX - tune level calculation -- it may look better to swap to the
        // next level before we actually cross the threshold
        final int level = (int) Math.ceil(Math.max((Math.log(1.0d / scale) / Math.log(2.0)) + this.core.options.levelTransitionAdjustment, 0d));
        this.core.drawPumpLevel = level;

        if (view.targeting && this.hasPreciseCoordinates())
            this.core.magFilter = GLES30.GL_NEAREST;
        else
            this.core.magFilter = GLES30.GL_LINEAR;

        try {
            if(this.core.tileReader != null)
                this.core.tileReader.start();
            this.core.vertexResolver.beginDraw(view);
            try {
                this.drawImpl(
                        view,
                        Math.min(level, root.level),
                        numRois);
            } finally {
                this.core.vertexResolver.endDraw(view);
            }
        } finally {
            if(this.core.tileReader != null)
                this.core.tileReader.stop();
        }

        GLES30.glDisableVertexAttribArray(this.core.shader.aTexCoords);
        GLES30.glDisableVertexAttribArray(this.core.shader.aVertexCoords);

        GLES30.glUseProgram(GLES30.GL_NONE);
        GLES30.glDisable(GLES30.GL_BLEND);
        //Log.v(TAG, "Tiles this frame: " + core.tilesThisFrame);

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GLES30.GL_NONE);

        core.renderState.texCoords = GLES30.GL_NONE;
        core.renderState.vertexCoords = GLES30.GL_NONE;
        core.renderState.indices = GLES30.GL_NONE;
        core.renderState.texId = 0;

        core.resources.deleteBuffers();

        if (this.core.frameBufferHandle[0] != 0) {
            GLES30.glDeleteFramebuffers(1, this.core.frameBufferHandle, 0);
            this.core.frameBufferHandle[0] = 0;
        }
        if (this.core.depthBufferHandle[0] != 0) {
            GLES30.glDeleteRenderbuffers(1, this.core.depthBufferHandle, 0);
            this.core.depthBufferHandle[0] = 0;
        }

        if(this.core.stateMask != lastStateMask)
            view.requestRefresh();
    }

    private boolean shouldResolve() {
        boolean resolveUnresolvable = false;
        for (int i = 0; i < this.children.length; i++)
            resolveUnresolvable |= (this.children[i] != null) && (this.children[i].receivedUpdate);

        return (this.state == State.UNRESOLVABLE && resolveUnresolvable) ||
                ((this.state == State.UNRESOLVED) && (this.currentRequest == null));
    }

    private void resolveTexture(boolean fetch) {
        if(!this.core.tileReader.isMultiResolution() && this.core.textureCache != null) {
            final String key = core.uri+"&x=" + tileColumn + "&y=" + tileRow + "&z=" + level;
            GLTextureCache.Entry entry = core.textureCache.remove(key);
            if(entry != null && entry.texture != null) {
                if(this.texture != null)
                    this.texture.release();
                this.texture = entry.texture;
                this.state = MathUtils.hasBits(entry.hints, TEXTURE_CACHE_HINT_RESOLVED) ? State.RESOLVED : State.UNRESOLVED;
                if(entry.opaque != null)
                    this.tileVersion = ((Number)entry.opaque).longValue();
                this.receivedUpdate = true;
                if(this.state == State.RESOLVED)
                    return;
            }
        }

        // copy data from the children to our texture
        if(this.state != State.RESOLVED &&
           this.core.options.textureCopyEnabled &&
           GLMapSurface.SETTING_enableTextureTargetFBO && this.core.textureCopyEnabled &&
           !offscreenFboFailed) {

            final long ntxChild = core.tileReader.getNumTilesX(this.level+1);
            final long ntyChild = core.tileReader.getNumTilesY(this.level+1);

            boolean hasChildData = false;
            boolean willBeResolved = true;
            int numChildren = 0;
            for (int i = 0; i < 4; i++) {
                final long ctx = this.tileColumn+(i%2);
                final long cty = this.tileRow+(i%2);
                if(ctx > ntxChild)
                    continue;
                if(cty > ntyChild)
                    continue;
                numChildren++;
                willBeResolved &= (this.children[i] != null && (this.children[i].state == State.RESOLVED));
                if (this.children[i] != null
                        && ((this.children[i].state == State.RESOLVED) || this.children[i].receivedUpdate)) {
                    hasChildData = true;
                }
            }

            if (hasChildData) {
                // XXX - luminance is not renderable for FBO
                this.glTexFormat = GLES30.GL_RGBA;
                this.glTexType = GLES30.GL_UNSIGNED_BYTE;

                this.validateTexture();
                this.texture.init();

                int parts = 0;
                int[] currentFrameBuffer = new int[1];
                int[] frameBuffer = this.core.frameBufferHandle;
                int[] depthBuffer = this.core.depthBufferHandle;

                GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, currentFrameBuffer, 0);

                boolean fboCreated = false;
                do {
                    if (frameBuffer[0] == 0)
                        GLES30.glGenFramebuffers(1, frameBuffer, 0);

                    if (depthBuffer[0] == 0)
                        GLES30.glGenRenderbuffers(1, depthBuffer, 0);

                    GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,
                            depthBuffer[0]);
                    GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
                            GLES30.GL_DEPTH_COMPONENT16, this.texture.getTexWidth(),
                            this.texture.getTexHeight());
                    GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);

                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,
                            frameBuffer[0]);

                    // clear any pending errors
                    while(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                        ;
                    GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                            GLES30.GL_COLOR_ATTACHMENT0,
                            GLES30.GL_TEXTURE_2D, this.texture.getTexId(), 0);

                    // XXX - observing hard crash following bind of "complete"
                    //       FBO on SM-T230NU. reported error is 1280 (invalid
                    //       enum) on glFramebufferTexture2D. I have tried using
                    //       the color-renderable formats required by GLES 2.0
                    //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
                    //       the same outcome.
                    if(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                        break;

                    GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                            GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER,
                            depthBuffer[0]);
                    final int fboStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
                    fboCreated = (fboStatus == GLES30.GL_FRAMEBUFFER_COMPLETE);
                } while(false);

                if (fboCreated) {
                    // x,y,width,height
                    int[] viewport = new int[4];
                    GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0);

                    // reset the viewport to the tile dimensions
                    GLES30.glViewport(0, 0, this.tileWidth, this.tileHeight);

                    // construct an ortho projection to render the tile data
                    float[] mx = new float[16];
                    android.opengl.Matrix.orthoM(mx, 0, 0, this.tileWidth, 0, this.tileHeight, 1, -1);
                    GLES30.glUniformMatrix4fv(core.shader.uMVP, 1, false, mx, 0);

                    core.resources.coordStreamBuffer.clear();
                    FloatBuffer xyuv = core.resources.coordStreamBuffer.asFloatBuffer();

                    GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

                    int tx;
                    int ty;
                    int partX;
                    int partY;
                    int partWidth;
                    int partHeight;

                    float childTexWidth;
                    float childTexHeight;
                    for (int i = 0; i < 4; i++) {
                        if (this.children[i] != null
                                && ((this.children[i].state == State.RESOLVED) || this.children[i].receivedUpdate)) {
                            tx = i % 2;
                            ty = i / 2;
                            partX = tx * this.halfTileWidth;
                            partY = ty * this.halfTileHeight;
                            partWidth = (Math.min((tx + 1) * (this.halfTileWidth), this.tileWidth) - partX);
                            partHeight = (Math.min((ty + 1) * (this.halfTileHeight), this.tileHeight) - partY);
                            childTexWidth = this.children[i].texture.getTexWidth();
                            childTexHeight = this.children[i].texture.getTexHeight();

                            xyuv.clear();
                            // ll
                            xyuv.put(partX);
                            xyuv.put(partY);
                            xyuv.put(0f/childTexWidth);
                            xyuv.put(0f/childTexHeight);
                            // lr
                            xyuv.put(partX + partWidth);
                            xyuv.put(partY);
                            xyuv.put((float) this.children[i].tileWidth/childTexWidth);
                            xyuv.put(0f/childTexHeight);
                            // ur
                            xyuv.put(partX + partWidth);
                            xyuv.put(partY + partHeight);
                            xyuv.put((float) this.children[i].tileWidth/childTexWidth);
                            xyuv.put((float) this.children[i].tileHeight/childTexHeight);
                            // ul
                            xyuv.put(partX);
                            xyuv.put(partY + partHeight);
                            xyuv.put(0f/childTexWidth);
                            xyuv.put((float) this.children[i].tileHeight/childTexHeight);

                            xyuv.flip();

                            GLES30.glUniform4f(core.shader.uColor, 1f, 1f, 1f, 1f);
                            core.renderState.r = 1f;
                            core.renderState.g = 1f;
                            core.renderState.b = 1f;
                            core.renderState.a = 1f;
                            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GLES30.GL_NONE);
                            core.renderState.indices = GLES30.GL_NONE;
                            core.renderState.texCoords = GLES30.GL_NONE;
                            core.renderState.vertexCoords = GLES30.GL_NONE;
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
                            GLES30.glVertexAttribPointer(core.shader.aVertexCoords, 2, GLES30.GL_FLOAT, false, 16, xyuv.position(0));
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
                            GLES30.glVertexAttribPointer(core.shader.aTexCoords, 2, GLES30.GL_FLOAT, false, 16, xyuv.position(2));
                            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.children[i].texture.getTexId());
                            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4);

                            // if the child is resolved, increment parts
                            if (this.children[i].state == State.RESOLVED)
                                parts++;
                        }
                    }

                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameBuffer[0]);
                    // restore the viewport
                    GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

                    this.textureCoordsValid = false;
                    this.receivedUpdate = true;

                    this.validateTexVerts();

                    if(!willBeResolved) {
                        this.texture.setMagFilter(GLES30.GL_NEAREST);
                        this.texture.getTexId();
                    }
                } else {
                    Log.w(TAG, "Failed to create FBO for texture copy.");
                    offscreenFboFailed = true;
                }

                final boolean wasUnresolvable = (this.state == State.UNRESOLVABLE);

                // mark resolved if all 4 children were resolved
                if (parts == numChildren) {
                    if(this.core.options.childTextureCopyResolvesParent)
                        this.state = State.RESOLVED;
                    else if(this.state != State.UNRESOLVABLE)
                        this.state = State.UNRESOLVED;
                } else if (this.state != State.SUSPENDED) {
                    this.state = State.UNRESOLVED;
                }

                // if the tile was previously unresolvable, record whether or
                // not we were able to derive any data through compositing
                if(wasUnresolvable)
                    this.derivedUnresolvableData |= (parts > 0);

                // if the tile will not be resolved via compositing, switch to
                // nearest neighbor interpolation to try to prevent edges
                if(!willBeResolved)
                    this.texture.setMinFilter(GLES30.GL_NEAREST);
            }
        } else if(this.state == State.RESOLVED) {
            // a refresh has been requested, move back into the unresolved state
            // to reload the tile
            this.state = State.UNRESOLVED;
        }
        if (this.state == State.UNRESOLVED && fetch) {
            this.state = State.RESOLVING;
            this.readRequestStart = System.currentTimeMillis();
            this.core.tileReader.asyncRead(this.level, this.tileColumn, this.tileRow, this);
        }
    }

    private void resetFadeTimer() {
        float levelScale = 1f - (float)(this.level-this.core.drawPumpLevel) / (float)(this.root.level-this.core.drawPumpLevel);
        //if(this.borrowingFrom != null) {
        if(false) {
             this.fadeTimer = Math.max((long)(this.core.fadeTimerLimit * levelScale) - this.readRequestElapsed, 0L);
        } else {
            this.fadeTimer = 0L;
        }
    }

    private static int computeLevel(GLMapView view, double gsd, double ullat, double ullng, double lrlat, double lrlng, int maxLevel, double lvladj) {
        final double tileGsd = GLMapView.estimateResolution(view, ullat, ullng, lrlat, lrlng, null);
        final double scale = (gsd / tileGsd);
        final int computed = (int) Math.ceil(Math.max((Math.log(1.0d / scale) / Math.log(2.0)) + lvladj, 0d));
        return Math.min(maxLevel, computed);
    }


    private boolean shouldDraw(int numRois, long minX, long minY, long maxX, long maxY) {
        for(int i = 0; i < numRois; i++) {
            if(Rectangle.intersects(core.drawROI[i].left, core.drawROI[i].top, core.drawROI[i].right, core.drawROI[i].bottom, minX, minY, maxX, maxY)) {
                return true;
            }
        }
        return false;
    }
    /**
     * @param view The view
     * @param level The resolution level. The scale factor is equivalent to
     *            <code>1.0d / (double)(1<<level)</code>
     * @return <code>true</code> if the ancestor should draw
     */
    private boolean drawImpl(GLMapView view, int level, int numRois) {
        this.view = view;

        // dynamically refine level based on expected nominal resolution for tile as rendered
        final int computed;
        if(this.core.options.adaptiveTileLod) {
            computed = computeLevel(view, this.core.gsd, maxLat, minLng, minLat, maxLng, this.level, this.core.options.levelTransitionAdjustment);
        } else {
            final double tileGsd = GLMapView.estimateResolution(view, maxLat, minLng, minLat, maxLng, null);
            final double scale = (this.core.gsd / tileGsd);
            computed = (int) Math.ceil(Math.max((Math.log(1.0d / scale) / Math.log(2.0)) + this.core.options.levelTransitionAdjustment, 0d));
        }
        if (computed > level) {
            level = (computed > this.level) ? this.level : level;
        }

        if (this.level == level) {
            this.drawImpl(view, true);
            this.abandon();
            this.verticesInvalid = false;
            this.descendantsRequestDraw = false;
            switch (this.state) {
                case RESOLVED:
                    core.stateMask |= STATE_RESOLVED;
                    break;
                case RESOLVING:
                    core.stateMask |= STATE_RESOLVING;
                    break;
                case UNRESOLVED:
                    core.stateMask |= STATE_UNRESOLVED;
                    break;
                case UNRESOLVABLE:
                    core.stateMask |= STATE_UNRESOLVABLE;
                    break;
                case SUSPENDED:
                    core.stateMask |= STATE_SUSPENDED;
                    break;
            }
            return (this.state != State.RESOLVED);
        } else if(this.level < level) {
            throw new IllegalStateException();
        } else if (!this.core.options.adaptiveTileLod) {
            // determine children to draw
            boolean[] drawChild = new boolean[4]; // UL, UR, LL, LR
            final boolean skipr = (tileWidth <= halfTileWidth);
            final boolean skipb = (tileHeight <= halfTileHeight);
            drawChild[0] = shouldDraw(numRois, this.tileSrcX, this.tileSrcY, skipr ? (tileSrcX+tileSrcWidth-1) : this.tileSrcMidX, skipb ? (tileSrcY+tileSrcHeight-1) : tileSrcMidY);
            drawChild[1] = skipr ? false : shouldDraw(numRois, this.tileSrcMidX, this.tileSrcY, (tileSrcX+tileSrcWidth-1), skipb ? (tileSrcY+tileSrcHeight-1) : tileSrcMidY);
            drawChild[2] = skipb ? false : shouldDraw(numRois, this.tileSrcX, this.tileSrcMidY, skipr ? (tileSrcX+tileSrcWidth-1) : this.tileSrcMidX, (tileSrcY+tileSrcHeight-1));
            drawChild[3] = (skipr || skipb) ? false : shouldDraw(numRois, tileSrcMidX, tileSrcMidY, (tileSrcX+tileSrcHeight), (tileSrcY+tileSrcHeight-1));

            for(int i = 0; i < 4; i++) {
                if(!drawChild[i]) {
                    if (this.children[i] != null) {
                        this.children[i].release();
                        this.children[i] = null;
                    }
                } else if(this.children[i] == null) {
                    this.children[i] = new GLQuadTileNode3(this, i);
                }
            }

            // should resolve self if:
            // - multires and any child is unresolvable
            // should draw self if:
            // - texture data available and any visible descendant not resolved

            final boolean multiRes = this.core.tileReader.isMultiResolution();

            // XXX - load texture to support child

            descendantsRequestDraw = false;
            for(int i = 0; i < 4; i++) {
                if(this.children[i] == null)
                    continue;
                this.children[i].verticesInvalid = this.verticesInvalid;
                descendantsRequestDraw |= this.children[i].drawImpl(view, level, numRois);
            }
            this.verticesInvalid = false;

            // request refresh if the descendants need to borrow and in the unresolved state
            if(this.descendantsRequestDraw && this.state == State.UNRESOLVED && !this.touched) {
                this.resolveTexture(multiRes);
                this.touched = true;
                view.requestRefresh();
            }
            return this.descendantsRequestDraw && (this.state != State.RESOLVED);
        } else { // adaptive LOD
            // determine children to draw
            int numDrawnChildren = 0;
            int[] childLevels = new int[4]; // UL, UR, LL, LR
            final int levelLimit = Math.min(this.level, Math.max(0, root.level-1));
            final boolean skipr = (tileWidth <= halfTileWidth);
            final boolean skipb = (tileHeight <= halfTileHeight);
            final double midLat = centroid.getLatitude();
            final double midLng = centroid.getLongitude();
            childLevels[0] = computeLevel(view, this.core.gsd, maxLat, minLng, skipb ? minLat : midLat, skipr ? maxLng : midLng, levelLimit, this.core.options.levelTransitionAdjustment);
            childLevels[1] = skipr ? -1 : computeLevel(view, this.core.gsd, maxLat, midLng, skipb ? minLat : midLat, maxLng, levelLimit, this.core.options.levelTransitionAdjustment);
            childLevels[2] = skipb ? -1 : computeLevel(view, this.core.gsd, midLat, minLng, minLat, skipr ? maxLng : midLng, levelLimit, this.core.options.levelTransitionAdjustment);
            childLevels[3] = (skipr || skipb) ? -1 : computeLevel(view, this.core.gsd, midLat, midLng, minLat, maxLng, levelLimit, this.core.options.levelTransitionAdjustment);

            int numChildrenToDraw = 0;
            int numChildrenUnresolvable = 0;
            int quadsVisible = 0;
            for(int i = 0; i < 4; i++) {
                if(childLevels[i] == -1 || // no child tile present
                        childLevels[i] >= this.level || // child should not be rendered
                        (childLevels[i] > (this.level-1) && !core.tileReader.isMultiResolution()) // tile out of view on non-multires reader
                ) {
                    if(childLevels[i] >= 0)
                        quadsVisible++;
                    if (this.children[i] != null) {
                        this.children[i].release();
                        this.children[i] = null;
                    }
                } else {
                    numChildrenToDraw++;
                    if (this.children[i] == null)
                        this.children[i] = new GLQuadTileNode3(this, i);
                    if(this.children[i].receivedUpdate)
                        numDrawnChildren++;
                    switch(this.children[i].state) {
                        case UNRESOLVABLE:
                            numChildrenUnresolvable++;
                            break;
                        default :
                            break;
                    }
                }
            }

            // should resolve self if:
            // - multires and any child is unresolvable
            // should draw self if:
            // - texture data available and any visible descendant not resolved

            final boolean multiRes = this.core.tileReader.isMultiResolution();
            if((multiRes || this.receivedUpdate) &&
                    ((numChildrenUnresolvable > 0) ||
                            (quadsVisible > numChildrenToDraw) ||
                            descendantsRequestDraw)) {

                // resolution of ancestor only occurs if multi-res
                this.drawImpl(view, multiRes);
            }

            descendantsRequestDraw = false;
            for(int i = 0; i < 4; i++) {
                if(this.children[i] == null)
                    continue;
                this.children[i].verticesInvalid = this.verticesInvalid;
                descendantsRequestDraw |= this.children[i].drawImpl(view, level, numRois);
            }
            this.verticesInvalid = false;

            // request refresh if the descendants need to borrow and in the unresolved state
            if(this.descendantsRequestDraw && this.state == State.UNRESOLVED && multiRes)
                view.requestRefresh();

            return this.descendantsRequestDraw && (this.state != State.RESOLVED);
        }
    }

    private void drawTexture(GLMapView view, GLTexture tex, int texCoords) {
        if (tex == this.texture) {
            if (this.texture.getMinFilter() != this.core.minFilter)
                this.texture.setMinFilter(this.core.minFilter);
            if (this.texture.getMagFilter() != this.core.magFilter)
                this.texture.setMagFilter(this.core.magFilter);
        }

        // draw tile in primary hemisphere
        drawTextureImpl(view, tex, texCoords, true);

        // draw secondary hemi, if necessary
        if (view.crossesIDL && view.drawSrid != 4978) {
            if((view.drawLng > 0d && Rectangle.intersects(this.minLng, this.minLat, this.maxLng, this.maxLat, -180d, view.southBound, view.eastBound, view.northBound)) ||
               (view.drawLng < 0d && Rectangle.intersects(this.minLng, this.minLat, this.maxLng, this.maxLat, view.westBound, view.southBound, 180d, view.northBound))) {

                drawTextureImpl(view, tex, texCoords, false);
            }
        }
    }

    private void setLCS(GLMapView view, boolean primaryHemi) {
        double tx;
        double ty;
        double tz;

        tx = centroidProj.x;
        ty = centroidProj.y;
        tz = centroidProj.z;

        // shift the tile into the secondary hemisphere if requested
        if(view.crossesIDL && view.drawSrid != 4978 && !primaryHemi)
            tx += Math.signum(view.drawLng)*360d;

        GLQuadTileNode2.setLCS(this.core.mvpPtr, this.core.shader.uMVP, tx, ty, tz);
    }

    private void drawTextureImpl(GLMapView view, GLTexture tex, int texCoords, boolean primaryHemi) {
        setLCS(view, primaryHemi);

        float fade = (tex == this.texture && this.core.fadeTimerLimit > 0L) ?
                ((float)(this.core.fadeTimerLimit
                        -this.fadeTimer) /
                        (float)this.core.fadeTimerLimit) :
                1f;

        final float r = this.core.colorR;
        final float g = this.core.colorG;
        final float b = this.core.colorB;
        final float a = fade * this.core.colorA;
        if(r != core.renderState.r || g != core.renderState.g || b != core.renderState.b || a != core.renderState.a) {
            GLES30.glUniform4f(core.shader.uColor, r, g, b, a);
            core.renderState.r = r;
            core.renderState.g = g;
            core.renderState.b = b;
            core.renderState.a = a;
        }

        // bind texture coordinates
        if(core.renderState.texCoords != texCoords)
        {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoords);
            GLES30.glVertexAttribPointer(core.shader.aTexCoords, 2, GLES30.GL_FLOAT, false, 0, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            core.renderState.texCoords = texCoords;
        }
        // bind vertex coordinates
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, this.vertexCoordinates2);
        GLES30.glVertexAttribPointer(core.shader.aVertexCoords, 3, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        core.renderState.vertexCoords = this.vertexCoordinates2;

        // bind texture
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.getTexId());

        // bind indices, if unused, this _unbinds_ any existing index binding
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, this.glTexCoordIndices2);
        if( this.glTexCoordIndices2 == GLES30.GL_NONE )
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, this.glTexGridVertCount);
        else
            GLES30.glDrawElements(GLES30.GL_TRIANGLE_STRIP, this.glTexGridIdxCount, GLES30.GL_UNSIGNED_SHORT, 0);

        core.tilesThisFrame++;
    }

    private void expandTexGrid() {
        this.glTexGridWidth *= 2;
        this.glTexGridHeight *= 2;

        this.gridVertices = new GLQuadTileNode2.GridVertex[(this.glTexGridWidth+1)*(this.glTexGridHeight+1)];

        this.textureCoordsValid = false;
    }

    /**
     * Invokes {@link #super_draw(GLMapView, boolean)}.
     *
     * @param view The view
     */
    private final void drawImpl(GLMapView view, boolean resolve) {
        this.vertexCoordsValid &= !this.verticesInvalid;

        // clear cached mesh vertices
        if(this.verticesInvalid)
            Arrays.fill(this.gridVertices, null);

        super_draw(view, resolve);
        if(this.state == State.RESOLVED && this.fadeTimer != 0L){
            this.fadeTimer = Math.max(
                    this.fadeTimer-view.animationDelta, 0L);
        }
    }

    private void super_draw(GLMapView view, boolean resolve) {
        this.view = view;

        // check the tiles version and move back into the UNRESOLVED state if
        // the tile version has changed
        if(resolve) {
            if ((this.state == State.UNRESOLVABLE || this.state == State.RESOLVED) &&
                core.versionCheckEnabled &&
                this.tileVersion != this.core.tileReader.getTileVersion(this.level, this.tileColumn, this.tileRow)) {

                if (this.state == State.UNRESOLVABLE)
                    this.state = State.UNRESOLVED;
                this.resolveTexture(resolve);
            }
            // read the data if we don't have it yet
            else if (this.shouldResolve())
                this.resolveTexture(resolve);
        }
        if(!touched && (this.state == State.RESOLVED || this.state == State.UNRESOLVABLE)) {
            touched = true;
        }
        if(!this.core.options.adaptiveTileLod) {
            if (this.state != State.RESOLVED) {
                // borrow
                this.validateTexCoordIndices();
                final GLTexture borrowedTexture = this.tryBorrow();
                if (borrowedTexture != null) {
                    this.validateVertexCoords(view);
                    this.drawTexture(view, borrowedTexture, this.borrowTextureCoordinates);
                }
            } else {
                this.borrowingFrom = null;
                this.borrowTextureCoordinates = core.resources.discardBuffer(this.borrowTextureCoordinates);
            }
        }

        if (this.receivedUpdate) {
            this.validateTexCoordIndices();
            this.validateTexVerts();
            this.validateVertexCoords(view);

            this.drawTexture(view, this.texture, this.textureCoordinates);
        }

        if(this.core.debugDrawEnabled)
            this.debugDraw(view);
    }

    private GLTexture tryBorrow() {
        GLQuadTileNode3 borrowFrom = this.parent;
        GLQuadTileNode3 updatedAncestor = null;
        while(borrowFrom != this.borrowingFrom) {
            if(borrowFrom.state == State.RESOLVED)
                break;
            else if(borrowFrom.receivedUpdate && updatedAncestor == null)
                updatedAncestor = borrowFrom;
            borrowFrom = borrowFrom.parent;
        }
        // if no ancestor is resolved, but there is an ancestor with an update, use the update
        if(borrowFrom == null && updatedAncestor != null)
            borrowFrom = updatedAncestor;
        // no texture to borrow
        if(borrowFrom == null)
            return null;
        if(borrowFrom != this.borrowingFrom) {
            this.vertexCoordsValid = false;
            if(this.borrowTextureCoordinates == GLES30.GL_NONE)
                this.borrowTextureCoordinates = core.resources.genBuffer();

            // map the region of the ancestor tile into this tile
            final float extentX = ((float) borrowFrom.tileWidth / (float) borrowFrom.texture.getTexWidth());
            final float extentY = ((float) borrowFrom.tileHeight / (float) borrowFrom.texture.getTexHeight());

            final float minX = Math.max(
                    ((float) (this.tileSrcX - borrowFrom.tileSrcX - 1) / (float) borrowFrom.tileSrcWidth) * extentX, 0.0f);
            final float minY = Math.max(
                    ((float) (this.tileSrcY - borrowFrom.tileSrcY - 1) / (float) borrowFrom.tileSrcHeight) * extentY, 0.0f);
            final float maxX = Math
                    .min(((float) ((this.tileSrcX + this.tileSrcWidth) - borrowFrom.tileSrcX + 1) / (float) borrowFrom.tileSrcWidth)
                            * extentX, 1.0f);
            final float maxY = Math.min(
                    ((float) ((this.tileSrcY + this.tileSrcHeight) - borrowFrom.tileSrcY + 1) / (float) borrowFrom.tileSrcHeight)
                            * extentY, 1.0f);

            core.resources.coordStreamBufferF.clear();
            GLTexture.createQuadMeshTexCoords(new PointF(minX, minY),
                                              new PointF(maxX, minY),
                                              new PointF(maxX, maxY),
                                              new PointF(minX, maxY),
                                              this.glTexGridWidth,
                                              this.glTexGridHeight,
                                              core.resources.coordStreamBufferF);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, this.borrowTextureCoordinates);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, this.glTexGridVertCount*2*4, core.resources.coordStreamBuffer, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);

            this.borrowingFrom = borrowFrom;
        }

        return this.borrowingFrom.texture;
    }

    private void debugDraw(GLMapView view) {
        this.validateVertexCoords(view);

        GLES30.glLineWidth(2f);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLRenderGlobals.get(view).getWhitePixel().getTexId());
        core.renderState.texId = 0;

        // bind texture coordinates
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        GLES30.glDisableVertexAttribArray(core.shader.aTexCoords);
        GLES30.glVertexAttrib2f(core.shader.aTexCoords, 0f, 0f);
        core.renderState.texCoords = GLES30.GL_NONE;

        // bind vertex coordinates
        if(core.renderState.vertexCoords != this.vertexCoordinates2) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, this.vertexCoordinates2);
            GLES30.glVertexAttribPointer(core.shader.aVertexCoords, 3, GLES30.GL_FLOAT, false, 0, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            core.renderState.vertexCoords = this.vertexCoordinates2;
        }

        this.setLCS(view, true);

        float r, g, b, a;
/*
        if(this.debugDrawIndices != null) {
            GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINES,
                    this.debugDrawIndices.limit(),
                    GLES20FixedPipeline.GL_UNSIGNED_SHORT,
                    this.debugDrawIndices);
        }
*/

        r = 0f;
        g = 1f;
        b = 1f;
        a = 1f;
        if(r != core.renderState.r || g != core.renderState.g || b != core.renderState.b || a != core.renderState.a) {
            GLES30.glUniform4f(core.shader.uColor, r, g, b, a);
            core.renderState.r = r;
            core.renderState.g = g;
            core.renderState.b = b;
            core.renderState.a = a;
        }

        final ByteBuffer dbg = com.atakmap.lang.Unsafe.allocateDirect(8);
        dbg.order(ByteOrder.nativeOrder());

        dbg.putShort((short)0);
        dbg.putShort((short)this.glTexGridWidth);
        dbg.putShort((short)(((this.glTexGridHeight + 1) * (this.glTexGridWidth + 1)) - 1));
        dbg.putShort((short)((this.glTexGridHeight * (this.glTexGridWidth + 1))));
        dbg.flip();

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GLES30.GL_NONE);

        // XXX - tints tile based on state
        if(false) {
            int color = 0;
            if (state == State.UNRESOLVABLE)
                color = 0x3FFFFFFF & Color.RED;
            else if (state == State.UNRESOLVED)
                color = 0x3FFFFFFF & Color.BLUE;
            else if (state == State.RESOLVED)
                color = 0x3FFFFFFF & Color.GREEN;
            else if (state == State.RESOLVING)
                color = 0x3FFFFFFF & Color.CYAN;
            else if (state == State.SUSPENDED)
                color = 0x3FFFFFFF & Color.YELLOW;
            r = Color.red(color) / 255f;
            g = Color.green(color) / 255f;
            b = Color.blue(color) / 255f;
            a = Color.alpha(color) / 255f;
            if (r != core.renderState.r || g != core.renderState.g || b != core.renderState.b || a != core.renderState.a) {
                GLES30.glUniform4f(core.shader.uColor, r, g, b, a);
                core.renderState.r = r;
                core.renderState.g = g;
                core.renderState.b = b;
                core.renderState.a = a;
            }
            GLES30.glDrawElements(GLES30.GL_TRIANGLE_FAN, 4, GLES30.GL_UNSIGNED_SHORT, dbg);
        }

        r = 0f;
        g = 1f;
        b = 1f;
        a = 1f;
        if(r != core.renderState.r || g != core.renderState.g || b != core.renderState.b || a != core.renderState.a) {
            GLES30.glUniform4f(core.shader.uColor, r, g, b, a);
            core.renderState.r = r;
            core.renderState.g = g;
            core.renderState.b = b;
            core.renderState.a = a;
        }
        GLES30.glDrawElements(GLES30.GL_LINE_LOOP, 4, GLES30.GL_UNSIGNED_SHORT, dbg);
        Unsafe.free(dbg);
        core.renderState.indices = GLES30.GL_NONE;

        GLES30.glUseProgram(GLES30.GL_NONE);

         com.atakmap.opengl.GLText _titleText = com.atakmap.opengl.GLText.getInstance(com.atakmap.map.AtakMapView.getDefaultTextFormat());
         view.scratch.pointD.x = this.tileSrcX+this.tileSrcWidth/2f;
         view.scratch.pointD.y = this.tileSrcY+this.tileSrcHeight/2f;
         this.core.imprecise.imageToGround(view.scratch.pointD, view.scratch.geo);
         view.forward(view.scratch.geo, view.scratch.pointF);
         com.atakmap.opengl.GLES20FixedPipeline.glPushMatrix();
         com.atakmap.opengl.GLES20FixedPipeline.glTranslatef(view.scratch.pointF.x, view.scratch.pointF.y, 0);
         _titleText.draw(this.tileColumn + "," + this.tileRow + "," + this.level + " " + this.state.name(), 0.0f, 0.0f, 1.0f, 1.0f);
          com.atakmap.opengl.GLES20FixedPipeline.glTranslatef(0f, -_titleText.getBaselineSpacing(), 0);
         _titleText.draw((new java.io.File(core.tileReader.getUri())).getName(), 0.0f, 0.0f, 1.0f, 1.0f);
         com.atakmap.opengl.GLES20FixedPipeline.glPopMatrix();

        // restore the program, re-enable attribute arrays
         GLES30.glUseProgram(core.shader.handle);
         GLES30.glEnableVertexAttribArray(core.shader.aVertexCoords);
         GLES30.glEnableVertexAttribArray(core.shader.aTexCoords);

         GLES30.glEnable(GLES30.GL_BLEND);
         GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Abandons all of the children.
     */
    private void abandon() {
        for (int i = 0; i < this.children.length; i++) {
            if (this.children[i] != null) {
                this.children[i].release();
                this.children[i] = null;
            }
        }
    }

    /**************************************************************************/

    // XXX - implementation for getState

    @Override
    public State getState() {
        // check for bits in order of precedence
        if(MathUtils.hasBits(this.core.stateMask, STATE_RESOLVING))
            // resolving if any resolving
            return State.RESOLVING;
        else if(MathUtils.hasBits(core.stateMask, STATE_UNRESOLVED))
            // unresolved if any unresolved
            return State.UNRESOLVED;
        else if(MathUtils.hasBits(core.stateMask, STATE_UNRESOLVABLE))
            // unresolvable if toggled and no resolving or unresolved
            return State.UNRESOLVABLE;
        else if(MathUtils.hasBits(core.stateMask, STATE_RESOLVED))
            // resolved if toggled and no resolving, unresolved or unresolvable
            return State.RESOLVED;
        else if(MathUtils.hasBits(core.stateMask, STATE_SUSPENDED))
            // suspended if toggled and no others
            return State.SUSPENDED;
        else
            // no bits, unresolved
            return State.UNRESOLVED;
    }

    @Override
    public void suspend() {
        core.suspended = true;
        for (int i = 0; i < this.children.length; i++)
            if (this.children[i] != null)
                this.children[i].suspend();
        if (this.state == State.RESOLVING && this.currentRequest != null) {
            this.currentRequest.cancel();
            this.state = State.SUSPENDED;
        }
    }

    @Override
    public void resume() {
        core.suspended = false;
        for (int i = 0; i < this.children.length; i++)
            if (this.children[i] != null)
                this.children[i].resume();
        // move us back into the unresolved from suspended to re-enable texture
        // loading
        if (this.state == State.SUSPENDED) {
            this.state = State.UNRESOLVED;
            this.currentRequest = null;
        }
    }

    /**************************************************************************/
    // RasterDataAccess2

    @Override
    public String getType() {
        return this.core.type;
    }

    @Override
    public String getUri() {
        return this.core.uri;
    }

    @Override
    public boolean imageToGround(PointD image, GeoPoint ground, boolean[] precise) {
        if(this.core.precise != null && precise != null) {
            if(this.core.precise.imageToGround(image, ground)) {
                // we were able to compute the precise I2G value -- make sure it
                // falls within the maximum allowed error
                GeoPoint imprecise = GeoPoint.createMutable();
                this.core.imprecise.imageToGround(image, imprecise);
                final double err = DistanceCalculations.calculateRange(ground, imprecise);
                final int errPixels = (int) (err / this.core.gsd);
                final int tileWidth = this.core.tileReader.getTileWidth();
                final int tileHeight = this.core.tileReader.getTileHeight();
                if (errPixels > (Math.sqrt((tileWidth * tileWidth) +
                        (tileHeight * tileHeight)) / 8d)) {

                    Log.w(TAG, "Large discrepency observed for " + this.getType() +" imageToGround, discarding point (error="
                            + err + "m, " + errPixels + "px)");

                    // fall through to imprecise computation
                } else {
                    precise[0] = true;
                    return true;
                }
            }
        }
        if(precise != null) precise[0] = false;
        return this.core.imprecise.imageToGround(image, ground);
    }

    @Override
    public boolean groundToImage(GeoPoint ground, PointD image, boolean[] precise) {
        if(this.core.precise != null && precise != null) {
            if(this.core.precise.groundToImage(ground, image)) {
                // we were able to compute the precise G2I value -- make sure it
                // falls within the maximum allowed error
                PointD imprecise = new PointD(0d, 0d);
                this.core.imprecise.groundToImage(ground, imprecise);
                final double dx = (image.x-imprecise.x);
                final double dy = (image.y-imprecise.y);
                final double errPixels = Math.sqrt(dx*dx + dy*dy);
                final double errMeters = (int) (errPixels * this.core.gsd);
                final int tileWidth = this.core.tileReader.getTileWidth();
                final int tileHeight = this.core.tileReader.getTileHeight();
                if (errPixels > (Math.sqrt((tileWidth * tileWidth) +
                        (tileHeight * tileHeight)) / 8d)) {

                    Log.w(TAG, "Large discrepency observed for " + this.getType() + " groundToImage, discarding point (error="
                            + errMeters + "m, " + errPixels + "px)");
                    return false;
                } else {
                    precise[0] = true;
                    return true;
                }
            }
        }
        if(precise != null) precise[0] = false;
        return this.core.imprecise.groundToImage(ground, image);
    }

    @Override
    public int getSpatialReferenceId() {
        return this.core.srid;
    }

    @Override
    public boolean hasPreciseCoordinates() {
        return (this.core.precise != null);
    }

    @Override
    public int getWidth() {
        return (int)this.core.tileReader.getWidth();
    }

    @Override
    public int getHeight() {
        return (int)this.core.tileReader.getHeight();
    }

    /**************************************************************************/
    // Asynchronous Read Request Listener

    /**
     * <P>
     * <B>IMPORTANT:</B> Must be invoked on the render thread.
     *
     * @param id
     * @return
     */
    private boolean checkRequest(int id) {
        return (this.currentRequest != null && this.currentRequest.id == id);
    }

    @Override
    public void requestCreated(TileReader.ReadRequest request) {
        this.currentRequest = request;
    }

    @Override
    public void requestStarted(int id) {
        if (DEBUG) {
            Log.d(TAG, toString(false) + " requestStarted(id=" + id + "), currentRequest="
                    + this.currentRequest);
        }
    }

    @Override
    public void requestUpdate(final int id, byte[] data, final int dstX, final int dstY,
                              final int dstW, final int dstH) {
        if (DEBUG) {
            Log.d(TAG, toString(false) + " requestUpdate(id=" + id + "), currentRequest="
                    + this.currentRequest);
        }

        final TileReader.ReadRequest current = this.currentRequest;
        if (current == null || current.id != id)
            return;

        final int transferSize = GdalGraphicUtils.getBufferSize(this.glTexFormat, this.glTexType, dstW, dstH);
        ByteBuffer transferBuffer = transferBuffers.get();
        if(transferBuffer == null || transferBuffer.capacity() < transferSize) {
            Unsafe.free(transferBuffer);
            transferBuffer = Unsafe.allocateDirect(transferSize);
            transferBufferAllocs++;
        }
        transferBuffer.clear();
        buffersTransferred++;

        GdalGraphicUtils.fillBuffer(transferBuffer, data,
                dstW,
                dstH,
                this.core.tileReader.getInterleave(),
                this.core.tileReader.getFormat(),
                this.glTexFormat,
                this.glTexType);
        final ByteBuffer buf = transferBuffer;

        this.view.queueEvent(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!checkRequest(id))
                        return;
                    // ensure there is a texture to load to, with correct format/type/size
                    GLQuadTileNode3.this.validateTexture();
                    // upload the data
                    GLQuadTileNode3.this.texture.load(buf, dstX, dstY, dstW, dstH);
                    // mark that data is received
                    GLQuadTileNode3.this.receivedUpdate = true;
                } finally {
                    if(!transferBuffers.put(buf))
                        Unsafe.free(buf);
                }
            }
        });
    }

    static int transferBufferAllocs = 0;
    static int buffersTransferred = 0;

    @Override
    public void requestCompleted(final int id) {
        if (DEBUG) {
            Log.d(TAG, toString(false) + " requestCompleted(id=" + id + "), currentRequest="
                    + this.currentRequest);
        }

        final TileReader.ReadRequest current = this.currentRequest;
        if (current == null || current.id != id)
            return;

        this.view.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (!GLQuadTileNode3.this.checkRequest(id))
                    return;
                GLQuadTileNode3.this.readRequestComplete = System.currentTimeMillis();
                GLQuadTileNode3.this.readRequestElapsed = (readRequestComplete-readRequestStart);

                GLQuadTileNode3.this.state = State.RESOLVED;
                GLQuadTileNode3.this.resetFadeTimer();

                if(mipmapEnabled) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLQuadTileNode3.this.texture.getTexId());
                    GLQuadTileNode3.this.texture.setMinFilter(GLES30.GL_LINEAR_MIPMAP_NEAREST);
                    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
                }


                // XXX - should be packaged in read request
                GLQuadTileNode3.this.tileVersion = GLQuadTileNode3.this.core.tileReader.getTileVersion(current.level, current.tileColumn, current.tileRow);

                GLQuadTileNode3.this.currentRequest = null;
            }
        });
    }

    @Override
    public void requestCanceled(final int id) {
        if (DEBUG)
            Log.d(TAG, toString(false) + " requestCanceled(id=" + id + "), currentRequest="
                    + this.currentRequest);
        final TileReader.ReadRequest current = this.currentRequest;
        if (current == null || current.id != id)
            return;

        this.view.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (!GLQuadTileNode3.this.checkRequest(id))
                    return;
                GLQuadTileNode3.this.currentRequest = null;
            }
        });
    }

    @Override
    public void requestError(final int id, Throwable error) {
        if (DEBUG) {
            Log.d(TAG, toString(false) + " requestError(id=" + id + "), currentRequest="
                    + this.currentRequest);
        }

        Log.e(TAG, "asynchronous read error", error);

        final TileReader.ReadRequest current = this.currentRequest;
        if (current == null || current.id != id)
            return;

        this.view.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (!GLQuadTileNode3.this.checkRequest(id))
                    return;
                GLQuadTileNode3.this.currentRequest = null;

                // XXX - should be packaged in read request
                GLQuadTileNode3.this.tileVersion = GLQuadTileNode3.this.core.tileReader.getTileVersion(current.level, current.tileColumn, current.tileRow);

                GLQuadTileNode3.this.state = State.UNRESOLVABLE;
            }
        });
    }

    /**************************************************************************/
    // Disposable

    @Override
    public void dispose() {
        if(this.root == this)
            this.core.dispose();
    }

    /**************************************************************************/
    // Object

    public String toString() {
        return this.toString(true);
    }

    public String toString(boolean l) {
        String className = this.getClass().getName();
        className = className.substring(className.lastIndexOf('.') + 1);
        StringBuilder retval = new StringBuilder(className + "@" + Integer.toString(hashCode(), 16));
        if (l)
            retval.append(" {level=" + this.level + ",tileColumn=" + this.tileColumn + ",tileRow="
                    + this.tileRow + ",tileWidth=" + this.tileWidth + ",tileHeight="
                    + this.tileHeight + "}");
        return retval.toString();
    }

    /**************************************************************************/

    private static class VertexCoordInfo {
        public final int srid;
        public final boolean valid;
        public final boolean projected;

        public VertexCoordInfo(int srid, boolean valid, boolean projected) {
            this.srid = srid;
            this.valid = valid;
            this.projected = projected;
        }
    }

    private static class DefaultVertexResolver implements VertexResolver<GLQuadTileNode3> {
        private PointD scratchImg;
        final RasterDataAccess2 i2g;

        public DefaultVertexResolver(RasterDataAccess2 i2g) {
            this.scratchImg = new PointD(0d, 0d);
            this.i2g = i2g;
        }

        @Override
        public void beginDraw(GLMapView ignored) {}
        @Override
        public void endDraw(GLMapView ignored) {}

        @Override
        public void beginNode(GLQuadTileNode3 ignored) {}

        @Override
        public void endNode(GLQuadTileNode3 ignored) {}

        @Override
        public void project(GLMapView view, long imgSrcX, long imgSrcY, GLQuadTileNode2.GridVertex vert) {
            this.scratchImg.x = imgSrcX;
            this.scratchImg.y = imgSrcY;
            this.i2g.imageToGround(this.scratchImg, vert.value, null);
        }

        @Override
        public void release() {}
    }

    private class PreciseVertexResolver extends DefaultVertexResolver implements Runnable {

        private final Object syncOn;
        private Thread active;
        private LinkedList<PointL> queue;
        private PointL query;
        private Set<PointL> pending;
        private Set<PointL> unresolvable;
        private Map<PointL, GeoPoint> precise;
        private GLQuadTileNode3 currentNode;

        private Set<PointL> currentRequest;
        private Set<GLQuadTileNode3> requestNodes;

        private GeoPoint scratchGeo;
        private PointD scratchImg;

        private GLMapView view;

        private int needsResolved;
        private int requested;
        private int numNodesPending;

        private boolean initialized;

        public PreciseVertexResolver(RasterDataAccess2 i2g, Object syncOn) {
            super(i2g);

            if (syncOn == null)
                syncOn = this;
            this.syncOn = syncOn;
            this.queue = new LinkedList<>();
            this.pending = new TreeSet<>(POINT_COMPARATOR);
            this.precise = new TreeMap<>(POINT_COMPARATOR);
            this.unresolvable = new TreeSet<>(POINT_COMPARATOR);

            this.currentRequest = new TreeSet<>(POINT_COMPARATOR);

            this.requestNodes = new HashSet<>();

            this.scratchGeo = GeoPoint.createMutable();
            this.scratchImg = new PointD(0, 0);
            this.query = new PointL();

            this.initialized = false;

            // fill the four corners -- we can assume that these are precisely
            // defined for the dataset projection
            final long minx = 0;
            final long miny = 0;
            final long maxx = core.tileReader.getWidth();
            final long maxy = core.tileReader.getHeight();

            GeoPoint ul = GeoPoint.createMutable();
            core.imprecise.imageToGround(new PointD(minx, miny), ul);
            this.precise.put(new PointL(minx, miny), ul);

            GeoPoint ur = GeoPoint.createMutable();
            core.imprecise.imageToGround(new PointD(maxx, miny), ur);
            this.precise.put(new PointL(maxx, miny), ur);

            GeoPoint lr = GeoPoint.createMutable();
            core.imprecise.imageToGround(new PointD(maxx, maxy), lr);
            this.precise.put(new PointL(maxx, maxy), lr);

            GeoPoint ll = GeoPoint.createMutable();
            core.imprecise.imageToGround(new PointD(minx, maxy), ll);
            this.precise.put(new PointL(minx, maxy), ll);
        }

        private GeoPoint preciseImageToGround(PointD image, boolean[] preciseFlag) {
            GeoPoint ground = GeoPoint.createMutable();
            preciseFlag[0] = false;
            if(!i2g.imageToGround(image, ground, preciseFlag) || !preciseFlag[0])
                return null;
            return ground;
        }

        private void resolve() {
            synchronized (this.syncOn) {
                if (this.unresolvable.contains(this.query))
                    return;
                this.needsResolved++;
                PointL p = new PointL(this.query);
                this.currentRequest.add(p);
                if (this.pending.contains(p)) {
                    this.syncOn.notify();
                    return;
                }
                this.queue.addLast(p);
                this.pending.add(p);

                if (this.active == null) {
                    this.active = new Thread(this);
                    this.active.setName("Precise Vertex Resolver "
                            + Integer.toString(this.active.hashCode(), 16) + "-"
                            + this.active.getId());
                    this.active.setPriority(Thread.NORM_PRIORITY);
                    this.active.start();
                }
                this.syncOn.notify();
            }
        }

        @Override
        public void beginDraw(GLMapView view) {
            this.currentRequest.clear();
            this.numNodesPending = 0;
        }

        @Override
        public void endDraw(GLMapView view) {
            if (!view.targeting && this.numNodesPending == 0 && this.view != null) {
                // all node grids are full resolved, go ahead and expand the
                // grids
                int minGridWidth = 16;
                for (GLQuadTileNode3 node : this.requestNodes)
                    if (node.glTexGridWidth < minGridWidth)
                        minGridWidth = node.glTexGridWidth;

                for (final GLQuadTileNode3 node : this.requestNodes) {
                    if (node.glTexGridWidth > minGridWidth)
                        continue;

                    final int targetGridWidth = 1 << (4 - Math.min(node.level << 1, 4));
                    if (node.glTexGridWidth < targetGridWidth)
                        view.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                if (node.glTexGridWidth < targetGridWidth) {
                                    node.expandTexGrid();
                                    GLQuadTileNode3.this.verticesInvalid = true;
                                }
                            }
                        });
                }
            }
            this.requestNodes.clear();

            synchronized (this.syncOn) {
                this.queue.retainAll(this.currentRequest);
                this.pending.retainAll(this.currentRequest);
                this.currentRequest.clear();
            }
        }

        /**********************************************************************/
        // Vertex Resolver

        @Override
        public void release() {
            synchronized (this.syncOn) {
                this.active = null;
                this.queue.clear();
                this.pending.clear();
                this.syncOn.notify();
                this.view = null;

                if (core.textureCache != null
                        && (this.precise.size() > 4 || this.unresolvable.size() > 0)) {
                    // XXX - need to restrict how many vertices we are storing
                    // in the cache
                    java.nio.ByteBuffer buffer = serialize(this.precise, this.unresolvable);
                    core.textureCache.put(getUri() + ",coords",
                            new GLTexture(1, buffer.capacity(),
                                    GLES30.GL_LUMINANCE,
                                    GLES30.GL_UNSIGNED_BYTE),
                            0,
                            null,
                            null,
                            0,
                            buffer);
                }

                this.precise.clear();
                this.unresolvable.clear();
                this.initialized = false;
            }
        }

        @Override
        public void beginNode(GLQuadTileNode3 node) {
            super.beginNode(node);

            this.currentNode = node;
            this.needsResolved = 0;
            this.requested = 0;
            this.requestNodes.add(node);
        }

        @Override
        public void endNode(final GLQuadTileNode3 node) {
            this.currentNode = null;
            // update our pending count if the node needs one or more vertices
            // resolved
            if (this.requested > 0 && this.needsResolved > 0)
                this.numNodesPending++;

            super.endNode(node);
        }

        @Override
        public void project(GLMapView view, long imgSrcX, long imgSrcY, GLQuadTileNode2.GridVertex retval) {
            GeoPoint geo = null;
            if (!view.targeting) {
                this.requested++;
                this.view = view;

                this.query.x = imgSrcX;
                this.query.y = imgSrcY;

                synchronized (this.syncOn) {
                    geo = this.precise.get(this.query);
                    if (geo == null && !this.initialized) {
                        try {
                            if (core.textureCache != null) {
                                GLTextureCache.Entry entry = core.textureCache.remove(getUri()
                                        + ",coords");
                                if (entry != null) {
                                    deserialize((java.nio.ByteBuffer) entry.opaque, this.precise,
                                            this.unresolvable);
                                    this.queue.removeAll(this.precise.keySet());
                                    this.queue.removeAll(this.unresolvable);
                                    this.pending.removeAll(this.precise.keySet());
                                    this.pending.removeAll(this.unresolvable);
                                }
                                geo = this.precise.get(this.query);
                            }
                        } finally {
                            this.initialized = true;
                        }
                    }

                    if (geo == null) {
                        this.resolve();

                        // try to obtain the next and previous points, if
                        // present we can interpolate this point

                        if (this.currentNode != null) {
                            final long texGridIncrementX = (this.currentNode.tileSrcWidth / this.currentNode.glTexGridWidth);
                            final long texGridIncrementY = (this.currentNode.tileSrcHeight / this.currentNode.glTexGridHeight);

                            final long prevImgSrcX = imgSrcX - texGridIncrementX;
                            final long prevImgSrcY = imgSrcY - texGridIncrementY;
                            final long nextImgSrcX = imgSrcX + texGridIncrementX;
                            final long nextImgSrcY = imgSrcY + texGridIncrementY;

                            GeoPoint interpolate0 = null;
                            GeoPoint interpolate1 = null;

                            // check horizontal interpolation
                            this.query.y = imgSrcY;

                            this.query.x = prevImgSrcX;
                            interpolate0 = this.precise.get(this.query);
                            this.query.x = nextImgSrcX;
                            interpolate1 = this.precise.get(this.query);
                            if (interpolate0 != null && interpolate1 != null) {
                                geo = new GeoPoint(
                                        (interpolate0.getLatitude() + interpolate1.getLatitude()) / 2.0d,
                                        (interpolate0.getLongitude() + interpolate1.getLongitude()) / 2.0d);
                            }

                            // check vertical interpolation
                            if (geo == null) {
                                interpolate0 = null;
                                interpolate1 = null;
                                this.query.x = imgSrcX;

                                this.query.y = prevImgSrcY;
                                interpolate0 = this.precise.get(this.query);
                                this.query.y = nextImgSrcY;
                                interpolate1 = this.precise.get(this.query);
                                if (interpolate0 != null && interpolate1 != null) {
                                    geo = new GeoPoint(
                                            (interpolate0.getLatitude() + interpolate1.getLatitude()) / 2.0d,
                                            (interpolate0.getLongitude() + interpolate1
                                                    .getLongitude()) / 2.0d);
                                }
                            }

                            // check cross interpolation
                            if (geo == null) {
                                interpolate0 = null;
                                interpolate1 = null;

                                // XXX - just doing this quickly along one
                                // diagonal, but should really be doing a
                                // bilinear interpolation
                                this.query.x = prevImgSrcX;
                                this.query.y = prevImgSrcY;
                                interpolate0 = this.precise.get(this.query);
                                this.query.x = nextImgSrcX;
                                this.query.y = nextImgSrcY;
                                interpolate1 = this.precise.get(this.query);
                                if (interpolate0 != null && interpolate1 != null) {
                                    geo = new GeoPoint(
                                            (interpolate0.getLatitude() + interpolate1.getLatitude()) / 2.0d,
                                            (interpolate0.getLongitude() + interpolate1
                                                    .getLongitude()) / 2.0d);
                                }
                            }
                        }
                    }
                }
            }
            if (geo == null) {
                super.project(view, imgSrcX, imgSrcY, retval);
            } else {
                retval.value.set(geo);
                retval.resolved = true;
            }
        }

        /**********************************************************************/
        // Runnable

        @Override
        public void run() {
            try {
                PointL processL = null;
                PointD processD = new PointD(0, 0);
                GeoPoint result = null;
                boolean[] preciseFlag = new boolean[1];
                while (true) {
                    synchronized (this.syncOn) {
                        if (result != null)
                            this.precise.put(processL, result);
                        else if (processL != null)
                            this.unresolvable.add(processL);

                        if (processL != null) {
                            this.pending.remove(processL);

                            if (view != null)
                                view.queueEvent(new Runnable() {
                                    @Override
                                    public void run() {
                                        GLQuadTileNode3.this.verticesInvalid = true;
                                    }
                                });
                        }

                        processL = null;
                        result = null;
                        if (this.active != Thread.currentThread())
                            break;
                        if (this.queue.size() < 1) {
                            try {
                                this.syncOn.wait();
                            } catch (InterruptedException ignored) {
                            }
                            continue;
                        }
                        processL = this.queue.removeFirst();
                    }

                    processD.x = processL.x;
                    processD.y = processL.y;
                    result = this.preciseImageToGround(processD, preciseFlag);
                    if (result != null
                            && (Double.isNaN(result.getLatitude()) || Double.isNaN(result
                                    .getLongitude())))
                        result = null;
                }
            } catch (Throwable t) {
                Log.e(TAG, "error: ", t);
            } finally {
                synchronized (this.syncOn) {
                    if (this.active == Thread.currentThread())
                        this.active = null;
                }
            }
        }
    }

    private static java.nio.ByteBuffer serialize(Map<PointL, GeoPoint> precise,
            Set<PointL> unresolvable) {

        java.nio.ByteBuffer retval = com.atakmap.lang.Unsafe.allocateDirect((4 + (32 * precise.size()))
                + (4 + (16 * unresolvable.size())));
        retval.order(ByteOrder.nativeOrder());
        retval.putInt(precise.size());
        for (Map.Entry<PointL, GeoPoint> e : precise.entrySet()) {
            retval.putLong(e.getKey().x);
            retval.putLong(e.getKey().y);
            retval.putDouble(e.getValue().getLatitude());
            retval.putDouble(e.getValue().getLongitude());
        }

        retval.putInt(unresolvable.size());
        for (PointL p : unresolvable) {
            retval.putLong(p.x);
            retval.putLong(p.y);
        }

        retval.flip();
        return retval;
    }

    private static void deserialize(java.nio.ByteBuffer inputStream, Map<PointL, GeoPoint> precise,
            Set<PointL> unresolvable) {
        int size;

        size = inputStream.getInt();
        for (int i = 0; i < size; i++) {
            precise.put(new PointL(inputStream.getLong(), inputStream.getLong()), new GeoPoint(
                    inputStream.getDouble(), inputStream.getDouble()));
        }

        size = inputStream.getInt();
        for (int i = 0; i < size; i++) {
            unresolvable.add(new PointL(inputStream.getLong(), inputStream.getLong()));
        }
    }

    /**************************************************************************/

    public static class Options {
        public boolean textureCopyEnabled;
        public boolean childTextureCopyResolvesParent;
        public GLTextureCache textureCache;
        public boolean progressiveLoad;
        public double levelTransitionAdjustment;
        public boolean textureBorrowEnabled;
        public boolean adaptiveTileLod;

        public Options() {
            this.textureCopyEnabled = true;
            this.childTextureCopyResolvesParent = true;
            this.textureCache = null;
            this.progressiveLoad = true;
            this.levelTransitionAdjustment = 0d;
            this.textureBorrowEnabled = true;
            this.adaptiveTileLod = false;
        }
    }
}
