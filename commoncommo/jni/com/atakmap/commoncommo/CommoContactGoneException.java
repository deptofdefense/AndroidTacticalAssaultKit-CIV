package com.atakmap.commoncommo;

/**
 * A CommoException derivative that indicates one or more contacts
 * specified for a Commo operation are known for certain 
 * to be no longer reachable.
 */
public class CommoContactGoneException extends CommoException {

    CommoContactGoneException() {
    }
    
}
