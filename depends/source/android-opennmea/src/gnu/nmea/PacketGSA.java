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
 * An object for storing an NMEA 0183 GSA (GPS Satellites Active) packet
 * <p>
 * GSA packets are expected to start with <code>$GPGSA</code>,
 * and are expected to have <code>18</code> parameters.
 * <p>
 * Checksums should be tested and removed from sentences before being passed to this class.
 * Malformed packets may throw exceptions or exhibit undefined behavior.
 * 
 * https://www.gpsinformation.org/dale/nmea.htm#GSA
 * 
 * GSA - GPS DOP and active satellites. This sentence provides details on the nature of 
 * the fix. It includes the numbers of the satellites being used in the current solution 
 * and the DOP. DOP (dilution of precision) is an indication of the effect of satellite 
 * geometry on the accuracy of the fix. It is a unitless number where smaller is better. 
 * For 3D fixes using 4 satellites a 1.0 would be considered to be a perfect number, 
 * however for overdetermined solutions it is possible to see numbers below 1.0.
 * There are differences in the way the PRN's are presented which can effect the ability 
 * of some programs to display this data. For example, in the example shown below there are 
 * 5 satellites in the solution and the null fields are scattered indicating that the almanac 
 * would show satellites in the null positions that are not being used as part of this solution. 
 * Other receivers might output all of the satellites used at the beginning of the sentence 
 * with the null field all stacked up at the end. This difference accounts for some satellite 
 * display programs not always being able to display the satellites being tracked. Some units 
 * may show all satellites that have ephemeris data without regard to their use as part of the 
 * solution but this is non-standard.
 * $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39
 * Where:
 *   GSA      Satellite status
 *   A        Auto selection of 2D or 3D fix (M = manual) 
 *   3        3D fix - values include: 1 = no fix
 *                                     2 = 2D fix
 *                                     3 = 3D fix
 *   04,05... PRNs of satellites used for fix (space for 12) 
 *   2.5      PDOP (dilution of precision) 
 *   1.3      Horizontal dilution of precision (HDOP) 
 *   2.1      Vertical dilution of precision (VDOP)
 *   *39      the checksum data, always begins with *
 *
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class PacketGSA extends Packet
{
    /**
     * Constructs a GSA packet
     * @param s Array of string parameters
     */
    public PacketGSA(String[] s)
    {
        super(6);
        if (!s[0].equals("$GPGSA"))
            throw new IllegalArgumentException("Cannot make GSA packet from "
                    + s[0]);
        if (s.length == 18)
        {
            if (s[1].length() != 1)
                throw new IllegalArgumentException();
            data[0] = new WrapperBoolean(s[1].equals("A"), "Auto selection");
            data[1] = new WrapperInt(s[2]);
            WrapperInt[] array = new WrapperInt[12];
            for (int i = 0; i < 12; i++)
                array[i] = new WrapperInt(s[i + 3]);
            data[2] = new WrapperArray(array, "PRNs of satellites");
            for (int i = 3; i < 6; i++)
                data[i] = new WrapperDouble(s[i + 12], "PHV".charAt(i - 3)
                        + "DOP");
        }
        else
            throw new IllegalArgumentException("Wrong number of parameters: "
                    + s.length);
    }

    /**
     * Determines if 2D/3D selection is automatic or manual
     * @return true if automatic, false if manual
     */
    public boolean isAutomatic()
    {
        return ((WrapperBoolean) data[0]).getValue();
    }

    /**
     * Returns the satellite fix type
     * <p>
     * Fix type can take the following values:<br>
     * <code>
     * 1 = No fix<br>
     * 2 = 2D fix<br>
     * 3 = 3D fix
     * </code>
     * @return the fix type
     */
    public int getFixType()
    {
        return ((WrapperInt) data[1]).getValue();
    }

    /**
     * Returns the PRNs for satellites used for fix
     * <p>
     * Note that empty PRN positions are set to <code>-1</code>.
     * @return an array of PRNs
     */
    public int[] getPRN()
    {
        int[] array = new int[12];
        for (int i = 0; i < 12; i++)
            array[i] = ((WrapperArray) data[2]).getValue()[i].isValid() ? ((WrapperArray) data[2])
                    .getValue()[i].intValue()
                    : -1;
        return array;
    }

    /**
     * Returns the position dilution of precision (smaller is better)
     * @return the position dilution of precision
     */
    public double getPDOP()
    {
        return ((WrapperDouble) data[3]).getValue();
    }

    /**
     * Returns the horizontal dilution of precision (smaller is better)
     * @return the horizontal dilution of precision
     */
    public double getHDOP()
    {
        return ((WrapperDouble) data[4]).getValue();
    }

    /**
     * Returns the vertical dilution of precision (smaller is better)
     * @return the vertical dilution of precision
     */
    public double getVDOP()
    {
        return ((WrapperDouble) data[5]).getValue();
    }
}
