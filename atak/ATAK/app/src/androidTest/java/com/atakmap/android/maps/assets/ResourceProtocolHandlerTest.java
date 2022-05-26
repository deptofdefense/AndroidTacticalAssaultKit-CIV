
package com.atakmap.android.maps.assets;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.atakmap.app.R;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class ResourceProtocolHandlerTest {
    @Test
    public void validResource() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler resources = new ResourceProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = resources
                .handleURI("android.resource://com.atakmap.app.civ/"
                        + R.drawable.alpha_sort)) {
            Assert.assertNotNull(strm);
            Assert.assertNotNull(strm.inputStream);
        }
    }

    @Test
    public void invalidResource() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler resources = new ResourceProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = resources
                .handleURI("android.resource://com.atakmap.app.civ/invalid")) {
            Assert.assertNull(strm);
        }
    }

    @Test
    public void invalidPackage() throws IOException {
        Context ctx = ApplicationProvider.getApplicationContext();

        ProtocolHandler resources = new ResourceProtocolHandler(ctx);
        try (UriFactory.OpenResult strm = resources
                .handleURI("android.resource://invalid.package/"
                        + R.drawable.alpha_sort)) {
            Assert.assertNull(strm);
        }
    }
}
