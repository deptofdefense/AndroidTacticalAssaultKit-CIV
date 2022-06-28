
package com.atakmap.android.cot.detail;

import com.atakmap.android.cot.MarkerDetailHandler;
import com.atakmap.android.cot.OpaqueHandler;
import com.atakmap.android.cot.UIDHandler;
import com.atakmap.android.emergency.EmergencyDetailHandler;
import com.atakmap.android.geofence.data.GeoFenceDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.routes.cot.MarkerIncludedRouteDetailHandler;
import com.atakmap.android.toolbars.BullseyeDetailHandler;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CotDetailManager {

    private static final String TAG = "CotDetailManager";

    private static CotDetailManager _instance;

    public static CotDetailManager getInstance() {
        return _instance;
    }

    private final MapView _mapView;

    // Detail handlers
    private final Map<String, Set<CotDetailHandler>> _handlerMap = new HashMap<>();
    private final Set<CotDetailHandler> _handlers = new HashSet<>();

    // Marker-specific detail handlers (legacy; use CotDetailHandler instead)
    private final Map<String, Set<MarkerDetailHandler>> _markerHandlerMap = new HashMap<>();
    private final Set<MarkerDetailHandler> _markerHandlers = new HashSet<>();

    public CotDetailManager(MapView mapView) {
        _mapView = mapView;
        if (_instance == null)
            _instance = this;
        registerDefaultHandlers();
    }

    /**
     * Register a detail handler for the given detail element name.  Registration of a handler
     * object more than once is disallowed.
     *
     * @param handler CoT detail handler
     */
    public synchronized void registerHandler(CotDetailHandler handler) {
        if (_handlers.contains(handler))
            return;
        Set<String> names = handler.getDetailNames();
        for (String key : names) {
            Set<CotDetailHandler> set = _handlerMap.get(key);
            if (set == null) {
                set = new HashSet<>();
                _handlerMap.put(key, set);
            }
            set.add(handler);
        }
        _handlers.add(handler);
    }

    /**
     * Unregister a detail handler
     *
     * @param handler CoT detail handler
     */
    public synchronized void unregisterHandler(CotDetailHandler handler) {
        if (!_handlers.contains(handler))
            return;
        Set<String> names = handler.getDetailNames();
        for (String key : names) {
            Set<CotDetailHandler> set = _handlerMap.get(key);
            if (set != null && set.remove(handler) && set.isEmpty())
                _handlerMap.remove(key);
        }
        _handlers.remove(handler);
    }

    /**
     * Get a list of detail handlers
     *
     * @return List of CoT event detail handlers
     */
    private synchronized List<CotDetailHandler> getHandlers() {
        return new ArrayList<>(_handlers);
    }

    /**
     * Register a marker-specific detail handler
     * This is here for legacy compatibility - detail handlers should
     * extend {@link CotDetailHandler} instead
     *
     * @param detailName Detail name used to lookup the handler
     * @param handler Marker handler
     */
    public synchronized void registerHandler(String detailName,
            MarkerDetailHandler handler) {
        if (_markerHandlers.contains(handler))
            return;
        Set<MarkerDetailHandler> set = _markerHandlerMap.get(detailName);
        if (set == null) {
            set = new HashSet<>();
            _markerHandlerMap.put(detailName, set);
        }
        set.add(handler);
        _markerHandlers.add(handler);
    }

    /**
     * Unregister a marker-specific detail handler
     * This is here for legacy compatibility - detail handlers should
     * extend {@link CotDetailHandler} instead
     *
     * @param handler Marker handler
     */
    public synchronized void unregisterHandler(MarkerDetailHandler handler) {
        if (!_markerHandlers.contains(handler))
            return;
        for (Set<MarkerDetailHandler> handlers : _markerHandlerMap.values())
            handlers.remove(handler);
        _markerHandlers.remove(handler);
    }

    /**
     * Get a list of all registered marker handlers
     *
     * @return List of marker handlers
     */
    private synchronized List<MarkerDetailHandler> getMarkerHandlers() {
        return new ArrayList<>(_markerHandlers);
    }

    /**
     * Create and add CoT details to a given event
     *
     * @param item The item to read from
     * @param event The CoT event to add to
     * @return True if one or more details were added, false if not
     */
    public boolean addDetails(MapItem item, CotEvent event) {
        boolean ret = false;
        CotDetail root = event.getDetail();
        List<CotDetailHandler> handlers = getHandlers();
        for (CotDetailHandler h : handlers) {
            if (h.isSupported(item, event, root))
                ret |= h.toCotDetail(item, event, root);
        }
        if (item instanceof Marker) {
            Marker marker = (Marker) item;
            List<MarkerDetailHandler> markerHandlers = getMarkerHandlers();
            for (MarkerDetailHandler h : markerHandlers)
                h.toCotDetail(marker, root);
        }

        // Include any leftover opaque details in the root node
        OpaqueHandler.getInstance().toCotDetail(item, root);

        return ret;
    }

    /**
     * Given a map item and a cot event, process the cot event details into the appropriate
     * tags within the map item
     * @param item the map item to fill
     * @param event the cot event to use
     * @return the map item correctly reflects the values provided by the cot event.
     */
    public ImportResult processDetails(MapItem item, CotEvent event) {
        CotDetail root = event.getDetail();
        if (root == null)
            return ImportResult.FAILURE;

        // Add all the sets first before calling the process method so we
        // don't run into potential deadlock
        Marker marker = item instanceof Marker ? (Marker) item : null;
        List<ProcessSet> sets = new ArrayList<>();
        List<CotDetail> children = root.getChildren();
        synchronized (this) {
            for (CotDetail d : children) {
                if (d == null)
                    continue;
                String name = d.getElementName();

                // Regular handlers
                Set<CotDetailHandler> handlers = _handlerMap.get(name);
                if (handlers != null) {
                    Set<CotDetailHandler> copy = null;
                    for (CotDetailHandler h : handlers) {
                        if (h.isSupported(item, event, d)) {
                            if (copy == null)
                                copy = new HashSet<>(handlers.size());
                            copy.add(h);
                        }
                    }
                    handlers = copy;
                }

                // Marker handlers
                Set<MarkerDetailHandler> markerHandlers = null;
                if (marker != null) {
                    markerHandlers = _markerHandlerMap.get(name);
                    if (markerHandlers != null)
                        markerHandlers = new HashSet<>(markerHandlers);
                }

                // Check if this detail has any handlers
                if (handlers == null && markerHandlers == null) {
                    // If not then it might be unhandled
                    // Stick it in the opaque details
                    //Log.d(TAG, "Unhandled detail: " + d.getElementName());
                    OpaqueHandler.getInstance().toMarkerMetadata(item, event,
                            d);
                }

                sets.add(new ProcessSet(d, handlers, markerHandlers));
            }
        }

        // Now process the sets
        ImportResult res = ImportResult.SUCCESS;
        for (ProcessSet ps : sets) {
            if (ps.handlers != null) {
                for (CotDetailHandler h : ps.handlers) {
                    ImportResult r = h.toItemMetadata(item, event, ps.detail);
                    if (r == ImportResult.FAILURE)
                        Log.e(TAG, "Failed to process detail: " + ps.detail);
                    res = res.getHigherPriority(r);
                }
            }
            if (marker != null && ps.markerHandlers != null) {
                for (MarkerDetailHandler h : ps.markerHandlers)
                    h.toMarkerMetadata(marker, event, ps.detail);
            }
        }
        return res;
    }

    private static class ProcessSet {

        private final CotDetail detail;
        private final Set<CotDetailHandler> handlers;
        private final Set<MarkerDetailHandler> markerHandlers;

        ProcessSet(CotDetail detail, Set<CotDetailHandler> handlers,
                Set<MarkerDetailHandler> markerHandlers) {
            this.detail = detail;
            this.handlers = handlers;
            this.markerHandlers = markerHandlers;
        }
    }

    private void registerDefaultHandlers() {
        // TODO: Can we consolidate some of these together?
        // i.e. ShapeDetailHandler and CircleDetailHandler,
        // ServicesDetailHandler and RequestDetailHandler, all the team SA
        // details, etc.
        registerHandler(new ContactDetailHandler());
        registerHandler(new PrecisionLocationHandler());
        registerHandler(new TakVersionDetailHandler());
        registerHandler(new LinkDetailHandler(_mapView));
        registerHandler(new AddressDetailHandler());
        registerHandler(new ShapeDetailHandler(_mapView));
        registerHandler(new ImageDetailHandler(_mapView));
        registerHandler(new RequestDetailHandler());
        registerHandler(new ServicesDetailHandler());
        registerHandler(new GeoFenceDetailHandler());
        registerHandler(new TrackDetailHandler());
        registerHandler(new RemarksDetailHandler());
        registerHandler(new ArchiveDetailHandler());
        registerHandler(new GroupDetailHandler());
        registerHandler(new StatusDetailHandler());
        registerHandler(new TadilJHandler());
        registerHandler(new UserIconHandler());
        registerHandler(new ColorDetailHandler());
        registerHandler(new MetaDetailHandler());
        registerHandler(new HeightDetailHandler());
        registerHandler(new CEDetailHandler());
        registerHandler(new CircleDetailHandler(_mapView));
        registerHandler(new TracksDetailHandler());
        registerHandler(new SensorDetailHandler());
        registerHandler(new EmergencyDetailHandler());
        registerHandler(new ForceXDetailHandler(_mapView));
        registerHandler(new BullseyeDetailHandler(_mapView));
        registerHandler(new LabelDetailHandler());
        registerHandler(new StrokeFillDetailHandler());
        registerHandler(new MarkerIncludedRouteDetailHandler());

        // This uses special "injection" logic, which seems unnecessary...
        // XXX - I'm not going to mess with it right now
        registerHandler("uid", new UIDHandler());
    }
}
