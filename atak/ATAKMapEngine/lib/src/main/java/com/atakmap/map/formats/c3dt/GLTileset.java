package com.atakmap.map.formats.c3dt;

import android.opengl.GLES30;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.SceneObjectControl;
import com.atakmap.map.layer.model.opengl.GLSceneSpi;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.opengl.DepthSampler;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.Collections2;
import com.atakmap.util.ConfigOptions;

import org.json.JSONObject;

import java.io.File;
import java.util.Collection;
import java.util.Set;

final class GLTileset implements GLMapRenderable2, Controls {
    // 128MB
    final static long cacheSize = 128L*1024L*1024L;

    public final static GLSceneSpi SPI = new GLSceneSpi() {
        @Override
        public GLMapRenderable2 create(MapRenderer renderer, ModelInfo info, String cacheDir) {
            if(info == null)
                return null;
            if(!Objects.equals(info.type, Cesium3DTilesModelInfoSpi.INSTANCE.getName()))
                return null;

            try {
                String globalCacheDir = ConfigOptions.getOption("3dtiles.cache-dir", null);

                byte[] buffer;
                ContentSource source = ContentSources.createDefault(true);
                ContentContainer cache = null;
                if((info.uri.startsWith("http:") || info.uri.startsWith("https:")) && (cacheDir != null || globalCacheDir != null)) {
                    if(globalCacheDir != null)
                        cache = ContentSources.createCache(new File(globalCacheDir), null);
                    else if(cacheDir != null)
                        cache = ContentSources.createCache(new File(cacheDir), info.uri.substring(0, info.uri.lastIndexOf('/')));
                }
                do {
                    if(cache != null) {
                        cache.connect();
                        buffer = cache.getData(info.uri, null);
                        cache.disconnect();
                        if(buffer != null)
                            break;
                    }
                    source.connect();
                    buffer = source.getData(info.uri, null);
                    source.disconnect();

                    // XXX - this is a little hacky
                    if(buffer != null && cache != null) {
                        cache.connect();
                        cache.put(info.uri, buffer, System.currentTimeMillis());
                        cache.disconnect();
                    }
                } while(false);

                final Tileset tileset = Tileset.parse(new JSONObject(new String(buffer, FileSystemUtils.UTF8_CHARSET)));
                if(tileset == null)
                    return null;
                final RenderContext ctx = LegacyAdapters.getRenderContext(renderer);
                if (ctx.getRenderSurface() != null)
                    tileset.maxScreenSpaceError *= ctx.getRenderSurface().getDpi() / 240d;

                // configure a proxy through the cache if one is available
                if(source != null && cache != null)
                    source = new ContentProxy(source, cache);

                final String uri = Util.resolve(info.uri);
                return new GLTileset(ctx, tileset, source, uri.substring(0, uri.lastIndexOf('/')+1));
            } catch(Throwable t) {
                return null;
            }
        }

        @Override
        public int getPriority() {
            return 0;
        }
    };

    final RenderContext ctx;
    final Tileset tileset;
    final String baseUri;
    final ContentSource source;
    GLTile root;
    final Set<Object> controls = Collections2.newIdentityHashSet();

    static double tileSize = 16;

    final ResourceManager resmgr;

    DepthSampler depthSampler;

    MapRendererState state;

    GLTileset(RenderContext ctx, Tileset tileset, ContentSource handler, String baseUri) {
        this.ctx = ctx;
        this.tileset = tileset;
        this.source = handler;
        this.baseUri = baseUri;

        controls.add(new SceneControlImpl());
        controls.add(new HitTestControlImpl());

        this.resmgr = new ResourceManager(ctx);

        this.state = new MapRendererState(ctx);
    }

    @Override
    public void draw(GLMapView view, int pass) {
        if(!MathUtils.hasBits(pass, GLMapView.RENDER_PASS_SCENES))
            return;

        if(view.drawMapResolution*tileSize > tileset.geometricError)
            return;
        if(root == null) {
            getControl(SceneControlImpl.class).dispatchSceneBoundsChanged(Tile.approximateBounds(tileset.root), tileset.geometricError/tileSize);

            source.connect();

            root = new GLTile(tileset, tileset.root, 0, baseUri);
            root.resmgr = resmgr;
            root.source = source;
        }

        // update state
        state.scene = view.scene;
        state.top = view._top;
        state.left = view._left;
        state.bottom = view._bottom;
        state.right = view._right;
        state.northBound = view.northBound;
        state.westBound = view.westBound;
        state.southBound = view.southBound;
        state.eastBound = view.eastBound;
        state.drawSrid = view.drawSrid;
        state.isDepthTest = false;
        state.uMVP = -1;

        // pull the projection matrix from the graphics state
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        for(int i = 0; i < 16; i++)
            state.projection.set(i%4, i/4, view.scratch.matrixF[i]);

        root.draw(state, false);
    }

