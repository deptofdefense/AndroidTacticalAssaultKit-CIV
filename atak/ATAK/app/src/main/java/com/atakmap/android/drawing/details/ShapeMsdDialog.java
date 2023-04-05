
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.MsdShape;
import com.atakmap.android.gui.ColorButton;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.toolbars.UnitsArrayAdapter;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.text.DecimalFormat;

import androidx.annotation.NonNull;

/**
 * Dialog for setting the minimum safe distance for a shape
 */
public class ShapeMsdDialog {

    private static final DecimalFormat _one = LocaleUtil
            .getDecimalFormat("0.0");
    private static final DecimalFormat _two = LocaleUtil
            .getDecimalFormat("0.00");

    private final MapView _mapView;
    private final Context _context;
    private final UnitPreferences _prefs;

    private Span _units = Span.METER;

    public ShapeMsdDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = new UnitPreferences(mapView);
    }

    /**
     * Show dialog for setting the minimum safe distance for a shape
     * @param shape Shape to set MSD
     */
    public void show(@NonNull
    final Shape shape) {
        final double range;
        final int color;

        final MsdShape existing = findExistingMSD(shape);
        if (existing != null) {
            range = existing.getRange();
            color = existing.getStrokeColor();
        } else {
            range = 0;
            color = Color.RED;
        }

        _units = _prefs.getRangeUnits(range);

        final View v = LayoutInflater.from(_context).inflate(
                R.layout.shape_msd_dialog, _mapView, false);

        final double r = SpanUtilities.convert(range, Span.METER, _units);
        final EditText rangeTxt = v.findViewById(R.id.msd_range);
        rangeTxt.setText(range < 100 ? _two.format(r) : _one.format(r));

        final UnitsArrayAdapter adapter = new UnitsArrayAdapter(_context,
                R.layout.spinner_text_view, Span.values());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        final Spinner unitsSp = v.findViewById(R.id.msd_units);
        unitsSp.setAdapter(adapter);
        unitsSp.setSelection(adapter.getPosition(_units));

        final ColorButton colorBtn = v.findViewById(R.id.msd_color);
        colorBtn.setColor(color);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.minimum_safe_distance);
        b.setView(v);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);

        // Option to remove the existing shape
        if (existing != null) {
            b.setNeutralButton(R.string.remove,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            existing.removeFromGroup();
                            shape.persist(_mapView.getMapEventDispatcher(),
                                    null, getClass());
                        }
                    });
        }

        final AlertDialog d = b.show();
        d.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        _units = (Span) unitsSp.getSelectedItem();
                        try {
                            final double newRange = SpanUtilities.convert(
                                    rangeTxt.getText().toString(), _units,
                                    Span.METER);
                            final int newColor = colorBtn.getColor();

                            // Check that range is greater than zero
                            if (newRange <= 0) {
                                Toast.makeText(_context,
                                        R.string.nineline_text10,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            _prefs.setRangeSystem(_units.getType());

                            MsdShape msd = existing != null ? existing
                                    : new MsdShape(_mapView, shape);
                            msd.setRange(newRange);
                            msd.setStrokeColor(newColor);
                            msd.addToShapeGroup();
                            shape.persist(_mapView.getMapEventDispatcher(),
                                    null, getClass());
                        } catch (Exception e) {
                            Log.e("ShapeMsdDialog",
                                    "error entering information", e);
                        }
                        d.dismiss();
                    }
                });
    }

    /**
     * Find the corresponding MSD shape for a given parent shape
     * @param shape Parent shape
     * @return MSD shape or null if not found
     */
    private MsdShape findExistingMSD(@NonNull
    final Shape shape) {
        MapItem existing = _mapView.getMapItem(shape.getUID() + ".msd");
        return existing instanceof MsdShape ? (MsdShape) existing : null;
    }
}
