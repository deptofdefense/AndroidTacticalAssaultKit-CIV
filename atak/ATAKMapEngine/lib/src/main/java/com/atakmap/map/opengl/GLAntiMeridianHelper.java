package com.atakmap.map.opengl;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import com.atakmap.map.layer.feature.geometry.Envelope;

/**
 * <P>This class is NOT thread-safe.
 * 
 * @author Developer
 */
public final class GLAntiMeridianHelper {
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
}
