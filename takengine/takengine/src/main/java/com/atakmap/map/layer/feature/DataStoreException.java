package com.atakmap.map.layer.feature;

public class DataStoreException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1066953757132123715L;

    public DataStoreException(String msg) {
        super(msg);
    }
    
    public DataStoreException(Throwable inner) {
        super(inner);
    }
    
    public DataStoreException(String msg, Throwable inner) {
        super(msg, inner);
    }
}
