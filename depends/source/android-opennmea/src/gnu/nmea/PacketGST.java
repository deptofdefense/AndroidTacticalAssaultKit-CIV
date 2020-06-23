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
 *    Standard for the GST String from Trimble
 * https://www.trimble.com/OEM_ReceiverHelp/V4.44/en/NMEA-0183messages_GST.html
 *    ----------------------------------------
 * 0     Message ID $GPGST
 * 1     UTC of position fix
 * 2     RMS value of the pseudorange residuals; includes carrier
 *          phase residuals during periods of RTK (float) and RTK
 *          (fixed) processing
 * 3     Error ellipse semi-major axis 1 sigma error, in meters
 * 4     Error ellipse semi-minor axis 1 sigma error, in meters
 * 5     Error ellipse orientation, degrees from true north
 * 6     Latitude 1 sigma error, in meters
 * 7     Longitude 1 sigma error, in meters
 * 8     Height 1 sigma error, in meters
 * 9     The checksum data, always begins with *
 * 
 * GST packets are expected to start with <code>$GPGST</code>,
 * and are expected to have <code>9</code> parameters.
 * <p>
 * Checksums should be tested and removed from sentences before being passed to this class.
 * Malformed packets may throw exceptions or exhibit undefined behavior.
 * 
 * @author Shawn Bisgrove
 * @version 1.00, 01/04/18
 * @since OpenNMEA v0.1
 */
public class PacketGST extends Packet implements ContainsTime
{
    /**
     * Constructs a GST packet
     * @param s Array of string parameters
     */
    public PacketGST(String[] s)
    {
        super(8);
        if (!s[0].equals("$GPGST"))
            throw new IllegalArgumentException("Cannot make GST packet from "
                    + s[0]);
        if (s.length == 9)
        {
            data[0] = new Time(s[1]);
            data[1] = new WrapperDouble(s[2], "RMS Value");
            data[2] = new WrapperDouble(s[3], "Semi-Major Error 1-Sigma");
            data[3] = new WrapperDouble(s[4], "Semi-Minor Error 1-Sigma");
            data[4] = new WrapperDouble(s[5], "Ellipse Orientation");
            data[5] = new WrapperDouble(s[6], "Latitude Error 1-Sigma");
            data[6] = new WrapperDouble(s[7], "Longitude Error 1-Sigma");
            data[7] = new WrapperDouble(s[8], "Height Error 1-Sigma");
        }
        else
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
     * Returns the RMS value represented by this packet
     * @return the rms value
     */
    public double getRMS()
    {
        return ((WrapperDouble) data[1]).getValue();
    }

    /**
     * Returns the Semi Major 1-Sigma value represented by this packet
     * @return the semi major 1-sigma value
     */
    public double getSemiMajor1Sigma()
    {
        return ((WrapperDouble) data[2]).getValue();
    }

    /**
     * Returns the Semi Minor 1-Sigma value represented by this packet
     * @return the semiminor value
     */
    public double getSemiMinor1Sigma()
    {
        return ((WrapperDouble) data[3]).getValue();
    }

    /**
     * Returns the EllipseOrientation represented by this packet
     * @return the elipse orientation value
     */
    public double getEllipseOrientation()
    {
        return ((WrapperDouble) data[4]).getValue();
    }

    /**
     * Returns the Latitude 1-Sigma value represented by this packet
     * @return the latitude 1-sigma value
     */
    public double getLatitude1Sigma()
    {
        return ((WrapperDouble) data[5]).getValue();
    }

    /**
     * Returns the Longitude 1-Sigma value represented by this packet
     * @return the longitude value
     */
    public double getLongitude1Sigma()
    {
        return ((WrapperDouble) data[6]).getValue();
    }

    /**
     * Returns the Height 1-Sigma value represented by this packet
     * @return the height value
     */
    public double getHeight1Sigma()
    {
        return ((WrapperDouble) data[6]).getValue();
    }

}
