
package com.atakmap.map.layer.raster.tilereader.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.DistanceCalculations;


import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.gdal.GdalGraphicUtils;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.map.projection.PointL;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.opengl.GLWireFrame;
import com.atakmap.util.Collections2;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Disposable;
import com.atakmap.util.Releasable;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.RectD;
import com.atakmap.math.Rectangle;

/**
 * @deprecated use {@link GLQuadTileNode3}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLQuadTileNode2 implements
                                    GLResolvableMapRenderable,
                                    TileReader.AsynchronousReadRequestListener,
                                    RasterDataAccess2,
                                    Disposable {

    private static final String TAG = "GLQuadTileNode2";

    final static Comparator<GLQuadTileNode2> ORPHANING_COMPARATOR = new Comparator<GLQuadTileNode2>() {
    @Override
    public int compare(GLQuadTileNode2 n1, GLQuadTileNode2 n2) {
        if (n1.texture != null && n2.texture != null) {
            int dx = (n1.texture.getTexWidth() - n2.texture.getTexWidth());
            int dy = (n1.texture.getTexHeight() - n2.texture.getTexHeight());
            if ((dx * dy) > 0)
                return dx;
        } else if (n1.texture != null && n2.texture == null) {
            return 1;
        } else if (n1.texture == null && n2.texture != null) {
            return -1;
        }

        return (n1.hashCode() - n2.hashCode());
        }
    };

    /*************************************************************************/

    protected final static boolean DEBUG = false;

    static {
        GdalTileReader.setPaletteRgbaFormat(GdalTileReader.Format.RGBA);
    }

    protected final static int TEXTURE_CACHE_HINT_RESOLVED = 0x00000001;

    protected NodeCore core;

    /**
     * The read request currently being serviced. Valid access to the request is only guaranteed on
     * the render thread.
     */
    protected TileReader.ReadRequest currentRequest;

    protected GLTexture texture;

    /*private*/ GLMapView view;

    protected long tileRow;
    protected long tileColumn;
    protected int level;

    protected State state;

    // source coordinate space (unscaled)
    protected long tileSrcX;
    protected long tileSrcY;
    protected long tileSrcWidth;
    protected long tileSrcHeight;

    protected int tileWidth;
    protected int tileHeight;

    /**
     * The texture coordinate for the texture in the order lower-left,
     * lower-right, upper-right, upper-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     */
    protected FloatBuffer textureCoordinates;
    /**
     * The texture coordinate for the texture in the order upper-left,
     * upper-right, lower-right, lower-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     */
    protected FloatBuffer vertexCoordinates;

    protected ByteBuffer glTexCoordIndices;

    protected ByteBuffer loadingTextureCoordinates;

    protected boolean textureCoordsValid;
    protected boolean receivedUpdate;

    protected int glTexType;
    protected int glTexFormat;

    protected int glTexGridWidth;
    protected int glTexGridHeight;
    protected int glTexGridVertCount;
    protected int glTexGridIdxCount;

    protected int vertexCoordSrid;
    protected int vertsDrawVersion;
    protected boolean vertexCoordsValid;
    protected GridVertex[] gridVertices;
    private GeoPoint centroid;
    private GeoPoint centroidHemi2;
    private int primaryHemi;
    private PointD centroidProj;
    private PointD centroidProjHemi2;

    private double minLat;
    private double minLng;
    private double maxLat;
    private double maxLng;

    protected boolean touched;

    protected long tileVersion;

    /**
     * This node's children, in the order: upper-left, upper-right, lower-left, lower-right.
     */
    protected GLQuadTileNode2[] children;

    protected int halfTileWidth;
    protected int halfTileHeight;

    protected GLQuadTileNode2 parent;
    protected final GLQuadTileNode2 root;

    protected GLQuadTileNode2 borrowingFrom;

    protected Set<GLQuadTileNode2> borrowers;

    /* OWNED BY ROOT */

    protected boolean verticesInvalid;

    protected int loadingTexCoordsVertCount;

    protected boolean derivedUnresolvableData;

    protected boolean shouldBorrowTexture;

    private long lastTouch;

    private long fadeTimer;

    private long readRequestStart;
    private long readRequestComplete;
    private long readRequestElapsed;

    private Buffer debugDrawIndices;

    private boolean isOverdraw;

    /**
     * This is the lazy loaded key for the texture to be used. It needs to be nulled out when the
     * value has become invalid
     */
    protected String textureKey = null;

    /**************************************************************************/

    /**
     * Creates a new root node.
     *
     * @param info          The image information
     * @param readerOpts    The {@link TileReaderFactory.Options} to be used
     *                      when creating the {@link TileReader} associated with
     *                      this node. May be <code>null</code>
     * @param opts          The render configuration {@link Options}. May be
     *                      <code>null</code>.
     * @param init          The initializer for this node.
     */
    public GLQuadTileNode2(ImageInfo info, TileReaderFactory.Options readerOpts, Options opts, Initializer init) {
        this(null, -1, NodeCore.create(info, readerOpts, opts, true, init));
    }

    /**
     * Creates a new child node.
     *
     * @param parent The parent node
     * @param idx The index; <code>0</code> for upper-left, <code>1</code> for upper-right,
     *            <code>2</code> for lower-left and <code>3</code> for lower-right.
     */
    protected GLQuadTileNode2(GLQuadTileNode2 parent, int idx) {
        this(parent, idx, parent.core);
    }

    private GLQuadTileNode2(GLQuadTileNode2 parent, int idx, NodeCore core) {
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
                core.vertexResolver = new PreciseVertexResolver(null);
            else
                core.vertexResolver = new DefaultVertexResolver();

            // vertex resolver is to be released prior to I2G functions
            core.releasables.addFirst(core.vertexResolver);
        }

        this.vertexCoordSrid = -1;
        this.vertexCoordsValid = false;

        this.touched = false;

        this.tileVersion = -1L;

        // super

        this.children = new GLQuadTileNode2[4];
        this.borrowers = Collections
                .newSetFromMap(new IdentityHashMap<GLQuadTileNode2, Boolean>());

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

        this.shouldBorrowTexture = false;

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

    protected VertexResolver createDefaultVertexResolver() {
        return new DefaultVertexResolver();
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
    protected void set(long tileColumn, long tileRow, int level) {
        if (this.tileColumn != tileColumn || this.tileRow != tileRow || this.level != level) {
            if (this.borrowingFrom != null) {
                this.borrowingFrom.unborrowTexture(this);
                this.borrowingFrom = null;
            }

            for (int i = 0; i < 4; i++)
                if (this.children[i] != null)
                    this.children[i].set((tileColumn * 2) + (i % 2), (tileRow * 2) + (i / 2),
                            level - 1);
        }

        super_set(tileColumn, tileRow, level);
    }

    private void super_set(long tileColumn, long tileRow, int level) {
        if (DEBUG)
            Log.d(TAG, toString(false) + " set(tileColumn=" + tileColumn + ",tileRow=" + tileRow
                    + ",level=" + level + ")");
        if (this.tileColumn == tileColumn && this.tileRow == tileRow && this.level == level)
            return;

        if (this.currentRequest != null) {
            this.currentRequest.cancel();
            this.currentRequest = null;
        }

        if (this.core.textureCache != null && ((this.state == State.RESOLVED) || this.receivedUpdate))
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
        this.textureKey = null;
        this.tileVersion = -1L;

        this.glTexFormat = GdalGraphicUtils.getBufferFormat(this.core.tileReader.getFormat());
        this.glTexType = GdalGraphicUtils.getBufferType(this.core.tileReader.getFormat());

        if (this.glTexFormat != GLES20FixedPipeline.GL_RGBA
                && !(MathUtils.isPowerOf2(this.tileWidth) && MathUtils.isPowerOf2(this.tileHeight))) {
            this.glTexFormat = GLES20FixedPipeline.GL_RGBA;
            this.glTexType = GLES20FixedPipeline.GL_UNSIGNED_BYTE;
        } else if (this.level >= this.core.tileReader.getMinCacheLevel()) {
            // if we are pulling from the cache, we want alpha since the tile
            // will be delivered as parts rather than as a whole
            if (this.glTexFormat == GLES20FixedPipeline.GL_LUMINANCE) {
                this.glTexFormat = GLES20FixedPipeline.GL_LUMINANCE_ALPHA;
                this.glTexType = GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            } else if (this.glTexFormat == GLES20FixedPipeline.GL_RGB) {
                this.glTexFormat = GLES20FixedPipeline.GL_RGBA;
                // if(this.glTexType == GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5)
                // this.glTexType = GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1;
                this.glTexType = GLES20FixedPipeline.GL_UNSIGNED_BYTE;
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

        this.gridVertices = new GridVertex[(this.glTexGridWidth+1)*(this.glTexGridHeight+1)];

        this.centroid = new GeoPoint((minLat+maxLat)/2d, (minLng+maxLng)/2d);
        this.centroidProj = new PointD(0d, 0d, 0d);

        this.centroidHemi2 = GeoPoint.createMutable();
        this.centroidProjHemi2 = new PointD(0d, 0d, 0d);
    }

    public void setLoadingTextureEnabled(boolean enabled) {
        this.core.loadingTextureEnabled = enabled;
    }

    protected void releaseTexture() {
        final boolean resolved = (this.state == State.RESOLVED);
        if (this.core.textureCache != null && (resolved || this.receivedUpdate)) {
            this.core.textureCache.put(this.getTextureKey(),
                                  this.texture,
                                  this.glTexGridVertCount,
                                  this.textureCoordinates,
                                  this.vertexCoordinates,
                                  this.glTexGridIdxCount,
                                  this.glTexCoordIndices,
                                  resolved ? TEXTURE_CACHE_HINT_RESOLVED : 0,
                                  new VertexCoordInfo(this.vertexCoordSrid, this.vertexCoordsValid, true));
        } else {
            this.texture.release();
        }
        this.texture = null;
        this.textureCoordinates = null;
        this.vertexCoordinates = null;
        this.glTexCoordIndices = null;

        this.textureCoordsValid = false;
        this.vertexCoordsValid = false;

        if(this.state != State.UNRESOLVABLE)
            this.state = State.UNRESOLVED;

        this.receivedUpdate = false;
    }

    @Override
    public void release() {
        if (this.children != null)
            this.abandon();
        if (this.borrowingFrom != null) {
            this.borrowingFrom.unborrowTexture(this);
            this.borrowingFrom = null;
        }
        if(this == this.root) {
            for(Releasable r : this.core.releasables)
                r.release();
            this.core.progressiveLoading = false;
        } else {
            this.parent = null;
        }
        this.loadingTexCoordsVertCount = 0;

        this.fadeTimer = 0L;

        super_release();
    }

    private void super_release() {
        if (this.currentRequest != null) {
            this.currentRequest.cancel();
            this.currentRequest = null;
        }
        if (this.texture != null)
            this.releaseTexture();

        this.loadingTextureCoordinates = null;

        this.tileColumn = -1;
        this.tileRow = -1;
        this.level = -1;
        this.textureKey = null;

        this.textureCoordsValid = false;

        this.state = State.UNRESOLVED;
        this.receivedUpdate = false;

        if(this.debugDrawIndices != null) {
            Unsafe.free(this.debugDrawIndices);
            this.debugDrawIndices = null;
        }
    }

    /**
     * Allows for borrowing a portion of this node's texture.
     *
     * @param ref The node performing the borrowing. A reference to this node is stored to track
     *            whether or not the borrow is still in effect.
     * @param srcX The x-coordinate of the source region (unscaled) to be borrowed
     * @param srcY The y-coordinate of the source region (unscaled) to be borrowed
     * @param srcW The width of the source region (unscaled) to be borrowed
     * @param srcH The height of the source region (unscaled) to be borrowed
     * @param texCoords Returns the texture coordinates for the requested region of the texture.
     * @return This node's texture
     */
    protected GLTexture borrowTexture(GLQuadTileNode2 ref, long srcX, long srcY, long srcW,
            long srcH, FloatBuffer texCoords, int texGridWidth, int texGridHeight) {
        final float extentX = ((float) this.tileWidth / (float) this.texture.getTexWidth());
        final float extentY = ((float) this.tileHeight / (float) this.texture.getTexHeight());

        final float minX = Math.max(
                ((float) (srcX - this.tileSrcX - 1) / (float) this.tileSrcWidth) * extentX, 0.0f);
        final float minY = Math.max(
                ((float) (srcY - this.tileSrcY - 1) / (float) this.tileSrcHeight) * extentY, 0.0f);
        final float maxX = Math
                .min(((float) ((srcX + srcW) - this.tileSrcX + 1) / (float) this.tileSrcWidth)
                        * extentX, 1.0f);
        final float maxY = Math.min(
                ((float) ((srcY + srcH) - this.tileSrcY + 1) / (float) this.tileSrcHeight)
                        * extentY, 1.0f);

        GLTexture.createQuadMeshTexCoords( minX, minY, maxX, maxY, texGridWidth, texGridHeight,
                    texCoords );

        if (ref != null)
            this.borrowers.add(ref);

        return this.texture;
    }

    /**
     * Notifies this node that the specified node is no longer borrowing the texture.
     *
     * @param ref A node that was previously borrowing this node's texture.
     */
    protected void unborrowTexture(GLQuadTileNode2 ref) {
        this.borrowers.remove(ref);
    }

    protected GLTexture getLoadingTexture(FloatBuffer texCoords, int texGridWidth, int texGridHeight) {
        GLQuadTileNode2 scratch = this.borrowingFrom;
        if(this.core.options.textureBorrowEnabled && this.core.textureBorrowEnabled && this.shouldBorrowTexture)
        if (scratch == null) {
            GLQuadTileNode2 updatedAncestor = null;
            scratch = this.parent;
            GLTextureCache.Entry scratchTexInfo;
            while (scratch != null) {
                if (scratch.state == State.RESOLVED)
                    break;
                if (this.core.textureCache != null) {
                    scratchTexInfo = this.core.textureCache.get(scratch.getTextureKey());
                    if (scratchTexInfo != null
                            && scratchTexInfo.hasHint(TEXTURE_CACHE_HINT_RESOLVED))
                        break;
                    else if (scratchTexInfo != null && updatedAncestor == null)
                        updatedAncestor = scratch;
                }
                if (scratch.receivedUpdate && updatedAncestor == null)
                    updatedAncestor = scratch;
                scratch = scratch.parent;
            }
            if (scratch == null)
                scratch = updatedAncestor;
            // if the ancestor is neither updated or resolved, we must have
            // found its texture in the cache
            if (scratch != null && !(scratch.receivedUpdate || (scratch.state == State.RESOLVED))) {
                if (!scratch.useCachedTexture())
                    throw new IllegalStateException();
            }
        }
        if (scratch != null && scratch != this.borrowingFrom) {
            if (this.borrowingFrom != null)
                this.borrowingFrom.unborrowTexture(this);
            this.borrowingFrom = scratch;
            this.loadingTexCoordsVertCount = this.glTexGridVertCount;
            return this.borrowingFrom.borrowTexture(this, this.tileSrcX, this.tileSrcY,
                    this.tileSrcWidth, this.tileSrcHeight, texCoords, texGridWidth, texGridHeight);
        } else if (this.borrowingFrom != null) {
            if (this.loadingTexCoordsVertCount != this.glTexGridVertCount) {
                this.borrowingFrom.borrowTexture(null, this.tileSrcX, this.tileSrcY,
                        this.tileSrcWidth, this.tileSrcHeight, texCoords, texGridWidth,
                        texGridHeight);
                this.loadingTexCoordsVertCount = this.glTexGridVertCount;
            }
            return this.borrowingFrom.texture;
        } else if (this.core.loadingTextureEnabled) {
            return super_getLoadingTexture(texCoords, texGridWidth, texGridHeight);
        } else {
            return null;
        }
    }

    private GLTexture super_getLoadingTexture(FloatBuffer texCoords, int texGridWidth, int texGridHeight) {
        GLTexture retval = GLTiledMapLayer2.getLoadingTexture();

        final float x = ((float) this.tileWidth / (float) retval.getTexWidth());
        final float y = ((float) this.tileHeight / (float) retval.getTexHeight());

        GLTexture.createQuadMeshTexCoords(x, y, texGridWidth, texGridHeight, texCoords);

        return retval;
    }

    protected void validateTexture() {
        if (this.texture == null ||
                this.texture.getTexWidth() < this.tileWidth
                || this.texture.getTexHeight() < this.tileHeight ||
                this.texture.getFormat() != this.glTexFormat
                || this.texture.getType() != this.glTexType) {

            if (this.texture != null)
                this.texture.release();

            this.texture = new GLTexture(this.tileWidth, this.tileHeight, this.glTexFormat,
                    this.glTexType);

            this.textureCoordsValid = false;
            this.textureCoordinates = null;
            this.vertexCoordinates = null;

        }

        this.validateTexVerts();
    }

    protected void validateTexVerts() {
        if (!this.textureCoordsValid) {
            this.glTexGridIdxCount = GLTexture.getNumQuadMeshIndices(this.glTexGridWidth,
                    this.glTexGridHeight);
            this.glTexGridVertCount = GLTexture.getNumQuadMeshVertices(this.glTexGridWidth,
                    this.glTexGridHeight);

            final int numVerts = this.glTexGridVertCount;
            if (this.textureCoordinates == null
                    || this.textureCoordinates.capacity() < (numVerts * 2)) {
                ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(numVerts * 8);
                buf.order(ByteOrder.nativeOrder());

                this.textureCoordinates = buf.asFloatBuffer();
            }

            if (this.glTexGridVertCount > 4) {
                if (this.glTexCoordIndices == null
                        || this.glTexCoordIndices.capacity() < (this.glTexGridIdxCount * 2)) {
                    this.glTexCoordIndices = com.atakmap.lang.Unsafe.allocateDirect(this.glTexGridIdxCount * 2);
                    this.glTexCoordIndices.order(ByteOrder.nativeOrder());
                }
            } else {
                this.glTexCoordIndices = null;
            }

            if (this.vertexCoordinates == null
                    || this.vertexCoordinates.capacity() < (numVerts * 3)) {

                ByteBuffer buf;

                buf = com.atakmap.lang.Unsafe.allocateDirect(numVerts * 12);
                buf.order(ByteOrder.nativeOrder());
                this.vertexCoordinates = buf.asFloatBuffer();
            }

            final float x = ((float) this.tileWidth / (float) this.texture.getTexWidth());
            final float y = ((float) this.tileHeight / (float) this.texture.getTexHeight());

            this.textureCoordinates.clear();
            GLTexture.createQuadMeshTexCoords(new PointF(0.0f, 0.0f),
                                              new PointF(x, 0.0f),
                                              new PointF(x, y),
                                              new PointF(0.0f, y),
                                              this.glTexGridWidth,
                                              this.glTexGridHeight,
                                              this.textureCoordinates);
            this.textureCoordinates.flip();

            if (this.glTexGridVertCount > 4) {
                GLTexture.createQuadMeshIndexBuffer(this.glTexGridWidth,
                        this.glTexGridHeight,
                        this.glTexCoordIndices.asShortBuffer());
            }

            if (glTexCoordIndices == null) {
                this.debugDrawIndices = GLWireFrame.deriveIndices(GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        this.glTexGridVertCount, GLES30.GL_UNSIGNED_SHORT);
            } else {
                this.debugDrawIndices = GLWireFrame.deriveIndices(glTexCoordIndices.asShortBuffer(), GLES30.GL_TRIANGLE_STRIP, glTexGridIdxCount, GLES30.GL_UNSIGNED_SHORT);
            }

        }
        this.textureCoordsValid = true;
    }

    protected void validateVertexCoords(GLMapView view) {
        if(!this.vertexCoordsValid || (this.vertexCoordSrid != view.drawSrid))
        {
            if(this.vertexCoordSrid != view.drawSrid)
                view.scene.mapProjection.forward(this.centroid, this.centroidProj);

            final long vertexCoordinatesPtr = Unsafe.getBufferPointer(this.vertexCoordinates);

            // recompute vertex coordinates as necessary
            this.core.vertexResolver.beginNode(this);
            try {
                int idx = 0;
                for (int i = 0; i <= this.glTexGridHeight; i++) {
                    for (int j = 0; j <= this.glTexGridWidth; j++) {
                        final int gridVertsIdx = (i*(this.glTexGridWidth+1))+j;
                        if(this.gridVertices[gridVertsIdx] == null)
                            this.gridVertices[gridVertsIdx] = new GridVertex();
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

            this.vertexCoordsValid = true;

            this.vertexCoordSrid = view.drawSrid;
            this.vertsDrawVersion = view.drawVersion;

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
                360d / (1L<<OSMUtils.mapnikTileLevel(view.drawMapResolution)));

        if (numRois < 1) // no intersection
            return;

        final double scale = (this.core.gsd / view.drawMapResolution);

        // XXX - tune level calculation -- it may look better to swap to the
        // next level before we actually cross the threshold
        final int level = (int) Math.ceil(Math.max((Math.log(1.0d / scale) / Math.log(2.0)) + this.core.options.levelTransitionAdjustment, 0d));
        this.core.drawPumpLevel = level;

        if (view.targeting && this.hasPreciseCoordinates())
            this.core.magFilter = GLES20FixedPipeline.GL_NEAREST;
        else
            this.core.magFilter = GLES20FixedPipeline.GL_LINEAR;

        long poiX = 0L;
        long poiY = 0L;

        double drawLng = view.drawLng;
        if (view.continuousScrollEnabled && !view.crossesIDL &&
                (this.core.unwrap > 0 && drawLng > 0
                        || this.core.unwrap < 0 && drawLng < 0)) {
            drawLng -= this.core.unwrap;
        } else if(view.crossesIDL && numRois > 1 && view.drawLng < 0d) {
            // ROI -- East is always first
            drawLng = view.drawLng + 360d;
        }

        view.scratch.geo.set(view.drawLat, drawLng);
        view.scratch.geo.set(GeoPoint.UNKNOWN);
        if(this.core.imprecise.groundToImage(view.scratch.geo, view.scratch.pointD)) {
            poiX = (long)view.scratch.pointD.x;
            poiY = (long)view.scratch.pointD.y;
        }

        try {
            if(this.core.tileReader != null)
                this.core.tileReader.start();
            this.core.vertexResolver.beginDraw(view);
            try {
                for(int i = 0; i < numRois; i++) {
                    // ROIs are always E hemi, W hemi

                    if(i == 0)
                        this.core.drawPumpHemi = GLAntiMeridianHelper.HEMISPHERE_EAST;
                    else if(i == 1)
                        this.core.drawPumpHemi = GLAntiMeridianHelper.HEMISPHERE_WEST;
                    else
                        throw new IllegalStateException();

                    RectD roi = this.core.drawROI[i];
                    this.draw(
                            view,
                            Math.min(level, core.tileReader.getMaxNumResolutionLevels() - 1),
                            (long) roi.left,
                            (long) roi.top,
                            (long) (Math.ceil(roi.right) - (long) roi.left),
                            (long) (Math.ceil(roi.bottom) - (long) roi.top),
                            poiX,
                            poiY,
                            ((i+1) == numRois));

                    // ROIS -- West is always second, reset the POI
                    if(view.drawLng > 0d)
                        drawLng = view.drawLng-360d;
                    else
                        drawLng = view.drawLng;
                    view.scratch.geo.set(view.drawLat, drawLng);
                    view.scratch.geo.set(GeoPoint.UNKNOWN);
                    if(this.core.imprecise.groundToImage(view.scratch.geo, view.scratch.pointD)) {
                        poiX = (long)view.scratch.pointD.x;
                        poiY = (long)view.scratch.pointD.y;
                    }
                }
            } finally {
                this.core.vertexResolver.endDraw(view);
            }
        } finally {
            if(this.core.tileReader != null)
                this.core.tileReader.stop();
        }

        //Log.v(TAG, "Tiles this frame: " + core.tilesThisFrame);
    }

    protected boolean shouldResolve() {
        boolean resolveUnresolvable = false;
        for (int i = 0; i < this.children.length; i++)
            resolveUnresolvable |= (this.children[i] != null);

        return (this.state == State.UNRESOLVABLE && resolveUnresolvable) || super_shouldResolve();
    }

    private boolean super_shouldResolve() {
        return (this.state == State.UNRESOLVED) && (this.currentRequest == null);
    }

    protected boolean useCachedTexture() {
        if(this.core.textureCache == null)
            return false;

        GLTextureCache.Entry cachedTexture = this.core.textureCache.remove(this.getTextureKey());
        if (cachedTexture == null)
            return false;
        if (this.texture != null)
            this.texture.release();
        this.texture = cachedTexture.texture;
        this.glTexFormat = this.texture.getFormat();
        this.glTexType = this.texture.getType();
        this.receivedUpdate = true;
        if (cachedTexture.hasHint(TEXTURE_CACHE_HINT_RESOLVED)) {
            this.state = State.RESOLVED;
            this.resetFadeTimer();
        }

        this.textureCoordsValid = false;

        this.textureCoordinates = (FloatBuffer) cachedTexture.textureCoordinates;
        this.vertexCoordinates = (FloatBuffer) cachedTexture.vertexCoordinates;
        this.glTexCoordIndices = (ByteBuffer) cachedTexture.indices;
        this.glTexGridIdxCount = cachedTexture.numIndices;
        this.glTexGridVertCount = cachedTexture.numVertices;
        // XXX -
        this.glTexGridWidth = (int) Math.sqrt(this.glTexGridVertCount) - 1;
        this.glTexGridHeight = (int) Math.sqrt(this.glTexGridVertCount) - 1;

        this.gridVertices = new GridVertex[(this.glTexGridWidth+1)*(this.glTexGridHeight+1)];

        this.vertexCoordSrid = -1;
        this.vertexCoordsValid = false;

        return true;
    }

    protected void resolveTexture() {
        // we only want to borrow if the texture is not yet resolved. if the
        // texture is already resolved, don't borrow -- just replace when the
        // latest version is loaded
        this.shouldBorrowTexture = (this.state != State.RESOLVED);

        // copy data from the children to our texture
        if(this.state != State.RESOLVED &&
           this.core.options.textureCopyEnabled &&
           GLMapSurface.SETTING_enableTextureTargetFBO && this.core.textureCopyEnabled &&
           !GLQuadTileNode3.offscreenFboFailed) {

            boolean hasChildData = false;
            boolean willBeResolved = true;
            for (int i = 0; i < 4; i++) {
                willBeResolved &= (this.children[i] != null && (this.children[i].state == State.RESOLVED));
                if (this.children[i] != null
                        && ((this.children[i].state == State.RESOLVED) || this.children[i].receivedUpdate)) {
                    hasChildData = true;
                }
            }

            if (hasChildData) {
                // XXX - luminance is not renderable for FBO
                if (!willBeResolved) {
                    this.glTexFormat = GLES20FixedPipeline.GL_RGBA;
                    this.glTexType = GLES20FixedPipeline.GL_UNSIGNED_BYTE;
                } else {
                    if (this.glTexFormat == GLES20FixedPipeline.GL_LUMINANCE)
                        this.glTexFormat = GLES20FixedPipeline.GL_RGB;
                    else if (this.glTexFormat == GLES20FixedPipeline.GL_LUMINANCE_ALPHA)
                        this.glTexFormat = GLES20FixedPipeline.GL_RGBA;
                }

                this.validateTexture();
                this.texture.init();

                int parts = 0;
                int[] currentFrameBuffer = new int[1];
                int[] frameBuffer = this.core.frameBufferHandle;
                int[] depthBuffer = this.core.depthBufferHandle;

                GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_FRAMEBUFFER_BINDING, currentFrameBuffer, 0);

                boolean fboCreated = false;
                do {
                    if (frameBuffer[0] == 0)
                        GLES20FixedPipeline.glGenFramebuffers(1, frameBuffer, 0);

                    if (depthBuffer[0] == 0)
                        GLES20FixedPipeline.glGenRenderbuffers(1, depthBuffer, 0);

                    GLES20FixedPipeline.glBindRenderbuffer(GLES20FixedPipeline.GL_RENDERBUFFER,
                            depthBuffer[0]);
                    GLES20FixedPipeline.glRenderbufferStorage(GLES20FixedPipeline.GL_RENDERBUFFER,
                            GLES20FixedPipeline.GL_DEPTH_COMPONENT16, this.texture.getTexWidth(),
                            this.texture.getTexHeight());
                    GLES20FixedPipeline.glBindRenderbuffer(GLES20FixedPipeline.GL_RENDERBUFFER, 0);

                    GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                            frameBuffer[0]);

                    // clear any pending errors
                    while(GLES20FixedPipeline.glGetError() != GLES20FixedPipeline.GL_NO_ERROR)
                        ;
                    GLES20FixedPipeline.glFramebufferTexture2D(GLES20FixedPipeline.GL_FRAMEBUFFER,
                            GLES20FixedPipeline.GL_COLOR_ATTACHMENT0,
                            GLES20FixedPipeline.GL_TEXTURE_2D, this.texture.getTexId(), 0);

                    // XXX - observing hard crash following bind of "complete"
                    //       FBO on SM-T230NU. reported error is 1280 (invalid
                    //       enum) on glFramebufferTexture2D. I have tried using
                    //       the color-renderable formats required by GLES 2.0
                    //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
                    //       the same outcome.
                    if(GLES20FixedPipeline.glGetError() != GLES20FixedPipeline.GL_NO_ERROR)
                        break;

                    GLES20FixedPipeline.glFramebufferRenderbuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                            GLES20FixedPipeline.GL_DEPTH_ATTACHMENT, GLES20FixedPipeline.GL_RENDERBUFFER,
                            depthBuffer[0]);
                    final int fboStatus = GLES20FixedPipeline.glCheckFramebufferStatus(GLES20FixedPipeline.GL_FRAMEBUFFER);
                    fboCreated = (fboStatus == GLES20FixedPipeline.GL_FRAMEBUFFER_COMPLETE);
                } while(false);

                if (fboCreated) {
                    FloatBuffer texCoordBuffer = this.textureCoordinates;

                    GLES20FixedPipeline.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT);

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
                            // ll
                            texCoordBuffer.put(0, 0f/childTexWidth);
                            texCoordBuffer.put(1, 0f/childTexHeight);
                            this.children[i].vertexCoordinates.put(0, partX);
                            this.children[i].vertexCoordinates.put(1, partY);
                            // lr
                            texCoordBuffer.put(2, (float) this.children[i].tileWidth
                                    / childTexWidth);
                            texCoordBuffer.put(3, 0f/childTexHeight);
                            this.children[i].vertexCoordinates.put(2, partX + partWidth);
                            this.children[i].vertexCoordinates.put(3, partY);
                            // ur
                            texCoordBuffer.put(4, (float) this.children[i].tileWidth
                                    / childTexWidth);
                            texCoordBuffer.put(5, (float) this.children[i].tileHeight
                                    / childTexHeight);
                            this.children[i].vertexCoordinates.put(4, partX + partWidth);
                            this.children[i].vertexCoordinates.put(5, partY + partHeight);
                            // ul
                            texCoordBuffer.put(6, 0f/childTexWidth);
                            texCoordBuffer.put(7, (float) this.children[i].tileHeight
                                    / childTexHeight);
                            this.children[i].vertexCoordinates.put(6, partX);
                            this.children[i].vertexCoordinates.put(7, partY + partHeight);

                            this.children[i].texture.draw(4, GLES20FixedPipeline.GL_FLOAT,
                                    this.textureCoordinates, this.children[i].vertexCoordinates);

                            // the child's vertex coordinates are now invalid
                            this.children[i].vertexCoordsValid = false;

                            // if the child is resolved, increment parts
                            if (this.children[i].state == State.RESOLVED)
                                parts++;
                        }
                    }

                    GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER, currentFrameBuffer[0]);

                    this.textureCoordsValid = false;
                    this.receivedUpdate = true;

                    this.validateTexVerts();

                    if(!willBeResolved) {
                        this.texture.setMagFilter(GLES20FixedPipeline.GL_NEAREST);
                        this.texture.getTexId();
                    }
                } else {
                    Log.w(TAG, "Failed to create FBO for texture copy.");
                    GLQuadTileNode3.offscreenFboFailed = true;
                }

                final boolean wasUnresolvable = (this.state == State.UNRESOLVABLE);

                // mark resolved if all 4 children were resolved
                if (parts == 4) {
                    if(this.core.options.childTextureCopyResolvesParent)
                        this.state = State.RESOLVED;
                    else if(this.state != State.UNRESOLVABLE)
                        this.state = State.UNRESOLVED;
                    // the tile is completely composited, but the client
                    // indicated that the tile should be refreshed
                    this.shouldBorrowTexture = false;
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
                    this.texture.setMinFilter(GLES20FixedPipeline.GL_NEAREST);
            }

            if ((this.state == State.RESOLVED) && this.borrowingFrom != null) {
                this.borrowingFrom.unborrowTexture(this);
                this.borrowingFrom = null;
            }
        } else if(this.state == State.RESOLVED) {
            // a refresh has been requested, move back into the unresolved state
            // to reload the tile
            this.state = State.UNRESOLVED;
        }

        // abandon the children before drawing ourself
        this.abandon();

        if (this.state == State.UNRESOLVED) {
            super_resolveTexture();
        }
    }

    private void resetFadeTimer() {
        float levelScale = 1f - (float)(this.level-this.core.drawPumpLevel) / (float)(this.root.level-this.core.drawPumpLevel);
        if(this.borrowingFrom != null) {
             this.fadeTimer = Math.max((long)(this.core.fadeTimerLimit * levelScale) - this.readRequestElapsed, 0L);
        } else {
            this.fadeTimer = 0L;
        }
    }

    private void super_resolveTexture() {
        this.state = State.RESOLVING;
        this.readRequestStart = System.currentTimeMillis();
        this.core.tileReader.asyncRead(this.level, this.tileColumn, this.tileRow, this);
    }

    /**
     * @param view The view
     * @param level The resolution level. The scale factor is equivalent to
     *            <code>1.0d / (double)(1<<level)</code>
     * @param srcX The x-coordinate of the source region to be rendered (unscaled)
     * @param srcY The y-coordinate of the source region to be rendered (unscaled)
     * @param srcW The width of the source region to be rendered (unscaled)
     * @param srcH The height of the source region to be rendered (unscaled)
     */
    private void draw(GLMapView view, int level, long srcX, long srcY, long srcW, long srcH, long poiX, long poiY, boolean cull) {
        this.view = view;

        // dynamically refine level based on expected nominal resolution for tile as rendered
        final double tileGsd = GLMapView.estimateResolution(view, maxLat, minLng, minLat, maxLng, null);
        final double scale = (this.core.gsd / tileGsd);
        final int computed = (int) Math.ceil(Math.max((Math.log(1.0d / scale) / Math.log(2.0)) + this.core.options.levelTransitionAdjustment, 0d));
        if (computed > level) {
            level = (computed > this.level) ? this.level : level;
        }

        if (this.level == level) {
            final boolean abandon = (this.texture == null);
            this.drawImpl(view);
            if (abandon && (this.texture != null))
                this.abandon();
        } else if (this.level > level) {
            boolean unresolvedChildren = false;
            if(this.core.tileReader.isMultiResolution()) {
                for (int i = 0; i < this.children.length; i++)
                    unresolvedChildren |= (this.children[i] != null && this.children[i].state == State.UNRESOLVABLE);

                if (unresolvedChildren
                        && (this.state == State.UNRESOLVED || (this.state == State.UNRESOLVABLE && this.derivedUnresolvableData))) {

                    this.useCachedTexture();
                    if (this.state == State.UNRESOLVED) {
                        this.validateTexture();
                        super_resolveTexture();
                    }
                }

                if(unresolvedChildren && ((this.state == State.RESOLVED) || this.receivedUpdate)) {
                    // force child to re-borrow if this node has data and the
                    // node that the child is borrowing from is lower res
                    for (int i = 0; i < this.children.length; i++) {
                        if(this.children[i] != null && this.children[i].state != State.RESOLVED) {
                            if(this.children[i].borrowingFrom != null && this.children[i].borrowingFrom.level > this.level) {
                                this.children[i].borrowingFrom.unborrowTexture(this.children[i]);
                                this.children[i].borrowingFrom = null;
                            }
                        }
                    }
                }
            }

            // when progressive load is enabled, only allow children to draw
            // once parent data for every other level has been rendered
            if(this.core.options.progressiveLoad &&
               (this.level%3) == 0 &&
               (!this.touched ||
                this.fadeTimer > 0L)) {

                this.core.progressiveLoading = true;
                this.drawImpl(view);
                return;
            } else {
                this.core.progressiveLoading = false;
            }

            final long maxSrcX = (srcX + srcW) - 1;
            final long maxSrcY = (srcY + srcH) - 1;

            final long tileMidSrcX = (this.tileSrcX + ((long)this.halfTileWidth << this.level));
            final long tileMidSrcY = (this.tileSrcY + ((long)this.halfTileHeight << this.level));
            final long tileMaxSrcX = (this.tileSrcX + this.tileSrcWidth) - 1;
            final long tileMaxSrcY = (this.tileSrcY + this.tileSrcHeight) - 1;

            // XXX - quickfix to get some more tiles into view when zoomed out
            //       in globe mode. selection should be done via intersection
            //       with frustum or global camera distance dynamic level
            //       selection

            final long limitX = this.core.tileReader.getWidth()-1;
            final long limitY = this.core.tileReader.getHeight()-1;

            final boolean left = (srcX < Math.min(tileMidSrcX, limitX)) && (maxSrcX > Math.max(this.tileSrcX, 0));
            final boolean upper = (srcY < Math.min(tileMidSrcY, limitY)) && (maxSrcY > Math.max(this.tileSrcY, 0));
            final boolean right = (srcX < Math.min(tileMaxSrcX, limitX)) && (maxSrcX > Math.max(tileMidSrcX, 0));
            final boolean lower = (srcY < Math.min(tileMaxSrcY, limitY)) && (maxSrcY > Math.max(tileMidSrcY, 0));

            // orphan all children that are not visible
            Iterator<GLQuadTileNode2> orphanIter;
            if(cull) {
                Set<GLQuadTileNode2> orphans = this.orphan(view.drawVersion,
                                                           !(upper && left),
                                                           !(upper & right),
                                                           !(lower && left),
                                                           !(lower && right));

                orphanIter = orphans.iterator();
            } else {
                orphanIter = Collections2.<GLQuadTileNode2>emptyIterator();
            }

            final boolean[] visibleChildren = new boolean[]{
                    (upper && left),
                    (upper && right),
                    (lower && left),
                    (lower && right),
            };

            int visCount = 0;
            for (int i = 0; i < this.children.length; i++) {
                if (visibleChildren[i]) {
                    if(this.children[i] == null) {
                        if (orphanIter.hasNext())
                            this.adopt(i, orphanIter.next());
                        else
                            this.children[i] = this.createChild(i);
                    }
                    // XXX - overdraw above is emitting 0 dimension children
                    //       causing subsequent FBO failure. implementing
                    //       quickfix to exclude empty children
                    visibleChildren[i] &= (this.children[i].tileWidth > 0 && this.children[i].tileHeight > 0);
                    if(!visibleChildren[i])
                        continue;

                    this.children[i].lastTouch = view.drawVersion;
                    visCount++;
                }
            }

            // release any unused orphans
            while (orphanIter.hasNext())
                orphanIter.next().release();

            // there are no visible children. this node must have been selected
            // for overdraw -- draw it and return
            if(visCount == 0) {
                this.isOverdraw = true;
                this.drawImpl(view);
                return;
            } else if(!cull) {
                // if this is not the 'cull' pass and we have not performed
                // overdraw
                this.isOverdraw = false;
            }

            // if there are no unresolved children and this node is requesting
            // tile data, cancel request
            if (!unresolvedChildren &&
                this.currentRequest != null &&
                (cull && !this.isOverdraw)) {

                this.currentRequest.cancel();
                this.currentRequest = null;
            }

            int iterOffset = 2;
            if(poiX > 0L || poiY > 0L) {
                // XXX - I think this should happen implicitly based on the
                //       POI vector, but it doesn't appear to???
                if(Rectangle.contains(this.tileSrcX,
                                      this.tileSrcY,
                                      this.tileSrcX+this.tileSrcWidth,
                                      this.tileSrcY+this.tileSrcHeight,
                                      poiX, poiY)) {

                    for (int i = 0; i < this.children.length; i++) {
                        if (visibleChildren[i] &&
                            Rectangle.contains(this.children[i].tileSrcX,
                                               this.children[i].tileSrcY,
                                               this.children[i].tileSrcX+this.children[i].tileSrcWidth,
                                               this.children[i].tileSrcY+this.children[i].tileSrcHeight,
                                               poiX, poiY)) {

                            this.children[i].verticesInvalid = this.verticesInvalid;
                            this.children[i].draw(view, level, srcX, srcY, srcW, srcH, poiX, poiY, cull);

                            // already drawn
                            visibleChildren[i] = false;

                            break;
                        }
                    }
                }

                // obtain angle between tile center and POI
                final long dx = poiX-tileMidSrcX;
                final long dy = poiY-tileMidSrcY;
                double theta = Math.toDegrees(Math.atan2(dy, dx));
                if(theta < 0d)
                    theta += 360d;

                // determine the half quadrant that the vector the POI extends
                // through
                int halfQuadrant = (int)(theta/45d) % 8;
                if(halfQuadrant >= 0 && halfQuadrant <= 7)
                    iterOffset = halfQuadrant;
            }

            for (int j = 0; j < this.children.length; j++) {
                final int i = GLQuadTileNode3.POI_ITERATION_BIAS[(iterOffset*4)+j];
                if (visibleChildren[i]) {
                    this.children[i].verticesInvalid = this.verticesInvalid;
                    this.children[i].draw(view, level, srcX, srcY, srcW, srcH, poiX, poiY, cull);
                    visibleChildren[i] = false;
                }
            }

            // if no one is borrowing from us, release our texture
            if (this.borrowers.isEmpty() && this.texture != null && !unresolvedChildren && (cull && !this.isOverdraw))
                this.releaseTexture();
        } else {
            throw new IllegalStateException();
        }

        if (this.parent == null) {
            if (this.core.frameBufferHandle[0] != 0) {
                GLES20FixedPipeline.glDeleteFramebuffers(1, this.core.frameBufferHandle, 0);
                this.core.frameBufferHandle[0] = 0;
            }
            if (this.core.depthBufferHandle[0] != 0) {
                GLES20FixedPipeline.glDeleteRenderbuffers(1, this.core.depthBufferHandle, 0);
                this.core.depthBufferHandle[0] = 0;
            }
        }

        this.verticesInvalid = false;
    }

    protected void drawTexture(GLMapView view, GLTexture tex, Buffer texCoords) {
        if (tex == this.texture) {
            if (this.texture.getMinFilter() != this.core.minFilter)
                this.texture.setMinFilter(this.core.minFilter);
            if (this.texture.getMagFilter() != this.core.magFilter)
                this.texture.setMagFilter(this.core.magFilter);
        }
        super_drawTexture(view, tex, texCoords);
    }

    private void setLCS(GLMapView view) {
        view.scratch.matrix.set(view.scene.forward);
        if(this.core.unwrap == 0d) {
            if (!view.crossesIDL || this.core.drawPumpHemi == view.idlHelper.getPrimaryHemisphere())
                view.scratch.matrix.translate(centroidProj.x, centroidProj.y, centroidProj.z);
            else
                view.scratch.matrix.translate(centroidProjHemi2.x, centroidProjHemi2.y, centroidProjHemi2.z);
        } else {
            if(core.unwrap < 0d || primaryHemi == GLAntiMeridianHelper.HEMISPHERE_WEST)
                view.scratch.matrix.translate(centroidProj.x, centroidProj.y, centroidProj.z);
            else if (core.unwrap > 0d || primaryHemi == GLAntiMeridianHelper.HEMISPHERE_EAST)
                view.scratch.matrix.translate(centroidProjHemi2.x, centroidProjHemi2.y, centroidProjHemi2.z);
        }
        view.scratch.matrix.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
        GLES20FixedPipeline.glLoadMatrixf(view.scratch.matrixF, 0);
    }
    static native void setLCS(long mvpPtr, int uMvp, double tx, double ty, double tz);

    private void super_drawTexture(GLMapView view, GLTexture tex, Buffer texCoords) {
        GLES20FixedPipeline.glPushMatrix( );
        setLCS(view);

        float fade = (tex == this.texture && this.core.fadeTimerLimit > 0L) ?
                ((float)(this.core.fadeTimerLimit
                                                -this.fadeTimer) /
                        (float)this.core.fadeTimerLimit) :
                1f;

        if( glTexCoordIndices == null )
        {
            GLTexture.draw( tex.getTexId( ),
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        this.glTexGridVertCount,
                        2, GLES20FixedPipeline.GL_FLOAT, texCoords,
                        3, GLES20FixedPipeline.GL_FLOAT, this.vertexCoordinates,
                        this.core.colorR,
                        this.core.colorG,
                        this.core.colorB,
                        fade * this.core.colorA );
        }
        else
        {
            GLTexture.draw( tex.getTexId( ),
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        this.glTexGridIdxCount,
                        2, GLES20FixedPipeline.GL_FLOAT, texCoords,
                        3, GLES20FixedPipeline.GL_FLOAT, this.vertexCoordinates,
                        GLES20FixedPipeline.GL_UNSIGNED_SHORT, this.glTexCoordIndices,
                        this.core.colorR,
                        this.core.colorG,
                        this.core.colorB,
                        fade * this.core.colorA );
        }
        GLES20FixedPipeline.glPopMatrix( );

        core.tilesThisFrame++;
    }

    protected String getTextureKey( )
    {
        if( textureKey == null )
        {
            textureKey = core.uri + "," + level + "," + tileColumn + "," + tileRow;
        }
        return textureKey;
    }

    protected void invalidateVertexCoords() {
        this.verticesInvalid = true;
    }

    protected void expandTexGrid() {
        this.glTexGridWidth *= 2;
        this.glTexGridHeight *= 2;

        this.gridVertices = new GridVertex[(this.glTexGridWidth+1)*(this.glTexGridHeight+1)];

        this.textureCoordsValid = false;
    }

    private void super_invalidateVertexCoords() {
        this.vertexCoordsValid = false;
    }

    /**
     * Invokes {@link #super_draw(GLMapView)}.
     *
     * @param view The view
     */
    protected final void drawImpl(GLMapView view) {
        this.vertexCoordsValid &= !this.verticesInvalid;

        // clear cached mesh vertices
        if(this.verticesInvalid)
            Arrays.fill(this.gridVertices, null);

        super_draw(view);
        if(this.state == State.RESOLVED && this.fadeTimer == 0L) {
            this.shouldBorrowTexture = false;
            if (this.borrowingFrom != null) {
                this.borrowingFrom.unborrowTexture(this);
                this.borrowingFrom = null;
            }
        } else if(this.state == State.RESOLVED){
            this.fadeTimer = Math.max(
                    this.fadeTimer-view.animationDelta, 0L);
        }
    }

    private void super_draw(GLMapView view) {
        this.view = view;

        if (this.core.textureCache != null && !((this.state == State.RESOLVED) || this.receivedUpdate))
            this.useCachedTexture();

        this.validateTexture();

        // check the tiles version and move back into the UNRESOLVED state if
        // the tile version has changed
        if((this.state == State.UNRESOLVABLE || this.state == State.RESOLVED) && core.versionCheckEnabled && this.tileVersion != this.core.tileReader.getTileVersion(this.level, this.tileColumn, this.tileRow)) {
            if(this.state == State.UNRESOLVABLE)
                this.state = State.UNRESOLVED;
            this.resolveTexture();
        }

        // read the data if we don't have it yet
        else if (this.shouldResolve())
            this.resolveTexture();

        if(!touched && (this.state == State.RESOLVED || this.state == State.UNRESOLVABLE)) {
            touched = true;
            if(this.core.progressiveLoading)
                view.requestRefresh();
        }

        if (this.state != State.RESOLVED || this.fadeTimer > 0L) {
            if (this.loadingTextureCoordinates == null
                    || (this.loadingTextureCoordinates.capacity() < (this.glTexGridVertCount * 8))) {
                this.loadingTextureCoordinates = com.atakmap.lang.Unsafe
                        .allocateDirect(this.glTexGridVertCount * 8);
                this.loadingTextureCoordinates.order(ByteOrder.nativeOrder());
            }
            GLTexture loadingTexture = this.getLoadingTexture(
                    this.loadingTextureCoordinates.asFloatBuffer(), this.glTexGridWidth,
                    this.glTexGridHeight);

            if (loadingTexture != null) {
                this.validateVertexCoords(view);

                this.drawTexture(view, loadingTexture, this.loadingTextureCoordinates);
            }
        } else {
            this.loadingTextureCoordinates = null;
        }
        if (this.receivedUpdate) {
            this.validateVertexCoords(view);

            this.drawTexture(view, this.texture, this.textureCoordinates);
        }

        if(this.core.debugDrawEnabled)
            this.debugDraw(view);
    }

    private void debugDraw(GLMapView view) {
        this.validateVertexCoords(view);

        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glColor4f(1.0f, 1.0f, 0.0f, 1.0f);
        GLES20FixedPipeline.glLineWidth(2.0f);
        GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, this.vertexCoordinates);

        GLES20FixedPipeline.glPushMatrix();
        this.setLCS(view);

        if(this.debugDrawIndices != null) {
            GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINES,
                    this.debugDrawIndices.limit(),
                    GLES20FixedPipeline.GL_UNSIGNED_SHORT,
                    this.debugDrawIndices);
        }

        GLES20FixedPipeline.glColor4f(0.0f, 1.0f, 1.0f, 1.0f);

        final ByteBuffer dbg = com.atakmap.lang.Unsafe.allocateDirect(8);
        dbg.order(ByteOrder.nativeOrder());

        dbg.putShort((short)0);
        dbg.putShort((short)this.glTexGridWidth);
        dbg.putShort((short)(((this.glTexGridHeight + 1) * (this.glTexGridWidth + 1)) - 1));
        dbg.putShort((short)((this.glTexGridHeight * (this.glTexGridWidth + 1))));
        dbg.flip();

        GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINE_LOOP, 4, GLES20FixedPipeline.GL_UNSIGNED_SHORT, dbg);

        GLES20FixedPipeline.glPopMatrix();

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

         com.atakmap.opengl.GLText _titleText = com.atakmap.opengl.GLText.getInstance(com.atakmap.map.AtakMapView.getDefaultTextFormat());
         view.scratch.pointD.x = this.tileSrcX+this.tileSrcWidth/2f;
         view.scratch.pointD.y = this.tileSrcY+this.tileSrcHeight/2f;
         this.core.imprecise.imageToGround(view.scratch.pointD, view.scratch.geo);
         view.forward(view.scratch.geo, view.scratch.pointF);
         GLES20FixedPipeline.glPushMatrix();
         GLES20FixedPipeline.glTranslatef(view.scratch.pointF.x, view.scratch.pointF.y, 0);
         _titleText.draw(this.tileColumn + "," + this.tileRow + "," + this.level + " " + this.state.name(), 0.0f, 0.0f, 1.0f, 1.0f);
         GLES20FixedPipeline.glPopMatrix();
         Unsafe.free(dbg);
    }

    protected GLQuadTileNode2 createChild(int idx) {
        return new GLQuadTileNode2(this, idx);
    }

    /**
     * Adopts the specified child
     *
     * @param idx The {@link #children} index to adopt the child at
     * @param child The child to adopt
     */
    private void adopt(int idx, GLQuadTileNode2 child) {
        if (this.children[idx] != null)
            throw new IllegalStateException();
        this.children[idx] = child;
        this.children[idx].set((this.tileColumn * 2) + (idx % 2), (this.tileRow * 2) + (idx / 2),
                this.level - 1);
        this.children[idx].parent = this;
    }

    /**
     * Orphans the specified children.
     *
     * @param upperLeft <code>true</code> to orphan the upper-left child
     * @param upperRight <code>true</code> to orphan the upper-right child
     * @param lowerLeft <code>true</code> to orphan the lower-left child
     * @param lowerRight <code>true</code> to orphan the lower-right child
     * @return The orphaned children
     */
    private Set<GLQuadTileNode2> orphan(long drawVersion, boolean upperLeft, boolean upperRight,
            boolean lowerLeft, boolean lowerRight) {
        Set<GLQuadTileNode2> retval = new TreeSet<GLQuadTileNode2>(ORPHANING_COMPARATOR);

        final boolean[] shouldOrphan = new boolean[]{
                upperLeft,
                upperRight,
                lowerLeft,
                lowerRight,
        };

        for (int i = 0; i < this.children.length; i++) {
            if (shouldOrphan[i] && this.children[i] != null && this.children[i].lastTouch != drawVersion) {
                retval.add(this.children[i]);
                this.children[i].parent = null;
                this.children[i] = null;
            }
        }
        return retval;
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
        final int mask = this.recurseState();

        // if anything in the hierarchy is resolving, return resolving
        if(MathUtils.hasBits(mask, GLQuadTileNode3.STATE_RESOLVING) || this.core.progressiveLoading)
            return State.RESOLVING;
        return this.getStateImpl();
    }

    private State getStateImpl() {
        State retval = null;
        State childState;
        for (int i = 0; i < this.children.length; i++) {
            if (this.children[i] == null)
                continue;
            childState = this.children[i].getState();
            if (childState == State.UNRESOLVABLE || childState == State.SUSPENDED)
                return childState;
            else if (retval == null || childState != State.RESOLVED)
                retval = childState;
        }
        if (retval == null)
            retval = this.state;
        return retval;
    }

    private int recurseState() {
        int retval = 0;
        switch(this.state) {
            case UNRESOLVED :
                retval |= GLQuadTileNode3.STATE_UNRESOLVED;
                break;
            case RESOLVING :
                retval |= GLQuadTileNode3.STATE_RESOLVING;
                break;
            case RESOLVED :
                if(this.fadeTimer > 0L)
                    retval |= GLQuadTileNode3.STATE_RESOLVING;
                else
                    retval |= GLQuadTileNode3.STATE_RESOLVED;
                break;
            case UNRESOLVABLE :
                retval |= GLQuadTileNode3.STATE_UNRESOLVABLE;
                break;
            case SUSPENDED :
                retval |= GLQuadTileNode3.STATE_SUSPENDED;
                break;
            default :
                throw new IllegalStateException();
        }

        for(int i = 0; i < this.children.length; i++) {
            if(this.children[i] != null)
                retval |= this.children[i].recurseState();
        }
        return retval;
    }

    private State super_getState() {
        return this.state;
    }

    @Override
    public void suspend() {
        for (int i = 0; i < this.children.length; i++)
            if (this.children[i] != null)
                this.children[i].suspend();
        super_suspend();
    }

    private void super_suspend() {
        if (this.state == State.RESOLVING && this.currentRequest != null) {
            this.currentRequest.cancel();
            this.state = State.SUSPENDED;
        }
    }

    @Override
    public void resume() {
        for (int i = 0; i < this.children.length; i++)
            if (this.children[i] != null)
                this.children[i].resume();
        super_resume();
    }

    private void super_resume() {
        // move us back into the unresolved from suspended to re-enable texture
        // loading
        if (this.state == State.SUSPENDED)
            this.state = State.UNRESOLVED;
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

        final int transferSize = GdalGraphicUtils.getBufferSize(glTexFormat, glTexType, dstW, dstH);
        ByteBuffer transferBuffer = transferBuffer = GLQuadTileNode3.transferBuffers.get();
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
                    GLQuadTileNode2.this.texture.load(buf, dstX, dstY, dstW, dstH);
                    GLQuadTileNode2.this.receivedUpdate = true;
                } finally {
                    if(!GLQuadTileNode3.transferBuffers.put(buf))
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
                if (!GLQuadTileNode2.this.checkRequest(id))
                    return;
                GLQuadTileNode2.this.readRequestComplete = System.currentTimeMillis();
                GLQuadTileNode2.this.readRequestElapsed = (readRequestComplete-readRequestStart);

                GLQuadTileNode2.this.state = State.RESOLVED;
                GLQuadTileNode2.this.resetFadeTimer();

                if(GLQuadTileNode3.mipmapEnabled) {
                    GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, GLQuadTileNode2.this.texture.getTexId());
                    GLQuadTileNode2.this.texture.setMinFilter(GLES20FixedPipeline.GL_LINEAR_MIPMAP_NEAREST);
                    GLES20FixedPipeline.glGenerateMipmap(GLES20FixedPipeline.GL_TEXTURE_2D);
                    GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
                }


                // XXX - should be packaged in read request
                GLQuadTileNode2.this.tileVersion = GLQuadTileNode2.this.core.tileReader.getTileVersion(current.level, current.tileColumn, current.tileRow);

                GLQuadTileNode2.this.currentRequest = null;
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
                if (!GLQuadTileNode2.this.checkRequest(id))
                    return;
                GLQuadTileNode2.this.currentRequest = null;
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
                if (!GLQuadTileNode2.this.checkRequest(id))
                    return;
                GLQuadTileNode2.this.currentRequest = null;

                // XXX - should be packaged in read request
                GLQuadTileNode2.this.tileVersion = GLQuadTileNode2.this.core.tileReader.getTileVersion(current.level, current.tileColumn, current.tileRow);

                GLQuadTileNode2.this.state = State.UNRESOLVABLE;
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

    protected static class VertexCoordInfo {
        public final int srid;
        public final boolean valid;
        public final boolean projected;

        public VertexCoordInfo(int srid, boolean valid, boolean projected) {
            this.srid = srid;
            this.valid = valid;
            this.projected = projected;
        }
    }

    protected static class GridVertex {
        public GeoPoint value;
        public boolean resolved;
        public PointD projected;
        public int projectedSrid;

        public GridVertex() {
            value = GeoPoint.createMutable();
            resolved = false;
            projected = new PointD(0d, 0d);
            projectedSrid = -1;
        }
    }

    protected static interface VertexResolver extends com.atakmap.map.layer.raster.tilereader.opengl.VertexResolver<GLQuadTileNode2> {}

    protected static class DefaultVertexResolver implements VertexResolver {

        private PointD scratchImg;
        private GLQuadTileNode2 node;
        private boolean populateTerrain;

        public DefaultVertexResolver() {
            this.scratchImg = new PointD(0d, 0d);
            this.node = null;
            this.populateTerrain = (ConfigOptions.getOption("glquadtilenode2.defaultvertexresolver.populate-terrain", 0) != 0);
        }

        @Override
        public void beginDraw(GLMapView view) {}
        @Override
        public void endDraw(GLMapView view) {}

        @Override
        public void beginNode(GLQuadTileNode2 node) {
            this.node = node;
        }

        @Override
        public void endNode(GLQuadTileNode2 node) {
            if(this.node != node)
                throw new IllegalStateException();
            this.node = null;
        }

        @Override
        public void project(GLMapView view, long imgSrcX, long imgSrcY, GridVertex vert) {
            this.scratchImg.x = imgSrcX;
            this.scratchImg.y = imgSrcY;
            this.node.imageToGround(this.scratchImg, vert.value, null);
        }

        static double elScaleFactor = Double.NaN;

        @Override
        public void release() {}
    }

    protected class PreciseVertexResolver extends DefaultVertexResolver implements Runnable {

        private final Object syncOn;
        private Thread active;
        private LinkedList<PointL> queue;
        private PointL query;
        private Set<PointL> pending;
        private Set<PointL> unresolvable;
        private Map<PointL, GeoPoint> precise;
        private GLQuadTileNode2 currentNode;

        private Set<PointL> currentRequest;
        private Set<GLQuadTileNode2> requestNodes;

        private GeoPoint scratchGeo;
        private PointD scratchImg;

        private GLMapView view;

        private int needsResolved;
        private int requested;
        private int numNodesPending;

        private boolean initialized;

        public PreciseVertexResolver(Object syncOn) {
            if (syncOn == null)
                syncOn = this;
            this.syncOn = syncOn;
            this.queue = new LinkedList<PointL>();
            this.pending = new TreeSet<PointL>(GLQuadTileNode3.POINT_COMPARATOR);
            this.precise = new TreeMap<PointL, GeoPoint>(GLQuadTileNode3.POINT_COMPARATOR);
            this.unresolvable = new TreeSet<PointL>(GLQuadTileNode3.POINT_COMPARATOR);

            this.currentRequest = new TreeSet<PointL>(GLQuadTileNode3.POINT_COMPARATOR);

            this.requestNodes = new HashSet<GLQuadTileNode2>();

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
            if(!GLQuadTileNode2.this.imageToGround(image, ground, preciseFlag) || !preciseFlag[0])
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
                for (GLQuadTileNode2 node : this.requestNodes)
                    if (node.glTexGridWidth < minGridWidth)
                        minGridWidth = node.glTexGridWidth;

                for (final GLQuadTileNode2 node : this.requestNodes) {
                    if (node.glTexGridWidth > minGridWidth)
                        continue;

                    final int targetGridWidth = 1 << (4 - Math.min(node.level << 1, 4));
                    if (node.glTexGridWidth < targetGridWidth)
                        view.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                if (node.glTexGridWidth < targetGridWidth) {
                                    node.expandTexGrid();
                                    GLQuadTileNode2.this.verticesInvalid = true;
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
                                    GLES20FixedPipeline.GL_LUMINANCE,
                                    GLES20FixedPipeline.GL_UNSIGNED_BYTE),
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
        public void beginNode(GLQuadTileNode2 node) {
            super.beginNode(node);

            this.currentNode = node;
            this.needsResolved = 0;
            this.requested = 0;
            this.requestNodes.add(node);
        }

        @Override
        public void endNode(final GLQuadTileNode2 node) {
            this.currentNode = null;
            // update our pending count if the node needs one or more vertices
            // resolved
            if (this.requested > 0 && this.needsResolved > 0)
                this.numNodesPending++;

            super.endNode(node);
        }

        @Override
        public void project(GLMapView view, long imgSrcX, long imgSrcY, GridVertex retval) {
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
                                        GLQuadTileNode2.this.verticesInvalid = true;
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

    public static class Options extends GLQuadTileNode3.Options {}

    /**
     * @deprecated to be removed without replacement; use {@link GLQuadTileNode3}
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    protected static class NodeCore extends com.atakmap.map.layer.raster.tilereader.opengl.NodeCore {
        private NodeCore(String type,
                         Initializer init,
                         Initializer.Result result,
                         int srid,
                         double gsdHint,
                         Options opts) {

            super(null, type, init, result, srid, gsdHint, opts);
        }

        static NodeCore create(ImageInfo info, TileReaderFactory.Options readerOpts, GLQuadTileNode2.Options opts, boolean throwOnReaderFailedInit, GLQuadTileNode2.Initializer init) {
            GLQuadTileNode2.Initializer.Result result = init.init(info, readerOpts);
            if(result.error != null && throwOnReaderFailedInit)
                throw new RuntimeException(result.error);

            return new NodeCore(info.type,
                                init,
                                result,
                                info.srid,
                                info.maxGsd,
                                opts);
        }
    }
    
    /**************************************************************************/

    /**
     * @deprecated to be removed without replacement; use {@link GLQuadTileNode3}
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public static interface Initializer {
        public static class Result {
            public TileReader reader;
            public DatasetProjection2 imprecise;
            public DatasetProjection2 precise;
            public Throwable error;
        }
        
        public Result init(ImageInfo info, TileReaderFactory.Options opts);
        public void dispose(Result result);
    }

    /**
     * @deprecated to be removed without replacement; use {@link GLQuadTileNode3}
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public final static Initializer DEFAULT_INIT = new Initializer() {
        @Override
        public Result init(ImageInfo info, TileReaderFactory.Options opts) {
            Result retval = new Result();
            try {
                retval.reader = TileReaderFactory.create(info.path, opts);
                retval.imprecise = new DefaultDatasetProjection2(
                                                            info.srid,
                                                            info.width,
                                                            info.height,
                                                            info.upperLeft,
                                                            info.upperRight,
                                                            info.lowerRight,
                                                            info.lowerLeft);
                
                if(info.precisionImagery) {
                    try {
                        PrecisionImagery p = PrecisionImageryFactory.create(info.path);
                        if(p == null)
                            throw new NullPointerException();
                        retval.precise = p.getDatasetProjection();
                    } catch(Throwable t) {
                        Log.w(TAG, "Failed to parse precision imagery for " + info.path, t);
                    }
                }
            } catch(Throwable t) {
                retval.error = t;
            }
            return retval;
        }
        
        @Override
        public void dispose(Result result) {
            if(result.reader != null)
                result.reader.dispose();
            if(result.imprecise != null)
                result.imprecise.release();
            if(result.precise != null)
                result.precise.release();
        }
    };
}
