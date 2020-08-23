package com.atakmap.io;

import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param uri
     * @return
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
     *
     * @param uri
     * @return
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
     *
     * @param handler
     */
    public static void registerProtocolHandler(ProtocolHandler handler) {
        synchronized (handlers) {
            handlers.add(handler);
        }
    }

    /**
     *
     * @param handler
     */
    public static void unregisterProtocolHandler(ProtocolHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException();
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
        ProtocolHandler[] hs = null;
        synchronized (handlers) {
            hs = handlers.toArray(new ProtocolHandler[handlers.size()]);
        }
        return hs;
    }

    private static List<ProtocolHandler> handlers = new ArrayList<>();
    private static FileProtocolHandler fileHandler = new FileProtocolHandler();
}
