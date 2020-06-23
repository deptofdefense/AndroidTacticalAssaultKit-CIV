//package transapps.geom;
//
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.junit.runners.JUnit4;
//@RunWith(JUnit4.class)
//public class BoxTest {
//
//    public void testIntersect(Factory f) {
//        Box box1 = f.createBox(0, 0, 10, 10);
//        Box box2 = f.createBox(5, 5, 15, 15);
//        Assert.assertTrue(box1 + " does not intersect with " + box2, box1.intersects(box2));
//
//        box1 = f.createBox(0, 0, 10, 10);
//        box2 = f.createBox(11, 11, 15, 15);
//        Assert.assertFalse(box1 + " does intersect with " + box2, box1.intersects(box2));
//    }
//
//    public void testContain(Factory f) {
//        Box box1 = f.createBox(0, 0, 10, 10);
//        for( int i = 0; i <= 10; i++ ) {
//            for( int ii = 0; ii <= 10; ii++ ) {
//                Coordinate newPoint = f.createPoint(i, ii);
//                Assert.assertTrue(box1 + " does not contain " + newPoint, box1.contains(newPoint));
//            }
//        }
//
//        Coordinate newPoint = f.createPoint(11, 11);
//        Assert.assertFalse(box1 + " does intersect with " + newPoint, box1.contains(newPoint));
//    }
//
//    public void testExpand(Factory f) {
//        Box box1 = f.createBox(0, 0, 10, 10);
//        Coordinate newPoint = f.createPoint(11, 11);
//        box1.expand(newPoint);
//        Assert.assertTrue(box1 + " does not contain " + newPoint, box1.contains(newPoint));
//    }
//
//    public void testEmpty(Factory f) {
//        Box box1 = f.createBox();
//        Assert.assertTrue(box1 + " is not empty", box1.isEmpty());
//        box1.expand(f.createPoint(11, 11));
//        Assert.assertFalse(box1 + " is empty", box1.isEmpty());
//    }
//
//    public void testCenter(Factory f) {
//        Box box1 = f.createBox(0,0,10,10);
//        Assert.assertTrue(box1 + " invalid center x", box1.getCenterX() == 5);
//        Assert.assertTrue(box1 + " invalid center y", box1.getCenterY() == 5);
//
//        box1 = f.createBox(-10,-10,10,10);
//        Assert.assertTrue(box1 + " invalid center x", box1.getCenterX() == 0);
//        Assert.assertTrue(box1 + " invalid center y", box1.getCenterY() == 0);
//
//        box1 = f.createBox(-10,-10,0,0);
//        Assert.assertTrue(box1 + " invalid center x", box1.getCenterX() == -5);
//        Assert.assertTrue(box1 + " invalid center y", box1.getCenterY() == -5);
//    }
//
//    @Test
//    public void testBoxCoords() {
//        Box box1 = new GeoFactory().createBox(0, 0, 10, 10);
//        Assert.assertTrue("Geo box top should be > than bottom", box1.getTop() > box1.getBottom());
//        box1 = new SimpleFactory().createBox(0, 0, 10, 10);
//        Assert.assertTrue("Simple box top should be < than bottom", box1.getTop() < box1.getBottom());
//    }
//
//    @Test
//    public void testIntersectCoord() { testIntersect(new SimpleFactory()); }
//    @Test
//    public void testContainCoord() { testContain(new SimpleFactory()); }
//    @Test
//    public void testExpandCoord() { testExpand(new SimpleFactory()); }
//    @Test
//    public void testEmptyCoord() { testEmpty(new SimpleFactory()); }
//    @Test
//    public void testCenterCoord() { testCenter(new SimpleFactory()); }
//    @Test
//    public void testIntersectE6() { testIntersect(new GeoFactory()); }
//    @Test
//    public void testContainE6() { testContain(new GeoFactory()); }
//    @Test
//    public void testExpandE6() { testExpand(new GeoFactory()); }
//    @Test
//    public void testEmptyE6() { testEmpty(new GeoFactory()); }
//    @Test
//    public void testCenterE6() { testCenter(new GeoFactory()); }
//}
