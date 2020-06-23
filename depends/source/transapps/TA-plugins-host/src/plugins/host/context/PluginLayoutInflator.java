package plugins.host.context;

import java.lang.reflect.Constructor;
import java.util.HashMap;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;

/**
 * Class to wrap the System Layout Inflater for plugins.
 *
 * This inflater will look in both the host and the plugin application for views and layouts to inflate.
 *
 * @author mriley
 */

public class PluginLayoutInflator extends LayoutInflater implements LayoutInflater.Factory{
    /**
     * unprefix widget packages.  See {@link com.android.internal.policy.impl.PhoneLayoutInflater} for details
     */
    private static final String[] classPrefixList = {"android.widget.", "android.webkit.", "android.view."};
    /**
     * constructor signature views should have
     */
    private static final Class<?>[] constructorSignature = new Class[] {Context.class, AttributeSet.class};
    /**
     * reusable constructor arg[]
     */
    private final Object[] constructorArgs = new Object[2];
    /**
     * constructor cache
     */
    private final HashMap<String, Constructor<? extends View>> constructorMap = new HashMap<String, Constructor<? extends View>>();
    
    private final PluginContext pluginContext;

    protected PluginLayoutInflator( PluginContext context ) {
        super(context);
        constructorArgs[0] = context;
        pluginContext = context;
        super.setFactory(this);
    }


    /**
     * Override of the onCreateView function to search for and create views, looking first in the plugin application
     * and then looking in the host context.
     *
     * @param name name of the view to create
     * @param attrs A collection of attributes, as found associated with a tag in an XML document, these are the attributes specified
     *              in the view xml file for the view
     * @return View The newly instantiated view, or null.
     * @throws ClassNotFoundException
     */
    @Override
    protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
        boolean fullyQualified = name.indexOf('.') > -1;
        for( int i = 0; i < classPrefixList.length; i++ ) {
            String prefix = fullyQualified ? null : classPrefixList[i];
            try {
                return createView(name, prefix, attrs, pluginContext.getClassLoader());
            } catch ( Exception e ) {
                Log.d(PluginContext.TAG, "Failed to create " + name + " from plugin context ("+e+").  Attempting host context...");
                try {
                    return createView(name, prefix, attrs, pluginContext.getHostContext().getClassLoader());    
                } catch ( Exception e2 ) {
                    Log.d(PluginContext.TAG, "Failed to create " + name + " from host context ("+e+").");
                }
            }
            if( fullyQualified ) {
                break;
            }
        }
        return super.onCreateView(name, attrs);
    }


    @Override
    public LayoutInflater cloneInContext(Context newContext) {
        return this;
    }



    /**
     * Low-level function for instantiating a view by name. This attempts to
     * instantiate a view class of the given <var>name</var> found in this
     * LayoutInflater's ClassLoader.
     * 
     * <p>
     * There are two things that can happen in an error case: either the
     * exception describing the error will be thrown, or a null will be
     * returned. You must deal with both possibilities -- the former will happen
     * the first time createView() is called for a class of a particular name,
     * the latter every time there-after for that class name.
     * 
     * @param name The full name of the class to be instantiated.
     * @param attrs The XML attributes supplied for this instance.
     * 
     * @return View The newly instantiated view, or null.
     */
    public final View createView(String name, String prefix, AttributeSet attrs, ClassLoader loader)
            throws ClassNotFoundException, InflateException {
        synchronized(constructorArgs) {
            Constructor<? extends View> constructor = constructorMap.get(name);
            Class<? extends View> clazz = null;

            try {
                if (constructor == null) {
                    // Class not found in the cache, see if it's real, and try to add it
                    clazz = loader.loadClass(prefix != null ? (prefix + name) : name).asSubclass(View.class);
                    constructor = clazz.getConstructor(constructorSignature);
                    constructorMap.put(name, constructor);
                }

                Object[] args = constructorArgs;
                args[1] = attrs;
                return constructor.newInstance(args);

            } catch (NoSuchMethodException e) {
                InflateException ie = new InflateException(attrs.getPositionDescription()
                        + ": Error inflating class "
                        + (prefix != null ? (prefix + name) : name));
                ie.initCause(e);
                throw ie;

            } catch (ClassCastException e) {
                // If loaded class is not a View subclass
                InflateException ie = new InflateException(attrs.getPositionDescription()
                        + ": Class is not a View "
                        + (prefix != null ? (prefix + name) : name));
                ie.initCause(e);
                throw ie;
            } catch (ClassNotFoundException e) {
                // If loadClass fails, we should propagate the exception.
                throw e;
            } catch (Exception e) {
                InflateException ie = new InflateException(attrs.getPositionDescription()
                        + ": Error inflating class "
                        + (clazz == null ? "<unknown>" : clazz.getName()));
                ie.initCause(e);
                throw ie;
            }
        }
    }

    /**
     * This method is the override from the LayoutInflater.Factory. It is needed due to an Android
     * bug when using multiple class loaders as we do in the plugin framework. The AOSP bug report
     * is listed here:
     *
     * https://code.google.com/p/android/issues/detail?id=185838
     *
     * @since NW SDK 1.1.8.6
     */

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        View retVal = null;
        /**
         * If this is one of the built in widgets it will come without a fully scoped name, think
         * <LinearLayout></LinearLayout>, then we do not need to try and load it from the class
         * loader of the plugin, so just return a null.
         *
         * But, if it is a custom type or else a support library version, then we should try to load
         * from the plugin context and class loader because it will then match the resource IDs
         * that it was compiled with.
         */

        //
        if (-1 != name.indexOf('.')) {
            try {
                retVal = this.createView(name, null, attrs, this.pluginContext.getClassLoader());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return retVal;
    }
}