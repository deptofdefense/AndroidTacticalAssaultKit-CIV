
package com.atakmap.android.widgets;

import android.graphics.Color;
import android.graphics.Point;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.xml.XMLUtils;

import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WidgetItemListParser {

    public static final String TAG = "WidgetItemListParser";

    public void setCurrentDirectory(String directory) {
        _currentDirectory = directory;
    }

    public void setStateBitfield(String name, int value) {
        _stateBits.put(name.toLowerCase(LocaleUtil.getCurrent()), value);
    }

    public WidgetItemList parse(String filePath) throws IOException,
            SAXException {
        WidgetItemList list = null;
        try (FileInputStream fis = IOProviderFactory
                .getInputStream(
                        new File(_currentDirectory + filePath))) {
            list = parse(fis);
        }
        return list;
    }

    public WidgetItemList parse(InputStream stream) throws SAXException,
            IOException {
        WidgetItemList list = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(stream);

            // items
            NodeList pnl = doc.getElementsByTagName("item");
            WidgetItem[] items = new WidgetItem[pnl.getLength()];

            for (int i = 0; i < pnl.getLength(); ++i) {
                Node pn = pnl.item(i);
                items[i] = _parseItem(pn);
            }

            list = new WidgetItemList(items);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }

        return list;
    }

    private WidgetItem _parseItem(Node itemNode) throws IOException,
            SAXException {

        WidgetItem.Builder b = null;

        NamedNodeMap attrs = itemNode.getAttributes();

        Node baseAttr = attrs.getNamedItem("base");
        if (baseAttr != null) {
            String basePath = baseAttr.getNodeValue();
            b = _getBaseBuilder(basePath);
            if (b == null) {
                throw new IOException(
                        "Unable to get base builder from path: " + basePath);
            }
            b = b.fork();
        } else {
            b = _getBuilderWithDefaults();
        }

        _parseStaticItemAttrs(b, attrs); // iconSize, iconAnchor, text
        _parseStateIconAttrs(b, 0, attrs); // icon, bgColor, etc.

        return b.build();
    }

    private int _parseStateValue(String valueString) {
        int value = 0;
        try {
            value = Integer.parseInt(valueString);
        } catch (Exception ex) {
            String[] bitParts = valueString.split("\\|");
            for (String bp : bitParts) {
                String lowerBp = bp.toLowerCase(LocaleUtil.getCurrent());
                Integer bf = _stateBits.get(lowerBp);
                if (bf != null) {
                    value |= bf;
                }
            }
        }
        return value;
    }

    private MapDataRef _resolveMapDataRef(String uriString) {
        MapDataRef ref = _refCache.get(uriString);
        if (ref == null) {
            String us = uriString.trim();
            if (us.startsWith("arc:") || us.startsWith("file://")
                    || us.startsWith("res:")) {
                ref = MapDataRef.parseUri(us);
            } else {
                ref = MapDataRef.parseUri("file://" + _currentDirectory + us);
            }
            _refCache.put(uriString, ref);
        }
        return ref;
    }

    private void _parseStateIconAttrs(WidgetItem.Builder b, int state,
            NamedNodeMap attrs) {
        Node iconAttr = attrs.getNamedItem("icon");
        if (iconAttr != null) {
            String iconRefUri = iconAttr.getNodeValue();
            MapDataRef iconRef = _resolveMapDataRef(iconRefUri);
            b.setIconRef(state, iconRef);
        }

        Node bgColorAttr = attrs.getNamedItem("bgColor");
        if (bgColorAttr != null) {
            String bgColorString = bgColorAttr.getNodeValue();
            int backingColor = Color.parseColor(bgColorString);
            b.setBackingColor(state, backingColor);
        }
    }

    private void _parseIconSizeAttrValue(WidgetItem.Builder b, String value) {
        try {
            String[] parts = value.split(",");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            b.setIconSize(width, height);
        } catch (NumberFormatException nfe) {
            // don't ignore
            Log.e(TAG, "Number Format Exception ", nfe);
        }
    }

    private void _parseIconAnchorAttrValue(WidgetItem.Builder b, String value) {
        try {
            String[] parts = value.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            b.setIconAnchor(new Point(x, y));
        } catch (NumberFormatException nfe) {
            Log.e(TAG, " Number format exception ", nfe);
        }
    }

    private WidgetItem.Builder _getBuilderWithDefaults() {
        WidgetItem.Builder b = new WidgetItem.Builder();
        b.setIconSize(32, 32);
        b.setIconAnchor(new Point(15, 15));
        return b;
    }

    private WidgetItem.Builder _getBaseBuilder(String path) throws IOException,
            SAXException {
        WidgetItem.Builder b = _itemCache.get(path);
        if (b == null) {

            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(
                            new File(_currentDirectory + path))) {
                DocumentBuilderFactory dbf = XMLUtils
                        .getDocumenBuilderFactory();

                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(fis);

                NamedNodeMap itemAttrs = doc.getDocumentElement()
                        .getAttributes();
                Node baseAttr = itemAttrs.getNamedItem("base");
                if (baseAttr != null) {
                    String basePath = baseAttr.getNodeValue();
                    b = _getBaseBuilder(basePath);
                    b = b.fork();
                }

                if (b == null) {
                    b = _getBuilderWithDefaults();
                }

                _parseStaticItemAttrs(b, itemAttrs);

                NodeList pnl = doc.getElementsByTagName("state");
                for (int i = 0; i < pnl.getLength(); ++i) {
                    Node pn = pnl.item(i);
                    NamedNodeMap attrs = pn.getAttributes();
                    if (attrs != null) {
                        Node valueAttr = attrs.getNamedItem("value");
                        int value = 0;
                        if (valueAttr != null) {
                            String valueString = valueAttr.getNodeValue();
                            value = _parseStateValue(valueString);
                        }
                        _parseStateIconAttrs(b, value, attrs);
                    }
                }
            } catch (ParserConfigurationException e) {
                Log.e(TAG, "error: ", e);
            }

            _itemCache.put(path, b);
        }

        return b;
    }

    private void _parseStaticItemAttrs(WidgetItem.Builder b,
            NamedNodeMap itemAttrs) {
        Node iconSizeAttr = itemAttrs.getNamedItem("iconSize");
        if (iconSizeAttr != null) {
            String iconSizeValue = iconSizeAttr.getNodeValue();
            _parseIconSizeAttrValue(b, iconSizeValue);
        }

        Node iconAnchorAttr = itemAttrs.getNamedItem("iconAnchor");
        if (iconAnchorAttr != null) {
            String iconAnchorValue = iconAnchorAttr.getNodeValue();
            _parseIconAnchorAttrValue(b, iconAnchorValue);
        }

        Node labelAttr = itemAttrs.getNamedItem("text");
        if (labelAttr != null) {
            String labelText = labelAttr.getNodeValue();
            b.setLabel(labelText);
        }
    }

    private final Map<String, Integer> _stateBits = new HashMap<>();
    private final Map<String, WidgetItem.Builder> _itemCache = new HashMap<>();
    private final Map<String, MapDataRef> _refCache = new HashMap<>();
    private String _currentDirectory = FileSystemUtils.getItem("mods")
            .getPath() + File.separator;
}
