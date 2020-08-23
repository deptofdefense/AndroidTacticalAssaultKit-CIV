
package com.atakmap.android.munitions.util;

/**
 * Used by {@link MunitionsHelper} for looping through weapon metadata
 */
public interface WeaponConsumer {

    /**
     * Called for each weapon in the target's metadata
     * @param data Weapon data
     */
    void forWeapon(WeaponData data);
}
