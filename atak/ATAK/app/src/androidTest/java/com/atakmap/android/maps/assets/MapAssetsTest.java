
package com.atakmap.android.maps.assets;

import android.content.Context;
import android.net.Uri;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.app.R;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import androidx.test.core.app.ApplicationProvider;

public class MapAssetsTest extends ATAKInstrumentedTest {
    @Test
    public void testGetAssetInputStream() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        MapAssets assets = new MapAssets(ctx);
        try (InputStream strm = assets
                .getInputStream(Uri.parse("icons/compass.png"))) {
            Assert.assertNotNull(strm);
        }
    }

    @Test
    public void testGetResourceInputStream() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        MapAssets assets = new MapAssets(ctx);
        try (InputStream strm = assets.getInputStream(
                Uri.parse("android.resource://com.atakmap.app.civ/"
                        + R.drawable.alpha_sort))) {
            Assert.assertNotNull(strm);
        }
    }
}
