
package com.atakmap.android.network;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class URIStreamHandlerFactory {
    private static final Map<String, URIStreamHandler> handlers = new HashMap<>();

    private URIStreamHandlerFactory() {
    }

    public static synchronized void registerHandler(String scheme,
            URIStreamHandler handler) {
        handlers.put(scheme, handler);
    }

    public static synchronized void unregisterHandler(
            URIStreamHandler handler) {

        // intentional - used to drain the handler from the handlers map.
        while (handlers.values().remove(handler)) {
        }
    }

    public static InputStream openInputStream(Uri uri) throws IOException {
        URIStreamHandler handler;
        synchronized (URIStreamHandlerFactory.class) {
            handler = handlers.get(uri.getScheme());
        }
        if (handler == null)
            return null;
        return handler.getContent(uri);
    }
}
