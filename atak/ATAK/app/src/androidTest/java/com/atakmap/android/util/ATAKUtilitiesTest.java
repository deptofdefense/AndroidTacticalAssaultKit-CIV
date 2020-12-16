
package com.atakmap.android.util;

import android.graphics.Bitmap;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.icons.IconModuleUtils;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class ATAKUtilitiesTest extends ATAKInstrumentedTest {
    @Test
    public void getIconUri_iconset_uri() {
        // use a pre-baked icon set URI (tests parse only)
        String source_uri = "sqlite:///storage/emulated/0/atak/Databases/iconsets.sqlite?query=select bitmap from icons where id=2703";
        Marker m = new Marker(new GeoPoint(37.42227474653952,
                -122.08279537179867, 5.350297899709043),
                UUID.randomUUID().toString());
        m.setType("a-n-G");
        m.setTitle("Animal Issue 1");
        m.setIcon(
                new Icon.Builder().setImageUri(0, source_uri).setColor(0, -1)
                        .setAnchor(16, 16).setSize(-1, -1).build());
        String result_uri = ATAKUtilities.getIconUri(m);
        assertNotNull(result_uri);
        assertEquals(source_uri, result_uri);
    }

    @Test
    public void getIconUri_iconset_bitmap() {
        // retrieve a real, but random iconset URI
        String source_uri = IconModuleUtils.randomIconUri();
        Marker m = new Marker(new GeoPoint(37.42227474653952,
                -122.08279537179867, 5.350297899709043),
                UUID.randomUUID().toString());
        m.setType("a-n-G");
        m.setTitle("Animal Issue 1");
        m.setIcon(
                new Icon.Builder().setImageUri(0, source_uri).setColor(0, -1)
                        .setAnchor(16, 16).setSize(-1, -1).build());
        String result_uri = ATAKUtilities.getIconUri(m);
        assertNotNull(result_uri);
        assertEquals(source_uri, result_uri);
        Bitmap bitmap = null;
        try {
            bitmap = ATAKUtilities.getUriBitmap(
                    InstrumentationRegistry.getTargetContext(), result_uri);
            assertNotNull(bitmap);
        } finally {
            if (bitmap != null)
                bitmap.recycle();
        }
    }
}
