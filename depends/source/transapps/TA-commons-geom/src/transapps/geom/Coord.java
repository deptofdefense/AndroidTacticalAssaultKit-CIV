package transapps.geom;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Implementation of the Coordinate interface using 3 integers for x, y and z and flat
 * surface geometry calculations for bearing and distance calculations between Coords.
 *
 */
public class Coord implements Coordinate {
    
    protected int x;
    protected int y;
    protected int z;
    
    public Coord() {
    }
    
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }
    
    @Override
    public int getZ() {
        return z;
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }
    
    @Override
    public void setZ(int z) {
        this.z = z;
    }

    @Override
    public void setCoords(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the distance in meters between this Coord and the one passed in.
     * @param point  Coord to calculate distance to.
     * @return   distance in meters between the two points.
     */
    @Override
    public int distanceTo(Coordinate point) {
        int x2 = point.getX();
        int y2 = point.getY();
        return (int) Math.sqrt((x2 - x)*(x2 - x)+(y2-y)*(y2-y));
    }

    /**
     * Calculate the angle of bearing between this Coord and the one passed in.
     *
     * @param point Coord to calculate bearing to.
     * @return bearing in degrees from this Coord to the one passed in.
     */
    @Override
    public float bearingTo(Coordinate point) {
        int x2 = point.getX();
        int y2 = point.getY();
        return (float) Math.toDegrees(Math.atan2(y2-y, x2-x));
    }

    /**
     * Given a distance and a bearing return the new Coord from this Coord point.
     *
     * @param aDistanceInMeters distance in meters from this Coord to the new Coord.
     * @param aBearingInDegrees bearing from this Coord to the new Coord point.
     * @return Coord that is aDistanceInMeters and aBearingInDegrees from this Coord
     */
    @Override
    public Coord destinationPoint(double aDistanceInMeters,
            float aBearingInDegrees) {
        double cos = Math.cos(Math.toRadians(aBearingInDegrees + 180));
        double sin = -Math.sin(Math.toRadians(aBearingInDegrees + 180));
        double x2 = 0;
        double y2 = 0;
        if(!Double.isNaN(cos) && cos != 0 && Math.abs(cos) >= 1e-6){
            x2 = aDistanceInMeters/cos;
        }
        if(!Double.isNaN(sin) && sin != 0 && Math.abs(sin) >= 1e-6){
            y2 = aDistanceInMeters/sin;
        }
        double x1 = getX() + y2;
        double y1 = getY() + x2; 
        int xCoord = (int) Math.round(x1);
        int yCoord = (int) Math.round(y1);
        return new Coord(xCoord, yCoord);
    }
    
    // ===========================================================
    // 
    // ===========================================================

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        result = prime * result + z;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Coord other = (Coord) obj;
        if (x != other.x)
            return false;
        if (y != other.y)
            return false;
        if (z != other.z)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return new StringBuilder().append(x).append(",").append(y).append(",").append(z)
        .toString();
    }
    
    @Override
    public String toDoubleString() {
        return toString();
    }
    
    // ===========================================================
    // Parcelable
    // ===========================================================


    public static final Parcelable.Creator<Coord> CREATOR = new Parcelable.Creator<Coord>() {
        @Override
        public Coord createFromParcel(final Parcel in) {
            return new Coord(in);
        }

        @Override
        public Coord[] newArray(final int size) {
            return new Coord[size];
        }
    };

    protected Coord( Parcel in ) {
        x = in.readInt();
        y = in.readInt();
        z = in.readInt();
    }
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel out, final int arg1) {
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
    }

}
