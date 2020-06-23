package transapps.geom;

import android.graphics.Point;

/**
 * Default (unprojected) projection.  This projection assumes that model coordinates == screen coordinates
 * == view coordinates == intermediate coordinates 
 * 
 * @author mriley
 */
public class SimpleProjection implements Projection {
    
    @Override
    public Point toPixels(int viewRelativeX, int viewRelativeY, Point out) {
        if(out == null){
            out = new Point();
        }
        out.set(viewRelativeX, viewRelativeY);        
        return out;
    }
    
    @Override
    public Point toPixelsProjected(Coordinate in, Point out) {
        if(out == null){
            out = new Point();
        }
        return toPixels(in, out);
    }

    @Override
    public Point fromPixelsProjected(Point in, Point out) {
        if(out == null){
            out = new Point();
        }
        out.set(in.x, in.y);
        return out;
    }
    
    @Override
    public Point toPixelsTranslated(Point in, Point out) {
        if(out == null){
            out = new Point();
        }
        out.set(in.x, in.y);
        return out;
    }
    
    @Override
    public <T extends Coordinate> T fromPixels(int x, int y, T out) {
        out.setX(x);
        out.setY(y);
        return out;
    }
    
    @Override
    public Point toPixels(Coordinate in, Point out) {
        if(out == null){
            out = new Point();
        }
        out.x = in.getX();
        out.y = in.getY();
        return out;
    }
    
    @Override
    public float distanceToMaxPixels(float modelDistance) {
        return modelDistance;
    }
}
