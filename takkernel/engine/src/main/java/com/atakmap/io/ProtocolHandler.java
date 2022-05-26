package com.atakmap.io;

import java.util.Collection;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
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
