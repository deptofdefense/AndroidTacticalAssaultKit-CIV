
package com.atakmap.android.widgets;

import android.graphics.Color;
import android.net.Uri;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import gov.tak.api.widgets.IWidgetBackground;

@Deprecated
@DeprecatedApi(since = "4.4")
public class WidgetBackground implements IWidgetBackground {

    public static final String TAG = "WidgetBackground";

    public WidgetBackground(int defaultColor) {
        _colors.put(0, defaultColor);
    }

    public static WidgetBackground resolveWidgetBackground(
            ConfigEnvironment config,
            String bgUriString) throws SAXException, IOException {
        WidgetBackground bg = null;

        // InputStream in = cfgRes.resolveInputStream(bgPath);
        Uri bgUri = Uri.parse(bgUriString);
        try (InputStream in = config.getMapAssets().getInputStream(bgUri)) {
            bg = _parseBackgroundXml(config, in);
        }
        return bg;
    }

    private static WidgetBackground _parseBackgroundXml(
            ConfigEnvironment config, InputStream in)
            throws SAXException, IOException {
        WidgetBackground bg = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(in);

            Node rootNode = doc.getDocumentElement();
            bg = _parseBackgroundNode(config, rootNode);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }
        return bg;
    }

    private static WidgetBackground _parseBackgroundNode(
            ConfigEnvironment config, Node bgNode) {
        WidgetBackground.Builder b = new WidgetBackground.Builder();

        NodeList nl = bgNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); ++i) {
            Node stateNode = nl.item(i);
            String nodeName = stateNode.getNodeName();
            if (nodeName.equals("state")) {
                _parseBgStateNode(config, b, stateNode);
            }
        }

        return b.build();
    }

    private static void _parseBgStateNode(ConfigEnvironment config,
            WidgetBackground.Builder b,
            Node stateNode) {
        NamedNodeMap attrs = stateNode.getAttributes();
        Node flagsNode = attrs.getNamedItem("flags");
        int state = 0;
        if (flagsNode != null) {
            // state = config.resolveFlags("state", flagsNode.getNodeValue(), 0);
            state = FlagsParser.parseFlags(config.getFlagsParserParameters(),
                    flagsNode.getNodeValue());
        }

        NodeList nl = stateNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); ++i) {
            Node itemNode = nl.item(i);
            if (itemNode.getNodeName().equals("color")) {
                try {
                    Node valueNode = itemNode.getFirstChild();
                    b.setColor(state,
                            Color.parseColor(valueNode.getNodeValue()));
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
    }

    public int getColor(int state) {// gets "highest" state color
        Integer r = _colors.get(0);

        int indx = 1;
        while (indx <= state) {// otherwise, use the state color
            Integer r2 = _colors.get(indx & state);
            if (r2 != null) {
                r = r2;
            }

            indx = indx << 1;
        }

        // Integer r = _colors.get(state);
        // if (r == null) {
        // r = _colors.get(0);
        // }
        return r == null ? 0 : r;
    }

    public WidgetBackground copy() {
        WidgetBackground bg = new WidgetBackground();
        bg._colors = new TreeMap<>(_colors);
        return bg;
    }

    public static class Builder {
        public Builder setColor(int state, int color) {
            _bg._colors.put(state, color);
            return this;
        }

        public WidgetBackground build() {
            return _bg.copy();
        }

        private final WidgetBackground _bg = new WidgetBackground();
    }

    private WidgetBackground() {
        _colors.put(0, 0);
    }

    private Map<Integer, Integer> _colors = new TreeMap<>();
}
