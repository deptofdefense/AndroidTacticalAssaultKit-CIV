
package com.atakmap.android.contact;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class Contact extends AbstractHierarchyListItem2 {

    public enum UpdateStatus {
        CURRENT,
        STALE,
        DEAD,
        NA
    }

    private static final String TAG = "Contact";

    public static final int BACKGROUND_COLOR_ALIVE = 0x4F000000;
    public static final int BACKGROUND_COLOR_STALE = 0x4F999966;
    public static final int BACKGROUND_COLOR_DEAD = 0x4F993333;
    public static final int BACKGROUND_COLOR_NA = 0x80666666;

    public static final int FOREGROUND_COLOR_ALIVE = 0xFF00FF00;
    public static final int FOREGROUND_COLOR_STALE = 0xFF999966;
    public static final int FOREGROUND_COLOR_DEAD = 0xFF993333;

    private String name;
    protected String contactUUID;
    private Bundle extras;
    private boolean bDispatch;

    Contact(String name) {
        this(name, UUID.randomUUID().toString());
    }

    Contact(String name, String uuid) {
        this(name, uuid, new Bundle());
    }

    Contact(String name, String uuid, Bundle bundle) {
        this.name = name;
        this.contactUUID = uuid;
        this.extras = bundle;
        this.bDispatch = true;
        this.asyncRefresh = true;

        setUpdateStatus(UpdateStatus.DEAD);
        this.extras.putInt("unreadMessageCount", 0);
        this.extras.putBoolean("fakeGroup", false);

        //Log.d(TAG, "wrap new contact: " + name + " uuid: " + uuid);
    }

    public void setDispatch(boolean bDispatch) {
        this.bDispatch = bDispatch;
    }

    /**
     * Return the display name of the contact
     * @return Display name
     */
    public String getName() {
        if (name == null)
            return "";
        return name;
    }

    /**
     * Set the display name of the contact
     * @param name Display name
     */
    public void setName(final String name) {
        this.name = name;
        dispatchChangeEvent();
    }

    /**
     * Set the uid of the contact
     * This should only be used for special cases (see RoleGroup)
     * @param uid New UID
     */
    protected void setUid(final String uid) {
        contactUUID = uid;
        dispatchChangeEvent();
    }

    public Bundle getExtras() {
        return extras;
    }

    public void setExtras(Bundle extras) {
        this.extras = extras;
        dispatchChangeEvent();
    }

    public void dispatchChangeEvent() {
        if (bDispatch)
            Contacts.getInstance().dispatchContactChangedEvent(getUID());
    }

    public void setUpdateStatus(UpdateStatus status) {
        //Log.d(TAG, "setUpdateStatus: " + status);
        this.extras.putSerializable("updateStatus", status);
    }

    public UpdateStatus getUpdateStatus() {
        return (UpdateStatus) this.extras.get("updateStatus");
    }

    /**
     * Get the text color of the contact name text view
     * @return Color as integer (black by default)
     */
    public int getTextColor() {
        return Color.WHITE;
    }

    /**
     * Get the color of the contact view background
     * @return Color as integer (gray by default)
     */
    public int getBackgroundColor() {
        return BACKGROUND_COLOR_ALIVE;
    }

    /**
     * Get color of presence indicator, or null if not applicable
     *
     * @return the integer representing the color of the presence indicator
     */
    public Integer getPresenceColor() {
        return null;
    }

    /** Hierarchy list item methods **/

    @Override
    public String getUID() {
        return contactUUID;
    }

    @Override
    public String getTitle() {
        return getName();
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public Object getUserObject() {
        return contactUUID;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public HierarchyListFilter refresh(HierarchyListFilter filter) {
        if (filter != null) {
            this.filter = filter;
            refreshImpl();
        }
        return this.filter;
    }

    public void refresh(String reason) {
        if (this.listener instanceof ContactListAdapter)
            ((ContactListAdapter) this.listener).refreshList(reason);
    }

    public List<Contact> getChildContacts() {
        List<Contact> ret = new ArrayList<>();
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (validChild(item))
                ret.add((Contact) item);
        }
        return ret;
    }

    /**
     * Return a complete list of filtered contacts
     * This includes descendant contacts
     * @param onlineOnly True to only include valid (online) contacts
     * @param incParents True to include contacts that are members
     *                       of any parent groups
     * @return List of filtered contacts
     */
    public List<Contact> getFiltered(boolean onlineOnly, boolean incParents) {
        Map<String, Contact> ret = new HashMap<>();
        for (Contact item : getChildContacts()) {
            ret.put(item.getUID(), item);
            for (Contact c : item.getFiltered(onlineOnly, false))
                ret.put(c.getUID(), c);
        }
        if (incParents) {
            for (Contact c : getParentContacts(true))
                ret.put(c.getUID(), c);
        }
        if (onlineOnly) {
            Set<String> keys = new HashSet<>(ret.keySet());
            for (String uid : keys) {
                if (!Contacts.getInstance().validContact(ret.get(uid)))
                    ret.remove(uid);
            }
        }
        return new ArrayList<>(ret.values());
    }

    List<Contact> getFiltered(boolean onlineOnly) {
        return getFiltered(onlineOnly, false);
    }

    List<String> getFilteredUIDs(boolean onlineOnly, boolean incParents) {
        List<Contact> contacts = getFiltered(onlineOnly, incParents);
        List<String> ret = new ArrayList<>(contacts.size());
        for (Contact c : contacts)
            ret.add(c.getUID());
        return ret;
    }

    public List<String> getFilteredUIDs(boolean onlineOnly) {
        return getFilteredUIDs(onlineOnly, false);
    }

    public List<Contact> getParentContacts(boolean filtered) {
        List<Contact> ret = new ArrayList<>();
        Contact parent = getParent();
        if (GroupContact.isGroup(parent) && parent != Contacts.getInstance()
                .getRootGroup()) {
            ret.addAll(parent.getParentContacts(filtered));
            ret.addAll(filtered ? parent.getChildContacts()
                    : ((GroupContact) parent).getAllContacts(false));
        }
        return ret;
    }

    public List<Contact> getParentHierarchy() {
        List<Contact> ret = new ArrayList<>();
        Contact parent = getParent();
        if (GroupContact.isGroup(parent) && parent != Contacts.getInstance()
                .getRootGroup()) {
            ret.add(parent);
            ret.addAll(parent.getParentHierarchy());
        }
        return ret;
    }

    @Override
    protected void updateChildren(final List<HierarchyListItem> items) {
        synchronized (this.children) {
            // Don't bother updating nothing
            if (this.children.isEmpty() && items.isEmpty())
                return;
        }
        this.uiHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (children) {
                    children.clear();
                    children.addAll(items);
                }
                notifyListener();
            }
        });
    }

    @Override
    protected void notifyListener() {
        super.notifyListener();
        if (this.listener instanceof ContactListAdapter)
            ((ContactListAdapter) this.listener)
                    .notifyDataSetChanged(this);
    }

    /**
     * Return the UID of this contact's parent
     * For individual contacts this is "Root"
     * @return Parent UID
     */
    public String getParentUID() {
        String ret = this.extras.getString("parent", null);
        if (ret != null && ret.isEmpty())
            ret = null;
        return ret;
    }

    /**
     * Set the parent UID of this contact
     * For groups, should typically let addContact handle this
     * @param uid Parent UID
     */
    public void setParentUID(String uid) {
        this.extras.putString("parent", uid);
    }

    public Contact getParent() {
        return Contacts.getInstance().getContactByUuid(getParentUID());
    }

    /**
     * Get the path to this contact as a string array
     * Form is [name1, uid1, name2, uid2, ..., nameN, uidN]
     * This is used specifically for CoT events
     * @return String array of names and UIDs
     */
    public String[] getHierarchyPath() {
        String[] path = new String[] {
                getName(), getUID()
        };
        Contact parent = getParent();
        if (parent != null) {
            String[] parentPath = parent.getHierarchyPath();
            String[] ret = new String[parentPath.length + path.length];
            System.arraycopy(parentPath, 0, ret, 0, parentPath.length);
            System.arraycopy(path, 0, ret, parentPath.length, path.length);
            return ret;
        }
        return path;
    }

    /**
     * Determine if this contact is within the hierarchy of UID
     * @param uid UID of the parent contact to check
     * @return True if this contact is descended from UID
     */
    public boolean descendedFrom(String uid) {
        if (getUID().equals(uid))
            return true;
        Contact parent = Contacts.getInstance()
                .getContactByUuid(getParentUID());
        return parent != null && parent.descendedFrom(uid);
    }

    /**
     * Find the path to the child contact UID to this contact
     * @param uid Child contact UID
     * @return The full path to the child contact, or null if not found
     */
    public List<String[]> findChildPaths(String uid) {
        List<HierarchyListItem> children = getChildren();
        List<String[]> paths = new ArrayList<>();
        List<String> path = new ArrayList<>(
                Arrays.asList(getHierarchyPath()));
        for (HierarchyListItem item : children) {
            if (!validChild(item))
                continue;
            Contact c = (Contact) item;
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

    /**
     * Set the listener adapter that is notified when refresh is finished
     * @param listener Listener adapter
     */
    public void setListener(BaseAdapter listener) {
        this.listener = listener;
    }

    /**
     * Check if a child item is valid
     * Assumes item is already part of this.children
     * @param item Item to test
     * @return True if item is a valid contact
     */
    protected boolean validChild(HierarchyListItem item) {
        return item != this && item instanceof Contact;
    }

    @Override
    public void dispose() {
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        return null;
    }

    public int getUnreadCount() {
        return extras.getInt("unreadMessageCount");
    }

    public void setUnreadCount(int unreadCount) {
        extras.putInt("unreadMessageCount", unreadCount);

        // In case unread filter is active
        if (this.listener instanceof ContactListAdapter)
            ((ContactListAdapter) this.listener).refreshList(
                    "Unread count for " + getName() + " changed");
    }

    /**
     * Set whether this list isn't part of the group hierarchy
     * @param ignore True to ignore group hierarchy
     */
    void setIgnoreStack(boolean ignore) {
        getExtras().putBoolean("ignoreStack", ignore);
    }

    /**
     * Return if this list isn't part of the group hierarchy
     * @return True if not part of the hierarchy
     */
    boolean getIgnoreStack() {
        return getExtras().getBoolean("ignoreStack", false);
    }

    /**
     * Sort by contact status
     */
    static final Comparator<HierarchyListItem> COMPARE_STATUS = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(final HierarchyListItem lhs,
                final HierarchyListItem rhs) {
            Contact lContact = lhs instanceof Contact ? (Contact) lhs : null;
            Contact rContact = rhs instanceof Contact ? (Contact) rhs : null;
            if (lContact == null)
                return -1;
            else if (rContact == null)
                return 1;
            int comp = lContact
                    .getName()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .compareTo(
                            rContact.getName().toLowerCase(
                                    LocaleUtil.getCurrent()));
            if (lContact instanceof GroupContact
                    || lContact.getExtras().getBoolean("fakeGroup", false)) {
                if (rContact instanceof GroupContact
                        || rContact.getExtras().getBoolean("fakeGroup", false))
                    return comp;
                return -1;
            } else if (rContact instanceof GroupContact
                    || rContact.getExtras().getBoolean("fakeGroup", false)) {
                return 1;
            }

            UpdateStatus lStatus = lContact.getUpdateStatus();
            UpdateStatus rStatus = rContact.getUpdateStatus();

            // What to do with group or broadcast contacts - do they still exist?

            int lValue = 0;
            int rValue = 0;
            if (lStatus != null && rStatus != null) {
                switch (lStatus) {
                    case CURRENT:
                        lValue = 4;
                        break;
                    case STALE:
                        lValue = 3;
                        break;
                    case DEAD:
                        lValue = 2;
                        break;
                    default:
                        lValue = 1;

                }
                switch (rStatus) {
                    case CURRENT:
                        rValue = 4;
                        break;
                    case STALE:
                        rValue = 3;
                        break;
                    case DEAD:
                        rValue = 2;
                        break;
                    default:
                        rValue = 1;
                }
            }

            if (lValue > rValue)
                return -1;
            else if (lValue < rValue)
                return 1;
            else
                return comp;
        }
    };

    /**
     * Sort alphabetically by contact name/UID
     */
    static final Comparator<HierarchyListItem> COMPARE_ALPHA = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(HierarchyListItem lhs, HierarchyListItem rhs) {
            Contact lContact = lhs instanceof Contact ? (Contact) lhs : null;
            Contact rContact = rhs instanceof Contact ? (Contact) rhs : null;
            if (lContact == null)
                return -1;
            else if (rContact == null)
                return 1;
            int comp = lContact
                    .getName()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .compareTo(
                            rContact.getName().toLowerCase(
                                    LocaleUtil.getCurrent()));
            if (lContact instanceof GroupContact
                    || lContact.getExtras().getBoolean("fakeGroup", false)) {
                if (rContact instanceof GroupContact
                        || rContact.getExtras().getBoolean("fakeGroup", false))
                    return comp;
                return -1;
            } else if (rContact instanceof GroupContact
                    || rContact.getExtras().getBoolean("fakeGroup", false)) {
                return 1;
            }
            if (comp == 0)
                comp = lContact
                        .getUID()
                        .toLowerCase(LocaleUtil.getCurrent())
                        .compareTo(
                                rContact.getUID().toLowerCase(
                                        LocaleUtil.getCurrent()));
            return comp;
        }
    };

    /**
     * Sort by number of unread messages within each contact
     */
    static final Comparator<HierarchyListItem> COMPARE_UNREAD = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(HierarchyListItem lhs, HierarchyListItem rhs) {
            Contact lContact = lhs instanceof Contact ? (Contact) lhs : null;
            Contact rContact = rhs instanceof Contact ? (Contact) rhs : null;
            if (lContact == null)
                return -1;
            else if (rContact == null)
                return 1;
            int comp = lContact
                    .getName()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .compareTo(
                            rContact.getName().toLowerCase(
                                    LocaleUtil.getCurrent()));
            if (lContact instanceof GroupContact
                    || lContact.getExtras().getBoolean("fakeGroup", false)) {
                if (rContact instanceof GroupContact
                        || rContact.getExtras().getBoolean("fakeGroup",
                                false)) {
                    int result = rContact.getUnreadCount() -
                            lContact.getUnreadCount();
                    if (result == 0)
                        return comp;
                    return result;
                }
                return -1;
            } else if (rContact instanceof GroupContact
                    || rContact.getExtras().getBoolean("fakeGroup", false)) {
                return 1;
            }
            int result = rContact.getUnreadCount() -
                    lContact.getUnreadCount();
            if (result == 0)
                return comp;
            return result;
        }
    };

    /**
     * Sort contacts by location
     */
    static final Comparator<HierarchyListItem> COMPARE_LOCATION = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(HierarchyListItem lhs, HierarchyListItem rhs) {
            Contact lContact = lhs instanceof Contact ? (Contact) lhs : null;
            Contact rContact = rhs instanceof Contact ? (Contact) rhs : null;
            if (lContact == null)
                return -1;
            else if (rContact == null)
                return 1;
            int alphaComp = lContact.getName()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .compareTo(rContact.getName().toLowerCase(
                            LocaleUtil.getCurrent()));

            // Check if either contact is a group
            if (GroupContact.isGroup(lContact)
                    || lContact.getExtras().getBoolean("fakeGroup", false)) {
                if (GroupContact.isGroup(rContact)
                        || rContact.getExtras().getBoolean("fakeGroup", false))
                    // Groups do not have locations
                    return alphaComp;
                return -1;
            } else if (GroupContact.isGroup(rContact)
                    || rContact.getExtras().getBoolean("fakeGroup", false)) {
                return 1;
            }

            // Check if either is an individual with location
            if (lhs instanceof IndividualContact
                    && ((IndividualContact) lhs).hasLocation()) {
                if (rhs instanceof IndividualContact
                        && ((IndividualContact) rhs).hasLocation()) {
                    MapView mv = MapView.getMapView();
                    Marker self = ATAKUtilities.findSelf(mv);
                    GeoPoint poi = self != null ? self.getPoint()
                            : mv
                                    .getPoint().get();
                    GeoPoint p1 = ((PointMapItem) ((IndividualContact) lhs)
                            .getMapItem()).getPoint();
                    GeoPoint p2 = ((PointMapItem) ((IndividualContact) rhs)
                            .getMapItem()).getPoint();
                    return Double.compare(p1.distanceTo(poi),
                            p2.distanceTo(poi));
                }
                return -1;
            } else if (rhs instanceof IndividualContact
                    && ((IndividualContact) rhs).hasLocation())
                return 1;
            return alphaComp;
        }
    };
}
