
package com.atakmap.android.androidtest.util;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;
import java.io.IOException;

import androidx.test.InstrumentationRegistry;

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
                    InstrumentationRegistry.getTargetContext().getCacheDir(),
                    null);
        }

        public static AutoDeleteFile createTempFile(String extension) {
            return createTempFile(
                    InstrumentationRegistry.getTargetContext().getCacheDir(),
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
    }
}
