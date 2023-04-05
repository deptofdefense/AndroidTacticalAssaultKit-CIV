
package com.atakmap.android.maps;

import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 * A wedge describes a triangle (drawn below) with vertex points (X, Y, Y').
 * There is an offset triangle (labeled "off") below, that allows you to skip
 * an angleX (labeled "x") before drawing your desired wedge with angle
 * "angleXprime" (labeled x' in the diagram below).
 *
 *               Y'
 *             /|  
 *   label0   / |  
 *           /  |  
 *          /   |  
 *         /    |Y    label2
 *        /    /|  
 *       /   /  |  
 *      /x'/    |  
 *     / /x  off|  
 *   X ---------- Z
 *
 *       label1
 *
 * Upper-case letters are points, (e.g., X, Y, Z, Y').
 * In code, we denote them as "pointX" or "pointYprime".
 * Lower-case letters are angles, (e.g., x, x', y, y')
 * In code, we denote them as "angleX" or "angleXprime".
 * when labeling 
 *
 */
public class Wedge extends Polyline {
    public static final String TAG = "Wedge";

    // ///////////////////////////////////////////////////////////////////
    // User input state variables

    /**
     * The multiplier to place the wedge past the z point. Defaults to a value of 1.0. So the wedge
     * would be the exact same distance as Point X to Point Z.
     */
    private double multiplier = 1.0;

    /** Source point of the wedge */
    private GeoPoint pointX = null;

    /** End point (along a right-angle) of the wedge */
    private GeoPoint pointZ = null;

    /**
     * Source point (offset) angle of inclination (in degrees). Defaults to zero.
     */
    private double angleX = 0.0;

    /** Source point angle of inclination (in degrees) */
    private double angleXprime = Double.NaN;

    /** Number of segments for each arc */
    private static final int NUM_SEGMENTS = 5;

    // ///////////////////////////////////////////////////////////////////
    // Constructor

    /**
     * Default constructor.
     */
    public Wedge(String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public Wedge(long serialId, MetaDataHolder metadata, String uid) {
        super(serialId, metadata, uid);
        setStyle(getStyle() | STYLE_CLOSED_MASK);// | STYLE_OUTLINE_STROKE_MASK);
    }

    // ///////////////////////////////////////////////////////////////////
    // State setter functions

    /**
     * Sets the originating point for the wedge.
     * 
     * @param pointX A valid GeoPoint denoting the start of the Wedge.
     * @return - A new immutable Wedge instance with the source point set. If pointX is null, a
     *         default Wedge instance is returned.
     */
    public Wedge setSourcePoint(GeoPoint pointX) {
        if (pointX == null)
            return new Wedge(UUID.randomUUID().toString());
        this.pointX = pointX;
        recomputeWedge();
        return this;
    }

    /**
     * Sets the end point for the wedge. See class documentation for how the Wedge is computed.
     * 
     * @param pointZ A valid GeoPoint denoting the end of the Wedge.
     * @return - A new immutable Wedge instance with its end point set. If poinZ is null, a default
     *         Wedge instance is returned.
     */
    public Wedge setEndPoint(GeoPoint pointZ) {
        if (pointZ == null)
            return new Wedge(UUID.randomUUID().toString());

        this.pointZ = pointZ;
        recomputeWedge();
        return this;
    }

    /**
     * The multiplier to place the wedge past the z point. Defaults to a value of 1.0. So the wedge
     * would be the exact same distance as Point X to Point Z.
     * 
     * @param multiplier A double representing the distance multiplier
     * @return - A new immutable Wedge instance with the multiplier value.
     */
    public Wedge setDistanceMultiplier(Double multiplier) {
        this.multiplier = multiplier;
        recomputeWedge();
        return this;
    }

    /**
     * Sets the offset angle for this Wedge. Setting this angle will rotate the Wedge by the
     * supplied value when drawing to screen.
     * 
     * @param angleX The desired offset angle expressed in degrees
     * @return A new immutable Wedge instance with the offset angle set.
     */
    public Wedge setOffsetAngle(Double angleX) {
        this.angleX = angleX;
        recomputeWedge();
        return this;
    }

    /**
     * Sets the primary interior angle for the Wedge.
     * 
     * @param angleXprime The desired primary interior angle expressed in degrees. The sum of all
     *            interior angles must be less than 180 degrees.
     * @return A new immutable Wedge instance with the primary angle set. If the angleXPrime angle
     *         is invalid then a default Wedge instance is returned.
     */
    public Wedge setAngle(Double angleXprime) {
        return setAngle(angleXprime, false);
    }

    /**
     * Sets the primary interior angle for the Wedge.
     * 
     * @param angleXprime The desired primary interior angle expressed in degrees. The sum of all
     *            interior angles must be less than 180 degrees.
     * @param showLabel Indicate whether or not the Wedge should have a Label indicating the primary
     *            interior angle.
     * @return A new immutable Wedge instance with the primary angle set. If the angleXPrime angle
     *         is invalid then a default Wedge instance is returned.
     */

    public Wedge setAngle(final double angleXprime, final boolean showLabel) {
        String primaryInterior = "";
        if (showLabel) {
            primaryInterior = (int) Math.round(angleXprime)
                    + Angle.DEGREE_SYMBOL;
        }
        return setAngle(angleXprime, null, primaryInterior, null);
    }

    /**
     * Sets the primary interior angle for the Wedge.
     * 
     * @param angleXprime The desired primary interior angle expressed in degrees. The sum of all
     *            interior angles must be less than 180 degrees.
     * @param label0 the label to be displayed for label0 described in the header of this class X->Y'
     *               which can be null or empty.
     * @param label1 the label to be displayed for label1 described in the header of this class X->Z
     *               which can be null or empty.
     * @param label2 the label to be displayed for label2 described in the header of this class Z->Y'
     *               which can be null or empty.
     
     * @return A new immutable Wedge instance with the primary angle set. If the angleXPrime angle
     *         is invalid then a default Wedge instance is returned.
     */
    public Wedge setAngle(double angleXprime,
            String label0,
            String label1,
            String label2) {
        if (angleXprime < 0 || angleXprime >= 180.0)
            return new Wedge(UUID.randomUUID().toString());

        this.angleXprime = angleXprime;
        recomputeWedge();
        Map<String, Object> labels = this.getMetaMap("labels");
        if (labels == null) {
            labels = new HashMap<>();
            this.setMetaMap("labels", labels);
        }

        Map<String, Object> seg0 = (Map<String, Object>) labels.get("seg0");
        Map<String, Object> seg1 = (Map<String, Object>) labels.get("seg1");
        Map<String, Object> seg2 = (Map<String, Object>) labels.get("seg2");
        labels.clear();

        if (label0 != null) {
            if (seg0 == null)
                seg0 = new HashMap<>();
            seg0.put("segment", 0);
            seg0.put("text", label0);
            labels.put("seg0", seg0);
        }

        if (label1 != null) {
            if (seg1 == null)
                seg1 = new HashMap<>();
            seg1.put("segment",
                    (int) Math.ceil(NUM_SEGMENTS / 2.0));
            seg1.put("text", label1);
            labels.put("seg1", seg1);
        }

        if (label2 != null) {
            if (seg2 == null)
                seg2 = new HashMap<>();
            seg2.put("segment", NUM_SEGMENTS + 1);
            seg2.put("text", label2);
            labels.put("seg2", seg2);
        }

        this.setMetaMap("labels", labels);
        setLabels(labels);
        return this;
    }

    // ///////////////////////////////////////////////////////////////////
    // State getter functions

    public GeoPoint getSourcePoint() {
        return this.pointX;
    }

    public GeoPoint getEndPoint() {
        return this.pointZ;
    }

    public double getDistanceMultiplier() {
        return this.multiplier;
    }

    public double getOffsetAngle() {
        return this.angleX;
    }

    public double getAngle() {
        return this.angleXprime;
    }

    // ///////////////////////////////////////////////////////////////////
    // Wedge Computation (and populating the Polyline's getPoints() state)

    private boolean recomputeWedge() {
        // Check that we have all the info we need.
        if (this.pointX == null ||
                this.pointZ == null ||
                Double.isNaN(this.angleX) ||
                Double.isNaN(this.angleXprime)) {
            return false;
        }

        // XXX We need to be using the glorthoview.scale and glorthoview.drawLat to generate angles
        // properly
        // otherwise the displayed angle will be skewed from what is represented on the map.

        // Since I don't want to make an entirely new GLMapItem for GLWedge, I'm just going to use
        // the DistanceCalculations to get the correct points given angleX and angleXprime which
        // will be
        // offset from the azimuth of Point X to Point Z

        // Initialize 3 points representing X, Y, and Y' respectively
        GeoPointMetaData[] corners = new GeoPointMetaData[NUM_SEGMENTS + 3];

        // Set the known point, the source, X
        corners[0] = GeoPointMetaData.wrap(pointX);

        // Get the true azimuth of point X to point Z
        // This will be used to apply the offset
        // Get the distance in meters so we can apply the multiplier for the
        // wedge's radius.
        double azimuth = GeoCalculations.bearingTo(pointX, pointZ);
        double distance = GeoCalculations.distanceTo(pointX, pointZ)
                * this.multiplier;
        double fraction = 1.0 / NUM_SEGMENTS;

        // include the starting point and make the ending point the same as the starting point
        for (int i = 0; i <= NUM_SEGMENTS; i++) {
            GeoPoint gp = GeoCalculations.pointAtDistance(pointX,
                    azimuth - angleX - angleXprime * i * fraction, distance);
            corners[i + 1] = GeoPointMetaData.wrap(gp);
        }

        corners[NUM_SEGMENTS + 2] = GeoPointMetaData.wrap(pointX);

        setPoints(corners);

        return true;
    }

    public String toString() {
        GeoPoint[] gp = getPoints();
        if (gp != null && gp.length >= 3) {
            return "<Wedge "
                    + "pointX=\"(" + gp[0].getLongitude() + ", "
                    + getPoints()[0].getLatitude() + ")\" "
                    + "pointY=\"(" + getPoints()[1].getLongitude() + ", "
                    + getPoints()[1].getLatitude() + ")\" "
                    + "pointZ=\"(" + getPoints()[2].getLongitude() + ", "
                    + getPoints()[2].getLatitude() + ")\" "
                    + ">";
        }
        return "<Wedge - undefined>";
    }

}
