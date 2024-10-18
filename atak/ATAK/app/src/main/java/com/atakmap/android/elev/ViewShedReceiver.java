
package com.atakmap.android.elev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.atakmap.android.elev.graphics.GLViewShed2;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlayRenderer;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import java.util.ArrayList;
import java.util.HashMap;

public class ViewShedReceiver extends BroadcastReceiver {

    private final static String TAG = "ViewShedReceiver";

    /**
     * Action to show the viewshed overlay. The {@link Intent} should include
     * one of the extras:
     * 
     * <UL>
     *   <LI>{@link #EXTRA_ELEV_THRESHOLD}</LI>
     *   <LI>{@link #EXTRA_ELEV_POINT}</LI>
     * </UL>
     * 
     * In the event that more than one is included, the threshold elevation
     * argument will be selected using the above order.
     * 
     * <P>This action may be posted after the viewshed is made visible to update
     * the viewshed elevation threshold value.
     */
    public final static String ACTION_SHOW_VIEWSHED = "com.atakmap.android.elev.ViewShedReceiver.SHOW_VIEWSHED";

    public final static String ACTION_SHOW_VIEWSHED_LINE = "com.atakmap.android.elev.ViewShedReceiver.SHOW_VIEWSHED_LINE";

    /**
     * Action to dismiss the viewshed overlay.
     */
    public final static String ACTION_DISMISS_VIEWSHED = "com.atakmap.android.elev.ViewShedReceiver.DISMISS_VIEWSHED";
    public final static String ACTION_DISMISS_VIEWSHED_LINE = "com.atakmap.android.elev.ViewShedReceiver.DISMISS_VIEWSHED_LINE";

    /**
     * Action to update the viewshed intensity.
     */
    public final static String UPDATE_VIEWSHED_INTENSITY = "com.atakmap.android.elev.ViewShedReceiver.UPDATE_VIEWSHED_INTENSITY";

    /**
     * The point of interest for viewshed analysis. The associated value should
     * be an {@link GeoPoint} instance. If the associated point does not have a
     * valid altitude, the elevation model will be queried.
     */
    public final static String EXTRA_ELEV_POINT = "point";
    /**
     * The point of interest for viewshed analysis. The associated value should
     * be an {@link GeoPoint} instance. If the associated point does not have a
     * valid altitude, the elevation model will be queried.
     */
    public final static String EXTRA_ELEV_POINT2 = "point2";

    /**
     * The radius in meters for how far around the center point the viewshed
     * should be calculated.
     */
    public final static String EXTRA_ELEV_RADIUS = "radius";
    public final static String EXTRA_RESOLUTION = "resolution";
    public final static String EXTRA_LINE_SAMPLES = "samples";

    /**
     * The opacity value for the viewshed overlay
     */
    public final static String EXTRA_ELEV_OPACITY = "opacity";

    public final static String EXTRA_ELEV_CIRCLE = "circle";

    /**
     * The UID of the {@link MapItem} of interest for viewshed analysis. The
     * associated value should be an instance of {@link String} that is the
     * {@link MapItem} UID. If the {@link GeoPoint} associated with the
     * {@link MapItem} does not have a valid altitude, the elevation
     * model will be queried at the point.
     */
    public final static String VIEWSHED_UID = "uid";
    public final static String VIEWSHED_UID2 = "uid2";

    public final static String SHOWING_LINE = "showingLine";

    /**
     * The elevation threshold value for the viewshed, in meters MSL. A value of
     * {@link Double#NaN} indicates an invalid value.
     */
    public final static String EXTRA_ELEV_THRESHOLD = "threshold";

    /** viewshed related preferences */
    public static final String VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY = "viewshed_prefs_dted_color_intensity";
    public static final String VIEWSHED_PREFERENCE_CIRCULAR_VIEWSHED = "viewshed_prefs_circular_viewshed";
    public static final String VIEWSHED_PREFERENCE_RADIUS_KEY = "viewshed_prefs_radius";

    public static final String VIEWSHED_PREFERENCE_HEIGHT_ABOVE_KEY = "viewshed_prefs_height_above_meters";

    public static final String VIEWSHED_LINE_UID_SEPERATOR = "*";

    public static class VsdLayer {
        private final MapView mapview = MapView.getMapView();
        private ViewShedLayer2 viewShed;
        private MapOverlayRenderer viewShedRenderer;
        private boolean visible = false;

        public VsdLayer() {
            this.viewShed = new ViewShedLayer2();
            this.viewShedRenderer = new MapOverlayRenderer(
                    MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                    new GLViewShed2(mapview.getGLSurface()
                            .getGLMapView(),
                            this.viewShed));
        }

        public ViewShedLayer2 getViewshed() {
            return viewShed;
        }

        public boolean getVisible() {
            return visible;
        }

        public void install() {
            MapOverlayManager.installOverlayRenderer(mapview,
                    this.viewShedRenderer);
            this.visible = true;
        }

        public void uninstall() {
            MapOverlayManager.uninstallOverlayRenderer(mapview,
                    this.viewShedRenderer);
            this.visible = false;
            this.viewShed = null;
            this.viewShedRenderer = null;
        }
    }

