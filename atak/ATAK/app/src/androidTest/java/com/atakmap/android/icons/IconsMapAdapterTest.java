
package com.atakmap.android.icons;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IconsMapAdapterTest extends ATAKInstrumentedTest {
    @Test
    public void IconsMapAdapter_initializeUserIconDb() {
        Context appContext = ApplicationProvider.getApplicationContext();

        UserIconDatabase db = IconsMapAdapter.initializeUserIconDB(appContext,
                PreferenceManager.getDefaultSharedPreferences(appContext));
        assertNotNull(db);
    }
}
