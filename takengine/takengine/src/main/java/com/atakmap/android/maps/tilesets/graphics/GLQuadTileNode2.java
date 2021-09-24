
package com.atakmap.android.maps.tilesets.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.android.maps.tilesets.TilesetSupport;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.util.ConfigOptions;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;

public class GLQuadTileNode2 implements GLResolvableMapRenderable {

    public static final String TAG = "GLQuadTileNode";

    private final static BitmapFactory.Options DEFAULT_BITMAP_FACTORY_OPTIONS = new BitmapFactory.Options();
    static {
        DEFAULT_BITMAP_FACTORY_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    private static boolean offscreenFboFailed = false;

    private final static int _NW = 0;
    private final static int _NE = 1;
    private final static int _SW = 2;
    private final static int _SE = 3;

    protected final static int TEXTURE_HINT_RESOLVED = 0x00000001;

    /**
     * This is a temporary array used to store the vertices in before they are added with a
     * bulk put command to a {@link FloatBuffer}. This is expected to be grown as big as
     * it needs and used by all nodes.
     */
    private static float[] vertsTempArray = null;

    /**
     * This is used to draw all the tiles in a batch
     */
    private final GLBatchTileTextureDrawer batchDrawer;

    /*************************************************************************/

    public GLQuadTileNode2(GLTilePatch2 patch, int latIndex, int lngIndex, GLBatchTileTextureDrawer batchDrawer) {
        this(patch, null, -1, 0, latIndex, lngIndex, batchDrawer);
    }

    private GLQuadTileNode2(GLQuadTileNode2 parentQuad, int quad, GLBatchTileTextureDrawer batchDrawer) {
        this(parentQuad._patch, parentQuad, quad, parentQuad._level + 1, (parentQuad._latIndex * 2)
                + (1 - (quad / 2)), (parentQuad._lngIndex * 2) + (quad % 2), batchDrawer);
    }

    private GLQuadTileNode2(GLTilePatch2 patch, GLQuadTileNode2 parentQuad, int quad, int level,
            int latIndex, int lngIndex, GLBatchTileTextureDrawer batchDrawer) {

        synchronized(GLQuadTileNode2.class) {
            if(!Options.initialized) {
                Options.hardwareTransforms = (ConfigOptions.getOption("imagery.hardware-transforms-enabled", 1) != 0);
                Options.textureBorrowEnabled = (ConfigOptions.getOption("imagery.texture-borrow", 1) != 0);
                Options.textureCopyEnabled = (ConfigOptions.getOption("imagery.texture-copy", 1) != 0);
                Options.forceLoResLoad = (ConfigOptions.getOption("imagery.force-lo-res-load", 1) != 0);
            }
        }
        
        _patch = patch;
        _parent = parentQuad;
        _level = level;
        _latIndex = latIndex;
        _lngIndex = lngIndex;
        this.batchDrawer = batchDrawer;
        
        this.vertsDirty = true;
        this.vertsSrid = -1;
        this.vertsIsProjected = false;

        this.rootAncestor = this;
        while (this.rootAncestor._parent != null)
            this.rootAncestor = this.rootAncestor._parent;

        if (_parent == null) {
            this.frameBufferHandle = new int[1];
            this.depthBufferHandle = new int[1];
        }

        this.borrowers = Collections.newSetFromMap(new IdentityHashMap<GLQuadTileNode2, Boolean>());
        this.state = State.UNRESOLVED;
        this.derivedUnresolvableData = false;

        _initBounds();
    }

    public void setTextureCache(GLTextureCache textureCache) {
        if (Options.textureCacheEnabled)
            this.textureCache = textureCache;
    }

    @Override
    public void draw(GLMapView view) {
        if (_dead)
            return;

        final double zoomScale = view.getLegacyScale();
        final double tilePixWidth = this._patch
                .getTilesetInfo().getTilePixelWidth();

        if (Double.compare(tilePixWidth,0.0) == 0)
             return;

        final double layerScale = ((zoomScale * this._patch.getTilesetInfo().getZeroWidth()) / tilePixWidth);
        final int drillLevel = Math.max(Math.min((int) Math.floor((Math.log(layerScale) / Math
                .log(2)) - 0.6), _patch.getLevelCount() - 1), 0);

        drawQuad(view, drillLevel);
    }

    private void drawQuad(GLMapView view, int drillLevel) {

        // Get rid of any unneeded children right away to free up resources
        _releaseInvisibleChildren(view, halfLat, halfLng, drillLevel);

        // need to drill down further?
        if (drillLevel > _level) {
            // check to see if any of our children could not be resolved (this
            // means the tile was not available) and try to make sure we'll have
            // a texture for them to borrow
            boolean unresolvedChildren = false;
            if (Options.forceLoResLoad) {
                for (int i = 0; i < _children.length; i++)
                    unresolvedChildren |= (_children[i] != null && _children[i].state == State.UNRESOLVABLE);

                if (unresolvedChildren
                        && (this.state == State.UNRESOLVED || (this.state == State.UNRESOLVABLE && this.derivedUnresolvableData))) {
                    this.checkCachedTexture();
                    if (this.state == State.UNRESOLVED) {
                        FutureTask<Bitmap> futureBitmap = _patch.getUriResolver().getTile(
                                _latIndex, _lngIndex, _level, DEFAULT_BITMAP_FACTORY_OPTIONS);
                        _pendingTex = new GLPendingTexture(futureBitmap, this.texture);

                        this.state = State.RESOLVING;
                    }
                }
            }

            // see if our texture is resolved
            if (this.state == State.RESOLVING) {
                // stop trying to load our texture
                if (!unresolvedChildren)
                    _pendingTex.cancel();
                // if the texture is done loading, mark us as resolved and
                // retain the texture to allow for borrowing. we will release it
                // shortly if no one is interested
                this.checkPendingTexture();
            }

            // draw children
            _drawVisibleChildren(view, halfLat, halfLng, drillLevel);

            // XXX - we could check for unresolved children again and start the
            // pending texture if necessary, but performance seems
            // adequate already

            // no one is borrowing from us, discard the texture
            if (this.borrowers.size() < 1 && this.texture != null)
                _releaseTexture();
        }
        else if (drillLevel == _level) {
            this.drawSelf(view);
        } else {
            throw new IllegalStateException("drillLevel=" + drillLevel + " _level=" + _level);
        }

        // if we are the root node, release the frame and depth buffer if they
        // were generated to do texture copying
        if (_parent == null) {
            if (this.frameBufferHandle[0] != 0) {
                GLES20FixedPipeline.glDeleteFramebuffers(1, this.frameBufferHandle, 0);
                this.frameBufferHandle[0] = 0;
            }
            if (this.depthBufferHandle[0] != 0) {
                GLES20FixedPipeline.glDeleteRenderbuffers(1, this.depthBufferHandle, 0);
                this.depthBufferHandle[0] = 0;
            }
        }
    }

    private String getTextureKey( )
    {
        if( textureKey == null )
        {
            textureKey = _patch.getUri( ) + "," + _latIndex + "," + _lngIndex + "," + _level;
        }
        return textureKey;
    }

    private void _releaseInvisibleChildren(GLMapView view, double halfLat, double halfLng,
            int drillLevel) {
        if (view.eastBound < halfLng) {
            _releaseChild(_NE);
            _releaseChild(_SE);

        }
        else if (view.westBound > halfLng) {
            _releaseChild(_NW);
            _releaseChild(_SW);
        }

        if (view.southBound > halfLat) {
            _releaseChild(_SW);
            _releaseChild(_SE);
        }
        else if (view.northBound < halfLat) {
            _releaseChild(_NW);
            _releaseChild(_NE);
        }
    }

    private void _drawVisibleChildren(GLMapView view, double halfLat, double halfLng, int drillLevel) {
        if (view.eastBound > halfLng) {
            // east
            if (view.northBound > halfLat) {
                // north-east
                _drawChild(_NE, view, drillLevel);
            }
            if (view.southBound < halfLat) {
                // south-east
                _drawChild(_SE, view, drillLevel);
            }
        }
        if (view.westBound < halfLng) {
            // west
            if (view.northBound > halfLat) {
                // north-west
                _drawChild(_NW, view, drillLevel);
            }
            if (view.southBound < halfLat) {
                // south-west
                _drawChild(_SW, view, drillLevel);
            }
        }
    }

    private boolean _tryBorrowing() {
        if(!Options.textureBorrowEnabled)
            return false;

        GLQuadTileNode2 a = _parent;
        GLQuadTileNode2 updatedAncestor = null;
        GLTextureCache.Entry aTexInfo;
        while (a != null) {
            // if the node is resolved or we've hit the node we're currently
            // borrowing from, break
            if (a.state == State.RESOLVED || a == _borrowNode)
                break;
            // check the cache for the texture; if the cache has a resolved
            // texture, break, otherwise select the closest ancestor with a
            // partially resolved texture
            if (this.rootAncestor.textureCache != null) {
                aTexInfo = this.rootAncestor.textureCache.get(a.getTextureKey());
                if (aTexInfo != null && aTexInfo.hasHint(TEXTURE_HINT_RESOLVED))
                    break;
                else if (aTexInfo != null && updatedAncestor == null)
                    updatedAncestor = a;
            }
            // we're not resolved but have data; record us as the updated
            // ancestor
            if (a.texture != null && updatedAncestor == null)
                updatedAncestor = a;
            a = a._parent;
        }
        // no resolved node could be be found, set to the resolved ancestor
        if (a == null)
            a = updatedAncestor;
        if (a != null && a != _borrowNode) {
            if (a.texture == null)
                a.checkCachedTexture();
            _startBorrowing(a);
        }
        return (a != null);
    }

    private void resolve(GLMapView view) {
        this.checkCachedTexture();
        if (this.state == State.RESOLVED)
            return;

        if (GLMapSurface.SETTING_enableTextureTargetFBO && Options.textureCopyEnabled && !offscreenFboFailed) {
            boolean hasChildData = false;
            boolean childrenResolved = true;
            for (int i = 0; i < this._children.length; i++) {
                if (this._children[i] != null && this._children[i].texture != null) {
                    // the child's texture is not null, so it has at least loaded
                    // some data
                    hasChildData |= true;
                    childrenResolved &= (this._children[i].state == State.RESOLVED);
                } else {
                    // we are only resolved if all children are resolved
                    childrenResolved = false;
                }
            }
            childrenResolved &= hasChildData;

            // copy data from the children into our texture if it is available
            if (hasChildData) {
                boolean fboCreated = false;
                boolean texCreated = false;
                do {
                    int[] frameBuffer = this.rootAncestor.frameBufferHandle;
                    if (frameBuffer[0] == 0)
                        GLES20FixedPipeline.glGenFramebuffers(1, frameBuffer, 0);

                    int[] depthBuffer = this.rootAncestor.depthBufferHandle;
                    if (depthBuffer[0] == 0)
                        GLES20FixedPipeline.glGenRenderbuffers(1, depthBuffer, 0);
                    GLES20FixedPipeline.glBindRenderbuffer(GLES20FixedPipeline.GL_RENDERBUFFER,
                            depthBuffer[0]);
                    
                    if (this.texture == null) {
                        Bitmap.Config config = Bitmap.Config.ARGB_8888;
                        this.texture = new GLTexture(this._patch.getTilesetInfo().getTilePixelWidth(),
                                this._patch.getTilesetInfo().getTilePixelHeight(), config);
                        //if (!childrenResolved)
                        //    this.texture.setMagFilter(GLES20FixedPipeline.GL_NEAREST);
                        this.texture.init();
                        texCreated = true;
                    }
                    
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
                            GLES20FixedPipeline.GL_DEPTH_ATTACHMENT,
                            GLES20FixedPipeline.GL_RENDERBUFFER, depthBuffer[0]);

                    final int fboStatus = GLES20FixedPipeline
                            .glCheckFramebufferStatus(GLES20FixedPipeline.GL_FRAMEBUFFER);
                    fboCreated = (fboStatus == GLES20FixedPipeline.GL_FRAMEBUFFER_COMPLETE);
                } while(false);
                
                if (fboCreated) {
                    GLES20FixedPipeline.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT);
                    
                    int parts = 0;
                    
                    PointD p = null;
                    for (int i = 0; i < 4; i++) {
                        if (this._children[i] != null && this._children[i].texture != null) {
                            if (this._children[i]._texCoords == null) {
                                ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(4 * 4 * 2);
                                b.order(ByteOrder.nativeOrder());
                                this._children[i]._texCoords = b.asFloatBuffer();
                            }
                            if (this._children[i]._verts == null) {
                                ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(4 * 4 * 2);
                                b.order(ByteOrder.nativeOrder());
                                this._children[i]._verts = b.asFloatBuffer();
                            }

                            // ll
                            p = _children[i].groundToImage(_children[i]._sw, p);
                            _children[i]._texCoords.put(0,
                                    (float) (Math.floor(p.x - 0.5) / (float) _children[i].texture
                                            .getTexWidth()));
                            _children[i]._texCoords.put(1,
                                    (float) (Math.floor(p.y - 0.5) / (float) _children[i].texture
                                            .getTexHeight()));
                            p = this.groundToImage(_children[i]._sw, p);
                            _children[i]._verts.put(0, (float) p.x);
                            _children[i]._verts.put(1, (float) p.y);

                            // lr
                            p = _children[i].groundToImage(_children[i]._se, p);
                            _children[i]._texCoords.put(2,
                                    (float) (Math.ceil(p.x + 0.5) / (float) _children[i].texture
                                            .getTexWidth()));
                            _children[i]._texCoords.put(3,
                                    (float) (Math.floor(p.y - 0.5) / (float) _children[i].texture
                                            .getTexHeight()));
                            p = this.groundToImage(_children[i]._se, p);
                            _children[i]._verts.put(2, (float) p.x);
                            _children[i]._verts.put(3, (float) p.y);

                            // ur
                            p = _children[i].groundToImage(_children[i]._ne, p);
                            _children[i]._texCoords.put(4,
                                    (float) (Math.ceil(p.x + 0.5) / (float) _children[i].texture
                                            .getTexWidth()));
                            _children[i]._texCoords.put(5,
                                    (float) (Math.ceil(p.y + 0.5) / (float) _children[i].texture
                                            .getTexHeight()));
                            p = this.groundToImage(_children[i]._ne, p);
                            _children[i]._verts.put(4, (float) p.x);
                            _children[i]._verts.put(5, (float) p.y);

                            // ul
                            p = _children[i].groundToImage(_children[i]._nw, p);
                            _children[i]._texCoords.put(6,
                                    (float) (Math.floor(p.x - 0.5) / (float) _children[i].texture
                                            .getTexWidth()));
                            _children[i]._texCoords.put(7,
                                    (float) (Math.ceil(p.y + 0.5) / (float) _children[i].texture
                                            .getTexHeight()));
                            p = this.groundToImage(_children[i]._nw, p);
                            _children[i]._verts.put(6, (float) p.x);
                            _children[i]._verts.put(7, (float) p.y);

                            this._children[i].texture.draw(4, GLES20FixedPipeline.GL_FLOAT,
                                    this._children[i]._texCoords, this._children[i]._verts);
                            if (_children[i].state == State.RESOLVED)
                                parts++;
                        }
                    }

                    // we are only going to mark resolved if we got the data
                    // from all children IF we are unresolvable. If the node is
                    // resolvable, the data for the current level is not
                    // guaranteed to be a subsampled version of the children so
                    // we still want to load the tile
                    if (parts == 4 && this.state == State.UNRESOLVABLE)
                        this.state = State.RESOLVED;
                    if (this.state == State.UNRESOLVABLE)
                        this.derivedUnresolvableData = true;

                    this.texCoordsDirty = true;
                } else {
                    Log.w(TAG, "Failed to create FBO for texture copy.");
                    if(texCreated) {
                        this.texture.release();
                        this.texture = null;
                    }
                    offscreenFboFailed = true;
                }

                GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER, 0);
                
                if (fboCreated && !childrenResolved)
                    this.texture.setMagFilter(GLES20FixedPipeline.GL_NEAREST);
            }
        }

        // child data has been copied into our texture so there is no need to
        // retain
        _releaseChildren();

        // This means a load (re)attempt is needed
        if (this.state == State.UNRESOLVED) {
            FutureTask<Bitmap> futureBitmap = _patch.getUriResolver().getTile(_latIndex,
                    _lngIndex, _level, DEFAULT_BITMAP_FACTORY_OPTIONS);
            // set the tile version at the point where the tile is requested
            this.tileVersion = _patch.getUriResolver().getTilesVersion(_latIndex, _lngIndex, _level);
            _pendingTex = new GLPendingTexture(futureBitmap, this.texture);
            
            this.state = State.RESOLVING;
        }
    }

    private void checkCachedTexture() {
        if (this.rootAncestor.textureCache != null) {
            GLTextureCache.Entry cachedTexture = this.rootAncestor.textureCache.remove(this
                    .getTextureKey());
            if (cachedTexture != null) {
                final int cacheTileVersion = ((Number)cachedTexture.opaque).intValue();
                
                if(this.texture != null && this.tileVersion > cacheTileVersion) {
                    // cached texture is out of date
                    cachedTexture.texture.release();
                    return;
                } else if(this.texture != null) {
                    // current is more out of date than cached, use cached
                    this.texture.release();
                }

                this.texture = cachedTexture.texture;
                if (cachedTexture.hasHint(TEXTURE_HINT_RESOLVED))
                    this.state = State.RESOLVED;
                this.tileVersion = cacheTileVersion;
                this.texCoordsDirty = true;
                this.vertsDirty = true;
            } else if (this.derivedUnresolvableData) {
                // the cached texture was released
                this.derivedUnresolvableData = false;
            }
        }
    }

    private void checkPendingTexture() {
        if (_pendingTex == null || _pendingTex.isPending())
            return;

        if (_pendingTex.isCanceled()) {
            this.state = State.UNRESOLVED;
        } else if (_pendingTex.isUnresolved()) {
            this.state = State.UNRESOLVABLE;
        } else if (_pendingTex.isResolved()) {
            // the pending texture has been resolved
            this.state = State.RESOLVED;
            if (this.texture != _pendingTex.getTexture()) {
                if (this.texture != null)
                    this.texture.release();
                this.texture = _pendingTex.getTexture();
            }
            this.texCoordsDirty = true;
            this.vertsDirty = true;
        }

        _pendingTex = null;
    }

    private void drawSelf(GLMapView view) {
        if (_verts == null) {
            ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(_pointCount*3*4);
            b.order(ByteOrder.nativeOrder());
            _verts = b.asFloatBuffer();
        }
        _projectVerts(view);

        boolean resolveUnresolvable = false;
        for (int i = 0; i < _children.length; i++)
            resolveUnresolvable |= (_children[i] != null);

        // if we are resolved or unresolvable, make sure that our tile is still
        // up to date. if not, move back to the unresolved state.
        if((this.state == State.RESOLVED || this.state == State.UNRESOLVABLE)) {
            final int version = this._patch.getUriResolver().getTilesVersion(_latIndex, _lngIndex, _level);
            if(version != 0 && this.tileVersion != version)
                this.state = State.UNRESOLVED;
        }

        // if the node is unresolved or it is unresolvable but we have children,
        // try to resolve the texture.
        if (this.state == State.UNRESOLVED
                || (resolveUnresolvable && this.state == State.UNRESOLVABLE))
            this.resolve(view);

        if (this.state != State.RESOLVED) {
            // check to see if the tile is done loading
            if (this.state == State.RESOLVING)
                this.checkPendingTexture();

            if (this.state != State.RESOLVED) {
                // the texture is unresolved, try to borrow low resolution data
                // from one of our ancestors while we're waiting for the data at
                // this level to load
                _tryBorrowing();
                // draw the borrowed texture if we've got it
                if (_borrowNode != null) {
                    if (this.texture != null)
                        _updateTexCoords(_borrowNode);
                    
                    this.drawTexture(view, _borrowNode.texture.getTexId());

                    this.texCoordsDirty |= (this.texture != null);
                }
            } 
        }
        if (this.state == State.RESOLVED)
            _stopBorrowing();

        if (this.texture != null) {
            if (this.texCoordsDirty)
                _updateTexCoords(this);

            this.drawTexture(view, this.texture.getTexId());
        }
    }

    private void drawTexture(GLMapView view, int texId) {
        if(this.texVertIndices != null) {
            batchDrawer.setUseForwardMatrix( vertsIsProjected );
            batchDrawer.drawWithIndices( texId, textureMode, texVertIndices.remaining(),
                        _texCoords, _verts, texVertIndices);
        } else {
            if( vertsIsProjected )
            {
                GLES20FixedPipeline.glPushMatrix( );
                GLES20FixedPipeline.glLoadMatrixf( view.sceneModelForwardMatrix, 0 );
            }
            GLTexture.draw( texId, this.textureMode, _pointCount,
                        2, GLES20FixedPipeline.GL_FLOAT, _texCoords,
                        3, GLES20FixedPipeline.GL_FLOAT, _verts,
                        this.colorR, this.colorG, this.colorB, this.colorA );
            if( vertsIsProjected )
            {
                GLES20FixedPipeline.glPopMatrix( );
            }
        }
    }

    private void _stopBorrowing() {
        if (_borrowNode != null) {
            _borrowNode.borrowers.remove(this);
            _borrowNode = null;
        }
    }

    private void _startBorrowing(GLQuadTileNode2 ancestor) {
        if (_borrowNode != null && _borrowNode == ancestor)
            return;
        else if (_borrowNode != null)
            _stopBorrowing();

        _borrowNode = ancestor;
        if (_borrowNode != null) {
            _borrowNode.borrowers.add(this);

            // update the texture coordinate from the node we are borrowing from
            _updateTexCoords(_borrowNode);
        }
    }

    private void _drawChild(int quad, GLMapView view, int drillLevel) {
        if (_children[quad] == null)
            _children[quad] = new GLQuadTileNode2(this, quad, batchDrawer);
        if (_children[quad] != null && !_children[quad]._dead)
            _children[quad].drawQuad(view, drillLevel);
    }

    @Override
    public void release() {
        _releaseChildren();

        // hint to gc to nab these
        _verts = null;
        _texCoords = null;
        this.texCoordsDirty = true;

        _releaseTexture();
        _stopBorrowing();
    }

    private void _releaseTexture() {
        if (_pendingTex != null)
            _pendingTex.cancel();
        this.checkPendingTexture();
        if (this.texture != null) {
            if (this.rootAncestor.textureCache != null)
                this.rootAncestor.textureCache.put(this.getTextureKey(), this.texture,
                        (this.state == State.RESOLVED) ? TEXTURE_HINT_RESOLVED : 0, Integer.valueOf(this.tileVersion));
            else
                this.texture.release();
            this.texture = null;
        }

        // This node is no longer resolved in this case
        if (this.state != State.UNRESOLVABLE)
            this.state = State.UNRESOLVED;
    }

    private void _releaseChildren() {
        for (int i = 0; i < _children.length; i++)
            _releaseChild(i);
    }

    private void _releaseChild(int index) {
        if (_children[index] != null)
            _children[index].release();
        _children[index] = null;
    }

    private void _initBounds() {
        this.boundsSWNE = new double[4];
        final TilesetSupport support = _patch.getUriResolver();

        // obtain the bounds of the lower-right child and use that to compute
        // half lat/lon
        this.boundsSWNE = support.getTileBounds(_latIndex * 2, _lngIndex * 2 + 1, _level + 1,
                this.boundsSWNE);
        this.halfLat = this.boundsSWNE[2];
        this.halfLng = this.boundsSWNE[1];

        // obtain our own bounds
        this.boundsSWNE = support.getTileBounds(_latIndex, _lngIndex, _level, this.boundsSWNE);
        _sw = new GeoPoint(this.boundsSWNE[0], this.boundsSWNE[1]);
        _nw = new GeoPoint(this.boundsSWNE[2], this.boundsSWNE[1]);
        _se = new GeoPoint(this.boundsSWNE[0], this.boundsSWNE[3]);
        _ne = new GeoPoint(this.boundsSWNE[2], this.boundsSWNE[3]);

        GeoPoint sw = _patch.getInfo().getLowerLeft();
        GeoPoint se = _patch.getInfo().getLowerRight();
        GeoPoint nw = _patch.getInfo().getUpperLeft();
        GeoPoint ne = _patch.getInfo().getUpperRight();

        _clipToBounds(sw, nw, ne, se);
        this.texCoordsDirty = true;
    }

    private void _clipToBounds(GeoPoint sw, GeoPoint nw, GeoPoint ne, GeoPoint se) {
        double minNorth = Math.min(nw.getLatitude(), ne.getLatitude());
        double maxSouth = Math.max(sw.getLatitude(), se.getLatitude());
        double minEast = Math.min(ne.getLongitude(), se.getLongitude());
        double maxWest = Math.max(nw.getLongitude(), sw.getLongitude());
        
        List<GeoPoint> points;
        // if this tile is trivially completely inside the tileset,
        // just set the points and don't bother clipping.
        if (this.boundsSWNE[2] <= minNorth && this.boundsSWNE[3] <= minEast
                && this.boundsSWNE[0] >= maxSouth && this.boundsSWNE[1] >= maxWest) {

            points = new ArrayList<GeoPoint>(4);
            points.add(new GeoPoint(this.boundsSWNE[2], this.boundsSWNE[1]));
            points.add(new GeoPoint(this.boundsSWNE[2], this.boundsSWNE[3]));
            points.add(new GeoPoint(this.boundsSWNE[0], this.boundsSWNE[3]));
            points.add(new GeoPoint(this.boundsSWNE[0], this.boundsSWNE[1]));
        } else {
            points = _getClipList(sw, nw, ne, se);
            if (points.isEmpty()) {
                _dead = true;
            }

            for (int i = 0; i < points.size(); i++) {
                _setPoint(i, points.get(i));
            }
            _pointCount = points.size();
            
            this.textureMode = GLES20FixedPipeline.GL_TRIANGLE_FAN;
        }
        
        if(points.size() == 4) {
            // XXX - we really want to be doing this so long as the clipped
            //       bounds are a quad (well, we really want to be doing this
            //       always!)
            final TilesetInfo tsInfo = _patch.getTilesetInfo();

            final int subs = Math.max(Math.min(MathUtils.nextPowerOf2((int)((tsInfo.getZeroHeight() /(double)(1<<_level)) / GLMapView.recommendedGridSampleDistance)), 32), 1);

            ByteBuffer bb = com.atakmap.lang.Unsafe.allocateDirect(2*GLTexture.getNumQuadMeshIndices(subs, subs));
            bb.order(ByteOrder.nativeOrder());
            GLTexture.createQuadMeshIndexBuffer(subs, subs, bb.asShortBuffer());
            this.texVertIndices = bb.asShortBuffer();
            
            _pointCount = GLTexture.getNumQuadMeshVertices(subs, subs);
            _points = new double[_pointCount*2];

            Matrix grid2geo = Matrix.mapQuads(
                    0, 0,
                    subs, 0,
                    subs, subs,
                    0, subs,
                    points.get(0).getLongitude(), points.get(0).getLatitude(),
                    points.get(1).getLongitude(), points.get(1).getLatitude(),
                    points.get(2).getLongitude(), points.get(2).getLatitude(),
                    points.get(3).getLongitude(), points.get(3).getLatitude());
            
            int idx = 0;
            PointD p = new PointD(0, 0, 0);
            GeoPoint geo = GeoPoint.createMutable();
            for(int y = 0; y <= subs; y++) {
                for(int x = 0; x <= subs; x++) {
                    p.x = x;
                    p.y = y;
                    
                    grid2geo.transform(p, p);

                    geo.set(p.y, p.x);
                    _setPoint(idx++, geo);
                }
            }
            this.textureMode = GLES20FixedPipeline.GL_TRIANGLE_STRIP;
        }
    }

    // Sutherland-Hodgman clipping algorithm. Clip window is this tile, polygon to clip is the
    // tileset.
    private List<GeoPoint> _getClipList(GeoPoint sw, GeoPoint nw, GeoPoint ne, GeoPoint se) {
        // Didn't want to use Double.MAX_VALUE here because the value will get multiplied in
        // GeoPoint.findIntersection(),
        // potentially causing problems. Just pick an arbitrary big number.
        double MIN = -1000000000;
        double MAX = 1000000000;

        LinkedList<GeoPoint> inputList = new LinkedList<GeoPoint>();
        inputList.add(sw);
        inputList.add(nw);
        inputList.add(ne);
        inputList.add(se);

        // could make this more generic, but it seems more efficient to simply unroll the algorithm
        // and
        // code each edge individually, since the clip window is not an arbitrary polygon -- we know
        // it's a
        // non-rotated rectangle.

        // clip to west edge
        LinkedList<GeoPoint> outputList = new LinkedList<GeoPoint>();
        GeoPoint last = inputList.getLast();
        GeoPoint west1 = new GeoPoint(MIN, this.boundsSWNE[1]);
        GeoPoint west2 = new GeoPoint(MAX, this.boundsSWNE[1]);
        for (GeoPoint cur : inputList) {
            if (cur.getLongitude() >= this.boundsSWNE[1]) { // inside
                if (last.getLongitude() < this.boundsSWNE[1]) {
                    GeoPoint inter = GeoCalculations.findIntersection(last, cur, west1, west2);
                    outputList.add(inter);
                }
                outputList.add(cur);
            } else if (last.getLongitude() >= this.boundsSWNE[1]) {
                GeoPoint inter = GeoCalculations.findIntersection(last, cur, west1, west2);
                outputList.add(inter);
            }
            last = cur;
        }

        if (outputList.isEmpty())
            return outputList;

        // swap input and output lists
        LinkedList<GeoPoint> tmp = inputList;
        inputList = outputList;
        outputList = tmp;
        outputList.clear();

        // clip to north edge
        last = inputList.getLast();
        GeoPoint north1 = new GeoPoint(this.boundsSWNE[2], MIN);
        GeoPoint north2 = new GeoPoint(this.boundsSWNE[2], MAX);
        for (GeoPoint cur : inputList) {
            if (cur.getLatitude() <= this.boundsSWNE[2]) { // inside
                if (last.getLatitude() > this.boundsSWNE[2]) {
                    GeoPoint inter = GeoCalculations.findIntersection(last, cur, north1, north2);
                    outputList.add(inter);
                }
                outputList.add(cur);
            } else if (last.getLatitude() <= this.boundsSWNE[2]) {
                GeoPoint inter = GeoCalculations.findIntersection(last, cur, north1, north2);
                outputList.add(inter);
            }
            last = cur;
        }

        if (outputList.isEmpty())
            return outputList;

        // clip to east edge
        tmp = inputList;
        inputList = outputList;
        outputList = tmp;
        outputList.clear();
        last = inputList.getLast();
        GeoPoint east1 = new GeoPoint(MIN, this.boundsSWNE[3]);
        GeoPoint east2 = new GeoPoint(MAX, this.boundsSWNE[3]);
        for (GeoPoint cur : inputList) {
            if (cur.getLongitude() <= this.boundsSWNE[3]) { // inside
                if (last.getLongitude() > this.boundsSWNE[3]) {
                    GeoPoint inter = GeoCalculations.findIntersection(last, cur, east1, east2);
                    outputList.add(inter);
                }
                outputList.add(cur);
            } else if (last.getLongitude() <= this.boundsSWNE[3]) {
                GeoPoint inter = GeoCalculations.findIntersection(last, cur, east1, east2);
                outputList.add(inter);
            }
            last = cur;
        }

        if (outputList.isEmpty())
            return outputList;

        // clip to south edge
        tmp = inputList;
        inputList = outputList;
        outputList = tmp;
        outputList.clear();
        last = inputList.getLast();
        GeoPoint south1 = new GeoPoint(this.boundsSWNE[0], MIN);
        GeoPoint south2 = new GeoPoint(this.boundsSWNE[0], MAX);
        for (GeoPoint cur : inputList) {
            if (cur.getLatitude() >= this.boundsSWNE[0]) { // inside
                if (last.getLatitude() < this.boundsSWNE[0]) {
                    GeoPoint inter = GeoCalculations.findIntersection(last, cur, south1, south2);
                    outputList.add(inter);
                }
                outputList.add(cur);
            } else if (last.getLatitude() >= this.boundsSWNE[0]) {
                GeoPoint inter = GeoCalculations.findIntersection(last, cur, south1, south2);
                outputList.add(inter);
            }
            last = cur;
        }

        return outputList;
    }

    private void _setPoint(int i, GeoPoint v) {
        int x = i * 2;
        int y = x + 1;
        _points[x] = v.getLongitude();
        _points[y] = v.getLatitude();
    }

    private void _updateTexCoords(GLQuadTileNode2 node) {
        if (node.texture == null)
            return;
        if (_texCoords == null) {
            ByteBuffer bb2 = com.atakmap.lang.Unsafe.allocateDirect(4*2 * _pointCount);
            bb2.order(ByteOrder.nativeOrder());
            _texCoords = bb2.asFloatBuffer();
        }

        GeoPoint g = GeoPoint.createMutable();
        PointD p = null;
        for (int i = 0; i < _pointCount; ++i) {
            int x = (i * 2);
            int y = x + 1;

            g.set(_points[y], _points[x]);
            p = node.groundToImage(g, p);

            _texCoords.put(x, ((float) p.x / (float) node.texture.getTexWidth()));
            _texCoords.put(y, ((float) p.y / (float) node.texture.getTexHeight()));
        }
        texCoordsDirty = false;
    }

    private void _projectVerts(GLMapView view) {
        int requireArraySize = _pointCount * 3;
        if( ( vertsTempArray == null ) || ( vertsTempArray.length < requireArraySize ) )
        {
            vertsTempArray = new float[requireArraySize];
        }
        
        final boolean hardwareTransforms = Options.hardwareTransforms &&
                !view.crossesIDL && 
                (view.drawMapResolution > view.hardwareTransformResolutionThreshold);
        if(hardwareTransforms) {
            if(this.vertsDirty || view.drawSrid != this.vertsSrid || !vertsIsProjected) {
                int idx = 0;
                for (int i = 0; i < _pointCount; ++i) {
                    int x = i * 2;
                    int y = (i * 2) + 1;
                    view.scratch.geo.set(_points[y], _points[x]);
                    view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);

                    vertsTempArray[idx++] = (float)view.scratch.pointD.x;
                    vertsTempArray[idx++] = (float)view.scratch.pointD.y;
                    vertsTempArray[idx++] = (float)view.scratch.pointD.z;
                }
                _verts.put( vertsTempArray, 0, idx );
                _verts.rewind( );

                this.vertsDirty = false;
                this.vertsSrid = view.drawSrid;
            }
        }
        else if( ( vertsDirty ) || ( view.drawVersion != vertsDrawVersion ) || ( view.drawSrid
                    != vertsSrid ) || vertsIsProjected )
        {
            int idx = 0;
            for( int i = 0; i < _pointCount; ++i )
            {
                int x = i * 2;
                int y = ( i * 2 ) + 1;
                view.scratch.geo.set( _points[y], _points[x] );
                view.scene.mapProjection.forward( view.scratch.geo, view.scratch.pointD );
                view.scene.forward.transform( view.scratch.pointD, view.scratch.pointD );

                vertsTempArray[idx++] = (float) view.scratch.pointD.x;
                vertsTempArray[idx++] = (float) view.scratch.pointD.y;
                vertsTempArray[idx++] = (float) view.scratch.pointD.z;
            }
            _verts.put( vertsTempArray, 0, idx );
            _verts.rewind( );

            vertsDirty = false;
            vertsDrawVersion = view.drawVersion;
            vertsSrid = view.drawSrid;
        }
        vertsIsProjected = hardwareTransforms;
    }

    public void setColor(int color) {
        this.colorR = Color.red(color)/255f;
        this.colorG = Color.green(color)/255f;
        this.colorB = Color.blue(color)/255f;
        this.colorA = Color.alpha(color)/255f;
    }

    /**************************************************************************/
    // GL Resolvable Map Renderable

    @Override
    public State getState() {
        State retval = null;
        State childState;
        for (int i = 0; i < _children.length; i++) {
            if (_children[i] == null)
                continue;
            childState = _children[i].getState();
            if (childState == State.RESOLVING)
                return State.RESOLVING;
            else if (retval == null || (retval != childState && childState != State.RESOLVED))
                retval = childState;
        }

        if (retval == null)
            retval = this.state;
        return retval;
    }

    @Override
    public void suspend() {
        for (int i = 0; i < _children.length; i++)
            if (_children[i] != null)
                _children[i].suspend();

        if (this.state == State.RESOLVING) {
            _pendingTex.cancel();
            this.checkPendingTexture();
            if (this.state != State.RESOLVED && this.state != State.UNRESOLVABLE)
                this.state = State.SUSPENDED;
        }
    }

    @Override
    public void resume() {
        for (int i = 0; i < _children.length; i++)
            if (_children[i] != null)
                _children[i].resume();

        if (this.state == State.SUSPENDED)
            this.state = State.UNRESOLVED;
    }

    /**************************************************************************/
    // Map Data

    public GeoPoint imageToGround(PointD p, GeoPoint g) {
        final TilesetSupport support = _patch.getUriResolver();
        final double lat = support.getTilePixelLat(_latIndex, _lngIndex, _level, (int) p.y);
        final double lng = support.getTilePixelLng(_latIndex, _lngIndex, _level, (int) p.x);
        if (g == null)
            return new GeoPoint(lat, lng);
        g.set(lat, lng);
        return g;
    }

    public PointD groundToImage(GeoPoint g, PointD p) {
        final TilesetSupport support = _patch.getUriResolver();
        final double x = support.getTilePixelX(_latIndex, _lngIndex, _level, g.getLongitude());
        final double y = support.getTilePixelY(_latIndex, _lngIndex, _level, g.getLatitude());
        if (p == null)
            return new PointD(x, y);
        p.x = x;
        p.y = y;
        return p;
    }

    /**************************************************************************/

    // The tileset boundary can potentially cross this tile twice on each edge,
    // so we need to allow for up to 8 vertices.
    double[] _points = new double[16];

    /**
     * The ancestor node we are borrowing low resolution data from.
     */
    private GLQuadTileNode2 _borrowNode;

    private final GLTilePatch2 _patch;

    private GLQuadTileNode2 _parent;

    private GLQuadTileNode2[] _children = {
            null, null,
            null, null
    };

    GeoPoint _sw, _nw, _se, _ne;

    private final int _level, _latIndex, _lngIndex;

    private double[] boundsSWNE;
    private double halfLat;
    private double halfLng;

    private GLPendingTexture _pendingTex;

    private boolean _dead;

    private int _pointCount = 4;

    private FloatBuffer _verts;
    private FloatBuffer _texCoords;
    private ShortBuffer texVertIndices;

    /**
     * When <code>true</code>, {@link #_updateTexCoords()} should be invoked prior to rendering the
     * texture.
     */
    private boolean texCoordsDirty;
    
    private boolean vertsDirty;
    private int vertsSrid;
    private int vertsDrawVersion;
    private boolean vertsIsProjected;

    /**
     * The top-most ancestor for this node. The root node retains ownership of
     * {@link #frameBufferHandle} and {@link #depthBufferHandle}, which may be used by descendants
     * for texture compositing during a render pump.
     */
    private GLQuadTileNode2 rootAncestor;

    private int[] frameBufferHandle;
    private int[] depthBufferHandle;

    /**
     * The texture data for this node.
     */
    private GLTexture texture;

    /**
     * This is the lazy loaded key for the texture to be used
     */
    private String textureKey = null;

    private GLTextureCache textureCache;
    private Set<GLQuadTileNode2> borrowers;
    private State state;
    private boolean derivedUnresolvableData;

    private int textureMode;
    
    private int tileVersion;
    
    private float colorR = 1f;
    private float colorG = 1f;
    private float colorB = 1f;
    private float colorA = 1f;
    
    /*************************************************************************/
    
    private final static class Options {
        public static boolean hardwareTransforms;

        /**
         * Allows for texture caching.
         */
        public static boolean textureCacheEnabled;

        /**
         * Allows for creation of a parent's texture by copying data from its children.
         */
        public static boolean textureCopyEnabled;

        /**
         * Forces parents to load their texture for borrow when a child's texture cannot be resolved.
         */
        public static boolean forceLoResLoad;
        
        public static boolean textureBorrowEnabled;

        public static boolean initialized = false;

        private Options() {}
    }
}
