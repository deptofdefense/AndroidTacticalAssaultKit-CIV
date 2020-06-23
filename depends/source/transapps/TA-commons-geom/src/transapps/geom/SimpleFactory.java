package transapps.geom;

public class SimpleFactory implements Factory {

    @Override
    public Coordinate createPoint() {
        return new Coord();
    }

    @Override
    public Coordinate createPoint(int x, int y) {
        return new Coord(x, y);
    }

    @Override
    public Coordinate createPoint(double x, double y) {
        return createPoint((int) x, (int) y);
    }

    @Override
    public Box createBox() {
        return new Rect();
    }

    @Override
    public Box createBox(Coordinate topLeft, Coordinate bottomRight) {
        return new Rect(topLeft.getX(), topLeft.getY(), bottomRight.getX(), bottomRight.getY());
    }

    @Override
    public Box createBox(int minx, int miny, int maxx, int maxy) {
        return new Rect(minx, miny, maxx, maxy);
    }
}
