
package com.atakmap.android.menu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.res.AssetManager;
import android.util.Base64;
import android.content.Context;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;

/**
 * Allows for the plugin to load menu items.   Without this class, the plugin would manually need to create the
 * menu item based on obscure rules
 */
public class PluginMenuParser {
    /* a map for storing menus that have already been parsed */
    private static final Map<Context, Map<String, String>> menuMap = new HashMap<>();

    public static final String TAG = "PluginMenuParser";

    /**
     * Return the full XML for the menu based on the pluginContext provided
     * which will allow on a per plugin loading of menu items
     * 
     * @param pluginContext - context
     * @param layout - the layout file to return the XML for
     * @return - the XML with encoded local actions and icons or empty if 
     * the menu is not found
     */
    public static String getMenu(final Context pluginContext,
            final String layout) {
        /* since this can be triggered on different threads loading different markers of
         * the same type, synchronize around the menu map so the parsing will only happen
         * once for each type
         */
        synchronized (menuMap) {

            Map<String, String> cache = menuMap.get(pluginContext);
            if (cache == null) {
                cache = new HashMap<>();
                menuMap.put(pluginContext, cache);
            }

            if (cache.containsKey(layout))
                return cache.get(layout);

            Log.d(TAG, "attempting to load menu: " + layout);
            //get the list of available actions from the plugin
            AssetManager am = pluginContext.getAssets();

            InputStream layoutIS = null;
            try {
                layoutIS = pluginContext.getAssets().open(layout);

                if (layoutIS == null) {
                    Log.w(TAG, "could not find: " + layout);
                    return "";
                }

                DocumentBuilderFactory docFactory = XMLUtils
                        .getDocumenBuilderFactory();

                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                Document dom = docBuilder.parse(layoutIS);
                Element docEle = dom.getDocumentElement();
                NodeList nl = docEle.getChildNodes();
                if (nl != null) {
                    int length = nl.getLength();
                    //check each element to see if it's a button
                    for (int i = 0; i < length; i++) {
                        if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
                            Element el = (Element) nl.item(i);
                            if (el.getNodeName().contains("button")) {
                                String onClick = el.getAttribute("onClick");

                                //check if the onClick action is local to the plugin
                                if (contains(am, onClick)) {
                                    //if the action is a local action, decode it and set the
                                    //attribute to the decoded string
                                    el.setAttribute("onClick",
                                            getItem(pluginContext, onClick));
                                }

                                //parse submenus
                                String menuAttr = el.getAttribute("submenu");
                                if (contains(am, menuAttr)) {
                                    //make recursive call to get submenu
                                    String attr = getMenu(pluginContext,
                                            menuAttr);
                                    el.setAttribute("submenu", attr);
                                }

                                //check to see if the icon is specific to the plugin
                                String iconAttr = el.getAttribute("icon");
                                if (contains(am, iconAttr)) {
                                    el.setAttribute("icon",
                                            getItem(pluginContext, iconAttr));
                                }
                            }
                        }
                    }
                }

                //write out the XML to a string and return
                DOMSource domSource = new DOMSource(dom);
                StringWriter writer = new StringWriter();
                StreamResult result = new StreamResult(writer);

                TransformerFactory tf = XMLUtils.getTransformerFactory();

                Transformer transformer = tf.newTransformer();
                transformer.transform(domSource, result);
                cache.put(layout, writer.toString());
                return writer.toString();
            } catch (IOException | ParserConfigurationException | SAXException
                    | TransformerException ioe) {
                Log.d(TAG, "error: " + ioe);
            } finally {
                if (layoutIS != null)
                    try {
                        layoutIS.close();
                    } catch (IOException ignored) {
                    }
            }

            return "";
        }
    }

    private static boolean contains(final AssetManager am, final String file) {
        InputStream is = null;
        try {
            is = am.open(file);
            return true;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }

    }

    /**
     * Return the menu item based on the on the pluginContext provided
     * which can be used to create a menu.
     *
     * @param pluginContext - context
     * @param file - the asset file that is the item
     * @return - the XML with encoded item or empty if not found.
     */
    public static String getItem(final Context pluginContext,
            final String file) {
        InputStream is = null;
        try {
            is = pluginContext.getAssets().open(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int size;
            byte[] buffer = new byte[1024];

            while ((size = is.read(buffer, 0, 1024)) >= 0) {
                outputStream.write(buffer, 0, size);
            }
            is.close();
            buffer = outputStream.toByteArray();

            return "base64://"
                    + new String(Base64.encode(buffer, Base64.URL_SAFE
                            | Base64.NO_WRAP), FileSystemUtils.UTF8_CHARSET);

        } catch (Exception e) {
            return "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
