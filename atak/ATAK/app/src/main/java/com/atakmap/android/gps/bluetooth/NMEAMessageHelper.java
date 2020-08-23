/***************************************************************************
 *  Copyright 2013 PAR Government Systems
 *
 * Restricted Rights:
 * Use, reproduction, or disclosure of executable code, application interface
 * (API), source code, or related information is subject to restrictions set
 * forth in the contract and/or license agreement.    The Government's rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software are restricted as identified in the purchase contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 *
 * Permission has been granted for use within the ATAK application.
 */

package com.atakmap.android.gps.bluetooth;

import com.atakmap.coremap.locale.LocaleUtil;
import java.lang.*;

import java.text.*;
import java.util.*;

import gnu.nmea.*;

/**
 * Please note this is a direct copy of the capability currently within
 * the serial monitor.   The next revision will likely make this more 
 * modular.
 */
public class NMEAMessageHelper {

    private static final String TAG = "NMEAMessageHelper";

    /**
     * Produces a date that is 1 day more than the given date.
     * @param d the date to format
     */
    static public Date produceStaleDate(Date d) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTime();
    }

    /**
     * Formats the date for use within the CoT message.
     * @param d the date to format
     */
    private static String formatTime(Date d) {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.000'Z'", LocaleUtil.US);
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormatGmt.format(d);
    }

    /**
     * Given the trimble PTNL packet, produce a valid external GPS input message.
     * For the dilution 
     */
    public static String createMessage(final PacketPTNL ptnl,
            final String src) {
        Geocoordinate pos = ptnl.getPosition();
        Date t = ptnl.getDate();
        final double altitude = ptnl.getAltitude();
        final double dilution = ptnl.getDilution() * 3;
        final int fixQuality = ptnl.getFixQuality();

        final String altStr;
        if (Double.isNaN(altitude))
            altStr = "9999999.0";
        else
            altStr = Double.toString(altitude);

        String retval = "<?xml version='1.0' standalone='yes'?>" +
                "<event version='2.0' uid='" + "serialmonitor" + "' " +
                "type='a-f-G-I-U-T' " +
                "time='" + formatTime(t) + "' " +
                "start='" + formatTime(t) + "' " +
                "stale='" + formatTime(produceStaleDate(t)) + "' " +
                "how='m-g'>" +
                "<point lat='" + pos.getLatitudeDegrees() + "' lon='"
                + pos.getLongitudeDegrees() +
                "' hae='" + altStr + "' ce='" + dilution +
                "' le='0'/>" +
                "<detail>" + ptnlFixQualityToCotEntry(fixQuality) +
                "<remarks>[" + src + "] "
                + ptnlFixQualityToString(fixQuality)
                + "</remarks>" +
                "<extendedGpsDetails fixQuality='" + fixQuality +
                "' numSatellites='" + ptnl.getNumberOfSatellites() + "' time='"
                + t.getTime() + "'/>" +
                "</detail>" + "</event>";

        //Log.d(TAG, "ptnl info: " + formatTime(t) + " " + altitude + " " + pos + ptnlFixQualityToString(fixQuality) + ptnlFixQualityToCotEntry(fixQuality));
        return retval;
    }

    /**
     * Given both the rmc and gga packets, produce a valid external GPS input message.
     * For the dilution, assume a best case accuracy of 3 meters -
     * https://gis.stackexchange.com/questions/111004/translating-hdop-pdop-and-vdop-to-metric-accuracy-from-given-nmea-strings
     */
    public static String createMessage(final PacketRMC rmc,
            final PacketGGA gga, final String src) {
        Geocoordinate pos = rmc.getPosition();
        Date t = rmc.getDate();

        // gotta be HAE not MSL
        final double altitude = (gga != null) ? gga.getAltitude()
                + gga.getGeoidHeight() : 0;

        final String altStr;
        if (Double.isNaN(altitude))
            altStr = "9999999.0";
        else
            altStr = Double.toString(altitude);

        final double dilution = (gga != null) ? gga.getDilution() * 3 : 9999999;
        final int fixQuality = (gga != null) ? gga.getFixQuality() : 0;
        final int numOfSats = (gga != null) ? gga.getNumberOfSatellites() : 0;

        String retval = "<?xml version='1.0' standalone='yes'?>" +
                "<event version='2.0' uid='" + "serialmonitor" + "' " +
                "type='a-f-G-I-U-T' " +
                "time='" + formatTime(t) + "' " +
                "start='" + formatTime(t) + "' " +
                "stale='" + formatTime(produceStaleDate(t)) + "' " +
                "how='m-g'>" +
                "<point lat='" + pos.getLatitudeDegrees() + "' lon='"
                + pos.getLongitudeDegrees() +
                "' hae='" + altStr + "' ce='" + dilution +
                "' le='0'/>" +
                "<detail>" + fixQualityToCotEntry(fixQuality) +
                "<track course='" + rmc.getTrackAngle() +
                "' speed='" + (rmc.getGroundSpeed() * 0.5144444) +
                "'/><remarks>[" + src + "] "
                + fixQualityToString(fixQuality)
                + "</remarks>" +
                "<extendedGpsDetails fixQuality='" + fixQuality +
                "' numSatellites='" + numOfSats + "' time='"
                + t.getTime() + "'/>" +
                "</detail>" + "</event>";

        return retval;
    }

    private static String fixQualityToString(final int fixQuality) {
        switch (fixQuality) {
            case 1:
                return "GPS fix (SPS)";
            case 2:
                return "DGPS";
            case 3:
                return "PPS";
            case 4:
                return "Real Time Kinematic";
            case 5:
                return "Float RTK";
            case 6:
                return "Estimated";
            case 7:
                return "Manual Input mode";
            case 8:
                return "Simulation Mode";
            default:
                return "Invalid";
        }
    }

    private static String ptnlFixQualityToString(final int fixQuality) {
        switch (fixQuality) {
            case 1:
                return "GPS fix";
            case 2:
                return "RTK float Solution";
            case 3:
                return "RTK fix solution";
            case 4:
                return "DGPS";
            case 5:
                return "SBAS";
            case 6:
                return "RTK";
            case 7:
                return "RTK";
            case 8:
                return "RTK";
            case 9:
                return "RTK";
            case 10:
                return "OmniSTAR";
            case 11:
                return "OmniSTAR";
            case 12:
                return "RTK";
            case 13:
                return "DGPS";
            default:
                return "Invalid";
        }
    }

    private static String ptnlFixQualityToCotEntry(final int fixQuality) {
        String retval = "<precisionlocation geopointsrc='";
        switch (fixQuality) {
            case 4:
            case 13:
                retval += "DGPS";
                retval += "' altitudesrc='";
                retval += "DGPS";
                break;
            case 2:
            case 3:
            case 6:
            case 7:
            case 8:
            case 9:
                retval += "RTK";
                retval += "' altitudesrc='";
                retval += "RTK";
                break;
            default:
                retval += "GPS";
                retval += "' altitudesrc='";
                retval += "GPS";
                break;
        }
        retval += "'/>";
        return retval;
    }

    private static String fixQualityToCotEntry(final int fixQuality) {
        String retval = "<precisionlocation geopointsrc='";
        switch (fixQuality) {
            case 1:
                retval += "GPS";
                retval += "' altitudesrc='";
                retval += "GPS";
                break;
            case 3:
                retval += "GPS_PPS";
                retval += "' altitudesrc='";
                retval += "GPS_PPS";
                break;
            default:
                retval += "GPS";
                retval += "' altitudesrc='";
                retval += "GPS";
                break;
        }
        retval += "'/>";
        return retval;
    }
}
