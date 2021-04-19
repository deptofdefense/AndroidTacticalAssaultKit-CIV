package com.atakmap.map.opengl;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import com.atakmap.map.layer.feature.geometry.Envelope;

import java.nio.DoubleBuffer;

/**
 * <P>This class is NOT thread-safe.
 * 
 * @author Developer
 */
public final class GLAntiMeridianHelper {
    public final static int MASK_IDL_CROSS = 0x02;
    public final static int MASK_PRIMARY_HEMISPHERE = 0x01;

    public final static int HEMISPHERE_EAST = 0;
    public final static int HEMISPHERE_WEST = 1;

    private double drawLng;
    private double eastBoundUnwrapped;
    private double westBoundUnwrapped;
    private boolean continuousScroll, crossesIDL;
    private int primaryHemisphere;
    private final MutableGeoBounds westHemisphere;
    private final MutableGeoBounds eastHemisphere;
    
    public GLAntiMeridianHelper() {
        //this.primaryHemisphere = HEMISPHERE_WEST;
        this.primaryHemisphere = HEMISPHERE_EAST;
        this.westHemisphere = new MutableGeoBounds(0d, 0d, 0d, 0d);
        this.eastHemisphere = new MutableGeoBounds(0d, 0d, 0d, 0d);
        this.crossesIDL = false;
    }

    public void update(GLMapView view) {
        this.crossesIDL = view.crossesIDL;
        this.continuousScroll = view.continuousScrollEnabled;

        this.primaryHemisphere = (view.drawLng < 0d) ? HEMISPHERE_WEST : HEMISPHERE_EAST;
        this.eastBoundUnwrapped = view.eastBoundUnwrapped;
        this.westBoundUnwrapped = view.westBoundUnwrapped;
        this.drawLng = view.drawLng;
 
        if(this.crossesIDL) {
            // eastern hemi
            this.eastHemisphere.set(view.northBound, view.westBound,
                                    view.southBound, 180d);
            // western hemi
            this.westHemisphere.set(view.northBound, -180d,
                                    view.southBound, view.eastBound);
        } else {
            // eastern hemi
            this.eastHemisphere.set(view.northBound, view.westBound,
                                    view.southBound, view.eastBound);
            // western hemi
            this.westHemisphere.set(view.northBound, view.westBound,
                                    view.southBound, view.eastBound);
        }
    }
    
    public int getPrimaryHemisphere() {
        return this.primaryHemisphere;
    }

    public void getBounds(int hemisphere, MutableGeoBounds hemi) {
        switch(hemisphere) {
            case HEMISPHERE_EAST :
                hemi.set(this.eastHemisphere);
                break;
            case HEMISPHERE_WEST :
                hemi.set(this.westHemisphere);
                break;
            default :
                throw new IllegalArgumentException();
        }
    }
    
    public void getBounds(MutableGeoBounds westHemi, MutableGeoBounds eastHemi) {
        westHemi.set(this.westHemisphere);
        eastHemi.set(this.eastHemisphere);
    }
    
    public void wrapLongitude(int hemisphere, GeoPoint src, GeoPoint dst) {
        if(!this.crossesIDL && hemisphere == this.primaryHemisphere && dst == src)
            return;
        
        dst.set(src.getLatitude(),
                wrapLongitude(hemisphere, src.getLongitude()),
                src.getAltitude(), src.getAltitudeReference(),
                src.getCE(),
                src.getLE());
    }
    
    public double wrapLongitude(int hemisphere, double longitude) {
        if(!this.crossesIDL || hemisphere == this.primaryHemisphere) {
            return longitude;
        } else if(this.primaryHemisphere == HEMISPHERE_WEST) {
            return longitude-360d;
        } else if(this.primaryHemisphere == HEMISPHERE_EAST) {
            return longitude+360d;            
        } else {
            throw new IllegalArgumentException();
        }
    }
    
    public double wrapLongitude(double longitude) {
        final int hemisphere;
        if(!this.crossesIDL) {
            return longitude;
        } else if((longitude >= this.eastHemisphere.getWest()
                && longitude <= this.eastHemisphere.getEast())) {
            hemisphere = HEMISPHERE_EAST;
        } else if((longitude >= this.westHemisphere.getWest()
                && longitude <= this.westHemisphere.getEast())) {
            hemisphere = HEMISPHERE_WEST;
        } else {
            return longitude; // out of bounds, don't wrap
        }

        return wrapLongitude(hemisphere, longitude);
    }

    /**
     * Get the longitudinal unwrap value for a set of geo bounds
     * +360 = transform all western hemisphere points to > 360 longitude
     * -360 = transform all eastern hemisphere points to < -360 longitude
     * @param bounds Geo bounds
     * @return The unwrap value (360, -360, or 0 for no unwrap)
     */
    public double getUnwrap(GeoBounds bounds) {
        if (!this.continuousScroll)
            return 0;
        return getUnwrap(this.eastBoundUnwrapped, this.westBoundUnwrapped,
                bounds);
    }

