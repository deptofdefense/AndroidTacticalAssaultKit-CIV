
package com.atakmap.android.video.overlay;

import android.graphics.SurfaceTexture;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Pair;

import com.atakmap.android.video.VideoOverlayLayer;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.opengl.GLAbstractLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.Visitor;
import com.partech.mobilevid.*;
import com.partech.mobilevid.gl.*;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class GLVideoOverlayLayer extends GLAbstractLayer implements
        VideoOverlayLayer.VideoFrameListener {

    private final static String TAG = "GLVideoOverlayLayer";

    // The GLLayerSpi will automatically create an instance of the renderer when
    // the VideoOverlayLayer is added to the map

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // VideoOverlayLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof VideoOverlayLayer))
                return null;
            return new GLVideoOverlayLayer(object.first,
                    (VideoOverlayLayer) object.second);
        }
    };

    /*************************************************************************/

    private final DoubleBuffer curPoints;
    private final FloatBuffer vertexCoordinates;
    private final FloatBuffer textureCoordinates;
    private DatasetProjection2 proj;
    private SurfaceTexture surfaceTexture;
    private SurfaceTextureHandler textureHandler;
    private final float[] texTransform;
    private TexturedRectProgram program;
    private boolean frameIsUpdating;

    private int sourceWidth;
    private int sourceHeight;
    private boolean updateSourceSizes;
    private final float[] mvandp = new float[16 * 2];
    private final float[] mvp = new float[16];

    private Envelope frameMbb;
    private SurfaceRendererControl surfaceCtrl;

    private GLVideoOverlayLayer(final MapRenderer surface,
            final VideoOverlayLayer subject) {
        super(surface, subject);
        textureHandler = new SurfaceTextureHandler(getClass().getName());
        this.curPoints = Unsafe.allocateDirect(6 * 2, DoubleBuffer.class);
        this.vertexCoordinates = Unsafe.allocateDirect(6 * 2,
                FloatBuffer.class);
        textureCoordinates = Unsafe.allocateDirect(6 * 2,
                FloatBuffer.class);
        texTransform = new float[16];
        proj = null;
        frameMbb = null;
        if (surface instanceof MapRenderer3)
            surfaceCtrl = ((MapRenderer3) surface)
                    .getControl(SurfaceRendererControl.class);
        else
            surface.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    surfaceCtrl = object;
                }
            }, SurfaceRendererControl.class);
    }

    @Override
    public void start() {
        super.start();

        ((VideoOverlayLayer) this.subject).setVideoFrameListener(this);
    }

    @Override
    public void stop() {
        // clear the frame listener
        ((VideoOverlayLayer) this.subject).setVideoFrameListener(null);

        super.stop();
    }

    @Override
    protected void init() {
        super.init();

        // clear any pending errors - body intentionally empty
        //noinspection StatementWithEmptyBody
        while (GLES20FixedPipeline
                .glGetError() != GLES20FixedPipeline.GL_NO_ERROR) {
        }

        try {
            program = new TexturedRectProgram(true);
            synchronized (this) {
                textureHandler.initFromCurrentContext(sourceWidth,
                        sourceHeight);
                updateSourceSizes = false;
            }
            surfaceTexture = textureHandler.getSurfaceTexture();
            ((VideoOverlayLayer) this.subject)
                    .surfaceTextureReady(textureHandler.getSurfaceTexture());
        } catch (GLOperationException ex) {
            program = null;
            Log.e(getClass().getName(), "Could not create texture", ex);
        }

        this.frameIsUpdating = false;
    }

    @Override
    protected void drawImpl(GLMapView view) {
        // draw the frame
        final int vertSize = 2;
        final int numVertices = textureCoordinates.remaining()
                / vertSize;

        if (surfaceTexture == null || !curPoints.hasRemaining())
            return;
        // clear any pending errors - body intentionally empty
        //noinspection StatementWithEmptyBody
        while (GLES20FixedPipeline
                .glGetError() != GLES20FixedPipeline.GL_NO_ERROR) {
        }

        // transform the frame's corner coordinates to GL x,y
        vertexCoordinates.limit(curPoints.remaining());
        view.forward(curPoints, vertexCoordinates);

        // draw the frame
        try {
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW,
                    mvandp, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION,
                    mvandp, 16);
            Matrix.multiplyMM(mvp, 0, mvandp, 0, mvandp, 16);
            program.draw(GLES30.GL_TRIANGLE_FAN, numVertices,
                    vertexCoordinates, textureCoordinates,
                    mvp, texTransform,
                    textureHandler.getAdjustedSourceWidth(),
                    textureHandler.getAdjustedSourceHeight(),
                    textureHandler.getTexId());
        } catch (GLOperationException ex) {
            Log.e(getClass().getName(), "Failed to draw frame", ex);
        }
    }

    @Override
    public void release() {
        // release all frame textures
        textureHandler.release();
        textureHandler = null;
        sourceWidth = sourceHeight = 0;

        frameMbb = null;

        super.release();
    }

    /**************************************************************************/
    // VideoOverlayLayer VideoFrameListener

    public synchronized void videoFrameSizeChange(int w, int h) {
        if (sourceWidth != w || sourceHeight != h) {
            sourceWidth = w;
            sourceHeight = h;
            updateSourceSizes = true;
        }
    }

    @Override
    public void videoFrame(GeoPoint upperLeft,
            GeoPoint upperRight, GeoPoint lowerRight, GeoPoint lowerLeft) {

        if (this.frameIsUpdating)
            return;

        this.frameIsUpdating = true;

        // create copies of the objects we plan to offload to the GL thread

        final GeoPoint ul = new GeoPoint(upperLeft);
        final GeoPoint ur = new GeoPoint(upperRight);
        final GeoPoint lr = new GeoPoint(lowerRight);
        final GeoPoint ll = new GeoPoint(lowerLeft);

        // offload the actual update to the GL thread -- GL objects may only be
        // updated on the GL thread (e.g. texture).
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                try {
                    int w, h;
                    synchronized (this) {
                        w = sourceWidth;
                        h = sourceHeight;
                    }

                    try {
                        if (updateSourceSizes) {
                            textureHandler.adjustTexBufSize(sourceWidth,
                                    sourceHeight);
                            sourceWidth = textureHandler
                                    .getAdjustedSourceWidth();
                            sourceHeight = textureHandler
                                    .getAdjustedSourceHeight();

                            updateSourceSizes = false;
                        }
                        textureHandler.getSurfaceTexture().updateTexImage();
                        textureHandler.getSurfaceTexture()
                                .getTransformMatrix(texTransform);
                    } catch (RuntimeException ex) {
                        // This is here because renderContext.queueEvent() does *not* always
                        // have a current GL context (even though it does most of the time)
                        // and thus the GL calls can fail;  updateTexImage() throws
                        // when it fails.
                        // Updating could potentially be moved to drawImpl(), which will always
                        // have a current context, but would need to flag
                        // need for texture upload versus just redrawing existing texture.
                    }

                    // update the corner coordinates for the frame; pairs are ordered
                    // X, Y (longitude, latitude)
                    fillVideoPoints(w, h, ul, ur, lr, ll);

                    if (surfaceCtrl != null) {
                        // mark old MBB dirty
                        if (frameMbb != null)
                            surfaceCtrl.markDirty(frameMbb, true);
                        else
                            frameMbb = new Envelope(0d, 0d, 0d, 0d, 0d, 0d);
                        // update MBB
                        frameMbb.minX = MathUtils.min(ul.getLongitude(),
                                ur.getLongitude(), lr.getLongitude(),
                                ll.getLongitude());
                        frameMbb.minY = MathUtils.min(ul.getLatitude(),
                                ur.getLatitude(), lr.getLatitude(),
                                ll.getLatitude());
                        frameMbb.maxX = MathUtils.min(ul.getLongitude(),
                                ur.getLongitude(), lr.getLongitude(),
                                ll.getLongitude());
                        frameMbb.maxY = MathUtils.min(ul.getLatitude(),
                                ur.getLatitude(), lr.getLatitude(),
                                ll.getLatitude());
                        // mark new MBB dirty
                        surfaceCtrl.markDirty(frameMbb, true);
                    }
                } finally {
                    frameIsUpdating = false;
                }
            }
        });
    }

    /**************************************************************************/

    private void fillVideoPoints(final int width, final int height,
            final GeoPoint ul, final GeoPoint ur,
            final GeoPoint lr, final GeoPoint ll) {
        this.proj = null;
        try {
            this.proj = new DefaultDatasetProjection2(4326, width, height,
                    ul, ur, lr, ll);
        } catch (Throwable t) {
            Log.w(TAG,
                    "Unable to construct video frame projective transform {w="
                            + width + ",h=" + height + ",ul=" + ul + ",ur="
                            + ur + ",lr=" + lr + ",ll=" + ll);
        }

        GeoPoint geo = GeoPoint.createMutable();
        PointD img = new PointD(0d, 0d, 0d);

        // set up the points -- if 'proj' is non-null, we will create a
        // triangle fan with a point in the center, otherwise, we will
        // create a triangle fan starting at the upper-left. because GL
        // will be rendering the quad as triangles, significant skew can
        // appear if the geometry is only two triangles.

        // NOTE: points are ordered X,Y (longitude, latitude) pairs
        // NOTE2: texture coordinates are also X,Y pairs
        // NOTE3: texture coordinates use standard OGL origin of lower-left
        //        of image. For video frames, this is *actually* the lower-left
        //        (not upper-left!)

        this.curPoints.clear();
        this.textureCoordinates.clear();
        if (proj != null) {
            try {
                // center
                img.x = width / 2d;
                img.y = height / 2d;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put((float) img.x / (float) width);
                this.textureCoordinates
                        .put((float) img.y / (float) height);
                // UL
                img.x = 0d;
                img.y = 0d;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put(0);
                this.textureCoordinates
                        .put(1.0f);
                // UR
                img.x = width;
                img.y = 0d;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put(1.0f);
                this.textureCoordinates
                        .put(1.0f);
                // LR
                img.x = width;
                img.y = height;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put(1.0f);
                this.textureCoordinates
                        .put(0);
                // LL
                img.x = 0d;
                img.y = height;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put(0);
                this.textureCoordinates
                        .put(0);
                // UL
                img.x = 0d;
                img.y = 0d;
                proj.imageToGround(img, geo);
                this.curPoints.put(geo.getLongitude());
                this.curPoints.put(geo.getLatitude());
                this.textureCoordinates
                        .put(0);
                this.textureCoordinates
                        .put(1.0f);
            } catch (RuntimeException ex) {
                Log.w(TAG, "Failed to transform frame corners for display", ex);
                curPoints.clear();
                textureCoordinates.clear();
            }
        } else {
            // some generic quad that could not be projective mapped

            // UL
            this.curPoints.put(ul.getLongitude());
            this.curPoints.put(ul.getLatitude());
            this.textureCoordinates
                    .put(0);
            this.textureCoordinates
                    .put(1.0f);
            // UR
            this.curPoints.put(ur.getLongitude());
            this.curPoints.put(ur.getLatitude());
            this.textureCoordinates
                    .put(1.0f);
            this.textureCoordinates
                    .put(1.0f);
            // LR
            this.curPoints.put(lr.getLongitude());
            this.curPoints.put(lr.getLatitude());
            this.textureCoordinates
                    .put(1.0f);
            this.textureCoordinates
                    .put(0);
            // LL
            this.curPoints.put(ll.getLongitude());
            this.curPoints.put(ll.getLatitude());
            this.textureCoordinates
                    .put(0);
            this.textureCoordinates
                    .put(0);
        }
        this.curPoints.flip();
        this.textureCoordinates.flip();
    }

}
