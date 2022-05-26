
package com.atakmap.android.maps.assets;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class AssetProtocolHandlerTest {
    @Test
    public void testNoHost() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler assets = new AssetProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = assets
                .handleURI("asset://icons/compass.png")) {
            Assert.assertNotNull(strm);
            Assert.assertNotNull(strm);
        }
    }

    @Test
    public void testEmptyHost() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler assets = new AssetProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = assets
                .handleURI("asset:///icons/compass.png")) {
            Assert.assertNotNull(strm);
            Assert.assertNotNull(strm);
        }
    }

    @Test
    public void invalidAsset() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler assets = new AssetProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = assets
                .handleURI("asset:///asset/does/not.exist")) {
            Assert.assertNull(strm);
        }
    }
}