    /**
     * Same as above but for envelopes, which are used by feature datasets
     * Also does not use 180-degree wrapping, meaning any bounds which cross
     * the IDL are represented with unwrapped longitudinal values
     * (i.e. minX = 179, maxX = 181)
     * @param bounds Envelope bounds
     * @return The unwrap value
     */
    public double getUnwrap(Envelope bounds) {
        if (!this.continuousScroll)
            return 0;
        return getUnwrap(this.eastBoundUnwrapped, this.westBoundUnwrapped,
                bounds);
    }

    public static double getUnwrap(double eastBoundUnwrapped,
            double westBoundUnwrapped, GeoBounds bounds) {
        boolean wrap = bounds.crossesIDL() || eastBoundUnwrapped > 180
                && bounds.getWest() + 360 <= eastBoundUnwrapped
                || westBoundUnwrapped < -180 && bounds.getEast() - 360
                >= westBoundUnwrapped;
        if (!wrap)
            return 0;
        if (bounds.crossesIDL()) {
            if (westBoundUnwrapped < -180)
                return -360;
            else if (eastBoundUnwrapped > 180)
                return 360;
            else if (westBoundUnwrapped <= bounds.getWest())
                return -360;
            else if (eastBoundUnwrapped >= bounds.getEast())
                return 360;
            else {
                // Potential de-sync where the item is no longer in bounds
                // but the drawing operation is still taking place
                return (eastBoundUnwrapped + westBoundUnwrapped) / 2 < 0
                        ? -360 : 360;
            }
        } else {
            if (westBoundUnwrapped < -180 && bounds.getWest() > 0)
                return -360;
            else if (eastBoundUnwrapped > 180 && bounds.getEast() < 0)
                return 360;
        }
        return 0;
    }

    public static double getUnwrap(double eastBoundUnwrapped,
            double westBoundUnwrapped, Envelope bounds) {
        boolean wrap = bounds.crossesIDL() || eastBoundUnwrapped > 180 &&
                bounds.maxX <= eastBoundUnwrapped || westBoundUnwrapped < -180
                && bounds.minX >= westBoundUnwrapped;
        if (!wrap)
            return 0;
        if (bounds.crossesIDL()) {
            if (westBoundUnwrapped < -180 && bounds.maxX > 180)
                return -360;
            else if (eastBoundUnwrapped > 180 && bounds.minX < -180)
                return 360;
            else if (westBoundUnwrapped <= bounds.maxX - 360)
                return -360;
            else if (eastBoundUnwrapped >= bounds.minX + 360)
                return 360;
        } else {
            if (westBoundUnwrapped < -180 && bounds.maxX - 360 >= westBoundUnwrapped)
                return -360;
            else if (eastBoundUnwrapped > 180 && bounds.minX + 360 <= eastBoundUnwrapped)
                return 360;
        }
        return 0;
    }

    /**
     * Normalizes all points relative into a primary hemisphere. Points
     * determined to cross the IDL from the primary hemisphere into the
     * secondary hemisphere will be "unwrapped".
     *
     * @param size  The number of elements per position, either <code>2</code>
     *              or <code>3</code>
     * @param src   The source points, ordered
     *              <I>longitude,latitude[,altitude]</I>
     * @param dst   The result points
     * @return  A bitmask indicating the primary hemisphere and whether the
     *          source geometry crosses the IDL. The {@link #MASK_IDL_CROSS}
     *          bit will be toggled if the source geometry crosses the IDL;
     *          the value resulting from applying the
     *          {@link #MASK_PRIMARY_HEMISPHERE} will be one of
     *          {@link #HEMISPHERE_EAST} or {@link #HEMISPHERE_WEST}.
     *          <P><code><BR>
     *              int result = GLAntiMeridianHelper.normalizeHemisphere(...);<BR>
     *              boolean crossesIdl = (result&GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;<BR>
     *              int primaryHemi = (result&GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);<BR>
     *          </code>
     *          </P>
     */
    public static int normalizeHemisphere(int size, DoubleBuffer src, DoubleBuffer dst) {
        final int numPoints = (src.remaining()/size);
        double lastx = GeoCalculations.wrapLongitude(src.get(0));
        double unwrap = 0d;

        boolean crossesIDL = false;
        int primaryHemi = 0;

        final int off = src.position();
        for (int currVert = 0; currVert < numPoints*size; currVert += size) {
            final int pos = currVert+off;

            // start of line segment
            double ax = GeoCalculations.wrapLongitude(src.get(pos));
            double ay = src.get(pos + 1);
            double az = size == 3 ? src.get(pos + 2) : 0d;

            // check for IDL crossing -- any longitudinal span greater than
            // 180 deg crosses
            if (Math.abs(ax - lastx) > 180d) {
                // capture IDL crossing, assign primary hemi
                if (!crossesIDL) {
                    crossesIDL = true;
                    primaryHemi = (lastx >= 0d) ? 1 : -1;
                }

                // we were unwrapping, but have crossed back
                if (unwrap != 0d)
                    unwrap = 0d;
                    // start wrapping east
                else if (lastx >= 0d)
                    unwrap = 360d;
                    // start wrapping west
                else
                    unwrap = -360d;
            }
            // assign BEFORE unwrapping
            lastx = ax;

            dst.put(ax + unwrap);
            dst.put(ay);
            if(size == 3)
                dst.put(az);
        }

        if(primaryHemi == 0)
            primaryHemi = (lastx >= 0d) ? 1 : -1;

        if(primaryHemi == 1)
            primaryHemi = HEMISPHERE_EAST;
        else
            primaryHemi = HEMISPHERE_WEST;

        int retval = 0;
        if(crossesIDL)
            retval |= MASK_IDL_CROSS;
        retval |= primaryHemi;
        return retval;
    }

