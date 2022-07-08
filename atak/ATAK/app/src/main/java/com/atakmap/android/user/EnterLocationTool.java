
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.LayoutInflater;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.android.gui.CoordDialogView;
import android.view.View.OnClickListener;
import android.view.View;

import com.atakmap.android.gui.RangeBearingInputView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

public class EnterLocationTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "EnterLocationTool";

    public static final String TOOL_NAME = "enter_location";

    private static String _currType;
    private boolean _listeningToMap;
    private boolean _clickBlocked = true;
    private MapView _mapView;
    private EnterLocationDropDownReceiver enterLocation;
    private final SharedPreferences _prefs;

    private final Context con;

    public EnterLocationTool(MapView mapView) {
        super(mapView, TOOL_NAME);
        this._mapView = mapView;
        this.enterLocation = EnterLocationDropDownReceiver.getInstance(mapView);
        con = mapView.getContext();
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(con);
    }

    @Override
    public void dispose() {
        _mapView = null;
        enterLocation = null;
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {

        _currType = extras.getString("current_type", null);
        //String _humanReadable = extras.getString("human_readable", null);
        int checkedPosition = extras.getInt("checked_position", -1);

        // If this isn't a valid type then don't start the tool
        if (checkedPosition == -1 && _currType != null
                && !_currType.equals("damaged")) {
            _requestRemoveMapListener();
            return false;
        }

        _requestAddMapListener();
        _mapView.getMapTouchController().skipDeconfliction(true);

        return super.onToolBegin(extras);
    }

    @Override
    protected void onToolEnd() {
        super.onToolEnd();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _requestRemoveMapListener();
        _listeningToMap = false;
        enterLocation.clearSelection(true);
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                MapMenuReceiver.HIDE_MENU));
    }

    private void _requestAddMapListener() {
        if (!_listeningToMap) {
            TextContainer.getTopInstance().displayPromptForceShow(
                    con.getResources().getString(R.string.location_prompt));
            _mapView.getMapEventDispatcher().pushListeners();
            _clearExtraListeners();
            _mapView.getMapEventDispatcher()
                    .addMapEventListener(MapEvent.ITEM_LONG_PRESS, this);
            _mapView.getMapEventDispatcher()
                    .addMapEventListener(MapEvent.MAP_LONG_PRESS, this);
            _mapView.getMapEventDispatcher()
                    .addMapEventListener(MapEvent.MAP_CLICK, this);
            _mapView.getMapEventDispatcher()
                    .addMapEventListener(MapEvent.MAP_CONFIRMED_CLICK, this);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_DOUBLE_TAP, this);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CONFIRMED_CLICK,
                    this);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK,
                    this);
            _listeningToMap = true;
        }
    }

    private void _requestRemoveMapListener() {
        if (_listeningToMap) {
            TextContainer.getTopInstance().closePrompt();
            _mapView.getMapEventDispatcher().popListeners();
            _listeningToMap = false;
        }
    }

    private void _clearExtraListeners() {
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);
        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.MAP_CONFIRMED_CLICK);
    }

    private void _handleItem(MapItem item) {
        if (_currType.equals("goto") && item.getUID() != null) {
            this.enterLocation.close();
            Intent gotoTool = new Intent();
            gotoTool.setAction("com.atakmap.android.routes.GOTO_NAV_BEGIN");
            gotoTool.putExtra("target", item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(gotoTool);
        }
    }

    // calculates the point using r&b, and then handles that point
    private void _calculateNewPoint(double range, double bearing,
            GeoPoint clickedPoint) {
        double tBearing = ATAKUtilities.convertFromMagneticToTrue(clickedPoint,
                bearing);
        GeoPoint newPoint = GeoCalculations.pointAtDistance(clickedPoint,
                tBearing, range);
        MapItem marker = this.enterLocation
                .processPoint(GeoPointMetaData.wrap(newPoint));
        if (marker != null) {
            RecentlyAddedDropDownReceiver.instance.addToRecentList(marker);
        }
    }

    private void _showRBEntryDialog(final GeoPoint clickedPoint) {
        // On Button click, start a dialog to edit the point location
        AlertDialog.Builder b = new AlertDialog.Builder(_mapView.getContext());
        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());
        // Custom dialog for entering a range and bearing
        final RangeBearingInputView rbView = (RangeBearingInputView) inflater
                .inflate(
                        R.layout.range_bearing_dialog_view, null);
        b.setTitle(R.string.point_dropper_text1)
                .setView(rbView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Double range = rbView.getRange();
                                Double bearing = rbView.getBearing();
                                if (range != null && bearing != null) {
                                    _calculateNewPoint(range, bearing,
                                            clickedPoint);
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void launchEnter() {
        CoordinateFormat _cFormat = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref",
                con.getString(R.string.coord_display_pref_default)));

        AlertDialog.Builder b = new AlertDialog.Builder(con);
        final CoordDialogView coordView = (CoordDialogView) LayoutInflater
                .from(con).inflate(
                        R.layout.draper_coord_dialog, _mapView, false);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);

        coordView.setParameters(null,
                _mapView.getPoint(), _cFormat);

        final AlertDialog ad = b.create();
        ad.show();
        ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = coordView.getPoint();
                        if (p == null) {
                            return; //if the point is null, do nothing
                        }
                        MapItem marker;
                        if (_currType.equals("damaged")) {
                            marker = enterLocation.processCASEVAC(p);
                            requestEndTool();
                        } else {
                            marker = enterLocation.processPoint(p);
                        }
                        if (marker != null) {
                            RecentlyAddedDropDownReceiver.instance
                                    .addToRecentList(marker);
                            CameraController.Programmatic.panTo(
                                    _mapView.getRenderer3(),
                                    p.get(), true);
                        }

                        ad.dismiss();

                    }
                });

    }

    @Override
    public void onMapEvent(MapEvent event) {

        final String type = event.getType();

        if (type.equals(MapEvent.MAP_LONG_PRESS) ||
                type.equals(MapEvent.ITEM_LONG_PRESS)) {
            launchEnter();
        } else if (type.equals(MapEvent.ITEM_CLICK)) {
            MapItem mi = event.getItem();
            GeoPointMetaData gp = findPoint(event);

            MapItem item = this.enterLocation.processPoint(gp, mi);
            if (item != null) {
                RecentlyAddedDropDownReceiver.instance
                        .addToRecentList(item);
            }
        } else if (type.equals(MapEvent.MAP_CLICK)) {
            _clickBlocked = false;
        } else if (type.equals(MapEvent.MAP_CONFIRMED_CLICK)) {

            // Regular map click was blocked
            // used to avoid placing point when closing radial
            if (_clickBlocked)
                return;

            // Regular map click! handle it
            PointF p = event.getPointF();
            GeoPointMetaData gp = _mapView.inverseWithElevation(p.x, p.y);

            MapItem marker;
            if (_currType.equals("damaged")) {
                marker = this.enterLocation.processCASEVAC(gp);
                requestEndTool();
            } else {
                marker = this.enterLocation.processPoint(gp);
            }
            if (marker != null) {
                RecentlyAddedDropDownReceiver.instance.addToRecentList(marker);
            }

            // Reset click blocked state
            _clickBlocked = true;
        } else if (type.equals(MapEvent.ITEM_DOUBLE_TAP)
                && (event.getItem() instanceof PointMapItem)) {
            // double tap on an item! prompt user to enter via range & bearing
            final GeoPoint clickedPoint = new GeoPoint(
                    ((PointMapItem) event.getItem()).getPoint());
            _showRBEntryDialog(clickedPoint);
        } else if (type.equals(MapEvent.ITEM_CONFIRMED_CLICK)) {
            if (_currType.equals("goto")) {
                if (event.getItem() instanceof PointMapItem) {
                    _handleItem(event.getItem());
                    event.getExtras().putBoolean("eventNotHandled", false);// tell the event that we
                                                                           // handled it
                } else {
                    event.getExtras().putBoolean("eventNotHandled", true);
                }
            } else {
                // check to see if user clicked themselves
                String deviceUID = _mapView.getSelfMarker().getUID();
                if (event.getItem() instanceof PointMapItem && // allow user to choose themselves
                                                               // for GPS location
                        event.getItem().getUID().equals(deviceUID)) {
                    GeoPointMetaData point = ((PointMapItem) event.getItem())
                            .getGeoPointMetaData();
                    MapItem marker = this.enterLocation.processPoint(point);
                    if (marker != null) {
                        RecentlyAddedDropDownReceiver.instance
                                .addToRecentList(marker);
                    }
                    event.getExtras().putBoolean("eventNotHandled", false);// tell the event that we
                                                                           // handled it
                } else {
                    // Tell touch controller we don't handle item clicks so we get map clicks
                    // instead.
                    event.getExtras().putBoolean("eventNotHandled", true);
                }
            }
        }

    }
}
