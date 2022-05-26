package com.atakmap.commoncommo;

/**
 * Enum representing possible addressing modes for network interfaces
 * (physical device addressing scheme)
 */
public enum NetInterfaceAddressMode {
    /** Addressing is done using mac/ethernet addresses */
    PHYS_ADDR(0),
    /** Addressing is done using interface names */
    NAME(1);
    
    private final int mode;
    
    private NetInterfaceAddressMode(int mode) {
        this.mode = mode;
    }
    
    int getNativeVal() {
        return mode;
    }
}
