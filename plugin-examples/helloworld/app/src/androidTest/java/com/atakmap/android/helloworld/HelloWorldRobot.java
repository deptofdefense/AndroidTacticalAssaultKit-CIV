package com.atakmap.android.helloworld;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.test.helpers.helper_versions.HelperFactory;
import com.atakmap.android.test.helpers.helper_versions.HelperFunctions;

import java.util.concurrent.Callable;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HelloWorldRobot {
    private static final HelperFunctions HELPER = HelperFactory.getHelper();
    private final Context appContext = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();

    public static void installPlugin() {
        HELPER.installPlugin("Hello World Tool");
    }

    public HelloWorldRobot openToolFromOverflow() {
        HELPER.pressButtonInOverflow("Hello World Tool");
        return this;
    }

    public HelloWorldRobot pressISSButton() {
        onView(withId(R.id.issLocation)).perform(scrollTo(), click());
        return this;
    }

    public HelloWorldRobot pressEmergencyButton() {
        onView(withId(R.id.emergency)).perform(scrollTo(), click());
        return this;
    }

    public HelloWorldRobot pressNoEmergencyButton() {
        onView(withId(R.id.no_emergency)).perform(scrollTo(), click());
        return this;
    }

    public HelloWorldRobot pressAddAnAircraftButton() {
        onView(withId(R.id.addAnAircraft)).perform(scrollTo(), click());
        return this;
    }

    public HelloWorldRobot verifyEmergencyMarkerExists() {
        assertNotNull("Could not find emergency marker", HELPER.getMarkerOfType("b-a-o-tbl"));
        return this;
    }

    public HelloWorldRobot verifyNoEmergencyMarkerExists() {
        assertNull("Found an emergency marker", HELPER.getMarkerOfType("b-a-o-tbl"));
        return this;
    }

    public HelloWorldRobot verifyAircraftMarkerWithNameExists(String name) {
        Marker marker = HELPER.getMarkerOfType("a-f-A");
        assertNotNull("Could not find aircraft marker", marker);
        assertEquals("Marker name does not match", marker.getTitle(), name);
        return this;
    }

    public HelloWorldRobot verifyISSExists() {
        HELPER.nullWait(new Callable<Marker>() {
            @Override
            public Marker call() {
                return HELPER.getMarkerOfUid("iss-unique-identifier");
            }
        }, 3000);
        return this;
    }

    public HelloWorldRobot pressAircraftDetailsRadialMenuButton() {
        MapAssets assets = new MapAssets(appContext);
        HELPER.pressRadialButton(HELPER.getMarkerOfType("a-f-A"), "showdetails", assets);
        return this;
    }

    public HelloWorldRobot verifyMarkerDetailsName(String name) {
        onView(withId(appContext.getResources().getIdentifier(
                        "cotInfoNameEdit",
                        "id",
                        "com.atakmap.app")))
                .check(matches(withText(name)));
        return this;
    }
}
