
package com.atakmap.android.helloworld.recyclerview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter used to display content in a RecyclerView
 */
public class RecyclerViewAdapter extends RecyclerView.Adapter {

    private static final String TAG = "TimelineMissionAdapter";

    private final MapView _mapView;
    private final LayoutInflater _inflater;
    private final List<MapItem> _items = new ArrayList<>();
    private boolean _listMode = true;

    public RecyclerViewAdapter(MapView mapView, Context plugin) {
        _mapView = mapView;
        _inflater = LayoutInflater.from(plugin);

        addItems(mapView.getRootGroup());
    }

    public void addItem(MapItem item) {
        _items.add(item);
    }

    public void removeItem(MapItem item) {
        _items.remove(item);
    }

    private void addItems(MapGroup group) {
        for (MapItem item : group.getItems()) {
            if (item.hasMetaValue("atakRoleType"))
                _items.add(item);
        }
        for (MapGroup grp : group.getChildGroups())
            addItems(grp);
    }

    public void setListMode(boolean listMode) {
        _listMode = listMode;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = _inflater.inflate(_listMode ? R.layout.marker_callsign_row
                : R.layout.marker_callsign_tile, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder rh, int pos) {
        if (!(rh instanceof ViewHolder))
            return;
        MapItem mi = _items.get(pos);
        ViewHolder h = (ViewHolder) rh;

        ATAKUtilities.SetIcon(_mapView.getContext(), h.icon, mi);

        h.callsign.setText(mi.getTitle());

        long now = new CoordinatedTime().getMilliseconds();
        h.lastUpdate.setText(MathUtils.GetTimeRemainingOrDateString(now,
                now - mi.getMetaLong("lastUpdateTime", 0), true));
    }

    @Override
    public int getItemCount() {
        return _items.size();
    }

    private class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        final ImageView icon;
        final TextView callsign;
        final TextView lastUpdate;

        public ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            callsign = v.findViewById(R.id.callsign);
            lastUpdate = v.findViewById(R.id.last_update);
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            if (pos < 0 || pos >= getItemCount())
                return;
            MapItem item = _items.get(pos);
            MapTouchController.goTo(item, true);
        }
    }
}
