package plugins.host;


import android.app.Application;

/**
 * Created by fhodum on 12/23/15.
 *
 * Base abstract class used for pluggable applications.
 *
 * @since NW SDK 1.1.8.6
 */
abstract public class ApplicationPluggableContext extends Application implements PluggableContext{

    protected void setPluginBaseClassLoader(ClassLoader loader )
    {
        getPluginRegistry().setPluginBaseClassLoader(loader);
    }
}
