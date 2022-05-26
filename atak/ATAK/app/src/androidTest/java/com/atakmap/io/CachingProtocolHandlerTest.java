
package com.atakmap.io;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CachingProtocolHandlerTest {
    @Test(expected = RuntimeException.class)
    public void null_source_provider_throws() throws IOException {
        try (TempFile dir = new TempFile(true)) {
            ProtocolHandler handler = new CachingProtocolHandler(null,
                    dir.file, 0);
            fail();
        }
    }

    @Test(expected = RuntimeException.class)
    public void provider_null_cache_file_throws() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
        final String uri = "mock://CachingProtocolHandlerTest/provider_roundtrip_known_length";
        MockProtocolHandler mock = new MockProtocolHandler(uri, data, true);
        ProtocolHandler handler = new CachingProtocolHandler(mock, null, 0);
    }

    @Test
    public void provider_invalid_uri_returns_null() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
        final String uri = "mock://CachingProtocolHandlerTest/provider_roundtrip_known_length";
        MockProtocolHandler mock = new MockProtocolHandler(uri, data, true);
        try (TempFile dir = new TempFile(true)) {
            ProtocolHandler handler = new CachingProtocolHandler(mock,
                    dir.file, 0);

            try (UriFactory.OpenResult result = handler
                    .handleURI("invalid://invaliduri")) {
                assertNull(result);
            }
        }
    }

    @Test
    public void provider_roundtrip_known_length() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
        final String uri = "mock://CachingProtocolHandlerTest/provider_roundtrip_known_length";
        MockProtocolHandler mock = new MockProtocolHandler(uri, data, true);
        try (TempFile dir = new TempFile(true)) {
            ProtocolHandler handler = new CachingProtocolHandler(mock,
                    dir.file, 0);

            final long reportedLength = handler.getContentLength(uri);
            assertEquals(data.length, reportedLength);

            try (UriFactory.OpenResult result = handler.handleURI(uri)) {
                assertNotNull(result);
                assertEquals(reportedLength, result.contentLength);

                try (ByteArrayOutputStream content = new ByteArrayOutputStream(
                        result.contentLength > 0 ? (int) result.contentLength
                                : 8192)) {
                    FileSystemUtils.copyStream(result.inputStream, false,
                            content, false);
                    assertEquals(data.length, content.size());
                    assertTrue(Arrays.equals(data, content.toByteArray()));
                }
            }
        }
    }

    @Test
    public void provider_roundtrip_unknown_length() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
        final String uri = "mock://CachingProtocolHandlerTest/provider_roundtrip_unknown_length";
        MockProtocolHandler mock = new MockProtocolHandler(uri, data, false);
        try (TempFile dir = new TempFile(true)) {
            ProtocolHandler handler = new CachingProtocolHandler(mock,
                    dir.file, 0);

            final long reportedLength = handler.getContentLength(uri);
            assertEquals(0, reportedLength);

            try (UriFactory.OpenResult result = handler.handleURI(uri)) {
                assertNotNull(result);

                try (ByteArrayOutputStream content = new ByteArrayOutputStream(
                        result.contentLength > 0 ? (int) result.contentLength
                                : 8192)) {
                    FileSystemUtils.copyStream(result.inputStream, false,
                            content, false);
                    assertEquals(data.length, content.size());
                    assertTrue(Arrays.equals(data, content.toByteArray()));
                }
            }
        }
    }

    @Test
    public void provider_retrieve_from_cache() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
        final String uri = "mock://CachingProtocolHandlerTest/provider_roundtrip_unknown_length";
        MockProtocolHandler mock = new MockProtocolHandler(uri, data, false);
        try (TempFile dir = new TempFile(true)) {
            ProtocolHandler handler = new CachingProtocolHandler(mock,
                    dir.file, 0);

            // do the initial cache
            try (UriFactory.OpenResult result = handler.handleURI(uri)) {
                assertNotNull(result);
                try (ByteArrayOutputStream content = new ByteArrayOutputStream(
                        result.contentLength > 0 ? (int) result.contentLength
                                : 8192)) {
                    FileSystemUtils.copyStream(result.inputStream, false,
                            content, false);
                    assertEquals(data.length, content.size());
                    assertArrayEquals(data, content.toByteArray());
                }
            }

            // disable reads from the underlying
            mock.setSimulateFailure(true);
            // confirm underlying is not producing output
            try (UriFactory.OpenResult result = mock.handleURI(uri)) {
                assertNull(result);
            }

            // confirm good result from caching handler
            try (UriFactory.OpenResult result = handler.handleURI(uri)) {
                assertNotNull(result);
                try (ByteArrayOutputStream content = new ByteArrayOutputStream(
                        result.contentLength > 0 ? (int) result.contentLength
                                : 8192)) {
                    FileSystemUtils.copyStream(result.inputStream, false,
                            content, false);
                    assertEquals(data.length, content.size());
                    assertArrayEquals(data, content.toByteArray());
                }
            }
        }
    }

    final static class TempFile implements AutoCloseable {
        final File file;

        public TempFile(boolean directory) throws IOException {
            file = File.createTempFile("tmp", "");
            if (directory) {
                file.delete();
                IOProviderFactory.mkdir(file);
            }
        }

        private static void delete(File file) {
            if (IOProviderFactory.isDirectory(file)) {
                File[] children = IOProviderFactory.listFiles(file);
                if (children != null)
                    for (File c : children)
                        delete(c);
            }
            file.delete();
        }

        @Override
        public void close() {
            delete(this.file);
        }
    }
}
