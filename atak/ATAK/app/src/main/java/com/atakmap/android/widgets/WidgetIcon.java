
package com.atakmap.android.widgets;

import android.graphics.Point;
import android.net.Uri;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.UriMapDataRef;
import com.atakmap.annotations.DeprecatedApi;
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

import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.commons.graphics.Icon;

/** @deprecated use {@link gov.tak.platform.commons.graphics.Icon} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class WidgetIcon implements Cloneable, IIcon {

    public static final String TAG = "WidgetIcon";

    public WidgetIcon(MapDataRef iconRef, Point anchor, int iconWidth,
            int iconHeight) {
        this(new Icon.Builder()
                .setImageUri(Icon.STATE_DEFAULT,
                        iconRef != null ? iconRef.toUri() : null)
                .setAnchor(anchor.x, anchor.y)
                .setSize(iconWidth, iconHeight)
                .build());
    }

    public MapDataRef getIconRef(int state) {// gets "highest" state icon
        final String uri = _impl.getImageUri(state);
        return (uri != null) ? new UriMapDataRef(uri) : null;
    }

    @Override
    public final String getImageUri(int state) {// gets "highest" state icon
        return _impl.getImageUri(state);
    }

    @Override
    public final int getColor(int state) {
        return _impl.getColor(state);
    }

    @Override
    public final Set<Integer> getStates() {
        return _impl.getStates();
    }

    @Override
    public int getAnchorX() {
        return _impl.getAnchorX();
    }

    @Override
    public int getAnchorY() {
        return _impl.getAnchorY();
    }

    public int getIconWidth() {
        return _impl.getWidth();
    }

    public int getIconHeight() {
        return _impl.getHeight();
    }

    @Override
    public final int getWidth() {
        return _impl.getWidth();
    }

    @Override
    public final int getHeight() {
        return _impl.getHeight();
    }

    public static class Builder {
        public Builder() {
            _impl = new Icon.Builder();
        }

        public Builder setAnchor(int x, int y) {
            _impl.setAnchor(x, y);
            return this;
        }

        public Builder setSize(int width, int height) {
            _impl.setSize(width, height);
            return this;
        }

        public Builder setImageRef(int state, MapDataRef ref) {
            _impl.setImageUri(state, (ref != null) ? ref.toUri() : null);
            return this;
        }

        public WidgetIcon build() {
            return new WidgetIcon(_impl.build());
        }

        private final Icon.Builder _impl;
    }

    @Override
    public WidgetIcon clone() {
        // NOTE: `_impl` is immutable
        return new WidgetIcon(_impl);
    }

    public static WidgetIcon resolveWidgetIcon(ConfigEnvironment config,
            String iconUriString)
            throws IOException, SAXException {
        WidgetIcon icon = null;

        Uri iconUri = Uri.parse(iconUriString);
        String iconPath = iconUri.getPath();
        if (iconPath.toLowerCase(LocaleUtil.getCurrent()).endsWith(".xml")) {
            try (InputStream in = config.getMapAssets()
                    .getInputStream(iconUri)) {
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

    private WidgetIcon(Icon impl) {
        _impl = impl;
    }

    private final Icon _impl;
}
