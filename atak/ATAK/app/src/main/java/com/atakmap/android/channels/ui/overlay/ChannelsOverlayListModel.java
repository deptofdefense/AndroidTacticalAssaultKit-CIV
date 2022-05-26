
package com.atakmap.android.channels.ui.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.widget.BaseAdapter;
import android.util.Log;

import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.app.R;
import com.atakmap.android.channels.ChannelsMapComponent;
import com.atakmap.android.channels.prefs.ChannelsPrefs;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Search;
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

@SuppressLint("LongLogTag")
class ChannelsOverlayListModel extends AbstractHierarchyListItem2
        implements Visibility2, Search {

    private static final String TAG = "ChannelsOverlayListModel";

    private final ChannelsOverlay overlay;
    private final Context context;
    private final SharedPreferences prefs;

    public ChannelsOverlayListModel(ChannelsOverlay channelsOverlay,
            BaseAdapter baseAdapter,
            HierarchyListFilter filter,
            Context context) {

        Log.d(TAG, "creating ChannelsOverlayListModel");
        this.context = context;
        this.overlay = channelsOverlay;
        this.listener = baseAdapter;

        this.asyncRefresh = true;
        this.reusable = true;
        this.prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        refresh(filter);
    }

    @Override
    public String getTitle() {
        return context.getString(R.string.actionbar_channels);
    }

    @Override
    public String getUID() {
        return overlay.getName();
    }

    @Override
    public String getDescription() {
        int numServers = getChildCount();
        if (numServers == 0)
            return context.getString(R.string.no_servers_available);
        else
            return context.getString(numServers == 1 ? R.string.server_singular
                    : R.string.server_plural, numServers);
    }

    @Override
    public Drawable getIconDrawable() {
        return context.getDrawable(R.drawable.nav_channels);
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

                    boolean enableChannelsForHost = prefs.getString(
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
        return overlay;
    }

    @Override
    public String getAssociationKey() {
        return ChannelsPrefs.ASSOCIATION_KEY;
    }

    private static boolean find(String str, String terms) {
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
        // Disable visibility
        if (clazz == Visibility.class || clazz == Visibility2.class)
            return null;
        return super.getAction(clazz);
    }
}
