
package com.atakmap.android.contact;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.contact.ContactListAdapter.ViewMode;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A group containing multiple contacts
 * Custom group classes should extend off this
 */
public class GroupContact extends Contact implements Search {
    //TODO: use set instead of arraylist to make sure there are no duplicate uids
    protected final Map<String, Contact> _contacts = new HashMap<>();
    protected boolean _userCreated = false;
    protected boolean _unmodifiable = false;
    protected boolean _hideIfEmpty = true;
    protected boolean _hideLockedGroups = false;
    protected int _totalUnread = 0, _filteredUnread = 0;
    protected String _iconUri;

    public static final String TAG = "GroupContact";

    private final ConcurrentLinkedQueue<OnGroupContactChangedListener> groupContactChangedListeners = new ConcurrentLinkedQueue<>();

    public interface OnGroupContactChangedListener {
        void onGroupContactChanged();
    }

    /**
     * A group that has a multiple endpoints.
     * 
     * @param uid UID of group
     * @param callsign Display name of group
     * @param contacts List of contacts under this group
     * @param userCreated True if the group was user-created
     */

    public GroupContact(String uid, String callsign, List<Contact> contacts,
            boolean userCreated) {
        super(callsign, uid);
        this.filter = new HierarchyListFilter(new SortAlphabet());
        _userCreated = userCreated;
        _iconUri = "android.resource://"
                + MapView.getMapView().getContext().getPackageName()
                + "/" + R.drawable.group_icon;
        addContacts(contacts);
        // do not synchronize during construction of the GroupContact
        // see CID 19284 Thread deadlock
        //setContacts(contacts);
    }

    public GroupContact(String uid, String callsign, boolean userCreated) {
        this(uid, callsign, new ArrayList<Contact>(), userCreated);
    }

    public GroupContact(String uid, String callsign) {
        this(uid, callsign, false);
    }

    /**
     * Get all contacts (no filtering) within this group
     * @param recursive True to include descendant contacts
     *                  False to only include direct children
     * @return List of all contacts
     */
    public List<Contact> getAllContacts(boolean recursive) {
        List<Contact> contacts;
        synchronized (_contacts) {
            contacts = new ArrayList<>(_contacts.values());
        }
        if (!recursive)
            return contacts;
        Map<String, Contact> ret = new HashMap<>();
        for (Contact c : contacts) {
            ret.put(c.getUID(), c);
            if (c instanceof GroupContact) {
                for (Contact child : ((GroupContact) c)
                        .getAllContacts(true))
                    ret.put(child.getUID(), child);
            }
        }
        return new ArrayList<>(ret.values());
    }

    public List<String> getAllContactUIDs(boolean recursive) {
        return Contacts.toUIDs(getAllContacts(recursive));
    }

