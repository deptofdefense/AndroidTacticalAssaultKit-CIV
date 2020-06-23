package com.atakmap.commoncommo;

/**
 * Class representing a NetInterface for a network interface on the physical
 * device (ethernet port, wifi, etc).
 * PhysicalNetInterfaces are identified by a unique hardware address.
 */
public class PhysicalNetInterface extends NetInterface {
    private final byte[] hardwareAddress;

    PhysicalNetInterface(long nativePtr, byte[] addr)
    {
        super(nativePtr);
        if (addr == null)
            this.hardwareAddress = new byte[0];
        else
            this.hardwareAddress = addr;
    }

    /**
     * Obtains a copy of the hardware address information
     * used to match this physical network interface.
     */    
    public byte[] getHardwareAddress()
    {
        byte[] b = new byte[hardwareAddress.length];
        System.arraycopy(hardwareAddress, 0, b, 0, b.length);
        return b;
    }
}
