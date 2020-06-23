package transapps.geom;

/**
 * Based on context, create a point
 * 
 * @author mriley
 */
public interface Factory {

    /**
     * @return a new model point
     */
    Coordinate createPoint();
    
    /**
     * @return a new model point
     */
    Coordinate createPoint(int x, int y);
    
    /**
     * @return a new model point
     */
    Coordinate createPoint(double x, double y);

    /**
     * @return a new model box
     */
    Box createBox();
    
    /**
     * @return a new model box
     */
    Box createBox(Coordinate topLeft, Coordinate bottomRight);
    
    /**
     * @return a new model box
     */
    Box createBox(int minx, int miny, int maxx, int maxy);
}
