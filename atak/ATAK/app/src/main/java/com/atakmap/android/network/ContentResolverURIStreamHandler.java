
package com.atakmap.android.network;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public class ContentResolverURIStreamHandler implements URIStreamHandler {

    private final ContentResolver contentResolver;

    public ContentResolverURIStreamHandler(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    /**************************************************************************/
    // URI Stream Handler

    @Override
    public InputStream getContent(Uri uri) throws IOException {
        return this.contentResolver.openInputStream(uri);
    }
} // ContentResolverURIStreamHandler