    /**
     * Normalizes the provided points relative to a primary hemisphere.
     * Points determined to cross the IDL from the primary hemisphere into the
     * secondary hemisphere will be "unwrapped".
     *
     * @param a The start point of the segment. May be modified if
     *          normalization is required
     * @param b The end point of the segment. May be modified if normalization
     *          is required
     * @return  A bitmask indicating the primary hemisphere and whether the
     *          source geometry crosses the IDL. The {@link #MASK_IDL_CROSS}
     *          bit will be toggled if the source geometry crosses the IDL;
     *          the value resulting from applying the
     *          {@link #MASK_PRIMARY_HEMISPHERE} will be one of
     *          {@link #HEMISPHERE_EAST} or {@link #HEMISPHERE_WEST}.
     *          <P><code><BR>
     *              int result = GLAntiMeridianHelper.normalizeHemisphere(...);<BR>
     *              boolean crossesIdl = (result&GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;<BR>
     *              int primaryHemi = (result&GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);<BR>
     *          </code>
     *          </P>
     */
    public static int normalizeHemisphere(GeoPoint a, GeoPoint b) {
        double unwrap = 0d;

        boolean crossesIDL = false;
        int primaryHemi = 0;

        // start of the line segment
        double ax = GeoCalculations.wrapLongitude(a.getLongitude());

        // end of line segment
        double bx = GeoCalculations.wrapLongitude(b.getLongitude());

        // check for IDL crossing -- any longitudinal span greater than
        // 180 deg crosses
        if (Math.abs(bx - ax) > 180d) {
            // capture IDL crossing, assign primary hemi
            if (!crossesIDL) {
                crossesIDL = true;
                primaryHemi = (ax >= 0d) ? 1 : -1;
            }

            // we were unwrapping, but have crossed back
            if (unwrap != 0d)
                unwrap = 0d;
                // start wrapping east
            else if (ax >= 0d)
                bx += 360d;
                // start wrapping west
            else
                bx -= 360d;
        }

        if(a.getLongitude() != ax)
            a.set(a.getLatitude(), ax, a.getAltitude(), a.getAltitudeReference(), a.getCE(), a.getLE());
        if(b.getLongitude() != bx)
            b.set(b.getLatitude(), bx, b.getAltitude(), b.getAltitudeReference(), b.getCE(), b.getLE());

        if(primaryHemi == 0)
            primaryHemi = (ax >= 0d) ? 1 : -1;

        if(primaryHemi == 1)
            primaryHemi = HEMISPHERE_EAST;
        else
            primaryHemi = HEMISPHERE_WEST;

        int retval = 0;
        if(crossesIDL)
            retval |= MASK_IDL_CROSS;
        retval |= primaryHemi;
        return retval;
    }

    /**
     * Returns the longitude unwrapping value for source data that has been
     * normalized relative to a primary hemisphere.
     * @param view              The {@link GLMapView}
     * @param srcCrossesIDL     <code>true</code> if the source data crosses
     *                          the IDL, <code>false</code> otherwise
     * @param srcPrimaryHemi    The primary hemisphere for the source data. One
     *                          of {@link #HEMISPHERE_EAST} or
     *                          {@link #HEMISPHERE_WEST}
     * @return  The value to be added to longitude values of the source data
     *          for display, given the current view state
     */
    public static double getUnwrap(GLMapView view, boolean srcCrossesIDL, int srcPrimaryHemi) {
        final int hemiMult = (srcPrimaryHemi == HEMISPHERE_WEST) ? -1 : 1;
        if(view.currentPass.drawSrid == 4326 && (view.currentPass.crossesIDL || srcCrossesIDL) && (hemiMult*view.currentPass.drawLng) < 0)
            return -360d*hemiMult;
        else
            return 0d;
    }
}
