
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.PointF;
import android.util.Pair;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerDrawableWidget;
import com.atakmap.coremap.maps.coords.GeoPoint;
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
    private final PointF _pointFWD = new PointF();

    public GLMarkerDrawableWidget(MarkerDrawableWidget drw, GLMapView ortho) {
        super(drw, ortho);
        _subject = drw;
        updateDrawable();
    }

    @Override
    public void drawWidget() {
        // Update the point based on set geo point or marker
        GeoPoint point = _geoPoint;
        if (_item != null)
            point = _item.getPoint();
        if (point != null) {
            orthoView.forward(point, _pointFWD);
            x = _pointFWD.x;
            y = orthoView.getTop() - _pointFWD.y;

            x -= _width / 2f;
            y -= _height / 2f;

            // Offset Y by half of icon size when tilted
            if (_item != null && orthoView.drawTilt > 0)
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
            }
        });
    }
}
