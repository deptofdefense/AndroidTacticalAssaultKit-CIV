
package com.atakmap.android.channels.ui.overlay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.BaseAdapter;

import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.annotations.DeprecatedApi;
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
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.android.channels.net.ServerGroupsClient;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.cot.event.CotEvent;
import gov.tak.api.util.Disposable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChannelsOverlay extends AbstractMapOverlay2 implements
        CotServiceRemote.CotEventListener,
        ServerGroupsClient.ServerGroupsCallback, Disposable {

    private static final String TAG = "ChannelsOverlay";
    private static final String CHANNELS = "ChannelsOverlay";

    // Timeout (ms) between server group updates
    private static final long SET_GROUPS_TIMEOUT = 2500;

    private final MapView mapView;
    private final Context context;
    private HierarchyListAdapter overlayManager;
    private final Map<String, List<ServerGroup>> serverGroupCache = new HashMap<>();
    private final Map<String, List<ServerGroupHierarchyListItem>> serverGroupHierarchyListItemCache = new HashMap<>();
    private final CotStreamListener cotStreamListener;
    private int notificationId = -1;
    private AlertDialog dialog;

    // Group update operation thread
    private final LimitingThread setGroupsThread;
    private final Set<String> setGroupHosts = new HashSet<>();
    private boolean active;

    public ChannelsOverlay(MapView mapView, Context context) {
        this.mapView = mapView;
        this.context = context;

        final ChannelsOverlay channelsOverlay = this;

        cotStreamListener = new CotStreamListener(mapView.getContext(),
                CHANNELS, this) {

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

        // Set group operation limiter to prevent network spam
        active = true;
        setGroupsThread = new LimitingThread(TAG + "-SetGroups",
                new Runnable() {
                    @Override
                    public void run() {
                        if (active) {
                            // Execute operations on all pending hosts
                            List<String> hosts;
                            synchronized (setGroupHosts) {
                                hosts = new ArrayList<>(setGroupHosts);
                                setGroupHosts.clear();
                            }
                            for (String host : hosts)
                                setGroupsImpl(host);
                            try {
                                Thread.sleep(SET_GROUPS_TIMEOUT);
                            } catch (InterruptedException ignore) {
                            }
                        }
                    }
                });

        // Request groups from all servers
        getGroups(null, false);
    }

    @Override
    public void dispose() {
        active = false;
        setGroupsThread.dispose(false);
        cotStreamListener.dispose();
    }

    @Override
    public String getIdentifier() {
        return CHANNELS;
    }

    @Override
    public String getName() {
        return context.getString(R.string.actionbar_channels);
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
        mapView.getRootGroup().deepForEachItem(
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

    /**
     * Set the active/inactive groups on a given server
     *
     * The operation is carried out asynchronously on a limiter thread
     * to prevent network spam
     *
     * @param host Server host
     */
    public void setGroups(String host) {
        // Add host to setGroups queue and notify the thread
        synchronized (setGroupHosts) {
            setGroupHosts.add(host);
        }
        setGroupsThread.exec();
    }

    private void setGroupsImpl(String host) {
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
                if (outGroups != null) {
                    allGroups.addAll(outGroups);
                    for (ServerGroup outGroup : outGroups) {
                        ServerGroup inGroup = new ServerGroup(outGroup);
                        inGroup.setDirection("IN");
                        allGroups.add(inGroup);
                    }
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

        refresh();

        if (dialog != null) {
            Log.d(TAG, "AlertDialog already visible, skipping");
            return;
        }

        String msg = null;
        if (!foundActiveGroup) {
            Log.d(TAG, "in onGetServerGroups, foundActiveGroup is false");
            msg = context.getString(R.string.server_no_active_channels_msg,
                    server);
        } else if (oldGroups != null
                && !compareGroupLists(oldGroups, outGroups)) {
            Log.d(TAG, "in onGetServerGroups, groups have been updated");
            msg = context.getString(R.string.server_channels_updated_msg,
                    server);
        }

        if (msg == null)
            return;

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.actionbar_channels);
        b.setMessage(msg);
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        displayOverlay(context);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        dialog = b.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                dialog = null;
            }
        });
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

    public static void displayOverlay(Context context) {
        try {
            Intent listIntent = getListIntent(context);
            Log.d(TAG, "Firing intent to open the overlay manager");
            Log.v(TAG, "Intent contents: " + listIntent);
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
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    /** Helper Methods **/

    public static Intent getListIntent(Context context) {
        try {
            Log.d(TAG, "in getListIntent");

            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add(context.getString(R.string.actionbar_channels));

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
                context.getString(R.string.channels_updated),
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

    /* Unused methods */

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public static ChannelsOverlay getOverlay() {
        return null;
    }

    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public static MapGroup getActiveGroupsMapGroup(MapView mapView) {
        return null;
    }
}
