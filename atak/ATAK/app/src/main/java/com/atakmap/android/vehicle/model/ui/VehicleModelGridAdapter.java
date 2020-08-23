
package com.atakmap.android.vehicle.model.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

public class VehicleModelGridAdapter extends BaseAdapter {

    private final Context _context;
    private final List<VehicleModelInfo> _models = new ArrayList<>();
    private VehicleModelInfo _selected;
    private int _color = Color.WHITE;

    public VehicleModelGridAdapter(MapView mapView) {
        _context = mapView.getContext();
    }

    public void setCategory(String category) {
        List<VehicleModelInfo> models = VehicleModelCache.getInstance()
                .getAll(category);
        _models.clear();
        _models.addAll(models);
        notifyDataSetChanged();
    }

    public void setColor(int color) {
        _color = color;
        notifyDataSetChanged();
    }

    public void setSelected(VehicleModelInfo vehicle) {
        _selected = vehicle;
        notifyDataSetChanged();
    }

    public VehicleModelInfo getSelected() {
        return _selected;
    }

    @Override
    public int getCount() {
        return _models.size();
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public VehicleModelInfo getItem(int position) {
        return _models.get(position);
    }

    @Override
    public View getView(final int position, View cell, ViewGroup parent) {
        if (cell == null) {
            LayoutInflater inf = LayoutInflater.from(_context);
            cell = inf.inflate(
                    R.layout.vehicle_model_pallet_child, parent, false);
        }
        ImageView icon = cell.findViewById(R.id.icon);
        TextView title = cell.findViewById(R.id.title);

        VehicleModelInfo vehicle = _models.get(position);

        icon.setImageDrawable(vehicle.getIcon());
        icon.setColorFilter(_color, PorterDuff.Mode.MULTIPLY);

        title.setText(vehicle.name);

        cell.setBackgroundColor(_context.getResources().getColor(
                _selected == vehicle ? R.color.led_green
                        : R.color.dark_gray));

        return cell;
    }
}
