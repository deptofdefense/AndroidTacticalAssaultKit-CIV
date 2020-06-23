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
 * An object for storing and comparing geographic latitude and longitude
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
public class Geocoordinate implements Comparable<Geocoordinate>
{
    private int latdeg, londeg, latmin, lonmin;
    private double latsec, lonsec, lattot, lontot;
    private char mode;
    private boolean valid = true;

    /**
     * Constructs a Geocoordinate from latitude and longitude strings, with cardinal direction
     * indicated by separate strings
     * <p>
     * This constructor defaults to the decimal minutes output mode.
     * <p>
     * If the strings passed to this constructor are malformed, the values will default to
     * <code>{0.0, 0.0}</code>. 
     * @param lat Latitude, in the form DDmm[.mmmm]
     * @param ns North/South ("N" or "S")
     * @param lon Longitude, in the form DDDmm[.mmmm]
     * @param ew East/West ("E" or "W")
     */
    public Geocoordinate(String lat, String ns, String lon, String ew)
    {
        this(lat.equals("") ? 0.0
                : (Integer.parseInt(lat.substring(0, 2)) + Double
                        .parseDouble(lat.substring(2)) / 60)
                        * (ns.equals("N") ? 1 : -1), lon.equals("") ? 0.0
                : (Integer.parseInt(lon.substring(0, 3)) + Double
                        .parseDouble(lon.substring(3)) / 60)
                        * (ew.equals("E") ? 1 : -1));
        if ((lat.equals("")) || (lon.equals("")))
            valid = false;
        mode = 'M';
    }

    /**
     * Constructs a Geocoordinate from floating-point latitude and longitude values, with
     * cardinal direction indicated by numerical sign
     * <p>
     * This constructor defaults to the decimal degrees output mode.
     * @param lat Latitude, in decimal degrees (south is negative)
     * @param lon Longitude, in decimal degrees (west is negative)
     */
    public Geocoordinate(double lat, double lon)
    {
        while (lat < -90)
            lat += 180;
        while (lat > 90)
            lat -= 180;
        while (lon <= -180)
            lat += 360;
        while (lon > 180)
            lat -= 360;
        lattot = lat;
        lontot = lon;
        latdeg = (int) lat;
        londeg = (int) lon;
        latmin = (int) (60 * (lat - latdeg));
        lonmin = (int) (60 * (lon - londeg));
        latsec = 60 * (60 * (lat - latdeg) - latmin);
        lonsec = 60 * (60 * (lon - londeg) - lonmin);
        mode = 'D';
    }

    /**
     * Sets the output mode to decimal degrees
     */
    public void toDegrees()
    {
        mode = 'D';
    }

    /**
     * Sets the output mode to degrees and decimal minutes
     */
    public void toMinutes()
    {
        mode = 'M';
    }

    /**
     * Sets the output mode to degrees, minutes, and decimal seconds
     */
    public void toSeconds()
    {
        mode = 'S';
    }

    /**
     * Tells where the current mode applies the fractional part of the latitude
     * and longitude values
     * <p>
     * Possible modes are degrees (<code>'D'</code>), minutes (<code>'M'</code>),
     * or seconds (<code>'S'</code>).
     * @return the current mode
     */
    public char getMode()
    {
        return mode;
    }

    /**
     * Returns the latitude, formatted according to the current mode
     * <p>
     * If the latitude stored by this Geocoordinate was 12.3456, then
     * the array returned for each mode would be:<br>
     * Degrees mode: <code>[12.3456]</code><br>
     * Minutes mode: <code>[12.0, 20.736]</code><br>
     * Seconds mode: <code>[12.0, 20.0, 44.16]</code>
     * @return the latitude
     * @see Geocoordinate#getLatitudeDegrees()
     */
    public double[] getLatitude()
    {
        double[] array;
        if (mode == 'D')
        {
            array = new double[1];
            array[0] = lattot;
        }
        else if (mode == 'M')
        {
            array = new double[2];
            array[0] = (double) latdeg;
            array[1] = latmin + latsec / 60;
        }
        else
        {
            array = new double[3];
            array[0] = (double) latdeg;
            array[1] = (double) latmin;
            array[2] = latsec;
        }
        return array;
    }

