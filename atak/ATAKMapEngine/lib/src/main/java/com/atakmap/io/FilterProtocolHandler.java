package com.atakmap.io;

import java.util.Collection;

abstract class FilterProtocolHandler implements ProtocolHandler {
    final ProtocolHandler impl;

    FilterProtocolHandler(ProtocolHandler impl) {
        this.impl = impl;
    }

    @Override
    public UriFactory.OpenResult handleURI(String uri) {
        UriFactory.OpenResult result = impl.handleURI(uri);
        if(result == null)
            return null;
        result.handler = this;
        return result;
    }

    @Override
    public long getContentLength(String uri) {
        return impl.getContentLength(uri);
    }
}