    /**
     * Determine if this group contact has a contact or sub-contact.
     * @param c the contact to search for
     * @param recursive whether to traverse down sub group contacts
     * @return boolean true if it contains the contact
     */
    public boolean hasContact(Contact c, boolean recursive) {
        if (c == null)
            return false;
        synchronized (_contacts) {
            if (_contacts.containsKey(c.getUID()))
                return true;
            if (recursive) {
                for (Contact child : _contacts.values()) {
                    if (child instanceof GroupContact
                            && ((GroupContact) child).hasContact(c, true))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Get all individual contacts within this group (no filtering)
     * @return List of user contacts
     */
    public List<IndividualContact> getUsers(boolean recursive) {
        Map<String, IndividualContact> ret = new HashMap<>();
        for (Contact c : getAllContacts(recursive)) {
            if (c instanceof IndividualContact)
                ret.put(c.getUID(), (IndividualContact) c);
        }
        return new ArrayList<>(ret.values());
    }

    public List<IndividualContact> getUsers() {
        return getUsers(false);
    }

    public List<String> getUserUIDs(boolean recursive) {
        return Contacts.toUIDs(new ArrayList<Contact>(getUsers(recursive)));
    }

    public List<String> getUserUIDs() {
        return getUserUIDs(false);
    }

    public List<String> getContactUids() {
        return getFilteredUIDs(false, false);
    }

    public List<String> getOnlineContactUids() {
        return getFilteredUIDs(true, false);
    }

    /**
     * Set the master (pre-filtered) contact list
     * @param contacts List of contacts
     */
    public void setContacts(List<Contact> contacts) {
        synchronized (_contacts) {
            _contacts.clear();
            addContacts(contacts);
        }
        fireOnGroupContactChanged();
    }

    public void setContactUIDs(List<String> uids) {
        setContacts(Contacts.fromUIDs(uids));
    }

    /**
     * Check if this group was created by the user
     * @return True if created locally, false if created automatically
     */
    public boolean isUserCreated() {
        return _userCreated;
    }

    /**
     * Set the modifiable state (locked/unlocked) of this group
     * Locked means the user cannot chat within this group
     * @param state True to lock, false to unlock
     */
    public void setUnmodifiable(boolean state) {
        _unmodifiable = state;
    }

    public void lockGroup(boolean lock) {
        setUnmodifiable(lock);
    }

    /**
     * Recursively set this group and all children groups to unmodifiable
     * @param lock True to lock, false to unlock
     */
    public void lockGroups(boolean lock) {
        for (Contact c : getAllContacts(true)) {
            if (c instanceof GroupContact)
                ((GroupContact) c).lockGroup(lock);
        }
        lockGroup(lock);
    }

    /**
     * Update the group locks on every sub-group
     */
    public void updateLocks() {
        if (getUID().equals(Contacts.USER_GROUPS))
            return;

        String selfUID = MapView.getDeviceUid();
        if (isUserCreated()) {
            lockGroups(false);
            return;
        }
        boolean selfWithin = false;
        List<Contact> children = getAllContacts(true);
        for (Contact c : children) {
            if (GroupContact.isGroup(c)) {
                GroupContact subGroup = (GroupContact) c;
                List<Contact> all = subGroup.getAllContacts(true);
                all.addAll(subGroup.getParentContacts(false));
                subGroup.lockGroup(!subGroup.isUserCreated());
                for (Contact c2 : all) {
                    if (c2.getUID().equals(selfUID)) {
                        subGroup.lockGroup(false);
                        break;
                    }
                }
            }
            if (c != null && c.getUID().equals(selfUID))
                selfWithin = true;
        }
        lockGroup(!selfWithin);
    }

    /**
     * Return the modifiable state of this group
     * @return True if the group is locked, false if unlocked
     */
    public boolean getUnmodifiable() {
        return _unmodifiable;
    }

    /**
     * Add contact to master (pre-filtered) list
     * @param c The contact to add
     */
    private void addContactNoSync(Contact c) {
        if (c != null && !c.getUID().isEmpty()
                && !c.getUID().equals(getUID())) {
            if (!getIgnoreStack())
                c.setParentUID(getUID());
            _contacts.put(c.getUID(), c);
        }
    }

    public void addContact(Contact c) {
        synchronized (_contacts) {
            addContactNoSync(c);
        }
        fireOnGroupContactChanged();
    }

    /**
     * Add a list of contacts to the master (pre-filtered) list
     * @param contacts List of contacts
     */
    public void addContacts(List<Contact> contacts) {
        synchronized (_contacts) {
            for (Contact c : contacts)
                addContactNoSync(c);
        }
        fireOnGroupContactChanged();
    }

    /**
     * Remove contact from master list
     * @param c Contact to remove
     */
    private void removeContactNoSync(Contact c) {
        if (c != null) {
            c.setParentUID(null);
            _contacts.remove(c.getUID());
        }
    }

    public void removeContact(Contact c) {
        synchronized (_contacts) {
            removeContactNoSync(c);
        }
        fireOnGroupContactChanged();
    }

    /**
     * Remove list of contacts from the master list
     * @param contacts List of contacts
     */
    public void removeContacts(List<Contact> contacts) {
        synchronized (_contacts) {
            for (Contact c : contacts)
                removeContactNoSync(c);
        }
        fireOnGroupContactChanged();
    }

    /**
     * Remove all individuals from this group and its sub-groups
     */
    public void removeAllUsers() {
        for (Contact c : getAllContacts(false)) {
            if (c instanceof IndividualContact)
                removeContact(c);
            else if (c instanceof GroupContact)
                ((GroupContact) c).removeAllUsers();
        }
    }

    @Override
    public View getExtraView() {
        if (listener instanceof ContactListAdapter
                && ((ContactListAdapter) listener)
                        .getBaseViewMode() == ViewMode.SEND_LIST) {
            // Don't display unread button in the "send item" list
            return null;
        }
        int subUnread = getUnreadCount(true) - super.getUnreadCount();
        if (subUnread > 0) {
            LayoutInflater inflater = LayoutInflater.from(
                    MapView.getMapView().getContext());
            View view = inflater.inflate(R.layout.group_contact_extra_view,
                    null);
            Button unreadBtn = view.findViewById(R.id.unread_btn);
            unreadBtn.setText(String.valueOf(subUnread));
            unreadBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener instanceof ContactListAdapter) {
                        // Find all sub-groups with unread messages
                        List<Contact> filtered = getFiltered(true);
                        List<Contact> unread = new ArrayList<>();
                        for (Contact c : filtered) {
                            if (c instanceof GroupContact
                                    &&
                                    c.getExtras()
                                            .getInt("unreadMessageCount") > 0)
                                unread.add(c);
                        }

                        // Open first sub-group with unread messages
                        ContactListAdapter adapter = (ContactListAdapter) listener;
                        if (!unread.isEmpty())
                            adapter.openGeoChatWindow(unread.get(0));
                    }
                }
            });
            return view;
        }
        return null;
    }

