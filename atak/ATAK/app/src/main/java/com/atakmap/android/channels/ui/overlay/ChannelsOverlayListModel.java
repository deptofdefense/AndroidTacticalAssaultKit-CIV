
package com.atakmap.android.channels.ui.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.BaseAdapter;
import android.util.Log;

import com.atakmap.app.R;
import com.atakmap.android.channels.ChannelsMapComponent;
import com.atakmap.android.channels.prefs.ChannelsPrefs;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
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

class ChannelsOverlayListModel extends AbstractHierarchyListItem2
        implements Visibility2, Search, HierarchyListStateListener {

    private static String TAG = "ChannelsOverlayListModel";

    private ChannelsOverlay channelsOverlay;
    private Context context;
    private SharedPreferences sharedPreferences;

    public ChannelsOverlayListModel(ChannelsOverlay channelsOverlay,
            BaseAdapter baseAdapter,
            HierarchyListFilter filter,
            Context context) {

        Log.d(TAG, "creating ChannelsOverlayListModel");
        this.context = context;
        this.channelsOverlay = channelsOverlay;
        this.listener = baseAdapter;

        this.asyncRefresh = true;
        this.reusable = true;
        this.sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);

        refresh(filter);
    }

    @Override
    public String getTitle() {
        return "Channels";
    }

    @Override
    public String getUID() {
        return channelsOverlay.getName();
    }

    @Override
    public String getDescription() {
        try {
            int numServers = getDescendantCount();
            return numServers > 0
                    ? numServers + (numServers == 1 ? " server" : " servers")
                    : "No servers available";
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public View getCustomLayout() {
        try {
            return super.getCustomLayout();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected void refreshImpl() {
        try {
            List<HierarchyListItem> items = new ArrayList<>();
            // Get all connected TAK Servers
            TAKServer[] servers = TAKServerListener.getInstance()
                    .getConnectedServers();
            if (servers == null) {
                Log.e(TAG, "refreshImpl: getConnectedServers returned null!");
                return;
            }

            if (servers.length > 0) {
                for (TAKServer server : servers) {
                    if (server == null) {
                        Log.e(TAG, "refreshImpl: found null server!!");
                        continue;
                    }

                    NetConnectString ncs = NetConnectString
                            .fromString(server.getConnectString());

                    boolean enableChannelsForHost = sharedPreferences.getString(
                            ChannelsMapComponent.PREFERENCE_ENABLE_CHANNELS_HOST_KEY
                                    + "-" +
                                    ncs.getHost(),
                            "false").equals("true");

                    if (enableChannelsForHost) {
                        TakServerHierarchyListItem item = new TakServerHierarchyListItem(
                                context, this, ncs.getHost());
                        item.syncRefresh(this.listener, this.filter);
                        if (this.filter.accept(item)) {
                            items.add(item);
                        }
                    }
                }
            }

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
    public boolean isMultiSelectSupported() {
        return false;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public Object getUserObject() {
        return channelsOverlay;
    }

    @Override
    public String getIconUri() {
        return "android.resource://" + context.getPackageName() + "/"
                + R.drawable.ic_channel;
    }

    @Override
    public String getAssociationKey() {
        return ChannelsPrefs.ASSOCIATION_KEY;
    }

    public static boolean find(String str, String terms) {
        return !FileSystemUtils.isEmpty(str) && str.toLowerCase(
                LocaleUtil.getCurrent()).contains(terms);
    }

    /**
     * Searches the current list of TAK Servers by name or UID
     * @param terms the search terms to use
     * @return the list of TAK Servers that matches the terms
     * Note: The logic for this method was borrowed from DataSync
     *        on 12/4/2020 by mfrazier
     */
    @Override
    public Set<HierarchyListItem> find(String terms) {

        Set<String> searched = new HashSet<>();
        Map<String, HierarchyListItem> ret = new HashMap<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        List<HierarchyListItem> children = getChildren();

        if (FileSystemUtils.isEmpty(children))
            return new HashSet<>(ret.values());

        for (HierarchyListItem item : children) {

            if (!(item instanceof TakServerHierarchyListItem))
                continue;

            TakServerHierarchyListItem serverItem = (TakServerHierarchyListItem) item;

            if (!searched.contains(serverItem.getUID())) {
                if (find(serverItem.getTitle(), terms)
                        || find(serverItem.getUID(), terms)) {
                    ret.put(serverItem.getUID(), serverItem);
                }
                searched.add(serverItem.getUID());
            }
            return new HashSet<>(ret.values());
        }

        return super.find(terms);
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if ((clazz.equals(Visibility.class)
                || clazz.equals(Visibility2.class)))
            return null;
        return super.getAction(clazz);
    }

    @Override
    public boolean onOpenList(HierarchyListAdapter hierarchyListAdapter) {
        Log.d(TAG, "in onOpenList");
        //activeGroupsOverlay.getGroups(null, false);
        return false;
    }

    @Override
    public boolean onCloseList(HierarchyListAdapter var1, boolean var2) {
        Log.d(TAG, "in onCloseList");
        return false;
    }

    @Override
    public void onListVisible(HierarchyListAdapter var1, boolean var2) {
        Log.d(TAG, "in onListVisible");
    }

    @Override
    public boolean onBackButton(HierarchyListAdapter var1, boolean var2) {
        Log.d(TAG, "in onBackButton");
        return false;
    }
}
