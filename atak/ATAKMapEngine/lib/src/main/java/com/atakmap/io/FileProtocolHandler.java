package com.atakmap.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

/**
 * Handles file:// URIs or treats path-only URI as raw file path
 */
public class FileProtocolHandler implements ProtocolHandler {

    @Override
    public UriFactory.OpenResult handleURI(String uri) {
        File file = getFile(uri);
        if (file == null)
            return null;

        try {
            UriFactory.OpenResult result = new UriFactory.OpenResult();
            result.inputStream = new FileInputStream(file);
            result.contentLength = file.length();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long getContentLength(String uri) {
        File file = getFile(uri);
        if (file != null)
            return file.length();
        return 0;
    }

    private File getFile(String uri) {
        File f = new File(uri);
        if(f.exists())
            return f;
        try {
            URI uriObj = new URI(uri);
            if (uriObj.getScheme() == null || uriObj.getScheme().compareToIgnoreCase("file") == 0)
                return new File(uriObj.getPath());
        } catch (Exception ex) {
            // ignore
        }
        return null;
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return Arrays.asList("file", null);
    }
}
