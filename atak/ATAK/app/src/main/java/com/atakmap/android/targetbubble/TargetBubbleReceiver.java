
package com.atakmap.android.targetbubble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdaterCompat;
import com.atakmap.android.tools.ActionBarReceiver;

import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.gui.FastMGRS;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.targetbubble.MapTargetBubble.OnLocationChangedListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.map.elevation.ElevationManager;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.AtakMapController;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.app.R;

import android.view.LayoutInflater;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Listens for intent and aguments that SHOW or DISMISS the MapTargetBubble overlay. A show intent
 * can have the following extras: * point - the initial point to show * uid - the uid of the
 * PointMapItem to change as the target bubble moves * adjustUID - the uid to adjust when the target
 * bubble is dismissed
 * 
 * 
 */
public class TargetBubbleReceiver extends BroadcastReceiver implements
        MapWidget.OnClickListener, View.OnKeyListener, View.OnTouchListener {

    private final static String TAG = "TargetBubbleReceiver";

    public static final String FINE_ADJUST = "com.atakmap.android.maps.FINE_ADJUST";
    public static final String MGRS_ENTRY = "com.atakmap.android.maps.MGRS_ENTRY";
    public static final String GENERAL_ENTRY = "com.atakmap.android.maps.GENERAL_ENTRY";

    private int squareSide;
    private final MetaMapPoint _targetPoint;
    private PointMapItem _editPoint;
    private boolean _manualDismiss;
    private boolean _motionDown;
    private GestureDetector _gestureDetector;
    private MapTargetBubble _bubble;
    private final MapView _mapView;
    private final AtakPreferences _prefs;
    private final MarkerIconWidget exitReticle;
    private final MarkerIconWidget cancelReticle;
    private final LayoutWidget _rootLayoutWidget;

    private double _restoreTilt;
    private int _restoreTiltEnabled;
    private boolean _enable3D;

    private boolean barPreviouslyHidden, hidSelfWidget;

    private static TargetBubbleReceiver _instance;

    private TargetBubbleImpl tbi;

    interface TargetBubbleImpl {
        /**
         * Allows for interception of an alternative target bubble implementation.
         * @param uid the unique identifier of the map item to be refined.
         * @return true if the target bubble was able to be launched for the provided 
         * map item.  false if the default target bubble is to be used.
         */
        boolean launchTargetBubble(String uid);

        boolean dismissTargetBubble();
    }

    public TargetBubbleReceiver(MapView mapView) {
        _mapView = mapView;
        _prefs = new AtakPreferences(mapView);

        // add the meta point for the target
        MetaMapPoint targetPoint = new MetaMapPoint(
                GeoPointMetaData.wrap(GeoPoint.ZERO_POINT),
                "TARGET_POINT");
        targetPoint.setMetaString("entry",
                "user"); /* XXX: hack to trigger elevation lookup */
        _mapView.getRootGroup().addItem(targetPoint);
        _targetPoint = targetPoint;

        _rootLayoutWidget = (LayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");

        exitReticle = new MarkerIconWidget() {

            @Override
            public void orientationChanged() {
                exitReticle.setPoint(
                        (_mapView.getWidth() / 2f + squareSide / 2f) - 22,
                        (_mapView.getHeight() / 2f + squareSide / 4f) - 30);
            }
        };

        final String imageUri = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.done;

        final Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        final Icon icon = builder.build();
        exitReticle.setIcon(icon);

        cancelReticle = new MarkerIconWidget() {

            @Override
            public void orientationChanged() {
                cancelReticle.setPoint(
                        (_mapView.getWidth() / 2f - squareSide / 2f) - 78,
                        (_mapView.getHeight() / 2f + squareSide / 4f) - 30);
            }
        };

        final String cimageUri = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.cancel;

        final Icon.Builder cbuilder = new Icon.Builder();
        cbuilder.setAnchor(0, 0);
        cbuilder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        cbuilder.setSize(48, 48);
        cbuilder.setImageUri(Icon.STATE_DEFAULT, cimageUri);

        final Icon cicon = cbuilder.build();
        cancelReticle.setIcon(cicon);

        exitReticle.addOnClickListener(this);
        cancelReticle.addOnClickListener(this);

        _instance = this;

    }

    /**
     * Obtain an instance of the TargetBubbleReceiver class used to register a different reticle tool
     */
    static public TargetBubbleReceiver getInstance() {
        return _instance;
    }

    /**
     * Sets a primary target bubble implementation that overrides the existing implementation.   
     * This is primarily used by the PointMensurationTool, but could be used by other implementations.
     * @param tbi The target bubble implementation to use, can be null
     */
    public void setTargetBubbleImpl(TargetBubbleImpl tbi) {
        this.tbi = tbi;
    }

    public synchronized void setExitReticleVisible(boolean state) {
        if (state) {
            _rootLayoutWidget.removeWidget(exitReticle);
            _rootLayoutWidget.addWidget(exitReticle);
            _rootLayoutWidget.removeWidget(cancelReticle);
            _rootLayoutWidget.addWidget(cancelReticle);
        } else {
            _rootLayoutWidget.removeWidget(exitReticle);
            _rootLayoutWidget.removeWidget(cancelReticle);
        }
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (widget == cancelReticle && _editPoint != null) {
            _mapView.getMapController().panTo(_editPoint.getPoint(), true);
            _editPoint = null;
        }
        _dismissBubble();
    }

    @Override
    public void onReceive(Context cxt, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        // mitigate possible race condition setting the tbi to null after the null check but
        // before the call.
        TargetBubbleImpl currImpl = tbi;
        if (currImpl != null && intent.hasExtra("uid")) {
            if (action.equals(FINE_ADJUST)) {
                final String uid = intent.getStringExtra("uid");
                if (currImpl.launchTargetBubble(uid))
                    return;
            } else if (action.equals(
                    "com.atakmap.android.maps.UNFOCUS")) {
                if (currImpl.dismissTargetBubble())
                    return;
            }
        }

        if (action.equals("com.atakmap.android.maps.UNFOCUS")) {
            _dismissBubble();
        } else if (action.equals("com.atakmap.android.maps.FOCUS")
                && intent.hasExtra("point")) {
            intent.putExtra("manualDismiss", true);
            _showTargetBubble(intent, false);
        } else if (action.equals(FINE_ADJUST)
                && intent.hasExtra("uid")) {
            _showTargetBubble(intent, false);
        } else if (action.equals(MGRS_ENTRY)
                && intent.hasExtra("uid")) {
            _mgrsEntry(intent);
        } else if (action.equals(GENERAL_ENTRY)
                && intent.hasExtra("uid")) {
            _generalEntry(intent);
        }
    }

    private void _showTargetBubble(final Intent intent, boolean delayed) {
        double tilt = _mapView.getMapTilt();
        if (!delayed) {
            _restoreTilt = tilt;
            _restoreTiltEnabled = _mapView.getMapTouchController()
                    .getTiltEnabledState();
            _enable3D = _prefs.get("status_3d_enabled", false);
        }
        if (tilt != 0) {
            GeoPoint focus = _mapView.inverseWithElevation(
                    _mapView.getMapController().getFocusX(),
                    _mapView.getMapController().getFocusY()).get();

            _mapView.getMapController().tiltBy(-tilt, focus, false);
            _mapView.getMapTouchController().setTiltEnabledState(
                    MapTouchController.STATE_TILT_DISABLED);

            _mapView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    _showTargetBubble(intent, true);
                }
            }, 100);
            return;
        }
        if (_bubble == null) {
            MapTargetBubble bubble = onTargetBubbleShow(_mapView, intent);
            if (bubble == null)
                return;
            _bubble = bubble;
            _bubble.addOnLocationChangedListener(
                    new OnLocationChangedListener() {
                        @Override
                        public void onMapTargetBubbleLocationChanged(
                                MapTargetBubble bubble) {
                            _setMapDataTargetPoint();
                        }
                    });
            _mapView.pushStack(MapView.RenderStack.TARGETING);
            _mapView.addLayer(MapView.RenderStack.TARGETING, _bubble);
            _setMapDataTargetPoint();
        }

        hidSelfWidget = SelfCoordOverlayUpdaterCompat.showGPSWidget(false);

        barPreviouslyHidden = _mapView.getActionBarHeight() == 0;
        Intent i = new Intent(ActionBarReceiver.TOGGLE_ACTIONBAR);
        i.putExtra("show", false);
        i.putExtra("handle", false);
        i.putExtra("toolbar", false);
        AtakBroadcast.getInstance().sendBroadcast(i);

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMode.NORTH_UP.getIntent()));
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent("com.atakmap.android.mapcompass.HIDE"));

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.maps.SHOW_DETAILS")
                        .putExtra("uid", _targetPoint.getUID()));

        setExitReticleVisible(true);
    }

    private void _mgrsEntry(Intent intent) {
        String uid = intent.getStringExtra("uid");
        _editPoint = findPoint(uid);
        if (_editPoint == null)
            return;

        FastMGRS entry = new FastMGRS(_mapView, false,
                new FastMGRS.OnEnterListener() {
                    @Override
                    public void onEnter(GeoPointMetaData gp) {
                        onTargetBubbleDismiss(gp);
                    }
                });
        entry.show();
    }

    private void _generalEntry(Intent intent) {
        String uid = intent.getStringExtra("uid");
        _editPoint = findPoint(uid);
        if (_editPoint == null)
            return;

        AlertDialog.Builder b = new AlertDialog.Builder(_mapView.getContext());
        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());

        final CoordDialogView coordView = (CoordDialogView) inflater
                .inflate(R.layout.draper_coord_dialog, null);
        b.setTitle("Enter Coordinate: ");
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(_mapView.getContext());
        CoordinateFormat _cFormat = CoordinateFormat.find(sp.getString(
                "coord_display_pref",
                _mapView.getContext().getString(
                        R.string.coord_display_pref_default)));
        coordView.setParameters(_editPoint.getGeoPointMetaData(),
                _mapView.getPoint(),
                _cFormat);

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = coordView.getPoint();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {
                            onTargetBubbleDismiss(p);
                            _mapView.getMapController().panTo(p.get(), true);
                        }
                        locDialog.dismiss();
                    }
                });
    }

    private PointMapItem findPoint(String uid) {
        if (uid != null) {
            MapItem item = _mapView.getRootGroup().deepFindUID(uid);
            if (item instanceof PointMapItem) {
                PointMapItem pmi = (PointMapItem) item;
                // Check if item belongs to a polyline (route)

                final String assocUID = item.getMetaString("assocSetUID", null);
                if (assocUID != null) {
                    MapItem assoc = _mapView.getRootGroup().deepFindUID(
                            assocUID);
                    if (assoc instanceof EditablePolyline) {
                        // Find its index and move that way
                        EditablePolyline poly = (EditablePolyline) assoc;
                        int index = poly.getIndexOfMarker(pmi);
                        if (index != -1) {
                            PointMapItem shpMrk = new ShapePointMapItem(
                                    poly, index);
                            String title = pmi.getTitle();
                            if (!FileSystemUtils.isEmpty(title))
                                shpMrk.setTitle(title);
                            return shpMrk;
                        }
                    }
                }
                return pmi;
            } else if (item instanceof DrawingCircle) {
                DrawingCircle circle = (DrawingCircle) item;
                return new RadiusPointMapItem(circle, GeoPointMetaData.wrap(
                        circle.findTouchPoint()));
            } else if (item instanceof EditablePolyline) {
                // Create temp item for managing position of shape vertex
                EditablePolyline poly = (EditablePolyline) item;
                String type = item.getMetaString("hit_type", "");
                int index = item.getMetaInteger("hit_index", -1);
                if (index >= 0 && index < poly.getNumPoints()
                        && poly.getPoint(index) != null)
                    return new ShapePointMapItem(poly, index,
                            type.equals("line"));
            }
        }
        return null;
    }

    /**
     * Given a latitude and longitude, will look up the corresponding altitude and return 
     * a new GeoPoint.
     */
    private GeoPointMetaData createPoint(final double lat, final double lon) {

        // if the elevation was user entered, retain 

        if (_editPoint != null) {
            GeoPointMetaData prevPt = _editPoint.getGeoPointMetaData();
            if (prevPt.getAltitudeSource().equals(GeoPointMetaData.USER)) {
                //Log.d(TAG, "user defined altitude detected");
                // update the altitude
                return GeoPointMetaData.wrap(
                        new GeoPoint(lat, lon, prevPt.get().getAltitude()),
                        GeoPointMetaData.USER,
                        prevPt.getAltitudeSource());

            }
        }

        // the return should be the same as what occurs when the user drops the point initially
        // The only difference with this return is that the 3-D model is not considered during the
        // movement.    This is because the target bubble fails to show the 3-D model during the
        // fine adjust process.
        // see MapView::inverseWithElevation
        final GeoPointMetaData gpm = new GeoPointMetaData();
        double alt = ElevationManager.getElevation(lat, lon, null, gpm);
        return gpm;
    }

    private void _setMapDataTargetPoint() {
        MapData bundle = _mapView.getMapData();

        GeoPointMetaData bubblePoint = createPoint(_bubble.getLatitude(),
                _bubble.getLongitude());
        bundle.putString("targetPoint", bubblePoint.get().toString());
        _targetPoint.setPoint(bubblePoint);
    }

    protected MapTargetBubble onTargetBubbleShow(MapView mapView,
            Intent intent) {

        int threeQuartersHeight = 3 * mapView.getHeight() / 4;
        int threeQuartersWidth = 3 * mapView.getWidth() / 4;
        squareSide = threeQuartersHeight;
        if (threeQuartersWidth < threeQuartersHeight) {
            squareSide = threeQuartersWidth;
        }
        exitReticle.orientationChanged();
        cancelReticle.orientationChanged();

        AtakMapController ctrl = mapView.getMapController();
        Point focusPoint = ctrl.getFocusPoint();

        int x = focusPoint.x - squareSide / 2;
        int y = focusPoint.y - squareSide / 2;

        String title = null;
        GeoPoint point = null;
        if (intent.hasExtra("point")) {
            point = GeoPoint.parseGeoPoint(intent.getStringExtra("point"));
            if (point == null) // there was a failure somewhere
            {
                StringBuilder output = new StringBuilder(intent.getAction());
                output.append("\r\n");
                final Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        Object obj = extras.get(key);
                        output.append(key);
                        output.append(" = ");
                        output.append(obj);
                        output.append("\r\n");
                    }
                }
                try { // write out to disk
                    final Date date = CoordinatedTime.currentDate();
                    File file = FileSystemUtils
                            .getItem("support/logs/point_parse_error_"
                                    + date.getTime()
                                    + ".txt");
                    FileOutputStream fos = IOProviderFactory
                            .getOutputStream(file);
                    try {
                        fos.write(output.toString().getBytes());
                    } finally {
                        fos.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        } else if (intent.hasExtra("uid")) {
            String uid = intent.getStringExtra("uid");
            _editPoint = findPoint(uid);
            if (_editPoint != null) {
                point = _editPoint.getPoint();
                title = _editPoint.getTitle();
                if (title == null)
                    title = _editPoint.getMetaString("label", null);
            }
        }

        if (point == null)
            return null;

        double bubbleResolution = _mapView.getMapResolution() / 4d;

        MapTargetBubble bubble = new MapTargetBubble(_mapView,
                x, y,
                squareSide, squareSide,
                _mapView.mapResolutionAsMapScale(bubbleResolution));

        // Bundle data = intent.getExtras();
        _manualDismiss = intent.getBooleanExtra("manualDismiss", false);

        _targetPoint.setTitle(title);
        _targetPoint.setMetaBoolean("displayRnB", true);
        if (_editPoint != null)
            _targetPoint.setMetaString("targetUID", _editPoint.getUID());

        _setLocation(bubble, point.getLatitude(), point.getLongitude());

        mapView.addOnTouchListenerAt(1, this);
        mapView.addOnKeyListener(this);

        return bubble;
    }

    protected void onTargetBubbleDismiss(MapTargetBubble bubble) {
        onTargetBubbleDismiss(createPoint(bubble.getLatitude(),
                bubble.getLongitude()));
    }

    protected void onTargetBubbleDismiss(GeoPointMetaData point) {
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.maps.HIDE_DETAILS"));
        if (_editPoint != null) {
            String type;
            type = _editPoint.getType();

            if (type != null) {
                // allow undo if drawing
                switch (type) {
                    case "corner_u-d-r": {
                        Intent intent = new Intent(
                                "com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT");
                        intent.putExtra("type", "corner_u-d-r");
                        intent.putExtra("uid", _editPoint.getUID());
                        intent.putExtra("lat",
                                String.valueOf(point.get().getLatitude()));
                        intent.putExtra("lon",
                                String.valueOf(point.get().getLongitude()));
                        intent.putExtra("from", "targetBubble");
                        intent.putExtra("alt",
                                String.valueOf(point.get().getAltitude()));
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                        break;
                    }
                    case "side_u-d-r": {
                        Intent intent = new Intent(
                                "com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT");
                        intent.putExtra("type", "side_u-d-r");
                        intent.putExtra("uid", _editPoint.getUID());
                        intent.putExtra("lat",
                                String.valueOf(point.get().getLatitude()));
                        intent.putExtra("lon",
                                String.valueOf(point.get().getLongitude()));
                        intent.putExtra("alt",
                                String.valueOf(point.get().getAltitude()));
                        intent.putExtra("from", "targetBubble");
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                        break;
                    }
                    default:
                        _editPoint.setPoint(point);
                        break;
                }
            } else
                _editPoint.setPoint(point);

            if (!_editPoint.getType().startsWith("b-m-p-j-dip")) {
                _editPoint.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            } else {
                Intent i = new Intent("com.atakmap.maps.jumpmaster.MOVE_DIP");
                i.putExtra("uid", _editPoint.getUID());
                i.putExtra("point", point.toString());
                AtakBroadcast.getInstance().sendBroadcast(i);
            }

            _editPoint = null;
        }

        if (hidSelfWidget && _prefs.get("show_self_coordinate_overlay", true))
            SelfCoordOverlayUpdater.getInstance().showGPSWidget(true);
        Intent i = new Intent(ActionBarReceiver.TOGGLE_ACTIONBAR);
        if (barPreviouslyHidden) {
            i.putExtra("toolbar", true);
            i.putExtra("handle", true);
            i.putExtra("show", false);
        } else
            i.putExtra("show", true);
        AtakBroadcast.getInstance().sendBroadcast(i);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.mapcompass.SHOW"));
    }

    private void _dismissBubble() {
        if (_bubble != null) {
            _mapView.removeLayer(MapView.RenderStack.TARGETING, _bubble);
            _mapView.popStack(MapView.RenderStack.TARGETING);

            _prefs.set("status_3d_enabled", _enable3D);
            _mapView.getMapTouchController().setTiltEnabledState(
                    _restoreTiltEnabled);
            _mapView.getMapController().tiltBy(_restoreTilt,
                    _targetPoint.getPoint(), false);
            onTargetBubbleDismiss(_bubble);
            _mapView.removeOnTouchListener(this);
            _mapView.removeOnKeyListener(this);
            _bubble = null;
        }
        setExitReticleVisible(false);

    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            _dismissBubble();
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (_gestureDetector == null)
            _gestureDetector = new GestureDetector(_mapView.getContext(),
                    _gestureListener);
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                setExitReticleVisible(true);
                return true;
            case MotionEvent.ACTION_DOWN:
                setExitReticleVisible(false);
                if (!_motionDown) {
                    // hide the menu while moving new points
                    Intent localMenu = new Intent();
                    localMenu
                            .setAction("com.atakmap.android.maps.HIDE_MENU");
                    localMenu.putExtra("uid", _mapView.getSelfMarker()
                            .getUID());
                    AtakBroadcast.getInstance().sendBroadcast(localMenu);
                }
                _motionDown = true;
                break;
        }
        _gestureDetector.onTouchEvent(event);

        return true;
    }

    private final GestureDetector.OnGestureListener _gestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            _scrollBy(distanceX / 2f, distanceY / 2f);
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    };

    void _scrollBy(float x, float y) {
        // legacy zoom ~204800
        double bubbleScale = 1.0d / 1926.0d;
        if (_bubble != null)
            bubbleScale = _bubble.getMapScale();
        _mapView.getMapController().panByAtScale(x, y, bubbleScale, false);
        if (_bubble != null) {
            Point focusPoint = _mapView.getMapController().getFocusPoint();
            GeoPointMetaData panToPoint = _mapView.inverse(focusPoint.x,
                    focusPoint.y,
                    MapView.InverseMode.RayCast);
            _setLocation(_bubble, panToPoint.get().getLatitude(),
                    panToPoint.get().getLongitude());
        }
    }

    void _setLocation(final MapTargetBubble bubble, double lat, double lng) {
        bubble.setLocation(lat, lng);
    }

    private static class MetaPointMapItem extends PointMapItem {

        MetaPointMapItem(MapItem item, GeoPointMetaData point) {
            super(point, item.getUID());
            setTitle(item.getTitle());
            setMetaBoolean("nevercot", true);
            setType(null);
        }

        @Override
        public void persist(MapEventDispatcher dispatcher, Bundle extras,
                Class<?> clazz) {
        }
    }

    /**
     * Wrapper class for editable shape vertices
     */
    private static class ShapePointMapItem extends MetaPointMapItem {

        private final EditablePolyline shape;
        private final int index;
        private final boolean line;

        ShapePointMapItem(EditablePolyline shape, int index, boolean line) {
            super(shape, line ? GeoPointMetaData.wrap(shape.findTouchPoint())
                    : shape.getPoint(index));
            this.shape = shape;
            this.index = index;
            this.line = line;
        }

        ShapePointMapItem(EditablePolyline shape, int index) {
            this(shape, index, false);
        }

        @Override
        public void setPoint(GeoPointMetaData point) {
            EditAction act;
            if (this.shape.hasMetaValue("static_shape"))
                act = this.shape.new MovePointAction(this.index,
                        getGeoPointMetaData(),
                        point);
            else if (line)
                act = this.shape.new InsertPointAction(point, this.index + 1);
            else
                act = this.shape.new MovePointAction(this.index, point);
            Undoable u = this.shape.getUndoable();
            if (u != null)
                u.run(act);
            else
                act.run();
        }
    }

    private class RadiusPointMapItem extends MetaPointMapItem {

        private final DrawingCircle circle;

        RadiusPointMapItem(DrawingCircle circle, GeoPointMetaData point) {
            super(circle, point);
            this.circle = circle;
        }

        @Override
        public void setPoint(GeoPointMetaData point) {
            double radius = point.get().distanceTo(circle.getCenterPoint());
            circle.setRadius(radius);
            circle.persist(_mapView.getMapEventDispatcher(), null, getClass());
        }
    }
}
