
package com.atakmap.android.fires.bridge;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;

/**
 * Bridge component in support of the the flavor subsystem.
 */
public abstract class Entity {

    private static Entity impl;

    public static Entity getInstance(MapView _view) {
        return impl;
    }

    public static void registerImplementation(Entity concreteImpl) {
        impl = concreteImpl;
    }

    /**
     * Update the target in the Entity.
     *
     * @param uid the uid of the target.
     */
    public abstract void updateTarget(String uid);

    /**
     * Responsible for returning the target associated with the active 5/9 line
     * entity.   Will return null if the target does not exist or has been removed.
     */
    public abstract PointMapItem getTarget();

    /**
     * Returns the closes friendly associated with the target nine line.
     * @return the point map item that is the closest friendly
     */
    public abstract PointMapItem getClosestFriendly();

}
