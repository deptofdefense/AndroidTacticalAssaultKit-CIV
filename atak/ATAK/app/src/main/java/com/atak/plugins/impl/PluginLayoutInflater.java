
package com.atak.plugins.impl;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.coremap.log.Log;

import java.lang.reflect.Field;
import java.util.Map;

public class PluginLayoutInflater {

    private static final String TAG = "LayoutInflaterHelper";

    /**
     * Preferred mechanism for loading a view within TAK from a plugin.   This mimics the current
     * parameters from calling inflate(resourceId, viewGroup).
     *
     * Used for inflating plugin without the concerns that come with previously cached views.  Cached
     * views become a problem when the class name is used to cache the view but the view might be
     * from a different class loader.
     * 
     *
     * @param c the plugin context
     * @param resId the resource id.
     * @param viewGroup the parent view group.
     * @return the view
     */
    public static View inflate(final Context c, final int resId,
            final ViewGroup viewGroup) {
        final LayoutInflater inflater = LayoutInflater.from(c);
        dispose();
        return inflater.inflate(resId, viewGroup);
    }

    /**
     * Responsible for cleaning out the cache in the LayoutInflater.  The view is incorrectly
     * cached and could potentially have the wrong classloader describing it.
     */
    public static void dispose() {
        try {
            //noinspection JavaReflectionMemberAccess
            Field f = LayoutInflater.class.getDeclaredField("sConstructorMap");
            f.setAccessible(true);
            Map sConstructorMap = (Map) f.get(null);
            sConstructorMap.clear();
            //Log.d(TAG, "cleared out the constructor map");
        } catch (Exception e) {
            // intentionally catch any and all possible exceptions
            Log.d(TAG, "error, could not clear out the constructor map: " + e);
        }
    }

}
