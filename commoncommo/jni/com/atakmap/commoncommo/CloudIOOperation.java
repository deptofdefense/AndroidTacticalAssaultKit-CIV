package com.atakmap.commoncommo;

/**
 * Enum representing the various cloud
 * operations that may be performed
 */
public enum CloudIOOperation {
    /** Test server authorization, paths, and connectivity */
    TEST_SERVER(0),
    /** List contents of a collection */
    LIST_COLLECTION(1),
    /** Get a file */
    GET(2),
    /** Upload/send a file */
    PUT(3),
    /** Rename a collection or file */
    MOVE(4),
    /** Create a new, empty collection */
    MAKE_COLLECTION(5);
    
    private final int id;
    
    private CloudIOOperation(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
