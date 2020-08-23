
package com.atakmap.android.routes.routearound;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atakmap.android.maps.Shape;
import com.atakmap.app.R;

import java.util.List;

/** Handles the display of items in the route around region manager */
public class RouteAroundRegionManagerAdapter extends ArrayAdapter<Shape> {

    private LayoutInflater inflater;
    private RouteAroundRegionViewModel viewModel;

    public RouteAroundRegionManagerAdapter(Context pluginContext,
            RouteAroundRegionViewModel viewModel, List<Shape> regions) {
        super(pluginContext, R.layout.route_around_region_row, regions);
        inflater = LayoutInflater.from(pluginContext);
        this.viewModel = viewModel;
        RouteAroundRegionEventRelay.getInstance()
                .addRouteAroundRegionEventListener(
                        new RouteAroundRegionEventRelay.RouteAroundRegionEventSubscriber() {
                            @Override
                            public void onEvent(
                                    RouteAroundRegionEventRelay.Event event) {
                                if (event instanceof RouteAroundRegionEventRelay.Event.RegionAdded) {
                                    notifyDataSetChanged();
                                }
                                if (event instanceof RouteAroundRegionEventRelay.Event.RegionRemoved) {
                                    notifyDataSetChanged();
                                }
                            }
                        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Shape user = getItem(position);
        RegionViewHolder viewHolder;
        if (convertView == null) {
            viewHolder = onCreateViewHolder(parent);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = viewHolder.view;
            // Cache the viewHolder object inside the fresh view
            convertView.setTag(viewHolder);
        } else {
            // View is being recycled, retrieve the viewHolder object from tag
            viewHolder = (RegionViewHolder) convertView.getTag();
        }
        onBindViewHolder(viewHolder, position);
        // Return the completed view to render on screen
        return convertView;
    }

    private RegionViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = inflater.inflate(R.layout.route_around_region_row, parent,
                false);
        return new RegionViewHolder(view);
    }

    private void onBindViewHolder(RegionViewHolder holder, final int position) {
        if (holder != null) {
            // Initialize the data for the view holder
            Shape region = viewModel.getRegions().get(position);
            if (region != null)
                holder.regionName.setText(region.getTitle());
            // Set the on click listeners
            holder.removeRegionBtn
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            viewModel.removeRegion(
                                    viewModel.getRegions().get(position));
                            notifyDataSetChanged();
                        }
                    });
        }
    }

    // For implementing the view holder pattern.
    private static class RegionViewHolder {
        View view;
        public TextView regionName;
        public ImageButton removeRegionBtn;

        public RegionViewHolder(View view) {
            this.view = view;
            regionName = view.findViewById(R.id.region_name);
            removeRegionBtn = view.findViewById(R.id.remove_region);
        }
    }
}
