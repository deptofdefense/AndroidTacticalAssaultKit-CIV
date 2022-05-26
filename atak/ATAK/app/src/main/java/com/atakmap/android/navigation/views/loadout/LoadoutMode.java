
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.util.MappingAdapterEventReceiver;

/**
 * Loadout editor modes
 *
 * These are used to establish context for button clicks in {@link LoadoutListVH}
 * i.e. When I click the delete button, the {@link #DELETE} mode is set and
 * {@link MappingAdapterEventReceiver#eventReceived(Object)} is called. The
 * receiver then knows the user wants to delete the loadout.
 */
public enum LoadoutMode {
    VIEW, // User wants to select a loadout and view all its tools
    SELECT, // User wants to select a loadout
    EDIT, // User wants to edit a loadout
    DELETE // User wants to delete a loadout
}
