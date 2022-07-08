
package com.atakmap.android.maps.graphics.widgets;

import android.content.SharedPreferences;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.widgets.MapFocusTextWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.opengl.GLMapView;

public class GLMapFocusTextWidget extends GLTextWidget implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // TextWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof MapFocusTextWidget) {
                MapFocusTextWidget textWidget = (MapFocusTextWidget) subject;
                return new GLMapFocusTextWidget(textWidget, orthoView);
            }
            return null;
        }
    };

    private final MapFocusTextWidget _subject;
    private final UnitPreferences _prefs;
    private final GeoPointMetaData _point;

    private double _drawLat, _drawLng;

    public GLMapFocusTextWidget(MapFocusTextWidget subject, GLMapView ortho) {
        super(subject, ortho);
        _subject = subject;
        _prefs = new UnitPreferences((MapView) ortho.getSurface().getMapView());
        _point = new GeoPointMetaData();
        _prefs.registerListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        _prefs.unregisterListener(this);
    }

    @Override
    public void drawWidgetContent() {
        // Update coordinate whenever draw position changes
        if (Double.compare(_drawLat, orthoView.currentPass.drawLat) != 0
                || Double.compare(_drawLng,
                        orthoView.currentPass.drawLng) != 0) {
            _drawLat = orthoView.currentPass.drawLat;
            _drawLng = orthoView.currentPass.drawLng;

            // Inverse for point at focus
            GLMapView.ScratchPad s = orthoView.scratch;
            s.pointD.x = orthoView.currentPass.focusx;
            s.pointD.y = orthoView.currentPass.focusy;
            s.pointD.z = 0;
            orthoView.inverse(s.pointD, s.geo, MapRenderer2.InverseMode.RayCast,
                    0, MapRenderer2.DisplayOrigin.UpperLeft);

            // Fetch elevation at focus point
            ElevationManager.getElevation(s.geo.getLatitude(),
                    s.geo.getLongitude(), null, _point);

            // Location + altitude w/ source
            String text = _prefs.formatPoint(_point, false) + " "
                    + _prefs.formatAltitude(_point);

            // Update subject text, which will be rendered next frame
            // It's required we update the subject itself because width/height
            // needs to be calculated in order to determine how this widget
            // is drawn within its parent
            _subject.setText(text);
        }
        super.drawWidgetContent();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (UnitPreferences.COORD_FMT.equals(key)) {
            orthoView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    _drawLat = _drawLng = Double.NaN;
                }
            });
        }
    }
}
