
package com.atakmap.android.contact;

import android.content.Intent;
import android.content.SharedPreferences;

import com.atakmap.android.chat.GeoChatConnector;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.export.MissionPackageConnector;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class IndividualContact extends Contact
        implements MapItemUser {

    private static final String TAG = "IndividualContact";

    public static final String DEFAULT_CONNECTOR = "connector.default";

    protected final HashMap<String, Connector> connectors = new HashMap<>();

    private MapItem mapItem = null;

    public IndividualContact(String name) {
        this(name, UUID.randomUUID().toString(), null, null);
    }

    public IndividualContact(String name, String uuid) {
        this(name, uuid, null, null);
    }

    public IndividualContact(String name, String uuid, MapItem assocItem) {
        this(name, uuid, assocItem, null);
    }

    public IndividualContact(String name, String uuid, NetConnectString ncs) {
        this(name, uuid, null, ncs);
    }

    /**
     * ctor
     *
     * @param name the name of the contact
     * @param uuid the unique identifier for the contact
     * @param assocItem if there is an associated map item
     * @param ncs   if provided, connectors are added: IpConnector, GeoChatConnector, MissionPackageConnector
     */
    public IndividualContact(String name, String uuid, MapItem assocItem,
            NetConnectString ncs) {
        super(name, uuid);
        mapItem = assocItem;

        setUpdateStatus(Contact.UpdateStatus.CURRENT);

        if (ncs != null) {
            addConnector(new IpConnector(ncs));
            addConnector(new GeoChatConnector(ncs));
            addConnector(new MissionPackageConnector(ncs));
        }
    }

    @Override
    public void setName(String name) {
        IpConnector connector = (IpConnector) getConnector(
                IpConnector.CONNECTOR_TYPE);
        if (connector != null) {
            connector.setCallsign(name);
        }
        //put super last because we want to update the IpConnector before dispatching the event
        super.setName(name);
    }

    @Override
    public String getParentUID() {
        // Individual contact parent is always root
        return Contacts.getInstance().getRootGroup().getUID();
    }

    @Override
    public void setParentUID(String uid) {
        // Do nothing - parent UID is always root group
    }

    @Override
    public String[] getHierarchyPath() {
        return new String[] {
                getName(), getUID()
        };
    }

    @Override
    public List<String[]> findChildPaths(String uid) {
        return new ArrayList<>();
    }

    @Override
    public List<Contact> getFiltered(boolean online, boolean incParents) {
        List<Contact> ret = new ArrayList<>();
        ret.add(this);
        return ret;
    }

    public String toString() {
        return getName() + "[" + (mapItem == null ? "" : mapItem.getUID())
                + "]";
    }

    /**
     * Add or update connector of the specified type
     *
     * @param connector
     * @return  true if added or updated
     */
    public synchronized boolean addConnector(final Connector connector) {
        Connector existing = connectors.get(connector.getConnectionType());
        if (existing == null) {
            //Log.d(TAG, "adding connector: " + connector.toString() + ", for: "
            //        + this.toString());
            connectors.put(connector.getConnectionType(), connector);
            dispatchChangeEvent();
            return true;
        }

        if (!existing.equals(connector)) {
            //Log.d(TAG, "updating connector: " + connector.toString()
            //        + ", for: " + this.toString());
            connectors.put(connector.getConnectionType(), connector);
            dispatchChangeEvent();
            return true;
        } else {
            //Log.v(TAG, "Connector unchanged: " + existing.toString());
            return false;
        }
    }

    /**
     * Get the default connector for this contact, in this order:
     *  1) Check if only a single connector available, if so use it
     *  2) Last user selected connector
     *  3) Use highest priority connector available
     *
     * @return
     */
    public Connector getDefaultConnector(SharedPreferences prefs) {
        return getConnector(DEFAULT_CONNECTOR, prefs);
    }

    /**
     * Get connector of the specified type
     *
     * @param type
     * @return connector, or null if none found for the specified type
     */
    public Connector getConnector(String type) {
        return getConnector(type, null);
    }

    private synchronized Connector getConnector(String type,
            SharedPreferences prefs) {
        if (connectors.size() < 1)
            return null;

        //see if we're looking for a good default
        if (!FileSystemUtils.isEquals(DEFAULT_CONNECTOR, type)) {
            //not default, get specific connector
            return connectors.get(type);
        }

        //looking for default, first see if we have just one
        if (connectors.size() == 1)
            return connectors.values().iterator().next();

        //see if we have a default for this contact
        if (prefs != null) {
            String defaultConnector = ContactConnectorManager
                    .getDefaultConnectorType(prefs, contactUUID);
            if (!FileSystemUtils.isEmpty(defaultConnector)
                    && connectors.containsKey(defaultConnector)) {
                return connectors.get(defaultConnector);
            }
        }

        //take highest priority connector
        Connector priority = null;
        for (Connector c : connectors.values()) {
            if (priority == null) {
                priority = c;
            } else if (c.getPriority() > priority.getPriority()) {
                priority = c;
            }
        }

        return priority;
    }

    /**
     * Get unmodifiable collection of connectors
     *
     * @param bUserOnly true to get only user selectable connectors
     * @return
     */
    public synchronized Collection<Connector> getConnectors(boolean bUserOnly) {
        if (!bUserOnly) {
            return Collections.unmodifiableCollection(new HashSet<>(connectors.values()));
        } else {
            List<Connector> ret = new ArrayList<>();
            for (Connector c : connectors.values()) {
                if (c != null && c.isUserConnector()) {
                    ret.add(c);
                }
            }

            return Collections.unmodifiableCollection(ret);
        }
    }

    /**
     * Check if contact has the specified connector type
     *
     * @param type
     * @return
     */
    public boolean hasConnector(String type) {
        return getConnector(type) != null;
    }

    /**
     * Remove the specified connector type
     *
     * @param type
     * @return
     */
    public synchronized Connector removeConnector(String type) {
        if (!hasConnector(type))
            return null;

        Connector ret = connectors.remove(type);
        if (ret != null) {
            dispatchChangeEvent();
        }
        return ret;
    }

    /**
     * Get the preferred connection string for this contact
     * i.e. if we're connected over WiFi, use null
     *      if we're ONLY connected over TAK server, use the TAK server
     * @return Preferred connection string
     */
    public String getServerFrom() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        MapItem mi = mv.getRootGroup().deepFindUID(getUID());
        String serverFrom = mi != null ? mi.getMetaString(
                "serverFrom", null) : null;
        String ncs = null;
        Connector con = getConnector(IpConnector.CONNECTOR_TYPE);
        if (con instanceof IpConnector)
            ncs = con.getConnectionString();
        if (!FileSystemUtils.isEmpty(ncs) && !ncs.startsWith(
                ContactUtil.TAK_SERVER_CONNECTION_STRING))
            // Connected over local network (null)
            return null;
        return serverFrom;
    }

    /**
     * Set status to CURRENT
     */
    public void current() {
        if (getUpdateStatus() != UpdateStatus.CURRENT) {
            setUpdateStatus(UpdateStatus.CURRENT);
            dispatchChangeEvent();
        } else {
            //TODO currently we track presence (icon color) on marker, and on this contact (UpdateStatus)
            //so in some cases need to update ImageView for marker icon even if UpdateStatus has not changed
            dispatchChangeEvent();
        }
    }

    /**
     * Set status to STALE
     */
    public void stale() {
        if (getUpdateStatus() != UpdateStatus.STALE) {
            setUpdateStatus(UpdateStatus.STALE);
            dispatchChangeEvent();
        } else {
            //TODO currently we track presence (icon color) on marker, and on this contact (UpdateStatus)
            //so in some cases need to update ImageView for marker icon even if UpdateStatus has not changed
            dispatchChangeEvent();
        }
    }

    /**
     * Set status to DEAD
     */
    public void die() {
        if (getUpdateStatus() != UpdateStatus.DEAD) {
            setUpdateStatus(UpdateStatus.DEAD);
            dispatchChangeEvent();
        } else {
            //TODO currently we track presence (icon color) on marker, and on this contact (UpdateStatus)
            //so in some cases need to update ImageView for marker icon even if UpdateStatus has not changed
            dispatchChangeEvent();
        }
    }

    @Override
    public int getBackgroundColor() {
        UpdateStatus status = getUpdateStatus();
        if (status != null) {
            switch (status) {
                case DEAD:
                    return BACKGROUND_COLOR_DEAD;
                case STALE:
                    return BACKGROUND_COLOR_STALE;
                case NA:
                    return BACKGROUND_COLOR_NA;
                default:
                    return BACKGROUND_COLOR_ALIVE;
            }
        }
        return super.getBackgroundColor();
    }

    @Override
    public Integer getPresenceColor() {
        // All streaming, all chat rooms
        if (getExtras().getBoolean("fakeGroup", false))
            return null;

        return getPresenceColor(getUpdateStatus());
    }

    public static Integer getPresenceColor(UpdateStatus status) {
        if (status == null)
            return null;

        switch (status) {
            case DEAD:
                return FOREGROUND_COLOR_DEAD;
            case STALE:
                return FOREGROUND_COLOR_STALE;
            case CURRENT:
                return FOREGROUND_COLOR_ALIVE;
            default:
            case NA:
                return null;
        }
    }

    @Override
    public String getIconUri() {
        if (mapItem != null) {
            return ATAKUtilities.getIconUri(getMapItem());
        }

        int resId = R.drawable.na_presence;

        // All streaming, all chat rooms
        if (getExtras().getBoolean("fakeGroup", false))
            resId = R.drawable.group_icon;
        return "android.resource://"
                + MapView.getMapView().getContext().getPackageName()
                + "/" + resId;
    }

    @Override
    public int getIconColor() {
        return ATAKUtilities.getIconColor(mapItem);
    }

    @Override
    public MapItem getMapItem() {
        return mapItem;
    }

    public void setMapItem(MapItem mi) {
        mapItem = mi;
    }

    @Override
    public boolean isVisible() {
        return mapItem == null || mapItem.getVisible();
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    protected void refreshImpl() {
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    /**
     * Get default avatar
     * Currently takes first avatar from any connector profile
     * TODO build avatar inherent to TAK?
     *
     * @return
     */
    public AvatarFeature getDefaultAvatar() {

        //TODO stronger typing, mabye using generics?
        List<Object> avatars = CotMapComponent.getInstance()
                .getContactConnectorMgr().getFeatures(this,
                        ContactConnectorManager.ConnectorFeature.Avatar, 1);
        if (FileSystemUtils.isEmpty(avatars))
            return null;

        Object avatar = avatars.get(0);
        if (avatar instanceof AvatarFeature)
            return (AvatarFeature) avatar;

        return null;
    }

    /**
     * Get default profile intent
     * First checks if there is an associated marker (e.g. a TAK user), then takes first profile
     * from any connector
     *
     * @return
     */
    public ActionBroadcastData getDefaultProfile() {
        if (mapItem != null) {
            ArrayList<ActionBroadcastExtraStringData> extras = new ArrayList<>();
            extras.add(new ActionBroadcastExtraStringData("targetUID", mapItem
                    .getUID()));
            return new ActionBroadcastData(
                    ContactDetailDropdown.CONTACT_DETAILS, extras);
        } else {
            List<Object> profiles = CotMapComponent
                    .getInstance()
                    .getContactConnectorMgr()
                    .getFeatures(this,
                            ContactConnectorManager.ConnectorFeature.Profile,
                            1);
            if (FileSystemUtils.isEmpty(profiles)) {
                Log.d(TAG, "No profile found: " + this);
                return null;
            }

            Object obj = profiles.get(0);
            if (!(obj instanceof ActionBroadcastData)) {
                Log.w(TAG, "Invalid profile found: " + this);
                return null;
            }

            return (ActionBroadcastData) obj;
        }

    }

    public boolean hasProfile() {
        return getDefaultProfile() != null;
    }

    @Override
    public int getUnreadCount() {
        List<Object> counts = CotMapComponent
                .getInstance()
                .getContactConnectorMgr()
                .getFeatures(
                        this,
                        ContactConnectorManager.ConnectorFeature.NotificationCount,
                        Integer.MAX_VALUE);
        if (FileSystemUtils.isEmpty(counts))
            return 0;

        int count = 0;
        for (Object cur : counts) {
            if (cur instanceof Integer) {
                int i = (Integer) cur;
                if (i < 0) {
                    Log.w(TAG, "Skipping invalid count: " + i);
                    continue;
                }

                count += i;
            }
        }

        return count;
    }

    public int getUnreadCount(Connector connector) {
        Object count = CotMapComponent.getInstance().getContactConnectorMgr()
                .getFeature(this, connector,
                        ContactConnectorManager.ConnectorFeature.NotificationCount);
        return count instanceof Integer ? (Integer) count : 0;
    }

    public boolean hasLocation() {
        return mapItem instanceof PointMapItem
                && ((PointMapItem) mapItem).getPoint() != null
                && ((PointMapItem) mapItem).getPoint().isValid();
    }

    /**
     * If location is available, zoom to it
     * Otherwise initiate default comms
     *
     * @return
     * @param prefs
     */
    public boolean onSelected(SharedPreferences prefs) {
        if (zoom()) {
            return true;
        } else {
            //initiate default comms...
            return initiateDefaultComms(prefs);
        }
    }

    public boolean zoom() {
        if (!hasLocation())
            return false;

        Log.d(TAG, "zooming for individual:  " + this);

        //zoom map
        final GeoPoint gp = ((PointMapItem) mapItem).getPoint();

        CameraController.Programmatic.panTo(
                MapView.getMapView().getRenderer3(),
                gp, false);

        //display details and break cam lock
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.SHOW_DETAILS");
        intent.putExtra("uid", mapItem.getUID());
        AtakBroadcast.getInstance().sendBroadcast(intent);

        intent = new Intent();
        intent.setAction("com.atakmap.android.maps.SHOW_MENU");
        intent.putExtra("uid", mapItem.getUID());
        AtakBroadcast.getInstance().sendBroadcast(intent);

        return true;
    }

    /**
     *  Initiate default comms following order:
    * 1) If no unread, use "default" connector, initiate comms when tapped
    * 2) If single connector with unread, use that connector, initiate comms when tapped
    * 3) If multiple connectors with unread, use generic icon and display comms tab when clicked
     *
     * Note, this matches behavior in ContactListAdapter.updateDefaultCommsBtn()
     * @param prefs
     *
     * @return true if initiated successfully
     */
    private boolean initiateDefaultComms(SharedPreferences prefs) {

        final Connector defaultConnector = getDefaultConnector(prefs);
        if (defaultConnector != null) {
            int totalUnread = getUnreadCount();
            if (totalUnread == 0) {
                //no unread, display default comms icon w/no unread overlay
                Log.d(TAG, "Default comms for individual:  " + this);
                return CotMapComponent
                        .getInstance()
                        .getContactConnectorMgr()
                        .initiateContact(this,
                                defaultConnector);
            } else {
                //see how many connectors have unread
                int connectorsWithUnread = 0;
                Collection<Connector> connectors = getConnectors(true);
                Connector unreadConnector = null;
                for (Connector cur : connectors) {
                    if (getUnreadCount(cur) > 0) {
                        unreadConnector = cur;
                        connectorsWithUnread++;
                    }

                    //for now we only care if there are multiple connectors with unrad
                    if (connectorsWithUnread > 1) {
                        break;
                    }
                }

                if (connectorsWithUnread > 1) {
                    //multiple connectors with unread
                    Log.d(TAG,
                            "Multiple comms clicked for individual: "
                                    + this);
                    ActionBroadcastData intent = getDefaultProfile();
                    if (intent != null
                            && ContactDetailDropdown.CONTACT_DETAILS
                                    .equals(intent
                                            .getAction())) {
                        //if default TAK profile, then lets jump to comms tab
                        intent.getExtras()
                                .add(new ActionBroadcastExtraStringData(
                                        "tab",
                                        ContactConnectorsView.TAG));
                    }
                    ActionBroadcastData.broadcast(intent);
                    return true;
                } else {
                    //display that connector with unread count overlay
                    Log.d(TAG,
                            "Unread comms clicked for individual: "
                                    + this
                                    + ", "
                                    + ((unreadConnector != null)
                                            ? unreadConnector
                                                    .toString()
                                            : "[null unreadconnector]"));
                    return unreadConnector != null && CotMapComponent
                            .getInstance().getContactConnectorMgr()
                            .initiateContact(this, unreadConnector);
                }
            }
        }

        return false;
    }
}
