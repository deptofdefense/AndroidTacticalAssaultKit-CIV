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

import java.util.Locale;

/**
 * A class with static methods for pre-processing data sentences
 * and automatically constructing packets
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class SentenceHandler
{
    private static String[] prefix = {
            "$GPGGA", "$GPGSA", "$GPGSV", "$GPRMC", "$GPGLL", "$LCGLL",
            "$GPVTG", "$LCVTG", "$GPGST", "$PTNL"
    };

    /**
     * Splits a data sentence into its individual parameters, a required step
     * before invoking a Packet constructor
     * @param s the sentence to split
     * @param check whether to checksum the sentence before splitting
     * @return the split sentence
     */
    public static String[] split(String s, boolean check)
    {
        if (check)
        {
            if (!checksum(s))
                throw new OpenNMEAException("Invalid or missing checksum");
            trim(s);
        }
        s += '*';
        String[] a = s.split(",");
        a[a.length - 1] = a[a.length - 1].substring(0,
                a[a.length - 1].length() - 1);
        return a;
    }

    /**
     * Creates a Packet object from a data sentence
     * @param s the sentence to use in constructing the packet
     * @param check whether to checksum the sentence before processing
     * @return a packet containing the data from the sentence 
     */


    public static Packet makePacket(String s, boolean check)
    {
        if (s != null)  { 

            // accommodate various NMEA-0183 prefixes (Talker IDs) where one of the most well
            // know is GP (Global Positioning System (GPS)) GL (GLONASS Receiver) or
            // GN (Global Navigation Satellite System (GNSS)).
            // The talker id is the first 2 characters after the $
            if (s.length() > 6 &&
                    s.startsWith("$")) { 
               if (s.regionMatches(3,"GGA", 0, 3)) {
                     s = "$GP" + s.substring(3);
               } else if (s.regionMatches(3,"RMC", 0, 3)) {
                 s = "$GP" + s.substring(3);
               }
            }
        }
        return makePacketImpl(s, check);

    }

    private static Packet makePacketImpl(String s, boolean check)
    {
        String a[] = split(s, check);
        switch (indexOf(prefix, a[0]))
        {
            case 0:
                return new PacketGGA(a);
            case 1:
                return new PacketGSA(a);
            case 2:
                return new PacketGSV(a);
            case 3:
                return new PacketRMC(a);
            case 4:
            case 5:
                return new PacketGLL(a);
            case 6:
            case 7:
                return new PacketVTG(a);
            case 8:
                return new PacketGST(a);
            case 9:
                return new PacketPTNL(a);
            default:
                throw new OpenNMEAException("Invalid sentence prefix: " + a[0]);
        }
    }

    private static int indexOf(String[] a, String s)
    {
        for (int i = 0; i < a.length; i++)
            if (s.equals(a[i]))
                return i;
        return -1;
    }

    /**
     * Tests the integrity of a data sentence using its checksum
     * @param s the sentence to check
     * @return true if the sentence checksums correctly,
     *         false if the checksum fails or is not present
     */
    public static boolean checksum(String s)
    {
        if ((s.length() < 3) || (s.charAt(0) != '$')
                || (s.charAt(s.length() - 3) != '*'))
            return false;
        String x = new String();
        x += s.charAt(s.length() - 2);
        x += s.charAt(s.length() - 1);
        s = s.substring(1, s.length() - 3);
        char c = s.charAt(0);
        for (int i = 1; i < s.length(); i++)
            c ^= s.charAt(i);
        if (x.charAt(0) == '0')
            x = x.substring(1, 2);
        return Integer.toHexString(c).equals(x.toLowerCase(Locale.US));
    }

    /**
     * Trims the checksum from the end of a data sentence, if present
     * @param s the sentence to trim
     * @return the trimmed sentence, which may be identical to the input
     *         if the original had no checksum
     */
    public static String trim(String s)
    {
        if ((s.length() < 3) || (s.charAt(s.length() - 3) != '*'))
            return s;
        return s.substring(0, s.length() - 3);
    }
}
