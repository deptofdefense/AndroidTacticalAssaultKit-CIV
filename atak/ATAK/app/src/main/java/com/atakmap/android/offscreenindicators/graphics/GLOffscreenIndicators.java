
package com.atakmap.android.offscreenindicators.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.offscreenindicators.OffscreenIndicatorController;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

public class GLOffscreenIndicators extends
        GLAsynchronousLayer2<OffscreenIndicatorParams> implements
        OffscreenIndicatorController.OnOffscreenIndicatorsThresholdListener,
        OffscreenIndicatorController.OnItemsChangedListener,
        Marker.OnIconChangedListener, PointMapItem.OnPointChangedListener {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // OffscreenIndicatorController : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof OffscreenIndicatorController)
                return GLLayerFactory.adapt(new GLOffscreenIndicators(surface,
                        (OffscreenIndicatorController) layer));
            return null;
        }
    };

    private static final float LINE_WIDTH = (float) Math.max(
            Math.ceil(1f * MapView.DENSITY), 1.0f);
    private static final float OUTLINE_WIDTH = LINE_WIDTH + 2;

    private final MapRenderer renderContext;
    private final OffscreenIndicatorController controller;
    private double threshold;
    private double timeout;

    private final static int OFFSCREEN_INDICATOR_DRAWRATE = 1000;
    private long lastdrawn = 0;

    private final Set<Marker> observed;
    private final LinkedList<GLMapRenderable2> renderable;

    public GLOffscreenIndicators(MapRenderer surface,
            OffscreenIndicatorController controller) {
        super(surface, controller);

        this.renderContext = surface;
        this.controller = controller;

        this.threshold = this.controller.getThreshold();
        this.timeout = this.controller.getTimeout();

        this.observed = new TreeSet<>(MapItem.ZORDER_RENDER_COMPARATOR);
        this.renderable = new LinkedList<>();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public void draw(GLMapView view, int pass) {
        GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_ALWAYS);
        super.draw(view, pass);
        GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);
    }

    @Override
    protected void initImpl(GLMapView view) {
        super.initImpl(view);

        this.controller.addOnOffscreenIndicatorsThresholdListener(this);
        this.controller.addOnItemsChangedListener(this);
    }

    @Override
    public void release() {
        this.controller.removeOnOffscreenIndicatorsThresholdListener(this);
        this.controller.removeOnItemsChangedListener(this);

        super.release();
    }

    @Override
    protected Collection<GLMapRenderable2> getRenderList() {
        return this.renderable;
    }

    @Override
    protected void resetPendingData(OffscreenIndicatorParams pendingData) {
        //pendingData.markers.clear();
    }

    @Override
    protected void releasePendingData(OffscreenIndicatorParams pendingData) {
        //pendingData.markers.clear();
    }

    @Override
    protected OffscreenIndicatorParams createPendingData() {
        return new OffscreenIndicatorParams();
    }

    @Override
    protected String getBackgroundThreadName() {
        return "Offscreen GL worker@" + Integer.toString(this.hashCode(), 16);
    }

    @Override
    protected boolean updateRenderList(
            ViewState state,
            OffscreenIndicatorParams pendingData) {
        Set<Marker> unobserved = new HashSet<>(this.observed);
        this.observed.clear();

        for (Marker m : pendingData.markers) {
            if (!unobserved.remove(m)) {
                m.addOnPointChangedListener(this);
                m.addOnIconChangedListener(this);
            }

            this.observed.add(m);
        }

        for (Marker m : unobserved) {
            m.removeOnPointChangedListener(this);
            m.removeOnIconChangedListener(this);
        }

        if (this.observed.size() > 0) {
            if (this.renderable.size() == 0)
                this.renderable.add(new GLBatchRenderer());
        } else if (this.renderable.size() > 0) {
            final Collection<GLMapRenderable2> released = new LinkedList<>(
                    this.renderable);
            this.renderable.clear();

            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    for (GLMapRenderable2 renderable : released)
                        renderable.release();
                    released.clear();
                }
            });
        }

        return true;
    }

    @Override
    protected void query(ViewState state, OffscreenIndicatorParams result) {
        final long curr = SystemClock.elapsedRealtime();
        if (curr - lastdrawn < OFFSCREEN_INDICATOR_DRAWRATE) {
            // skip drawing 
            return;
        }
        lastdrawn = curr;
        result.markers.clear();

        this.controller.getMarkers(result.markers);

        final GeoPoint viewCenter = new GeoPoint(state.drawLat, state.drawLng);

        Iterator<Marker> iter = result.markers.iterator();
        Marker m;
        double distance;

        if (MapView.getMapView().getSelfMarker() == null)
            return;

        final String teamColor = MapView.getMapView().getSelfMarker()
                .getMetaString("team", "Cyan");

        if (this.checkQueryThreadAbort())
            return;

        while (iter.hasNext()) {
            if (this.checkQueryThreadAbort())
                break;

            m = iter.next();
            String mTeam = m.getMetaString("team", "");
            long interest = m.getMetaLong("offscreen_interest", -1);
            double intTime = timeout + interest;
            long clock = SystemClock.elapsedRealtime();
            if (timeout <= 0d
                    || mTeam.equals(teamColor)
                    || intTime > clock) {
                distance = MapItem.computeDistance(m, viewCenter);
            } else {
                distance = Double.NaN;
            }

            if (Double.isNaN(distance) || distance > this.threshold)
                iter.remove();
        }

        if (this.checkQueryThreadAbort())
            result.markers.clear();
    }

    /**************************************************************************/
    // On Offscreen Indicators Threshold Listener

    @Override
    public void onOffscreenIndicatorsThresholdChanged(
            OffscreenIndicatorController controller) {
        final double t = controller.getThreshold();
        final double to = controller.getTimeout();

        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLOffscreenIndicators.this.threshold = t;
                GLOffscreenIndicators.this.timeout = to;
                GLOffscreenIndicators.this.invalidate();
            }
        });

    }

    /**************************************************************************/
    // On Point Changed Listener

    @Override
    public void onPointChanged(PointMapItem item) {
        // we could perform our threshold culling here, but in environments
        // where many items' points are changing with high frequency it is
        // better to mark invalid and rebuild the list at the highest frequency
        // that we can manage.
        this.invalidateNoSync();
    }

    /**************************************************************************/
    // On Icon Changed Listener

    @Override
    public void onIconChanged(Marker marker) {
        this.invalidateNoSync();
    }

    /**************************************************************************/
    // On Items Changed Listener

    @Override
    public void onItemsChanged(OffscreenIndicatorController controller) {
        this.invalidateNoSync();
    }

    /**************************************************************************/

    private static double distance(final float x, final float y,
            final float x2, final float y2) {
        return Math.sqrt((x - x2) * (x - x2) + (y - y2) * (y - y2));
    }

    /**************************************************************************/

    private class GLBatchRenderer implements GLMapRenderable2 {

        private GLRenderBatch impl;
        private FloatBuffer arcVerts;
        private long arcVertsPointer;
        private final Matrix IDENTITY = Matrix.getIdentity();
        private final Matrix xform = Matrix.getIdentity();

        @Override
        public void draw(GLMapView view, int renderPass) {
            if ((renderPass & GLMapView.RENDER_PASS_SPRITES) == 0)
                return;

            String iconUri = null;
            int iconColor = 0;
            GLImageCache.Entry entry = null;

            final float haloBorderSize = GLOffscreenIndicators.this.controller
                    .getHaloBorderSize();
            final float haloIconSize = GLOffscreenIndicators.this.controller
                    .getHaloIconSize();

            // Create a rectangle around the border of the screen that all icons will fall on
            final RectF innerRect = new RectF(view._left + haloBorderSize,
                    view._bottom + haloBorderSize,
                    view._right - haloBorderSize,
                    view._top - haloBorderSize);

            PointF screenPoint;
            PointF screenCoordsHaloIcon = new PointF();
            PointF markerUL = new PointF();
            PointF markerUR = new PointF();
            PointF markerLR = new PointF();
            PointF markerLL = new PointF();

            final int lineCount = 8;
            final int vertCount = lineCount + 1;

            // instantiate the vertex coords if necessary
            if (this.arcVerts == null) {
                ByteBuffer buf = com.atakmap.lang.Unsafe
                        .allocateDirect(vertCount * 2 * 4);
                buf.order(ByteOrder.nativeOrder());
                this.arcVerts = buf.asFloatBuffer();
                this.arcVertsPointer = Unsafe.getBufferPointer(this.arcVerts);
            }

            if (this.impl == null)
                this.impl = new GLRenderBatch(1024);
            this.impl.begin();

            GeoPoint mgp = GeoPoint.createMutable();
            for (Marker m : GLOffscreenIndicators.this.observed) {
                if (m.getMetaBoolean("disable_offscreen_indicator", false)) {
                    continue;
                }

                mgp.set(m.getPoint());
                if (view.continuousScrollEnabled) {
                    // Test to see if point is closer by going over IDL
                    double lng = mgp.getLongitude();
                    double lng2 = lng + (lng < 0 ? 360 : -360);
                    if (Math.abs(view.currentPass.drawLng - lng2) < Math
                            .abs(view.currentPass.drawLng - lng))
                        mgp.set(mgp.getLatitude(), lng2);
                }

                AbstractGLMapItem2.forward(view, mgp, view.scratch.pointD);
                view.scratch.pointF.x = (float) view.scratch.pointD.x;
                view.scratch.pointF.y = (float) view.scratch.pointD.y;
                screenPoint = view.scratch.pointF;

                if (screenPoint.x >= view.currentPass.left
                        && screenPoint.x <= view.currentPass.right
                        && screenPoint.y >= view.currentPass.bottom
                        && screenPoint.y <= view.currentPass.top)
                    continue;

                screenCoordsHaloIcon.x = screenPoint.x;
                screenCoordsHaloIcon.y = screenPoint.y;

                screenCoordsHaloIcon.x = Math.min(screenCoordsHaloIcon.x,
                        innerRect.right);
                screenCoordsHaloIcon.x = Math.max(screenCoordsHaloIcon.x,
                        innerRect.left);
                screenCoordsHaloIcon.y = Math.min(screenCoordsHaloIcon.y,
                        innerRect.bottom);
                screenCoordsHaloIcon.y = Math.max(screenCoordsHaloIcon.y,
                        innerRect.top);

                // add halo

                final float distance = (float) distance(screenPoint.x,
                        screenPoint.y,
                        screenCoordsHaloIcon.x, screenCoordsHaloIcon.y);

                //item.arc.setRadius(distance);
                float radius = distance;

                // Added by Tim: This math here limits the arc to the exact area displayed for
                // objects off any side, but does overdraw some on the corners (but not nearly as
                // much as the full circle used to)
                float arclen = 90;
                float ang = (float) Math.toDegrees(Math.atan2(screenPoint.y
                        - screenCoordsHaloIcon.y,
                        screenCoordsHaloIcon.x - screenPoint.x));
                arclen = (float) Math.toDegrees(Math
                        .acos((distance - haloBorderSize) / distance));

                //item.arc.setOffsetAngle(ang - arclen);
                final double offsetAngle = (ang - arclen);
                //item.arc.setCentralAngle(arclen * 2);
                final double centralAngle = (arclen * 2);
                // End Tim code
                //item.arc.setPoint(screenPoint.x, screenPoint.y);
                //item.arc.setStrokeColor(setArcColor(item));
                final int arcColor = GLOffscreenIndicators.this.controller
                        .getArcColor(m);

                final double angle = Math.toRadians(offsetAngle);
                final double step = Math.toRadians(centralAngle / lineCount);

                for (int i = 0; i < vertCount; i++) {
                    float px = radius * (float) Math.cos(angle + (i * step));
                    float py = radius * (float) Math.sin(angle + (i * step));
                    Unsafe.setFloats(this.arcVertsPointer + (i * 8),
                            screenPoint.x + px,
                            screenPoint.y - py);
                }

                // outline
                this.impl.addLineStrip(this.arcVerts,
                        OUTLINE_WIDTH,
                        0.0f, 0.0f, 0.0f, 1.0f);

                // arc
                this.impl.addLineStrip(this.arcVerts,
                        LINE_WIDTH,
                        Color.red(arcColor) / 255f,
                        Color.green(arcColor) / 255f,
                        Color.blue(arcColor) / 255f,
                        Color.alpha(arcColor) / 255f);

                // reset entry
                entry = null;

                // add icon
                Icon icon = m.getIcon();
                if (icon != null) {
                    iconUri = m.getIcon().getImageUri(m.getState());
                    iconColor = m.getIcon().getColor(m.getState());
                }

                // Icon override meta string
                iconUri = m.getMetaString("offscreen_icon_uri", iconUri);
                iconColor = m.getMetaInteger("offscreen_icon_color", iconColor);

                // Load from bitmap
                if (iconUri != null) {
                    GLImageCache imageCache = GLRenderGlobals.get(view)
                            .getImageCache();
                    entry = imageCache.tryFetch(iconUri, true);
                    if (entry == null)
                        entry = imageCache.tryFetch(iconUri, false);
                }

                if (entry != null && entry.getTextureId() != 0) {
                    markerUL.x = screenCoordsHaloIcon.x - haloIconSize / 2;
                    markerUL.y = screenCoordsHaloIcon.y + haloIconSize / 2;
                    markerUR.x = screenCoordsHaloIcon.x + haloIconSize / 2;
                    markerUR.y = screenCoordsHaloIcon.y + haloIconSize / 2;
                    markerLR.x = screenCoordsHaloIcon.x + haloIconSize / 2;
                    markerLR.y = screenCoordsHaloIcon.y - haloIconSize / 2;
                    markerLL.x = screenCoordsHaloIcon.x - haloIconSize / 2;
                    markerLL.y = screenCoordsHaloIcon.y - haloIconSize / 2;

                    // rotation
                    if ((m.getStyle()
                            & Marker.STYLE_ROTATE_HEADING_MASK) == Marker.STYLE_ROTATE_HEADING_MASK) {
                        xform.set(IDENTITY);

                        xform.translate(
                                screenCoordsHaloIcon.x, screenCoordsHaloIcon.y,
                                0);
                        xform.rotate(Math
                                .toRadians(view.currentPass.drawRotation
                                        - m.getTrackHeading()));
                        xform.translate(
                                -screenCoordsHaloIcon.x,
                                -screenCoordsHaloIcon.y, 0);

                        view.scratch.pointD.x = markerUL.x;
                        view.scratch.pointD.y = markerUL.y;
                        xform.transform(view.scratch.pointD,
                                view.scratch.pointD);
                        markerUL.x = (float) view.scratch.pointD.x;
                        markerUL.y = (float) view.scratch.pointD.y;

                        view.scratch.pointD.x = markerUR.x;
                        view.scratch.pointD.y = markerUR.y;
                        xform.transform(view.scratch.pointD,
                                view.scratch.pointD);
                        markerUR.x = (float) view.scratch.pointD.x;
                        markerUR.y = (float) view.scratch.pointD.y;

                        view.scratch.pointD.x = markerLR.x;
                        view.scratch.pointD.y = markerLR.y;
                        xform.transform(view.scratch.pointD,
                                view.scratch.pointD);
                        markerLR.x = (float) view.scratch.pointD.x;
                        markerLR.y = (float) view.scratch.pointD.y;

                        view.scratch.pointD.x = markerLL.x;
                        view.scratch.pointD.y = markerLL.y;
                        xform.transform(view.scratch.pointD,
                                view.scratch.pointD);
                        markerLL.x = (float) view.scratch.pointD.x;
                        markerLL.y = (float) view.scratch.pointD.y;
                    }

                    this.impl.addSprite(
                            entry.getTextureId(),
                            markerUL.x,
                            markerUL.y,
                            markerUR.x,
                            markerUR.y,
                            markerLR.x,
                            markerLR.y,
                            markerLL.x,
                            markerLL.y,
                            (float) entry.getImageTextureX()
                                    / (float) entry.getTextureWidth(),
                            (float) (entry.getImageTextureY() + entry
                                    .getImageTextureHeight())
                                    / (float) entry.getTextureHeight(),
                            (float) (entry.getImageTextureX() + entry
                                    .getImageTextureWidth())
                                    / (float) entry.getTextureWidth(),
                            (float) entry.getImageTextureY()
                                    / (float) entry.getTextureHeight(),
                            Color.red(iconColor) / 255f,
                            Color.green(iconColor) / 255f,
                            Color.blue(iconColor) / 255f,
                            Color.alpha(iconColor) / 255f);
                }
            }
            this.impl.end();

        }

        @Override
        public void release() {
            if (this.impl != null)
                this.impl = null;
            if (this.arcVerts != null) {
                this.arcVerts = null;
                this.arcVertsPointer = 0L;
            }
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SPRITES;
        }
    }
}
