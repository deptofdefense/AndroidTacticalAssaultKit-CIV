
package com.atakmap.android.channels.ui.overlay;

import android.annotation.SuppressLint;
import android.content.Context;

import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.app.R;
import com.atakmap.android.http.rest.ServerGroup;

@SuppressLint("LongLogTag")
public class ServerGroupHierarchyListItem extends AbstractChildlessListItem
        implements Visibility, ItemClick {

    private static final String TAG = "ChannelsServerGroupHierarchyListItem";

    private final String server;
    private final ServerGroup serverGroup;
    private final ChannelsOverlay channelsOverlay;
    private final Context context;
    private final String title;

    public ServerGroupHierarchyListItem(String server, ServerGroup serverGroup,
            ChannelsOverlay channelsOverlay, Context context) {
        this.server = server;
        this.serverGroup = serverGroup;
        this.channelsOverlay = channelsOverlay;
        this.title = serverGroup.getName();
        this.context = context;
    }

    @Override
    public String getTitle() {
        if (this.title.equalsIgnoreCase("__ANON__"))
            return context.getString(R.string.public_channel);
        else
            return this.title;
    }

    @Override
    public String getDescription() {
        return serverGroup.getDescription();
    }

    @Override
    public ServerGroup getUserObject() {
        return serverGroup;
    }

    @Override
    public String getIconUri() {
        return "gone";
    }

    @Override
    public boolean isVisible() {
        return serverGroup.isActive();
    }

    @Override
    public boolean setVisible(boolean visible) {
        if (channelsOverlay.isConnected(server)) {
            serverGroup.setActive(visible);
            channelsOverlay.setGroups(server);
        }
        return serverGroup.isActive();

        // XXX - Copied from old view code
        /*int activeCount = 0;
        for (ServerGroupHierarchyListItem serverGroupHierarchyListItem : channelsOverlay
                .getServerGroups(server)) {
            if (serverGroupHierarchyListItem.isVisible()) {
                activeCount++;
            }
        }
        
        if (activeCount == 1 && !isChecked) {
            fHolder.visibilityCheckbox.setChecked(!isChecked);
        } else {
            setVisible(isChecked);
        }*/
    }

    @Override
    public boolean onClick() {
        // Toggle visibility when the entire row is tapped
        setVisible(!isVisible());
        notifyListener();
        return true;
    }

    @Override
    public boolean onLongClick() {
        return false;
    }
}
