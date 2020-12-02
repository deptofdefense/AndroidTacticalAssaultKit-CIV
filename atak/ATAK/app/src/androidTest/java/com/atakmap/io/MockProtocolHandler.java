
package com.atakmap.io;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;

public final class MockProtocolHandler implements ProtocolHandler {
    final String uri;
    final byte[] data;
    final boolean knownContentLength;
    boolean simulateFailure;

    public MockProtocolHandler(String uri, byte[] data,
            boolean knownContentLength) {
        this.uri = uri;
        this.data = data;
        this.knownContentLength = knownContentLength;
        this.simulateFailure = false;
    }

    public void setSimulateFailure(boolean fail) {
        this.simulateFailure = fail;
    }

    @Override
    public UriFactory.OpenResult handleURI(String uri) {
        if (!this.uri.equals(uri))
            return null;
        if (this.simulateFailure)
            return null;
        UriFactory.OpenResult result = new UriFactory.OpenResult();
        result.contentLength = this.knownContentLength ? data.length : -1;
        result.inputStream = new ByteArrayInputStream(data);
        return result;
    }

    @Override
    public long getContentLength(String uri) {
        return this.knownContentLength ? data.length : 0;
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return Collections.singleton("mock");
    }
}
