
package com.atakmap.android.user;

import android.graphics.Color;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

public enum TLECategory {
    CAT1(0, 0, 6, "CAT I", Color.GREEN),
    CAT2(1, 7, 15, "CAT II", Color.YELLOW),
    CAT3(2, 16, 30, "CAT III", Color.RED),
    CAT4(3, 31, 91, "CAT IV", Color.WHITE),
    CAT5(4, 92, 305, "CAT V", Color.WHITE),
    UNKNOWN(5, 306, GeoPoint.UNKNOWN, "Unknown", Color.WHITE);

    // Min/max accuracy in meters
    // High is lowest value (greatest accuracy)
    private final int _value;
    private final double _high;
    private final double _low;
    private final String _name;
    private final int _color;

    TLECategory(int value, double high, double low, String name, int color) {
        _value = value;
        _high = high;
        _low = low;
        _name = name;
        _color = color;
    }

    public int getValue() {
        return _value;
    }

    public double getHigh() {
        return _high;
    }

    public double getLow() {
        return _low;
    }

    public double getMedium() {
        if (Double.isNaN(_high))
            return _high;

        return (_high + _low) / 2;
    }

    public String getName() {
        return _name;
    }

    /**
     * Returns the recommended color coding to be associated with the CAT value
     * for display throughout the application. A value of
     * <code>Color.White</code> indicates that there is no recommendation and
     * the application should assume default text coloring.
     * 
     * @return  The recommended color coding to be associated with the CAT value
     *          or <code>Color.White</code> for default text color.
     */
    public int getColor() {
        return _color;
    }

    public static TLECategory getCategory(double ce) {
        for (TLECategory tle : values()) {
            if (ce <= tle._low)
                return tle;
        }
        return UNKNOWN;
    }

    public TLEAccuracy getAccuracy(double ce) {

        if (Double.isNaN(ce))
            return TLEAccuracy.LOW;

        if (ce > _low)
            ce = _low;
        if (ce < _high)
            ce = _high;
        double span = _low - _high;
        ce -= _high;
        int acc = (int) Math.round((ce / span) * 2);
        if (acc == 0)
            return TLEAccuracy.HIGH;
        else if (acc == 2)
            return TLEAccuracy.LOW;
        return TLEAccuracy.MEDIUM;
    }

    public double getCE(TLEAccuracy acc) {
        if (this == UNKNOWN)
            return _low;
        if (acc == TLEAccuracy.HIGH)
            return _high;
        if (acc == TLEAccuracy.LOW)
            return _low;
        return getMedium();
    }

    /**
     * Given a GeoPoint, obtain the appropriate CE string that 
     * describes the category.   If the category is unknown, the
     * the string returned will be UNK.
     * @param gp the geopoint to use to construct the formatted 
     * ce string.
     * @return the formatted ce string, or UNK if unknown.
     */
    public static String getCEString(final GeoPoint gp) {
        return getCEString(gp.getCE());
    }

    /**
     * Given a TLEAccuracy, return the correctly formatted ce 
     * string.
     * @param acc the tle accuracy.
     * @return the formatted string.
     */
    public String getCEString(final TLEAccuracy acc) {
        return TLECategory.getCEString(getCE(acc));
    }

    /**
     * Given a GeoPoint, obtain the appropriate CE string that 
     * describes the category.   If the category is unknown, the
     * the string returned will be UNK.
     * @param ce the ce to use to construct the formatted ce string.
     * @return the formatted ce string, or UNK if unknown.
     */
    public static String getCEString(final double ce) {
        TLECategory tleCat = TLECategory.getCategory(ce);
        return (tleCat == TLECategory.UNKNOWN) ? "UNK"
                : SpanUtilities.formatType(Span.METRIC, ce, Span.METER, true);
    }

}
