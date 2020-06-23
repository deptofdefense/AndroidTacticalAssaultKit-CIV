
package com.atakmap.android.imagecapture;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Toolbar options for box select
 */
public class GridToolbar implements IToolbarExtension,
        OnClickListener {

    private static final String TAG = "GridToolbar";
    public static final String IDENTIFIER = "com.atakmap.android.grg.GRID_TOOLBAR";

    private final MapView _mapView;
    private final Context _context;
    private final CustomGrid _grid;
    private ActionBarView _root;
    private ImageButton _deleteBtn, _editBtn;
    private final Button _toolButton;
    private int _gridColor = Color.WHITE;
    private final SharedPreferences _prefs;

    private List<Tool> _tools;
    private GridTool _gridTool;

    public GridToolbar(MapView mapView, Button toolButton) {
        _mapView = mapView;
        _toolButton = toolButton;
        _context = mapView.getContext();
        _grid = GridLinesMapComponent.getCustomGrid();
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());
        setupView();
        ToolbarBroadcastReceiver.getInstance().registerToolbarComponent(
                IDENTIFIER, this);
    }

    private synchronized void setupView() {
        if (_root == null) {
            _root = (ActionBarView) LayoutInflater.from(_context)
                    .inflate(R.layout.grid_toolbar, _mapView, false);

            // Main buttons
            _deleteBtn = _root
                    .findViewById(R.id.grid_remove);
            _deleteBtn.setOnClickListener(this);
            _editBtn = _root.findViewById(R.id.grid_edit);
            _editBtn.setOnClickListener(this);
        }
    }

    @Override
    public synchronized ActionBarView getToolbarView() {
        return _root;
    }

    @Override
    public List<Tool> getTools() {
        synchronized (this) {
            if (_tools == null) {
                _tools = new ArrayList<>();
                _gridTool = new GridTool(_mapView, _toolButton);
                _tools.add(_gridTool);
            }
        }
        return _tools;
    }

    @Override
    public void onToolbarVisible(boolean v) {
        if (_gridTool != null) {
            if (v) {
                _gridTool.requestBeginTool();
            } else {
                _gridTool.requestEndTool();
            }
        }
    }

    @Override
    public boolean hasToolbar() {
        return true;
    }

    @Override
    public synchronized void onClick(View v) {
        if (v == _deleteBtn)
            _gridTool.deleteGrid();
        else if (v == _editBtn)
            openEditDialog();
    }

    private void openEditDialog() {
        View v = LayoutInflater.from(_context).inflate(
                R.layout.grid_edit_dialog, _mapView,
                false);
        final ImageButton colorBtn = v
                .findViewById(R.id.grid_color);
        final SeekBar alphaBar = v.findViewById(R.id.grid_alpha);
        final EditText spacingTxt = v
                .findViewById(R.id.grid_spacing);
        final Button spacingUnits = v.findViewById(R.id.grid_units);
        final CheckBox labelsCb = v
                .findViewById(R.id.grid_show_labels);
        labelsCb.setChecked(_grid.showLabels());

        AlertDialog.Builder adb = new AlertDialog.Builder(_context);
        adb.setTitle("Grid Settings");
        adb.setView(v);
        adb.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                double spacing = getDouble(spacingTxt, _grid.getSpacing());
                String units = spacingUnits.getText().toString();
                if (units.equals(Span.FOOT.getPlural()))
                    spacing = SpanUtilities.convert(spacing, Span.FOOT,
                            Span.METER);
                SharedPreferences.Editor edit = _prefs.edit();
                edit.putFloat(CustomGrid.SPACING_PREF, (float) spacing);
                edit.putInt(CustomGrid.COLOR_PREF, _gridColor);
                edit.putBoolean(CustomGrid.LABELS_PREF, labelsCb.isChecked());
                edit.apply();
                _grid.setColor(_gridColor);
                _grid.setSpacing(spacing);
                _grid.setShowLabels(labelsCb.isChecked());
            }
        });
        adb.setNegativeButton(R.string.cancel, null);
        adb.show();
        setButtonColor(colorBtn, _grid.getColor());
        colorBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openColorPrompt(colorBtn, alphaBar);
            }
        });
        alphaBar.setProgress(Color.alpha(_gridColor));
        alphaBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        setButtonColor(colorBtn,
                                setAlpha(_gridColor, progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        String units = _prefs.getString(CustomGrid.UNITS_PREF,
                Span.METER.getPlural());
        double spacing = _grid.getSpacing();
        if (units.equals(Span.FOOT.getPlural()))
            spacing = SpanUtilities.convert(spacing, Span.METER, Span.FOOT);
        spacingTxt.setText(String.format(LocaleUtil.getCurrent(), "%.1f",
                spacing));
        spacingUnits.setText(units.toLowerCase(LocaleUtil.getCurrent()));
        spacingUnits.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                double spacing = getDouble(spacingTxt, _grid.getSpacing());
                String units = spacingUnits.getText().toString();
                if (units.equals(Span.FOOT.getPlural())) {
                    units = Span.METER.getPlural();
                    spacing = SpanUtilities.convert(spacing, Span.FOOT,
                            Span.METER);
                } else {
                    units = Span.FOOT.getPlural();
                    spacing = SpanUtilities.convert(spacing, Span.METER,
                            Span.FOOT);
                }
                _prefs.edit().putString(CustomGrid.UNITS_PREF, units).apply();
                spacingTxt.setText(String.format(LocaleUtil.getCurrent(),
                        "%.1f", spacing));
                spacingUnits
                        .setText(units.toLowerCase(LocaleUtil.getCurrent()));
            }
        });
    }

    private double getDouble(EditText txt, double defVal) {
        double ret;
        try {
            ret = Double.parseDouble(
                    txt.getText().toString());
        } catch (Exception e) {
            ret = defVal;
        }
        return ret;
    }

    private void setButtonColor(ImageButton v, int color) {
        if (v != null) {
            v.setColorFilter((color & 0xFFFFFF) + 0xFF000000,
                    PorterDuff.Mode.MULTIPLY);
            //v.setImageAlpha(Color.alpha(color));
        }
        _gridColor = color;
    }

    private void openColorPrompt(final ImageButton btn, final SeekBar sb) {
        AlertDialog.Builder adb = new AlertDialog.Builder(_context);
        adb.setTitle("Grid Color");
        ColorPalette palette = new ColorPalette(_context, _grid.getColor());
        adb.setView(palette);
        final AlertDialog alert = adb.create();
        alert.show();
        palette.setOnColorSelectedListener(
                new ColorPalette.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int color, String label) {
                        setButtonColor(btn, setAlpha(color, sb.getProgress()));
                        alert.dismiss();
                    }
                });
    }

    private static int setAlpha(int color, int alpha) {
        return Color.argb(alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }
}
