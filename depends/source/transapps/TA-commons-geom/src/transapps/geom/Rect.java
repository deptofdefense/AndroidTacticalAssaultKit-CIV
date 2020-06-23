// Created by plusminus on 19:06:38 - 25.09.2008
package transapps.geom;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 
 * @author Nicolas Gramlich
 */
public class Rect implements Box {

    // ===========================================================
    // Fields
    // ===========================================================

    protected int top;
    protected int bottom;
    protected int right;
    protected int left;
    protected boolean empty;

    // ===========================================================
    // Constructors
    // ===========================================================

    public Rect() {
        setEmpty();
    }
    
    public Rect(final int left, final int top, final int right, final int bottom) {
        setCoords(left, top, right, bottom);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================
    
    @Override
    public void setEmpty() {
        setCoords(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        this.empty = true;
    }
    
    @Override
    public boolean isEmpty() {
        return empty;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Coordinate> T getCenter(T reuse) {
        reuse = (T) (reuse == null ? new Coord() : reuse);
        reuse.setCoords(getCenterX(), getCenterY());
        return reuse;
    }
    
    @Override
    public int getCenterX() {
        return (right + left) / 2;
    }
    
    @Override
    public int getCenterY() {
        return (bottom + top) / 2;
    }

    @Override
    public int getTop() {
        return top;
    }
    
    @Override
    public int getBottom() {
        return bottom;
    }
    
    @Override
    public int getRight() {
        return right;
    }
    
    @Override
    public int getLeft() {
        return left;
    }
    
    @Override
    public void setRight(int maxx) {
        this.right = maxx;
        empty = false;
    }
    
    @Override
    public void setBottom(int maxy) {
        this.bottom = maxy;
        empty = false;
    }
    
    @Override
    public void setLeft(int minx) {
        this.left = minx;
        empty = false;
    }
    
    @Override
    public void setTop(int miny) {
        this.top = miny;
        empty = false;
    }
    
    @Override
    public int getYSpan() {
        return Math.abs(this.bottom - this.top);
    }

    @Override
    public int getXSpan() {
        return Math.abs(this.right - this.left);
    }
    
    @Override
    public void setCoords(final int left, final int top, final int right, final int bottom) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
        this.empty = false;
    }
    
    @Override
    public void expand( Coordinate point ) {
        int y = point.getY();
        int x = point.getX();
        if( empty ) {
            top = bottom = y;
            left = right = x;
        } else {
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
        empty = false;
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public String toString() {
        return new StringBuffer().append("T:").append(top).append("; R:")
                .append(right).append("; B:").append(bottom).append("; L:")
                .append(left).toString();
    }

    // ===========================================================
    // Methods
    // ===========================================================

    public static Rect fromPoints(final Iterable<? extends Coordinate> partialPolyLine) {
        Rect box = new Rect();
        for (final Coordinate gp : partialPolyLine) {
            box.expand(gp);
        }
        return box;
    }

    @Override
    public boolean contains(final Coordinate point) {
        return contains(point.getX(), point.getY());
    }

    public boolean contains(final int x, final int y) {
        return ((y >= top) && (y <= bottom)) && ((x <= right) && (x >= left));
    }
    
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

        return left1 < right2 && left2 < right1 && top1 < bottom2 && top2 < bottom1;
    }
    
    @Override
    public List<? extends Coordinate> getCorners() {
        List<Coordinate> output = new ArrayList<Coordinate>(4);
        output.add(new Coord(left, top));
        output.add(new Coord(right, top));
        output.add(new Coord(right, bottom));
        output.add(new Coord(left, bottom));
        return output;
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    // ===========================================================
    // Parcelable
    // ===========================================================

    public static final Parcelable.Creator<Rect> CREATOR = new Parcelable.Creator<Rect>() {
        @Override
        public Rect createFromParcel(final Parcel in) {
            return new Rect(in);
        }

        @Override
        public Rect[] newArray(final int size) {
            return new Rect[size];
        }
    };
    
    protected Rect( Parcel in ) {
        this(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        this.empty = in.readByte() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel out, final int arg1) {
        out.writeInt(this.left);
        out.writeInt(this.top);
        out.writeInt(this.right);
        out.writeInt(this.bottom);
        out.writeByte((byte) (this.empty ? 1 : 0));
    }
}
