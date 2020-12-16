package com.atakmap.android.helloworld;

import android.content.Context;
import android.preference.PreferenceManager;

import android.view.View;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.test.helpers.ATAKTestClass;
import com.atakmap.android.test.helpers.DrawableMatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Callable;

/**
 * Basic class to begin testing on the ATAK Map. This class is for generic testing and
 * testing of common code helper functions.
 */
public class MapTest extends ATAKTestClass {
    private final Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    // TODO: move these to ATAKTestClass if they're useful to generalize, but may need to be tailored a bit for each test class
    @BeforeClass
    public static void setupBeforeAllTests() throws Exception {
      helper.deleteAllMarkers();
    }

    @Before
    public void setupBeforeEachTest() {
        helper.panZoomTo(0.0, new GeoPoint(.5, .5));
    }

    @After
    public void cleanupAfterEachTests() throws InterruptedException {
        helper.pressBackTimes(5);
        helper.deleteAllMarkers();
    }


    // TODO: move this to a helper class?
    public static Matcher<View> withDrawable(final int resourceId) {
        return new DrawableMatcher(resourceId);
    }


    @Test
    public void useAppContext() throws Exception {
        Assert.assertEquals("com.atakmap.app", appContext.getPackageName());
    }

    @Test
    public void openOverflowMenu() throws Exception {
        Espresso.openActionBarOverflowOrOptionsMenu(appContext);
        helper.pressBackTimes(1);
    }


    @Test
    public void placeMarker() throws Exception {
        // Short-term fix for first hint dialog, just disable that hint so the rest of the test works:
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putBoolean("atak.hint.iconset", false)
            .apply();
            
        // Open marker palette
        helper.pressButtonFromLayoutManager("Point Dropper");
        
        // Close hint dialog that appears the first time this tool is run
        //TODO: fix the next line -- I think Espresso is probably getting hung up on the animation the Point Dropper does while the hint dialog is open? Long-term, might need to have a way to either disable the animation or animate in a way Espresso can deal with.
        //onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            
        // Select friendly marker button
        Espresso.onView(ViewMatchers.withId(R.id.enterLocationTypeFriendly)).perform(ViewActions.click());
        
        // Close hint dialog that appears the first time this button is pressed
        helper.closeHelperDialog();

        // Place a marker at 0,0
        helper.pressMapLocationMinScale(new GeoPoint(0,0));

        // Verify newly placed marker exists
        Assert.assertNotNull(helper.nullWait(new Callable<Marker>() {
            @Override
            public Marker call() {
                return helper.getMarkerOfType("a-f-G");
            }
        }, 3000));
    }

    @Test
    public void selectRadialMenuButton() throws Exception {
        helper.pressButtonFromLayoutManager("Red X Tool");
        MapAssets assets = new MapAssets(appContext);
        //see assets\menus\redx_menu.xml for all the actions you can perform for red x radial
        helper.pressMarkerNameOnMap("Red X");
        helper.pressRadialButton(helper.getMarkerOfName("Red X"), "dropfriendly", assets);
    }
}
