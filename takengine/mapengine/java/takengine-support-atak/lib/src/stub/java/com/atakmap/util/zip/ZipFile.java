package com.atakmap.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

public final class ZipFile {
    public ZipFile(File ignored) { throw new UnsupportedOperationException(); }
    public ZipEntry getEntry(String ignored) { throw new UnsupportedOperationException(); }
    public void close() throws IOException {}
    public InputStream getInputStream(ZipEntry entry) throws IOException { throw new UnsupportedOperationException(); }
    public Enumeration<? extends ZipEntry> entries() { throw new UnsupportedOperationException(); }
}
