
package com.atakmap.android.lrf.reader;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import com.atakmap.coremap.locale.LocaleUtil;

public class RangeFinderData {
    public enum Type {
        RANGE(0, "RANGE"), // data in units of meters
        AZIMUTH(1, "AZIMUTH"), // data in units of milliradians
        ELEVATION(2, "ELEVATION"), // data in units of milliradians
        COMPASS_ERROR(3, "COMPASS_ERROR"),
        DISTANCE_ERROR(4, "DISTANCE_ERROR"),
        MAINBOARD_ERROR(5, "MAINBOARD_ERROR"),
        UNKNOWN(6, "UNKNOWN");

        private final int value;
        private final String name;

        Type(final int value, final String name) {
            this.value = value;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

        static public Type findFromValue(int value) {
            for (Type t : Type.values())
                if (t.value == value)
                    return t;
            return null;
        }
    }

    final private Type type;
    final private double data;
    final private String raw;
    final private String checksum;
    final private long timestamp;

    private double convert(final Type type, final double d) {
        if ((type == Type.ELEVATION) || (type == Type.AZIMUTH)) {
            return ((d / 1000.0d) * (180.0d / Math.PI));
        } else {
            return d;
        }
    }

    public String toString() {
        return "type = " + type.getName() + "\n" +
                "raw = " + raw + "\n" +
                "data = " + data + "\n" +
                "data converted = " + convert(type, data) + "\n" +
                "checksum = " + checksum + "\n";
    }

    private RangeFinderData(final Type type, final String raw,
            final double data,
            final String checksum) {

        timestamp = new CoordinatedTime().getMilliseconds(); // record when this line was read according to the
                                                             // system reading the line.

        this.type = type;
        this.data = data;
        this.raw = raw;
        this.checksum = checksum;
    }

    public Type getType() {
        return type;
    }

    public double getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * For TYPE_ELEVATION or TYPE_AZIMUTH this coverters the data to degrees from milliradians. For
     * TYPE_RANGE, this does nothing and just returns getData();
     */
    public double getDataConverted() {
        return convert(type, data);
    }

    /**
     * Parsing of line information for the Vectronix and other variants of a laser range finder.
     * @param l the line of data
     */
    public static RangeFinderData parse(final String l) {
        String line = l.toUpperCase(LocaleUtil.getCurrent());

        if (line.length() > 8) {

            if (line.length() > 9) {
                // VECTOR - USMC VECTOR 21-V.0.3
                if (line.startsWith("D")) {
                    return new RangeFinderData(Type.RANGE,
                            line,
                            Double.parseDouble(line.substring(1, 8)) / 100.d,
                            line.substring(8, 10));
                } else if (line.startsWith("V")) {
                    return new RangeFinderData(Type.RANGE,
                            line,
                            Double.parseDouble(line.substring(1, 8)) / 100.d,
                            line.substring(8, 10));
                } else if (line.startsWith("R")) {
                    return new RangeFinderData(Type.DISTANCE_ERROR,
                            line,
                            Double.parseDouble(line.substring(1, 7)),
                            line.substring(7, 9));
                }
            } else if (line.startsWith("E")) {
                return new RangeFinderData(Type.ELEVATION,
                        line,
                        Double.parseDouble(line.substring(1, 7)) / 10.0d,
                        line.substring(7, 9));
            } else if (line.startsWith("D")) {
                return new RangeFinderData(Type.RANGE,
                        line,
                        Double.parseDouble(line.substring(1, 7)) / 100.d,
                        line.substring(7, 9));
            } else if (line.startsWith("V")) {
                return new RangeFinderData(Type.RANGE,
                        line,
                        Double.parseDouble(line.substring(1, 7)) / 100.d,
                        line.substring(7, 9));
            } else if (line.startsWith("A")) {
                return new RangeFinderData(Type.AZIMUTH,
                        line,
                        Double.parseDouble(line.substring(1, 7)) / 10.0d,
                        line.substring(7, 9));
            } else if (line.startsWith("C")) {
                return new RangeFinderData(Type.COMPASS_ERROR,
                        line,
                        Double.parseDouble(line.substring(1, 7)),
                        line.substring(7, 9));
            } else if (line.startsWith("R")) {
                return new RangeFinderData(Type.DISTANCE_ERROR,
                        line,
                        Double.parseDouble(line.substring(1, 7)),
                        line.substring(7, 9));
            } else if (line.startsWith("M")) {
                return new RangeFinderData(Type.MAINBOARD_ERROR,
                        line,
                        Double.parseDouble(line.substring(1, 7)),
                        line.substring(7, 9));
            }
        }
        return new RangeFinderData(Type.UNKNOWN, line, -1.0d, "");
    }

}
