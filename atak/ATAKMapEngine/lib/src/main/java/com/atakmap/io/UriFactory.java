package com.atakmap.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UriFactory {

    public static class OpenResult implements AutoCloseable {
        public InputStream inputStream;
        public long contentLength;
        public ProtocolHandler handler;

        @Override
        public void close() throws IOException {
            if(this.inputStream != null)
                this.inputStream.close();
        }
    }

    /**
     *
     * @param uri the uri to open
     * @return the result of opening the uri based on the set of Protocol handlers.
     */
    public static OpenResult open(String uri) {
        OpenResult result = null;
        for (ProtocolHandler h : getHandlers()) {
            result = h.handleURI(uri);
            if (result != null) {
                result.handler = h;
                break;
            }
        }
        if (result == null)
            return fileHandler.handleURI(uri);
        return result;
    }
    /**
      Get the content length of a URI
     * @param uri the uri to see if content length can be determined.
     * @return the content length, otherwise 0 if no content length can be determined.
     */
    public static long getContentLength(String uri) {
        long result = 0;
        for (ProtocolHandler h : getHandlers()) {
            result = h.getContentLength(uri);
            if (result >= 0)
                break;
        }
        if (result < 0)
            return fileHandler.getContentLength(uri);
        return result;
    }

    /**
     * Register a protocol handler to act on a given uri.
     * @param handler the handler to register
     */
    public static void registerProtocolHandler(ProtocolHandler handler) {
        synchronized (handlers) {
            handlers.add(handler);
        }
    }

    /**
     * Unregister a protocol handler to act on a given uri.
     * @param handler the handler to unregister
     */
    public static void unregisterProtocolHandler(ProtocolHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException("handler cannot be null");
        synchronized (handlers) {
            handlers.remove(handler);
        }
    }

    public static ProtocolHandler findHandler(String scheme) {
        synchronized (handlers) {
            for(int i = 0; i < handlers.size(); i++) {
                if(handlers.get(i).getSupportedSchemes().contains(scheme))
                    return handlers.get(i);
            }
        }
        return null;
    }

    private static ProtocolHandler[] getHandlers() {
        ProtocolHandler[] hs;
        synchronized (handlers) {
            hs = handlers.toArray(new ProtocolHandler[0]);
        }
        return hs;
    }

    private static final List<ProtocolHandler> handlers = new ArrayList<>();
    private static final FileProtocolHandler fileHandler = new FileProtocolHandler();
    static {
        handlers.add(new ZipProtocolHandler());
    }
}
