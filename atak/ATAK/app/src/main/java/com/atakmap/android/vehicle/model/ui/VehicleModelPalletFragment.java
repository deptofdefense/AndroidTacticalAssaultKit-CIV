
package com.atakmap.android.vehicle.model.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;

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
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import androidx.fragment.app.Fragment;

public class VehicleModelPalletFragment extends Fragment implements
        View.OnClickListener, AdapterView.OnItemClickListener,
        CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "VehicleModelPalletFragment";
    private static final String PREF_SELECTED_COLOR = "vehicle.model.selected.color";
    private static final String PREF_SELECTED_CATEGORY = "vehicle.model.selected.group";
    private static final String PREF_OUTLINE = "vehicle.model.outline";

    private MapView _mapView;
    private SharedPreferences _prefs;
    private MapGroup _vehicleGroup;
    private Context _context;
    private View _root;
    private Button _groupBtn;
    private CheckBox _outlineCB;
    private ImageButton _colorBtn;
    private int _color;
    private ExpandableGridView _grid;
    private String _selectedType = "";
    private VehicleModelGridAdapter _adapter;
    private CustomNamingView _customNamingView;
    private String _category;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup cont,
            Bundle savedInstanceState) {
        _mapView = MapView.getMapView();
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());
        _vehicleGroup = VehicleMapComponent.getVehicleGroup(_mapView);
        _context = inflater.getContext();
        _root = inflater.inflate(R.layout.vehicle_model_pallet, cont, false);
        _customNamingView = new CustomNamingView(
                CustomNamingView.VEHICLE);

        LinearLayout customHolder = _root.findViewById(R.id.customHolder);
        customHolder.addView(_customNamingView.getMainView());

        _adapter = new VehicleModelGridAdapter(_mapView);

        _groupBtn = _root.findViewById(R.id.vehicleGroupBtn);
        _groupBtn.setOnClickListener(this);

        _colorBtn = _root.findViewById(R.id.vehicleColorBtn);
        _colorBtn.setOnClickListener(this);
        _colorBtn.setColorFilter(_color = getColor(), PorterDuff.Mode.MULTIPLY);

        _grid = _root.findViewById(R.id.pallet_grid);
        _grid.setAdapter(_adapter);
        _grid.setExpanded(true);
        _grid.setOnItemClickListener(this);

        setCategory(_prefs.getString(PREF_SELECTED_CATEGORY, "Aircraft"));

        _outlineCB = _root.findViewById(R.id.vehicleOutline);
        _outlineCB.setChecked(_prefs.getBoolean(PREF_OUTLINE, false));
        _outlineCB.setOnCheckedChangeListener(this);

        return _root;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        VehicleModelInfo vehicle = _adapter.getItem(position);
        String type = vehicle.name;
        if (type == null || type.isEmpty())
            return;

        Tool active = ToolManagerBroadcastReceiver
                .getInstance().getActiveTool();

        if (_adapter.getSelected() != vehicle) {
            _selectedType = type;
            if (!(active instanceof EnterLocationTool))
                setToolActive(true);
            _adapter.setSelected(vehicle);
        } else {
            clearSelection();
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
                            _adapter.setColor(color);
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
            final String[] cats = VehicleModelCache.getInstance()
                    .getCategories().toArray(new String[0]);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
            b.setTitle(_context.getString(R.string.select_group));
            b.setItems(cats, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    setCategory(cats[w]);
                }
            });
            b.show();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _outlineCB)
            _prefs.edit().putBoolean(PREF_OUTLINE, checked).apply();
    }

    private void setCategory(String category) {
        _category = category;
        _groupBtn.setText(category);
        _adapter.setCategory(category);
        _prefs.edit().putString(PREF_SELECTED_CATEGORY, category).apply();
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

    protected VehicleModel getPointPlacedIntent(GeoPointMetaData gp,
            String uid) {
        if (_vehicleGroup == null) {
            Log.e(TAG, "Failed to find vehicle group");
            return null;
        }

        VehicleModelInfo info = VehicleModelCache.getInstance().get(
                _category, _selectedType);
        if (info == null) {
            Log.e(TAG, "Failed to find vehicle model: "
                    + _category + "/" + _selectedType);
            return null;
        }

        String name = generateName();
        if (name == null) {
            Log.e(TAG, "Failed to generate name for new vehicle");
            return null;
        }

        VehicleModel vehicle = new VehicleModel(info, gp, uid);
        vehicle.setTitle(name);
        vehicle.setMetaString("entry", "user");
        vehicle.updateOffscreenInterest();
        vehicle.setStrokeColor(getColor());
        if (_outlineCB.isChecked()) {
            vehicle.setShowOutline(true);
            vehicle.setAlpha(0);
        }
        _vehicleGroup.addItem(vehicle);
        vehicle.persist(_mapView.getMapEventDispatcher(), null,
                VehicleModelPalletFragment.class);
        return vehicle;
    }

    private String generateName() {
        String name = _customNamingView.genCallsign();
        if (!name.equals("")) {
            _customNamingView.incrementStartIndex();
            return name;
        } else
            return _selectedType;
    }

    protected void clearSelection() {
        _selectedType = "";
        if (_adapter != null)
            _adapter.setSelected(null);
    }
}
