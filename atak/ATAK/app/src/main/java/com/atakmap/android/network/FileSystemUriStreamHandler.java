
package com.atakmap.android.network;

import android.net.Uri;

import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * URI Stream handler for file system only URIs
 */
public final class FileSystemUriStreamHandler implements URIStreamHandler {

    /**
     * Single instance
     */
    public final static URIStreamHandler INSTANCE = new FileSystemUriStreamHandler();

    /**
     * Private constructor so it cannot be constructed outside this class
     */
    private FileSystemUriStreamHandler() {
    }

    /**
     * Gets the uri content input stream
     *
     * @param uri the uri to use
     * @return the uri content input stream
     * @throws IOException Throws IOException if uri's path is empty.
     */
    @Override
    public InputStream getContent(Uri uri) throws IOException {
        final String path = uri.getPath();
        if (path == null || path.isEmpty())
            throw new FileNotFoundException("URI does not specify a path");
        return IOProviderFactory.getInputStream(new File(path));
    }
}
