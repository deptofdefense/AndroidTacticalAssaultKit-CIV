// Created by plusminus on 19:06:38 - 25.09.2008
package transapps.geom;

import java.util.ArrayList;
import java.util.List;

import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;

/**
 *
 * Class that represents a geographic bounding region.  Can be used to bound points or to determine
 * if points are within the bounding box.
 *
 * @author Nicolas Gramlich
 * 
 */
public class BoundingBoxE6 extends Rect implements Parcelable {

    // ===========================================================
    // Constructors
    // ===========================================================

    /**
     * Empty constructor
     *
     * @param
     */
    public BoundingBoxE6() {
        super();
    }

    /**
     * Constructor to create a bounding box based on upper left and bottom right
     * points using lat/lon in 1E6 format.
     *
     * @param northE6 Top right
     * @param eastE6 Top right
     * @param southE6 Bottom left
     * @param westE6 Bottom left
     *
     */
    public BoundingBoxE6(final int northE6, final int eastE6, final int southE6, final int westE6) {
        super(westE6, northE6, eastE6, southE6);
    }

    /**
     * Constructor to create a bounding box based on upper right and bottom left
     * points using double format.
     *
     * @param north Top right
     * @param east Top right
     * @param south Bottom left
     * @param west Bottom left
     *
     */
    public BoundingBoxE6(final double north, final double east, final double south,
            final double west) {
        this((int) (north * 1E6), (int) (east * 1E6), (int) (south * 1E6), (int) (west * 1E6));
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    /**
     * @return GeoPoint center of this BoundingBox
     */
    public GeoPoint getCenter() {
        return super.getCenter(new GeoPoint());
    }

    /**
     * Method returns the center of the bounding box as a type derived from
     * Coordinate
     *
     * @return Corodinate center of this BoundingBox
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Coordinate> T getCenter(T reuse) {
        return super.getCenter((T) (reuse == null ? new GeoPoint() : reuse));
    }

    /**
     * @return int the diagonal length of the bounding box in meters
     */
    public int getDiagonalLengthInMeters() {
        return new GeoPoint(top, left).distanceTo(new GeoPoint(bottom, right));
    }

    /**
     * @return int lat E6 of the top right corner of the box
     */
    public int getLatNorthE6() {
        return this.top;
    }

    /**
     * @return int lat E6 of the bottom left corner of the box
     */
    public int getLatSouthE6() {
        return this.bottom;
    }

    /**
     * @return int lon E6 of the top right corner of the box
     */
    public int getLonEastE6() {
        return this.right;
    }

    /**
     * @return int lon E6 of the bottom right corner of the box
     */
    public int getLonWestE6() {
        return this.left;
    }

    /**
     * Setter for top right
     * @param late6 top right latitude in E6 format
     */
    public void setLatNorthE6(int late6) {
        this.top = late6;
        empty = false;
    }

    /**
     * Setter for bottom left
     *
     * @param late6 bottom left latitude in E6 format
     */
    public void setLatSouthE6(int late6) {
        this.bottom = late6;
        empty = false;
    }

    /**
     * Setter for top right
     *
     * @param lone6 top right longitude in E6 format
     */
    public void setLonEastE6(int lone6) {
        this.right = lone6;
        empty = false;
    }

    /**
     * Setter for bottom left
     *
     * @param lone6 bottom left longitude in E6 format
     */
    public void setLonWestE6(int lone6) {
        this.left = lone6;
        empty = false;
    }

    /**
     * Setter for all coordinates in E6 format
     *
     * @param northE6 top right latitude in E6 format
     * @param eastE6 top right longitude in E6 format
     * @param southE6 bottom left latitude in E6 format
     * @param westE6 bottom left longitude in E6 format
     */
    public void setCoordsE6(final int northE6, final int eastE6, final int southE6, final int westE6) {
        setCoords(westE6, northE6, eastE6, southE6);
    }


    public int getLatitudeSpanE6() {
        return getYSpan();
    }

    public int getLongitudeSpanE6() {
        return getXSpan();
    }

    /**
     * Expand the Bounding box to encompass the passed in Point if it does not already
     *
     * @param point
     */
    @Override
    public void expand(Coordinate point) {
        int y = point.getY();
        int x = point.getX();
        if( empty ) {
            top = bottom = y;
            left = right = x;
        } else {
            left = Math.min(left, x);
            bottom = Math.min(bottom, y);
            right = Math.max(right, x);
            top = Math.max(top, y);
        }
        empty = false;
    }

    /**
     * 
     * @param aLatitude   latitude of point within the bounding box
     * @param aLongitude  longitude of point within the bounding box
     * @param reuse       can be null, PointF parameter that will be filled with the interpolated point if not null
     * @return relative position determined from the upper left corner.<br />
     *         {0,0} would be the upper left corner. {1,1} would be the lower right corner. {1,0}
     *         would be the lower left corner. {0,1} would be the upper right corner.
     */
    public PointF getRelativePositionOfGeoPointInBoundingBoxWithLinearInterpolation(
            final int aLatitude, final int aLongitude, final PointF reuse) {
        final PointF out = (reuse != null) ? reuse : new PointF();
        final float y = ((float) (this.top - aLatitude) / getLatitudeSpanE6());
        final float x = 1 - ((float) (this.right - aLongitude) / getLongitudeSpanE6());
        out.set(x, y);
        return out;
    }

    /**
     *
     * @param aLatitudeE6   latitude of point within the bounding box
     * @param aLongitudeE6  longitude of point within the bounding box
     * @param reuse can be null, PointF parameter that will be filled with the interpolated point if not null
     * @return relative position determined from the upper left corner.<br />
     *         {0,0} would be the upper left corner. {1,1} would be the lower right corner. {1,0}
     *         would be the lower left corner. {0,1} would be the upper right corner.
     */
    public PointF getRelativePositionOfGeoPointInBoundingBoxWithExactGudermannInterpolation(
            final int aLatitudeE6, final int aLongitudeE6, final PointF reuse) {
        final PointF out = (reuse != null) ? reuse : new PointF();
        final float y = (float) ((GeoMath.gudermannInverse(this.top / 1E6) - GeoMath.gudermannInverse(aLatitudeE6 / 1E6)) / (GeoMath.gudermannInverse(this.top / 1E6) - GeoMath.gudermannInverse(this.bottom / 1E6)));
        final float x = 1 - ((float) (this.right - aLongitudeE6) / getLongitudeSpanE6());
        out.set(x, y);
        return out;
    }

    /**
     *
     * @param relX relative point within the bounding box to map to a GeoPoint
     * @param relY relative point within the bounding box to map to a GeoPoint
     * @param reuse can be null, GeoPoint value that will be filled in if non null
     * @return relative position determined from the upper left corner.<br />
     *         {0,0} would be the upper left corner. {1,1} would be the lower right corner. {1,0}
     *         would be the lower left corner. {0,1} would be the upper right corner.
     */
    public GeoPoint getGeoPointOfRelativePositionWithLinearInterpolation(final float relX,
            final float relY, GeoPoint reuse) {

        int lat = (int) (this.top - (this.getLatitudeSpanE6() * relY));

        int lon = (int) (this.left + (this.getLongitudeSpanE6() * relX));

        /* Bring into bounds. */
        while (lat > 90500000)
            lat -= 90500000;
        while (lat < -90500000)
            lat += 90500000;

        /* Bring into bounds. */
        while (lon > 180000000)
            lon -= 180000000;
        while (lon < -180000000)
            lon += 180000000;

        if( reuse == null ) reuse = new GeoPoint();
        reuse.setCoordsE6(lat, lon);
        return reuse;
    }

    /**
     *
     * @param relX relative point within the bounding box to map to a GeoPoint
     * @param relY relative point within the bounding box to map to a GeoPoint
     * @param reuse can be null, GeoPoint value that will be filled in if non null
     * @return relative position determined from the upper left corner.<br />
     *         {0,0} would be the upper left corner. {1,1} would be the lower right corner. {1,0}
     *         would be the lower left corner. {0,1} would be the upper right corner.
     */
    public GeoPoint getGeoPointOfRelativePositionWithExactGudermannInterpolation(final float relX,
            final float relY, GeoPoint reuse) {

        final double gudNorth = GeoMath.gudermannInverse(this.top / 1E6);
        final double gudSouth = GeoMath.gudermannInverse(this.bottom / 1E6);
        final double latD = GeoMath.gudermann((gudSouth + (1 - relY) * (gudNorth - gudSouth)));
        int lat = (int) (latD * 1E6);

        int lon = (int) ((this.left + (this.getLongitudeSpanE6() * relX)));

        /* Bring into bounds. */
        while (lat > 90500000)
            lat -= 90500000;
        while (lat < -90500000)
            lat += 90500000;

        /* Bring into bounds. */
        while (lon > 180000000)
            lon -= 180000000;
        while (lon < -180000000)
            lon += 180000000;

        return new GeoPoint(lat, lon);
    }

    /**
     * Expand the bounding box in both directions by this factor
     *
     * @param pBoundingboxPaddingRelativeScale float factor to scale the bounding box by (<1 will shrink the box)
     */
    public void increaseByScale(final float pBoundingboxPaddingRelativeScale) {
        int lone6 = getCenterX();
        int late6 = getCenterY();
        final int mLatSpanE6Padded_2 = (int) ((this.getLatitudeSpanE6() * pBoundingboxPaddingRelativeScale) / 2);
        final int mLonSpanE6Padded_2 = (int) ((this.getLongitudeSpanE6() * pBoundingboxPaddingRelativeScale) / 2);

        setCoordsE6(late6 + mLatSpanE6Padded_2,
                lone6 + mLonSpanE6Padded_2, late6
                        - mLatSpanE6Padded_2, lone6 - mLonSpanE6Padded_2);
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    /**
     * Pretty print the bounding box.
     *
     */
    @Override
    public String toString() {
        return new StringBuffer().append("N:").append(this.top).append("; E:")
                .append(this.right).append("; S:").append(this.bottom).append("; W:")
                .append(this.left).toString();
    }

    // ===========================================================
    // Methods
    // ===========================================================

    public GeoPoint bringToBoundingBox(final int aLatitudeE6, final int aLongitudeE6) {
        return new GeoPoint(Math.max(this.bottom, Math.min(this.top, aLatitudeE6)),
                Math.max(this.left, Math.min(this.right, aLongitudeE6)));
    }

    /**
     * Method to create the smallest bounding box tha includes all the points passed in. The method
     * will find the min and max latitude and longitude of the points and use those for the corners
     * of the bounding box.
     *
     * @param partialPolyLine List of GeoPoints the bounding box should be large enough to encompass.
     *
     */
    public static BoundingBoxE6 fromGeoPoints(final List<? extends GeoPoint> partialPolyLine) {
        int minLat = Integer.MAX_VALUE;
        int minLon = Integer.MAX_VALUE;
        int maxLat = Integer.MIN_VALUE;
        int maxLon = Integer.MIN_VALUE;
        for (final GeoPoint gp : partialPolyLine) {
            final int latitudeE6 = gp.getLatitudeE6();
            final int longitudeE6 = gp.getLongitudeE6();

            minLat = Math.min(minLat, latitudeE6);
            minLon = Math.min(minLon, longitudeE6);
            maxLat = Math.max(maxLat, latitudeE6);
            maxLon = Math.max(maxLon, longitudeE6);
        }

        return new BoundingBoxE6(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Method to determine if the Coordinate is within the Bounding Box.
     *
     * @param coord coordinate to check if it is inside the bounding box
     * @return true if in the box, false if outside
     */
    @Override
    public boolean contains(final Coordinate coord) {
        return contains(coord.getY(), coord.getX());
    }

    /**
     * Determines if the latitude and longitude in E6 format is within the Bounding Box.
     *
     * @param aLatitudeE6 latitude of point to check if it is inside the bounding box
     * @param aLongitudeE6 longitude of point to check if it is inside the bounding box
     * @return true if in the box, false if outside
     */
    @Override
    public boolean contains(final int aLatitudeE6, final int aLongitudeE6) {
        return ((aLatitudeE6 >= this.bottom) && (aLatitudeE6 <= this.top))
                && ((aLongitudeE6 <= this.right) && (aLongitudeE6 >= this.left));
    }

    /**
     * This will check to see if this box completely contains the box passed in
     *
     * @param b
     *             The box to check to see if it is completely contained inside this box
     * @return true if this box completely contains the passed in box
     * @since NW SDK 1.1.15.4
     */
    public boolean contains( BoundingBoxE6 b )
    {
        return ( b.right <= right ) && ( b.left >= left ) && ( b.bottom >= bottom ) && ( b.top
                    <= top );
    }

    /**
     * Determine if two Boxes intersect
     *
     * @param b2 Box to compare for intersection
     * @return true if intersects, false otherwise
     */
    @Override
    public boolean intersects(Box b2) {
        int top1 = top;
        int left1 = left;
        int bottom1 = bottom;
        int right1 = right;

        int top2 = b2.getTop();
        int left2 = b2.getLeft();
        int bottom2 = b2.getBottom();
        int right2 = b2.getRight();

        return left1 < right2 && left2 < right1 && top1 > bottom2 && top2 > bottom1;
    }

    /**
     * Determine if the two bounding boxes intersect
     *
     * @param b2
     *             Box to compare for intersection
     * @return true if intersects, false otherwise
     * @since NW SDK 1.1.15.4
     */
    public boolean intersects( BoundingBoxE6 b2 )
    {
        int top1 = top;
        int left1 = left;
        int bottom1 = bottom;
        int right1 = right;

        int top2 = b2.top;
        int left2 = b2.left;
        int bottom2 = b2.bottom;
        int right2 = b2.right;

        return left1 < right2 && left2 < right1 && top1 > bottom2 && top2 > bottom1;
    }

    /**
     * Returns the four corners of this bounding box
     * 
     * @return List of Coordinates of the four corners of the Bounding Box
     */
    @Override
    public List<? extends Coordinate> getCorners() {
        List<Coordinate> output = new ArrayList<Coordinate>(4);
        output.add(new GeoPoint(top, left));
        output.add(new GeoPoint(top, right));
        output.add(new GeoPoint(bottom, right));
        output.add(new GeoPoint(bottom, left));
        return output;
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    // ===========================================================
    // Parcelable
    // ===========================================================
    /**
     * Inner class to convert a Bounding box to a parcelable for Android
     */
    public static final Parcelable.Creator<BoundingBoxE6> CREATOR = new Parcelable.Creator<BoundingBoxE6>() {
        @Override
        public BoundingBoxE6 createFromParcel(final Parcel in) {
            return new BoundingBoxE6(in);
        }

        @Override
        public BoundingBoxE6[] newArray(final int size) {
            return new BoundingBoxE6[size];
        }
    };
    
    protected BoundingBoxE6( Parcel in ) {
        super(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel out, final int arg1) {
        super.writeToParcel(out, arg1);
    }

    /**
     * This will copy the values from the passed in box into this object
     *
     * @param toCopy
     *             The bounding box to copy the values from into this object
     * @since NW SDK 1.1.15.4
     */
    public void copy( BoundingBoxE6 toCopy )
    {
        this.left = toCopy.left;
        this.right = toCopy.right;
        this.top = toCopy.top;
        this.bottom = toCopy.bottom;
        this.empty = toCopy.empty;
    }
}
