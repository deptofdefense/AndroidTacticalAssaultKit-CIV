package com.atakmap.commoncommo;

/**
 * Enum representing the methods by which cot messages may be sent to remote
 * recipients.
 */
public enum CoTSendMethod {
    /** Send only via TAK server (streaming) connections */
    TAK_SERVER(1),
    /** Send only via point to point direct to device methods */
    POINT_TO_POINT(2),
    /** Send via any available method */
    ANY(3);
    
    private final int id;
    
    private CoTSendMethod(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
