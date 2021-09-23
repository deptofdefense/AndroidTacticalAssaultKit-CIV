
package com.atakmap.android.editableShapes;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.targetbubble.TargetBubbleReceiver;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Handles common functions for EditablePolylines that require broadcasts / an overarching view of
 * the system beyond a single association _poly. Could simply be it's own component, in
 * components.xml, but that doesn't enforce any concept of dependencies so at the moment this is a
 * class intended to be called by all components which depend on EditablePolylines.
 * 
 * 
 */

public class EditablePolylineReceiver extends BroadcastReceiver {

    public static final String INSERT_EDITABLE_POLYLINE_VERTEX = "com.atakmap.android.maps.INSERT_EDITABLE_POLYLINE_VERTEX";
    public static final String MOVE_EDITABLE_POLYLINE_VERTEX = "com.atakmap.android.maps.MOVE_EDITABLE_POLYLINE_VERTEX";
    public static final String REMOVE_VERTEX = "com.atakmap.android.maps.REMOVE_EDITABLE_POLYLINE_VERTEX";
    public static final String ENTER_LOCATION_VERTEX = "com.atakmap.android.maps.MANUAL_POINT_ENTRY_EDITABLE_POLYLINE_VERTEX";

    static EditablePolylineReceiver instance;

    final MapView _mapView;
    final Context _context;
    final EditablePolylineMoveTool _moveTool;

    public static final String TAG = "EditablePolylineReceiver";

    synchronized public static EditablePolylineReceiver init(MapView mapView,
            Context context) {
        if (instance == null) {
            instance = new EditablePolylineReceiver(mapView, context);
        }
        return instance;
    }

    private EditablePolylineReceiver(MapView mapView, Context context) {
        _mapView = mapView;
        _context = context;
        _moveTool = new EditablePolylineMoveTool(mapView);
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                EditablePolylineMoveTool.TOOL_IDENTIFIER, _moveTool);

        DocumentedIntentFilter showFilter = new DocumentedIntentFilter();
        showFilter.addAction(INSERT_EDITABLE_POLYLINE_VERTEX);
        showFilter.addAction(MOVE_EDITABLE_POLYLINE_VERTEX);
        showFilter.addAction(ENTER_LOCATION_VERTEX);
        showFilter.addAction(REMOVE_VERTEX);

        AtakBroadcast.getInstance().registerReceiver(this, showFilter);
    }

    public void dispose() {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(this);
        } catch (Exception ignored) {
        }
        instance = null;
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {

        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        switch (action) {
            case INSERT_EDITABLE_POLYLINE_VERTEX: {
                // This now handles moves for ALL types of association sets.
                MapItem found = _mapView.getMapItem(extras.getString("uid"));
                // TODO: handle center?? did it before? no.
                if (found instanceof EditablePolyline) {
                    EditablePolyline poly = (EditablePolyline) found;
                    int index = poly.getMetaInteger("hit_index", -1);
                    String hitType = poly.getMetaString("hit_type", "");
                    GeoPoint point = GeoPoint
                            .parseGeoPoint(extras.getString("point"));

                    if (point != null && hitType.equals("line")) {
                        // if it's an assoc marker, we just need to insert a point in it's place
                        index++;
                        poly.getUndoable().run(poly.new InsertPointAction(
                                GeoPointMetaData.wrap(point), index));
                        // TODO: make a combo action for the insert and the move????? how?
                    }
                }
                break;
            }
            case MOVE_EDITABLE_POLYLINE_VERTEX: {
                // This now handles moves for ALL types of association sets.
                MapItem found = _mapView.getMapItem(extras.getString("uid"));
                // TODO: handle center?? did it before? no.
                if (found instanceof EditablePolyline) {
                    Intent i;
                    boolean mgrsEntry = extras.getBoolean("mgrs_entry", false);
                    if (mgrsEntry)
                        i = new Intent(TargetBubbleReceiver.MGRS_ENTRY);
                    else
                        i = new Intent(TargetBubbleReceiver.FINE_ADJUST);
                    i.putExtra("uid", found.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }
                break;
            }
            case ENTER_LOCATION_VERTEX: {
                MapItem found = _mapView.getMapItem(extras.getString("uid"));
                if (found instanceof EditablePolyline)
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            TargetBubbleReceiver.GENERAL_ENTRY)
                                    .putExtra("uid", found.getUID()));
                break;
            }
            case REMOVE_VERTEX:
                if (extras.getString("uid") != null) {
                    MapItem item = _mapView.getMapItem(extras.getString("uid"));

                    if (item != null) {
                        // standard remove dialog from RemoveBroadcastReceiver
                        final int index = item.getMetaInteger("hit_index", -1);
                        String type = item.getMetaString("hit_type", "");

                        if (item instanceof EditablePolyline &&
                                index != -1 &&
                                type.equals("point")) {

                            final EditablePolyline poly = (EditablePolyline) item;
                            final EditAction a = poly.new RemovePointAction(
                                    index);

                            AlertDialog.Builder b = new AlertDialog.Builder(
                                    _context);
                            b.setTitle(
                                    _context.getResources().getString(
                                            R.string.confirmation_dialogue))
                                    .setMessage(
                                            _context.getResources().getString(
                                                    R.string.remove_point_dialogue))
                                    .setPositiveButton(R.string.yes,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(
                                                        DialogInterface arg0,
                                                        int arg1) {
                                                    // don't let user delete last 1 or 2 points? if we do allow
                                                    // that there're other behaviors we'd need to flesh out
                                                    if (poly.getNumPoints() <= 2) {
                                                        Toast.makeText(
                                                                _context,
                                                                _context.getResources()
                                                                        .getString(
                                                                                R.string.remove_point_error),
                                                                Toast.LENGTH_LONG)
                                                                .show();
                                                        return;
                                                    }

                                                    // Delete the point
                                                    try {
                                                        poly.getUndoable()
                                                                .run(a);
                                                    } catch (Exception e) {
                                                        Log.d(TAG,
                                                                "error occurred attempting to undo.",
                                                                e);

                                                    }

                                                }
                                            })
                                    .setNegativeButton(R.string.cancel, null);

                            AlertDialog d = b.create();
                            d.show();
                        }
                    }
                }
                break;
        }

    }
}