    @Override
    public void release() {
        if(root != null) {
            root.release();
            root = null;
        }
        if(depthSampler != null) {
            depthSampler.dispose();
            depthSampler = null;
        }

        source.disconnect();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    /*************************************************************************/
    // Controls

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        if(controlClazz == null)
            return null;
        for(Object ctrl : controls)
            if(controlClazz.isAssignableFrom(ctrl.getClass()))
                return controlClazz.cast(ctrl);
        return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.addAll(this.controls);
    }

    /*************************************************************************/


    /*************************************************************************/

    private class SceneControlImpl implements SceneObjectControl {

        Set<OnBoundsChangedListener> listeners = Collections2.newIdentityHashSet();

        @Override
        public boolean isModifyAllowed() {
            return false;
        }

        @Override
        public void setLocation(GeoPoint location) {}

        @Override
        public void setLocalFrame(Matrix localFrame) {}

        @Override
        public void setSRID(int srid) {}

        @Override
        public void setAltitudeMode(ModelInfo.AltitudeMode mode) {}

        @Override
        public GeoPoint getLocation() {
            return null;
        }

        @Override
        public int getSRID() {
            return 4326;
        }

        @Override
        public Matrix getLocalFrame() {
            return null;
        }

        @Override
        public ModelInfo.AltitudeMode getAltitudeMode() {
            return ModelInfo.AltitudeMode.Absolute;
        }

        @Override
        public void addOnSceneBoundsChangedListener(OnBoundsChangedListener l) {
            synchronized(listeners) {
                listeners.add(l);
            }
        }

        @Override
        public void removeOnSceneBoundsChangedListener(OnBoundsChangedListener l) {
            synchronized (listeners) {
                listeners.remove(l);
            }
        }

        void dispatchSceneBoundsChanged(Envelope aabb, double minRes) {
            synchronized(listeners) {
                for(OnBoundsChangedListener l : listeners)
                    l.onBoundsChanged(aabb, minRes, 0d);
            }
        }
    }

    private class HitTestControlImpl implements ModelHitTestControl {

        @Override
        public boolean hitTest(final float screenX, final float screenY, final GeoPoint geoPoint) {
            final boolean[] retval = new boolean[] {false, false};
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    do {
                        if(root == null)
                            break;
                        if(depthSampler == null)
                            depthSampler = DepthSampler.create();
                        if(depthSampler == null)
                            break;

                        MapRendererState queryState = new MapRendererState(state);
                        queryState.isDepthTest = true;
                        queryState.uMVP = depthSampler.program.uMVP;

                        // pull the projection matrix from the graphics state

                        // NOTE: We are pushing the far plane very far out as
                        // the distribution of depth values is concentrated
                        // close to the near plane. During rendering, we want
                        // to minimize the distance between the near and far
                        // planes to avoid z-fighting, hwoever, during the
                        // depth hit-test, we'll push them further apart to get
                        // better precision for depth value retrieval.

                        // A better implementation of depth value encoding and
                        // the use of an actual perspective projection could
                        // mitigate the need to modify here
                        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION);
                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glOrthof(queryState.left, queryState.right, queryState.bottom, queryState.top, (float) queryState.scene.camera.near, -2f);
                        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, queryState.scratch.matrixF, 0);
                        for (int i = 0; i < 16; i++)
                            queryState.projection.set(i % 4, i / 4, queryState.scratch.matrixF[i]);
                        GLES20FixedPipeline.glPopMatrix();
                        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);

                        GLES30.glDepthMask(true);
                        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
                        GLES30.glDepthFunc(GLES30.GL_LEQUAL);

                        depthSampler.begin(screenX, queryState.top-screenY);
                        root.draw(queryState, false);
                        float depth = depthSampler.getDepth();
                        depthSampler.end();

                        if(depth >= 1.0f)
                            break;

                        // pull the ortho matrix
                        queryState.scratch.matrix.set(queryState.projection);

                        queryState.scratch.pointD.x = screenX;
                        queryState.scratch.pointD.y = queryState.top-screenY;
                        queryState.scratch.pointD.z = 0;

                        queryState.scratch.matrix.transform(queryState.scratch.pointD, queryState.scratch.pointD);
                        queryState.scratch.pointD.z = depth * 2.0f - 1.0f;

                        try {
                            queryState.scratch.matrix.set(queryState.scratch.matrix.createInverse());
                        } catch(NoninvertibleTransformException e) {
                            break;
                        }

                        // NDC -> ortho
                        queryState.scratch.matrix.transform(queryState.scratch.pointD, queryState.scratch.pointD);
                        // ortho -> projection
                        queryState.scene.inverse.transform(queryState.scratch.pointD, queryState.scratch.pointD);
                        // projection -> LLA
                        queryState.scene.mapProjection.inverse(queryState.scratch.pointD, geoPoint);
                        retval[1] = true;
                    } while(false);

                    synchronized(retval) {
                        retval[0] = true;
                        retval.notify();
                    }
                }
            };
            if(ctx.isRenderThread())
                r.run();
            else
                ctx.queueEvent(r);
            synchronized(retval) {
                while(!retval[0])
                    try {
                        retval.wait();
                    } catch(InterruptedException ignored) {}
            }
            return retval[1];
        }
    }
}
