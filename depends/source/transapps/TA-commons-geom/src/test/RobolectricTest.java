//package transapps.geom;
//
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//import org.robolectric.annotation.Config;
//
//@RunWith(RobolectricTestRunner.class)
//@Config(manifest=Config.NONE)
//public class RobolectricTest {
//
//    @Test
//    public void testDistanceE6() {testDistance(new GeoFactory(), 78);}
//    @Test
//    public void testDistanceSimple() {testDistance(new SimpleFactory(), 707);}
//    @Test
//    public void testBearingE6() {testBearing(new GeoFactory(), 45);}
//    @Test
//    public void testBearingSimple() {testBearing(new SimpleFactory(), 45);}
//
//    public void testDistance( Factory f, int expectedDistance ) {
//        Coordinate p1 = f.createPoint(0, 0);
//        Coordinate p2 = f.createPoint(500, 500);
//        Assert.assertEquals(p1 + ".distanceTo("+p2+")", expectedDistance,  p1.distanceTo(p2));
//    }
//
//    public void testBearing( Factory f, float expectedBearing ) {
//        Coordinate p1 = f.createPoint(0, 0);
//        Coordinate p2 = f.createPoint(500, 500);
//        Assert.assertEquals(p1 + ".distanceTo("+p2+")", expectedBearing,  p1.bearingTo(p2), 0);
//    }
//}
