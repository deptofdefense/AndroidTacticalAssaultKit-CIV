
package com.atakmap.android.location;

import org.junit.Test;

import static org.junit.Assert.*;

public class AlphaBetaFilterTest {

    @Test
    public void simpleTest() {
        AlphaBetaFilter abf = new AlphaBetaFilter(.1, .2);
        abf.reset(0);
        abf.update(10, 1, 1);
        abf.update(12, 2, 1);
        abf.update(15, 3, 1);
        abf.update(11, 2, 1);
        abf.update(16, 3, 1);
        abf.update(11, 1, 1);
        assertTrue(Double.compare(abf.getEstimate(), 13.537712) == 0);

    }

    @Test
    public void simpleTest2() {
        AlphaBetaFilter abf = new AlphaBetaFilter(.1, .2);
        abf.reset(0);
        abf.update(10, 1, 1);
        abf.update(12, 2, 1);
        abf.update(15, 3, 1);
        abf.update(11, 2, 1);
        abf.update(16, 3, 1);
        abf.update(11, 1, 1);
        abf.update(Double.NaN, 1, 1);
        assertTrue(Double.compare(abf.getEstimate(), 13.537712) == 0);
    }

}
