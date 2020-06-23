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
import java.util.TimeZone;

/**
 * An object to store a time value with no date
 * <p>
 * This object is compatible with java.util.Date, and thus can adapt to
 * different time zones and locales in the same ways. When initialized,
 * the date for this object is set to January 1, 1970. As such, only
 * the hour, minute, second, and millisecond fields will contain useful
 * information. 
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class Time extends Date
{
    private static final long serialVersionUID = 7306671905188253505L;

    /**
     * Constructs an object containing the time from a string of the form
     * <code>HHMMSS[.sss]</code>, in the GMT time zone and English locale
     * @param s the string to parse
     */
    public Time(String s)
    {
        super();
        double d = Double.parseDouble(s);
        int i = (int) d;
        Calendar c = new GregorianCalendar(new SimpleTimeZone(0, "GMT"),
                Locale.ENGLISH);
        c.setTime(new Date(0));
        c.set(Calendar.HOUR_OF_DAY, i / 10000);
        c.set(Calendar.MINUTE, (i % 10000) / 100);
        c.set(Calendar.SECOND, i % 100);
        c.set(Calendar.MILLISECOND, (int) ((d - i) * 1000));
        setTime(c.getTimeInMillis());
    }

    /**
     * Constructs an object containing the time from a string of the form
     * <code>HHMMSS[.sss]</code>, with a specific time zone and locale
     * @param s the string to parse
     * @param t the timezone to use
     * @param l the locale to use
     */
    public Time(String s, TimeZone t, Locale l)
    {
        super();
        double d = Double.parseDouble(s);
        int i = (int) d;
        Calendar c = new GregorianCalendar(t, l);
        c.setTime(new Date(0));
        c.set(Calendar.HOUR_OF_DAY, i / 10000);
        c.set(Calendar.MINUTE, (i % 10000) / 100);
        c.set(Calendar.SECOND, i % 100);
        c.set(Calendar.MILLISECOND, (int) ((d - i) * 1000));
        setTime(c.getTimeInMillis());
    }

    /**
     * Returns a string representation of the time, of the form
     * <code>hh:mm:ss zzz</code>
     * @return a string representation of the time
     * @see java.util.Date#toString() 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        String[] s = super.toString().split(" ");
        return s[3] + ' ' + s[4];
    }
}
