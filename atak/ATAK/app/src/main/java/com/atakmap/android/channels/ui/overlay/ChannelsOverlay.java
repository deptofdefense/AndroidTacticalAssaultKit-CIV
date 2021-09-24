
package com.atakmap.android.channels.ui.overlay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.app.R;
import com.atakmap.android.channels.ChannelsMapComponent;
import com.atakmap.android.channels.ChannelsReceiver;
import com.atakmap.android.cot.CotMapAdapter;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.http.rest.ServerGroup;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.android.channels.net.ServerGroupsClient;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChannelsOverlay extends DefaultMapGroupOverlay implements
        CotServiceRemote.CotEventListener,
        ServerGroupsClient.ServerGroupsCallback {

    private static final String TAG = "ChannelsOverlay";
    private static final String CHANNELS = "ChannelsOverlay";
    private MapView mapView;
    private Context context;
    private HierarchyListAdapter overlayManager;
    private View overlayHeader;
    private HashMap<String, List<ServerGroup>> serverGroupCache = new HashMap<>();
    private HashMap<String, List<ServerGroupHierarchyListItem>> serverGroupHierarchyListItemCache = new HashMap<>();
    private CotServiceRemote cotServiceRemote;
    private CotStreamListener cotStreamListener;
    private int notificationId = -1;
    private boolean dialogVisible = false;

    public ChannelsOverlay(MapView mapView, Context context) {
        super(mapView, ChannelsOverlay.getActiveGroupsMapGroup(mapView),
                getIconUri(context));
        Log.d(TAG, "Creating ChannelsOverlay");

        this.mapView = mapView;
        this.context = context;

        cotServiceRemote = new CotServiceRemote();
        cotServiceRemote.setCotEventListener(this);

        getGroups(null, false);

        final ChannelsOverlay channelsOverlay = this;

        cotStreamListener = new CotStreamListener(mapView.getContext(),
                CHANNELS, null) {

            @Override
            public void onCotOutputRemoved(Bundle bundle) {
                Log.d(TAG, "in onCotOutputRemoved");
            }

            @Override
            protected void enabled(CotPortListActivity.CotPort port,
                    boolean enabled) {
                Log.d(TAG, "in enabled");
            }

            @Override
            protected void connected(CotPortListActivity.CotPort port,
                    boolean connected) {
                Log.d(TAG, "in connected");
                ServerGroupsClient.getInstance().getAllGroups(context,
                        port.getConnectString(), false, channelsOverlay);
            }

            @Override
            public void onCotOutputUpdated(Bundle descBundle) {
                Log.d(TAG, "in onCotOutputUpdated");
                boolean enabled = descBundle.getBoolean(
                        CotPortListActivity.CotPort.ENABLED_KEY, true);
                boolean connected = descBundle.getBoolean(
                        CotPortListActivity.CotPort.CONNECTED_KEY, false);
                if (enabled && connected) {
                    String connectString = descBundle.getString(
                            CotPortListActivity.CotPort.CONNECT_STRING_KEY);
                    Log.d(TAG, "calling ServerGroupsClient.getAllGroups");
                    ServerGroupsClient.getInstance().getAllGroups(context,
                            connectString, false, channelsOverlay);
                }
            }
        };
    }

    public void getGroups(String host, boolean sendLatestSA) {

        Log.d(TAG, "in getGroups : " + host);

        TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        if (servers == null) {
            Log.e(TAG, "getConnectedServers returned null!");
            return;
        }

        for (TAKServer server : servers) {
            if (host != null) {
                NetConnectString netConnectString = NetConnectString.fromString(
                        server.getConnectString());
                if (!netConnectString.getHost().equals(host)) {
                    continue;
                }
            }

            ServerGroupsClient.getInstance().getAllGroups(context,
                    server.getConnectString(), sendLatestSA, this);
        }
    }

    private boolean nonArchivedMarker(MapItem item) {
        return item instanceof Marker
                && !mapView.getSelfMarker().equals(item)
                && item.getVisible()
                && !CotMapAdapter.isAtakSpecialType(item);
    }

    private void clearMapItems(String netConnectString) {
        // clear the map of all markers streamed in from this server
        MapView.getMapView().getRootGroup().deepForEachItem(
                new MapGroup.MapItemsCallback() {
                    @Override
                    public boolean onItemFunction(
                            MapItem item) {

                        // skip any archived markers
                        if (!nonArchivedMarker(item)) {
                            return false;
                        }

                        // grab the attribute that tells what server the marker came from
                        String serverFromExtra = item
                                .getMetaString("serverFrom", null);

                        // skip any markers that didn't get streamed in
                        if (serverFromExtra == null) {
                            return false;
                        }

                        // skip any markers that weren't from the current server
                        if (netConnectString
                                .compareToIgnoreCase(serverFromExtra) != 0) {
                            return false;
                        }

                        // remove the marker from the map
                        item.removeFromGroup();
                        return false;
                    }
                });
    }

    public boolean isConnected(String host) {
        TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        if (servers == null) {
            Log.e(TAG, "getConnectedServers returned null!");
            return false;
        }

        // find the server we want to update
        for (TAKServer server : servers) {
            NetConnectString netConnectString = NetConnectString.fromString(
                    server.getConnectString());
            if (netConnectString.getHost().equals(host)) {
                return server.isConnected();
            }
        }

        return false;
    }

    public void setGroups(String host) {
        TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        if (servers == null) {
            Log.e(TAG, "getConnectedServers returned null!");
            return;
        }

        // find the server we want to update
        for (TAKServer server : servers) {
            NetConnectString netConnectString = NetConnectString.fromString(
                    server.getConnectString());
            if (netConnectString.getHost().equals(host)) {

                // clear the map of all items from this server
                clearMapItems(netConnectString.toString());

                // prepare our update
                // TODO : revisit for IN/OUT group support
                List<ServerGroup> outGroups = serverGroupCache.get(host);
                List<ServerGroup> allGroups = new ArrayList<>();
                allGroups.addAll(outGroups);
                for (ServerGroup outGroup : outGroups) {
                    ServerGroup inGroup = new ServerGroup(outGroup);
                    inGroup.setDirection("IN");
                    allGroups.add(inGroup);
                }

                // push the update to the server, which will send down an updated SA blast
                ServerGroupsClient.getInstance().setActiveGroups(
                        context, netConnectString.toString(), allGroups);
                return;
            }
        }
    }

    private boolean compareGroupLists(List<ServerGroup> oldGroups,
            List<ServerGroup> newGroups) {
        // bail if the sizes aren't equal
        if (oldGroups.size() != newGroups.size()) {
            return false;
        }

        // try to find each of the old groups in the new groups list
        for (ServerGroup oldGroup : oldGroups) {
            boolean found = false;
            for (ServerGroup newGroup : newGroups) {
                if (oldGroup.getName().equals(newGroup.getName())) {
                    found = true;
                    break;
                }
            }

            // bail if we couldn't find a match
            if (!found) {
                return false;
            }
        }

        // if sizes area equal and each old group is present in the new group list, return true
        return true;
    }

    public void onGetServerGroups(String server,
            List<ServerGroup> serverGroups) {

        boolean foundActiveGroup = false;

        List<ServerGroup> outGroups = new ArrayList<>();

        List<ServerGroupHierarchyListItem> serverGroupHierarchyListItems = new ArrayList<>();
        for (ServerGroup serverGroup : serverGroups) {

            if (serverGroup.getDirection().equals("IN")) {
                continue;
            }

            if (serverGroup.isActive()) {
                foundActiveGroup = true;
            }

            outGroups.add(serverGroup);

            ServerGroupHierarchyListItem serverGroupHierarchyListItem = new ServerGroupHierarchyListItem(
                    server, serverGroup, this, context);
            serverGroupHierarchyListItems.add(serverGroupHierarchyListItem);
        }

        List<ServerGroup> oldGroups = serverGroupCache.put(server, outGroups);
        serverGroupHierarchyListItemCache.put(server,
                serverGroupHierarchyListItems);

        if (dialogVisible) {
            Log.d(TAG, "AlertDialog already visible, skipping");
        } else {
            if (!foundActiveGroup) {
                Log.d(TAG, "in onGetServerGroups, foundActiveGroup is false");
                dialogVisible = true;
                new AlertDialog.Builder(MapView.getMapView().getContext())
                        .setTitle("Channels")
                        .setMessage(
                                "TAK Server has no active channels selected. Would you like to select a channel?")
                        .setPositiveButton(
                                "Yes",
                                (d, w) -> {
                                    displayOverlay(context);
                                    dialogVisible = false;
                                })
                        .setNegativeButton("Cancel",
                                (d, w) -> dialogVisible = false)
                        .show();
            } else if (oldGroups != null
                    && !compareGroupLists(oldGroups, outGroups)) {
                Log.d(TAG, "in onGetServerGroups, groups have been updated");
                dialogVisible = true;
                new AlertDialog.Builder(MapView.getMapView().getContext())
                        .setTitle("Channels")
                        .setMessage(
                                "TAK Server available channels have been updated. Would you like to select a channel?")
                        .setPositiveButton(
                                "Yes",
                                (d, w) -> {
                                    displayOverlay(context);
                                    dialogVisible = false;
                                })
                        .setNegativeButton("Cancel",
                                (d, w) -> dialogVisible = false)
                        .show();
            }
        }

        refresh();
    }

    public List<ServerGroupHierarchyListItem> getServerGroups(String server) {

        List<ServerGroupHierarchyListItem> serverGroupHierarchyListItems = serverGroupHierarchyListItemCache
                .get(server);
        if (serverGroupHierarchyListItems == null) {
            Log.d(TAG,
                    "getServerGroups didnt find serverGroupHierarchyListItems, calling getGroups...");
            getGroups(server, false);
            return null;
        }

        return serverGroupHierarchyListItems;
    }

    public static MapGroup getActiveGroupsMapGroup(MapView mapView) {
        try {
            MapGroup retGroup = mapView.getRootGroup().findMapGroup(CHANNELS);

            if (retGroup == null) {
                retGroup = new DefaultMapGroup(CHANNELS);
                retGroup.setMetaBoolean("permaGroup", true);
                retGroup.setMetaBoolean("ignoreOffscreen", true);

                mapView.getRootGroup().addGroup(retGroup);
            }
            return retGroup;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    private static String getIconUri(Context context) {
        return "android.resource://" + context.getPackageName() + "/"
                + R.drawable.ic_channel;
    }

    public static void displayOverlay(Context context) {
        try {
            Intent listIntent = getListIntent(context);
            Log.d(TAG, "Firing intent to open the overlay manager");
            Log.v(TAG, "Intent contents: " + listIntent.toString());
            AtakBroadcast.getInstance().sendBroadcast(listIntent);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void refresh() {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                if (overlayManager == null) {
                    Log.d(TAG, "calling refreshList with null overlayManager");
                    return;
                }

                if (!overlayManager.isActive()) {
                    Log.d(TAG,
                            "calling refreshList with inactive overlayManager");
                    return;
                }

                Log.d(TAG, "calling refreshList!");
                overlayManager.refreshList();
            }
        });
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter baseAdapter,
            long capabilities,
            HierarchyListFilter hierarchyListFilter) {
        try {
            if (baseAdapter instanceof HierarchyListAdapter)
                overlayManager = (HierarchyListAdapter) baseAdapter;
            return new ChannelsOverlayListModel(
                    this, baseAdapter, hierarchyListFilter, context);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getIdentifier() {
        return CHANNELS;
    }

    @Override
    public String getName() {
        return "Channels";
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    /** Helper Methods **/

    View getOverlayHeader() {
        if (overlayHeader == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            overlayHeader = inflater.inflate(
                    R.layout.channels_overlay_header, mapView, false);
        }
        return overlayHeader;
    }

    /**
     * This method logic is borrowed from Data Sync
     *
     * Get this overlay by searching the overlay listing
     *
     * NOTE: Do not heavily rely on this method - pass in the overlay instance
     * to your class for a more dependable instance
     *
     * @return Data Sync map overlay
     */
    @Deprecated
    public static ChannelsOverlay getOverlay() {
        try {
            Log.d(TAG, "in getOverlay");

            MapView mv = MapView.getMapView();
            if (mv == null)
                return null;

            MapOverlayManager om = mv.getMapOverlayManager();
            if (om == null)
                return null;

            MapOverlay o = om.getOverlay(ChannelsOverlay.class.getSimpleName());
            if (o instanceof ChannelsOverlay)
                return (ChannelsOverlay) o;

            return null;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    public static Intent getListIntent(Context context) {
        try {
            Log.d(TAG, "in getListIntent");

            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add("Channels");

            SharedPreferences sharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context);

            int enableChannnelsCount = 0;
            String enableChannnelsHost = null;

            for (TAKServer takServer : TAKServerListener.getInstance()
                    .getConnectedServers()) {
                NetConnectString ncs = NetConnectString
                        .fromString(takServer.getConnectString());
                String host = ncs.getHost();

                boolean enableChannelsForHost = sharedPreferences.getString(
                        ChannelsMapComponent.PREFERENCE_ENABLE_CHANNELS_HOST_KEY
                                + "-" +
                                host,
                        "false").equals("true");
                if (enableChannelsForHost) {
                    enableChannnelsCount++;
                    enableChannnelsHost = host;
                }
            }

            if (enableChannnelsCount == 1) {
                overlayPaths.add(enableChannnelsHost);
            }

            return new Intent(HierarchyListReceiver.MANAGE_HIERARCHY)
                    .putStringArrayListExtra("list_item_paths", overlayPaths)
                    .putExtra("isRootList", true);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void onCotEvent(CotEvent event, Bundle extra) {

        // validate the inputs
        if (event == null || (event.getType() == null)) {
            Log.e(TAG, "onCotEvent called with invalid event!");
            return;
        }

        // skip any cot events we don't care about
        if (!event.getType().equals("t-x-g-c")) {
            return;
        }

        // grab the serverFrom attribute and bail if not present
        String serverFrom = extra.getString("serverFrom", null);
        if (serverFrom == null) {
            Log.e(TAG,
                    "onCotEvent called with t-x-g-c that's missing serverFrom!");
            return;
        }

        if (notificationId == -1) {
            notificationId = NotificationUtil.getInstance().reserveNotifyId();
        }

        NotificationUtil.getInstance().postNotification(
                notificationId,
                NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                NotificationUtil.BLUE,
                "Groups Updated",
                null, null,
                true);

        // clear the map of all items from this server
        clearMapItems(serverFrom);

        // retrieve the updated groups
        String host = NetConnectString.fromString(serverFrom).getHost();
        getGroups(host, true);

        // send out channels updated intent
        Intent channelsUpdatedIntent = new Intent(
                ChannelsReceiver.CHANNELS_UPDATED);
        channelsUpdatedIntent.putExtra("server", host);
        AtakBroadcast.getInstance().sendBroadcast(channelsUpdatedIntent);
    }
}
