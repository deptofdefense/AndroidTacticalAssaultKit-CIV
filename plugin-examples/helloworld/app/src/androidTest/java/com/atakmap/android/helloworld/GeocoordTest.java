
package com.atakmap.android.helloworld;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.test.helpers.ATAKTestClass;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import static org.junit.Assert.*;

import org.junit.Test;

public class GeocoordTest extends ATAKTestClass {
    private final Context appContext = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();

    @Test
    public void coordTest() {

        double bearing = GeoCalculations.bearingTo(new GeoPoint(0, 100),
                new GeoPoint(100, 100));
        assertTrue(Double.compare(bearing, 0) == 0);
    }

}
