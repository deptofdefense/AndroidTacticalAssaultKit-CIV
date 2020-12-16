
package com.atakmap.app.system;

import android.content.Context;

public class ResourceUtil {

    /**
     * Allow for proper lookup of Strings based on the capabilities of the flavor
     * @param c The context to use when looking up a resource
     * @param resIdCiv the civilian capability resource
     * @param resIdMil the military capability resource
     * @return
     */
    public static String getString(Context c, int resIdCiv, int resIdMil) {
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null)
            if (fp.hasMilCapabilities())
                return c.getString(resIdMil);

        return c.getString(resIdCiv);
    }

    /**
     * Allow for proper lookup of resource id based on the capabilities of the flavor
     * @param resIdCiv the civilian capability resource
     * @param resIdMil the military capability resource
     * @return
     */
    public static int getResource(int resIdCiv, int resIdMil) {
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null)
            if (fp.hasMilCapabilities())
                return resIdMil;

        return resIdCiv;
    }

}
