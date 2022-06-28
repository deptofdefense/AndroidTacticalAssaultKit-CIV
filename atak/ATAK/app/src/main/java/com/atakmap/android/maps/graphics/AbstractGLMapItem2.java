
package com.atakmap.android.maps.graphics;

import android.graphics.PointF;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.atakmap.android.maps.MapItem;

import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.hittest.HitTestable;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.PointD;
import com.atakmap.util.Visitor;

public abstract class AbstractGLMapItem2 implements GLMapItem2, HitTestable,
        MapItem.OnVisibleChangedListener, MapItem.OnZOrderChangedListener,
        MapItem.OnClickableChangedListener, ClampToGroundControl {

    // Default minimum and maximum altitude used for render bounds calculation
    protected static final double DEFAULT_MIN_ALT = -500d;
    protected static final double DEFAULT_MAX_ALT = 9000d;

    private static final String TAG = "AbstractGLMapItem2";

    private Object opaque;
    protected final MapRenderer context;
    protected final MapItem subject;
    protected final int renderPass;
    protected boolean visible;
    protected final MutableGeoBounds bounds;
    protected double zOrder;
    protected boolean clickable;
    protected double minMapGsd;
    private boolean clampToGroundAtNadir;

    private final Collection<OnBoundsChangedListener> boundsListeners;
    private final Collection<OnVisibleChangedListener> visibleListeners;

    protected AbstractGLMapItem2(MapRenderer context, MapItem subject,
            int renderPass) {
        this.context = context;
        this.subject = subject;
        this.renderPass = renderPass;
        this.bounds = new MutableGeoBounds(0d, 0d, 0d, 0d);
        this.clampToGroundAtNadir = false;

        this.boundsListeners = new ConcurrentLinkedQueue<>();
        this.visibleListeners = new ConcurrentLinkedQueue<>();

        visible = subject.getVisible();
        zOrder = subject.getZOrder();
        clickable = subject.getClickable();
    }

    @Override
    public final MapItem getSubject() {
        return this.subject;
    }

    @Override
    public final void setOpaque(Object opaque) {
        this.opaque = opaque;
    }

    @Override
    public final Object getOpaque() {
        return this.opaque;
    }

    @Override
    public void startObserving() {
        this.visible = subject.getVisible();
        this.zOrder = subject.getZOrder();
        this.minMapGsd = subject.getMetaDouble("minMapGsd", Double.MAX_VALUE);

        this.subject.addOnVisibleChangedListener(this);
        this.subject.addOnZOrderChangedListener(this);
        this.subject.addOnClickableChangedListener(this);
    }

    /**
     * Stops observing the subject. This method should unwind any callback listeners registered via
     * {@link #startObserving()}.
     * <P>
     * <B>This method may not be invoked on the GL context thread.</B>
     */
    @Override
    public void stopObserving() {
        this.subject.removeOnVisibleChangedListener(this);
        this.subject.removeOnZOrderChangedListener(this);
        this.subject.removeOnClickableChangedListener(this);
    }

    @Override
    public final int getRenderPass() {
        return this.renderPass;
    }

    @Override
    public void release() {
    }

    @Override
    public final boolean isVisible() {
        return this.visible;
    }

    @Override
    public final double getZOrder() {
        return this.zOrder;
    }

    @Override
    public final double getMinDrawResolution() {
        return this.minMapGsd;
    }

    /**
     * Rendering style modified by the {@link ClampToGroundControl}
     * @param v True if at NADIR the item is clamped to ground
     */
    @Override
    public void setClampToGroundAtNadir(boolean v) {
        this.clampToGroundAtNadir = v;
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return this.clampToGroundAtNadir;
    }

    @Override
    public final void addBoundsListener(OnBoundsChangedListener l) {
        this.boundsListeners.add(l);
    }

    @Override
    public final void removeBoundsListener(OnBoundsChangedListener l) {
        this.boundsListeners.remove(l);
    }

    protected final void dispatchOnBoundsChanged() {
        for (OnBoundsChangedListener l : this.boundsListeners)
            l.onBoundsChanged(this, this.bounds);
    }

    @Override
    public final void addVisibleListener(OnVisibleChangedListener l) {
        this.visibleListeners.add(l);
    }

    @Override
    public final void removeVisibleListener(OnVisibleChangedListener l) {
        this.visibleListeners.remove(l);
    }

    protected final void dispatchOnVisibleChanged() {
        for (OnVisibleChangedListener l : this.visibleListeners)
            l.onVisibleChanged(this, this.visible);
    }

    @Override
    public final void getBounds(MutableGeoBounds bnds) {
        bnds.set(this.bounds);
    }

    @Override
    public final void onVisibleChanged(MapItem item) {
        final boolean v = item.getVisible();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                // this check has to happen inside the post because otherwise,
                // rapidly toggling visiblity false and then true again turns
                // into just a false; the change back to true gets skipped since
                // visible hasn't been changed to false yet when it happens
                if (v != visible) {
                    visible = v;
                    dispatchOnVisibleChanged();
                }
            }
        });
    }

    @Override
    public final void onZOrderChanged(MapItem item) {
        final double z = item.getZOrder();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                zOrder = z;
            }
        });
    }

    @Override
    public void onClickableChanged(MapItem item) {
        final boolean v = item.getClickable();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                clickable = v;
            }
        });
    }

    /**
     * Hit test control implementation that checks touchability
     * Sub-classes should override the {@link #hitTestImpl(MapRenderer3, HitTestQueryParameters)} method
     *
     * @param renderer GL instance of the map view
     * @param params Query parameters
     */
    @Override
    public final HitTestResult hitTest(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        return getClickable() && params.acceptsResult(getSubject())
                ? hitTestImpl(renderer, params)
                : null;
    }

    /**
     * Determine whether or not this item is touchable
     * By default this is tied to {@link MapItem#getClickable()}
     *
     * @return True if touchable
     */
    protected boolean getClickable() {
        return clickable;
    }

    /**
     * Perform a hit test on this map item
     * Sub-classes should override this method for hit-testing
     *
     * @param renderer Map renderer
     * @param params Query parameters
     * @return Hit test result or null if not hit
     */
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        return null;
    }

    /**
     * Run a method on the GL thread
     * Unlike calling {@link MapRenderer#queueEvent} directly, this will
     * run the method immediately if we're already on the GL thread
     * @param r Runnable
     */
    protected void runOnGLThread(Runnable r) {
        if (context.isRenderThread())
            r.run();
        else
            context.queueEvent(r);
    }

    protected final void markSurfaceDirty(final boolean realtime) {
        final SurfaceRendererControl[] ctrl = new SurfaceRendererControl[1];
        if (context instanceof MapRenderer3) {
            ctrl[0] = ((MapRenderer3) context)
                    .getControl(SurfaceRendererControl.class);
        } else {
            context.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    ctrl[0] = object;
                }
            }, SurfaceRendererControl.class);
        }
        if (ctrl[0] != null) {
            if (realtime)
                ctrl[0].markDirty(
                        new Envelope(bounds.getWest(), bounds.getSouth(), 0d,
                                bounds.getEast(), bounds.getNorth(), 0d),
                        realtime);
            else
                ctrl[0].markDirty();
        }
    }

    /**
     * Transforms the specified coordinate, applying any elevation adjustments
     * (e.g. exaggeration/offset) that is used by the renderer. The value is
     * returned via <code>point</code>.
     * 
     * <P>If <code>gp</code> does not have a valid altitude, or its altitude is
     * below the terrain surface, the terrain surface value will be used.
     * 
     * @param ortho         The view
     * @param gp            The LLA coordinate
     * @param point         Returns the x,y,z for the coordinate
     * @param unwrap       Longitudinal unwrap value (360, -360, or 0 to ignore)
     */
    public static void forward(GLMapView ortho, GeoPoint gp, PointD point,
            double unwrap) {
        final double lat = gp.getLatitude();
        final double lng = gp.getLongitude();

        // Z/altitude
        double terrain = ortho.getTerrainMeshElevation(lat, lng);

        forward(ortho, gp, point, unwrap, terrain);
    }

    /**
     * Transforms the specified coordinate, applying any elevation adjustments
     * (e.g. exaggeration/offset) that is used by the renderer. The value is
     * returned via <code>point</code>.
     *
     * <P>If <code>gp</code> does not have a valid altitude, or its altitude is
     * below the terrain surface, the terrain surface value will be used.
     *
     * @param ortho         The view
     * @param gp            The LLA coordinate
     * @param point         Returns the x,y,z for the coordinate
     * @param unwrap        Longitudinal unwrap value (360, -360, or 0 to ignore)
     * @param terrain       The local terrain value at the location
     * @param clampSubAlt   True to clamp sub-terrain altitude to the ground
     */
    public static void forward(GLMapView ortho, GeoPoint gp, PointD point,
            double unwrap, double terrain, boolean clampSubAlt) {

        // capture here as `gp==ortho.scratch.geo` will get blown away below
        final double lat = gp.getLatitude();
        final double lng = gp.getLongitude();
        final double alt = gp.getAltitude();
        final boolean altValid = gp.isAltitudeValid();
        final AltitudeReference altRef = gp.getAltitudeReference();

        // adjust altitude
        double lon = lng + (unwrap > 0 && lng < 0
                || unwrap < 0 && lng > 0 ? unwrap : 0);
        ortho.scratch.geo.set(lat, lon, 0d);

        // Z/altitude -- always needs to be populated with perspective camera
        if (ortho.currentPass.scene.camera.perspective
                || ortho.currentPass.drawTilt > 0d) {
            double adjustedAlt = alt;
            if (altValid) {
                switch (altRef) {
                    case HAE:
                        // expect HAE; done
                        break;
                    case AGL:
                        // apply terrain
                        adjustedAlt += terrain;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                // if the explicitly specified altitude is below the terrain,
                // float above and annotate appropriately
                if (alt < terrain && clampSubAlt)
                    adjustedAlt = terrain;
            } else {
                adjustedAlt = terrain;
            }

            // note: always NaN if source alt is NaN
            adjustedAlt = (adjustedAlt + GLMapView.elevationOffset)
                    * ortho.elevationScaleFactor;

            ortho.scratch.geo.set(lat, lng, adjustedAlt);
        } else {
            ortho.scratch.geo.set(terrain);
        }

        ortho.currentPass.scene.forward(ortho.scratch.geo, point);
    }

    public static void forward(GLMapView ortho, GeoPoint gp, PointD point,
            double unwrap, double terrain) {
        forward(ortho, gp, point, unwrap, terrain, true);
    }

    public static void forward(GLMapView ortho, GeoPoint gp, PointD point) {
        forward(ortho, gp, point, 0);
    }

    public static void forward(GLMapView ortho, GeoPoint gp, PointF retval,
            double unwrap) {
        double lng = gp.getLongitude();
        if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0) {
            lng += unwrap;
            ortho.scratch.geo.set(gp.getLatitude(),
                    lng,
                    gp.getAltitude(),
                    gp.getAltitudeReference(),
                    Double.NaN,
                    Double.NaN);
        } else if (ortho.scratch.geo != gp) {
            ortho.scratch.geo.set(gp);
        }
        ortho.forward(ortho.scratch.geo, retval);
    }

    public static void forward(GLMapView ortho, Buffer pBuf,
            FloatBuffer vBuf, GeoBounds bounds) {
        forward(ortho, pBuf, 2, vBuf, 2, bounds);
    }

    public static void forward(GLMapView ortho, Buffer pBuf,
            int srcSize,
            FloatBuffer vBuf,
            int dstSize,
            GeoBounds bounds) {

        int size = 0;
        if (pBuf instanceof FloatBuffer)
            size = 4;
        else if (pBuf instanceof DoubleBuffer)
            size = 8;
        if (size == 0)
            return;
        vBuf.limit(pBuf.limit() / srcSize * dstSize);
        double unwrap = ortho.idlHelper.getUnwrap(bounds);
        if (Double.compare(unwrap, 0) == 0) {
            if (size == 4)
                ortho.forward((FloatBuffer) pBuf, srcSize, vBuf, dstSize);
            else
                ortho.forward((DoubleBuffer) pBuf, srcSize, vBuf, dstSize);
        } else if (srcSize == 2 && dstSize == 2) {
            long pointsPtr = Unsafe.getBufferPointer(pBuf);
            long vertsPtr = Unsafe.getBufferPointer(vBuf);
            for (int i = 0; i < pBuf.limit() / srcSize; i++) {
                double lat, lng, hae;
                hae = 0d;
                if (size == 4) {
                    lat = Unsafe.getFloat(pointsPtr + size);
                    lng = Unsafe.getFloat(pointsPtr);
                } else {
                    lat = Unsafe.getDouble(pointsPtr + size);
                    lng = Unsafe.getDouble(pointsPtr);
                }
                if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0)
                    lng += unwrap;

                pointsPtr += size * srcSize;

                ortho.scratch.geo.set(lat, lng, hae);
                ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);

                Unsafe.setFloats(vertsPtr, ortho.scratch.pointF.x,
                        ortho.scratch.pointF.y);
                vertsPtr += 4 * dstSize;
            }
        } else if (srcSize == 3 && dstSize == 2) {
            long pointsPtr = Unsafe.getBufferPointer(pBuf);
            long vertsPtr = Unsafe.getBufferPointer(vBuf);
            for (int i = 0; i < pBuf.limit() / srcSize; i++) {
                double lat, lng, hae;
                if (size == 4) {
                    lat = Unsafe.getFloat(pointsPtr + size);
                    lng = Unsafe.getFloat(pointsPtr);
                    hae = Unsafe.getFloat(pointsPtr + 2 * size);
                } else {
                    lat = Unsafe.getDouble(pointsPtr + size);
                    lng = Unsafe.getDouble(pointsPtr);
                    hae = Unsafe.getFloat(pointsPtr + 2 * size);
                }
                if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0)
                    lng += unwrap;

                pointsPtr += size * srcSize;

                ortho.scratch.geo.set(lat, lng, hae);
                ortho.forward(ortho.scratch.geo, ortho.scratch.pointF);

                Unsafe.setFloats(vertsPtr, ortho.scratch.pointF.x,
                        ortho.scratch.pointF.y);
                vertsPtr += 4 * dstSize;
            }
        } else if (srcSize == 2 && dstSize == 3) {
            long pointsPtr = Unsafe.getBufferPointer(pBuf);
            long vertsPtr = Unsafe.getBufferPointer(vBuf);
            for (int i = 0; i < pBuf.limit() / srcSize; i++) {
                double lat, lng, hae;
                hae = 0d;
                if (size == 4) {
                    lat = Unsafe.getFloat(pointsPtr + size);
                    lng = Unsafe.getFloat(pointsPtr);
                } else {
                    lat = Unsafe.getDouble(pointsPtr + size);
                    lng = Unsafe.getDouble(pointsPtr);
                }
                if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0)
                    lng += unwrap;

                pointsPtr += size * srcSize;

                ortho.scratch.geo.set(lat, lng, hae);
                ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);

                Unsafe.setFloats(vertsPtr,
                        (float) ortho.scratch.pointD.x,
                        (float) ortho.scratch.pointD.y,
                        (float) ortho.scratch.pointD.z);
                vertsPtr += 4 * dstSize;
            }
        } else if (srcSize == 3 && dstSize == 3) {
            long pointsPtr = Unsafe.getBufferPointer(pBuf);
            long vertsPtr = Unsafe.getBufferPointer(vBuf);
            for (int i = 0; i < pBuf.limit() / srcSize; i++) {
                double lat, lng, hae;
                if (size == 4) {
                    lat = Unsafe.getFloat(pointsPtr + size);
                    lng = Unsafe.getFloat(pointsPtr);
                } else {
                    lat = Unsafe.getDouble(pointsPtr + size);
                    lng = Unsafe.getDouble(pointsPtr);
                }
                hae = Unsafe.getFloat(pointsPtr + 2 * size);
                if (unwrap > 0 && lng < 0 || unwrap < 0 && lng > 0)
                    lng += unwrap;

                pointsPtr += size * srcSize;

                ortho.scratch.geo.set(lat, lng, hae);
                ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);

                Unsafe.setFloats(vertsPtr,
                        (float) ortho.scratch.pointD.x,
                        (float) ortho.scratch.pointD.y,
                        (float) ortho.scratch.pointD.z);
                vertsPtr += 4 * dstSize;
            }
        } else {
            throw new IllegalArgumentException();
        }
    }
}
