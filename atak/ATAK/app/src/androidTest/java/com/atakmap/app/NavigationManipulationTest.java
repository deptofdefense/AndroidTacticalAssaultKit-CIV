
package com.atakmap.app;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.atakmap.android.navigation.views.loadout.LoadoutToolsGridVM;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NavigationManipulationTest {

    @Rule
    public ActivityTestRule<ATAKActivity> mActivityTestRule = new ActivityTestRule<>(
            ATAKActivity.class);

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void pressMenuButton() {
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
    }

    @Test
    public void aTAKActivityTest1() {

        sleep(2000);
        pressMenuButton();

        // utilize the default navigation layout
        ViewInteraction imageButton = onView(
                allOf(childAtPosition(
                        allOf(withId(R.id.nav_stack_toolbar_buttons),
                                childAtPosition(
                                        withId(R.id.nav_stack_toolbar),
                                        1)),
                        2),
                        isDisplayed()));
        imageButton.perform(click());

        DataInteraction loadoutListVH = onData(anything())
                .inAdapterView(allOf(withId(R.id.toolbar_list),
                        withContentDescription("toolbars"),
                        childAtPosition(
                                withId(R.id.nav_stack_container),
                                0)))
                .atPosition(1);
        loadoutListVH.perform(click());

        pressBack();

        sleep(2000);
        pressMenuButton();

        // Example for click on a tool by Position
        DataInteraction loadoutToolsGridVH = onData(anything())
                .inAdapterView(allOf(withId(R.id.tools_list),
                        childAtPosition(
                                withClassName(
                                        is("android.widget.LinearLayout")),
                                1)))
                .atPosition(27);
        loadoutToolsGridVH.perform(click());

        pressBack();

        pressMenuButton();

        // Example for click on a tool by Name
        loadoutToolsGridVH = onData(withName("Settings"))
                .inAdapterView(allOf(withId(R.id.tools_list),
                        childAtPosition(
                                withClassName(
                                        is("android.widget.LinearLayout")),
                                1)))
                .atPosition(0);
        loadoutToolsGridVH.perform(click());

        pressBack();

    }

    public static Matcher withName(String name) {
        return new TypeSafeMatcher<LoadoutToolsGridVM>() {
            @Override
            public boolean matchesSafely(LoadoutToolsGridVM l) {

                return name.equals(l.getName());
            }

            @Override
            public void describeTo(Description description) {
            }
        };
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
