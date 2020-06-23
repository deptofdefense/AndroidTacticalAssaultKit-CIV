
package com.atakmap.android.network;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public interface URIStreamHandler {
    InputStream getContent(Uri uri) throws IOException;
}
