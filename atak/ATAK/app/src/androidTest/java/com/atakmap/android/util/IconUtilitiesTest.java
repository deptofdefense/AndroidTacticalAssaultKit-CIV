
package com.atakmap.android.util;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.app.R;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IconUtilitiesTest extends ATAKInstrumentedTest {
    @Test
    public void get_icon() {
        Context appContext = ApplicationProvider.getApplicationContext();
        Bitmap bmp = IconUtilities.getBitmap(appContext,
                R.drawable.lpt_white_star_drawable);
        String encoded = IconUtilities.encodeBitmap(bmp);
        Assert.assertTrue(encoded != null && encoded.startsWith("base64://"));
        Assert.assertNotNull(IconUtilities.decodeBitmap(encoded));
    }
}
