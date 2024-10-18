
package com.atakmap.android.gui;

import android.view.View;
import android.widget.TextView;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

public class RangeAndBearingTableHandler {
    private final TextView _rangeMetricText;
    private final TextView _rangeEnglishText;
    private final TextView _rangeNauticalText;
    private final TextView _bearingMagText;
    private final TextView _bearingTrueText;
    private final TextView _bearingMMagText;
    private final TextView _bearingMTrueText;
    private final TextView _bearingCardinal;

    private final View v;
    private final View _rangeBearingTable;

    /**
     * Handle the Range Range and Bearing table included in the v provided.
     * The range and bearing table is defined in rab_layout.xml.
     */
    public RangeAndBearingTableHandler(final View v) {
        this.v = v;
        _rangeBearingTable = v.findViewById(R.id.infoRangeBearingTable);
        _rangeMetricText = v.findViewById(R.id.infoRangeMetricText);
        _rangeEnglishText = v
                .findViewById(R.id.infoRangeEnglishText);
        _rangeNauticalText = v
                .findViewById(R.id.infoRangeNauticalText);
        _bearingMagText = v
                .findViewById(R.id.infoBearingMagneticText);
        _bearingTrueText = v.findViewById(R.id.infoBearingTrueText);
        _bearingMMagText = v
                .findViewById(R.id.infoBearingMMagneticText);
        _bearingMTrueText = v
                .findViewById(R.id.infoBearingMTrueText);
        _bearingCardinal = v
                .findViewById(R.id.infoBearingCardinalText);
    }

    public void setVisibility(int visibility) {
        _rangeBearingTable.setVisibility(visibility);
    }

    public void setEnabled(boolean b) {
        _rangeBearingTable.setEnabled(b);
        _rangeMetricText.setEnabled(b);
        _rangeEnglishText.setEnabled(b);
        _rangeNauticalText.setEnabled(b);
        _bearingMagText.setEnabled(b);
        _bearingTrueText.setEnabled(b);
    }

    public void update(final PointMapItem device, final PointMapItem _marker) {
        if (device != null && _marker != null) {
            update(device.getPoint(), _marker.getPoint());
        } else {
            update((GeoPoint) null, null);
        }
    }

    public void update(final PointMapItem device, final GeoPoint _marker) {
        if (device != null && _marker != null) {
            update(device.getPoint(), _marker);
        } else {
            update((GeoPoint) null, null);
        }
    }

    /**
     * Given two geopoints set up the range and bearing table computing the various values
     * based on the supplied points.
     */
    public void update(final GeoPoint from, final GeoPoint to) {

        if (from != null && to != null) {

            final double distance = GeoCalculations.distanceTo(from, to);
            final double bearing = GeoCalculations.bearingTo(from, to);
            // Adjust the displayed bearing based on the north reference pref

            final double m = ATAKUtilities.convertFromTrueToMagnetic(from,
                    bearing);

            v.post(new Runnable() {
                @Override
                public void run() {
                    _rangeEnglishText.setText(SpanUtilities.formatType(
                            Span.ENGLISH, distance,
                            Span.METER));
                    _rangeMetricText.setText(SpanUtilities.formatType(
                            Span.METRIC, distance,
                            Span.METER));
                    _rangeNauticalText
                            .setText(SpanUtilities.formatType(Span.NM, distance,
                                    Span.METER));
                    _bearingMagText.setText(AngleUtilities.format(m,
                            Angle.DEGREE) + "M");
                    _bearingTrueText.setText(AngleUtilities.format(bearing,
                            Angle.DEGREE) + "T");
                    _bearingMMagText.setText(AngleUtilities
                            .format(m, Angle.MIL) + "M");
                    _bearingMTrueText.setText(AngleUtilities.format(bearing,
                            Angle.MIL) + "T");
                    _bearingCardinal.setText(DirectionType.getDirection(m)
                            .getAbbreviation());
                }
            });
        } else {
            v.post(new Runnable() {
                @Override
                public void run() {
                    _rangeEnglishText.setText("");
                    _rangeMetricText.setText("");
                    _rangeNauticalText.setText("");
                    _bearingMagText.setText("");
                    _bearingTrueText.setText("");
                    _bearingCardinal.setText("");
                }
            });
        }

    }
}
