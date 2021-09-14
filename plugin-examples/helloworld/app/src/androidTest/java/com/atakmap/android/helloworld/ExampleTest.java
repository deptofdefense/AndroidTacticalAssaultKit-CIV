
package com.atakmap.android.helloworld;

import com.atakmap.android.test.helpers.ATAKTestClass;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.atakmap.android.test.helpers.ClassLoaderReplacer.fixClassLoaderForClass;
import static com.atakmap.android.test.helpers.ClassLoaderReplacer.restoreLoader;

import android.os.Build;

public class ExampleTest extends ATAKTestClass {
    private final HelloWorldRobot helloWorldRobot = new HelloWorldRobot();

    @BeforeClass
    public static void setupPlugin() throws Exception {
        HelloWorldRobot.installPlugin();
        fixClassLoaderForClass(ExampleTest.class,
                "com.atakmap.android.helloworld.plugin");
    }

    @AfterClass
    public static void restoreClassLoader() throws Exception {
        restoreLoader(ExampleTest.class);
    }

    @After
    public void cleanupAfterEachTest() {
        // Code to run between each test, to attempt to reset the state. Adjust as needed for your tests.
        helper.pressBackTimes(5);
        helper.deleteAllMarkers();
    }

    @Test
    public void testEmergencyButtons() {
        helloWorldRobot
                .openToolFromOverflow()
                .pressEmergencyButton()
                .verifyEmergencyMarkerExists()
                .pressNoEmergencyButton()
                .verifyNoEmergencyMarkerExists();
    }

    @Test
    public void testAddAircraftButton() {
        String expectedName = "SNF";
        helloWorldRobot
                .openToolFromOverflow()
                .pressAddAnAircraftButton()
                .verifyAircraftMarkerWithNameExists(expectedName)
                .pressAircraftDetailsRadialMenuButton()
                .verifyMarkerDetailsName(expectedName);
    }

    @Test
    public void testTrackSpaceStation() {

        // The current ISS plotting site uses cleartext http connection and offers no https ability.
        // Since this is not allowed on Android 9 or higher, do not test this capability.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return;

        helloWorldRobot
                .openToolFromOverflow()
                .pressISSButton()
                .verifyISSExists();
    }
}
