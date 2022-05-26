package com.atakmap.commoncommo;

/**
 * Class representing a NetInterface for a network interface on the physical
 * device (ethernet port, wifi, etc).
 * PhysicalNetInterfaces are identified by a unique hardware address or
 * name, depending on the hardware address mode.
 */
public class PhysicalNetInterface extends NetInterface {
    private final byte[] hardwareAddress;
    private final String name;

    PhysicalNetInterface(long nativePtr, byte[] addr, int addrModeIdx)
    {
        super(nativePtr);
        NetInterfaceAddressMode addrMode =
            NetInterfaceAddressMode.values()[addrModeIdx];
        
        String n = null;
        
        if (addr == null)
            this.hardwareAddress = new byte[0];
        else if (addrMode == NetInterfaceAddressMode.PHYS_ADDR)
            this.hardwareAddress = addr;
        else {
            this.hardwareAddress = new byte[0];
            try {
                n = new String(addr, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                // ignore; must be supported by all JREs
            }
        }

        this.name = n;
    }

    /**
     * Obtains a copy of the hardware address information
     * used to match this physical network interface.
     * @return interface's hardware address
     *         or an empty array if this interface was not created using
     *         a hardware address identifier
     */    
    public byte[] getHardwareAddress()
    {
        byte[] b = new byte[hardwareAddress.length];
        System.arraycopy(hardwareAddress, 0, b, 0, b.length);
        return b;
    }

    /**
     * Obtains the interface's identifying name
     * used to match this physical network interface.
     * @return interface's identifying name or null if this
     *         interface was not created using a name-based
     *         identifier.
     */    
    public String getIdentifyingName()
    {
        return name;
    }
}
