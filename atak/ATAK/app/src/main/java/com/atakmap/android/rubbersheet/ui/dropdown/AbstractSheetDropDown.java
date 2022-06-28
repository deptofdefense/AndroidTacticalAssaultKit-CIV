
package com.atakmap.android.rubbersheet.ui.dropdown;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.tool.RubberSheetEditTool;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolListener;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

public abstract class AbstractSheetDropDown extends DropDownReceiver
        implements DropDown.OnStateListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        MapEventDispatcher.MapEventDispatchListener,
        CompoundButton.OnCheckedChangeListener,
        PointMapItem.OnPointChangedListener,
        SeekBar.OnSeekBarChangeListener,
        Shape.OnPointsChangedListener,
        View.OnClickListener,
        ToolListener {

    protected static final String COORD_FMT = "coord_display_pref";
    protected static final String RANGE_FMT = "rab_rng_units_pref";

    protected final MapView _mapView;
    protected final Context _context;
    protected final SharedPreferences _prefs;
    protected RubberSheetEditTool _editTool;

    protected AbstractSheet _item;
    protected CoordinateFormat _coordFmt = CoordinateFormat.MGRS;
    protected int _rangeSys = Span.METRIC;

    protected ViewGroup _root;
    protected EditText _nameTxt;
    protected RemarksLayout _remarksLayout;
    protected TextView _areaTxt;
    protected Button _centerBtn;
    protected ImageButton _colorBtn;
    protected CheckBox _showLabels;
    protected SeekBar _alphaBar, _thicknessBar;
    protected View _defaultActions, _editActions;
    protected Button _editBtn, _undoBtn, _endBtn;
    protected ViewGroup _extraLayout;
    protected ExtraDetailsLayout _extraDetails;

    public AbstractSheetDropDown(MapView mapView) {
        super(mapView);
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _prefs.registerOnSharedPreferenceChangeListener(this);
        ToolManagerBroadcastReceiver.getInstance().registerListener(this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Handled by RubberSheetReceiver
    }

    @Override
    public void disposeImpl() {
        if (_editTool != null)
            _editTool.dispose();
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
        ToolManagerBroadcastReceiver.getInstance().unregisterListener(this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    public boolean show(final AbstractSheet item, final boolean edit) {
        if (_root == null)
            inflateView();

        // Re-open on top
        if (!isClosed() && !isVisible()) {
            closeDropDown();
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    show(item, edit);
                }
            });
            return true;
        }

        _item = item;
        if (edit)
            edit();

        if (isClosed())
            showDropDown(_root, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, this);

        return true;
    }

    public void edit() {
        if (_item == null || _editTool == null)
            return;
        Bundle extras = new Bundle();
        extras.putString("uid", _item.getUID());
        ToolManagerBroadcastReceiver.getInstance().startTool(
                _editTool.getIdentifier(), extras);
    }

    protected void inflateView() {
        _root = (ViewGroup) LayoutInflater.from(_context).inflate(
                R.layout.rs_details_view, _mapView, false);
        GenericDetailsView.addEditTextPrompts(_root);

        _nameTxt = _root.findViewById(R.id.sheetName);
        _centerBtn = _root.findViewById(R.id.sheetCenter);
        _areaTxt = _root.findViewById(R.id.sheetArea);
        _showLabels = _root.findViewById(R.id.sheetLabels);
        _colorBtn = _root.findViewById(R.id.sheetColor);
        _alphaBar = _root.findViewById(R.id.sheetAlpha);
        _thicknessBar = _root.findViewById(R.id.sheetThickness);
        _remarksLayout = _root.findViewById(R.id.remarksLayout);
        _defaultActions = _root.findViewById(R.id.sheetActionsDefault);
        _editActions = _root.findViewById(R.id.sheetActionsEdit);
        _editBtn = _root.findViewById(R.id.sheetEdit);
        _undoBtn = _root.findViewById(R.id.sheetEditUndo);
        _endBtn = _root.findViewById(R.id.sheetEditEnd);
        _extraLayout = _root.findViewById(R.id.sheetExtraLayout);
        _extraDetails = _root.findViewById(R.id.extrasLayout);

        _centerBtn.setOnClickListener(this);
        _showLabels.setOnCheckedChangeListener(this);
        _colorBtn.setOnClickListener(this);
        _alphaBar.setOnSeekBarChangeListener(this);
        _thicknessBar.setOnSeekBarChangeListener(this);
        _editBtn.setOnClickListener(this);
        _undoBtn.setOnClickListener(this);
        _endBtn.setOnClickListener(this);
        _nameTxt.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable et) {
                if (_item != null)
                    _item.setTitle(et.toString());
            }
        });
        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable et) {
                if (_item != null)
                    _item.setMetaString("remarks", et.toString());
            }
        });
    }

    protected void refresh() {
        if (_root == null || _item == null || !isVisible())
            return;

        _nameTxt.setText(_item.getTitle());

        refreshCenter();
        refreshArea();

        _showLabels.setChecked(_item.getLabelVisibility());

        _colorBtn.setColorFilter(_item.getStrokeColor(),
                PorterDuff.Mode.MULTIPLY);

        _alphaBar.setProgress(_item.getAlpha());
        _thicknessBar.setProgress((int) (_item.getStrokeWeight() * 10) - 10);

        String remarks = _item.getMetaString("remarks", null);
        _remarksLayout.setText(remarks != null ? remarks : "");

        Tool tool = getActiveTool();
        if (tool != null) {
            _undoBtn.setVisibility(tool instanceof Undoable
                    ? View.VISIBLE
                    : View.GONE);
            _editActions.setVisibility(View.VISIBLE);
            _defaultActions.setVisibility(View.GONE);
        } else {
            _editActions.setVisibility(View.GONE);
            _defaultActions.setVisibility(View.VISIBLE);
        }

        _extraDetails.setItem(_item);
    }

    protected void refreshCenter() {
        GeoPointMetaData center = _item.getCenter();
        GeoPoint c = center.get();
        _centerBtn.setText(_context.getString(R.string.coordinate_alt,
                CoordinateFormatUtilities.formatToString(c, _coordFmt),
                AltitudeUtilities.format(c, _prefs),
                center.getAltitudeSource()));
    }

    protected void refreshArea() {
        double width = _item.getWidth();
        double length = _item.getLength();
        double area = width * length;
        int units = _rangeSys;
        if (_rangeSys == Span.ENGLISH && _prefs.getBoolean(
                "area_units_pref", false))
            units = Area.AC;
        _areaTxt.setText(_context.getString(R.string.area_fmt,
                formatRange(length), formatRange(width),
                AreaUtilities.formatArea(units, area, Area.METER2)));
    }

    protected void refreshUnits() {
        _coordFmt = CoordinateFormat.find(_prefs.getString(COORD_FMT,
                CoordinateFormat.MGRS.toString()));
        _rangeSys = Span.METRIC;
        try {
            _rangeSys = Integer.parseInt(_prefs.getString(RANGE_FMT,
                    String.valueOf(_rangeSys)));
        } catch (Exception ignore) {
        }
    }

    protected String formatRange(double meters) {
        return SpanUtilities.formatType(_rangeSys, meters, Span.METER);
    }

    protected Tool getActiveTool() {
        Tool t = ToolManagerBroadcastReceiver.getInstance().getActiveTool();
        return t == _editTool ? t : null;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED)
                && event.getItem() == _item && !isClosed())
            closeDropDown();
    }

    @Override
    public void onPointChanged(PointMapItem pmi) {
        refreshCenter();
    }

    @Override
    public void onPointsChanged(Shape shape) {
        refreshArea();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (_item == null)
            return;
        if (v) {
            addSheetListeners();
            refreshUnits();
            refresh();
        } else
            removeSheetListeners();
    }

    protected void addSheetListeners() {
        _item.addOnPointsChangedListener(this);
        Marker m = _item.getCenterMarker();
        if (m != null)
            m.addOnPointChangedListener(this);
    }

    protected void removeSheetListeners() {
        _item.removeOnPointsChangedListener(this);
        Marker m = _item.getCenterMarker();
        if (m != null)
            m.removeOnPointChangedListener(this);
    }

    @Override
    public void onToolBegin(Tool tool, Bundle extras) {
        refresh();
    }

    @Override
    public void onToolEnded(Tool tool) {
        refresh();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(COORD_FMT) || key.equals(RANGE_FMT)) {
            refreshUnits();
            refresh();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (_item == null)
            return;

        // Toggle label visibility
        if (cb == _showLabels && checked != _item.getLabelVisibility())
            _item.setLabelVisibility(checked);
    }

    @Override
    public void onProgressChanged(SeekBar bar, int prog, boolean user) {
        if (_item == null)
            return;

        // Content alpha
        if (bar == _alphaBar)
            _item.setAlpha(prog);

        // Stroke width
        else if (bar == _thicknessBar)
            _item.setStrokeWeight(1 + (prog / 10d));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onClick(View v) {
        if (_item == null)
            return;

        // Set center point
        if (_centerBtn == v)
            promptCenterLocation();

        // Set rectangle color
        else if (_colorBtn == v)
            promptColor();

        // Edit rectangle
        else if (_editBtn == v)
            edit();

        // Undo edit
        else if (_undoBtn == v || _endBtn == v) {
            Tool tool = getActiveTool();
            if (tool != null) {
                if (_undoBtn == v) {
                    if (tool instanceof Undoable)
                        ((Undoable) tool).undo();
                } else
                    tool.requestEndTool();
            }
        }
    }

    private void promptCenterLocation() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        LayoutInflater inf = LayoutInflater.from(_context);
        final CoordDialogView cdv = (CoordDialogView) inf.inflate(
                com.atakmap.app.R.layout.draper_coord_dialog, null);
        b.setTitle(_context.getString(R.string.sheet_center_point));
        b.setView(cdv);
        b.setPositiveButton(_context.getString(R.string.ok), null);
        b.setNegativeButton(_context.getString(R.string.cancel), null);
        cdv.setParameters(_item.getCenter(), _mapView.getPoint(), _coordFmt);
        final AlertDialog d = b.create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        GeoPointMetaData p = cdv.getPoint();
                        CoordinateFormat cf = cdv.getCoordFormat();
                        CoordDialogView.Result res = cdv.getResult();
                        if (res == CoordDialogView.Result.INVALID)
                            return;
                        _coordFmt = cf;
                        if (res == CoordDialogView.Result.VALID_CHANGED) {
                            _item.move(_item.getCenter(), p, true);
                            CameraController.Programmatic.panTo(
                                    _mapView.getRenderer3(), p.get(), true);
                        }
                        refreshCenter();
                        d.dismiss();
                    }
                });
    }

    private void promptColor() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.rectangle_color));
        ColorPalette p = new ColorPalette(_context, _item.getStrokeColor());
        b.setView(p);
        final AlertDialog d = b.create();
        ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                _item.setStrokeColor(color);
                refresh();
                d.dismiss();
            }
        };
        p.setOnColorSelectedListener(l);
        d.show();
    }
}
