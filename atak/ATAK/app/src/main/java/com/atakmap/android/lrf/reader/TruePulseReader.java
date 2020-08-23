
package com.atakmap.android.lrf.reader;

import android.bluetooth.BluetoothDevice;

import com.atakmap.coremap.conversions.ConversionFactors;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Manages the connection and reading from specific supported laser range finders.
 */
public class TruePulseReader extends LRFReader {

    /** 
     * $PLTIT,HV,HDvalue,units,AZvalue,units,INCvalue,units,SDvalue,units,*csum <CR><LF>
     *    where:
     * $PLTIT, is the Criterion message identifier.
     * HV, * Horizontal Vector message type.
     * HDvalue, Calculated Horizontal Distance. Two decimal places.
     * units, F=feet Y=yards M=meters
     * AZvalue, Measured Azimuth value. Two decimal places.  May be positive or negative value.
     * units, D = degrees
     * INCvalues, Measured Inclination value. Two decimal places. May be positive or negative value.
     * units, D = degrees
     * SDvalue, Measured Slope Distance Value. Two decimal places.
     * units, F=feet Y=yards M=meters
     * *csum
     * An asterisk followed by a hexadecimal checksum.
     * The checksum is calculated by XORing all the
     * characters between the dollar sign and the asterisk.
     * <CR>
      * Carriage return.
     * <LF>
      * Optional linefeed.
     * b
     * SD, INC, and HD values always include two decimal places:
     * X X.YY
     * 
     * For the 200X, the azimuth can be Double.NaN - this value will be passed up and the 
     * azimuth will be derived from the orientation of the phone.   A warning will be 
     * toasted.
     */

    static final private String TAG = "TruePulseReader";

    public TruePulseReader(BluetoothDevice device) {
        super(device);
    }

    String line;

    @Override
    public void onRead(final byte[] d) {

        if (d == null)
            return;

        final String buf = new String(d, FileSystemUtils.UTF8_CHARSET);

        //Log.d(TAG, "read in: " + buf);

        if (line == null) {
            line = "";
        }

        line = line + buf;
        Log.d(TAG, "appended line: " + line);

        String processedLine = "";

        if (line.contains("\n")) {
            //Log.d(TAG, "processing line: " + line);
            processedLine = line.substring(0, line.indexOf("\n"));
            processedLine = processedLine.replace("\n", "").replace("\r", "");
            line = line.substring(line.indexOf("\n"));
            line = line.replace("\n", "").replace("\r", "");
        } else {
            // case where the checksum has come in but no newline was received.
            //
            if (line.contains("*")
                    && line.substring(line.indexOf("*")).length() > 2) {
                //Log.d(TAG, "processing line (no newline): " + line);
                processedLine = line;
                line = "";
            }

        }

        //Log.d(TAG, "processed line: " + processedLine);

        if (!processedLine.startsWith("$PLTIT,HV")) {
            return;
        }

        String[] data = processedLine.split(",");
        String distanceString = data[2];
        String distanceUnits = data[3];
        String azimuthString = data[4];
        String inclinationString = data[6];

        double distance = getDistance(distanceString, distanceUnits);
        double azimuth = getAngle(azimuthString);
        double inclination = getAngle(inclinationString);

        try {
            Log.d(TAG, "received: " + processedLine + "   values are  d: "
                    + distance + " a: " + azimuth + " i: " + inclination);

            if (callback != null &&
                    !Double.isNaN(distance)
                    && !Double.isNaN(inclination)) {

                callback.onRangeFinderInfo(azimuth, inclination, distance);

            } else {
                Log.d(TAG, "error reading line: " + processedLine
                        + "   values are  d: " + distance + " " + "a: "
                        + azimuth + "i: " + inclination);
            }
        } catch (Exception e) {
            Log.d(TAG, "error reading line: " + processedLine, e);
        }

    }

    private double getDistance(String valString, String units) {
        try {
            double val = Double.parseDouble(valString);
            if (units.equals("F")) {
                val /= ConversionFactors.METERS_TO_FEET;
            } else if (units.equals("Y")) {
                val /= ConversionFactors.METERS_TO_YARDS;
            }
            return val;
        } catch (Exception e) {
            return Double.NaN;
        }

    }

    private double getAngle(String valString) {
        try {
            return Double.parseDouble(valString);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
