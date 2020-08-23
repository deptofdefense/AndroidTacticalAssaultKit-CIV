package com.atakmap.io;

import java.io.InputStream;
import java.util.Collection;

public interface ProtocolHandler {
    /**
     *
     * @param uri
     * @return
     */
    UriFactory.OpenResult handleURI(String uri);

    /**
     *
     * @param uri
     * @return
     */
    long getContentLength(String uri);

    Collection<String> getSupportedSchemes();
}
