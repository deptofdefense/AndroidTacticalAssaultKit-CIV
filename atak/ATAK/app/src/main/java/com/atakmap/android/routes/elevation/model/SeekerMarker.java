
package com.atakmap.android.routes.elevation.model;

import java.util.UUID;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import com.atakmap.android.elev.ViewShedReceiver;
import com.atakmap.android.elev.ViewShedReceiver.VsdLayer;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.routes.elevation.AnalysisPanelPresenter;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.Globe;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.math.MathUtils;

public class SeekerMarker implements OnSharedPreferenceChangeListener {
    public static final int MIN_VSD_ELEV = 2;

    private Icon _centerIcon = null;
    private final MapView _mapView;
    private Marker _marker;
    private boolean bCenterMap;
    private boolean showViewshed = false;
    private boolean quickDraw = false;
    private final ViewShedReceiver vsdRec;
    private VsdLayer layer;
    // Auto centering suppression is needed to fix bug 5976.
    // Auto centering was triggering while dragging vertices around in the map view
    private boolean _suppressAutoCentering = false;

    private final SharedPreferences prefs;

    public SeekerMarker(MapView mapView) {
        this._mapView = mapView;

        prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        this.bCenterMap = prefs.getBoolean("elevProfileCenterOnSeeker", true);
        this.showViewshed = prefs.getBoolean(
                AnalysisPanelPresenter.PREFERENCE_SHOW_VIEWSHED, false);

        prefs.registerOnSharedPreferenceChangeListener(this);
        vsdRec = ViewShedReceiver.getInstance();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp,
            String key) {

        if (key == null)
            return;

        if (key.equals("elevProfileCenterOnSeeker")) {
            bCenterMap = prefs.getBoolean(
                    "elevProfileCenterOnSeeker", true);
        } else if (key
                .equals(AnalysisPanelPresenter.PREFERENCE_SHOW_VIEWSHED)
                ||
                key.equals(
                        AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_ALT)
                ||
                key.equals(
                        AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_CIRCLE)
                ||
                key.equals(
                        AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_RADIUS)
                ||
                key.equals(
                        AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_OPACITY)) {
            showViewshed = prefs.getBoolean(
                    AnalysisPanelPresenter.PREFERENCE_SHOW_VIEWSHED, false);
            if (showViewshed && _marker != null)
                draw(_marker.getPoint());
            else {
                if (layer != null) {
                    layer.uninstall();
                    layer = null;
                }
            }
        }
    }

    public void make(GeoPoint p) {
        if (_centerIcon == null)
            _centerIcon = new Icon("asset:/icons/seekermarker.png");

        if (_marker != null)
            clear();

        String _uid = UUID.randomUUID().toString();

        _marker = new Marker(_uid);
        _marker.setIcon(_centerIcon);
        _marker.setVisible(true);
        _marker.setShowLabel(false);
        _marker.setTitle("Seeker");
        _marker.setMetaBoolean("addToObjList", false);
        _marker.setMetaBoolean("ignoreOffscreen", false);
        _marker.setMovable(false);
        _marker.setPoint(p);
        // HACK HACK HACK
        _marker.setZOrder(_marker.getZOrder() + 1);

        _mapView.getRootGroup().addItem(_marker);
    }

    public void setQuickDraw(boolean qd) {
        quickDraw = qd;
    }

    public void draw(GeoPoint p) {
        if (_marker == null)
            make(p);
        _marker.setPoint(p);
        if (bCenterMap && !_suppressAutoCentering) {
            double zoomScale = _mapView.getMapScale();
            final double maxGsd = ATAKUtilities.getMaximumFocusResolution(p);
            final double gsd = Globe.getMapResolution(_mapView.getDisplayDpi(),
                    zoomScale);
            if (gsd < maxGsd) {
                zoomScale = Globe.getMapScale(_mapView.getDisplayDpi(), maxGsd);
            }
            _mapView.getMapController().panZoomTo(p,
                    zoomScale, true);
        }

        if (showViewshed && p.isAltitudeValid()) {
            double groundElev = ElevationManager.getElevation(
                    p.getLatitude(),
                    p.getLongitude(), null);
            if (!GeoPoint.isAltitudeValid(groundElev)) {
                if (layer != null) {
                    layer.uninstall();
                    layer = null;
                }
                return;
            }
            int opacity = prefs.getInt(
                    AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_OPACITY,
                    50);
            opacity = MathUtils.clamp(opacity, 1, 99);
            boolean circle = true;
            double radius = prefs.getInt(
                    AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_RADIUS,
                    7000);
            double heightAboveReference = prefs.getInt(
                    AnalysisPanelPresenter.PREFERENCE_PROFILE_VIEWSHED_ALT,
                    2);

            double markerAltAGL = 0;
            if (p.getAltitudeReference() != AltitudeReference.AGL) {
                if (GeoPoint.isAltitudeValid(groundElev)) {
                    double altM = EGM96
                            .getAGL(p, groundElev);
                    if (GeoPoint.isAltitudeValid(altM))
                        markerAltAGL = altM;
                }
            } else {
                markerAltAGL = p.getAltitude();
            }
            double heightAboveGround = heightAboveReference;
            if (markerAltAGL > 0)
                heightAboveGround += markerAltAGL;

            if (heightAboveGround < MIN_VSD_ELEV)
                heightAboveGround = MIN_VSD_ELEV;

            double pointAlt = heightAboveGround;

            GeoPoint refPoint = new GeoPoint(p.getLatitude(),
                    p.getLongitude(),
                    pointAlt, AltitudeReference.AGL,
                    GeoPoint.UNKNOWN,
                    GeoPoint.UNKNOWN);

            if (layer == null)
                layer = new ViewShedReceiver.VsdLayer();

            if (layer.getViewshed().getPointOfInterest() == null)
                layer.getViewshed().setPointOfInterest(refPoint);
            else if (layer.getViewshed().getPointOfInterest()
                    .getLatitude() != refPoint
                            .getLatitude()
                    ||
                    layer.getViewshed().getPointOfInterest()
                            .getLongitude() != refPoint
                                    .getLongitude()
                    ||
                    layer.getViewshed().getPointOfInterest()
                            .getAltitude() != refPoint
                                    .getAltitude())
                layer.getViewshed().setPointOfInterest(refPoint);

            if (layer.getViewshed().getRadius() != radius)
                layer.getViewshed().setRadius(radius);
            if (quickDraw)
                layer.getViewshed().setResolution(101);
            else
                layer.getViewshed().setResolution(301);

            if (layer.getViewshed().getOpacity() != opacity)
                layer.getViewshed().setOpacity(opacity);
            if (layer.getViewshed().getCircle() != circle)
                layer.getViewshed().setCircle(circle);

            if (!layer.getVisible()) {
                layer.install();
            }
        } else {
            if (layer != null) {
                layer.uninstall();
                layer = null;
            }
        }

    }

    public void clear() {
        Intent i = new Intent(ViewShedReceiver.ACTION_DISMISS_VIEWSHED);
        if (_marker != null)
            i.putExtra(ViewShedReceiver.VIEWSHED_UID, _marker.getUID());

        AtakBroadcast.getInstance().sendBroadcast(i);

        if (_marker != null) {
            _mapView.getRootGroup().removeItem(_marker);
            _marker = null;
        }

        if (layer != null) {
            layer.uninstall();
            layer = null;
        }
    }

    public boolean getSuppressAutoCentering() {
        return _suppressAutoCentering;
    }

    public boolean setSuppressAutoCentering(boolean value) {
        return _suppressAutoCentering = value;
    }

}
