package transapps.geom;

/**
 * Uses a static instance of {@link Factory} to create points.  Before you use this you must first ensure
 * that is called.
 *  
 * @author mriley
 */
public final class GeomFactory implements Factory {
    
    private static final Factory NULL = new Factory() {
        private final void fail() {
            throw new RuntimeException("You MUST ensure that you've callled setFactory before you may any calls");
        }
        @Override public Coordinate createPoint() {fail();return null;}
        @Override public Coordinate createPoint(int x, int y) {fail(); return null;}
        @Override public Coordinate createPoint(double x, double y) {fail(); return null;}
        @Override public Box createBox() {fail();return null;}
        @Override public Box createBox(Coordinate topLeft, Coordinate bottomRight) {fail();return null;}
        @Override public Box createBox(int minx, int miny, int maxx, int maxy) {fail();return null;}
    };
    
    private static final GeomFactory instance = new GeomFactory();
    private static Factory f = NULL;
    
    public static void setInstance(Factory i) {
        GeomFactory.f = i == null ? NULL : i;
    }
    
    public static boolean isSet() {
        return f != NULL; 
    }
    
    public static Factory getInstance() {
        return instance;
    }
    
    private GeomFactory() {
    }

    @Override
    public Coordinate createPoint() {
        return f.createPoint();
    }

    @Override
    public Coordinate createPoint(int x, int y) {
        return f.createPoint(x, y);
    }

    @Override
    public Coordinate createPoint(double x, double y) {
        return f.createPoint(x, y);
    }

    @Override
    public Box createBox() {
        return f.createBox();
    }

    @Override
    public Box createBox(Coordinate topLeft, Coordinate bottomRight) {
        return f.createBox(topLeft, bottomRight);
    }

    @Override
    public Box createBox(int minx, int miny, int maxx, int maxy) {
        return f.createBox(minx, miny, maxx, maxy);
    }
}
