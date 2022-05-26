package gov.tak.platform.widgets.config;

import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.MapDataRef;
import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.widgets.IWidgetBackground;
import gov.tak.platform.commons.graphics.Icon;
import gov.tak.platform.config.ConfigEnvironment;
import gov.tak.platform.config.FlagsParser;
import gov.tak.platform.config.PhraseParser;
import gov.tak.platform.utils.XMLUtils;
import gov.tak.platform.widgets.AbstractButtonWidget;
import gov.tak.platform.widgets.WidgetBackground;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.io.UriFactory;

/**
 * Resolves Config based resources
 */
public class ConfigResolver {

    public static final String TAG = "ConfigResolver";

    /**
     * Resolve a ConfigEnvironment defined IIcon instance
     *
     * @param config
     * @param textValue
     * @return
     */
    public static IIcon resolveIcon(ConfigEnvironment config, String textValue) {
        String iconUri = textValue;
        if (iconUri != null) {
            if (config.getPhraseParserParameters() != null) {
                iconUri = PhraseParser.expandPhrase(iconUri,
                        config.getPhraseParserParameters());
            }
            try {
                ConfigEnvironment config2 = config.buildUpon()
                        .setFlagsParameters(getStateFlagsParams())
                        .build();
                IIcon icon = _resolveWidgetIcon(config2, iconUri);
                return icon;
            } catch (Exception ex) {
                Log.e(TAG, "error: {}", ex);
            }
        }

        return null;
    }

    /**
     * Resolve a ConfigEnvironment defined IWidgetBackground instance
     *
     * @param config
     * @param textValue
     * @return
     */
    public static IWidgetBackground resolveWidgetBackground(ConfigEnvironment config, String textValue) {
        IWidgetBackground bg = null;
        String bgRef = textValue;

        if (bgRef != null) {
            try {
                ConfigEnvironment config2 = config.buildUpon()
                        .setFlagsParameters(getStateFlagsParams())
                        .build();
                bg = WidgetBackground.resolveWidgetBackground(config2,
                        bgRef);
            } catch (Exception e) {
                Log.e(TAG, "error: {}", e);
            }
        }
        return bg;
    }

    /**
     * Get flag parameters for widget state
     *
     * @return
     */
    public static final FlagsParser.Parameters getStateFlagsParams() {
        FlagsParser.Parameters params = new FlagsParser.Parameters();
        params.setFlagBits("disabled", AbstractButtonWidget.STATE_DISABLED);
        params.setFlagBits("selected", AbstractButtonWidget.STATE_SELECTED);
        params.setFlagBits("pressed", AbstractButtonWidget.STATE_PRESSED);
        return params;
    }

    private static IIcon _resolveWidgetIcon(ConfigEnvironment config,
                                            String iconUriString)
            throws IOException, SAXException {
        IIcon icon = null;

        URI iconUri = URI.create(iconUriString);
        String iconPath = iconUri.getPath();
        if (iconPath.toLowerCase(LocaleUtil.getCurrent()).endsWith(".xml")) {
            if(iconUri.getScheme() == null)
                iconUriString = "asset:///" + iconUriString;
            try (UriFactory.OpenResult in = UriFactory.open(iconUriString)) {
                icon = _parseIconXml(config, in.inputStream);
            }
        } else if (iconUriString.startsWith("base64:/")) {
            MapDataRef ref = MapDataRef.parseUri(iconUriString);
            icon = new Icon.Builder()
                    .setImageUri(IIcon.STATE_DEFAULT, ref.toUri())
                    .setAnchor(15, 15)
                    .setSize(32, 32)
                    .build();
        } else {
            // assume it's an image
            /*
             * Uri uri = resolver.getBaseUri(); uri = uri.buildUpon().appendPath(iconFile).build();
             */
            URI uri = URI.create("asset:///" + iconPath);
            MapDataRef ref = MapDataRef.parseUri(uri.toString());
            icon = new Icon.Builder()
                    .setImageUri(IIcon.STATE_DEFAULT, ref.toUri())
                    .setAnchor(15, 15)
                    .setSize(32, 32)
                    .build();
        }

        return icon;
    }

    private static IIcon _parseIconXml(ConfigEnvironment config,
                                       InputStream stream)
            throws SAXException, IOException {
        IIcon icon = null;
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumentBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(stream);

            Node rootNode = doc.getDocumentElement();
            icon = _parseIconNode(config, rootNode);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: {}", e);
        }
        return icon;
    }

    private static IIcon _parseIconNode(ConfigEnvironment config,
                                        Node iconNode) {
        Icon.Builder b = new Icon.Builder();

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
                                        Icon.Builder b,
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
                URI uri = URI.create("asset:///" + valueNode.getNodeValue());
                MapDataRef ref = MapDataRef.parseUri(uri.toString());
                b.setImageUri(state, ref.toUri());
            }
        }
    }

    private static void _parseAnchorAttr(ConfigEnvironment config,
                                         Icon.Builder b,
                                         Node anchorAttr) {
        try {
            String v = anchorAttr.getNodeValue();
            String[] parts = v.split(",");
            b.setAnchor(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            Log.e(TAG, "error: {}", e);
        }
    }

    private static void _parseSizeAttr(ConfigEnvironment config,
                                       Icon.Builder b, Node sizeAttr) {
        try {
            String v = sizeAttr.getNodeValue();
            String[] parts = v.split(",");
            b.setSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception ex) {
            Log.e(TAG, "error: {}", ex);
        }
    }
}
