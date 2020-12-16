
package com.atakmap.android.maps.assets;

import androidx.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;

public class ArchiveMapAssetInputStream extends InputStream {
    private ZipFile _openZipFile;
    private InputStream _entryInputStream;

    public ArchiveMapAssetInputStream(String archivePath, String entryPath)
            throws IOException {
        _openZipFile = new ZipFile(
                FileSystemUtils.sanitizeWithSpacesAndSlashes(archivePath));
        ZipEntry entry = _openZipFile.getEntry(entryPath);
        if (entry != null)
            _entryInputStream = _openZipFile.getInputStream(entry);
        else
            throw new IOException("Invalid entry path");
    }

    @Override
    public int available() throws IOException {
        return _entryInputStream.available();
    }

    @Override
    public void close() throws IOException {
        if (_openZipFile != null) {
            _openZipFile.close();
            _openZipFile = null;
            _entryInputStream = null;
        }
    }

    @Override
    public void mark(int readLimit) {
        _entryInputStream.mark(readLimit);
    }

    @Override
    public boolean markSupported() {
        return _entryInputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
        return _entryInputStream.read();
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException {
        return _entryInputStream.read(b);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        return _entryInputStream.read(b, off, len);
    }

    @Override
    public void reset() throws IOException {
        _entryInputStream.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return _entryInputStream.skip(n);
    }

}
