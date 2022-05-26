package com.atakmap.map.layer.feature.wfs;

import java.util.HashMap;
import java.util.Map;

public final class WFSSchemaHandlerRegistry {

    private final static Map<String, WFSSchemaHandler> handlers = new HashMap<String, WFSSchemaHandler>();
    
    private WFSSchemaHandlerRegistry() {}
    
    public static synchronized void register(WFSSchemaHandler handler) {
        handlers.put(handler.getUri(), handler);
    }
    
    public static synchronized void unregister(WFSSchemaHandler handler) {
        handlers.put(handler.getUri(), handler);
    }
    
    public static synchronized WFSSchemaHandler get(String uri) {
        return handlers.get(uri);
    }
}
