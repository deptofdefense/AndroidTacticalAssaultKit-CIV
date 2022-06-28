
package com.atakmap.android.action;

import android.net.Uri;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigLoader;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Responsible for loading and creating a MapAction from an xml file from the defined radial menu
 * actions.
 */
public class MapActionFactory {
    private static final ConfigLoader<MapAction> _loader;

    static {
        // simply add action types here

        ConfigLoader<MapAction> loader = new ConfigLoader<>();
        loader.addFactory("broadcast", new BroadcastIntentMapAction.Factory());
        loader.addFactory("activity", new BroadcastIntentMapAction.Factory());
        loader.addFactory("set", new SetMapAction.Factory());
        _loader = loader;
    }

    /**
     * Given an input stream, create the appropriate MapAction to be performed.
     * A MapAction generally is defined series of broadcasts in a set statement.
     * For a listing of the original MapActions please see assets/actions
     * @param is the input stream used
     * @param config the configuration environment
     */
    public static MapAction createFromInputStream(final InputStream is,
            final ConfigEnvironment config)
            throws IOException, ParserConfigurationException, SAXException {
        return _loader.loadFromConfig(is, config);
    }

    /**
     * Given a uri, create the appropriate MapAction to be performed.
     * A MapAction generally is defined series of broadcasts in a set statement.
     * For a listing of the original MapActions please see assets/actions
     * @param configUri the uri used
     * @param config the configuration environment
     */
    public static MapAction createFromUri(final Uri configUri,
            final ConfigEnvironment config)
            throws IOException, ParserConfigurationException, SAXException {
        return _loader.loadFromConfigUri(configUri, config);
    }

    /**
     * Given a string resource, create the appropriate MapAction to be performed.
     * A MapAction generally is defined series of broadcasts in a set statement.
     * For a listing of the original MapActions please see assets/actions
     * @param resource the string resource to be used
     * @param config the configuration environment
     */
    public static MapAction loadFromConfigResource(final String resource,
            final ConfigEnvironment config)
            throws ParserConfigurationException, SAXException, IOException {
        return _loader.loadFromConfigResource(resource, config);
    }

    /**
     * Given a npde element, create the appropriate MapAction to be performed.
     * A MapAction generally is defined series of broadcasts in a set statement.
     * For a listing of the original MapActions please see assets/actions
     * @param elemNode the node element to be used
     * @param config the configuration environment
     */
    public static MapAction createFromElem(final Node elemNode,
            final ConfigEnvironment config) {
        return _loader.loadFromElem(elemNode, config);
    }

}
