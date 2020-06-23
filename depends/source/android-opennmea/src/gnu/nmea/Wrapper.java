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
 * An interface for objects that wrap primitive data types
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public interface Wrapper extends Comparable<Wrapper>
{
    /**
     * Returns the boolean value of the wrapped type
     * @return the value as a boolean
     */
    public boolean booleanValue();

    /**
     * Returns the integer value of the wrapped type
     * @return the value as an int
     */
    public int intValue();

    /**
     * Returns the floating-point value of the wrapped type
     * @return the value as a double
     */
    public double doubleValue();

    /**
     * Determines if the wrapped type contains a number
     * @return true is the wrapper contains a number, false if it contains NaN
     */
    public boolean isValid();
}
