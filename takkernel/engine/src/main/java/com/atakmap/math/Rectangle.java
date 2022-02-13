package com.atakmap.math;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class Rectangle {

    public double X;
    public double Y;
    public double Width;
    public double Height;

    public Rectangle() {
        this(0, 0, 0, 0);
    }

    public Rectangle(double x, double y, double w, double h) {
        X = x;
        Y = y;
        Width = w;
        Height = h;
    }

    public boolean contains(double x, double y) {
        return contains(X, Y, X + Width, Y + Height, x, y);
    }

    @Override
    public String toString() {
        return X + ", " + Y + ", " + Width + ", " + Height;
    }

    /**
     * Tests two rectangles, <code>a</code> and <code>b</code> for intersection.
     * 
     * @param aX1   The minimum x-coordinate for <code>a</code>
     * @param aY1   The minimum y-coordinate for <code>a</code>
     * @param aX2   The maximum x-coordinate for <code>a</code>
     * @param aY2   The maximum y-coordinate for <code>a</code>
     * @param bX1   The minimum x-coordinate for <code>b</code>
     * @param bY1   The minimum y-coordinate for <code>b</code>
     * @param bX2   The maximum x-coordinate for <code>b</code>
     * @param bY2   The maximum y-coordinate for <code>b</code>
     * 
     * @return  <code>true</code> if the rectangles intersect,
     *          <code>false</code> otherwise.
     */
    public static boolean intersects(double aX1, double aY1, double aX2, double aY2, double bX1, double bY1, double bX2, double bY2) {
        return intersects(aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2, true);
    }
    public static boolean intersects(double aX1, double aY1, double aX2, double aY2, double bX1, double bY1, double bX2, double bY2, boolean edgeIsect) {
        final boolean strictIsect = aX1 < bX2 &&
                                    aY1 < bY2 &&
                                    aX2 > bX1 &&
                                    aY2 > bY1;
        if(strictIsect || !edgeIsect)
            return strictIsect;

        return (aX1 == bX2) || (aY1 == bY2) || (aX2 == bX1) || (aY2 == bY1);
    }
    
    public static boolean contains(double rX1, double rY1, double rX2, double rY2, double x, double y) {
        return ((x >= rX1) && (x <= rX2)) && ((y >= rY1) &&(y <= rY2));
    }
    
    /**
     * Returns <code>true</code> if rectangle <I>a</I> contains rectangle
     * <I>b</I>.
     * 
     * @param aX1
     * @param aY1
     * @param aX2
     * @param aY2
     * @param bX1
     * @param bY1
     * @param bX2
     * @param bY2
     * @return
     */
    public static boolean contains(double aX1, double aY1, double aX2, double aY2, double bX1, double bY1, double bX2, double bY2) {
        return aX1 <= bX1 && aY1 <= bY1 && aX2 >= bX2 && aY2 >= bY2;
    }
    
    /**
     * Subtracts rectangle <I>b</I> from rectangle <I>a</I>. Any remainder
     * rectangles are inclusive to <I>a</I>.
     * 
     * @param aX1
     * @param aY1
     * @param aX2
     * @param aY2
     * @param bX1
     * @param bY1
     * @param bX2
     * @param bY2
     * 
     * @return  The number of remainder rectangles resulting from the
     *          subtraction. At most, <code>4</code> <code>Rectangle</code>
     *          instances will be returned. Note that in the case of no
     *          intersection, a value of <code>1</code> will be returned and
     *          the remainder will be equal to <I>a</I>.
     */
    public static int subtract(double aX1, double aY1, double aX2, double aY2, double bX1, double bY1, double bX2, double bY2, Rectangle[] remainder) {
        if(contains(bX1, bY1, bX2, bY2, aX1, aY1, aX2, aY2))
            return 0;
        if(!intersects(aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2)) {
            remainder[0] = new Rectangle(aX1, aY1, aX2, aY2);
            return 1;
        }
        
        // compute the intersection
        double isectX1 = Math.max(aX1, bX1);
        double isectY1 = Math.max(aY1, bY1);
        double isectX2 = Math.min(aX2, bX2);
        double isectY2 = Math.min(aY2, bY2);
        
        int remainders = 0;
        
        // compute top remainder
        final double topX1 = aX1;
        final double topY1 = aY1;
        final double topX2 = aX2;
        final double topY2 = isectY1;
        if(topX2>topX1 && topY2>topY1)
            remainder[remainders++] = new Rectangle(topX1, topY1, topX2, topY2);
        // compute right remainder
        final double rightX1 = isectX2;
        final double rightY1 = isectY1;
        final double rightX2 = aX2;
        final double rightY2 = isectY2;
        if(rightX2>rightX1 && rightY2>rightY1)
            remainder[remainders++] = new Rectangle(rightX1, rightY1, rightX2, rightY2);
        // compute bottom remainder
        final double bottomX1 = aX1;
        final double bottomY1 = isectY2;
        final double bottomX2 = aX2;
        final double bottomY2 = aY2;
        if(bottomX2>bottomX1 && bottomY2>bottomY1)
            remainder[remainders++] = new Rectangle(bottomX1, bottomY1, bottomX2, bottomY2);
        // compute right remainder
        final double leftX1 = aX1;
        final double leftY1 = isectY1;
        final double leftX2 = isectX1;
        final double leftY2 = isectY2;
        if(leftX2>leftX1 && leftY2>leftY1)
            remainder[remainders++] = new Rectangle(leftX1, leftY1, leftX2, leftY2);
        
        return remainders;
    }

}
