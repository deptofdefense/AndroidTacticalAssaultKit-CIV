
package com.atakmap.android.rubbersheet.ui.dropdown;

import android.content.SharedPreferences;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.tool.RubberModelEditTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.coords.GeoCalculations;

public class RubberModelDropDown extends AbstractSheetDropDown implements
        RubberModel.OnChangedListener {

    private static final String NORTH_REF = "rab_north_ref_pref";

    private RubberModel _item;
    private NorthReference _northRef;

    private TextView _pathTxt;
    private TextView _lengthTxt, _widthTxt, _heightTxt;
    private TextView _pitchTxt, _headingTxt, _rollTxt;
    private TextView _scaleTxt;

    public RubberModelDropDown(MapView mapView, MapGroup group) {
        super(mapView);
        _editTool = new RubberModelEditTool(mapView, group);
    }

    @Override
    public boolean show(AbstractSheet item, boolean edit) {
        if (!(item instanceof RubberModel))
            return false;

        _item = (RubberModel) item;

        return super.show(item, edit);
    }

    @Override
    protected void inflateView() {
        super.inflateView();

        View root = LayoutInflater.from(_context).inflate(
                R.layout.rs_model_details_view, _extraLayout);

        _pathTxt = root.findViewById(R.id.modelPath);
        _lengthTxt = root.findViewById(R.id.modelLength);
        _widthTxt = root.findViewById(R.id.modelWidth);
        _heightTxt = root.findViewById(R.id.modelHeight);
        _pitchTxt = root.findViewById(R.id.modelPitch);
        _headingTxt = root.findViewById(R.id.modelHeading);
        _rollTxt = root.findViewById(R.id.modelRoll);
        _scaleTxt = root.findViewById(R.id.modelScale);
    }

    @Override
    protected void refreshArea() {
        super.refreshArea();

        _pathTxt.setText(FileSystemUtils.prettyPrint(_item.getFile()));

        double[] dim = _item.getModelDimensions(true);
        double[] rot = _item.getModelRotation();
        double[] scale = _item.getModelScale();

        // Dimensions
        _widthTxt.setText(Html.fromHtml(_context.getString(
                R.string.width_fmt, fontWhite(formatRange(dim[0])))));
        _lengthTxt.setText(Html.fromHtml(_context.getString(
                R.string.length_fmt, fontWhite(formatRange(dim[1])))));
        _heightTxt.setText(Html.fromHtml(_context.getString(
                R.string.height_fmt, fontWhite(formatRange(dim[2])))));

        // Heading
        double heading = rot[1];
        GeoPoint center = _item.getCenterPoint();
        GeoPoint point4 = _item.getPoint(4);

        // not sure why this could be null, but if it is.
        if (point4 == null)
            point4 = GeoCalculations.pointAtDistance(center, heading, 2000);

        if (_northRef == NorthReference.MAGNETIC)
            heading = ATAKUtilities.convertFromTrueToMagnetic(center, heading);
        else if (_northRef == NorthReference.GRID)
            heading -= ATAKUtilities.computeGridConvergence(center,
                    point4);
        _headingTxt.setText(Html.fromHtml(_context.getString(
                R.string.heading_fmt, fontWhite(AngleUtilities.format(heading)
                        + _northRef.getAbbrev()))));

        // Rotation
        _pitchTxt.setText(Html.fromHtml(_context.getString(
                R.string.pitch_fmt, fontWhite(AngleUtilities.format(rot[0],
                        Angle.DEGREE, false)))));
        _rollTxt.setText(Html.fromHtml(_context.getString(
                R.string.roll_fmt, fontWhite(AngleUtilities.format(rot[2],
                        Angle.DEGREE, false)))));

        // Scale is always 1:1:1 for now
        _scaleTxt.setText(_context.getString(R.string.scale_fmt, scale[0]));
    }

    @Override
    protected void refreshUnits() {
        super.refreshUnits();
        _northRef = NorthReference.MAGNETIC;
        try {
            String val = _prefs.getString(NORTH_REF, String.valueOf(
                    NorthReference.MAGNETIC.getValue()));
            _northRef = NorthReference.findFromValue(Integer.parseInt(val));
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(NORTH_REF)) {
            refreshUnits();
            refresh();
        } else
            super.onSharedPreferenceChanged(p, key);
    }

    @Override
    protected void addSheetListeners() {
        _item.addChangeListener(this);
        super.addSheetListeners();
    }

    @Override
    protected void removeSheetListeners() {
        _item.removeChangeListener(this);
        super.removeSheetListeners();
    }

    @Override
    public void onRotationChanged(RubberModel model, double[] rotation) {
        refreshArea();
    }

    @Override
    public void onAltitudeChanged(RubberModel model, double altitude,
            GeoPoint.AltitudeReference ref) {
        refreshCenter();
    }

    private String fontWhite(String str) {
        return "<font color=\"#FFFFFF\">" + str + "</font>";
    }
}
