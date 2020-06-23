package com.atakmap.commoncommo;

/**
 * Generic exception derivative used for all Commo library-specific
 * exceptions.
 */
public class CommoException extends Exception {

    CommoException() {
    }
    
    CommoException(String msg) {
        super(msg);
    }
    
}
