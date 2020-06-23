package plugins.host;

import plugins.core.model.Extension;
import plugins.core.model.Plugin;

/**
 * Used by plugin registry to limit the plugins and extensions that may be loaded. Note the name and expected behavior
 * of the functions. If the item passed in should be filtered out, return true, otherwise return false
 * 
 * @author mriley
 */
public interface PluginFilter {

    /**
     * A filter that doesn't filter
     * 
     * @author mriley
     */
    public static class PassFilter implements PluginFilter {
        @Override
        public boolean filterLoad(Class<?> extensionType) {
            return false;
        }        
        @Override
        public boolean filterLoad(Plugin plugin) {
            return false;
        }
        @Override
        public boolean filterLoad(Object extensionImpl) {
            return false;
        }
        @Override
        public boolean filterRegister(Extension extension) {
            return false;
        }
        @Override
        public boolean filterRegister(Plugin plugin) {
            return false;
        }
    };
    
    public final static PluginFilter PASS = new PassFilter();
    
    /**
     * Should all extensions from this plugin be filtered? 
     * 
     * @param plugin
     * @return true to filter the plugin
     */
    boolean filterRegister( Plugin plugin );
    
    /**
     * Should this particular extension be filtered?
     * 
     * @param extension
     * @return true to filter the plugin
     */
    boolean filterRegister( Extension extension );

    /**
     * Should this plugin be loaded?  There may be cases where the plugin is only valid for use under certain
     * circumstances.  So the plugin should get registered, but only be loadable sometimes.  This filter method
     * can support that case.
     * 
     * @param plugin
     * @return true to filter the plugin
     */
    boolean filterLoad( Plugin plugin );
    
    /**
     * Should extensions of this type be loaded? There may be cases where extensions are only valid for use under
     * certain circumstances.  So the plugin should get registered, but only be loadable sometimes.  This filter method
     * can support that case.
     * 
     * @param extensionType
     * @return true to filter the extension
     */
    boolean filterLoad( Class<?> extensionType );
    
    
    /**
     * Should this extension impl be filtered?
     * 
     * @param extensionImpl
     * @return true to filter the extension
     */
    boolean filterLoad( Object extensionImpl );
}
