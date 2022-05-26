
package com.atakmap.app;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicStartStopTest {

    @Rule
    public ActivityTestRule<ATAKActivity> mActivityRule = new ActivityTestRule<>(
            ATAKActivity.class,
            true,
            false);

    private void sleep(long millis) {

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void runBasicTest() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(
                ApplicationProvider.getApplicationContext());
        pref.edit().putBoolean("callSystemExit", false).apply();

        mActivityRule.launchActivity(new Intent());

        sleep(2000);

        ViewInteraction imageView = onView(
                allOf(withId(R.id.tak_nav_menu_button),
                        childAtPosition(
                                allOf(withId(R.id.atak_app_nav),
                                        childAtPosition(
                                                withId(R.id.map_parent),
                                                1)),
                                0),
                        isDisplayed()));
        imageView.perform(click());

        DataInteraction loadoutAllToolsVH = onData(anything())
                .inAdapterView(allOf(withId(R.id.toolbar_list),
                        withContentDescription("toolbars"),
                        childAtPosition(
                                withId(R.id.nav_stack_container),
                                0)))
                .atPosition(0);
        loadoutAllToolsVH.perform(click());

        DataInteraction navSettingsGridVH = onData(anything())
                .inAdapterView(allOf(withId(R.id.settings_list),
                        childAtPosition(
                                withClassName(
                                        is("android.widget.LinearLayout")),
                                1)))
                .atPosition(33);
        navSettingsGridVH.perform(click());

        ViewInteraction button = onView(
                allOf(withId(android.R.id.button1), withText("Yes"),
                        childAtPosition(
                                childAtPosition(
                                        withClassName(is(
                                                "android.widget.LinearLayout")),
                                        0),
                                2),
                        isDisplayed()));
        button.perform(click());
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup
                        && parentMatcher.matches(parent)
                        && view.equals(
                                ((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
