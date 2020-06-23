
package com.atakmap.android.importexport;

import android.net.Uri;

import com.atakmap.android.network.URIStreamHandlerFactory;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractMarshal implements Marshal {
    protected final String contentType;

    protected AbstractMarshal(final String contentType) {
        this.contentType = contentType;
    }

    @Override
    public final String getContentType() {
        return this.contentType;
    }

    /**************************************************************************/

    public static String marshalUriAsStream(Marshal marshal, Uri uri)
            throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = URIStreamHandlerFactory.openInputStream(uri);
            if (inputStream == null)
                return null;
            return marshal.marshal(inputStream, Integer.MAX_VALUE);
        } finally {
            if (inputStream != null)
                inputStream.close();
        }
    }
}
