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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An object for storing satellite information from a GSV packet
 * <p>
 * A GSV satellite is expected to have <code>4</code> parameters.
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class SatelliteGSV implements Iterable
{
    private WrapperInt[] data;

    /**
     * Constructs a new satellite data set
     * @param array Four parameters describing a satellite
     */
    public SatelliteGSV(WrapperInt[] array)
    {
        if (array.length != 4)
            throw new IllegalArgumentException("Wrong number of parameters: "
                    + array.length);
        data = array;
    }

    /**
     * Returns the PRN number of the satellite
     * @return the PRN number
     */
    public int getPRN()
    {
        return data[0].getValue();
    }

    /**
     * Returns the elevation of the satellite
     * @return the elevation, in degrees
     */
    public int getElevation()
    {
        return data[1].getValue();
    }

    /**
     * Returns the azimuth of the satellite
     * @return the azimuth, in degrees
     */
    public int getAzimuth()
    {
        return data[2].getValue();
    }

    /**
     * Returns the signal-to-noise ratio of the satellite signal
     * @return the signal-to-noise ratio, in decibels
     */
    public int getSNR()
    {
        return data[3].getValue();
    }

    /**
     * Returns an array of all of the satellite data
     * @return all of the satellite data
     */
    public int[] getData()
    {
        int[] array = new int[4];
        for (int i = 0; i < 4; i++)
            array[i] = data[i].getValue();
        return array;
    }

    /**
     * Returns an iterator over the data of the satellite
     * @return an iterator over the data of the satellite
     * @see java.lang.Iterable#iterator()
     */
    public Iterator iterator()
    {
        return new GSVIterator(this.getData());
    }

    /**
     * Returns a string representation of the satellite
     * <p>
     * This is a sample of the string returned by this method:
     * "[ PRN: <code>xx</code>, Elevation: <code>xx</code>\u00B0,
     * Azimuth: <code>xx</code>\u00B0, SNR: <code>xx</code>dB ]"
     * @return a string representation of the satellite
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        return "[ PRN: " + data[0] + ", Elevation: " + data[1]
                + "\u00B0, Azimuth: " + data[2] + "\u00B0, SNR: " + data[3]
                + "dB ]";
    }

    private class GSVIterator implements Iterator
    {
        private int index;
        private int[] data;

        public GSVIterator(int[] array)
        {
            index = 0;
            data = array;
        }

        public boolean hasNext()
        {
            return true;
        }

        public Object next()
        {
            index++;
            if (index >= data.length)
                throw new NoSuchElementException();
            return new Integer(data[index]);
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
