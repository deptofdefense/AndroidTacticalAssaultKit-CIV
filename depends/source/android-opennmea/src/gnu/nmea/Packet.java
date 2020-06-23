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
 * An abstract object for storing an NMEA 0183 packet
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public abstract class Packet implements Iterable
{
    /**
     * Storage space for the data within a packet
     */
    protected Object[] data;

    /**
     * Constructs a packet to store data
     * @param size The number of data elements to store
     */
    public Packet(int size)
    {
        if (size < 0)
            throw new IllegalArgumentException();
        data = new Object[size];
    }

    /**
     * Returns the element at a given index in a packet
     * @param index the index of the element requested
     * @return the element requested
     */
    public Object get(int index)
    {
        if ((index < 0) || (index >= data.length))
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: "
                    + data.length);
        return data[index];
    }

    /**
     * Returns the size of the packet
     * @return the size of the packet
     */
    public int size()
    {
        return data.length;
    }

    /**
     * Returns an iterator over the set of data elements in the packet
     * @return an iterator over the set of data elements in the packet
     * @see java.lang.Iterable#iterator()
     */
    public Iterator iterator()
    {
        return new PacketIterator(data);
    }

    /**
     * Returns a string representation of the packet
     * @return a string representation of the packet
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        String s = new String();
        for (int i = 0; i < data.length; i++)
        {
            if (data[i] != null)
            {
                if (i != 0)
                    s += '\n';
                s += data[i].toString();
            }
        }
        return s;
    }

    private class PacketIterator implements Iterator
    {
        private int index;
        private Object[] data;

        public PacketIterator(Object[] array)
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
            return data[index];
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
