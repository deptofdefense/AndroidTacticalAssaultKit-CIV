
package com.atakmap.android.maps.graphics;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;

import org.apache.commons.lang.reflect.ConstructorUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Use {@link GLMapGroup2}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public final class GLMapGroup {

    public static final String TAG = "GLMapGroup";

    private GLMapGroup() {
    }

    private static Class<? extends GLMapItem2> lookupGLClass(
            Class<?> itemClass) {
        Class<?> glClass = null;

        try {
            String packageName = itemClass.getPackage().getName();
            String className = itemClass.getSimpleName();

            // Allow either graphics.GLClassName or just GLClassName? Former is good if you have
            // several, latter seems better if just one
            try {
                String glClassName = packageName + ".graphics.GL" + className;
                glClass = Class.forName(glClassName);
            } catch (ClassNotFoundException e) {
                String glClassName = packageName + ".GL" + className;
                glClass = Class.forName(glClassName);
            }
        } catch (ClassCastException | SecurityException
                | IllegalArgumentException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class " +
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        } catch (ClassNotFoundException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class "+
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        }

        // Check if it's a GLMapItem
        if (glClass != null && !GLMapItem2.class.isAssignableFrom(glClass)) {
            glClass = null;
            Log.e(TAG,
                    "GL class was not a subclass of GLMapItem for item of class "
                            + itemClass.getName());
        }

        return (Class<? extends GLMapItem2>) glClass;
    }

    static final Map<Class<? extends MapItem>, Class<? extends GLMapItem2>> glClasses = new HashMap<>();
    static final Map<Class<? extends GLMapItem2>, Constructor<?>> glCtors = new HashMap<>();

    /**
     * @deprecated Use {@link GLMapGroup2#DEFAULT_GLMAPITEM_SPI3}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public final static GLMapItemSpi2 DEFAULT_GLMAPITEM_SPI2 = new GLMapItemSpi2() {

        @Override
        public int getPriority() {
            // this SPI will be the fall-through if all else fails
            return -1;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            final MapRenderer surface = arg.first;
            final MapItem item = arg.second;

            return this.reflect(surface, item);
        }

        private synchronized GLMapItem2 reflect(MapRenderer surface,
                MapItem item) {
            // Automatically try to find the GL class if it isn't one of the core map items but
            // something added by another component
            Class<? extends MapItem> itemClass = item.getClass();
            Class<? extends GLMapItem2> glClass = null;

            Class<?> currentClass = itemClass;
            // If class is cached, use it
            if (glClasses.containsKey(itemClass)) {
                glClass = glClasses.get(itemClass);
            } else {
                // Otherwise, look it up
                while (currentClass != null && glClass == null) {
                    glClass = lookupGLClass(currentClass);
                    if (glClass == null)
                        currentClass = currentClass.getSuperclass();
                }

                // Cache class, or null if it wasn't found
                glClasses.put(itemClass, glClass);
            }

            // If there is a GL class, instantiate it
            if (glClass != null) {
                Constructor<?> ctor = glCtors.get(glClass);
                if (ctor == null) {
                    ctor = ConstructorUtils.getMatchingAccessibleConstructor(
                            glClass,
                            new Class[] {
                                    MapRenderer.class, currentClass
                    });
                    glCtors.put(glClass, ctor);
                }

                try {
                    return (GLMapItem2) ctor.newInstance(surface, item);
                } catch (InstantiationException | InvocationTargetException
                        | IllegalAccessException ignored) {
                }
            }

            return null;
        }

    };
}
