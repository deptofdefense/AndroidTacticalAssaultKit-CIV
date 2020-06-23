package plugins.host.context;

import dalvik.system.PathClassLoader;

/**
 * Created by fhodum on 6/2/15.
 *
 * A classloader populated with all dependency dex paths for the PluginRegistry.
 * This PluginClassLoader is used when instantiating the impl class for a plugin in the PluginRegistry.
 *
 * @since NW SDK 1.1.8.6
 */
public class PluginClassLoader extends PathClassLoader {

    private ClassLoader myParent;

    public PluginClassLoader(String path, ClassLoader parent) {
        super(path, null, parent);
        myParent = parent;
    }

    public PluginClassLoader(String path, String libPath, ClassLoader parent) {
        super(path,libPath,parent);
        myParent = parent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = findLoadedClass(name);

        if (clazz == null) {
            ClassNotFoundException suppressed = null;
            try {
                clazz = myParent.loadClass(name);
            } catch (ClassNotFoundException e) {
                suppressed = e;
            }

            if (clazz == null) {
                try {
                    clazz = findClass(name);
                } catch (ClassNotFoundException e) {
                    throw e;
                }
            }
        }

        return clazz;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
