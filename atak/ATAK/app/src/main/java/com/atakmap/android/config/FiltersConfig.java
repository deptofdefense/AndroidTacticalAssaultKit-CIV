
package com.atakmap.android.config;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * A XML definable structure for filtering data Bundles based on the values contained.
 * 
 * 
 */
public class FiltersConfig {

    public static final String TAG = "FiltersConfig";

    /**
     * An individual Filter in the list of filters
     */
    public class Filter {

        /**
         * Get the filter value
         * 
         * @return the string filter value
         */
        public final String getValue() {
            return _value;
        }

        /**
         * Test a Bundle against the Filter
         * 
         * @param values the Bundle values
         * @return true if the bundle matches the filter
         */
        final boolean testFilter(Map<String, Object> values) {
            boolean r = true;
            for (_KeyValue v : _vars) {
                Object dataValue = values.get(v.key);
                if (dataValue == null) {
                    r = false;
                    break;
                }

                Comparator<Object> cmp = _comparators.get(v.key);
                if (cmp != null && 0 != cmp.compare(v.value, dataValue)) {
                    r = false;
                    break;
                } else if (cmp == null
                        && !v.value.equals(dataValue.toString())) {
                    r = false;
                    break;
                }
            }

            return r;
        }

        private String _value;
        private _KeyValue[] _vars;
    }

    /**
     * Set the comparator to use on a given filter attribute/key.
     * 
     * @param key the filter attribute key
     * @param cmp the Comparator
     */
    public void setComparator(String key, Comparator<Object> cmp) {
        _comparators.put(key, cmp);
    }

    public static class StringStartsWithComparator implements
            Comparator<Object>, Serializable {

        @Override
        public int compare(Object filterValue, Object bundleValue) {
            int r = -1;
            if (bundleValue instanceof String) {
                if (((String) bundleValue).startsWith((String) filterValue)) {
                    r = 0;
                }
            }
            return r;
        }

    }

    private static class _KeyValue {
        String key;
        String value;
    }

    /**
     * Parse the XML filter definition from an InputStream.
     * 
     * @param stream the input stream
     * @return a new FiltersConfig
     * @throws SAXException if an xml parsing error has occurred
     * @throws IOException if the file cannot be read.
     */
    public static FiltersConfig parseFromStream(InputStream stream)
            throws SAXException,
            IOException {
        FiltersConfig config = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(stream);

            Node rootNode = doc.getDocumentElement();
            FiltersConfig c = new FiltersConfig();
            c._parseMenusConfigFromNode(rootNode);
            config = c;
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }

        return config;
    }

    public Filter lookupFilter(final Map<String, Object> data) {
        Filter r = null;
        for (Filter e : _entries) {
            if (e.testFilter(data)) {
                r = e;
            }
        }
        return r;
    }

    private void _parseMenusConfigFromNode(Node root) {
        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); ++i) {
            Node child = nl.item(i);
            NamedNodeMap attrs;
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && (attrs = child.getAttributes()) != null) {
                if (child.getNodeName().equals("filter")) {
                    _KeyValue[] vars = _parseFilterVars(attrs);
                    String value = child.getFirstChild().getNodeValue();
                    Filter f = new Filter();
                    f._value = value;
                    f._vars = vars;
                    _entries.add(f);
                }
            }
        }
    }

    private static _KeyValue[] _parseFilterVars(NamedNodeMap attrs) {
        _KeyValue[] vars = new _KeyValue[attrs.getLength()];
        for (int i = 0; i < attrs.getLength(); ++i) {
            Node a = attrs.item(i);
            _KeyValue kv = new _KeyValue();
            kv.key = a.getNodeName();
            kv.value = a.getNodeValue();
            vars[i] = kv;
        }
        return vars;
    }

    private final HashMap<String, Comparator<Object>> _comparators = new HashMap<>();
    private final List<Filter> _entries = new LinkedList<>();

}
