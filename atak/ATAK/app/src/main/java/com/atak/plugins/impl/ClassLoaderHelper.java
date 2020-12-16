
package com.atak.plugins.impl;

import android.content.Context;
import com.atakmap.coremap.log.Log;

public class ClassLoaderHelper {

    public static final String TAG = "ClassLoaderHelper";

    /**
    * Creates an Object given a classname.
    * @param className is the classname to be used.   The class needs to 
    * have a no parameter constructor in order to instantiate.
    * This will walk through the list of all plugins.
    * @param packageName the packageName to look at when instatiating the class for the plugin.
    */
    public static Object createObject(final String className,
            String packageName) {
        // Check if plugin preference fragment
        try {
            AtakPluginRegistry registry = AtakPluginRegistry.get();
            if (registry != null) {
                for (Object plugin : registry.getPluginInstantiations()) {

                    // Tool needs to implement PluginContextProvider to use this feature
                    if (!(plugin instanceof PluginContextProvider))
                        continue;

                    // Get plugin context and make sure it's non-null
                    Context pContext = ((PluginContextProvider) plugin)
                            .getPluginContext();
                    if (pContext == null)
                        continue;

                    // Skip if non-null package name doesn't match
                    if (packageName != null && !packageName.equals(pContext
                            .getPackageName()))
                        continue;

                    // Attempt to create object
                    Object o = create(pContext.getClassLoader(), className);
                    if (o != null) {
                        if (o instanceof PluginContextSettable)
                            ((PluginContextSettable) o).setPluginContext(
                                    pContext);
                        return o;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "error occurred, falling back", e);
        }

        // Fallback to default ATAK class loader
        return create(ClassLoaderHelper.class.getClassLoader(), className);
    }

    /**
    * Creates an Object given a classname.
    * @param classname is the classname to be used.   The class needs to 
    * have a no parameter constructor in order to instantiate.
    * This will walk through the list of all plugins.
    */
    public static Object createObject(final String classname) {
        return createObject(classname, null);
    }

    /**
    * Creates an Class given a classname and package name.
    * @param className is the class name to be used.
    * @param packageName the package name of the apk to be used.
    */
    public static Class<?> createClass(final String className,
            final String packageName) {
        // Check if plugin preference fragment
        AtakPluginRegistry registry = AtakPluginRegistry.get();
        if (registry != null) {
            for (Object plugin : registry.getPluginInstantiations()) {

                // Tool needs to implement PluginContextProvider to use this feature
                if (!(plugin instanceof PluginContextProvider))
                    continue;

                // Get plugin context and make sure it's non-null
                Context pContext = ((PluginContextProvider) plugin)
                        .getPluginContext();
                if (pContext == null)
                    continue;

                // Skip if non-null package name doesn't match
                if (packageName != null && !packageName.equals(pContext
                        .getPackageName()))
                    continue;

                ClassLoader classLoader = pContext.getClassLoader();
                try {
                    if (classLoader != null) {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (clazz != null) {
                            return clazz;
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // in this case do nothing
                }
            }
        }
        try {
            // Fallback to default ATAK class loader
            ClassLoader cl = ClassLoaderHelper.class.getClassLoader();
            if (cl == null)
                return null;

            return cl.loadClass(className);

        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    /**
    * Creates an Class given a classname and searches all plugins for that class.
    * @param className is the class name to be used.
    * This implementation searches through all plugins for the specified class
    */
    public static Class<?> createClass(final String className) {
        return createClass(className, null);
    }

    /**
     * Instantiate a new object based on a class
     * @param cl Class loader
     * @param classname the class name to load
     * @return New object or null if failed
     */
    private static Object create(final ClassLoader cl, final String classname) {
        if (cl == null || classname == null)
            return null;
        try {
            Class<?> clazz = cl.loadClass(classname);
            return clazz.newInstance();
        } catch (ClassNotFoundException e) {
            // do nothing here. 
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access "
                    + classname, e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Could not instantiate "
                    + classname, e);
        }
        return null;
    }
}
