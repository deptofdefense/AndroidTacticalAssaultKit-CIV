package gov.tak.examples.examplewindow;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.RenderSurface;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Vector3D;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class MouseMapInputHandler extends MouseAdapter implements MouseWheelListener {
    final static GeoPoint offworldCheckGeo = GeoPoint.createMutable();
    final static int offworldCheckHints = MapRenderer2.HINT_RAYCAST_IGNORE_SURFACE_MESH|MapRenderer2.HINT_RAYCAST_IGNORE_TERRAIN_MESH;

    final MapRenderer2 glglobe;

    boolean isPan;
    boolean isTilt;
    boolean isRotate;
    int pressX;
    int pressY;
    GeoPoint centerOnPress = GeoPoint.createMutable();
    GeoPoint pressLocation = GeoPoint.createMutable();
    int lastX;
    int lastY;
    double dragStartRotation;
    boolean cameraPanTo;

    MouseMapInputHandler(MapRenderer2 glglobe) {
        this.glglobe = glglobe;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        final int button = e.getButton();
        switch(button) {
            case MouseEvent.BUTTON1 : // left drag to pan
                isPan = true;
                break;
            case MouseEvent.BUTTON2 : // wheel drag to tilt
                isTilt = true;
                break;
            case MouseEvent.BUTTON3 : // right drag to rotate
                isRotate = true;
                break;
            default :
                return;
        }

        // record where the press started
        pressX = e.getX();
        pressY = e.getY();
        lastX = pressX;
        lastY = pressY;
        dragStartRotation = glglobe.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft).camera.azimuth;

        // record the center when the press occurred
        final RenderSurface surface = glglobe.getRenderContext().getRenderSurface();
        glglobe.inverse(new PointD(surface.getWidth()/2d+glglobe.getFocusPointOffsetX(), surface.getHeight()/2d+glglobe.getFocusPointOffsetY(), 0d), centerOnPress, MapRenderer2.InverseMode.RayCast, 0, MapRenderer2.DisplayOrigin.UpperLeft);
        glglobe.inverse(new PointD(pressX, pressY, 0d), pressLocation, MapRenderer2.InverseMode.RayCast, 0, MapRenderer2.DisplayOrigin.UpperLeft);

        // establish whether the camera is below the tangent plane at the press location
        {
            final MapSceneModel sm = glglobe.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);

            // create plane at press location
            PointD startProj = sm.mapProjection.forward(pressLocation, null);
            PointD startProjUp = sm.mapProjection.forward(new GeoPoint(pressLocation.getLatitude(), pressLocation.getLongitude(), pressLocation.getAltitude() + 100d), null);

            // compute the normal at the start point
            final Vector3D startNormal = new Vector3D(startProjUp.x - startProj.x, startProjUp.y - startProj.y, startProjUp.z - startProj.z);
            startNormal.X *= sm.displayModel.projectionXToNominalMeters;
            startNormal.Y *= sm.displayModel.projectionYToNominalMeters;
            startNormal.Z *= sm.displayModel.projectionZToNominalMeters;
            final double startNormalLen = MathUtils.distance(startNormal.X, startNormal.Y, startNormal.Z, 0d, 0d, 0d);
            startNormal.X /= startNormalLen;
            startNormal.Y /= startNormalLen;
            startNormal.Z /= startNormalLen;

            final double d = -startNormal.dot(startProj.x, startProj.y, startProj.z);
            final double above = startNormal.dot(startProjUp.x, startProjUp.y, startProjUp.z) + d;
            final double camera = startNormal.dot(sm.camera.location.x, sm.camera.location.y, sm.camera.location.z) + d;

            // determine what side of the plane the camera is on; if the
            // camera is below the plane, we'll do an ortho style drag,
            // otherwise we'll do a real drag.
            this.cameraPanTo = sm.camera.perspective && (above*camera > 0d);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        isPan = false;
        isTilt = false;
        isRotate = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if(isPan) {
            boolean doPanTo = this.cameraPanTo;
            if(doPanTo) {
                // don't attempt pan-to when off world
                final MapRenderer2.InverseResult result = glglobe.inverse(new PointD(e.getX(), e.getY(), 0d), offworldCheckGeo, MapRenderer2.InverseMode.RayCast, offworldCheckHints, MapRenderer2.DisplayOrigin.UpperLeft);
                doPanTo &= (result != MapRenderer2.InverseResult.None);
            }

            if(doPanTo)
                CameraController.panTo(glglobe, pressLocation, e.getX(), e.getY(), false);
            else
                CameraController.panBy(glglobe, lastX - e.getX(), lastY - e.getY(), false);
        } else if(isRotate) {
            // map center
            final float centerX = glglobe.getRenderContext().getRenderSurface().getWidth() / 2f;
            final float centerY = glglobe.getRenderContext().getRenderSurface().getHeight() / 2f;

            // compute relative angle between screen center and mouse drag start
            double initAz = Math.atan2(pressY - centerY, pressX - centerX);
            // compute the relative angle between the center and the current mouse position
            double currentAz = Math.atan2(e.getY() - centerY, e.getX() - centerX);

            // relative rotation from drag start in degrees
            double relativeRotation = (currentAz - initAz) * 180d / Math.PI;

            // the new rotation azimuth is the rotation on drag start plus
            // the current relative rotation
            CameraController.rotateTo(glglobe, dragStartRotation - relativeRotation, centerOnPress, false);
        } else if(isTilt) {
            // NOTE: currently set for drag up => tilt forward
            final MapSceneModel currentModel = glglobe.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
            CameraController.tiltTo(glglobe, (90d+currentModel.camera.elevation)+(lastY-e.getY()), centerOnPress, false);
        }
        // update the last drag location
        lastX = e.getX();
        lastY = e.getY();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        CameraController.zoomBy(glglobe, Math.pow(2.0, -e.getWheelRotation()), e.getX(), e.getY(), false);
    }
}