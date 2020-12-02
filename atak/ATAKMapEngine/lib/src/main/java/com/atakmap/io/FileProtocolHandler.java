package com.atakmap.io;

import com.atakmap.coremap.io.FileIOProviderFactory;

import java.io.File;
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
            result.inputStream = FileIOProviderFactory.getInputStream(file);
            result.contentLength = FileIOProviderFactory.length(file);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long getContentLength(String uri) {
        File file = getFile(uri);
        if (file != null)
            return FileIOProviderFactory.length(file);
        return 0;
    }

    private File getFile(String uri) {
        File f = new File(uri);
        if(FileIOProviderFactory.exists(f))
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
