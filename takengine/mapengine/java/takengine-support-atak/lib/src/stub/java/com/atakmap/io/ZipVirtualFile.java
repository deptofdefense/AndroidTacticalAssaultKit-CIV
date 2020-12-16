package com.atakmap.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class ZipVirtualFile extends File {
    private ZipVirtualFile() {
        super((String)null);
    }

    public InputStream openStream() throws IOException {
        throw new UnsupportedOperationException();
    }
}
