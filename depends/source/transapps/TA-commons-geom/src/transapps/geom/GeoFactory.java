package transapps.geom;


/**
 *
 * Implementation of the {@link Factory} interface that returns GeoPoint instances.
 *
 */

public class GeoFactory implements Factory {
    @Override
    public Coordinate createPoint() {
        return new GeoPoint();
    }

    @Override
    public Coordinate createPoint(int x, int y) {
        return new GeoPoint(y, x);
    }
    
    @Override
    public Coordinate createPoint(double x, double y) {
        return new GeoPoint(y, x);
    }

    @Override
    public Box createBox() {
        return new BoundingBoxE6();
    }

    @Override
    public Box createBox(Coordinate topLeft, Coordinate bottomRight) {
        return new BoundingBoxE6(topLeft.getY(), bottomRight.getX(), bottomRight.getY(), topLeft.getX());
    }

    @Override
    public Box createBox(int minx, int miny, int maxx, int maxy) {
        return new BoundingBoxE6(maxy, maxx, miny, minx);
    }
}
