package com.atakmap.commoncommo;

/**
 * Abstract base class for all Commo NetInterfaces.
 */
public abstract class NetInterface {

    private long nativePtr;
    
    protected NetInterface(long nativePtr)
    {
        this.nativePtr = nativePtr;
    }

    void nativeClear()
    {
        nativePtr = 0;
    }

    long getNativePtr()
    {
        return nativePtr;
    }
    
}
