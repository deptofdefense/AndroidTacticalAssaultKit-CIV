package plugins.core;

import android.content.Context;

/**
 * Interface to define the mechanism for any activation required by the plugin.
 *
 * If defined in the plugin.xml file,
 * the implementation of this class will be instantiated and the activate method is called before any extensions are
 * actually loaded out of the plugin apk.  The implementation class must have a no argument constructor.
 *
 */
public interface Activator {

    /**
     * Activate method called by the plugin host code after class loader info and plugin context have been
     * created.  The plugin context will be passed into this method.  Any global initialization required by the plugin
     * can be executed here since it is guaranteed to be called before the extensions are instantiated.
     *
     * @param context Cotext that is a plugin context specific to this plugin
     */
    void activate( Context context );
}
