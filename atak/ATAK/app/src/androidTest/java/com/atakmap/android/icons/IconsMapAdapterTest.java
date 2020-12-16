
package com.atakmap.android.icons;

import android.content.Context;
import android.preference.PreferenceManager;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class IconsMapAdapterTest extends ATAKInstrumentedTest {
    @Test
    public void IconsMapAdapter_initializeUserIconDb() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        UserIconDatabase db = IconsMapAdapter.initializeUserIconDB(appContext,
                PreferenceManager.getDefaultSharedPreferences(appContext));
        assertNotNull(db);
    }
}
