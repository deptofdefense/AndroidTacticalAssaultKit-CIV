
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.PointF;
import android.util.Pair;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerDrawableWidget;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.opengl.GLMapView;

/**
 * OpenGL rendering for drawable widget attached to a marker
 */
public class GLMarkerDrawableWidget extends GLDrawableWidget {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof MarkerDrawableWidget) {
                MarkerDrawableWidget drw = (MarkerDrawableWidget) subject;
                GLMarkerDrawableWidget glm = new GLMarkerDrawableWidget(
                        drw, orthoView);
                glm.startObserving(drw);
                return glm;
            } else {
                return null;
            }
        }
    };

    private final MarkerDrawableWidget _subject;

    private PointMapItem _item;
    private GeoPoint _geoPoint;
    private final ClampToGroundControl _clampCtrl;
    private final PointF _pointFWD = new PointF();
    private int _drawVersion;
    private int _terrainVersion;
    private boolean _nadirClamp;

    public GLMarkerDrawableWidget(MarkerDrawableWidget drw, GLMapView ortho) {
        super(drw, ortho);
        _subject = drw;
        _clampCtrl = orthoView.getControl(ClampToGroundControl.class);
        updateDrawable();
    }

    @Override
    public void drawWidget() {
        // Update the point based on set geo point or marker
        GeoPoint point = _geoPoint;
        AltitudeMode altMode = AltitudeMode.Absolute;
        double height = Double.NaN;
        if (_item != null) {
            point = _item.getPoint();
            altMode = _item.getAltitudeMode();
            height = _item.getHeight();
        }
        if (point != null) {
            updateScreenPoint(point, altMode, height);
            x = _pointFWD.x;
            y = orthoView.getTop() - _pointFWD.y;

            x -= _width / 2f;
            y -= _height / 2f;

            // Offset Y by half of icon size when tilted
            if (_item != null && orthoView.currentPass.drawTilt > 0)
                y -= 24;
        }

        // Continue drawing
        super.drawWidget();
    }

    @Override
    protected void updateDrawable() {
        if (_subject == null)
            return;
        super.updateDrawable();
        final PointMapItem item = _subject.getMarker();
        final GeoPoint point = _subject.getGeoPoint();
        orthoView.queueEvent(new Runnable() {
            @Override
            public void run() {
                _item = item;
                _geoPoint = point;
                _needsRedraw = true;
                _drawVersion = -1;
            }
        });
    }

    /**
     * Get the latest screen coordinate for the corresponding {@link GeoPoint}
     * position of this widget
     * @param point Geo point to update from
     * @param altMode Altitude mode
     * @param height Marker height (if applicable)
     */
    private void updateScreenPoint(GeoPoint point, AltitudeMode altMode,
            double height) {

        // Check if a redraw is necessary
        boolean nadirClamp = _clampCtrl != null && _clampCtrl
                .getClampToGroundAtNadir()
                && Double.compare(
                        orthoView.currentScene.drawTilt, 0) == 0;
        int drawVersion = orthoView.currentScene.drawVersion;
        int terrainVersion = orthoView.getTerrainVersion();
        if (drawVersion == _drawVersion && terrainVersion == _terrainVersion
                && _nadirClamp == nadirClamp)
            return;

        _drawVersion = drawVersion;
        _terrainVersion = terrainVersion;
        _nadirClamp = nadirClamp;

        // Clamp to the ground if nadir clamping is enabled and the tilt is set
        // to zero
        if (_nadirClamp)
            altMode = AltitudeMode.ClampToGround;

        // Get point altitude and corresponding terrain data
        double alt = point.getAltitude();
        final double terrain = orthoView.getTerrainMeshElevation(
                point.getLatitude(), point.getLongitude());

        // Offset altitude based on altitude mode
        if (!GeoPoint.isAltitudeValid(alt)
                || altMode == AltitudeMode.ClampToGround)
            alt = terrain;
        else if (altMode == AltitudeMode.Relative)
            alt += terrain;

        // Offset the height if the marker has a "height" meta value,
        // which it should have if a shape was extruded in GLPolyline
        if (!_nadirClamp && !Double.isNaN(height))
            alt += height;

        // Markers don't allow sub-terrain altitude
        if (alt < terrain)
            alt = terrain;

        // Update the scratch geo point and forward for the screen point
        orthoView.scratch.geo.set(point);
        orthoView.scratch.geo.set(alt);
        orthoView.forward(orthoView.scratch.geo, _pointFWD);
    }
}
