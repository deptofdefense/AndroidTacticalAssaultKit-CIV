
package com.atakmap.android.routes.elevation.model;

import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

public class UnitConverter {
    private static final double _FEET_TO_METER = 1200.0 / 3937.0;
    private static final double _METER_TO_FEET = 3937.0 / 1200.0;

    public enum FORMAT {
        FEET,
        METER,
        SLOPE,
        RADIAN,
        GRADE,
        DEGREE
    }

    public static class Feet {
        public static double toMeter(double value) {
            return value * _FEET_TO_METER;
        }
    }

    public static class Meter {
        public static double toFeet(double value) {
            return value * _METER_TO_FEET;
        }
    }

    public static class Grade {

        public static double toSlope(double value) {
            return value / 100.0f;
        }

        public static double toRadian(double value) {
            return Slope.toRadian(value / 100.0f);
        }

        public static double toDegree(double value) {
            return toRadian(value) * 180 / Math.PI;
        }
    }

    public static class Radian {
        public static double toSlope(double value) {
            return Math.tan(value);
        }

        public static double toGrade(double value) {
            return Slope.toGrade(Math.tan(value));
        }

        public static double toDegree(double value) {
            return value * 180 / Math.PI;
        }
    }

    public static class Slope {
        public static double toGrade(double value) {
            value *= 100.0f;
            return value < 0 ? Math.floor(value) : Math.ceil(value);
        }

        public static double toRadian(double value) {
            double angdeg = Math.toDegrees(Math.atan(value));
            // adjust angdeg
            if (!(angdeg >= 0 && angdeg <= 90)) {
                angdeg = -(angdeg - 180.0f);
            }
            double radians = Math.toRadians(angdeg);
            if (radians > Math.PI) {
                radians = -radians + Math.PI;
            }
            return radians;
        }

        public static double toDegree(double value) {
            return toRadian(value) * 180 / Math.PI;
        }
    }

    public static String formatToString(double value, FORMAT from, FORMAT to) {
        DecimalFormat formatter = LocaleUtil.getDecimalFormat("#,###");
        if (from.equals(FORMAT.FEET)) {
            if (to.equals(FORMAT.METER)) {
                return SpanUtilities.format(value, Span.FOOT, Span.METER);
            } else
                return SpanUtilities.format(value, Span.FOOT);
        } else if (from.equals(FORMAT.METER)) {
            if (to.equals(FORMAT.FEET)) {
                return SpanUtilities.format(value, Span.METER, Span.FOOT);
            } else
                return SpanUtilities.format(value, Span.METER);
        } else if (from.equals(FORMAT.GRADE)) {
            if (to.equals(FORMAT.SLOPE)) {
                return formatter.format(Grade.toSlope(value));
            } else if (to.equals(FORMAT.RADIAN)) {
                return formatter.format(Grade.toRadian(value));
            } else if (to.equals(FORMAT.DEGREE)) {
                return formatter.format(Grade.toDegree(value))
                        + Angle.DEGREE_SYMBOL;
            } else
                return ((int) value > 0 ? "+" : "") + (int) value + "%";

        } else if (from.equals(FORMAT.SLOPE)) {
            if (to.equals(FORMAT.GRADE)) {
                return ((int) Slope.toGrade(value) > 0 ? "+" : "")
                        + (int) Slope.toGrade(value)
                        + "%";
            } else if (to.equals(FORMAT.RADIAN)) {
                return formatter.format(Slope.toRadian(value));
            } else if (to.equals((FORMAT.DEGREE))) {
                return formatter.format(Slope.toDegree(value))
                        + Angle.DEGREE_SYMBOL;
            } else
                return formatter.format(value);

        } else if (from.equals(FORMAT.RADIAN)) {
            if (to.equals(FORMAT.SLOPE)) {
                return formatter.format(Radian.toSlope(value));
            } else if (to.equals(FORMAT.GRADE)) {
                return ((int) Radian.toGrade(value) > 0 ? "+" : "")
                        + (int) Slope.toGrade(value)
                        + "%";
            } else
                return formatter.format(value);
        } else if (from.equals(FORMAT.DEGREE)) {
            if (to.equals(FORMAT.SLOPE)) {
                return formatter.format(Radian.toSlope(value));
            } else if (to.equals(FORMAT.GRADE)) {
                return ((int) Radian.toGrade(value) > 0 ? "+" : "")
                        + (int) Slope.toGrade(value)
                        + "%";
            } else
                return formatter.format(value);
        } else
            return formatter.format(value);
    }
}
