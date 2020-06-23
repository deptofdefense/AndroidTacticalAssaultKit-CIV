
package com.atakmap.android.config;

import android.net.Uri;

import com.atakmap.android.maps.assets.MapAssets;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import com.atakmap.coremap.log.Log;

/**
 * Loads complex objects of a given type by delegating to registered ConfigFactory objects.
 * 
 * 
 * @param <T>
 */
public class ConfigLoader<T> {

    private final static String TAG = "ConfigLoader";

    private final HashMap<String, ConfigFactory<T>> _factories = new HashMap<>();

    public void addFactory(String elemName, ConfigFactory<T> factory) {
        _factories.put(elemName, factory);
    }

    public T loadFromConfigUri(Uri configUri,
            ConfigEnvironment configEnvironment)
            throws IOException, ParserConfigurationException, SAXException {
        MapAssets mapAssets = configEnvironment.getMapAssets();
        InputStream in = mapAssets.getInputStream(configUri);
        return loadFromConfig(in, configEnvironment);

    }

    public T loadFromConfig(InputStream in,
            ConfigEnvironment configEnvironment)
            throws IOException, ParserConfigurationException, SAXException {
        T t = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true);
            } catch (Exception ignore) {
                Log.d(TAG, "exception occured setting property on factory");
            }
            try {
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (Exception ignored) {
            }

            final DocumentBuilder db = dbf.newDocumentBuilder();
            if (in != null) {
                Document doc = db.parse(in);
                t = loadFromElem(doc.getDocumentElement(), configEnvironment);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                    Log.d(TAG, "failed to close the stream");
                }
            }
        }
        return t;
    }

    public T loadFromElem(Node elemNode, ConfigEnvironment configEnvironment) {
        T t = null;
        ConfigFactory<T> factory = _factories.get(elemNode.getNodeName());
        if (factory != null) {
            t = factory.createFromElem(configEnvironment, elemNode);
        }
        return t;
    }

}
