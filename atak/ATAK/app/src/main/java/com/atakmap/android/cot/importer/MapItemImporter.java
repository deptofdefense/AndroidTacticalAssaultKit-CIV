
package com.atakmap.android.cot.importer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.FocusBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationIdRecycler;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.util.Collections2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CoT importer specifically for items on the map
 */
public abstract class MapItemImporter extends CotEventTypeImporter {

    /**
     * Callback interface to allow external components to determines whether or not an imported
     * item should post a notification.
     */
    public interface NotificationFilter {
        /**
         * @param item  The imported item
         * @return  <code>true</code> to post a notification on import, <code>false</code> if no
         *          notification should be posted
         */
        boolean accept(MapItem item);
    }

    private static final String TAG = "MapItemImporter";

    protected static final String FROM_STATESAVER = "StateSaver";
    protected static final String FROM_MISSIONPACKAGE = "MissionPackage";

    protected static final NotificationIdRecycler NOTIFICATION_ID = new NotificationIdRecycler(
            8880, 5);

    private final static Set<NotificationFilter> _notificationFilters = Collections2
            .newIdentityHashSet();

    protected final MapView _mapView;
    protected final Context _context;
    protected final SharedPreferences _prefs;
    protected final MapGroup _group;

    protected MapItemImporter(MapView mapView, MapGroup group,
            Set<String> types) {
        super(mapView, types);
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _group = group;
        /*if (group == null)
            Log.w(TAG, "Importer has null group!", new Throwable());*/
    }

    protected MapItemImporter(MapView mapView, MapGroup group,
            String... types) {
        this(mapView, group, new HashSet<>(Arrays.asList(types)));
    }

    // No type filtering
    protected MapItemImporter(MapView mapView, MapGroup group) {
        this(mapView, group, new HashSet<String>());
    }

    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {
        if (super.importData(event, extras) != ImportResult.SUCCESS)
            return ImportResult.IGNORE;

        MapItem existing = findItem(event);
        ImportResult res = importMapItem(existing, event, extras);
        if (res == ImportResult.SUCCESS) {
            MapItem item = findItem(event);
            if (existing == null && !isLocalImport(extras)) {
                // Notify new map item
                postNotification(item);
            }
            if (item != null) {
                try {
                    dispatchItemImported(item, extras);
                } catch (Exception e) {
                    Log.e(TAG, "Error when notifying " + MapEvent.ITEM_IMPORTED
                            + " listeners for: " + existing, e);
                }
            } else
                Log.w(TAG, "Import successful but item is null: "
                        + event.getType(), new Throwable());
        }
        return res;
    }

    /**
     * Dispatch event for when the item has been imported
     * @param item Map item
     * @param extras Import extras
     */
    protected void dispatchItemImported(MapItem item, Bundle extras) {
        MapEvent.Builder b = new MapEvent.Builder(
                MapEvent.ITEM_IMPORTED);
        b.setItem(item);
        if (extras != null)
            b.setExtras(new Bundle(extras));
        _mapView.getMapEventDispatcher().dispatch(b.build());
    }

    /**
     * Import a map item via CoT event
     * Sub-class implementation is responsible for the following:
     *   - Create the new map item (if it doesn't exist)
     *   - Update item metadata based on event content
     *   - Add the item to a map group (if not already added)
     *
     * @param existing Existing map item (null if new)
     * @param cot CoT event
     * @param extras Import extras
     *
     * @return {@link ImportResult#SUCCESS} if handled successfully
     * {@link ImportResult#FAILURE} if handled but failed
     * {@link ImportResult#IGNORE} if not handled or N/A
     * {@link ImportResult#DEFERRED} if we should try again later
     */
    protected abstract ImportResult importMapItem(MapItem existing,
            CotEvent cot, Bundle extras);

    /**
     * Find a map item given its CoT event
     * This should be a quick operation
     *
     * @return Map item or null if not found
     */
    protected MapItem findItem(String uid) {
        // RootMapGroup deep find UID is faster than regular find
        // since it uses a UID map, so we use it by default
        MapGroup group = _mapView.getRootGroup();
        if (group != null)
            return group.deepFindUID(uid);
        else if (_group != null)
            return _group.deepFindUID(uid);
        return null;
    }

    protected MapItem findItem(CotEvent event) {
        return findItem(event.getUID());
    }

    /**
     * Helper method for adding an item to the defined group for this instance
     *
     * @param item Map item
     */
    protected void addToGroup(MapItem item, MapGroup group) {
        // The group transfer is taken care of within the addItem method
        if (group != null)
            group.addItem(item);
    }

    protected void addToGroup(MapItem item) {
        if (_group == null)
            return;
        addToGroup(item, _group);
    }

    /**
     * Helper method to check if import extras signify a local import
     * depending on the "from" attribute
     *
     * @param extras Import extras
     * @return True if local import
     */
    protected boolean isLocalImport(Bundle extras) {
        String from = extras.getString("from");
        if (from == null)
            return false;
        return from.equals(FROM_STATESAVER)
                || from.equals(FROM_MISSIONPACKAGE);
    }

    /**
     * Helper method to check if import extras signify an import from the
     * statesaver. Items that are imported from the statesaver generally
     * should NOT be persisted upon import.
     *
     * @param extras Import extras
     * @return True if from statesaver
     */
    protected boolean isStateSaverImport(Bundle extras) {
        return FROM_STATESAVER.equals(extras.getString("from"));
    }

    /**
     * Persist a map item if it 'should' be persisted
     * (has the archive flag and isn't being imported from the statesaver)
     *
     * @param item Map item
     * @param extras Import extras
     * @return True if persisted
     */
    protected boolean persist(MapItem item, Bundle extras) {
        if (isStateSaverImport(extras) || item.hasMetaValue("nevercot")
                || !item.hasMetaValue("archive"))
            return false;
        item.persist(_mapView.getMapEventDispatcher(), extras, getClass());
        return true;
    }

    /**
     * Get an to represent this importer's notification
     * Note: Due to a limitation with the Android notification API,
     * this method MUST return an ATAK core icon resource ID
     *
     * @param item Map item (not null)
     * @return Notification icon
     */
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.select_from_map;
    }

    /**
     * Post notification for a newly received map item
     *
     * @param item Map item to post the notification for
     */
    protected void postNotification(MapItem item) {
        if (item == null)
            return;

        synchronized (_notificationFilters) {
            for (NotificationFilter filter : _notificationFilters)
                if (!filter.accept(item))
                    return;
        }

        String ticker = _context.getString(R.string.new_event);
        String title = ATAKUtilities.getDisplayName(item);
        String msg = "";

        GeoPoint point = item instanceof ILocation ? ((ILocation) item)
                .getPoint(null) : null;
        if (point != null)
            msg = CoordinateFormatUtilities.formatToString(point,
                    CoordinateFormat.find(_prefs));

        NotificationUtil.getInstance().postNotification(
                NOTIFICATION_ID.getNotificationId(), getNotificationIcon(item),
                NotificationUtil.WHITE, title, ticker, msg,
                new Intent(FocusBroadcastReceiver.FOCUS)
                        .putExtra("uid", item.getUID())
                        .putExtra("immediateUnfocus", true),
                true);
    }

    public static void addNotificationFilter(NotificationFilter filter) {
        synchronized (_notificationFilters) {
            _notificationFilters.add(filter);
        }
    }

    public static synchronized void removeNotificationFilter(
            NotificationFilter filter) {
        synchronized (_notificationFilters) {
            _notificationFilters.remove(filter);
        }
    }
}
