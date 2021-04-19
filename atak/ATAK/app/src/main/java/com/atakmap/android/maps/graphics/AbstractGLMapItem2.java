
package com.atakmap.android.maps.graphics;

import android.graphics.PointF;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.atakmap.android.maps.MapItem;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.PointD;

public abstract class AbstractGLMapItem2 implements GLMapItem2,
        MapItem.OnVisibleChangedListener, MapItem.OnZOrderChangedListener {

    private Object opaque;
    protected final MapRenderer context;
    protected final MapItem subject;
    protected final int renderPass;
    protected boolean visible;
    protected final MutableGeoBounds bounds;
    protected double zOrder;
    protected double minMapGsd;

    private final Collection<OnBoundsChangedListener> boundsListeners;
    private final Collection<OnVisibleChangedListener> visibleListeners;

    protected AbstractGLMapItem2(MapRenderer context, MapItem subject,
            int renderPass) {
        this.context = context;
        this.subject = subject;
        this.renderPass = renderPass;
        this.bounds = new MutableGeoBounds(0d, 0d, 0d, 0d);

        this.boundsListeners = new ConcurrentLinkedQueue<>();
        this.visibleListeners = new ConcurrentLinkedQueue<>();

        visible = subject.getVisible();
        zOrder = subject.getZOrder();
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
        double terrain = 0d;
        if (ortho.drawTilt > 0d)
            terrain = ortho.getTerrainMeshElevation(lat, lng);

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

        // Z/altitude
        if (ortho.drawTilt > 0d) {
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
        }

        ortho.scene.forward(ortho.scratch.geo, point);
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
