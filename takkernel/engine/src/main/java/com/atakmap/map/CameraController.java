package com.atakmap.map;

import com.atakmap.annotations.IncubatingApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.Interop;
import com.atakmap.interop.Pointer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.Plane;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.map.IMapRendererEnums;

/**
 * Utility methods for effecting camera motion. Methods are divided into
 * category by <I>interactive</I> and <I>programmatic</I>. <I>Interactive</I>
 * methods are those that are intended to support motions initiated by user
 * interaction. <I>Programmatic</I> methods are generally convenience methods
 * accept a subset of parameters for
 * {@link MapRenderer3#lookAt(GeoPoint, double, double, double, boolean)}.
 *
 * <P>The following implementation details should be well understood:
 * <UL>
 *     <LI>All methods that accept {@link GeoPoint} will respect altitude.
 *     <LI>Nominal resolution is always interpreted with respect to focus point
 *     altitude to facilitate constant camera offset for non-zoom operations.
 *     GSD relative camera range is computed from the viewport parameters;
 *     range from the focus point is computed by subtracting focus point
 *     altitude.
 * </UL>
 */
@DontObfuscate
public final class CameraController {
    private final static int COLLIDE_IGNORE = 0;
    private final static int COLLIDE_ADJUST_CAMERA = 1;
    private final static int COLLIDE_ADJUST_FOCUS = 2;
    private final static int COLLIDE_ABORT = 3;

    private final static Interop<MapSceneModel> MapSceneModel_interop = Interop.findInterop(MapSceneModel.class);
    private final static Interop<MapRenderer3> MapRenderer_interop = new MapRendererInteropImpl();
    private final static Interop<GeometryModel> GeometryModel_interop = Interop.findInterop(GeometryModel.class);

    private final static String TAG = "CameraController";

    public final static class Interactive {
        /**
         * Pan the map a given number of pixels.
         *
         * @param tx        Horizontal pixels to pan
         * @param ty        Vertical pixels to pan
         * @param animate   Pan smoothly if true; immediately if false
         */
        public static void panBy(MapRenderer3 renderer, float tx, float ty, MapRenderer3.CameraCollision collide, boolean animate) {
            CameraController.panBy(MapRenderer_interop.getPointer(renderer), tx, ty, marshalCollide(collide), animate);
        }

        /**
         * Pans the specified location to the specified offset within the
         * viewport. Screen position is interpreted as upper-left origin
         * @param renderer
         * @param focus
         * @param x
         * @param y
         * @param animate
         */
        public static void panTo(MapRenderer3 renderer, GeoPoint focus, float x, float y, MapRenderer3.CameraCollision collide, boolean animate) {
            CameraController.panTo(MapRenderer_interop.getPointer(renderer), focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), x, y, marshalCollide(collide), animate);
        }

        public static void zoomBy(MapRenderer3 renderer, double scaleFactor, MapRenderer3.CameraCollision collide, boolean animate) {
            CameraController.zoomBy(MapRenderer_interop.getPointer(renderer), scaleFactor, marshalCollide(collide), animate);
        }