    public void setIconUri(String iconUri) {
        _iconUri = iconUri;
    }

    @Override
    public String getIconUri() {
        return _iconUri;
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    protected void refreshImpl() {
        List<Contact> contacts = getAllContacts(false);

        // Filter
        List<HierarchyListItem> filtered = new ArrayList<>();
        List<Contact> newContacts = new ArrayList<>();
        boolean containsSelf = false;
        String selfUID = MapView.getDeviceUid();
        int totalUnread = 0, filteredUnread = 0;
        for (Contact c : contacts) {
            if (c == null)
                continue;

            // Don't add ourselves to the list, but ack that we're in this group
            if (c.getUID().equals(selfUID)) {
                containsSelf = true;
                continue;
            }

            // In case the instance has changed
            Contact real = Contacts.getInstance().getContactByUuid(c.getUID());
            if (real == null || real == this)
                continue;

            // Refresh child group
            if (real instanceof GroupContact)
                real.syncRefresh(this.listener, this.filter);

            // Update unread count
            // Item must be an exclusive child
            int unreadCount = getUID().equals(real.getParentUID())
                    ? real.getUnreadCount()
                    : 0;
            totalUnread += unreadCount;

            // Used to hide locked groups in the root "Groups" list
            if (_hideLockedGroups && real instanceof GroupContact &&
                    ((GroupContact) real).getUnmodifiable()
                    && unreadCount <= 0)
                continue;

            // Filter
            if (this.filter.accept(c)) {
                filtered.add(real);
                filteredUnread += unreadCount;
            }

            // Add to list of modified contacts
            if (real != c)
                newContacts.add(real);
        }

        // Update from contacts instance (in case info has changed)
        if (!newContacts.isEmpty()) {
            synchronized (_contacts) {
                for (Contact c : newContacts)
                    _contacts.put(c.getUID(), c);
            }
        }

        // Sort
        if (this.filter.sort instanceof ComparatorSort)
            ((ComparatorSort) this.filter.sort).sort(filtered);
        else if (this.filter.sort instanceof SortAlphabet)
            Collections.sort(filtered, COMPARE_ALPHA);

        // Update
        getExtras().putBoolean("containsSelf", containsSelf);
        _filteredUnread = filteredUnread;
        _totalUnread = totalUnread;
        updateChildren(filtered);
    }

    public void setHideIfEmpty(boolean hide) {
        _hideIfEmpty = hide;
    }

    public void setHideLockedGroups(boolean hide) {
        _hideLockedGroups = hide;
    }

    public boolean alwaysShow() {
        // Get base adapter
        ContactListAdapter adapter = this.listener instanceof ContactListAdapter
                ? (ContactListAdapter) this.listener
                : null;

        // Always show user groups in GeoChat mode
        return getUID().equals(
                Contacts.USER_GROUPS)/* && adapter != null
                                     && adapter.getBaseViewMode() == ViewMode.GEO_CHAT*/;
    }

    @Override
    public boolean hideIfEmpty() {
        if (alwaysShow())
            return false;

        // Check active filters
        boolean fov = getFilter(this.filter, FOVFilter.class) != null;
        boolean unread = getFilter(this.filter, UnreadFilter.class) != null;

        // Don't hide groups that contain our UID
        boolean hide = fov || (!_userCreated && _hideIfEmpty
                && super.getUnreadCount() <= 0
                && !getExtras().getBoolean("containsSelf", false));

        // Don't hide subgroups when we're part of the parent group
        if (!fov && hide) {
            List<Contact> contacts = getParentHierarchy();
            for (Contact c : contacts) {
                if (c.getExtras().getBoolean("containsSelf", false)) {
                    hide = false;
                    break;
                }
            }
        }

        // Don't hide groups with unread chats if using unread filter
        if (hide && unread && (super.getUnreadCount() > 0
                || getUnreadCount(true) > 0))
            hide = false;

        return hide;
    }

    /**
     * Count total unread messages within and under this group
     *
     * @param filtered True to only count filtered contacts
     * @return Unread message count
     */
    public int getUnreadCount(boolean filtered) {
        return super.getUnreadCount() + (filtered ? _filteredUnread
                : _totalUnread);
    }

    @Override
    public int getUnreadCount() {
        return getUnreadCount(true);
    }

    /**
     * Calculate the total unread count for all items in this group hierarchy
     * This does not depend on the result stored by refreshImpl
     * @return Total unread count
     */
    public int calculateUnread() {
        int unread = super.getUnreadCount();
        List<Contact> contacts = getAllContacts(false);
        for (Contact c : contacts) {
            if (c != null && getUID().equals(c.getParentUID())
                    && !FilteredContactsManager.getInstance()
                            .isContactFiltered(c)) {
                if (c instanceof GroupContact)
                    unread += ((GroupContact) c).calculateUnread();
                else
                    unread += c.getUnreadCount();
            }
        }
        return unread;
    }

    /**
     * Get list of contacts under this group which contain unread messages
     * @param filtered True to only count filtered contacts
     * @return List of contacts with unread messages
     */
    public List<Contact> getUnreadContacts(boolean filtered) {
        List<Contact> contacts = (filtered ? getFiltered(false, false)
                : getAllContacts(true));
        List<Contact> ret = new ArrayList<>();
        for (Contact c : contacts) {
            if (c.getExtras().getInt("unreadMessageCount") > 0)
                ret.add(c);
        }
        return ret;
    }

    public List<Contact> getUnreadContacts() {
        return getUnreadContacts(true);
    }

    /**
     * Return the root user group that contains this group
     * @return The root user group, or null if not applicable
     */
    public GroupContact getRootUserGroup() {
        String[] path = getHierarchyPath();
        String uid = null;
        for (int i = 1; i < path.length - 2; i += 2) {
            if (path[i].equals(Contacts.USER_GROUPS)) {
                uid = path[i + 2];
                break;
            }
        }
        if (uid == null)
            return null;
        Contact group = Contacts.getInstance().getContactByUuid(uid);
        return !isGroup(group) ? null : (GroupContact) group;
    }

    @Override
    protected boolean validChild(HierarchyListItem item) {
        synchronized (_contacts) {
            return super.validChild(item) && _contacts
                    .containsKey(item.getUID());
        }
    }

    @Override
    public List<String[]> findChildPaths(String uid) {
        // In case filtered is empty, fall back to master list
        List<String[]> paths = new ArrayList<>();
        List<String> path = new ArrayList<>(
                Arrays.asList(getHierarchyPath()));
        for (Contact c : getAllContacts(false)) {
            if (c.getUID().equals(uid)) {
                path.add(c.getName());
                path.add(c.getUID());
                paths.add(path.toArray(new String[0]));
            } else {
                List<String[]> reFind = c.findChildPaths(uid);
                if (reFind != null)
                    paths.addAll(reFind);
            }
        }
        return paths;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> ret = new HashSet<>();
        if (terms.isEmpty())
            return ret;
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        List<HierarchyListItem> children = getChildren();
        if (getUID().equals("RootChatGroup"))
            Log.d(TAG, "root refresImpl has " + children.size()
                    + " children");
        for (HierarchyListItem item : children) {
            if (item.getTitle().toLowerCase(LocaleUtil.getCurrent())
                    .contains(terms))
                ret.add(item);
            if (item instanceof Search)
                ret.addAll(((Search) item).find(terms));
        }
        return ret;
    }

    /**
     * Clear this groups hierarchy without invalidating any contacts
     * This is mainly used when building the group hierarchy
     */
    public void clearHierarchy() {
        synchronized (_contacts) {
            for (Contact c : _contacts.values()) {
                if (isGroup(c))
                    ((GroupContact) c).clearHierarchy();
            }
            _contacts.clear();
        }
    }

    public boolean isEmpty() {
        synchronized (_contacts) {
            return _contacts.isEmpty();
        }
    }

    public static boolean isGroup(Contact c) {
        return c instanceof GroupContact;
    }

    @Override
    public String toString() {
        return getName() + "[" + getUID() + "]";
    }

    /**
     * Adds a listener that is notified when there is a change to the group contact
     * @param listener the listener to be added.
     */
    public void addOnGroupContactChangedListener(
            OnGroupContactChangedListener listener) {
        groupContactChangedListeners.add(listener);
    }

    /**
     * Removes a listener that is notified when there is a change to the group contact
     * @param listener the listener to be remove.
     */
    public void removeOnGroupContactChangedListener(
            OnGroupContactChangedListener listener) {
        groupContactChangedListeners.remove(listener);
    }

    protected void fireOnGroupContactChanged() {
        for (OnGroupContactChangedListener listener : groupContactChangedListeners) {
            listener.onGroupContactChanged();
        }
    }
}
