
package com.atakmap.android.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class UserIconTest extends ATAKInstrumentedTest {
    @Test
    public void UserIcon_GetIconBitmap_random_icon_not_null() {
        Context appContext = ApplicationProvider.getApplicationContext();

        UserIconDatabase db = IconsMapAdapter.initializeUserIconDB(appContext,
                PreferenceManager.getDefaultSharedPreferences(appContext));
        assertNotNull(db);
        UserIcon icon = IconModuleUtils.randomIcon(db);
        assertNotNull(icon);
        Bitmap bitmap = null;
        try {
            bitmap = UserIcon.GetIconBitmap(
                    IconModuleUtils.getIconUri(appContext, db, icon),
                    appContext);
            assertNotNull(bitmap);
        } finally {
            if (bitmap != null)
                bitmap.recycle();
        }
    }

    @Test
    public void UserIcon_GetIconBitmap_bad_path_is_null() {
        Context appContext = ApplicationProvider.getApplicationContext();

        Bitmap bitmap = null;
        try {
            bitmap = UserIcon.GetIconBitmap("asdasdasfsadsadasd", appContext);
            assertNull(bitmap);
        } finally {
            if (bitmap != null)
                bitmap.recycle();
        }
    }

    @Test
    public void UserIcon_GetIconBitmap_null_path_is_null() {
        Context appContext = ApplicationProvider.getApplicationContext();

        Bitmap bitmap = null;
        try {
            bitmap = UserIcon.GetIconBitmap(null, appContext);
            assertNull(bitmap);
        } finally {
            if (bitmap != null)
                bitmap.recycle();
        }
    }
}
