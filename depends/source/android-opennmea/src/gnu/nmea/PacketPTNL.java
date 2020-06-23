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
 *    Standard for the PTNL String from Trimble
 *https://www.trimble.com/OEM_ReceiverHelp/V4.44/en/NMEA-0183messages_PTNL_GGK.html
 *    -----------------------------------------
 *    0     Talker ID $PTNL
 *    1     Message ID GGK
 *    2     UTC time of position fix, in hhmmmss.ss format. Hours must be
 *              two numbers, so may be padded. For example, 7 is shown as 07.
 *    3     UTC date of position fix, in ddmmyy format. Day must be two
 *              numbers, so may be padded. For example, 8 is shown as 08.
 *    4     Latitude, in degrees and decimal minutes (dddmm.mmmmmmm)
 *    5     Direction of latitude:
 *       N: North
 *       S: South
 *    6     Longitude, in degrees and decimal minutes (dddmm.mmmmmmm).
 *              Should contain three digits of ddd.
 *    7     Direction of longitude:
 *    E: East
 *    W: West
 *    8     GPS Quality indicator:
 *       0: Fix not available or invalid
 *       1: Autonomous GPS fix
 *       2: RTK float solution
 *       3: RTK fix solution
 *       4: Differential, code phase only solution (DGPS)
 *       5: SBAS solution – WAAS/EGNOS/MSAS
 *       6: RTK float or RTK location 3D Network solution
 *       7: RTK fixed 3D Network solution
 *       8: RTK float or RTK location 2D in a Network solution
 *       9: RTK fixed 2D Network solution
 *       10: OmniSTAR HP/XP solution
 *       11: OmniSTAR VBS solution
 *       12: Location RTK solution
 *       13: Beacon DGPS
 *    9     Number of satellites in fix
 *    10     Dilution of Precision of fix (DOP)
 *    11     Ellipsoidal height of fix (antenna height above
 *              ellipsoid). Must start with EHT.
 *    12     M: ellipsoidal height is measured in meters
 *    13     The checksum data, always begins with *
 * Note – The PTNL,GGK message is longer than the NMEA-0183 standard of
 * 80 characters.
 * Note – Even if a user-defined geoid model, or an inclined plane is loaded
 * into the receiver, then the height output in the NMEA GGK string is always
 * an ellipsoid height, for example, EHT24.123.
 * 
 * PTNL packets are expected to start with <code>$PTNL</code>,
 * and are expected to have <code>14</code> parameters.
 * <p>
 * Checksums should be tested and removed from sentences before being passed to this class.
 * Malformed packets may throw exceptions or exhibit undefined behavior.
 * 
 * @author Shawn Bisgrove
 * @version 1.00, 01/04/18
 * @since OpenNMEA v0.1
 */
public class PacketPTNL extends Packet implements ContainsPosition,
        ContainsTime
{
    /**
     * Constructs a PTNL packet
     * @param s Array of string parameters
     */
    public PacketPTNL(String[] s)
    {
        super(9);
        if (!s[0].equals("$PTNL"))
            throw new IllegalArgumentException("Cannot make GGA packet from "
                    + s[0]);
        if (s.length == 13)
        {
            data[0] = s[1]; // ID GGK

            Time t = new Time(s[2]);
            data[1] = t;
            Calendar c = new GregorianCalendar(new SimpleTimeZone(0, "GMT"),
                    Locale.ENGLISH);
            c.setTime(t);

            // Verified that the below is backwards from the information provided
            // for the spec.    This logic has been verified using a R10/R8
            // The trimble I have provides MMDDYY.
            c.set(Calendar.YEAR, Integer.parseInt(s[3].substring(4)) + 2000);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s[3].substring(2, 4)));
            c.set(Calendar.MONTH, Integer.parseInt(s[3].substring(0, 2)) - 1);

            data[2] = c.getTime();

            data[3] = new Geocoordinate(s[4], s[5], s[6], s[7]);
            data[4] = new WrapperInt(s[8], "GPS quality indicator");
            data[5] = new WrapperInt(s[9], "Number of satellites");
            data[6] = new WrapperDouble(s[10], "Dilution of Precision Fix");
            data[7] = new WrapperDouble(s[11].replaceAll("EHT", ""),
                    "Altitude above sea level (m)");
            data[8] = s[12];
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
        return (Time) data[1];
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
        return (Date) data[2];
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
     * Returns the satellite fix quality
     * <p>
     * Fix quality can take the following values:<br>
     * <code>
     *   0 = Fix not available or invalid
     *   1 = Autonomous GPS fix
     *   2 = RTK float solution
     *   3 = RTK fix solution
     *   4 = Differential, code phase only solution (DGPS)
     *   5 = SBAS solution – WAAS/EGNOS/MSAS
     *   6 = RTK float or RTK location 3D Network solution
     *   7 = RTK fixed 3D Network solution
     *   8 = RTK float or RTK location 2D in a Network solution
     *   9 = RTK fixed 2D Network solution
     *   10 = OmniSTAR HP/XP solution
     *   11 = OmniSTAR VBS solution
     *   12 = Location RTK solution
     *   13 = Beacon DGPS
     * </code>
     * @return the fix quality
     */
    public int getFixQuality()
    {
        return ((WrapperInt) data[4]).getValue();
    }

    public boolean isActive()
    {
        return ((WrapperInt) data[4]).getValue() != 0;
    }

    /**
     * Returns the number of satellites being tracked
     * @return the number of satellites
     */
    public int getNumberOfSatellites()
    {
        return ((WrapperInt) data[5]).getValue();
    }

    /**
     * Returns the horizontal dilution of precision
     * @return the horizontal dilution
     */
    public double getDilution()
    {
        return ((WrapperDouble) data[6]).getValue();
    }

    /**
     * Returns the altitude, in meters
     * @return the altitude
     */
    public double getAltitude()
    {
        return ((WrapperDouble) data[7]).getValue();
    }

}
