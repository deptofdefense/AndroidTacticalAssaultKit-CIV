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

/**
 * An object for storing an NMEA 0183 GLL (Geographic Latitude and Longitude) packet
 * <p>
 * GLL packets are expected to start with <code>$GPGLL</code> or <code>$LCGLL</code>,
 * and are expected to have <code>7</code> parameters.
 * <p>
 * Checksums should be tested and removed from sentences before being passed to this class.
 * Malformed packets may throw exceptions or exhibit undefined behavior.
 * 
 *
 * https://www.gpsinformation.org/dale/nmea.htm#GLL
 * 
 * GLL - Geographic Latitude and Longitude is a holdover from Loran data and some old units 
 * may not send the time and data active information if they are emulating Loran data. If a 
 * gps is emulating Loran data they may use the LC Loran prefix instead of GP.
 * 
 *  $GPGLL,4916.45,N,12311.12,W,225444,A,*1D
 * Where:
 *   GLL          Geographic position, Latitude and Longitude
 *   4916.46,N    Latitude 49 deg. 16.45 min. North
 *   12311.12,W   Longitude 123 deg. 11.12 min. West
 *   225444       Fix taken at 22:54:44 UTC
 *   A            Data Active or V (void)
 *   *iD          checksum data

 * Note that, as of the 2.3 release of NMEA, there is a new field in the GLL sentence at the 
 * end just prior to the checksum. For more information on this field see here. 
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class PacketGLL extends Packet implements ContainsPosition, ContainsTime
{
    /**
     * Constructs a GLL packet
     * @param s Array of string parameters
     */
    public PacketGLL(String[] s)
    {
        super(3);
        if (!s[0].equals("$GPGLL") && !s[0].equals("$LCGLL"))
            throw new IllegalArgumentException("Cannot make GLL packet from "
                    + s[0]);
        if (s.length == 8)
        {
            data[0] = new Geocoordinate(s[1], s[2], s[3], s[4]);
            data[1] = new Time(s[5]);
            data[2] = new WrapperBoolean(s[6].equals("A"), "Active");
        }
        else
            throw new IllegalArgumentException("Wrong number of parameters: "
                    + s.length);
    }

    /**
     * Returns the geographic position represented by this packet
     * @return a geographic position 
     */
    public Geocoordinate getPosition()
    {
        return (Geocoordinate) data[0];
    }

    /**
     * Returns the time represented by this packet
     * @return the time
     */
    public Time getTime()
    {
        return (Time) data[1];
    }

    /**
     * Determines whether the data is active or void
     * @return true if data is active, false if data is void
     */
    public boolean isActive()
    {
        return ((WrapperBoolean) data[2]).getValue();
    }
}
