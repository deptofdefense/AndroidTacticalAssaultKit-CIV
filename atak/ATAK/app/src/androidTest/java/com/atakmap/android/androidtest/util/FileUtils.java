
package com.atakmap.android.androidtest.util;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

public final class FileUtils {
    public final static class AutoDeleteFile implements AutoCloseable {
        public final File file;

        public AutoDeleteFile(File f) {
            this.file = f;
        }

        @Override
        public void close() {
            FileSystemUtils.delete(this.file);
        }
    }
}
