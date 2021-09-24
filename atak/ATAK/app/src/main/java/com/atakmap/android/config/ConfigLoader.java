
package com.atakmap.android.config;

import android.net.Uri;
import android.util.Base64;

import com.atakmap.android.maps.assets.MapAssets;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.util.zip.IoUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.atakmap.coremap.xml.XMLUtils;

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

    public T loadFromConfigResource(String resource,
            ConfigEnvironment configEnvironment)
            throws IOException, ParserConfigurationException, SAXException {
        if (resource.endsWith(".xml")) {
            return loadFromConfigUri(Uri.parse(resource), configEnvironment);
        } else if (resource.startsWith("base64:/")) {
            resource = resource.substring(8);
            if (resource.startsWith("/")) {
                resource = resource.substring(1);
            }
            final byte[] a = Base64.decode(resource, Base64.URL_SAFE
                    | Base64.NO_WRAP);
            return loadFromConfig(new ByteArrayInputStream(a),
                    configEnvironment);
        } else {
            return loadFromConfig(new ByteArrayInputStream(
                    resource.getBytes(FileSystemUtils.UTF8_CHARSET)),
                    configEnvironment);
        }
    }

    public T loadFromConfig(InputStream in,
            ConfigEnvironment configEnvironment)
            throws IOException, ParserConfigurationException, SAXException {
        T t = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

            final DocumentBuilder db = dbf.newDocumentBuilder();
            if (in != null) {
                Document doc = db.parse(in);
                t = loadFromElem(doc.getDocumentElement(), configEnvironment);
            }
        } finally {
            IoUtils.close(in, TAG, "failed to close the stream");
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