    private static ViewShedReceiver instance;
    private static MapView mapView;
    private static final HashMap<String, ArrayList<VsdLayer>> singleVsdLayerMap = new HashMap<>();
    private static final HashMap<String, ArrayList<VsdLayer>> vsdLineLayerMap = new HashMap<>();

    public static synchronized ViewShedReceiver getInstance() {
        if (instance == null)
            instance = new ViewShedReceiver(MapView.getMapView());
        return instance;
    }

    private ViewShedReceiver(MapView mv) {
        mapView = mv;
    }

    public static HashMap<String, ArrayList<VsdLayer>> getSingleVsdLayerMap() {
        return singleVsdLayerMap;
    }

    public static HashMap<String, ArrayList<VsdLayer>> getVsdLineLayerMap() {
        return vsdLineLayerMap;
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action == null)
            return;

        if (!intent.hasExtra(VIEWSHED_UID)
                || intent.getStringExtra(VIEWSHED_UID).length() == 0)
            return;
        String uid = intent.getStringExtra(VIEWSHED_UID);
        switch (action) {
            case ACTION_SHOW_VIEWSHED: {
                //allow for multiple here
                if (!singleVsdLayerMap.containsKey(uid)) {
                    ArrayList<VsdLayer> layerList = new ArrayList<>();
                    layerList.add(new VsdLayer());
                    singleVsdLayerMap.put(uid, layerList);
                }

                VsdLayer layer = singleVsdLayerMap.get(uid).get(0);

                if (intent.hasExtra(EXTRA_ELEV_POINT)) {
                    if (layer.getViewshed().getPointOfInterest() == null)
                        layer.getViewshed().setPointOfInterest(intent
                                .<GeoPoint> getParcelableExtra(
                                        EXTRA_ELEV_POINT));
                    else if (layer.getViewshed().getPointOfInterest()
                            .getLatitude() != intent
                                    .<GeoPoint> getParcelableExtra(
                                            EXTRA_ELEV_POINT)
                                    .getLatitude()
                            ||
                            layer.getViewshed().getPointOfInterest()
                                    .getLongitude() != intent
                                            .<GeoPoint> getParcelableExtra(
                                                    EXTRA_ELEV_POINT)
                                            .getLongitude()
                            ||
                            layer.getViewshed().getPointOfInterest()
                                    .getAltitude() != intent
                                            .<GeoPoint> getParcelableExtra(
                                                    EXTRA_ELEV_POINT)
                                            .getAltitude())
                        layer.getViewshed().setPointOfInterest(intent
                                .<GeoPoint> getParcelableExtra(
                                        EXTRA_ELEV_POINT));
                }
                if (intent.hasExtra(EXTRA_ELEV_RADIUS)) {
                    if (layer.getViewshed().getRadius() != intent
                            .getDoubleExtra(
                                    EXTRA_ELEV_RADIUS, 1000))
                        layer.getViewshed().setRadius(intent.getDoubleExtra(
                                EXTRA_ELEV_RADIUS, 1000));
                }
                if (intent.hasExtra(EXTRA_RESOLUTION)) {
                    layer.getViewshed().setResolution(intent.getIntExtra(
                            EXTRA_RESOLUTION, 201));
                }
                if (intent.hasExtra(EXTRA_ELEV_OPACITY)) {
                    if (layer.getViewshed().getOpacity() != intent.getIntExtra(
                            EXTRA_ELEV_OPACITY, 50))
                        layer.getViewshed().setOpacity(intent.getIntExtra(
                                EXTRA_ELEV_OPACITY, 50));
                }
                if (intent.hasExtra(EXTRA_ELEV_CIRCLE)) {
                    if (layer.getViewshed().getCircle() != intent
                            .getBooleanExtra(
                                    EXTRA_ELEV_CIRCLE, false))
                        layer.getViewshed().setCircle(intent.getBooleanExtra(
                                EXTRA_ELEV_CIRCLE, false));
                } else if (PreferenceManager.getDefaultSharedPreferences(mapView
                        .getContext()).getBoolean(
                                ViewShedReceiver.VIEWSHED_PREFERENCE_CIRCULAR_VIEWSHED,
                                false)) {
                    layer.getViewshed().setCircle(true);
                }

                layer.getViewshed().setCircle(true); // force a circle

                if (!layer.getVisible()) {
                    layer.install();
                }
                break;
            }
            case ACTION_SHOW_VIEWSHED_LINE:
                if (intent.hasExtra(EXTRA_ELEV_POINT)
                        && intent.hasExtra(EXTRA_ELEV_POINT2) &&
                        intent.hasExtra(VIEWSHED_UID2)) {
                    String uidDest = intent.getStringExtra(VIEWSHED_UID2);

                    if (vsdLineLayerMap.containsKey(uid
                            + VIEWSHED_LINE_UID_SEPERATOR + uidDest)) {
                        ArrayList<VsdLayer> layerList = vsdLineLayerMap.get(uid
                                + VIEWSHED_LINE_UID_SEPERATOR + uidDest);
                        if (layerList != null && !layerList.isEmpty()) {
                            for (VsdLayer layer : layerList) {
                                if (layer != null) {
                                    layer.uninstall();
                                }
                            }
                        }
                        vsdLineLayerMap.remove(uid + VIEWSHED_LINE_UID_SEPERATOR
                                + uidDest);
                    }
                    GeoPoint gp1 = intent
                            .getParcelableExtra(EXTRA_ELEV_POINT);
                    GeoPoint gp2 = intent
                            .getParcelableExtra(EXTRA_ELEV_POINT2);
                    int samples = intent.getIntExtra(EXTRA_LINE_SAMPLES, 4);
                    int opacity = 50;
                    if (intent.hasExtra(EXTRA_ELEV_OPACITY)) {
                        opacity = intent.getIntExtra(
                                EXTRA_ELEV_OPACITY, 50);
                    }
                    double distance = gp1.distanceTo(gp2);
                    double bearing = gp1.bearingTo(gp2);
                    double altChange = gp2.getAltitude()
                            - gp1.getAltitude();
                    double startAltVal = gp1.getAltitude();
                    double radius = 1000;
                    if (intent.hasExtra(EXTRA_ELEV_RADIUS)) {
                        radius = intent.getDoubleExtra(EXTRA_ELEV_RADIUS, 1000);
                    }
                    showViewshed(uid + VIEWSHED_LINE_UID_SEPERATOR + uidDest,
                            gp1,
                            radius, opacity);
                    for (int i = 0; i < samples - 1; i++) {
                        GeoPoint gp = GeoCalculations.pointAtDistance(gp1,
                                bearing, (distance / (samples - 1) * (i + 1)));
                        double altval = startAltVal
                                + ((altChange / (samples - 1)) * (i + 1));
                        gp = new GeoPoint(gp.getLatitude(), gp.getLongitude(),
                                altval, AltitudeReference.AGL,
                                GeoPoint.UNKNOWN, GeoPoint.UNKNOWN);
                        showViewshed(
                                uid + VIEWSHED_LINE_UID_SEPERATOR + uidDest,
                                gp, radius,
                                opacity);
                    }

                }
                break;
            case UPDATE_VIEWSHED_INTENSITY:
                if (!intent.hasExtra(EXTRA_ELEV_OPACITY))
                    return;

                if (intent.hasExtra(SHOWING_LINE)) {
                    ArrayList<VsdLayer> layerList = vsdLineLayerMap.get(uid);
                    if (layerList != null && !layerList.isEmpty()) {
                        for (VsdLayer layer : layerList) {
                            if (layer != null) {
                                if (layer.getViewshed() != null) {
                                    layer.getViewshed().setOpacity(
                                            intent.getIntExtra(
                                                    EXTRA_ELEV_OPACITY, 50));
                                }
                            }
                        }
                    }
                } else {
                    if (!singleVsdLayerMap.containsKey(uid))
                        return;

                    VsdLayer layer = singleVsdLayerMap.get(uid).get(0);
                    if (layer.getViewshed() != null) {
                        layer.getViewshed().setOpacity(intent.getIntExtra(
                                EXTRA_ELEV_OPACITY, 50));
                    }
                }
                break;
            case ACTION_DISMISS_VIEWSHED: {
                if (!singleVsdLayerMap.containsKey(uid))
                    return;

                VsdLayer layer = singleVsdLayerMap.get(uid).get(0);
                layer.uninstall();
                singleVsdLayerMap.remove(uid);
                break;
            }
            case ACTION_DISMISS_VIEWSHED_LINE:
                ArrayList<VsdLayer> layerList = vsdLineLayerMap.get(uid);
                if (layerList != null && !layerList.isEmpty()) {
                    for (VsdLayer layer : layerList) {
                        if (layer != null) {
                            layer.uninstall();
                        }
                    }
                }
                vsdLineLayerMap.remove(uid);

                break;
        }
    }

    private void showViewshed(String uid, GeoPoint gp, double radius,
            int opacity) {
        if (!vsdLineLayerMap.containsKey(uid)) {
            ArrayList<VsdLayer> layerList = new ArrayList<>();
            layerList.add(new VsdLayer());
            vsdLineLayerMap.put(uid, layerList);
        } else {
            vsdLineLayerMap.get(uid).add(new VsdLayer());
        }

        VsdLayer layer = vsdLineLayerMap.get(uid).get(
                vsdLineLayerMap.get(uid).size() - 1);

        if (layer.getViewshed().getPointOfInterest() == null)
            layer.getViewshed().setPointOfInterest(gp);
        else if (layer.getViewshed().getPointOfInterest().getLatitude() != gp
                .getLatitude()
                ||
                layer.getViewshed().getPointOfInterest().getLongitude() != gp
                        .getLongitude()
                ||
                layer.getViewshed().getPointOfInterest().getAltitude() != gp
                        .getAltitude())
            layer.getViewshed().setPointOfInterest(gp);

        if (layer.getViewshed().getRadius() != radius)
            layer.getViewshed().setRadius(radius);

        if (layer.getViewshed().getOpacity() != opacity)
            layer.getViewshed().setOpacity(opacity);

        if (!layer.getVisible()) {
            layer.install();
        }
    }

}