    /**
     * Returns the latitude in decimal degrees
     * <p>
     * This is equivalent to <code>getLatitude()[0]</code> in Degrees mode.
     * @return the latitude
     * @see Geocoordinate#getLatitude()
     */
    public double getLatitudeDegrees()
    {
        return lattot;
    }

    /**
     * Returns the longitude, formatted according to the current mode
     * <p>
     * If the longitude stored by this Geocoordinate was 12.3456, then
     * the array returned for each mode would be:<br>
     * Degrees mode: <code>[12.3456]</code><br>
     * Minutes mode: <code>[12.0, 20.736]</code><br>
     * Seconds mode: <code>[12.0, 20.0, 44.16]</code>
     * @return the longitude
     * @see Geocoordinate#getLongitudeDegrees()
     */
    public double[] getLongitude()
    {
        double[] array;
        if (mode == 'D')
        {
            array = new double[1];
            array[0] = lontot;
        }
        else if (mode == 'M')
        {
            array = new double[2];
            array[0] = (double) londeg;
            array[1] = lonmin + lonsec / 60;
        }
        else
        {
            array = new double[3];
            array[0] = (double) londeg;
            array[1] = (double) lonmin;
            array[2] = lonsec;
        }
        return array;
    }

    /**
     * Returns the longitude in decimal degrees
     * <p>
     * This is equivalent to <code>getLongitude()[0]</code> in Degrees mode.
     * @return the longitude
     * @see Geocoordinate#getLongitude()
     */
    public double getLongitudeDegrees()
    {
        return lontot;
    }

    /**
     * Determines if a coordinate is valid
     * <p>
     * An invalid coordinate could be generated by the constructor if
     * passed invalid strings. This method is helpful in filtering out
     * such coordinates.
     * @return true if the coordinate is valid, false otherwise
     */
    public boolean isValid()
    {
        return valid;
    }

    /**
     * Returns the distance between two Geocoordinates on Earth, in meters
     * <p>
     * In order to fulfill the contract of <code>Comparable</code>, this method returns
     * a negative distance if <code>this</code> is farther south than <code>g</code>. If
     * <code>this</code> and <code>g</code> are at the same latitude, then this method
     * returns a negative distance if <code>this</code> is farther west than <code>g</code>.
     * @return the distance between two Geocoordinates
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Geocoordinate g)
    {
        return (int) (Math.toDegrees(Math.acos(Math.sin(Math.toRadians(lattot))
                * Math.sin(Math.toRadians(g.getLatitudeDegrees()))
                + Math.cos(Math.toRadians(lattot))
                * Math.cos(Math.toRadians(g.getLatitudeDegrees()))
                * Math.cos(Math.toRadians(lontot - g.getLongitudeDegrees())))) * 111189.577)
                * (lattot == g.getLatitudeDegrees() ? (lontot > g
                        .getLongitudeDegrees() ? 1 : -1) : (lattot > g
                        .getLatitudeDegrees() ? 1 : -1));
    }

    /**
     * Determines if this Geocoordinate is equal to another object
     * <p>
     * A comparison only makes sense if the object being compared is also a Geocoordinate,
     * so this method returns false if the parameter is not an instance of Geocoordinate.
     * @return true if the objects are equal, false otherwise
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Geocoordinate))
            return false;
        return ((lattot == ((Geocoordinate) o).getLatitudeDegrees()) && (lontot == ((Geocoordinate) o)
                .getLongitudeDegrees()));
    }

    /**
     * Returns a string representation of the coordinate
     * @return a string representation of the coordinate
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        if (mode == 'D')
            return (latdeg + latmin / 60) + "\u00B0" + ' '
                    + (londeg + lonmin / 60) + '\u00B0';
        else if (mode == 'M')
            return latdeg + "\u00B0" + Math.abs(latmin) + '\'' + ' ' + londeg
                    + '\u00B0' + Math.abs(lonmin) + '\'';
        else
            return latdeg + "\u00B0" + Math.abs(latmin) + '\''
                    + Math.abs(latsec) + '\"' + ' ' + londeg + '\u00B0'
                    + Math.abs(lonmin) + '\'' + Math.abs(lonsec) + '\"';
    }
}
