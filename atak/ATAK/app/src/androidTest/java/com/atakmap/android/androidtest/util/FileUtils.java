
package com.atakmap.android.androidtest.util;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.util.Collections2;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FileUtils {
    public final static class AutoDeleteFile implements AutoCloseable {
        public final File file;

        public AutoDeleteFile(File f) {
            this.file = f;
        }

        public String getPath() {
            return file.getAbsolutePath();
        }

        @Override
        public void close() {
            FileSystemUtils.delete(this.file);
        }

        public static AutoDeleteFile createTempFile() {
            return createTempFile(
                    ApplicationProvider.getApplicationContext().getCacheDir(),
                    null);
        }

        public static AutoDeleteFile createTempFile(String extension) {
            return createTempFile(
                    ApplicationProvider.getApplicationContext().getCacheDir(),
                    extension);
        }

        public static AutoDeleteFile createTempFile(File directory) {
            return createTempFile(directory, null);
        }

        public static AutoDeleteFile createTempFile(File directory,
                String extension) {
            if (extension == null)
                extension = ".autodelete";
            try {
                return new AutoDeleteFile(
                        File.createTempFile("tmp", extension, directory));
            } catch (IOException e) {
                fail("Unable to create temporary file");
                return null;
            }
        }

        public static AutoDeleteFile createTempDir() {
            return createTempDir(
                    ApplicationProvider.getApplicationContext().getCacheDir());
        }

        public static AutoDeleteFile createTempDir(File directory) {
            try {
                final AutoDeleteFile retval = new AutoDeleteFile(
                        File.createTempFile("tmp", "", directory));
                if (!retval.file.delete() || !retval.file.mkdir())
                    throw new IOException("Failed to create temp dir");
                return retval;
            } catch (IOException e) {
                fail("Unable to create temporary file");
                return null;
            }
        }
    }

    /**
     * Asserts that the file trees relative to the specified roots are
     * nominally equivalent. Children, file lengths and file contents are
     * compared. Access modifiers and timestamps are ignored.
     * @param expected  The expected file tree structure
     * @param actual    The actual file tree structure
     * @throws IOException
     */
    public static void assertRelativeTreeEquals(File expected, File actual)
            throws IOException {
        assertEquals(expected.exists(), actual.exists());
        if (!expected.exists())
            return;

        assertTrue(expected.isFile() || expected.isDirectory());

        if (expected.isFile()) {
            assertTrue(actual.isFile());

            assertEquals(expected.length(), actual.length());

            try (InputStream is1 = new BufferedInputStream(openStream(expected),
                    8192);
                    InputStream is2 = new BufferedInputStream(
                            openStream(actual), 8192)) {

                do {
                    final int a = is1.read();
                    final int b = is2.read();
                    if (a < 0) {
                        assertTrue(b < 0);
                        break;
                    } else {
                        assertEquals(a, b);
                    }
                } while (true);
            }
        } else if (expected.isDirectory()) {
            assertTrue(actual.isDirectory());

            Map<String, File> expectedChildren = new HashMap<>();
            getChildren(expected, expectedChildren);
            Map<String, File> actualChildren = new HashMap<>();
            getChildren(actual, actualChildren);

            assertEquals(expectedChildren.size(), actualChildren.size());

            assertTrue(Collections2.equals(expectedChildren.keySet(),
                    actualChildren.keySet()));
            for (File ec : expectedChildren.values()) {
                File ac = actualChildren.get(ec.getName());
                assertNotNull(ac);
                assertRelativeTreeEquals(ec, ac);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private static InputStream openStream(File f) throws IOException {
        if (f instanceof ZipVirtualFile)
            return ((ZipVirtualFile) f).openStream();
        else
            return new FileInputStream(f);
    }

    private static void getChildren(File dir, Map<String, File> children) {
        File[] c = dir.listFiles();
        if (c == null)
            return;
        for (File f : c)
            children.put(f.getName(), f);
    }
}