        public static void zoomBy(MapRenderer3 renderer, double scaleFactor, GeoPoint focus, float focusx, float focusy, MapRenderer3.CameraCollision collide, boolean animate) {
            CameraController.zoomBy(MapRenderer_interop.getPointer(renderer), scaleFactor, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), focusx, focusy, marshalCollide(collide), animate);
        }

        /**
         * Zooms the current scene to the specified resolution. The provided
         * focus location will be positioned at the specified screenspace
         * location on completion of the motion.
         *
         * @param renderer
         * @param gsd
         * @param focus
         * @param focusx
         * @param focusy
         * @param collide
         * @param animate
         */
        public static void zoomTo(MapRenderer3 renderer, double gsd, GeoPoint focus, float focusx, float focusy, MapRenderer3.CameraCollision collide, boolean animate) {
            CameraController.zoomTo(MapRenderer_interop.getPointer(renderer), gsd, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), focusx, focusy, marshalCollide(collide), animate);
        }

        public static void rotateBy(MapRenderer3 renderer,
                                    double theta,
                                    GeoPoint focus,
                                    MapRenderer3.CameraCollision collide,
                                    boolean animate) {

            CameraController.rotateBy(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), marshalCollide(collide), animate);
        }

        public static void rotateTo(MapRenderer3 renderer,
                                    double theta,
                                    GeoPoint focus,
                                    float focusx,
                                    float focusy,
                                    MapRenderer3.CameraCollision collide,
                                    boolean animate) {

            CameraController.rotateTo(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), focusx, focusy, marshalCollide(collide), animate);
        }

        public static void tiltBy(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            CameraController.tiltBy(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), marshalCollide(collide), animate);
        }

        public static void tiltBy(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  float focusx,
                                  float focusy,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            CameraController.tiltBy(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), focusx, focusy, marshalCollide(collide), animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  float focusx,
                                  float focusy,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            CameraController.tiltTo(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), focusx, focusy, marshalCollide(collide), animate);
        }
    }

    public final static class Programmatic {
        /**
         * Pans the map to the specified location as the new focus point.
         * Rotation, tilt and zoom are preserved.
         * @param renderer
         * @param focus
         * @param animate
         */
        public static void panTo(MapRenderer3 renderer, GeoPoint focus, boolean animate) {
            CameraController.panTo(MapRenderer_interop.getPointer(renderer), focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), animate);
        }
        /**
         * Sets the map to the specified rotation
         *
         * @param rotation  The new rotation of the map
         * @param animate   Rotate smoothly if true; immediately if false
         */
        public static void rotateTo (MapRenderer3 renderer,
                                     double rotation,
                                     boolean animate) {

            CameraController.rotateTo(MapRenderer_interop.getPointer(renderer), rotation, animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double tilt,
                                  boolean animate) {
            CameraController.tiltTo(MapRenderer_interop.getPointer(renderer), tilt, animate);
        }

        public static void zoomTo(MapRenderer3 renderer, double gsd, boolean animate) {
            CameraController.zoomTo(MapRenderer_interop.getPointer(renderer), gsd, animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  boolean animate) {

            CameraController.tiltTo(MapRenderer_interop.getPointer(renderer), theta, focus.getLatitude(), focus.getLongitude(), focus.getAltitude(), animate);
        }
    }

    public static class Util {
        /**
         * Computes the relative density ratio between the pixel at the
         * current focus and specific screen location. Pixel densities are
         * computed on the plane tangent to the focus point.
         */
        public static double computeRelativeDensityRatio(MapSceneModel sm, float x, float y) {
            return CameraController.computeRelativeDensityRatio(MapSceneModel_interop.getPointer(sm), x, y);
        }

        /**
         * Creates a tangent plane at the specified focus point.
         *
         * @param sm
         * @param focus
         * @return
         */
        public static Plane createTangentPlane(MapSceneModel sm, GeoPoint focus) {
            return (Plane)GeometryModel_interop.create(CameraController.createTangentPlane(MapSceneModel_interop.getPointer(sm), focus.getLatitude(), focus.getLongitude(), focus.getAltitude()));
        }

        public static GeometryModel createFocusAltitudeModel(MapSceneModel sm, GeoPoint focus) {
            return GeometryModel_interop.create(CameraController.createFocusAltitudeModel(MapSceneModel_interop.getPointer(sm), focus.getLatitude(), focus.getLongitude(), focus.getAltitude()));
        }
    }
    
    private static int marshalCollide(IMapRendererEnums.CameraCollision collide) {
        switch(collide) {
            case Abort:
                return COLLIDE_ABORT;
            case AdjustCamera:
                return COLLIDE_ADJUST_CAMERA;
            case AdjustFocus:
                return COLLIDE_ADJUST_FOCUS;
            case Ignore:
            default :
                return COLLIDE_IGNORE;
        }
    }

    // Interactive
    static native void panBy(long rendererPtr, float tx, float ty, int collide, boolean animate);
    static native void panTo(long rendererPtr, double focusLat, double focusLng, double focusAlt, float x, float y, int collide, boolean animate);
    static native void zoomBy(long rendererPtr, double scaleFactor, int collide, boolean animate);
    static native void zoomBy(long rendererPtr, double scaleFactor, double focusLat, double focusLng, double focusAlt, float focusx, float focusy, int collide, boolean animate);
    static native void zoomTo(long rendererPtr, double gsd, double focusLat, double focusLng, double focusAlt, float focusx, float focusy, int collide, boolean animate);
    static native void rotateBy(long rendererPtr,
                                double theta,
                                double focusLat, double focusLng, double focusAlt,
                                int collide,
                                boolean animate);
    static native void rotateTo(long rendererPtr,
                                double theta,
                                double focusLat, double focusLng, double focusAlt,
                                float focusx,
                                float focusy,
                                int collide,
                                boolean animate);

    static native void tiltBy(long rendererPtr,
                              double theta,
                              double focusLat, double focusLng, double focusAlt,
                              int collide,
                              boolean animate);

    static native void tiltBy(long rendererPtr,
                              double theta,
                              double focusLat, double focusLng, double focusAlt,
                              float focusx,
                              float focusy,
                              int collide,
                              boolean animate);

    static native void tiltTo(long rendererPtr,
                              double theta,
                              double focusLat, double focusLng, double focusAlt,
                              float focusx,
                              float focusy,
                              int collide,
                              boolean animate);

    // Programmatic
    static native void panTo(long rendererPtr, double focusLat, double focusLng, double focusAlt, boolean animate);
    static native void rotateTo (long rendererPtr,
                                 double rotation,
                                 boolean animate);
    static native void tiltTo(long rendererPtr,
                                  double tilt,
                                  boolean animate);
    static native void zoomTo(long rendererPtr, double gsd, boolean animate);
    static native void tiltTo(long rendererPtr,
                              double theta,
                              double focusLat, double focusLng, double focusAlt,
                              boolean animate);

    // Util
    static native double computeRelativeDensityRatio(long sceneModel, float x, float y);
    static native Pointer createTangentPlane(long sceneModel, double focusLat, double focusLng, double focusAlt);
    static native Pointer createFocusAltitudeModel(long sceneModel, double focusLat, double focusLng, double focusAlt);

    final static class MapRendererInteropImpl extends Interop<MapRenderer3> {
        Interop<GLMapView> glmapview = Interop.findInterop(GLMapView.class);
        Interop<MapRenderer3> base = Interop.findInterop(MapRenderer3.class);

        @Override
        public long getPointer(MapRenderer3 obj) {
            if(obj instanceof GLMapView && glmapview != null) {
                final long ptr = glmapview.castToPointer(MapRenderer3.class, (GLMapView)obj);
                if(ptr != 0L)
                    return ptr;
            }

            if(base != null) {
                return base.getPointer(obj);
            }

            return 0L;
        }

        @Override
        public MapRenderer3 create(Pointer pointer, Object owner) {
            return null;
        }

        @Override
        public Pointer clone(long pointer) {
            return null;
        }

        @Override
        public Pointer wrap(MapRenderer3 object) {
            return null;
        }

        @Override
        public void destruct(Pointer pointer) {

        }

        @Override
        public boolean hasObject(long pointer) {
            return false;
        }

        @Override
        public MapRenderer3 getObject(long pointer) {
            return null;
        }

        @Override
        public boolean hasPointer(MapRenderer3 object) {
            return false;
        }

        @Override
        public boolean supportsWrap() {
            return false;
        }

        @Override
        public boolean supportsClone() {
            return false;
        }

        @Override
        public boolean supportsCreate() {
            return false;
        }
    }
}
