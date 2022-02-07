
package com.atakmap.android.channels.ui.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.app.R;
import com.atakmap.android.channels.prefs.ChannelsPrefs;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.http.rest.ServerGroup;

@SuppressLint("LongLogTag")
public class ServerGroupHierarchyListItem extends AbstractHierarchyListItem2
        implements Visibility2 {

    private static final String TAG = "ChannelsServerGroupHierarchyListItem";

    private String server;
    private ServerGroup serverGroup;
    private ChannelsOverlay channelsOverlay;
    private Context context;
    private String title;
    private boolean drawerOpen = false;

    public ServerGroupHierarchyListItem(String server, ServerGroup serverGroup,
            ChannelsOverlay channelsOverlay, Context context) {
        this.server = server;
        this.serverGroup = serverGroup;
        this.channelsOverlay = channelsOverlay;
        this.title = serverGroup.getName();
        this.context = context;

        this.asyncRefresh = true;
        this.reusable = true;
    }

    @Override
    public String getTitle() {
        if (this.title.compareToIgnoreCase("__ANON__") == 0) {
            return "Public";
        } else {
            return this.title;
        }
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    public boolean isChildSupported() {
        return false;
    }

    @Override
    public Object getUserObject() {
        return serverGroup;
    }

    @Override
    public String getIconUri() {
        return "android.resource://" + context.getPackageName() + "/"
                + R.drawable.ic_channel;
    }

    @Override
    public int getVisibility() {
        try {
            return isVisible() ? Visibility2.VISIBLE : Visibility2.INVISIBLE;

        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return Visibility2.VISIBLE;
        }
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
    }

    private static class ViewHolder {
        LinearLayout groupListItemParent;
        CheckBox visibilityCheckbox;
        TextView title;
        TextView desc;

        private ViewHolder() {
        }
    }

    @Override
    public View getListItemView(View view, ViewGroup parent) {
        try {
            ViewHolder holder = view != null
                    && view.getTag() instanceof ViewHolder
                            ? (ViewHolder) view.getTag()
                            : null;

            if (holder == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(context);
                view = inflater.inflate(R.layout.channels_list_item, parent,
                        false);
                holder.groupListItemParent = view
                        .findViewById(R.id.group_list_item_parent);
                holder.visibilityCheckbox = view
                        .findViewById(R.id.group_visibility_checkbox);
                holder.title = view.findViewById(R.id.group_title);
                holder.desc = view.findViewById(R.id.group_description);
                view.setTag(holder);
            }

            holder.visibilityCheckbox.setOnCheckedChangeListener(null);
            holder.visibilityCheckbox.setChecked(isVisible());

            final ViewHolder fHolder = holder;
            holder.visibilityCheckbox
                    .setOnCheckedChangeListener((buttonView, isChecked) -> {

                        int activeCount = 0;
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
                        }
                    });

            holder.groupListItemParent
                    .setOnClickListener((v) -> {
                fHolder.visibilityCheckbox.setChecked(
                        !fHolder.visibilityCheckbox.isChecked());
            });

            holder.title.setText(getTitle());
            if (serverGroup.getDescription() != null) {
                holder.desc.setText(serverGroup.getDescription());
            } else {
                holder.desc.setText("");
            }

            return view;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected void refreshImpl() {
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    @Override
    public String getAssociationKey() {
        return ChannelsPrefs.ASSOCIATION_KEY;
    }
}
