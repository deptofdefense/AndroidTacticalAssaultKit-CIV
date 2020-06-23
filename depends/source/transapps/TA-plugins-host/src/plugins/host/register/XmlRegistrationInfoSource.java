package plugins.host.register;

import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;

import plugins.core.model.Extension;
import plugins.core.model.PluginDescriptor;
import plugins.host.PluginRegistry;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

/**
 * Specific class to load and parse the plugin xml file.
 *
 * @author mriley
 *
 */
public class XmlRegistrationInfoSource extends RegistrationInfoSource {

    private static final String ELEM_EXTENSION = "extension";
    private static final String ELEM_DEPENDENCY = "dependency";
    private static final String ELEM_PLUGIN = "plugin";

    private static final String ATTR_SINGLETON = "singleton";
    private static final String ATTR_IMPL = "impl";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ID = "id";    
    private static final String ATTR_ACTIVATOR = "activator";

    public XmlRegistrationInfoSource(Context hostContext) {
        super(hostContext);
    }

    /**
     * Override of the abstract method from the base class that is called to load the plugin information. In this case
     * it will read the plugin.xml file, parse it and create the dependencies and extensions lists.
     *
     * @throws Exception
     */
    @Override
    protected void doLoadInfo() throws Exception {
        parseXml(getXmlInputStream(pluginContext));        
    }

    /**
     * Loads the plugin xml file directly from the plugin.
     *
     * @param pluginContext context to load the plugin information from
     * @return InputStream an InputStream to read the data from
     * @throws Exception
     */
    protected InputStream getXmlInputStream(Context pluginContext) throws Exception {
        return PluginFileLocator.locatePluginFile(pluginContext);
    }

    /**
     * Parse function that reads the plugin.xml and parses it into the dependency and extension lists
     *
     * @param in input stream to read from and parse
     */
    protected void parseXml(InputStream in) {
        if( in != null ) {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);

                int next = XmlPullParser.END_DOCUMENT;
                while( ( next = parser.next() ) != XmlPullParser.END_DOCUMENT ) {        
                    if ( next == XmlPullParser.START_TAG ) {
                        String name = parser.getName();
                        if( name.equals(ELEM_PLUGIN) ) {
                            String activator = parser.getAttributeValue(null, ATTR_ACTIVATOR);
                            if( !TextUtils.isEmpty(activator) ) {
                                plugin.setActivator(activator);
                            }

                        } else if( name.equals(ELEM_DEPENDENCY) ) {
                            String id = parser.getAttributeValue(null, ATTR_ID);
                            String version = parser.getAttributeValue(null, ATTR_VERSION);
                            if( !TextUtils.isEmpty(id) && !TextUtils.isEmpty(version) ) {
                                PluginDescriptor desc = new PluginDescriptor();
                                desc.setPluginId(id);
                                desc.setVersion(version);
                                deps.add(desc);
                            }

                        } else if( name.equals(ELEM_EXTENSION) ) {
                            String type = parser.getAttributeValue(null, ATTR_TYPE);
                            String impl = parser.getAttributeValue(null, ATTR_IMPL);
                            String singleton = parser.getAttributeValue(null, ATTR_SINGLETON);
                            if( !TextUtils.isEmpty(type) && !TextUtils.isEmpty(impl) ) {
                                Extension extension = new Extension();
                                extension.setType(type);
                                extension.setImpl(impl);
                                extension.setSingleton(Boolean.valueOf(singleton));
                                extensions.add(extension);
                            }
                        }
                    }
                }
            } catch ( Exception e ) {
                Log.e(PluginRegistry.TAG, "Failed to parse " + PluginFileLocator.PLUGIN_FILE + " from package " + pluginContext.getPackageName(), e);
            }
        }
    }
}
