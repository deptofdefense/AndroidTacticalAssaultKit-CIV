
package com.atakmap.android.contact;

import android.content.Intent;

import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.HashSet;
import java.util.Set;

public class FilteredContactsManager {

    private static FilteredContactsManager instance;

    private final FilteredContactsDatabase db;

    private final Set<String> filteredContacts = new HashSet<>();

    public static final String ATAK_FILTER_CONTACT = "gov.tak.android" +
            ".FILTER_CONTACT";

    final MapEventDispatcher.MapEventDispatchListener _mapEventHandler = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            final MapItem item = event.getItem();
            if ((item == null) || !anyContactsFiltered())
                return;

            final boolean itemFiltered = isItemFiltered(item);

            // update visibiility; target visibility state is the inverse of filtered state
            item.setVisible(!itemFiltered);
        }
    };

    private FilteredContactsManager() {
        db = new FilteredContactsDatabase();
        // update filter status from storage
        for (String uid : db.getAllUids())
            setContactFiltered(uid, true, false);
        if (!filteredContacts.isEmpty())
            fireFilteredContactsChangedIntent();

        // install notification filter
        MapItemImporter.addNotificationFilter(
                new MapItemImporter.NotificationFilter() {
                    @Override
                    public boolean accept(MapItem item) {
                        return !isItemFiltered(item);
                    }
                });
        // install map event handlers
        MapView.getMapView().getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_IMPORTED, _mapEventHandler);
        MapView.getMapView().getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_ADDED, _mapEventHandler);
    }

    public synchronized static FilteredContactsManager getInstance() {
        if (instance == null) {
            instance = new FilteredContactsManager();
        }
        return instance;
    }

    void fireFilteredContactsChangedIntent() {
        Intent contactFilterIntent = new Intent(ATAK_FILTER_CONTACT);
        AtakBroadcast.getInstance().sendBroadcast(contactFilterIntent);
    }

    public boolean anyContactsFiltered() {
        return (!filteredContacts.isEmpty());
    }

    /**
     * Sets a contact as filtered if it is unfiltered or to remove its
     * filter status if it is already filtered.
     * @param c The contact to be filtered. FilteredContactsDatabase stores filters
     *            contacts based on UID
     * @param filterStatus desired filtered status of the contact, if <code>true</code> the contact
     *                     and all items authored by the contact will be hidden on the map and chat
     *                     messages received from the contact will not be shown.
     * @param fireIntent used to update the contact to display and act as filtered.
     *                   It is true by default.
     */

    public void setContactFiltered(Contact c, boolean filterStatus,
            boolean fireIntent) {
        if (c != null) {
            setContactFiltered(c.contactUUID, filterStatus, fireIntent);
        }
    }

    private void setChildMapItemsForContactVisible(MapView mv,
            String contactUid, final boolean visible) {
        if (contactUid != null) {
            MapGroup rg = mv.getRootGroup();
            rg.deepForEachItem(
                    new MapGroup.OnItemCallback<MapItem>(MapItem.class) {
                        @Override
                        protected boolean onMapItem(MapItem mapItem) {
                            if (visible != mapItem.getVisible()) {
                                if (mapItem.getMetaString("parent_uid", "")
                                        .equals(contactUid)) {
                                    mapItem.setVisible(visible);
                                }
                            }
                            return false;
                        }
                    });
        }
    }

    private void setContactFiltered(String uid, boolean filterStatus,
            boolean fireIntent) {
        synchronized (filteredContacts) {
            MapView mv = MapView.getMapView();
            if (filterStatus == filteredContacts.contains(uid))
                return;

            // update bookkeeping
            if (filterStatus) {
                filteredContacts.add(uid);
                db.addUid(uid);
            } else {
                filteredContacts.remove(uid);
                db.removeUid(uid);
            }

            // reset visibility

            // contact is visible if:
            //   - it is not filtered
            // OR
            //   - it is an emergency or bailout beacon
            final MapItem contact = MapView.getMapView().getMapItem(uid);
            if (contact != null)
                contact.setVisible(!filterStatus
                        || determineIsEmergencyOrBailout(contact));
            // children adopt filter status of contact
            setChildMapItemsForContactVisible(mv, uid, !filterStatus);

            // fire intent
            if (fireIntent) {
                fireFilteredContactsChangedIntent();
            }
        }
    }

    public boolean isContactFiltered(Contact c) {
        return isContactFiltered(c.contactUUID);
    }

    public boolean isContactFiltered(String uid) {
        synchronized (filteredContacts) {
            return filteredContacts.contains(uid);
        }
    }

    /**
     * @param mapItem
     * @return  <code>true</code> if the _item_ is considered filtered
     */
    private boolean isItemFiltered(MapItem mapItem) {
        return (isContactFiltered(mapItem.getUID())
                && !determineIsEmergencyOrBailout(mapItem)) ||
                (!mapItem.getMetaString("parent_uid", "").isEmpty() &&
                        isContactFiltered(
                                mapItem.getMetaString("parent_uid", "")));
    }

    public Set<String> getFilteredContacts() {
        Set<String> list;
        synchronized (filteredContacts) {
            list = new HashSet<>(filteredContacts);
        }
        return list;
    }

    private static boolean determineIsEmergencyOrBailout(MapItem item) {
        return item.getType().startsWith("a-f") &&
                (item.getMetaBoolean("isBailoutIndicator", false) ||
                        item.getMetaBoolean("isEmergencyIndicator", false));
    }

    public void dispose() {
        db.close();
    }
}
