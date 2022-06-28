
package com.atakmap.android.fires.bridge;

import android.graphics.drawable.Drawable;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.maps.AbstractMapComponent;

/**
 * Bridge component in support of the the flavor subsystem.
 */
public abstract class AdditionalCFFMapComponent extends AbstractMapComponent {

    private static AdditionalCFFMapComponent impl;

    public static AdditionalCFFMapComponent getInstance() {
        return impl;
    }

    /**
     * Used by the system plugin to register a concrete implementation of the
     * call for fire capability.
     * @param concreteImpl the concrete call for fire implementation
     */
    public static void registerImplementation(
            AdditionalCFFMapComponent concreteImpl) {
        if (impl == null)
            impl = concreteImpl;
    }

    /**
     * Allows for additional Call for Fire buttons to be made available to the
     * end user.
     * @param i is the intent string to call.  This intent is filled in with
     * targetUID and friendlyUID.
     */
    public abstract TileButtonDialog.TileButton registerCapability(
            Drawable icon,
            String text, final String i);

    /**
     * Removes the additional call for fire button from the tile button dialog.
     * @param tb the TileButton returned by the registerCapability.
     */
    public abstract void unregisterCapability(TileButtonDialog.TileButton tb);
}
