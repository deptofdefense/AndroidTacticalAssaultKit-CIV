package com.atakmap.map;

import android.graphics.PointF;

import com.atakmap.annotations.IncubatingApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.Ellipsoid;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.Plane;
import com.atakmap.math.PointD;
import com.atakmap.math.Sphere;
import com.atakmap.math.Vector3D;

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
@IncubatingApi(since = "4.3")
public final class CameraController {
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
            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            panByImpl(currentModel, tx, ty, animate, new RendererCamera(renderer));
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
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            if(!sm.camera.perspective) {
                panToImpl_ortho(sm, focus, x, y, animate, new RendererCamera(renderer));
            } else {
                panToImpl_perspective(sm, focus, x, y, animate, new RendererCamera(renderer));
            }
        }

        public static void zoomBy(MapRenderer3 renderer, double scaleFactor, MapRenderer3.CameraCollision collide, boolean animate) {
            // Don't zoom to NaN
            if(Double.isNaN(scaleFactor) || scaleFactor <= 0.0)
                return;
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            final double gsdRange = MapSceneModel.range(sm.gsd, sm.camera.fov, sm.height);
            final double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel.projectionXToNominalMeters;
            final double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel.projectionYToNominalMeters;
            final double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel.projectionZToNominalMeters;

            final double offsetRange0 = MathUtils.distance(dx, dy, dz, 0d, 0d, 0d);
            final double offsetRange = offsetRange0 / scaleFactor;
            double mapResolution = MapSceneModel.gsd(gsdRange+(offsetRange-offsetRange0), sm.camera.fov, sm.height);
            renderer.lookAt(sm.mapProjection.inverse(sm.camera.target, null), mapResolution, sm.camera.azimuth, 90d+sm.camera.elevation, collide, animate);
        }

        public static void zoomBy(MapRenderer3 renderer, double scaleFactor, GeoPoint focus, float focusx, float focusy, MapRenderer3.CameraCollision collide, boolean animate) {
            if(Double.isNaN(scaleFactor))
                return;
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            final double gsdRange = MapSceneModel.range(sm.gsd, sm.camera.fov, sm.height);
            final double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel.projectionXToNominalMeters;
            final double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel.projectionYToNominalMeters;
            final double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel.projectionZToNominalMeters;

            final double offsetRange0 = MathUtils.distance(dx, dy, dz, 0d, 0d, 0d);
            final double offsetRange = offsetRange0 / scaleFactor;
            double mapResolution = MapSceneModel.gsd(gsdRange+(offsetRange-offsetRange0), sm.camera.fov, sm.height);
            lookAtImpl(renderer,
                    sm,
                    mapResolution,
                    sm.camera.azimuth,
                    (90d+sm.camera.elevation),
                    focus,
                    focusx, focusy,
                    true,
                    collide,
                    animate);
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
            if(Double.isNaN(gsd))
                return;
            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            lookAtImpl(renderer,
                    currentModel,
                    gsd,
                    currentModel.camera.azimuth,
                    (90d+currentModel.camera.elevation),
                    focus,
                    focusx, focusy,
                    true,
                    collide,
                    animate);
        }

        public static void rotateBy(MapRenderer3 renderer,
                                    double theta,
                                    GeoPoint focus,
                                    MapRenderer3.CameraCollision collide,
                                    boolean animate) {

            if(Double.isNaN(theta))
                return;
            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt(focus, currentModel.gsd, currentModel.camera.azimuth+theta, 90d+currentModel.camera.elevation, collide, animate);
        }

        public static void rotateTo(MapRenderer3 renderer,
                                    double theta,
                                    GeoPoint focus,
                                    float focusx,
                                    float focusy,
                                    MapRenderer3.CameraCollision collide,
                                    boolean animate) {

            if(Double.isNaN(theta))
                return;

            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            lookAtImpl(renderer,
                    currentModel,
                    currentModel.gsd,
                    theta,
                    (90d+currentModel.camera.elevation),
                    focus,
                    focusx, focusy,
                    true,
                    collide,
                    animate);
        }

        public static void tiltBy(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            lookAtImpl(renderer,
                    sm,
                    sm.gsd,
                    sm.camera.azimuth,
                    theta + (90+sm.camera.elevation),
                    focus,
                    sm.focusx, sm.focusy,
                    true,
                    collide,
                    animate);
        }

        public static void tiltBy(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  float focusx,
                                  float focusy,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            lookAtImpl(renderer,
                    sm,
                    sm.gsd,
                    sm.camera.azimuth,
                    theta + (90+sm.camera.elevation),
                    focus,
                    focusx, focusy,
                    true,
                    collide,
                    animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  float focusx,
                                  float focusy,
                                  MapRenderer3.CameraCollision collide,
                                  boolean animate) {

            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            lookAtImpl(renderer,
                    sm,
                    sm.gsd,
                    sm.camera.azimuth,
                    theta,
                    focus,
                    focusx, focusy,
                    true,
                    collide,
                    animate);
        }
    }


    private static void panByImpl(MapSceneModel currentModel, float tx, float ty, boolean animate, ICamera camera) {
        // obtain the current focus
        GeoPoint focusLLA = GeoPoint.createMutable();
        currentModel.mapProjection.inverse(currentModel.camera.target, focusLLA);

        // obtain resolution at focus
        final MapSceneModel sm = currentModel;

        final double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel.projectionXToNominalMeters;
        final double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel.projectionYToNominalMeters;
        final double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel.projectionZToNominalMeters;

        final double offsetRange = Math.sqrt(dx*dx + dy*dy + dz*dz);
        final double gsdFocus = Math.tan(Math.toRadians(sm.camera.fov/2d))*offsetRange/(sm.height / 2d);

        // compute the translation vector length, in nominal display meters at
        // the focus point
        final double cos_theta = Math.cos(Math.toRadians(sm.camera.elevation));
        final double translation = Math.sqrt(tx*tx + ty*ty)*Math.max(gsdFocus, 0.025d*cos_theta*cos_theta);

        // compute UP at focus
        PointD camTargetUp = currentModel.mapProjection.forward(new GeoPoint(focusLLA.getLatitude(), focusLLA.getLongitude(), focusLLA.getAltitude()+1d), (PointD)null);
        Vector3D focusUp = new Vector3D(camTargetUp.x-sm.camera.target.x, camTargetUp.y-sm.camera.target.y, camTargetUp.z-sm.camera.target.z);
        {
            focusUp.X *= sm.displayModel.projectionXToNominalMeters;
            focusUp.Y *= sm.displayModel.projectionYToNominalMeters;
            focusUp.Z *= sm.displayModel.projectionZToNominalMeters;
            final double focusUpLen = MathUtils.distance(focusUp.X, focusUp.Y, focusUp.Z, 0d, 0d, 0d);
            focusUp.X /= focusUpLen;
            focusUp.Y /= focusUpLen;
            focusUp.Z /= focusUpLen;
        }
        // compute NORTH at focus
        PointD camTargetNorth = currentModel.mapProjection.forward(new GeoPoint(focusLLA.getLatitude()+0.00001, focusLLA.getLongitude(), focusLLA.getAltitude()), (PointD)null);
        Vector3D focusNorth = new Vector3D(camTargetNorth.x-sm.camera.target.x, camTargetNorth.y-sm.camera.target.y, camTargetNorth.z-sm.camera.target.z);
        {
            focusNorth.X *= sm.displayModel.projectionXToNominalMeters;
            focusNorth.Y *= sm.displayModel.projectionYToNominalMeters;
            focusNorth.Z *= sm.displayModel.projectionZToNominalMeters;
            final double focusNorthLen = MathUtils.distance(focusNorth.X, focusNorth.Y, focusNorth.Z, 0d, 0d, 0d);
            focusNorth.X /= focusNorthLen;
            focusNorth.Y /= focusNorthLen;
            focusNorth.Z /= focusNorthLen;
        }

        final double translateDir = Math.toDegrees(Math.atan2(ty, -tx))+90d;

        // create a rotation matrix about the axis formed by the focus UP
        // vector, relative to the focus point
        Matrix mx = Matrix.getIdentity();
        mx.rotate(
                Math.toRadians(-sm.camera.azimuth+translateDir),
                sm.camera.target.x*sm.displayModel.projectionXToNominalMeters,
                sm.camera.target.y*sm.displayModel.projectionYToNominalMeters,
                sm.camera.target.z*sm.displayModel.projectionZToNominalMeters,
                focusUp.X,
                focusUp.Y,
                focusUp.Z);

        PointD translated = new PointD(
                sm.camera.target.x*sm.displayModel.projectionXToNominalMeters+focusNorth.X*translation,
                sm.camera.target.y*sm.displayModel.projectionYToNominalMeters+focusNorth.Y*translation,
                sm.camera.target.z*sm.displayModel.projectionZToNominalMeters+focusNorth.Z*translation);
        mx.transform(translated, translated);
        translated.x /= sm.displayModel.projectionXToNominalMeters;
        translated.y /= sm.displayModel.projectionYToNominalMeters;
        translated.z /= sm.displayModel.projectionZToNominalMeters;

        GeoPoint translatedLLA = GeoPoint.createMutable();
        currentModel.mapProjection.inverse(translated, translatedLLA);
        if(Math.abs(translatedLLA.getLongitude()) > 180d)
            translatedLLA.set(translatedLLA.getLatitude(), GeoCalculations.wrapLongitude(translatedLLA.getLongitude()), translatedLLA.getAltitude(), translatedLLA.getAltitudeReference(), translatedLLA.getCE(), translatedLLA.getLE());
        if (Double.isNaN(translatedLLA.getLongitude()) || Double.isNaN(translatedLLA.getLatitude()))
            return;
        translatedLLA.set(focusLLA.getAltitude());

        camera.lookAt(translatedLLA,
                currentModel.gsd,
                currentModel.camera.azimuth,
                90d+currentModel.camera.elevation,
                animate);
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
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt(focus, sm.gsd, sm.camera.azimuth, 90d+sm.camera.elevation, animate);
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

            if(Double.isNaN(rotation))
                return;
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt(sm.mapProjection.inverse(sm.camera.target, null), sm.gsd, rotation, 90d+sm.camera.elevation, animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double tilt,
                                  boolean animate) {
            if(Double.isNaN(tilt))
                return;
            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt (
                    currentModel.mapProjection.inverse(currentModel.camera.target, null),
                    currentModel.gsd,
                    currentModel.camera.azimuth,
                    tilt,
                    animate);
        }

        public static void zoomTo(MapRenderer3 renderer, double gsd, boolean animate) {
            // Don't zoom to NaN
            if (Double.isNaN (gsd))
                return;
            final MapSceneModel sm = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt(sm.mapProjection.inverse(sm.camera.target, null), gsd, sm.camera.azimuth, 90d+sm.camera.elevation, animate);
        }

        public static void tiltTo(MapRenderer3 renderer,
                                  double theta,
                                  GeoPoint focus,
                                  boolean animate) {

            if(Double.isNaN(theta))
                return;
            final MapSceneModel currentModel = renderer.getMapSceneModel(false, MapRenderer3.DisplayOrigin.UpperLeft);
            renderer.lookAt(focus, currentModel.gsd, currentModel.camera.azimuth, theta, animate);
        }
    }

    public static class Util {
        /**
         * Computes the relative density ratio between the pixel at the
         * current focus and specific screen location. Pixel densities are
         * computed on the plane tangent to the focus point.
         */
        public static double computeRelativeDensityRatio(MapSceneModel sm, float x, float y) {
            GeoPoint focus = GeoPoint.createMutable();
            sm.mapProjection.inverse(sm.camera.target, focus);

            // intersect the end point with the plane to determine the translation vector in WCS
            GeoPoint endgeo = GeoPoint.createMutable();
            if (sm.inverse(new PointF(x, y), endgeo) == null)
                return 0d;

            PointD endWCS = new PointD(0d, 0d, 0d);
            sm.mapProjection.forward(endgeo, endWCS);

            final double lenEndWCS = MathUtils.distance(
                    endWCS.x*sm.displayModel.projectionXToNominalMeters,
                    endWCS.y*sm.displayModel.projectionYToNominalMeters,
                    endWCS.z*sm.displayModel.projectionZToNominalMeters,
                    sm.camera.location.x*sm.displayModel.projectionXToNominalMeters,
                    sm.camera.location.y*sm.displayModel.projectionYToNominalMeters,
                    sm.camera.location.z*sm.displayModel.projectionZToNominalMeters);
            if(lenEndWCS == 0d)
                return 0d;
            final double lenFocus = MathUtils.distance(
                    sm.camera.target.x*sm.displayModel.projectionXToNominalMeters,
                    sm.camera.target.y*sm.displayModel.projectionYToNominalMeters,
                    sm.camera.target.z*sm.displayModel.projectionZToNominalMeters,
                    sm.camera.location.x*sm.displayModel.projectionXToNominalMeters,
                    sm.camera.location.y*sm.displayModel.projectionYToNominalMeters,
                    sm.camera.location.z*sm.displayModel.projectionZToNominalMeters);
            return  lenFocus / lenEndWCS;
        }

        /**
         * Creates a tangent plane at the specified focus point.
         *
         * @param sm
         * @param focus
         * @return
         */
        public static Plane createTangentPlane(MapSceneModel sm, GeoPoint focus) {
            if(Double.isNaN(focus.getAltitude()))
                focus = new GeoPoint(focus.getLatitude(), focus.getLongitude(), 0d);

            // create plane at press location
            PointD startProj = sm.mapProjection.forward(focus, null);
            PointD startProjUp = sm.mapProjection.forward(new GeoPoint(focus.getLatitude(), focus.getLongitude(), focus.getAltitude()+100d), null);

            // compute the normal at the start point
            Vector3D startNormal = new Vector3D(startProjUp.x-startProj.x, startProjUp.y-startProj.y, startProjUp.z-startProj.z);
            startNormal.X *= sm.displayModel.projectionXToNominalMeters;
            startNormal.Y *= sm.displayModel.projectionYToNominalMeters;
            startNormal.Z *= sm.displayModel.projectionZToNominalMeters;
            final double startNormalLen = MathUtils.distance(startNormal.X, startNormal.Y, startNormal.Z, 0d, 0d, 0d);
            startNormal.X /= startNormalLen;
            startNormal.Y /= startNormalLen;
            startNormal.Z /= startNormalLen;

            return new Plane(startNormal, startProj);
        }

        public static GeometryModel createFocusAltitudeModel(MapSceneModel sm, GeoPoint focus) {
            if(sm.earth instanceof Plane) {
                return createTangentPlane(sm, focus);
            } else if(sm.earth instanceof Sphere) {
                final Sphere s = (Sphere)sm.earth;
                final double alt = Double.isNaN(focus.getAltitude()) ? 0d : focus.getAltitude();
                return new Sphere(s.center, s.radius+alt);
            } else if(sm.earth instanceof Ellipsoid) {
                final Ellipsoid e = (Ellipsoid) sm.earth;
                final double alt = Double.isNaN(focus.getAltitude()) ? 0d : focus.getAltitude();
                return new Ellipsoid(e.location, e.radiusX+alt, e.radiusY+alt, e.radiusZ+alt);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static void panToImpl_ortho(MapSceneModel sm, GeoPoint focus, float x, float y, boolean animate, ICamera camera) {
        PointF xy = sm.forward(focus, (PointF)null);
        if(xy == null)
            return;
        panByImpl(sm, xy.x-sm.focusx, xy.y-sm.focusy, false, camera);
        panByImpl(sm, sm.focusx-x, sm.focusy-y, animate, camera);
    }

    private static void panToImpl_perspective(MapSceneModel sm, GeoPoint focus, float x, float y, boolean animate, ICamera camera) {
        if(Double.isNaN(focus.getAltitude()))
            focus = new GeoPoint(focus.getLatitude(), focus.getLongitude(), 0d);

        final GeometryModel panModel = Util.createFocusAltitudeModel(sm, focus);

        // intersect the end point with the model adjusted to focus altitude to
        // determine the translation vector in WCS
        GeoPoint endgeo = GeoPoint.createMutable();
        if (sm.inverse(new PointF(x, y), endgeo, panModel) == null)
            return;

        PointD endWCS = new PointD(0d, 0d, 0d);
        sm.mapProjection.forward(endgeo, endWCS);

        // compute translation of focus point on model surface to end point on plane
        final double tx = endWCS.x - sm.camera.target.x;
        final double ty = endWCS.y - sm.camera.target.y;
        final double tz = endWCS.z - sm.camera.target.z;

        GeoPoint target = GeoPoint.createMutable();
        sm.mapProjection.inverse(sm.camera.target, target);

        // XXX - translation is not really correct here, though it (seems to)
        //       work well enough as an approximation. We need the magnitude of
        //       the vector, however, direction will be dependent on the points
        //       selected for non-planar projections.

        // new focus is the desired location minus the translation
        PointD focusProj = sm.mapProjection.forward(focus, null);
        GeoPoint newFocus = GeoPoint.createMutable();
        if(sm.mapProjection.inverse(new PointD(focusProj.x - tx, focusProj.y - ty, focusProj.z - tz), newFocus) == null)
            return;
        newFocus.set(newFocus.getLatitude(), newFocus.getLongitude(), focus.getAltitude());
        if(Math.abs(newFocus.getLongitude()) > 180d)
            newFocus = new GeoPoint(newFocus.getLatitude(), GeoCalculations.wrapLongitude(newFocus.getLongitude()), newFocus.getAltitude(), newFocus.getAltitudeReference());

        camera.lookAt(
                newFocus,
                sm.gsd,
                sm.camera.azimuth,
                90d + sm.camera.elevation,
                animate);
    }

    /*
     * @param gsdRelativeCurrent    If <code>true</code>, GSD is specified
     *                              relative to _current_ focus, not newly
     *                              specified focus. If <code>false</code> GSD
     *                              is specified as relative to the newly
     *                              specified focus.
     */
    private static void lookAtImpl(MapRenderer3 renderer,
                                   MapSceneModel currentModel,
                                   double gsd,
                                   double rotation,
                                   double tilt,
                                   GeoPoint focus,
                                   float focusx,
                                   float focusy,
                                   boolean gsdRelativeCurrent,
                                   MapRenderer3.CameraCollision collide,
                                   boolean animate) {

        if(Double.isNaN(gsd))
            return;
        if(Double.isNaN(rotation))
            return;
        if(Double.isNaN(tilt))
            return;

        CameraBuilder builder = new CameraBuilder(currentModel);

        if(gsdRelativeCurrent) {
            MapSceneModel sm = currentModel;
            // execute zoom first
            if(gsd != sm.gsd) {
                sm = new MapSceneModel(
                        sm.dpi,
                        sm.width, sm.height,
                        sm.mapProjection,
                        sm.mapProjection.inverse(sm.camera.target, null),
                        sm.focusx, sm.focusy,
                        sm.camera.azimuth,
                        90d+sm.camera.elevation,
                        gsd,
                        true);
            }

            // compute the point against the current model that corresponds to
            // the same altitude as the focus point
            GeoPoint focusAtGsd = GeoPoint.createMutable();
            GeometryModel focusModel = Util.createFocusAltitudeModel(sm, focus);
            sm.inverse(new PointF(sm.focusx, sm.focusy), focusAtGsd, focusModel);

            PointD focusAtGsdProj = new PointD(0d, 0d, 0d);
            sm.mapProjection.forward(focusAtGsd, focusAtGsdProj);

            // compute the range from the current camera to the point along the
            // LOS at focus altitude
            final double camrange = MathUtils.distance(
                    sm.camera.location.x*sm.displayModel.projectionXToNominalMeters,
                    sm.camera.location.y*sm.displayModel.projectionYToNominalMeters,
                    sm.camera.location.z*sm.displayModel.projectionZToNominalMeters,
                    focusAtGsdProj.x*sm.displayModel.projectionXToNominalMeters,
                    focusAtGsdProj.y*sm.displayModel.projectionYToNominalMeters,
                    focusAtGsdProj.z*sm.displayModel.projectionZToNominalMeters);

            final double rangeAltAdj = Double.isNaN(focus.getAltitude()) ? 0d : focus.getAltitude();
            gsd = MapSceneModel.gsd(
                            camrange+rangeAltAdj,
                            sm.camera.fov,
                            sm.height);
        }

        // perform basic camera orientation about focus point
        builder.lookAt (
            focus,
            gsd,
            rotation,
            tilt,
            false);

        // pan focus geo to focus x,y
        if(focusx != builder.sm.focusx || focusy != builder.sm.focusy) {
            if (!builder.sm.camera.perspective) {
                panToImpl_ortho(builder.sm, focus, focusx, focusy, false, builder);
            } else {
                panToImpl_perspective(builder.sm, focus, focusx, focusy, false, builder);
            }
        }

        // execute `lookAt` with the aggregated motion
        builder.dispatch(renderer, collide, animate);
    }

    interface ICamera {
        void lookAt(GeoPoint focus, double resolution, double azimuth, double tilt, boolean animate);
    }

    static class CameraBuilder implements ICamera {
        MapSceneModel sm;

        public CameraBuilder(MapSceneModel sm) {
            this.sm = sm;
        }

        public void lookAt(GeoPoint focus, double resolution, double azimuth, double tilt, boolean animate) {
            sm = new MapSceneModel(sm.dpi, sm.width, sm.height, sm.mapProjection, focus, sm.focusx, sm.focusy, azimuth, tilt, resolution, true);
        }

        void dispatch(MapRenderer3 renderer, MapRenderer3.CameraCollision collide, boolean animate) {
            renderer.lookAt(
                    sm.mapProjection.inverse(sm.camera.target, null),
                    sm.gsd,
                    sm.camera.azimuth,
                    90d+sm.camera.elevation,
                    collide,
                    animate);
        }
    }

    static class RendererCamera implements ICamera {
        MapRenderer3 _renderer;

        public RendererCamera(MapRenderer3 renderer) {
            _renderer = renderer;
        }

        public void lookAt(GeoPoint focus, double resolution, double azimuth, double tilt, boolean animate) {
            _renderer.lookAt(focus, resolution, azimuth, tilt, animate);
        }
    }
}
