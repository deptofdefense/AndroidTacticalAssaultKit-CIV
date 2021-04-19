
package com.atakmap.android.user.icon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.CustomNamingView;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.ExpandableGridView;
import com.atakmap.android.vehicle.VehicleBlock;
import com.atakmap.android.vehicle.VehicleShape;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VehiclePalletFragment extends Fragment implements
        View.OnClickListener {

    private static final String TAG = "VehiclePalletFragment";
    private static final String PREF_SELECTED_COLOR = "vehicle.selected.color";
    private static final String PREF_SELECTED_GROUP = "vehicle.selected.group";

    private Context _context;
    private SharedPreferences _prefs;
    private View _root;
    private Button _groupBtn;
    private ImageButton _colorBtn;
    private ExpandableGridView _grid;
    private String _selectedType = "";
    private int _selectedPos = -1;
    private VehicleBlockAdapter _adapter;
    private CustomNamingView _customNamingView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        _context = inflater.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _root = inflater.inflate(R.layout.enter_location_vehicles,
                container, false);
        _customNamingView = new CustomNamingView(CustomNamingView.VEHICLE);

        LinearLayout customHolder = _root.findViewById(
                R.id.customHolder);
        customHolder.addView(_customNamingView.getMainView());

        _adapter = new VehicleBlockAdapter(_context);

        _groupBtn = _root.findViewById(R.id.vehicleGroupBtn);
        _groupBtn.setOnClickListener(this);

        _colorBtn = _root.findViewById(R.id.vehicleColorBtn);
        _colorBtn.setOnClickListener(this);
        _colorBtn.setColorFilter(getVehicleColor(), PorterDuff.Mode.MULTIPLY);

        _grid = _root.findViewById(R.id.cot_vehicle_grid);
        _grid.setAdapter(_adapter);
        _grid.setExpanded(true);

        setGroup(_prefs.getString(PREF_SELECTED_GROUP, "default"));

        return _root;
    }

    public void onItemClick(String type, boolean selected) {
        if (type == null || type.isEmpty())
            return;

        Tool active = ToolManagerBroadcastReceiver
                .getInstance().getActiveTool();

        if (selected) {
            _selectedType = type;
            if (!(active instanceof EnterLocationTool))
                setToolActive(true);
        } else {
            _selectedType = "";
            setToolActive(false);
        }
    }

    private void setGroup(String group) {
        _groupBtn.setText(capitalize(group));
        _adapter.setBlocks(group);
        _prefs.edit().putString(PREF_SELECTED_GROUP, group).apply();
    }

    @Override
    public void onClick(View v) {
        if (v == _colorBtn) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.point_dropper_text56);
            ColorPalette palette = new ColorPalette(getActivity(),
                    getVehicleColor());
            b.setView(palette);
            final AlertDialog alert = b.create();
            palette.setOnColorSelectedListener(new OnColorSelectedListener() {
                @Override
                public void onColorSelected(int color, String label) {
                    alert.cancel();
                    _prefs.edit().putInt(PREF_SELECTED_COLOR, color).apply();
                    _colorBtn.setColorFilter(getVehicleColor(),
                            PorterDuff.Mode.MULTIPLY);
                }
            });
            alert.show();
        } else if (v == _groupBtn) {
            // Clear selection and stop tool
            clearSelection();
            Tool active = ToolManagerBroadcastReceiver.getInstance()
                    .getActiveTool();
            if (active instanceof EnterLocationTool)
                active.requestEndTool();

            // Show groups dialog
            final String[] groups = VehicleBlock.getGroups();
            for (int i = 0; i < groups.length; i++)
                groups[i] = capitalize(groups[i]);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
            b.setTitle(_context.getString(R.string.select_group));
            b.setItems(groups, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    setGroup(groups[w]);
                }
            });
            b.show();
        }
    }

    private String capitalize(String str) {
        StringBuilder sb = new StringBuilder();
        String[] s = str.split(" ");
        for (int j = 0; j < s.length; j++) {
            if (FileSystemUtils.isEmpty(s[j]))
                continue;
            String firstChar = s[j].substring(0, 1).toUpperCase(
                    LocaleUtil.getCurrent());
            if (s[j].length() > 1)
                s[j] = firstChar + s[j].substring(1);
            else
                s[j] = firstChar;
            sb.append(s[j]);
            if (j < s.length - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    private int getVehicleColor() {
        return _prefs.getInt(PREF_SELECTED_COLOR, Color.GREEN);
    }

    private void setToolActive(boolean activate) {
        Intent intent = new Intent();
        if (activate) {
            intent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
            intent.putExtra("current_type", "shape_marker");
            intent.putExtra("checked_position", 0);
        } else
            intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", EnterLocationTool.TOOL_NAME);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    protected MapItem getPointPlacedIntent(GeoPointMetaData gp, String uid) {
        if (_selectedType == null)
            return null;
        VehicleShape vs = new VehicleShape(MapView.getMapView(), uid);
        int alpha = _prefs.getInt("shape_fill_alpha", 150);
        int color = (getVehicleColor() & 0xFFFFFF) + (alpha << 24);
        String name = _selectedType;
        if (!_customNamingView.genCallsign().equals("")) {
            name = _customNamingView.genCallsign();
            _customNamingView.incrementStartIndex();
        }
        vs.setup(_selectedType, name, gp, 0, true);
        vs.setColor(color);
        vs.updateOffscreenInterest();
        vs.save();
        return vs;
    }

    protected void clearSelection() {
        _selectedType = "";
        if (_adapter != null)
            _adapter.clearSelection();
    }

    private class VehicleBlockAdapter extends BaseAdapter {
        private static final String TAG = "VehicleBlockAdapter";

        class ViewHolder {
            TextView label;
            View view;
            String blockName;
        }

        private final Context _context;
        private final List<String> _blocks = new ArrayList<>();

        VehicleBlockAdapter(Context c) {
            _context = c;
        }

        public void setBlocks(String group) {
            String[] blocks = VehicleBlock.getBlocks(group);
            _blocks.clear();
            _blocks.addAll(Arrays.asList(blocks));
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return _blocks.size();
        }

        @Override
        public Object getItem(int position) {
            if (position < _blocks.size())
                return _blocks.get(position);
            return null;
        }

        @Override
        public long getItemId(int position) {
            if (position < _blocks.size())
                return _blocks.get(position).hashCode();
            return 0;
        }

        public void clearSelection() {
            select(-1);
        }

        private void select(int pos) {
            if (_selectedPos != pos) {
                _selectedPos = pos;
                notifyDataSetChanged();
            }
        }

        // create a new ImageView for each item referenced by the Adapter
        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            if (position >= _blocks.size()) {
                Log.e(TAG, "Invalid position: " + position);
                return null;
            }

            View row = convertView;
            final ViewHolder holder;

            if (row == null) {
                LayoutInflater inflater = ((Activity) _context)
                        .getLayoutInflater();
                row = inflater.inflate(R.layout.enter_location_vehicle_child,
                        parent, false);

                holder = new ViewHolder();
                holder.label = row.findViewById(
                        R.id.enter_location_group_childLabel);
                row.setTag(holder);
            } else {
                holder = (ViewHolder) row.getTag();
            }
            String block = _blocks.get(position);
            holder.label.setText(block);
            holder.view = row;
            holder.blockName = block;
            row.setBackgroundColor(_context.getResources().getColor(
                    _selectedPos == position ? R.color.led_green
                            : R.color.dark_gray));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean selected = _selectedPos != position;
                    VehiclePalletFragment.this.onItemClick(holder.blockName,
                            selected);
                    select(selected ? position : -1);
                }
            });
            return row;
        }
    }
}
