
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.control.ScaleGestureDetector;
import com.atakmap.android.control.ScaleGestureDetector.OnScaleGestureListener;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeconflictionAdapter.DeconflictionType;
import com.atakmap.android.menu.MenuCapabilities;
import com.atakmap.android.routes.Route;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapController;

import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;

public class MapTouchController implements OnTouchListener,
        OnGestureListener,
        OnScaleGestureListener,
        OnDoubleTapListener {

    public static final String TAG = "MapTouchController";

    public static final int MAXITEMS = 24;

    private boolean _recentlyZoomed = false;
    private boolean _lockControls = false;
    private boolean _skipDeconfliction = false;
    private Timer longtouchTimer;

    private DeconflictionDropDownReceiver ddr;
    private final DeconflictionOnStateListener ddosl;

    private MapItem _downHitItem;
    private MapItem _itemPressed;
    private MapItem _itemDragged;
    private boolean _dragStarted;
    private final List<Marker> _pressedMarkers = new LinkedList<>();
    private boolean _mapPressed;
    private boolean _mapDoubleTapDrag;
    private boolean _ignoreDoubleTap;
    private float _mapDoubleTapLastY;
    private final GestureDetector _gestureDetector;
    private final ScaleGestureDetector _scaleGestureDetector;
    private final MapView _mapView;
    private Point _lockedZoomFocus;
    private boolean _inGesture, _inLongPressDrag;
    private final MapTouchEventListener _mapListener;
    private boolean _toolActive;
    private MotionEvent _lastDrag;
    private final android.view.ViewConfiguration viewConfig;

    // if show details is requested, only fire this intent after the
    // drop down receiver is closed (otherwise it will show and get 
    // removed)
    private Intent queuedShowDetails = null;

    // recorded list of candidates derived during the onDown event for use in 
    // in the onRelease 
    private SortedSet<MapItem> onDownHitItems;

    public void lockControls() {
        _lockControls = true;
    }

    public void unlockControls() {
        _lockControls = false;
    }

    public MapTouchController(final MapView view) {
        _gestureDetector = new GestureDetector(view.getContext(), this);
        _gestureDetector.setOnDoubleTapListener(this);
        _scaleGestureDetector = new ScaleGestureDetector(view.getContext(),
                this);
        _mapListener = new MapTouchEventListener(view);

        viewConfig = android.view.ViewConfiguration.get(view.getContext());

        view.addOnTouchListener(this);

        _mapView = view;
        _inGesture = false;
        _toolActive = false;
        MapEventDispatcher dispatcher = view.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.MAP_SCROLL, _mapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_SCALE, _mapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_LONG_PRESS, _mapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_DOUBLE_TAP, _mapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_CONFIRMED_CLICK,
                _mapListener);

        ddr = new DeconflictionDropDownReceiver(_mapView);
        ddosl = new DeconflictionOnStateListener();

        _tiltEnabled = STATE_TILT_DISABLED;

        if (DeveloperOptions.getIntOption(
                "disable-3D-mode", 0) == 0)
            MenuCapabilities.registerSupported("capability.3d");

    }

    public void dispose() {
        if (longtouchTimer != null) {
            longtouchTimer.cancel();
            longtouchTimer.purge();
        }
        longtouchTimer = null;
    }

    protected void onItemLongPress(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_LONG_PRESS);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()))
                .setItem(item);
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected boolean onItemDragStarted(MapItem item, MotionEvent event) {
        _lastDrag = event;
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_DRAG_STARTED);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()))
                .setItem(item)
                .setExtras(new Bundle());
        MapEvent mapEvent = eventBuilder.build();
        eventDispatcher.dispatch(mapEvent);
        return !mapEvent.getExtras().getBoolean("eventNotHandled");
    }

    protected void onItemDragContinued(MapItem item, MotionEvent event) {
        _lastDrag = event;
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_DRAG_CONTINUED);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()))
                .setItem(item);
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected void onItemDragDropped(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_DRAG_DROPPED);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()))
                .setItem(item);
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected void onMapLongPress(MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_LONG_PRESS);
        eventBuilder.setPoint(
                new Point((int) event.getX(), (int) event.getY()));
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected void onItemPress(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_PRESS);
        eventBuilder
                .setItem(item);
        //Not sure about this, I added the point to the event, so even if they
        //click an item we can still know the point aswell
        Point p = new Point();
        p.set(Math.round(event.getX()), Math.round(event.getY()));
        eventBuilder
                .setPoint(p);
        eventDispatcher.dispatch(eventBuilder.build());
        _itemPressed = item;

        if (item.getMetaBoolean("drag", false)) {
            _itemDragged = item;
            _dragStarted = false;
        }
    }

    protected void onMapPress(MotionEvent event) {
        onMapPress(event, _mapView.inverse(event.getX(), event.getY(),
                MapView.InverseMode.RayCast).get());
    }

    protected void onMapPress(MotionEvent event, GeoPoint touchPoint) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();

        Point screenTouchPoint = new Point((int) event.getX(),
                (int) event.getY());

        _mapView.getMapData().putString("touchPoint",
                touchPoint.toString());

        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_PRESS);
        eventBuilder
                .setPoint(screenTouchPoint);
        eventDispatcher.dispatch(eventBuilder.build());
        _mapPressed = true;
    }

    protected void onItemDoubleTap(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_DOUBLE_TAP);
        eventBuilder
                .setItem(item);
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected void onMapDoubleTap(MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_DOUBLE_TAP);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()));
        eventDispatcher.dispatch(eventBuilder.build());
    }

    /**
     * @param item MapItem that was clicked.
     * @param event Event representing the click.
     * @return True if the item click was handled.
     */
    protected boolean onItemSingleTap(MapItem item, MotionEvent event) {
        // nothing
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_CONFIRMED_CLICK);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()))
                .setItem(item)
                .setExtras(new Bundle());
        MapEvent mapEvent = eventBuilder.build();
        eventDispatcher.dispatch(mapEvent);

        // Same hack as below in _dispatchItemClick
        return !mapEvent.getExtras().getBoolean("eventNotHandled");
    }

    protected void onMapSingleTap(MotionEvent event) {
        // nothing
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_CONFIRMED_CLICK);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()));
        eventDispatcher.dispatch(eventBuilder.build());
    }

    protected void onItemRelease(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_RELEASE);
        eventBuilder
                .setItem(item);
        eventDispatcher.dispatch(eventBuilder.build());

        boolean itemClickEventHandled = true;
        if (item == _itemPressed && !_dragStarted) {
            itemClickEventHandled = _dispatchItemClick(item, event);
        }

        // flag a marker as interesting at this time.  this will be used by the offscreen
        // search mechanism to weed out offscreen markers that are close but may no longer be
        // interesting.   New markers are interesting....
        item.setMetaLong("offscreen_interest", SystemClock.elapsedRealtime());

        _mapPressed = false;

        if (!itemClickEventHandled) {
            // item click event was not handled; thus, try making it a map click event instead
            _mapPressed = true;
        } else {
            // If item click was handled, cancel further gestures; prevents a confirmed click from
            // later
            // processing, causing duplicate processing
            // TODO: are there valid cases to handle both??
            // Log.d(TAG, "canceled");
            cancelMotionEvents();
        }

        if (isFreeForm3DEnabled())
            setFreeForm3DItem(item);
    }

    /**
     * @param item MapItem that was clicked.
     * @return True if the item click was handled.
     */
    private boolean _dispatchItemClick(MapItem item) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_CLICK);
        eventBuilder
                .setItem(item)
                .setExtras(new Bundle());
        MapEvent event = eventBuilder.build();
        eventDispatcher.dispatch(event);

        // This is a hack, albeit also the least invasive way to implement this.
        // By allowing some way of identifying when there is a listener but it doesn't do anything,
        // we can accommodate the case where some classes of items on the map should be clickable
        // (ie, for surveying, self marker and probably other surveyors to use a GPS location) and
        // others shouldn't be (for surveying, anything else, especially shapes and such that will
        // annoyingly intercept touches when you try to put objects near / abutting them)

        // Should maybe use a more generalized system where every item listener returns if it
        // handled the event,
        // although that would impact a lot of listeners? This method assumes that if one listener
        // didn't handle,
        // none of them did; probably fine for our purposes, since generally there's only one
        // listener ATM in these cases.

        // If eventNotHandled is not set, it is assumed the event was handled; this was done to
        // maintain
        // compatability, but may not be the most elegant solution.
        return !event.getExtras().getBoolean("eventNotHandled");
    }

    //Added a new dispatch which also contains the event along with the item that was pressed.
    private boolean _dispatchItemClick(MapItem item, MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.ITEM_CLICK);
        Point p = new Point();
        p.set(Math.round(event.getX()), Math.round(event.getY()));
        eventBuilder
                .setItem(item)
                .setExtras(new Bundle())
                .setPoint(p);
        MapEvent eventTest = eventBuilder.build();
        eventDispatcher.dispatch(eventTest);
        return !eventTest.getExtras().getBoolean("eventNotHandled");
    }

    protected void onMapRelease(MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_RELEASE);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()));
        eventDispatcher.dispatch(eventBuilder.build());
        if (_mapPressed) {
            _onMapClick(event);
            // _mapPressed = false;
            // This will get set to false by the map click confirmed, since it has a timer to wait
            // for a double tap it will always fire after a release from the map

        }
        _inGesture = false;
        // Log.d(TAG,"in onMapRelease _inGesture = false");
    }

    private void _onMapClick(MotionEvent event) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_CLICK);
        eventBuilder
                .setPoint(new Point((int) event.getX(), (int) event.getY()));
        eventDispatcher.dispatch(eventBuilder.build());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // Log.d(TAG,"onTouch in MapTouchController, controls locked = "+_lockControls);
        boolean r = false;
        /*if (!_lockControls)*/ { //Let all events through - we'll deal with them via the dispatcher
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Log.d(TAG,"- ACTION_UP");
                _onRelease(event);
                // Ignore double tap if controls are locked after the first tap
                // Prevents double-tap zoom immediately after closing radial
                _ignoreDoubleTap = _lockControls;
            }
            try {
                // Log.d(TAG,"- passing to _scaleGestureDetector");
                r = _scaleGestureDetector.onTouchEvent(event);
                if (!_scaleGestureDetector.isInProgress()) {
                    // Log.d(TAG,"- scale gesture not in progress, passing to _scaleGestureDetector (again?)");
                    r = _gestureDetector.onTouchEvent(event);
                }
            } catch (java.lang.IllegalArgumentException e) {
                Log.e(TAG, "error: ", e);
            }
        }
        // Log.d(TAG,"MapTouchController consumed the event = "+r);
        return r;
    }

    private void _onRelease(MotionEvent event) {

        SortedSet<MapItem> hitItems = onDownHitItems;
        onDownHitItems = null;

        if (!_lockControls) { //Prevent deconfliction, etc.
            if (!_scaleGestureDetector.isInProgress()
                    && !_inGesture &&
                    hitItems != null && hitItems.size() > 1) {
                if (hitItems.size() < MAXITEMS) {
                    buildDropDown(event, hitItems,
                            _toolActive ? DeconflictionType.SET
                                    : DeconflictionType.RADIAL);
                    _mapPressed = false;
                } else {
                    Toast.makeText(
                            _mapView.getContext(),
                            hitItems.size()
                                    + " map items detected, please zoom in",
                            Toast.LENGTH_SHORT).show();
                }
            } else if (!_inGesture) {
                if (ddr.isVisible()) {
                    if (_itemPressed != null)
                        ddosl.ignoreNext();
                    ddr.closeDropDown();
                }
                if (_itemPressed != null)
                    onItemRelease(_itemPressed, event);
            }

            if ((_itemDragged != null) && (_dragStarted)) {
                onItemDragDropped(_itemDragged, event);
            }

            _itemPressed = null;
            _itemDragged = null;
            _dragStarted = false;
            setDoubleTapDragging(false);

            // release any pressed states
            Iterator<Marker> it = _pressedMarkers.iterator();
            while (it.hasNext()) {
                Marker m = it.next();
                it.remove();
                m.setState(m.getState() & ~Marker.STATE_PRESSED_MASK);
            }
        }

        onMapRelease(event);
    }

    @Override
    public boolean onDown(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        final GeoPoint geo = _mapView.inverse(x, y, MapView.InverseMode.RayCast)
                .get();

        _downHitItem = null;
        SortedSet<MapItem> hitItems = _fetchOrthoHitItems(x, y, geo);
        hitItems = filterItems(hitItems, false);
        if (hitItems != null && !hitItems.isEmpty())
            _downHitItem = hitItems.first();

        if (!_skipDeconfliction && hitItems != null && hitItems.size() > 1) {
            onDownHitItems = hitItems;

            /**
             * If there is a dragable item detected, go ahead in queue it up.  
             * If it is finally dragged, then the deconfliction menu will not 
             * come up, otherwise launch the deconfliction menu.
             */
            MapItem dragItem = filterDrag(hitItems);
            if (dragItem != null) {
                if (dragItem instanceof Marker) {
                    Marker marker = (Marker) dragItem;
                    marker.setState(marker.getState()
                            | Marker.STATE_PRESSED_MASK);
                    _pressedMarkers.add(marker);
                }
                onItemPress(dragItem, event);
            }

        } else if (_downHitItem != null && itemListenersExist(_downHitItem)) {
            if (_downHitItem instanceof Marker) {
                Marker marker = (Marker) _downHitItem;
                marker.setState(marker.getState() | Marker.STATE_PRESSED_MASK);
                _pressedMarkers.add(marker);
            }
            onItemPress(_downHitItem, event);
        } else {
            if (geo.isValid()) {
                onMapPress(event, geo);
            }
        }
        return true;
    }

    @Override
    public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
            float arg3) {
        return false;
    }

    @Override
    public void onLongPress(final MotionEvent event) {
        if (!_scaleGestureDetector.isInProgress() && !_mapDoubleTapDrag) {
            float x = event.getX();
            float y = event.getY();
            onDownHitItems = null;

            boolean longPress = true;

            final GeoPoint geo = _mapView
                    .inverse(x, y, MapView.InverseMode.RayCast).get();

            MapItem hitItem = _fetchOrthoHit(x, y, geo);
            SortedSet<MapItem> hitItems = filterItems(
                    _fetchOrthoHitItems(x, y, geo), true);
            if (hitItems != null && hitItems.size() == 1)
                hitItem = hitItems.first();
            if (hitItems != null && hitItems.size() > 1) {
                if (hitItems.size() < MAXITEMS) {
                    longPress = false;
                    ddosl.ignoreNext();
                    buildDropDown(event, hitItems,
                            DeconflictionType.MOVE);
                } else {
                    Toast.makeText(
                            _mapView.getContext(),
                            hitItems.size()
                                    + " map items detected, please zoom in",
                            Toast.LENGTH_SHORT).show();
                }
            } else if (hitItem != null) {
                if (ddr.isVisible()) {
                    ddosl.ignoreNext();
                    ddr.closeDropDown();
                }

                _mapPressed = false;
                _itemPressed = null;
                _itemDragged = null;
                _dragStarted = false;
                onItemLongPress(hitItem, event);
                String uid = hitItem.getUID();
                if (uid != null
                        &&
                        (uid.equals("_SELECT_POINT_") || uid
                                .startsWith(_mapView.getSelfMarker().getUID()
                                        + ".SPI"))) {
                    onMapLongPress(event);
                    _mapPressed = false;
                    _itemPressed = null;
                }
            } else {
                _mapPressed = false;
                _itemPressed = null;
                _itemDragged = null;
                _dragStarted = false;
                onMapLongPress(event);
            }

            _inGesture = true;

            if (longtouchTimer == null) {
                longtouchTimer = new Timer("MapLongTouchTimer");
            }
            longDraggerListener.setInitialMotionEvent(event);
            if (longPress) {
                longtouchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        _inGesture = false;
                        _inLongPressDrag = false;

                        if (_downHitItem != null) {
                            //Log.d(TAG, "shb: long press ended");
                            _mapView.removeOnTouchListener(
                                    MapTouchController.this);
                            _mapView.addOnTouchListener(longDraggerListener);
                            //Log.d(TAG, "shb: transitioned to a new state");
                        }
                    }
                }, 250);
            }
            // Log.d(TAG,"in onLongPress _inGesture = false");
        }
    }

    private MapItem filterDrag(final SortedSet<MapItem> hitItems) {
        if (hitItems != null) {
            for (MapItem hitItem : hitItems) {
                if (hitItem.getMetaBoolean("drag", false)) {
                    return hitItem;
                }
            }
        }
        return null;
    }

    /**
     * RC/AML/2015-06-11: Function to filter out specific types
     * (Shapes,ROZ, RAB Endpoint for now, rely on their center-points instead)
     */
    private SortedSet<MapItem> filterItems(final SortedSet<MapItem> hitItems,
            boolean move) {
        /**
         * To disable deconfliction uncomment the below two line.
         */
        //if (true) 
        //   return new TreeSet<MapItem>();

        // Let tools filter the results first
        if (hitItems != null && !hitItems.isEmpty()) {
            for (DeconflictionListener l : _deconfListeners)
                l.onConflict(hitItems);
        }

        if (hitItems != null && hitItems.size() > 1) {
            Iterator<MapItem> iter = hitItems.iterator();
            List<String> uidList = new ArrayList<>();
            List<String> shapeList = new ArrayList<>();
            List<RangeAndBearingMapItem> rabLines = new ArrayList<>();
            while (iter.hasNext()) {
                MapItem hitItem = iter.next();
                String filterUID = hitItem.getUID();
                String type = hitItem.getType();
                //Log.d(TAG, "touched: " + type);

                // Exclude items with no defined type
                if (type.equals(MapItem.EMPTY_TYPE)) {
                    iter.remove();
                    continue;
                }

                // Item is excluded from deconfliction all together
                if (hitItem.hasMetaValue("deconfliction_exclude")) {
                    iter.remove();
                    continue;
                }

                // Filter out non-movable items if we're trying to move
                if (move && (!(hitItem instanceof PointMapItem)
                        && !(hitItem instanceof MultiPolyline)
                        || !hitItem.getMovable()
                        || type.startsWith("center_b-e-r"))) {
                    iter.remove();
                    continue;
                }

                // Route waypoint selected - add route to excluded shape list
                if (type.equals("b-m-p-w")) {
                    MapItem shape = ATAKUtilities.findAssocShape(hitItem);
                    if (shape instanceof EditablePolyline)
                        shapeList.add(shape.getUID());
                }

                // Remove route if contained in excluded shape list
                else if (hitItem instanceof EditablePolyline
                        && shapeList.contains(hitItem.getUID())) {
                    iter.remove();
                    continue;
                }

                // Avoid selecting both shape and its center marker
                else if (hitItem instanceof AnchoredMapItem) {
                    PointMapItem centerMarker = ((AnchoredMapItem) hitItem)
                            .getAnchorItem();
                    if (centerMarker != null)
                        filterUID = centerMarker.getUID();
                }

                // Avoid selecting both the line and its endpoint
                else if (hitItem instanceof PointMapItem
                        && hitItem.hasMetaValue("rabUUID"))
                    rabLines.addAll(RangeAndBearingMapItem.getUsers(
                            (PointMapItem) hitItem));

                // remove item if UID is already in list
                if (uidList.contains(filterUID))
                    iter.remove();
                else
                    uidList.add(filterUID);
            }
            hitItems.removeAll(rabLines);
        }
        return hitItems;
    }

    private class DeconflictionOnStateListener implements
            DropDown.OnStateListener {
        private boolean ignoreNext = false;

        @Override
        public void onDropDownSelectionRemoved() {
        }

        /**
         * Tell the state listener not fiddle with the inProgress
         * and itemPressed variables for the next closure pump only.
         */
        public synchronized void ignoreNext() {
            ignoreNext = true;
        }

        @Override
        synchronized public void onDropDownClose() {
            if (!ignoreNext) {
                _itemPressed = null;
            }
            ignoreNext = false;

            if (queuedShowDetails != null) {
                Log.d(TAG, "executing broadcast send for showing details");

                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        AtakBroadcast.getInstance().sendBroadcast(
                                queuedShowDetails);
                        queuedShowDetails = null;
                    }
                });
            }

        }

        @Override
        public void onDropDownSizeChanged(double width, double height) {
        }

        @Override
        public void onDropDownVisible(boolean v) {
            if (ddr != null && ddr.getDropDown() != null)
                ddr.getDropDown().setCloseBeforeTool(true);
        }
    }

    private static class DeconflictionDropDownReceiver
            extends DropDownReceiver {
        public DeconflictionDropDownReceiver(final MapView mv) {
            super(mv);
        }

        @Override
        public void disposeImpl() {
        }

        @Override
        public void onReceive(Context ctx, Intent intent) {
        }

        public void setSelected(final MapItem pmi) {
            setSelected(pmi, "asset:/icons/outline.png");
        }

        @Override
        public String getAssociationKey() {
            return "DeconflictionDropDown";
        }
    }

    /**
     * Listener that's fired before sending the item list
     * to the deconfliction drop-down
     */
    private final ConcurrentLinkedQueue<DeconflictionListener> _deconfListeners = new ConcurrentLinkedQueue<>();

    public interface DeconflictionListener {
        /**
         * Fired when there is a conflict.   The caller can manipulate the sorted set for the 
         * deconfliction menu.   No deconfliction menu is shown if the set is of length 0/1 or if 
         * skipDeconfliction is set.
         * @param hitItems the non-null set that is provided for possible manipulation by the implementaion.
         */
        void onConflict(final SortedSet<MapItem> hitItems);
    }

    /**
     * Add a custom deconfliction filter this is responsible for doing something wtih the 
     * sorted set.
     * @param l the deconfliction listener.
     */
    public void addDeconflictionListener(final DeconflictionListener l) {
        if (!_deconfListeners.contains(l))
            _deconfListeners.add(l);
    }

    /**
     * Remove a custom deconfliction filter this is responsible for doing something wtih the 
     * sorted set.
     * @param l the deconfliction listener.
     */
    public void removeDeconflictionListener(final DeconflictionListener l) {
        _deconfListeners.remove(l);
    }

    /**
     * Pan, zoom, and bring up radial and coord overlay for a map item
     * @param item The map item to go to
     * @param select True to bring up radial, false to just focus on
     * @return True if the map was able to go to the item
     */
    public static boolean goTo(MapItem item, boolean select) {
        // Do nothing if the item no longer exists
        if (item == null || item.getGroup() == null)
            return true;

        ArrayList<Intent> intents = new ArrayList<>();

        final int heightScale = item instanceof Marker ? 18 : 1;
        final int widthScale = item instanceof Marker ? 18 : 1;

        MapView mv = MapView.getMapView();
        if (mv != null)
            ATAKUtilities.scaleToFit(mv, item, false,
                    mv.getWidth() / widthScale,
                    mv.getHeight() / heightScale);

        intents.add(new Intent("com.atakmap.android.maps.UNFOCUS"));
        intents.add(new Intent("com.atakmap.android.maps.HIDE_DETAILS"));

        String uid = item.getUID();
        if (item instanceof PointMapItem) {
            // Focus on marker in case it moves
            Intent focus = new Intent("com.atakmap.android.maps.FOCUS");
            focus.putExtra("uid", uid);
            focus.putExtra("useTightZoom", true);
            intents.add(focus);

            if (select) {
                // Open radial and details widgets
                Intent localMenu = new Intent(
                        "com.atakmap.android.maps.SHOW_MENU");
                localMenu.putExtra("uid", uid);

                Intent localDetails = new Intent(
                        "com.atakmap.android.maps.SHOW_DETAILS");
                localDetails.putExtra("uid", uid);

                intents.add(localMenu);
                intents.add(localDetails);
            }
        } else if (item instanceof Shape) {
            // Set focus to shape center point by default
            GeoPointMetaData shapeCenter = ((Shape) item).getCenter();
            if (shapeCenter == null)
                shapeCenter = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);

            String focusPoint = shapeCenter.get().toStringRepresentation();

            // For routes focus on the starting point instead
            if (item instanceof Route) {
                Route route = (Route) item;
                if (route.getNumPoints() > 0) {
                    int focusIndex = route.isReversedDir()
                            ? route.getNumPoints() - 1
                            : 0;
                    if (route.getPoint(focusIndex) != null)
                        focusPoint = route.getPoint(focusIndex).get()
                                .toStringRepresentation();
                    if (!route.hasMetaValue("menu"))
                        route.setMetaString("menu", "menus/b-m-r.xml");
                }
            }

            if (select) {
                // Make sure the radial opens on the focus point
                item.setMetaString("menu_point", focusPoint);
                ((Shape) item).setTouchPoint(shapeCenter.get());

                // Avoid potential crash in case the edit menu is still in use
                item.setMetaInteger("hit_index", 0);
                item.setMetaString("hit_type", "point");

                Intent localMenu = new Intent();
                localMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                localMenu.putExtra("uid", uid);
                intents.add(localMenu);

                // Bring up coordinate overlay
                Intent focus = new Intent();
                focus.setAction("com.atakmap.android.maps.SHOW_DETAILS");
                focus.putExtra("point", focusPoint);
                focus.putExtra("uid", uid);
                intents.add(focus);
            }
        }

        // broadcast intents
        for (Intent i : intents)
            AtakBroadcast.getInstance().sendBroadcast(i);

        return !intents.isEmpty();
    }

    // solely for the purpose of the TrackAdapter //
    void action(DeconflictionType dt, final MapItem hitItem,
            final MotionEvent event) {
        ddr.closeDropDown();
        if (dt == DeconflictionType.SET) {
            onItemPress(hitItem, event);
            _onRelease(event);
        } else if (dt == DeconflictionType.RADIAL) {
            if (hitItem != null) {
                // These are already fired within item_click.xml
                /*Intent focus = new Intent();
                focus.setAction("com.atakmap.android.maps.FOCUS");
                focus.putExtra("uid", hitItem.getUID());
                focus.putExtra("source", TAG);
                
                Intent showMenu = new Intent();
                showMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                showMenu.putExtra("uid", hitItem.getUID());
                
                // broadcast intent
                AtakBroadcast.getInstance().sendBroadcast(showMenu);
                AtakBroadcast.getInstance().sendBroadcast(focus);*/

                Intent showDetails = new Intent();
                showDetails.setAction("com.atakmap.android.maps.SHOW_DETAILS");
                showDetails.putExtra("uid", hitItem.getUID());

                queuedShowDetails = showDetails;

                onItemPress(hitItem, event);
                _onRelease(event);
            } else {
                final GeoPointMetaData geoPoint = _mapView
                        .inverse(new PointF(
                                (int) event.getX(),
                                (int) event
                                        .getY()),
                                MapView.InverseMode.RayCast);
                if (geoPoint.get().isValid()) {
                    onMapPress(event);
                    _onRelease(event);
                }
            }
        } else if (dt == DeconflictionType.MOVE) {
            onItemLongPress(hitItem, event);
        } else {
            Log.d(TAG, "invalid deconfliction type received: " + dt);

        }
    }

    private void buildDropDown(final MotionEvent event,
            SortedSet<MapItem> hitItems,
            final DeconflictionType type) {

        // Build dialog
        if (!hitItems.isEmpty()) {
            // List view + adapter
            final ArrayList<MapItem> tracks = new ArrayList<>(hitItems);

            // Save shape touch point locations
            final SparseArray<GeoPoint> touchPoints = new SparseArray<>();
            for (int i = 0; i < tracks.size(); i++) {
                MapItem item = tracks.get(i);
                if (item instanceof Shape)
                    touchPoints.put(i, ((Shape) item).findTouchPoint());
            }

            final DeconflictionAdapter listAdapter = new DeconflictionAdapter(
                    _mapView.getContext(), tracks, type, this, event);

            LayoutInflater inflater = LayoutInflater
                    .from(_mapView.getContext());
            final View v = inflater.inflate(R.layout.deconfliction_menu, null);

            final ListView trackList = v
                    .findViewById(android.R.id.list);
            trackList.setAdapter(listAdapter);
            trackList.setClickable(true);
            trackList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            final TextView title = v.findViewById(R.id.pickertitle);
            title.setTextSize(18);
            title.setTextColor(Color.GREEN);

            if (type != DeconflictionType.MOVE) {
                title.setText(R.string.select_item);
            } else {
                title.setText(R.string.select_item_move);
            }

            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    ddr = new DeconflictionDropDownReceiver(_mapView);
                    ddr.showDropDown(v,
                            DropDownReceiver.HALF_WIDTH,
                            DropDownReceiver.FULL_HEIGHT,
                            DropDownReceiver.FULL_WIDTH,
                            DropDownReceiver.HALF_HEIGHT,
                            ddosl);
                }
            });

            trackList
                    .setOnItemClickListener(
                            new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent,
                                        View view, final int position,
                                        long id) {
                                    MapItem item = tracks.get(position);
                                    // Restore touch points
                                    GeoPoint gp = touchPoints.get(position);
                                    if (item instanceof Shape && gp != null) {
                                        ((Shape) item).setTouchPoint(gp);
                                        item.setMetaString("menu_point",
                                                gp.toStringRepresentation());
                                    }
                                    action(type, item, event);
                                }
                            });

        }
    }

    public void focus(MapItem item) {
        ddr.setSelected(item);
    }

    /**
     * Check to see if the value ie either a valid CE or LE.
     * @param v the value to check.
     * @return true if the value is valid.
     */
    public static boolean isValidErrorEllipse(final double v) {
        return !Double.isNaN(v) && v > 0.0d;
    }

    /**
     * Checks if hitItem has *any* listeners listening for it's PRESS, RELEASE, or CLICK events.
     * 
     * @param hitItem
     * @return true if anything is listening for hitItem's events
     */
    private boolean itemListenersExist(MapItem hitItem) {
        MapEventDispatcher eventDispatcher = _mapView.getMapEventDispatcher();

        return eventDispatcher.hasMapEventListener(MapEvent.ITEM_CLICK) ||
                eventDispatcher.hasMapEventListener(MapEvent.ITEM_PRESS) ||
                eventDispatcher.hasMapEventListener(MapEvent.ITEM_RELEASE)/*
                                                                           * ||
                                                                           * hitItem.hasMapEventListener
                                                                           * () ||
                                                                           * hitItem.getGroup()
                                                                           * .hasMapEventListener()
                                                                           */;
        // Flaw: can't tell if item/group listeners are click listeners or not, and all
        // items have such listeners, so we're ignoring all such click listeners ATM... Could be
        // bad!
    }

    private MapItem _fetchOrthoHit(float x, float y, GeoPoint geo) {
        return _mapView.getRootGroup().deepHitTest((int) x, (int) y,
                geo,
                _mapView);
    }

    // RC/AML/2015-01-20: Check which tracks were hit and return a sorted set containing them.
    private SortedSet<MapItem> _fetchOrthoHitItems(float x, float y,
            GeoPoint geo) {
        return _mapView.getRootGroup().deepHitTestItems((int) x, (int) y,
                geo,
                _mapView);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx,
            float dy) {
        if ((_itemPressed != null) && (_itemDragged != null)) {
            if (!onItemDragStarted(_itemDragged, e2)) {
                // Cancel drag if the start event was not handled
                _itemDragged = null;
                _itemPressed = null;
                _dragStarted = false;
                return false;
            } else
                _dragStarted = true;
        } else if ((_itemDragged != null) && (_dragStarted)) {
            onItemDragContinued(_itemDragged, e2);
        } else if (!_recentlyZoomed) {
            _itemPressed = null;
            MapEventDispatcher eventDispatcher = _mapView
                    .getMapEventDispatcher();
            MapEvent.Builder eventBuilder = new MapEvent.Builder(
                    MapEvent.MAP_SCROLL);
            eventBuilder
                    .setPoint(new Point((int) dx, (int) dy));
            eventDispatcher.dispatch(eventBuilder.build());
            // draw event for telestration
            MapEventDispatcher drawEventDispatcher = _mapView
                    .getMapEventDispatcher();
            MapEvent.Builder drawEventBuilder = new MapEvent.Builder(
                    MapEvent.MAP_DRAW);
            drawEventBuilder
                    .setPoint(new Point((int) e2.getX(), (int) e2.getY()));
            drawEventDispatcher.dispatch(drawEventBuilder.build());

            // Move focus point
            if (isFreeForm3DEnabled() && _freeFormItem == null)
                setFreeForm3DEnabled(true);
        }

        _recentlyZoomed = false;
        _mapPressed = false;
        _inGesture = true;
        // Log.d(TAG,"in onScroll _inGesture = true");
        _itemPressed = null; // disable item clicks if we've scrolled too
        return true;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        return false;
    }

    private boolean _userOrientation = false;
    private double _originalMapAngle = 0, _originalAngle = 0;

    // Tilt states
    public static final int STATE_TILT_ENABLED = 0;
    public static final int STATE_MANUAL_TILT_DISABLED = 1;
    public static final int STATE_PROGRAMATIC_TILT_DISABLED = 2;
    public static final int STATE_TILT_DISABLED = 3;

    // Tilt map 3D mode
    private int _tiltEnabled = STATE_TILT_ENABLED;

    // Free-form 3D mode
    private boolean _freeForm3DEnabled = false;
    private GeoPoint _freeFormPoint;
    private PointMapItem _freeFormItem;
    private final PointMapItem.OnPointChangedListener _ffPointListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            if (_freeFormItem == item) {
                _mapView.getMapController().panTo(item.getPoint(), true);
                setFreeForm3DPoint(item.getPoint());
            }
        }
    };
    private MapItem.OnGroupChangedListener _ffRemoveListener = new MapItem.OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            if (_freeFormItem == item)
                setFreeForm3DEnabled(false);
        }
    };

    private float _originalFocusX;
    private float _originalFocusY;
    private GeoPointMetaData _originalFocusPoint;

    // internal use for debugging purposes
    private final boolean zoomEnabled = true;

    public void setUserOrientation(boolean orientation) {
        _userOrientation = orientation;
    }

    public boolean isUserOrientationEnabled() {
        return _userOrientation;
    }

    /**
     * Allows for programatic setting of the tilt state.   The 
     * @param state can either be:
     * STATE_TILT_ENABLED=0, 
     * STATE_MANUAL_TILT_DISABLED=1, 
     * STATE_PROGRAMATIC_TILT_DISABLED=2, 
     * STATE_TILT_DISABLED=3 
     */
    public void setTiltEnabledState(int state) {

        if (DeveloperOptions.getIntOption(
                "disable-3D-mode", 0) == 1)
            return;

        _tiltEnabled = state;
    }

    public int getTiltEnabledState() {
        return _tiltEnabled;
    }

    public void setFreeForm3DEnabled(boolean enabled) {

        if (DeveloperOptions.getIntOption("disable-3D-mode", 0) == 1)
            return;

        double tilt = _mapView.getMapTilt();
        GeoPointMetaData focus = _mapView.inverseWithElevation(
                _mapView.getMapController().getFocusX(),
                _mapView.getMapController().getFocusY());
        GeoPoint ffPoint = _freeFormPoint;
        if (ffPoint == null)
            ffPoint = focus.get();
        _freeForm3DEnabled = enabled;
        setFreeForm3DPoint(enabled ? focus.get() : null);
        if (!enabled) {
            if (getTiltEnabledState() != STATE_TILT_ENABLED)
                _mapView.getMapController().tiltBy(-tilt, ffPoint, true);
            if (!isUserOrientationEnabled())
                _mapView.getMapController().rotateTo(0, true);
            setFreeForm3DItem(null);
        }
    }

    /**
     * Set the focused map item in free form 3D mode
     * @param item Map item
     */
    public void setFreeForm3DItem(MapItem item) {

        // Remove listener on existing point
        if (_freeFormItem != null) {
            _freeFormItem.removeOnGroupChangedListener(_ffRemoveListener);
            _freeFormItem.removeOnPointChangedListener(_ffPointListener);
        }

        // Associate with anchor item
        _freeFormItem = getAnchorItem(item);

        if (_freeFormItem != null) {
            GeoPoint point = _freeFormItem.getPoint();
            double height = item.getHeight();
            if (item instanceof Marker && !Double.isNaN(height)) {
                // Apply height offset to marker
                point = new GeoPoint(point, GeoPoint.Access.READ_WRITE);
                double alt = point.getAltitude();
                if (Double.isNaN(alt))
                    alt = 0;
                alt += height;
                point.set(alt);
            } else if (item instanceof Shape)
                point = ((Shape) item).findTouchPoint();
            if (point != null)
                setFreeForm3DPoint(point);
            _freeFormItem.addOnGroupChangedListener(_ffRemoveListener);
            _freeFormItem.addOnPointChangedListener(_ffPointListener);
        }
    }

    public PointMapItem getFreeForm3DItem() {
        return _freeFormItem;
    }

    public void setFreeForm3DPoint(GeoPoint point) {
        _freeFormPoint = point;
    }

    public GeoPoint getFreeForm3DPoint() {
        return _freeFormPoint;
    }

    public boolean isFreeForm3DEnabled() {
        return _freeForm3DEnabled;
    }

    private PointMapItem getAnchorItem(MapItem item) {
        if (item == null)
            return null;
        if (item instanceof PointMapItem)
            return (PointMapItem) item;
        if (item instanceof AnchoredMapItem)
            return ((AnchoredMapItem) item).getAnchorItem();
        return null;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        AtakMapController cont = _mapView.getMapController();
        if (_dragStarted && _itemDragged != null)
            onItemDragDropped(_itemDragged, _lastDrag);
        _recentlyZoomed = true;
        float factor = detector.getScaleFactor();
        int focusx = (int) detector.getFocusX();
        int focusy = (int) detector.getFocusY();

        GeoPoint focusPoint = _originalFocusPoint.get();
        if (_freeFormPoint != null) {
            focusPoint = _freeFormPoint;
            PointF p = _mapView.forward(focusPoint);
            focusx = (int) p.x;
            focusy = (int) p.y;
        } else if (_lockedZoomFocus != null) {
            focusx = _lockedZoomFocus.x == -1 ? (int) cont.getFocusX()
                    : _lockedZoomFocus.x;
            focusy = _lockedZoomFocus.y == -1 ? (int) cont.getFocusY()
                    : _lockedZoomFocus.y;
        }
        _mapPressed = false;
        if (zoomEnabled) {
            MapEvent.Builder eventBuilder = new MapEvent.Builder(
                    MapEvent.MAP_SCALE);
            eventBuilder
                    .setPoint(new Point(focusx, focusy))
                    .setScaleFactor(factor);
            _mapView.getMapEventDispatcher().dispatch(eventBuilder.build());
        }
        _inGesture = true;

        // can't item_click if we're scaling
        _itemPressed = null;
        _itemDragged = null;
        _dragStarted = false;

        // Log.d(TAG,"in onScale _inGesture = true");

        // rotation
        if (_userOrientation || isFreeForm3DEnabled()) {
            double angle = detector.getAngle();
            //this._mapView.getMapController().rotateTo(
            //        _originalMapAngle + (angle - _originalAngle), true);
            Bundle b = new Bundle();
            b.putBoolean("eventNotHandled", true);
            b.putDouble("angle", angle);
            MapEvent.Builder eb = new MapEvent.Builder(MapEvent.MAP_ROTATE);
            eb.setPoint(new Point(focusx, focusy));
            eb.setExtras(b);
            MapEvent evt = eb.build();
            _mapView.getMapEventDispatcher().dispatch(evt);
            if (evt.getExtras().getBoolean("eventNotHandled")) {
                if (_mapView.getMapTilt() > 0d || isFreeForm3DEnabled()) {
                    // XXX - rotateBy requires center, once this is resolves change to use detector
                    //       focus
                    cont.rotateBy((_originalMapAngle + (angle - _originalAngle))
                            - _mapView.getMapRotation(), focusPoint, true);

                } else {
                    cont.rotateBy((_originalMapAngle + (angle - _originalAngle))
                            - _mapView.getMapRotation(),
                            focusx, focusy, true);
                }
            }
        }

        // tilt
        if (_tiltEnabled == STATE_TILT_ENABLED || isFreeForm3DEnabled()) {
            double tiltBy = 0;
            final MotionEvent e0 = detector.getPreviousEvent();
            final MotionEvent e1 = detector.getEvent();
            if (e0.getPointerCount() == 2 && e1.getPointerCount() == 2) {

                final float dx0 = e1.getX(0) - e0.getX(0);
                final float dy0 = e1.getY(0) - e0.getY(0);

                final float dx1 = e1.getX(1) - e0.getX(1);
                final float dy1 = e1.getY(1) - e0.getY(1);

                // make sure both pointers are moving in the same direction to
                // effect a tilt
                if ((dx1 * dx0) >= 0d && (dy1 * dy0) >= 0d) {
                    final double currentTilt = _mapView.getMapTilt();
                    double tilt = -dy0 / 2d;
                    final double maxTilt = _mapView
                            .getMaxMapTilt(_mapView.getMapScale());
                    final double minTilt = _mapView
                            .getMinMapTilt(_mapView.getMapScale());
                    if ((tilt > 0d && maxTilt > currentTilt)
                            ||
                            (tilt < 0d && minTilt < currentTilt)) {
                        final double absTilt = currentTilt + tilt;
                        if (maxTilt < absTilt)
                            tilt = maxTilt - currentTilt;
                        if (minTilt > absTilt)
                            tilt = minTilt - currentTilt;
                        tiltBy = tilt;
                    }
                }
            }
            double newTilt = _mapView.getMapTilt() + tiltBy;
            newTilt = Math.max(newTilt, _mapView.getMinMapTilt());
            newTilt = Math.min(newTilt, _mapView.getMaxMapTilt());
            Bundle b = new Bundle();
            b.putBoolean("eventNotHandled", true);
            b.putDouble("angle", newTilt);
            MapEvent.Builder eb = new MapEvent.Builder(MapEvent.MAP_TILT);
            eb.setPoint(new Point((int) e1.getX(), (int) e1.getY()));
            eb.setExtras(b);
            MapEvent evt = eb.build();
            _mapView.getMapEventDispatcher().dispatch(evt);
            if (Double.compare(tiltBy, 0) != 0 && evt.getExtras()
                    .getBoolean("eventNotHandled"))
                cont.tiltBy(tiltBy, focusPoint, false);
        }

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        // Send a cancel event when scales start to make *sure* that long presses, etc are well and
        // fully canceled
        cancelMotionEvents();

        if (_userOrientation || isFreeForm3DEnabled()) {
            _originalMapAngle = _mapView.getMapRotation();
            try {
                _originalAngle = detector.getAngle();
            } catch (NullPointerException npe) {
                // Address crash log from PlayStore described in ATAK-13717
                return false;
            }
        }

        _originalFocusX = detector.getFocusX();
        _originalFocusY = detector.getFocusY();

        // XXX - requires center for rotateBy -- change to detector focus once this is fixed

        // XXX - use render terrain elevation
        _originalFocusPoint = _mapView.inverseWithElevation(
                _mapView.getMapController().getFocusX(),
                _mapView.getMapController().getFocusY());

        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        if (_ignoreDoubleTap) {
            // Cancel double tap so user can pan map instead
            cancelMotionEvents();
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        MapItem hitItem = _fetchOrthoHit(x, y,
                _mapView.inverse(x, y, MapView.InverseMode.RayCast).get());
        if (hitItem != null) {
            onItemDoubleTap(hitItem, event);
        } else {
            onMapDoubleTap(event);
            setDoubleTapDragging(true);
            _mapDoubleTapLastY = event.getY();
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        boolean dragging = _mapDoubleTapDrag
                && event.getAction() != MotionEvent.ACTION_UP;
        if (!dragging || _ignoreDoubleTap) {
            setDoubleTapDragging(false);
            _inGesture = false;
            return false;
        }

        // Do map zoom
        float scaleFactor = (event.getY() - _mapDoubleTapLastY)
                / (_mapView.getHeight() / 2.5f);
        if (scaleFactor >= 0)
            scaleFactor += 1;
        else
            scaleFactor = 1.0f / (Math.abs(scaleFactor) + 1.0f);
        int focusx = (int) _mapView.getMapController().getFocusX();
        int focusy = (int) _mapView.getMapController().getFocusY();
        if (_lockedZoomFocus != null) {
            if (_lockedZoomFocus.x != -1)
                focusx = _lockedZoomFocus.x;
            if (_lockedZoomFocus.y != -1)
                focusy = _lockedZoomFocus.y;
        }
        MapEvent.Builder eventBuilder = new MapEvent.Builder(
                MapEvent.MAP_SCALE);
        eventBuilder
                .setPoint(new Point(focusx, focusy))
                .setScaleFactor(scaleFactor);
        _mapView.getMapEventDispatcher().dispatch(eventBuilder.build());
        _mapDoubleTapLastY = event.getY();
        _inGesture = true;
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean handled = false;
        if (_downHitItem != null) {
            handled = onItemSingleTap(_downHitItem, event);
        }

        // If not handled as an item tap, try as a map tap.
        if (!handled && _mapPressed) {
            onMapSingleTap(event);
            _mapPressed = false;
        }
        return true;
    }

    private void setDoubleTapDragging(boolean dragging) {
        _mapDoubleTapDrag = dragging;
        _gestureDetector.setIsLongpressEnabled(!_mapDoubleTapDrag);
    }

    /**
     * Lock the scale focus to whatever the focus point
     * This will update when the map view size changes
     */
    public void lockScaleFocus() {
        lockScaleFocus(-1, -1);
    }

    public void lockScaleFocus(int pixelx, int pixely) {
        _lockedZoomFocus = new Point(pixelx, pixely);
    }

    public void unlockScaleFocus() {
        _lockedZoomFocus = null;
    }

    public boolean getInGesture() {
        return _inGesture;
    }

    public boolean isLongPressDragging() {
        return _inLongPressDrag;
    }

    public void cancelMotionEvents() {
        MotionEvent cancelEvent = MotionEvent.obtain(0, 0,
                MotionEvent.ACTION_CANCEL, 0, 0, 0);
        _gestureDetector.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    public void setToolActive(boolean toolActive) {
        _toolActive = toolActive;
    }

    public void skipDeconfliction(boolean skip) {
        _skipDeconfliction = skip;
    }

    LongTouchDraggerListener longDraggerListener = new LongTouchDraggerListener();

    class LongTouchDraggerListener implements OnTouchListener {

        float x;
        float y;

        /**
         * Sets the initial motion event to be considered when considering 
         * movement to be considered a drag.
         * @param me the motion event.
         */
        public void setInitialMotionEvent(final MotionEvent me) {
            x = me.getX();
            y = me.getY();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int slop = viewConfig.getScaledTouchSlop();
            final int action = event.getAction();

            if (action == MotionEvent.ACTION_MOVE) {
                if (!_inLongPressDrag) {
                    //System.out.println("SHB: " +  Math.abs(x - event.getX()) );
                    if ((Math.abs(x - event.getX()) > slop / 2f) ||
                            (Math.abs(y - event.getY()) > slop / 2f)) {
                        onItemDragStarted(_downHitItem, event);
                        _inLongPressDrag = true;
                    }
                } else {
                    onItemDragContinued(_downHitItem, event);
                }
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                if (!_inLongPressDrag) {
                    _mapView.removeOnTouchListener(this);
                    _mapView.addOnTouchListener(MapTouchController.this);
                } else {
                    _inLongPressDrag = false;
                    onItemDragDropped(_downHitItem, event);
                    _onRelease(event);
                    _mapView.removeOnTouchListener(this);
                    _mapView.addOnTouchListener(MapTouchController.this);
                }
            } else {
                _mapView.removeOnTouchListener(this);
                _mapView.addOnTouchListener(MapTouchController.this);
                _inLongPressDrag = false;
                if (action == MotionEvent.ACTION_DOWN) {
                    // Need to pass this upward so it's processed as a map click
                    // This is the actual root cause of ATAK-6935
                    MapTouchController.this.onTouch(v, event);
                } else {
                    Log.d(TAG, "other action detected: " + action);
                }
            }
            return true;
        }
    }

}
