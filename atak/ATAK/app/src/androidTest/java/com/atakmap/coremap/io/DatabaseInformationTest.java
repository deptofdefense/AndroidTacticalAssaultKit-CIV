
package com.atakmap.coremap.io;

import android.net.Uri;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatabaseInformationTest {
    @Test
    public void uriRoundTrip() {
        final File file = new File("/path/to/some/file");
        final Uri uri = Uri.fromFile(file);
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertEquals(uri, info.uri);
    }

    @Test
    public void optionsRoundTrip() {
        final File file = new File("/path/to/some/file");
        final Uri uri = Uri.fromFile(file);
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertEquals(options, info.options);
    }

    @Test
    public void memoryUri() {
        final Uri uri = Uri.parse("memory://");
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertTrue(DatabaseInformation.isMemoryDatabase(info));
    }

    @Test
    public void memoryUriWithPath() {
        final Uri uri = Uri.parse("memory://path");
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertTrue(DatabaseInformation.isMemoryDatabase(info));
    }

    @Test
    public void fileUriIsNotMemory() {
        final File file = new File("/path/to/some/file");
        final Uri uri = Uri.fromFile(file);
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertFalse(DatabaseInformation.isMemoryDatabase(info));
    }

    @Test
    public void arbitraryUriIsNotMemory() {
        final Uri uri = Uri.parse("foo://something");
        final int options = 123456;

        DatabaseInformation info = new DatabaseInformation(uri, options);

        assertFalse(DatabaseInformation.isMemoryDatabase(info));
    }
}
