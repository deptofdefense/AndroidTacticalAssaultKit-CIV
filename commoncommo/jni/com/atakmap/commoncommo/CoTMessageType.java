package com.atakmap.commoncommo;

/**
 * Enum representing the different types of CoT message traffic.
 */
public enum CoTMessageType {
    SITUATIONAL_AWARENESS(0),
    CHAT(1);
    
    private final int id;
    
    private CoTMessageType(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
