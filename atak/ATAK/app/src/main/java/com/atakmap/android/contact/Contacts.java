
package com.atakmap.android.contact;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.contact.Contact.UpdateStatus;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Contacts implements MapEventDispatcher.MapEventDispatchListener {

    // TODO: get rid of singleton

    // TODO:  getContactsByConnector(String) : ArrayList<Contact>
    // TODO:  getContactsByRole(String) : ArrayList<Contact>
    // TODO:  getContactsByTeam(String) : ArrayList<Contact>
    // TODO:  getContactUUIDsByRole(String) : ArrayList<String>
    // TODO:  getContactUUIDsByTeam(String) : ArrayList<String>

    final static public String TAG = "Contacts";
    public static final String USER_GROUPS = "UserGroups";
    public static final String TEAM_GROUPS = "TeamGroups";
    private final List<Contact> contacts = new ArrayList<>();
    private final GroupContact rootGroup;

    private final Map<String, Contact> uidmap = new HashMap<>();

    private static final ConcurrentLinkedQueue<OnContactsChangedListener> contactsChangedListeners = new ConcurrentLinkedQueue<>();

    private static Contacts instance;

    private Contacts() {
        MapView mv = MapView.getMapView();
        this.rootGroup = new GroupContact("RootContactGroup",
                mv != null ? mv.getContext().getString(
                        R.string.actionbar_contacts) : "Contacts",
                false);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.utc_time_set");
        AtakBroadcast.getInstance().registerReceiver(timeDriftDetected, filter);

        MapView.getMapView().getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    synchronized public static Contacts getInstance() {
        if (instance == null)
            instance = new Contacts();

        return instance;

    }

    public GroupContact getRootGroup() {
        return this.rootGroup;
    }

    /**
     * Check if the supplied contact is registered (valid)
     * @param c Contact object
     * @return True if the contact is valid
     */
    public boolean validContact(Contact c) {
        synchronized (contacts) {
            return c != null && uidmap.containsKey(c.getUID());
        }
    }

    /**
     * Add contact to collection of contacts
     *  
     * @param contact Contact to add
     */
    public void addContact(GroupContact parent, Contact contact) {
        if (contact == null)
            return;
        if (parent == null) {
            Log.w(TAG, "Failed to add contact " + contact.getName()
                    + " to null parent, adding to root group instead.");
            parent = this.rootGroup;
        }

        //Log.d(TAG, "Adding " + contact.getName() + " to " + parent.getName());
        if (contact instanceof GroupContact) {
            // In case children weren't added to this list
            GroupContact gc = (GroupContact) contact;
            for (Contact c : gc.getAllContacts(false)) {
                // Don't automatically add individual contact children
                // These should be added explicitly when the user joins
                if (!(c instanceof IndividualContact))
                    addContact(gc, c);
            }
        }
        boolean changed = false;
        synchronized (contacts) {
            if (!uidmap.containsKey(contact.getUID())) {
                contacts.add(contact);
                uidmap.put(contact.getUID(), contact);
                changed = true;
            }
        }
        parent.addContact(contact);
        if (changed) {
            dispatchSizeChangedEvents();
            updateTotalUnreadCount();
        }
    }

    public void addContact(Contact contact) {
        addContact(rootGroup, contact);
    }

    /**
     * Remove contact from collection of contacts
     *  
     * @param contact Contact to remove
     */
    public void removeContact(final Contact contact) {
        if (contact == null)
            return;

        Log.d(TAG, "removeContact: " + contact);
        // Remove contact from master list
        synchronized (contacts) {
            contacts.remove(contact);
            uidmap.remove(contact.getUID());
        }

        // Remove contact from hierarchy
        Contact parent = getContactByUuid(contact.getParentUID());
        if (parent instanceof GroupContact)
            ((GroupContact) parent).removeContact(contact);

        dispatchSizeChangedEvents();
        updateTotalUnreadCount();
    }

    public void removeContactByUuid(final String uuid) {
        Contact contactToRemove = getContactByUuid(uuid);
        if (contactToRemove != null)
            removeContact(contactToRemove);
    }

    public static List<Contact> fromUIDs(List<String> uids) {
        List<Contact> contacts = new ArrayList<>(uids.size());
        for (String uid : uids)
            contacts.add(Contacts.getInstance().getContactByUuid(uid));
        return contacts;
    }

    public static List<String> toUIDs(List<Contact> contacts) {
        List<String> uids = new ArrayList<>();
        for (Contact c : contacts) {
            if (c != null)
                uids.add(c.getUID());
        }
        return uids;
    }

    /**
     * Get a copy of all contacts, so it can be used without violating thread 
     * safety.
     * 
     * @return a copy of all of the contacts
     */
    public List<Contact> getAllContacts() {
        synchronized (contacts) {
            return new ArrayList<>(contacts);
        }
    }

    public List<String> getAllIndividualContactUuids() {
        return getAllContactsOfClass(IndividualContact.class);
    }

    private List<String> getAllContactsOfClass(Class<?> classType) {
        List<String> uuidsToReturn = new ArrayList<>();
        synchronized (contacts) {
            for (Contact contact : contacts) {
                UpdateStatus status = contact.getUpdateStatus();
                if (classType.isInstance(contact) && status != null
                        && !status.equals(UpdateStatus.NA)
                        && !contact.getExtras().getBoolean("fakeGroup")) {
                    uuidsToReturn.add(contact.getUID());
                }
            }
            return uuidsToReturn;
        }
    }

    /**
     * Iterate through contacts to find contact with specified uuid.
     * 
     * @param uuid the unique identifier to use
     * @return get a contact given the unique identifier.
     */
    public Contact getContactByUuid(final String uuid) {
        if (FileSystemUtils.isEmpty(uuid))
            return null;

        if (this.rootGroup != null
                && FileSystemUtils.isEquals(this.rootGroup.getUID(), uuid))
            return this.rootGroup;

        synchronized (contacts) {
            return uidmap.get(uuid);
        }
    }

    /**
     * Create a paths bundle for everything under this group
     * Meant to be called on user groups only
     * @param group Group contact
     * @return Bundle containing the path string arrays
     */
    public static Bundle buildPaths(Contact group) {
        Bundle pathsBundle = new Bundle();
        if (!(group instanceof GroupContact))
            return pathsBundle;

        // Find top-level user group (Groups -> [group name])
        GroupContact gc = ((GroupContact) group).getRootUserGroup();
        if (gc == null)
            return pathsBundle;

        // Build paths for all users under top-level user group
        String selfUID = MapView.getDeviceUid();
        Bundle rootPaths = buildLocalPaths(gc);
        if (gc.isUserCreated())
            rootPaths.putBundle(selfUID, buildLocalPaths(
                    CotMapComponent.getInstance().getSelfContact(false)));
        pathsBundle.putBundle(gc.getUID(), rootPaths);
        return pathsBundle;
    }

    private static Bundle buildLocalPaths(Contact contact) {
        Bundle paths = new Bundle();
        if (contact == null)
            return paths;
        paths.putString("uid", contact.getUID());
        paths.putString("name", contact.getName());
        paths.putString("type",
                contact instanceof GroupContact ? "group" : "contact");
        if (!(contact instanceof GroupContact))
            return paths;
        for (Contact c : ((GroupContact) contact).getAllContacts(false)) {
            Bundle childPaths = buildLocalPaths(c);
            paths.putBundle(c.getUID(), childPaths);
        }
        return paths;
    }

    /**
     * Iterate through contacts to find contacts with specified uuids.
     * 
     * @param uuids List of UUIDs to find
     * @return List of individual contacts
     */
    public IndividualContact[] getIndividualContactsByUuid(List<String> uuids) {
        Set<IndividualContact> ret = new HashSet<>();
        synchronized (contacts) {
            for (String uuid : uuids) {
                Contact contact = uidmap.get(uuid);
                if (contact instanceof IndividualContact)
                    ret.add((IndividualContact) contact);
            }
        }
        return ret.toArray(new IndividualContact[0]);
    }

    /**
     * Iterate through contacts and find first contact with specified name.
     * 
     * @param callsign the callsign to use in the search
     * @return the first contact that matches
     */
    public Contact getFirstContactWithCallsign(final String callsign) {
        if (FileSystemUtils.isEmpty(callsign))
            return null;
        Contact contactToReturn = null;
        synchronized (contacts) {
            for (Contact contact : contacts) {
                if (callsign.equals(contact.getName())) {
                    contactToReturn = contact;
                    break;
                }
            }
        }

        return contactToReturn;
    }

    public List<String> getAllContactsInTeam(final String team) {
        List<String> ret = new ArrayList<>();
        synchronized (contacts) {
            for (Contact contact : contacts) {
                if (contact.getExtras().getString("team", "none").equals(team))
                    ret.add(contact.getUID());
            }
        }
        return ret;
    }

    public List<String> getAllContactsWithRole(final String role) {
        List<String> ret = new ArrayList<>();
        synchronized (contacts) {
            for (Contact contact : contacts) {
                if (contact.getExtras().getString("role", "none").equals(role))
                    ret.add(contact.getUID());
            }
            return ret;
        }
    }

    /**
     * Get uuids of all the contacts.
     * 
     * @return a list of all uids
     */
    public List<String> getAllContactUuids() {
        List<String> uuidsToReturn = new ArrayList<>();
        synchronized (contacts) {
            for (Contact contact : contacts) {
                UpdateStatus status = contact.getUpdateStatus();
                if (status != null && !status.equals(UpdateStatus.NA))
                    uuidsToReturn.add(contact.getUID());
            }

            return uuidsToReturn;
        }
    }

    public void updateTotalUnreadCount() {

        // Refresh unread count at most once per second
        // Too many action bar refreshes prevents buttons from being clickable
        refreshUnread.exec();

        //refresh the contact list rows and other listeners as well...
        dispatchContactChangedEvent(null);
    }

    private void disposeAllContacts() {
        synchronized (contacts) {
            contacts.clear();
            uidmap.clear();
        }
    }

    /*
     *  Used when properties are changed on a Contact in the Contracts list.
     */
    public interface OnContactsChangedListener {
        /**
         * Fired when the contact list changes size
         * @param contacts the contact list
         */
        void onContactsSizeChange(Contacts contacts);

        /**
         * Fired when a contact changes such as name or if the contact is stale
         * @param uuid the uuid for the contact that changed
         */
        void onContactChanged(String uuid);
    }

    /**
     * Adds a listener for when the Contact list or a specific contact is changed
     * @param listener the listener for the event.
     */
    public void addListener(OnContactsChangedListener listener) {
        contactsChangedListeners.add(listener);
    }

    /**
     * Removes the listener for when the Contact list or a specific contact is changed
     * @param listener the listener for the event.
     */
    public void removeListener(OnContactsChangedListener listener) {
        contactsChangedListeners.remove(listener);
    }

    public void dispose() {
        contactsChangedListeners.clear();
        disposeAllContacts();
        AtakBroadcast.getInstance().unregisterReceiver(timeDriftDetected);

    }

    private void dispatchSizeChangedEvents() {
        for (OnContactsChangedListener listener : contactsChangedListeners) {
            listener.onContactsSizeChange(this);
        }
    }

    void dispatchContactChangedEvent(String uuid) {
        for (OnContactsChangedListener listener : contactsChangedListeners) {
            listener.onContactChanged(uuid);
        }
    }

    private final BroadcastReceiver timeDriftDetected = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (contacts) {
                Log.d(TAG, "time drift detected based on GPS");
                for (Contact c : contacts) {
                    /**
                                        if (c instanceof IndividualContact) { 
                                           IndividualContact ic = (IndividualContact)c;
                                           IpConnector ipConnector = (IpConnector)ic.getConnector(IndividualContact.ConnectorType.IP);
                                           
                                           if (ipConnector != null) {
                                                ipConnector.updateLastSeen(ipConnector.getLastSeen().addMilliseconds( -1 * (int)CoordinatedTime.getCoordinatedTimeOffset()));
                                                Log.d(TAG, "updating indvidual contact based on time shift: " + ic + " " + CoordinatedTime.getCoordinatedTimeOffset());
                                           }
                                        }
                    **/
                }
            }

        }
    };

    @Override
    public void onMapEvent(MapEvent event) {
        MapItem mi = event.getItem();
        if (mi != null && mi.getType().startsWith("a-f"))
            removeContactByUuid(mi.getUID());
    }

    private static final int refreshRate = 1000;
    private final LimitingThread refreshUnread = new LimitingThread(
            "RefreshUnread", new Runnable() {

                private int _totalUnread = 0;

                @Override
                public void run() {
                    try {
                        if (CotMapComponent.getInstance() != null
                                && CotMapComponent.getInstance()
                                        .getContactConnectorMgr() != null) {
                            int totalUnread = rootGroup.calculateUnread();
                            if (_totalUnread != totalUnread) {
                                _totalUnread = totalUnread;
                                // Update the unread count on both chat icons
                                setUnreadCount("contacts.xml", totalUnread);
                                //setUnreadCount("groupchat.xml", totalUnread);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Failed to refresh unread count for contacts.");
                    } finally {
                        try {
                            Thread.sleep(refreshRate);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            });

    private void setUnreadCount(String ref, int totalUnread) {
        NavButtonModel mdl = NavButtonManager.getInstance()
                .getModelByReference(ref);
        if (mdl != null) {
            MapView mapView = MapView.getMapView();
            if (mapView != null) {
                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        mdl.setBadgeCount(totalUnread);
                        NavButtonManager.getInstance().notifyModelChanged(mdl);
                    }
                });
            }
        }
    }
}
