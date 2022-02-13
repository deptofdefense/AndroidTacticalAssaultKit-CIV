/*
 * OpenNMEA - A Java library for parsing NMEA 0183 data sentences
 * Copyright (C)2006 Joey Gannon
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package gnu.nmea;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.SimpleTimeZone;

/**
 * An object for storing an NMEA 0183 RMC (Recommended Minimum sentence C) packet
 * <p>
 * RMC packets are expected to start with <code>$GPRMC</code>,
 * and are expected to have <code>13</code> parameters.
 * <p>
 * Checksums should be tested and removed from sentences before being passed to this class.
 * Malformed packets may throw exceptions or exhibit undefined behavior.
 * 
 * https://www.gpsinformation.org/dale/nmea.htm#RMC
 *
 * RMC - NMEA has its own version of essential gps pvt (position, velocity, time) data. It
 * is called RMC, The Recommended Minimum, which will look similar to:
 * $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
 * Where:
 *   RMC          Recommended Minimum sentence C
 *   123519       Fix taken at 12:35:19 UTC
 *   A            Status A=active or V=Void.
 *   4807.038,N   Latitude 48 deg 07.038' N
 *   01131.000,E  Longitude 11 deg 31.000' E
 *   022.4        Speed over the ground in knots
 *   084.4        Track angle in degrees True
 *   230394       Date - 23rd of March 1994
 *   003.1,W      Magnetic Variation
 *   *6A          The checksum data, always begins with *
 * 
 *    https://www.xj3.nl/dokuwiki/doku.php?id=nmea
 * 
 *  After the Magnetic Variation but before the checksum there could be 
 *  one additional fields
 * 
 *    - FAA Mode Indicator 
 *        A = Autonomous mode
 *        D = Differential Mode
 *        E = Estimated (dead-reckoning) mode
 *        M = Manual Input Mode
 *        S = Simulated Mode
 *        N = Data Not Valid
 *
 *  Note that, as of the 2.3 release of NMEA, there is a new field in the RMC sentence at 
 *  the end just prior to the checksum. For more information on this field see here.  
 *
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class PacketRMC extends Packet implements ContainsPosition,
        ContainsSpeed, ContainsTime
{
    /**
     * Constructs an RMC packet
     * @param s Array of string parameters
     */
    public PacketRMC(String[] s)
    {
        super(7);
        if (!s[0].equals("$GPRMC"))
            throw new IllegalArgumentException("Cannot make RMC packet from "
                    + s[0]);

        if (s.length == 12) {

            // added in for BU-353
            Time t = new Time(s[1]);
            data[0] = t;
            Calendar c = new GregorianCalendar(new SimpleTimeZone(0, "GMT"),
                    Locale.ENGLISH);
            c.setTime(t);
            c.set(Calendar.YEAR, Integer.parseInt(s[9].substring(4)) + 2000);
            c.set(Calendar.MONTH, Integer.parseInt(s[9].substring(2, 4)) - 1);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s[9].substring(0, 2)));
            data[1] = c.getTime();
            data[2] = new WrapperBoolean(s[2].equals("A"), "Active");
            data[3] = new Geocoordinate(s[3], s[4], s[5], s[6]);
            data[4] = new WrapperDouble(s[7], "Ground speed");
            data[5] = new WrapperDouble(s[8], "Track angle");
            data[6] = new WrapperDouble("0.0", "Magnetic variation");
        } else if (s.length == 13 || s.length == 14) {
            Time t = new Time(s[1]);
            data[0] = t;
            Calendar c = new GregorianCalendar(new SimpleTimeZone(0, "GMT"),
                    Locale.ENGLISH);
            c.setTime(t);
            c.set(Calendar.YEAR, Integer.parseInt(s[9].substring(4)) + 2000);
            c.set(Calendar.MONTH, Integer.parseInt(s[9].substring(2, 4)) - 1);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s[9].substring(0, 2)));
            data[1] = c.getTime();
            data[2] = new WrapperBoolean(s[2].equals("A"), "Active");
            data[3] = new Geocoordinate(s[3], s[4], s[5], s[6]);
            data[4] = new WrapperDouble(s[7], "Ground speed");
            data[5] = new WrapperDouble(s[8], "Track angle");
            if (s[10].length() > 0) {
                data[6] = new WrapperDouble((String) (s[9].equals("W") ? '-'
                        : '0' + s[10]), "Magnetic variation");
            } else
                data[6] = new WrapperDouble("0.0", "Magnetic variation");
        } else if (s.length == 15) {
            // https://anavs.de/knowledge-base/nmea-format/
            Time t = new Time(s[1]);
            data[0] = t;
            Calendar c = new GregorianCalendar(new SimpleTimeZone(0, "GMT"),
                    Locale.ENGLISH);
            c.setTime(t);
            c.set(Calendar.YEAR, Integer.parseInt(s[11].substring(4)) + 2000);
            c.set(Calendar.MONTH, Integer.parseInt(s[11].substring(2, 4)) - 1);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s[11].substring(0, 2)));
            data[1] = c.getTime();
            data[2] = new WrapperBoolean(s[2].equals("A"), "Active");
            data[3] = new Geocoordinate(s[3], s[4], s[5], s[6]);
            data[4] = new WrapperDouble(s[7], "Ground speed");
            // format include knots which is useless as part of the message
            data[5] = new WrapperDouble(s[9], "Track angle");
            if (s[12].length() > 0) {
                data[6] = new WrapperDouble((String) (s[13].equals("W") ? '-'
                        : '0' + s[12]), "Magnetic variation");
            } else
                data[6] = new WrapperDouble("0.0", "Magnetic variation");
        } else
            throw new IllegalArgumentException("Wrong number of parameters: "
                    + s.length);
    }

    /**
     * Returns the time represented by this packet
     * @return the time
     * @see gnu.nmea.ContainsTime#getTime()
     */
    public Time getTime()
    {
        return (Time) data[0];
    }

    /**
     * Returns the date represented by this packet
     * <p>
     * Note that the date may be set to January 1, 1970 if no date information
     * was available in the data sentence.
     * @return the date
     */
    public Date getDate()
    {
        return (Date) data[1];
    }

    /**
     * Determines whether the data is active or void
     * @return true if data is active, false if data is void
     */
    public boolean isActive()
    {
        return ((WrapperBoolean) data[2]).getValue();
    }

    /**
     * Returns the geographic position represented by this packet
     * @return a geographic position
     * @see gnu.nmea.ContainsPosition#getPosition()
     */
    public Geocoordinate getPosition()
    {
        return (Geocoordinate) data[3];
    }

    /**
     * Returns the ground speed, in knots
     * @return the ground speed
     * @see gnu.nmea.ContainsSpeed#getGroundSpeed()
     */
    public double getGroundSpeed()
    {
        return ((WrapperDouble) data[4]).getValue();
    }

    /**
     * Returns the track angle (course made good) in degrees true
     * @return the track angle
     */
    public double getTrackAngle()
    {
        return ((WrapperDouble) data[5]).getValue();
    }

    /**
     * Returns the magnetic variation, in degrees (west is negative)
     * @return the magnetic variation
     */
    public double getMagneticVariation()
    {
        return ((WrapperDouble) data[6]).getValue();
    }
}
