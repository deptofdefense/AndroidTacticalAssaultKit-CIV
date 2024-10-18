
package com.atakmap.android.toolbar.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnVisibleChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.DragMarkerHelper;
import com.atakmap.app.R;
import com.atakmap.comms.ReportingRate;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class MovePointTool extends Tool implements OnVisibleChangedListener {

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbar.tools.LONG_PRESS_MOVE";
    private static final String TAG = "MovePointTool";
    private PointMapItem itemToMove;
    private MultiPolyline itemMP;
    private GeoPointMetaData touchPointMP;
    private static final ArrayList<String> typesToPrompt = new ArrayList<>();

    //Map to freeze the movement of certain types when given tools are active <type, toolID>
    private static final Map<String, String> freezeMoveWhenActive = new HashMap<>();

    public MovePointTool(MapView mapView) {
        super(mapView, TOOL_IDENTIFIER);
    }

    @Override
    public void dispose() {
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        String uid = extras.getString("uid");
        if (FileSystemUtils.isEmpty(uid))
            return false;

        final MapItem item = _mapView.getRootGroup().deepFindUID(uid);
        if (item == null) {
            return false;
        } else if (item instanceof MultiPolyline
                && item.getMovable()) {
            itemMP = (MultiPolyline) item;
            listenForTouchMP();
            return true;
        }
        if (!(item instanceof PointMapItem))
            return false;

        if (!item.getMovable())
            return false;

        itemToMove = (PointMapItem) item;

        Tool active = ToolManagerBroadcastReceiver.getInstance()
                .getActiveTool();
        //if there is an active tool, and that tool has items that are not supposed to be moved
        //when the tool is active prevent the move
        if (active != null
                && freezeMoveWhenActive.containsValue(active.getIdentifier())) {
            for (String freezeType : freezeMoveWhenActive.keySet())
                if (itemToMove.getType().startsWith(freezeType)) {
                    /* If JM is a plugin will the active tool still show up as expected? */
                    if (active.getIdentifier().contentEquals(
                            freezeMoveWhenActive.get(freezeType))) {
                        // no DIPs can be moved while the realtime jump tool is active
                        return false;
                    }
                }
        }

        // check to see if a tool has registered this type to prompt the user with a 
        // confirmation dialog before it's allowed to be moved
        boolean prompt = false;
        for (String promptType : typesToPrompt) {
            if (itemToMove.getType().startsWith(promptType)) {
                prompt = true;
                break;
            }
        }

        if (prompt) {
            String name = itemToMove.getMetaString("callsign", null);
            AlertDialog.Builder b = new AlertDialog.Builder(
                    _mapView.getContext());
            b.setTitle(
                    _mapView.getContext().getString(R.string.move_space)
                            + name
                            + _mapView.getContext().getString(
                                    R.string.question_mark_symbol))
                    .setMessage(
                            _mapView.getContext().getString(
                                    R.string.are_you_sure_move)
                                    + name
                                    + _mapView.getContext().getString(
                                            R.string.question_mark_symbol))
                    .setPositiveButton(R.string.yes, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1) {
                            listenForTouch();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestEndTool();
                        }
                    });
            AlertDialog d = b.create();
            d.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    requestEndTool();
                }
            });
            d.show();
            return true;
        } else
            listenForTouch();

        return true;
    }

    /**
     * Add a type that should show the user a confirmation prompt before 
     * allowing the point to be moved
     * 
     * @param type - the type that should be prompted before moving
     */
    public static void addPromptType(String type) {
        if (!typesToPrompt.contains(type))
            typesToPrompt.add(type);
    }

    /**
     * Add a CoT type that should not be moved while a given tool is active
     * 
     * @param type - the CoT type to prevent being moved
     * @param toolID - the tool ID that is active when preventing the movement
     */
    public static void freezeMoveOnTypeWhenToolActive(String type,
            String toolID) {
        if (!freezeMoveWhenActive.containsKey(type))
            freezeMoveWhenActive.put(type, toolID);
    }

    private void listenForTouchMP() {
        String text = _mapView.getResources().getString(
                R.string.move_point_prompt2);
        if (itemMP.hasMetaValue("callsign")
                && itemMP.getMetaString("callsign", null) != null)
            text += itemMP.getMetaString("callsign", null);
        else if (itemMP.getUID().length() < 15)
            // if the callsign is small enough to be human readable, use that
            text += itemMP.getUID();
        else
            text += _mapView.getResources().getString(
                    R.string.move_this_point_prompt);

        TextContainer.getInstance().displayPrompt(text);

        // push all the dispatch listeners
        _mapView.getMapEventDispatcher().pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        //Somehow listener is getting dispatched twice so remove one of them here.
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, mapClickMP);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, mapClickMP);

        // If item becomes invisible, tool should stop
        itemMP.addOnVisibleChangedListener(this);
        touchPointMP = GeoPointMetaData.wrap(itemMP.getClickPoint());
        _mapView.getMapTouchController().skipDeconfliction(true);
    }

    /**
     * Display the prompt to the user and listen for the touch event
     */
    private void listenForTouch() {
        String text = _mapView.getResources().getString(
                R.string.move_point_prompt);
        if (itemToMove.getMetaString("shapeUID", null) != null)
            text = _mapView.getResources().getString(
                    R.string.move_point_prompt2);

        if (itemToMove.getType().equals("b-r-f-h-c"))
            //CASEVACs are identified by title
            text += itemToMove.getMetaString("title", null);
        else if (itemToMove.hasMetaValue("callsign")
                && itemToMove.getMetaString("callsign", null) != null)
            text += itemToMove.getMetaString("callsign", null);
        else if (itemToMove.getUID().length() < 15)
            // if the callsign is small enough to be human readable, use that
            text += itemToMove.getUID();
        else
            text += _mapView.getResources().getString(
                    R.string.move_this_point_prompt);

        TextContainer.getInstance().displayPrompt(text);

        // push all the dispatch listeners
        _mapView.getMapEventDispatcher().pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, mapClick);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, mapClick);
        AtakBroadcast.getInstance().registerReceiver(locReceiver,
                new DocumentedIntentFilter(ReportingRate.REPORT_LOCATION,
                        "Track if the self marker is still movable"));
        if (itemToMove instanceof Marker)
            DragMarkerHelper.getInstance().startDrag((Marker) itemToMove,
                    mapClick);

        // If item becomes invisible, tool should stop
        itemToMove.addOnVisibleChangedListener(this);
        _mapView.getMapTouchController().skipDeconfliction(true);
    }

    @Override
    protected void onToolEnd() {
        DragMarkerHelper.getInstance().endDrag();
        if (itemToMove != null || itemMP != null)
            _mapView.getMapEventDispatcher().popListeners();

        if (itemToMove != null)
            itemToMove.removeOnVisibleChangedListener(this);

        AtakBroadcast.getInstance().unregisterReceiver(locReceiver);

        itemMP = null;
        itemToMove = null;
        touchPointMP = null;
        TextContainer.getInstance().closePrompt();
        _mapView.getMapTouchController().skipDeconfliction(false);
    }

    private final MapEventDispatcher.MapEventDispatchListener mapClick = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            PointF p = event.getPointF();
            if (p == null)
                return;

            // calculate the new ground point
            GeoPointMetaData newPoint = _mapView.inverseWithElevation(p.x, p.y);

            // get the original ground point.
            GeoPointMetaData oldPoint = itemToMove.getGeoPointMetaData();

            if (oldPoint.getAltitudeSource().equals(GeoPointMetaData.USER)) {
                // reuse the old altitude, but given the new lat, and lon - no guarantee of CE or LE
                // can be made.
                newPoint = GeoPointMetaData.wrap(
                        new GeoPoint(newPoint.get().getLatitude(),
                                newPoint.get().getLongitude(),
                                oldPoint.get().getAltitude()),
                        GeoPointMetaData.USER, GeoPointMetaData.USER);
            }

            String type = itemToMove.getType();
            if (type.startsWith("b-m-p-j-dip")) {
                Intent i = new Intent("com.atakmap.maps.jumpmaster.MOVE_DIP");
                i.putExtra("uid", itemToMove.getUID());
                i.putExtra("point", newPoint.get().toStringRepresentation());
                AtakBroadcast.getInstance().sendBroadcast(i);
            } else if (type.equals(QuickPicReceiver.QUICK_PIC_IMAGE_TYPE)) {
                Intent i = new Intent(QuickPicReceiver.QUICK_PIC_MOVE);
                i.putExtra("uid", itemToMove.getUID());
                i.putExtra("point", newPoint.get().toStringRepresentation());
                AtakBroadcast.getInstance().sendBroadcast(i);
            } else {
                itemToMove.setPoint(newPoint);
                itemToMove.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());

                // move the point and then report that it has been moved
                if (ATAKUtilities.isSelf(_mapView, itemToMove)) {
                    Log.d(TAG,
                            "Moving self marker, sending a system broadcast for now until CotService is augmented");
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(ReportingRate.REPORT_LOCATION)
                                    .putExtra("reason",
                                            "Self marker manually moved"));
                }
            }
            itemMP = null;
            requestEndTool();
        }
    };

    private final MapEventDispatcher.MapEventDispatchListener mapClickMP = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            PointF p = event.getPointF();
            if (p == null)
                return;

            // calculate the new ground point
            GeoPointMetaData newPoint = _mapView.inverseWithElevation(p.x, p.y);

            // get the original ground point.
            GeoPointMetaData oldPoint = touchPointMP != null ? touchPointMP
                    : itemMP.getCenter();
            if (oldPoint.getAltitudeSource().equals(GeoPointMetaData.USER)) {
                newPoint = GeoPointMetaData.wrap(
                        new GeoPoint(newPoint.get().getLatitude(),
                                newPoint.get().getLongitude(),
                                oldPoint.get().getAltitude()),
                        GeoPointMetaData.USER, GeoPointMetaData.USER);

            }
            itemMP.move(oldPoint, newPoint);
            itemMP.persist(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.MAP_CLICK, this);
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_CLICK, this);
            itemToMove = null;
            requestEndTool();
        }
    };

    private final BroadcastReceiver locReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (itemToMove != null && itemToMove == _mapView.getSelfMarker()
                    && !itemToMove.getMovable())
                requestEndTool();
        }
    };

    @Override
    public void onVisibleChanged(MapItem item) {
        if (!item.getVisible())
            requestEndTool();
    }

}
