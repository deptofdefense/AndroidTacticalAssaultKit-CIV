package transapps.geom;

import java.util.List;

import android.os.Parcelable;

/**
 * A box in model space.
 * 
 * For geo space, left, top, right, bottom will be west*1e6, north*1e6, east*1e6, south*1e6 respectively.
 * 
 * @author mriley
 */
public interface Box extends Parcelable {
    
    boolean isEmpty();

    void setEmpty();
    
    <T extends Coordinate> T getCenter( T reuse );
    
    int getCenterX();
    
    int getCenterY();

    int getTop();
    
    int getBottom();
    
    int getRight();
    
    int getLeft();
    
    void setRight(int maxx);
    
    void setBottom(int maxy);
    
    void setLeft(int minx);
    
    void setTop(int miny);

    int getYSpan();

    int getXSpan();
    
    void setCoords(final int left, final int top, final int right, final int bottom);
    
    void expand( Coordinate point );
    
    
    boolean contains( Coordinate coord );
    
    boolean intersects(Box b2);
    
    List<? extends Coordinate> getCorners();
}
