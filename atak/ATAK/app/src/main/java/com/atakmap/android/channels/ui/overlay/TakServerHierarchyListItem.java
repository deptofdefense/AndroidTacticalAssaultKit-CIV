
package com.atakmap.android.channels.ui.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;

import com.atakmap.android.channels.prefs.ChannelsPrefs;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.http.rest.ServerGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressLint("LongLogTag")
public class TakServerHierarchyListItem extends AbstractHierarchyListItem2
        implements Visibility2, Search {

    private static final String TAG = "ChannelsTakServerHierarchyLI";

    private Context context;
    private ChannelsOverlayListModel listModel;
    private ChannelsOverlay overlay;
    private final String host;

    public static final String TAK_SERVER_URL_TAG = "TAK_SERVER_URL";

    public TakServerHierarchyListItem(Context context,
            ChannelsOverlayListModel listModel, String host) {
        this.context = context;
        this.listModel = listModel;
        this.overlay = (ChannelsOverlay) listModel.getUserObject();
        this.host = host;

        this.asyncRefresh = true;
        this.reusable = true;
    }

    @Override
    protected void refreshImpl() {
        try {
            List<ServerGroupHierarchyListItem> serverGroupHierarchyListItems = overlay
                    .getServerGroups(host);
            if (serverGroupHierarchyListItems == null) {
                Log.d(TAG,
                        "no ServerGroupHierarchyListItems found for server! : "
                                + host);
                return;
            }

            List<HierarchyListItem> items = new ArrayList<>();
            for (ServerGroupHierarchyListItem serverGroupHierarchyListItem : serverGroupHierarchyListItems) {
                serverGroupHierarchyListItem.syncRefresh(this.listener,
                        this.filter);
                items.add(serverGroupHierarchyListItem);
            }

            sortItems(items);
            updateChildren(items);

        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    @Override
    public String getTitle() {
        try {
            TAKServer server = getServer();
            return server != null ? server.getDescription() : host;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getDescription() {
        try {
            int numGroups = getDescendantCount();
            return numGroups > 0
                    ? numGroups + (numGroups == 1 ? " group" : " groups")
                    : "No groups found";
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getUID() {
        return host;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public Object getUserObject() {
        return getServer();
    }

    @Override
    public boolean isMultiSelectSupported() {
        return false;
    }

    @Override
    public View getHeaderView() {
        View header = overlay.getOverlayHeader();
        return header;
    }

    @Override
    public String getIconUri() {
        try {
            TAKServer server = getServer();

            String prefix = "android.resource://"
                    + MapView.getMapView().getContext().getPackageName() + "/";

            return server != null && server.isConnected()
                    ? prefix + com.atakmap.app.R.drawable.ic_server_success
                    : prefix + com.atakmap.app.R.drawable.ic_server_error;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<Sort> getSorts() {
        try {
            List<Sort> sorts = new ArrayList<>();
            sorts.add(new SortAlphabet());
            sorts.add(new SortAlphabetDesc());
            return sorts;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    public TAKServer getServer() {

        TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        if (servers == null) {
            Log.e(TAG, "getServer: getConnectedServers returned null!");
            return null;
        }

        if (servers.length > 0) {
            for (TAKServer server : servers) {
                if (server == null) {
                    Log.e(TAG, "getServer: found null server!!");
                    continue;
                }

                NetConnectString ncs = NetConnectString
                        .fromString(server.getConnectString());
                if (ncs.getHost().equals(host)) {
                    return server;
                }
            }
        }

        return null;
    }

    @Override
    public String getAssociationKey() {
        return ChannelsPrefs.ASSOCIATION_KEY;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {

        Set<String> searched = new HashSet<>();
        Map<String, HierarchyListItem> ret = new HashMap<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        List<HierarchyListItem> children = getChildren();

        if (FileSystemUtils.isEmpty(children))
            return new HashSet<>(ret.values());

        for (HierarchyListItem item : children) {

            if (!(item instanceof ServerGroupHierarchyListItem))
                continue;

            ServerGroupHierarchyListItem serverGroupHierarchyListItem = (ServerGroupHierarchyListItem) item;

            if (searched.contains(serverGroupHierarchyListItem.getUID()))
                continue;

            ServerGroup serverGroup = (ServerGroup) serverGroupHierarchyListItem
                    .getUserObject();
            if (serverGroup == null) {
                Log.e(TAG,
                        "Found a null ServerGroup while searching! Ignoring and continuing the search.");
                continue;
            }

            if ((serverGroup.getName() != null && serverGroup.getName()
                    .toLowerCase(LocaleUtil.getCurrent())
                    .contains(terms.toLowerCase(LocaleUtil.getCurrent()))) ||
                    (serverGroup.getDescription() != null
                            && serverGroup.getDescription()
                                    .toLowerCase(LocaleUtil.getCurrent())
                                    .contains(terms.toLowerCase(
                                            LocaleUtil.getCurrent())))) {
                ret.put(serverGroupHierarchyListItem.getUID(),
                        serverGroupHierarchyListItem);
            }

            searched.add(serverGroupHierarchyListItem.getUID());
        }

        return new HashSet<>(ret.values());
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if ((clazz.equals(Visibility.class)
                || clazz.equals(Visibility2.class)))
            return null;
        return super.getAction(clazz);
    }
}
