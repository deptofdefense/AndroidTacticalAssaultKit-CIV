
package com.atakmap.android.navigation.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NavButtonModelTest extends ATAKInstrumentedTest {

    @Test
    public void createTakButtonModel() {
        NavButtonModel tbm = new NavButtonModel("reference", null);
        assertEquals("reference", tbm.getReference());
        assertNull(tbm.getImage());
        assertNull(tbm.getAction());
    }

    @Test
    public void languageComparison() {
        NavButtonModel quit = new NavButtonModel("reference", "Quit", null);
        NavButtonModel quit_french = new NavButtonModel("reference", "Quitter",
                null);

        assertEquals(quit, quit_french);

    }

}
