
package com.atak.plugins.impl;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.log.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class PluginLayoutInflater {

    private static final String TAG = "LayoutInflaterHelper";

    /**
     * Preferred mechanism for loading a view within TAK from a plugin.   This mimics the current
     * parameters from calling {@link LayoutInflater#inflate(int, ViewGroup, boolean)}
     *
     * Used for inflating plugin without the concerns that come with previously cached views.  Cached
     * views become a problem when the class name is used to cache the view but the view might be
     * from a different class loader.
     * 
     *
     * @param plugin Plugin context used to inflate the layout
     * @param resId Layout resource ID
     * @param root Root/parent view group
     * @param attachToRoot True to attach to the parent view
     *                     False to only use it for layout parameters
     * @return Newly inflated view
     */
    public static View inflate(final Context plugin, final int resId,
            final ViewGroup root, boolean attachToRoot) {
        final LayoutInflater inflater = LayoutInflater.from(plugin);
        dispose();
        return inflater.inflate(resId, root, attachToRoot);
    }

    /**
     * Preferred mechanism for loading a view within TAK from a plugin.   This mimics the current
     * parameters from calling {@link LayoutInflater#inflate(int, ViewGroup)}
     *
     * Used for inflating plugin without the concerns that come with previously cached views.  Cached
     * views become a problem when the class name is used to cache the view but the view might be
     * from a different class loader.
     *
     *
     * @param plugin Plugin context used to inflate the layout
     * @param resId Layout resource ID
     * @param root Root/parent view group
     * @return Newly inflated view
     */
    public static View inflate(Context plugin, int resId, ViewGroup root) {
        return inflate(plugin, resId, root, root != null);
    }

    /**
     * Preferred mechanism for loading a view within TAK from a plugin.   This mimics the current
     * parameters from calling {@link LayoutInflater#inflate(int, ViewGroup)} with a null root.
     *
     * Used for inflating plugin without the concerns that come with previously cached views.  Cached
     * views become a problem when the class name is used to cache the view but the view might be
     * from a different class loader.
     *
     *
     * @param plugin Plugin context used to inflate the layout
     * @param resId Layout resource ID
     * @return Newly inflated view
     */
    public static View inflate(Context plugin, int resId) {
        return inflate(plugin, resId, null);
    }

    /**
     * Responsible for cleaning out the cache in the LayoutInflater.  The view is incorrectly
     * cached and could potentially have the wrong classloader describing it.
     */
    public static void dispose() {
        try {
            //noinspection JavaReflectionMemberAccess
            Field f;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                f = LayoutInflater.class.getDeclaredField("sConstructorMap");
            } else {
                // the blacklist only checks to see the calling function and in this case
                // the reflection a second time makes the calling function is from the
                // system and not from this application.   Warn users for future SDK's
                // that this might not work when running debug versions - so it can be
                // checked.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && BuildConfig.DEBUG)
                    Log.e(TAG,
                            "may need to revisit double reflection trick: PluginLayoutInflator");
                final Method xgetDeclaredField = Class.class
                        .getDeclaredMethod("getDeclaredField",
                                String.class);
                f = (Field) xgetDeclaredField.invoke(LayoutInflater.class,
                        "sConstructorMap");
            }
            if (f != null) {
                f.setAccessible(true);
                final Map sConstructorMap = (Map) f.get(null);
                if (sConstructorMap != null)
                    sConstructorMap.clear();
            }
            //Log.d(TAG, "cleared out the constructor map");
        } catch (Exception e) {
            // intentionally catch any and all possible exceptions
            Log.d(TAG, "error, could not clear out the constructor map: " + e);
        }
    }

}
