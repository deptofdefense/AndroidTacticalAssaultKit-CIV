
package com.atakmap.math;

public final class MathUtils {
    
    private final static double LOG2 = Math.log(2);

    private MathUtils() {
    }

    public static double min(double a, double b, double c) {
        double retval = a;
        if (b < retval)
            retval = b;
        if (c < retval)
            retval = c;
        return retval;
    }

    public static double min(double a, double b, double c, double d) {
        double retval = a;
        if (b < retval)
            retval = b;
        if (c < retval)
            retval = c;
        if (d < retval)
            retval = d;
        return retval;
    }

    public static int min(int[] vals) {
        int retval = vals[0];
        for (int i = 1; i < vals.length; i++)
            if (vals[i] < retval)
                retval = vals[i];
        return retval;
    }
    
    public static double min(double[] vals) {
        double retval = vals[0];
        for (int i = 1; i < vals.length; i++)
            if (vals[i] < retval)
                retval = vals[i];
        return retval;
    }

    public static float min(float[] vals) {
        float retval = vals[0];
        for (int i = 1; i < vals.length; i++)
            if (vals[i] < retval)
                retval = vals[i];
        return retval;
    }

    public static double max(double a, double b, double c) {
        double retval = a;
        if (b > retval)
            retval = b;
        if (c > retval)
            retval = c;
        return retval;
    }

    public static double max(double a, double b, double c, double d) {
        double retval = a;
        if (b > retval)
            retval = b;
        if (c > retval)
            retval = c;
        if (d > retval)
            retval = d;
        return retval;
    }

    public static double max(double[] vals) {
        double retval = vals[0];
        for (int i = 1; i < vals.length; i++)
            if (vals[i] > retval)
                retval = vals[i];
        return retval;
    }

    public static float max(float[] vals) {
        float retval = vals[0];
        for (int i = 1; i < vals.length; i++)
            if (vals[i] > retval)
                retval = vals[i];
        return retval;
    }

    public static boolean isPowerOf2(int i) {
        if (i == 0)
            return false;
        return ((i&(i-1))==0);
    }
    
    public static int nextPowerOf2(int i) {
        return (int)(Math.log(i) / LOG2) + 1;
    }

    /**
     * Returns the power of the input value, as an integer, using the specified rounding.
     * @param v     A value
     * @param round Less then zero, round down, equal to zero round nearest,
     *              greater than zero, round up
     * @return  The power of two for the input value, per the specified rounding
     */
    public static int powerOf2(int v, int round) {
        final double p2 = (Math.log(v)/LOG2);
        if(round < 0)
            return (int)p2;
        else if(round == 0)
            return (int)(p2+0.5d);
        else
            return (int)Math.ceil(p2);
    }

    public static double clamp(double v, double min, double max) {
        if (v < min)
            return min;
        else if (v > max)
            return max;
        return v;
    }

    public static float clamp(float v, float min, float max) {
        if (v < min)
            return min;
        else if (v > max)
            return max;
        return v;
    }

    public static int clamp(int v, int min, int max) {
        if (v < min)
            return min;
        else if (v > max)
            return max;
        return v;
    }
    
    public static long clamp(long v, long min, long max) {
        if (v < min)
            return min;
        else if (v > max)
            return max;
        return v;
    }
    
    public static boolean hasBits(int value, int mask) {
        return ((value&mask)==mask);
    }
    
    public static boolean hasBits(long value, long mask) {
        return ((value&mask)==mask);
    }
    
    public static double distance(double x0, double y0, double x1, double y1) {
        return Math.sqrt((x1-x0)*(x1-x0) + (y1-y0)*(y1-y0));
    }
    
    public static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        final double dx = (x1-x0);
        final double dy = (y1-y0);
        final double dz = (z1-z0);
        
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    /**
     * Checks for equality. Will return <code>true</code> if both values are
     * {@link Double#NaN}
     * @param a
     * @param b
     * @return
     */
    public static boolean equals(double a, double b) {
        final boolean anan = (a!=a);
        final boolean bnan = (b!=b);

        return (anan && bnan) || (a==b);
    }
    
    /**************************************************************************/
    
    // derived from http://www.geeksforgeeks.org/check-if-two-given-line-segments-intersect/
    public static boolean intersects(double Ax0, double Ay0, double Ax1, double Ay1, double Bx0, double By0, double Bx1, double By1) {
        double p1x = Ax0;
        double p1y = Ay0;
        double q1x = Ax1;
        double q1y = Ay1;
        double p2x = Bx0;
        double p2y = By0;
        double q2x = Bx1;
        double q2y = By1;
        
        // Find the four orientations needed for general and
        // special cases
        int o1 = orientation(p1x, p1y, q1x, q1y, p2x, p2y);
        int o2 = orientation(p1x, p1y, q1x, q1y, q2x, q2y);
        int o3 = orientation(p2x, p2y, q2x, q2y, p1x, p1y);
        int o4 = orientation(p2x, p2y, q2x, q2y, q1x, q1y);
     
        // General case
        if (o1 != o2 && o3 != o4)
            return true;
     
        // Special Cases
        // p1, q1 and p2 are colinear and p2 lies on segment p1q1
        if (o1 == 0 && onSegment(p1x, p1y, p2x, p2y, q1x, q1y)) return true;
     
        // p1, q1 and p2 are colinear and q2 lies on segment p1q1
        if (o2 == 0 && onSegment(p1x, p1y, q2x, q2y, q1x, q1y)) return true;
     
        // p2, q2 and p1 are colinear and p1 lies on segment p2q2
        if (o3 == 0 && onSegment(p2x, p2y, p1x, p1y, q2x, q2y)) return true;
     
        // p2, q2 and q1 are colinear and q1 lies on segment p2q2
        if (o4 == 0 && onSegment(p2x, p2y, q1x, q1y, q2x, q2y)) return true;
     
        return false; // Doesn't fall in any of the above cases
    }
    
    // Given three colinear points p, q, r, the function checks if
    // point q lies on line segment 'pr'
    private static boolean onSegment(double Px, double Py, double Qx, double Qy, double Rx, double Ry) {
        return (Qx <= Math.max(Px, Rx) && Qx >= Math.min(Px, Rx) &&
            Qy <= Math.max(Py, Ry) && Qy >= Math.min(Py, Ry));
    }
  
    // To find orientation of ordered triplet (p, q, r).
    // The function returns following values
    // 0 --> p, q and r are colinear
    // 1 --> Clockwise
    // 2 --> Counterclockwise
    private static int orientation(double Px, double Py, double Qx, double Qy, double Rx, double Ry) {
        // See http://www.geeksforgeeks.org/orientation-3-ordered-points/
        // for details of below formula.
        double val = (Qy - Py) * (Rx - Qx) -
                     (Qx - Px) * (Ry - Qy);
     
        if (val == 0) return 0;  // colinear
     
        return (val > 0)? 1: 2; // clock or counterclock wise
    }
    
    /**************************************************************************/
}
