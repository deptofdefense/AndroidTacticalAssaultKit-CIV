
package com.atakmap.android.user.icon;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.CustomNamingView;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.ExpandableGridView;
import com.atakmap.android.vehicle.VehicleMapComponent;
import com.atakmap.android.vehicle.overhead.OverheadImage;
import com.atakmap.android.vehicle.overhead.OverheadMarker;
import com.atakmap.android.vehicle.overhead.OverheadParser;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

public class OverheadPalletFragment extends Fragment implements
        View.OnClickListener {

    private static final String TAG = "OverheadPalletFragment";
    private static final String PREF_SELECTED_COLOR = "overhead.selected.color";
    private static final String PREF_SELECTED_GROUP = "overhead.selected.group";

    private MapView _mapView;
    private SharedPreferences _prefs;
    private MapGroup _overheadGroup;
    private Context _context;
    private View _root;
    private Button _groupBtn;
    private ImageButton _colorBtn;
    private int _color;
    private ExpandableGridView _grid;
    private String _selectedType = "";
    private OverheadPalletAdapter _adapter;
    private CustomNamingView _customNamingView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        _mapView = MapView.getMapView();
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());
        _overheadGroup = VehicleMapComponent.getOverheadGroup(_mapView);
        _context = inflater.getContext();
        _root = inflater.inflate(
                R.layout.enter_location_overhead, container, false);
        _customNamingView = new CustomNamingView(
                CustomNamingView.OVERHEAD);

        LinearLayout customHolder = _root
                .findViewById(R.id.customHolder);
        customHolder.addView(_customNamingView.getMainView());

        _adapter = new OverheadPalletAdapter(_context);

        _groupBtn = _root.findViewById(R.id.vehicleGroupBtn);
        _groupBtn.setOnClickListener(this);

        _colorBtn = _root.findViewById(R.id.vehicleColorBtn);
        _colorBtn.setOnClickListener(this);
        _colorBtn.setColorFilter(_color = getColor(), PorterDuff.Mode.MULTIPLY);

        _grid = _root.findViewById(R.id.pallet_grid);
        _grid.setAdapter(_adapter);
        _grid.setExpanded(true);

        setGroup(_prefs.getString(PREF_SELECTED_GROUP, "Aircraft"));

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

    @Override
    public void onClick(View v) {
        if (v == _colorBtn) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.point_dropper_text56);
            ColorPalette palette = new ColorPalette(getActivity(),
                    getColor());
            b.setView(palette);
            final AlertDialog alert = b.create();
            palette.setOnColorSelectedListener(
                    new ColorPalette.OnColorSelectedListener() {
                        @Override
                        public void onColorSelected(int color, String label) {
                            alert.cancel();
                            _color = color;
                            _prefs.edit().putInt(PREF_SELECTED_COLOR, color)
                                    .apply();
                            _colorBtn.setColorFilter(_color,
                                    PorterDuff.Mode.MULTIPLY);
                            _adapter.notifyDataSetChanged();
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
            final String[] groups = OverheadParser.getGroups();
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

    private void setGroup(String group) {
        _groupBtn.setText(group);
        _adapter.setGroup(group);
        _prefs.edit().putString(PREF_SELECTED_GROUP, group).apply();
    }

    private int getColor() {
        return _prefs.getInt(PREF_SELECTED_COLOR, Color.WHITE);
    }

    private void setToolActive(boolean activate) {
        Intent intent = new Intent();
        if (activate) {
            intent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
            intent.putExtra("current_type", "overhead_marker");
            intent.putExtra("checked_position", 0);
        } else
            intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        intent.putExtra("tool", EnterLocationTool.TOOL_NAME);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    protected OverheadMarker getPointPlacedIntent(GeoPointMetaData gp,
            String uid) {
        if (_overheadGroup == null)
            return null;
        OverheadImage img = OverheadParser.getImageByName(_selectedType);
        OverheadMarker marker = new OverheadMarker(img, gp, uid);
        marker.setMetaString("entry", "user");
        marker.setColor(getColor());
        marker.setMetaDouble("height", img.height);
        marker.setMetaInteger("height_unit", Span.METER.getValue());
        _overheadGroup.addItem(marker);
        marker.persist(_mapView.getMapEventDispatcher(), null,
                OverheadPalletFragment.class);
        String name = _customNamingView.genCallsign();
        if (!name.equals("")) {
            marker.setTitle(name);
            _customNamingView.incrementStartIndex();
            return marker;
        } else {
            if (_selectedType != null) {
                marker.setTitle(_selectedType);
                return marker;
            } else {
                return null;
            }
        }
    }

    protected void clearSelection() {
        _selectedType = "";
        if (_adapter != null)
            _adapter.select(-1);
    }

    private class OverheadPalletAdapter extends BaseAdapter {

        private final Context _context;
        private final List<OverheadImage> _images = new ArrayList<>();
        private int _selected = -1;

        OverheadPalletAdapter(Context context) {
            _context = context;
        }

        public void setGroup(String group) {
            List<OverheadImage> images = OverheadParser.getImages(group);
            _images.clear();
            _images.addAll(images);
            notifyDataSetChanged();
        }

        public void select(int position) {
            _selected = position;
            notifyDataSetChanged();
        }

        public int getSelected() {
            return _selected;
        }

        @Override
        public int getCount() {
            return _images.size();
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return _images.get(position);
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inf = LayoutInflater.from(_context);
                convertView = inf.inflate(
                        R.layout.enter_location_overhead_child, parent, false);
            }
            ImageView icon = convertView.findViewById(R.id.icon);
            TextView title = convertView.findViewById(R.id.title);

            OverheadImage img = _images.get(position);
            final String type = img.name;

            if (img.resId != 0) {
                icon.setImageResource(img.resId);
                icon.setColorFilter(_color, PorterDuff.Mode.MULTIPLY);
            }

            title.setText(type);

            convertView.setBackgroundColor(_context.getResources().getColor(
                    _selected == position ? R.color.led_green
                            : R.color.dark_gray));

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(_selected == position ? -1 : position);
                    onItemClick(type, _selected != -1);
                }
            });

            return convertView;
        }
    }
}
