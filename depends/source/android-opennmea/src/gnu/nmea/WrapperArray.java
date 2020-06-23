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

import java.util.Arrays;

/**
 * A wrapper object for arrays of <code>Wrapper</code> objects
 * <p>
 * It is important to note that while this class wraps <code>Wrapper</code>
 * objects, it does NOT implement the <code>Wrapper</code> interface.
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class WrapperArray
{
    private Wrapper[] value;
    private String description;

    /**
     * Constructs a <code>Wrapper[]</code> wrapper with a blank description string
     * @param w the array to wrap
     */
    public WrapperArray(Wrapper[] w)
    {
        value = w;
        description = "";
    }

    /**
     * Constructs an <code>int</code> wrapper with a blank description string
     * @param w the array to wrap
     * @param s the string describing the contents of the wrapper
     */
    public WrapperArray(Wrapper[] w, String s)
    {
        value = w;
        description = s;
    }

    /**
     * Returns the array of data contained in this object
     * @return the array of data contained in this object
     */
    public Wrapper[] getValue()
    {
        return value;
    }

    /**
     * Returns a string representation of this wrapper
     * <p>
     * If the wrapper has a description string, this method returns
     * "<code>description</code>: <code>value</code>". Otherwise, this
     * method returns "<code>value</code>". <code>value</code> is
     * formatted by {@link java.util.Arrays#toString(Object[])}.
     * @see java.util.Arrays#toString(Object[])
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return description + (description.equals("") ? "" : ": ")
                + Arrays.toString(value);
    }
}
