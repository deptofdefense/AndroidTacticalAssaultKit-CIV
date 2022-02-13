
package com.atakmap.android.menu;

import java.util.Set;
import java.util.HashSet;

public class MenuCapabilities {

    private static final Set<String> capabilities = new HashSet<>();

    /**
     * When using certain core menu radials, there may be a capability that is not 
     * visible unless certain plugins are loaded.    In that case, the plugin needs 
     * to register the capability with core in order to get that menu item to appear.
     */
    synchronized static public void registerSupported(String capability) {
        capabilities.add(capability);
    }

    public synchronized static boolean contains(final String capability) {
        return capabilities.contains(capability);
    }

    synchronized static public void unregisterSupported(
            final String capability) {
        capabilities.remove(capability);
    }

}
