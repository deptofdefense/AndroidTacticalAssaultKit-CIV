
package com.atakmap.android.helloworld.heatmap;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.opengl.GLAbstractLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.Visitor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class GLSimpleHeatMapLayer extends GLAbstractLayer implements SimpleHeatMapLayer.OnLayerChangedListener {

    // The GLLayerSpi will automatically create an instance of the renderer when
    // the ExampleLayer is added to the map

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // ExampleLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof SimpleHeatMapLayer))
                return null;
            return new GLSimpleHeatMapLayer(object.first,
                    (SimpleHeatMapLayer) object.second);
        }
    };

    /*************************************************************************/

    private Data frame;
    private final SimpleHeatMapLayer subject;

    public GLSimpleHeatMapLayer(MapRenderer surface, SimpleHeatMapLayer subject) {
        super(surface, subject);
        this.subject = subject;

    }

    @Override
    protected void init() {
        super.init();
        subject.addOnLayerChangedListener(this);
        this.frame = new Data();
        onLayerChanged(subject);

    }
    
    @Override
    protected void drawImpl(GLMapView view) {

        // transform the frame's corner coordinates to GL x,y
        view.forward(frame.points, frame.vertexCoordinates);

        if (frame.texture == null) {
            return;
        }
        frame.texture.draw(4, GLES20FixedPipeline.GL_FLOAT,
                frame.textureCoordinates, frame.vertexCoordinates);
    }

    @Override
    public void release() {
        subject.removeOnLayerChangedListener(this);
        // release all frame textures
        if (this.frame != null && this.frame.texture != null)
            this.frame.texture.release();
        this.frame = null;
        super.release();

    }

    public void setData(int[] argb, final int width, final int height,
            GeoPoint upperLeft,
            GeoPoint upperRight, GeoPoint lowerRight, GeoPoint lowerLeft) {

        // this example makes use of Bitmap, but does not need to.

        final Bitmap bitmap = Bitmap.createBitmap(argb, width, height,
                Bitmap.Config.ARGB_8888);
        final GeoPoint ul = new GeoPoint(upperLeft);
        final GeoPoint ur = new GeoPoint(upperRight);
        final GeoPoint lr = new GeoPoint(lowerRight);
        final GeoPoint ll = new GeoPoint(lowerLeft);

        // offload the actual update to the GL thread -- GL objects may only be
        // updated on the GL thread (e.g. texture).
        this.renderContext.queueEvent(new Runnable() {
            public void run() {
                try {
                    if (frame != null)
                        frame.update(bitmap, width, height, ul, ur,
                                lr, ll);
                } finally {
                    // cleanup the bitmap
                    bitmap.recycle();
                }
            }
        });
    }

    /**************************************************************************/

    private static class Data {
        GLTexture texture;
        final DoubleBuffer points;
        final FloatBuffer vertexCoordinates;
        final ByteBuffer textureCoordinates;

        Data() {
            this.texture = null;
            this.points = ByteBuffer.allocateDirect(8 * 2 * 4)
                    .order(ByteOrder.nativeOrder()).asDoubleBuffer();
            this.vertexCoordinates = ByteBuffer.allocateDirect(4 * 2 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            this.textureCoordinates = ByteBuffer.allocateDirect(4 * 2 * 4)
                    .order(ByteOrder.nativeOrder());
        }

        // XXX - we could use a java.nio.Buffer in place of a bitmap that
        //       contains the pixel data. This may be faster as we won't incur
        //       additional copying during Bitmap construction. The reason
        //       Bitmap was selected is that we can explicitly clean up object
        //       when we are done; use of Buffer relies on GC. As is generally
        //       the case with mobile development we must weigh the options and
        //       experiment to determine the best utilization of resources
        //       versus performance

        void update(Bitmap frame, final int width, final int height,
                final GeoPoint ul, final GeoPoint ur, final GeoPoint lr,
                final GeoPoint ll) {
            // if the bitmap data exceeds the bounds of the texture, allocate a
            // new instance
            if (this.texture == null
                    || (this.texture.getTexWidth() < width || this.texture
                            .getTexHeight() < height)) {
                if (this.texture != null)
                    this.texture.release();
                this.texture = new GLTexture(width, height, frame.getConfig());
            }

            this.texture.load(null, 0, 0, width, height);

            // note that while 'v' originates in the lower-left, by using an
            // upper-left origin we will have the GPU do the vertical flip for
            // us

            // update the texture coordinates to match the size of the new frame
            this.textureCoordinates.clear();
            this.textureCoordinates.putFloat(0.0f); // upper-left
            this.textureCoordinates.putFloat(0.0f);
            this.textureCoordinates.putFloat((float) width
                    / (float) this.texture.getTexWidth()); // upper-right
            this.textureCoordinates.putFloat(0.0f);
            this.textureCoordinates.putFloat((float) width
                    / (float) this.texture.getTexWidth()); // lower-right
            this.textureCoordinates.putFloat((float) height
                    / (float) this.texture.getTexHeight());
            this.textureCoordinates.putFloat(0.0f); // lower-left
            this.textureCoordinates.putFloat((float) height
                    / (float) this.texture.getTexHeight());
            this.textureCoordinates.flip();

            // update the corner coordinates for the frame; pairs are ordered
            // X, Y (longitude, latitude)
            this.points.clear();
            this.points.put(ul.getLongitude());
            this.points.put(ul.getLatitude());
            this.points.put(ur.getLongitude());
            this.points.put(ur.getLatitude());
            this.points.put(lr.getLongitude());
            this.points.put(lr.getLatitude());
            this.points.put(ll.getLongitude());
            this.points.put(ll.getLatitude());
            this.points.flip();

            // upload the bitmap data
            this.texture.load(frame);
        }
    }

    @Override
    public void onLayerChanged(SimpleHeatMapLayer simpleHeatMapLayer) {
        GeoPoint[] pts = subject.getPoints();
        setData(subject.layerARGB, subject.layerWidth, subject.layerHeight,
                pts[0], pts[1], pts[2], pts[3]);

        final SurfaceRendererControl[] ctrl = new SurfaceRendererControl[1];
        renderContext.visitControl(null, new Visitor<SurfaceRendererControl>() {
            @Override
            public void visit(SurfaceRendererControl object) {
                ctrl[0] = object;
            }
        }, SurfaceRendererControl.class);

        GeoBounds bounds = new GeoBounds( pts[0], pts[2]);
        ctrl[0].markDirty(new Envelope(bounds.getWest(), bounds.getSouth(), 0d, bounds.getEast(), bounds.getNorth(), 0d), true);

    }
}
