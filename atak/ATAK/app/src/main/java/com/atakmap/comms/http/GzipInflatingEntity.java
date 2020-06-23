
package com.atakmap.comms.http;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class GzipInflatingEntity extends HttpEntityWrapper {
    public GzipInflatingEntity(final HttpEntity wrapped) {
        super(wrapped);
    }

    @Override
    public InputStream getContent() throws IOException {
        return new GZIPInputStream(wrappedEntity.getContent());
    }

    @Override
    public long getContentLength() {
        return -1;
    }
}
