
package com.atakmap.android.widgets;

import android.graphics.Point;
import android.net.Uri;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.xml.XMLUtils;

import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WidgetIcon implements Cloneable {

    public static final String TAG = "WidgetIcon";

    public WidgetIcon(MapDataRef iconRef, Point anchor, int iconWidth,
            int iconHeight) {
        _stateRefs.put(0, iconRef);
        _anchorx = anchor.x;
        _anchory = anchor.y;
        _iconWidth = iconWidth;
        _iconHeight = iconHeight;
    }

    public MapDataRef getIconRef(int state) {// gets "highest" state icon
        MapDataRef r = _stateRefs.get(0);// initially set to default
        int indx = 1;
        while (indx <= state) {
            MapDataRef r2 = _stateRefs.get(indx & state);
            if (r2 != null) {
                r = r2;
            }

            indx = indx << 1;
        }

        // MapDataRef r = _stateRefs.get(state);
        // if (r == null) {
        // r = _stateRefs.get(0);
        // }
        return r;
    }

    public int getAnchorX() {
        return _anchorx;
    }

    public int getAnchorY() {
        return _anchory;
    }

    public int getIconWidth() {
        return _iconWidth;
    }

    public int getIconHeight() {
        return _iconHeight;
    }

    public static class Builder {
        public Builder() {
            _icon = new WidgetIcon();
        }

        public Builder setAnchor(int x, int y) {
            _icon._anchorx = x;
            _icon._anchory = y;
            return this;
        }

        public Builder setSize(int width, int height) {
            _icon._iconWidth = width;
            _icon._iconHeight = height;
            return this;
        }

        public Builder setImageRef(int state, MapDataRef ref) {
            _icon._stateRefs.put(state, ref);
            return this;
        }

        public WidgetIcon build() {
            return _icon.clone();
        }

        private final WidgetIcon _icon;
    }

    @Override
    public WidgetIcon clone() {
        WidgetIcon icon = new WidgetIcon();
        icon._anchorx = _anchorx;
        icon._anchory = _anchory;
        icon._iconWidth = _iconWidth;
        icon._iconHeight = _iconHeight;
        icon._stateRefs = new TreeMap<>(_stateRefs);
        return icon;
    }

    public static WidgetIcon resolveWidgetIcon(ConfigEnvironment config,
            String iconUriString)
            throws IOException, SAXException {
        WidgetIcon icon = null;

        Uri iconUri = Uri.parse(iconUriString);
        String iconPath = iconUri.getPath();
        if (iconPath.toLowerCase(LocaleUtil.getCurrent()).endsWith(".xml")) {
            try (InputStream in = config.getMapAssets().getInputStream(iconUri)) {
                icon = _parseIconXml(config, in);
            }
        } else if (iconUriString.startsWith("base64:/")) {
            MapDataRef ref = MapDataRef.parseUri(iconUriString);
            icon = new WidgetIcon(ref, new Point(15, 15), 32, 32);
        } else {
            // assume it's an image
            /*
             * Uri uri = resolver.getBaseUri(); uri = uri.buildUpon().appendPath(iconFile).build();
             */
            Uri uri = Uri.parse("asset:///" + iconPath);
            MapDataRef ref = MapDataRef.parseUri(uri.toString());
            icon = new WidgetIcon(ref, new Point(15, 15), 32, 32);
        }

        return icon;
    }

    private static WidgetIcon _parseIconXml(ConfigEnvironment config,
            InputStream stream)
            throws SAXException, IOException {
        WidgetIcon icon = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(stream);

            Node rootNode = doc.getDocumentElement();
            icon = _parseIconNode(config, rootNode);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }
        return icon;
    }

    private static WidgetIcon _parseIconNode(ConfigEnvironment config,
            Node iconNode) {
        WidgetIcon.Builder b = new WidgetIcon.Builder();

        NamedNodeMap attrs = iconNode.getAttributes();
        _parseAnchorAttr(config, b, attrs.getNamedItem("anchor"));
        _parseSizeAttr(config, b, attrs.getNamedItem("size"));

        NodeList nl = iconNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); ++i) {
            Node stateNode = nl.item(i);
            String nodeName = stateNode.getNodeName();
            if (nodeName.equals("state")) {
                _parseStateNode(config, b, stateNode);
            }
        }

        return b.build();
    }

    private static void _parseStateNode(ConfigEnvironment config,
            WidgetIcon.Builder b,
            Node stateNode) {
        NamedNodeMap attrs = stateNode.getAttributes();
        Node flagsNode = attrs.getNamedItem("flags");
        int state = 0;
        if (flagsNode != null) {
            // state = resolver.resolveFlags("state", flagsNode.getNodeValue(), state);
            state = FlagsParser.parseFlags(config.getFlagsParserParameters(),
                    flagsNode.getNodeValue());
        }

        NodeList nl = stateNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); ++i) {
            Node itemNode = nl.item(i);
            if (itemNode.getNodeName().equals("image")) {
                Node valueNode = itemNode.getFirstChild();

                /*
                 * Uri uri = resolver.getBaseUri(); uri =
                 * uri.buildUpon().appendPath(valueNode.getNodeValue()).build(); MapDataRef ref =
                 * MapDataRef.parseUri(uri.toString());
                 */
                Uri uri = Uri.parse("asset:///" + valueNode.getNodeValue());
                MapDataRef ref = MapDataRef.parseUri(uri.toString());
                b.setImageRef(state, ref);
            }
        }
    }

    private static void _parseAnchorAttr(ConfigEnvironment config,
            WidgetIcon.Builder b,
            Node anchorAttr) {
        try {
            String v = anchorAttr.getNodeValue();
            String[] parts = v.split(",");
            b.setAnchor(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            Log.e(TAG, "Caught exception ", e);
        }
    }

    private static void _parseSizeAttr(ConfigEnvironment config,
            WidgetIcon.Builder b, Node sizeAttr) {
        try {
            String v = sizeAttr.getNodeValue();
            String[] parts = v.split(",");
            b.setSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception ex) {
            Log.e(TAG, "Caught exception ", ex);
        }
    }

    private WidgetIcon() {
    }

    private int _anchorx;
    private int _anchory;
    private int _iconWidth;
    private int _iconHeight;
    private Map<Integer, MapDataRef> _stateRefs = new TreeMap<>();
}
