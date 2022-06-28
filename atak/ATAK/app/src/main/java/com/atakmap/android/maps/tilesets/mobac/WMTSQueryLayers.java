
package com.atakmap.android.maps.tilesets.mobac;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.res.XmlResourceParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * <p>
 * Concrete class extending the QueryLayers abstract class, providing an implementation for
 * querying a WMTS server's GetCapabilities operation.
 * </p>
 * <p>
 * WMTS Services provide a set of layers which link against TileMatrixSets that represent
 * the different scale and style layers that will be served to the user. TileMatrixSets may
 * be referenced by multiple layers.
 * </p>
 * <p>
 * WMTS Services do not allow for nested child layers.
 * </p>
 * <p>
 * The WMTS specification can be found here:
 * http://www.opengeospatial.org/standards/wmts
 * </p>
 */
public class WMTSQueryLayers extends QueryLayers {
    private static final String TAG = "WMTSQueryLayers";

    // Enum containing the currently supported GetTile protocols.
    private enum GetTileProtocol {
        KVP,
        REST
    }

    // Stores the protocol used by this map server.
    private GetTileProtocol supportedProtocol = null;

    // TileMatrixSets can be referenced by multiple layers, so parse
    // them once and store them here to be accessed by layers as they are
    // constructed.
    private final Map<String, List<TileMatrix>> matrixMap = new HashMap<>();

    /**
     * <p>
     * Create a new WMTSQueryLayers object with the given URL string that points to a WMTS server.
     * baseURLStr may be missing the protocol, in which case HTTP is assumed and "http://" will be
     * pre-pended to the URL string.
     * </p>
     * No processing or network access is done at this time except for URL syntax validation;
     * process() must be called to actually query the WMS.
     * 
     * @param baseURLStr the URL of the WMS server.
     * @throws IllegalArgumentException if baseURLStr is not a valid URL, the above protocol
     *             exception notwithstanding.
     */
    public WMTSQueryLayers(String baseURLStr) {
        if (!baseURLStr.contains("://"))
            baseURLStr = "http://" + baseURLStr;

        try {
            this.baseURL = new URL(baseURLStr);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL string");
        }
    }

    protected Map<String, List<TileMatrix>> getMatrixMap() {
        return matrixMap;
    }

    private boolean validateResults() {
        if (layers == null || layers.isEmpty()) {
            Log.d(TAG, "failed validation, no layers returned");
            return false;
        }

        if (matrixMap.isEmpty()) {
            Log.d(TAG, "failed validation, no matrixMap entries");
            return false;
        }

        if (serviceTitle == null || serviceTitle.equals("")) {
            Log.d(TAG, "failed validation, no serviceTitle returned");
            return false;
        }

        if (mimeTypes == null || mimeTypes.isEmpty()) {
            Log.d(TAG, "failed validation, no mineTypes");
            return false;
        }

        return true;
    }

    @Override
    public void process() throws IOException {

        // Try to request GetCapabilities in the KVP style
        try {
            layers = new LinkedList<>();

            String queryStr = "service=WMTS&request=GetCapabilities&version=1.0.0";

            String curQuery = baseURL.getQuery();

            // if the user-provided URL already has a query string, append to it
            String urlStr;
            if (curQuery == null || curQuery.trim().isEmpty())
                urlStr = baseURL.toString() + "?" + queryStr;
            else
                urlStr = baseURL.toString() + "&" + queryStr;

            URL url;
            try {
                url = new URL(urlStr);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid URL constructed? "
                        + e.getMessage());
            }

            parseGetCapabilities(url);
            if (!validateResults()) {
                throw new IOException("Invalid data returned via KVP.");
            }

            return;
        } catch (IOException e) {
            Log.d(TAG, "Couldn't access KVP GetCapapabilities", e);
        }

        // Try to request GetCapabilities using the REST style
        try {
            layers = new LinkedList<>();

            String queryStr = "WMTS/1.0.0/WMTSCapabilities.xml";

            String curQuery = baseURL.toString();

            if (!curQuery.endsWith("/")) {
                curQuery += "/";
            }

            URL url;
            try {
                url = new URL(curQuery + queryStr);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid URL constructed? "
                        + e.getMessage());
            }

            parseGetCapabilities(url);

            if (!validateResults()) {
                throw new IOException("Invalid data returned via KVP.");
            }

            return;
        } catch (IOException e) {
            Log.d(TAG, "Couldn't access REST GetCapapabilities", e);
        }

        throw new IOException(
                "Couldn't parse WMTS GetCapabilities using KVP or REST.");
    }

    /**
     * Parse the result of a call to a WMTS server's GetCapabilies request.
     */
    private void parseGetCapabilities(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "TAK");
        conn.setUseCaches(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        // support authenticated connections
        InputStream input;
        if (conn instanceof HttpURLConnection) {
            AtakAuthenticationHandlerHTTP.Connection connection;
            connection = AtakAuthenticationHandlerHTTP
                    .makeAuthenticatedConnection(
                            (HttpURLConnection) conn, 3, true,
                            AtakAuthenticationHandlerHTTP.UNAUTHORIZED_ONLY);
            conn = connection.conn;
            input = connection.stream;
        } else {
            conn.connect();
            input = conn.getInputStream();
        }

        // Parse the actual data with the PullParser
        XmlPullParser parser = null;
        try {
            parser = XMLUtils.getXmlPullParser();
            parser.setInput(input, null);

            int eventType;
            do {
                eventType = parser.next();
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.getName().equalsIgnoreCase("Layer")) {
                        WebMapLayer layer = parseLayer(parser);
                        layers.add(layer);
                        Log.d(TAG, "Found Layer: " + layer.getName());
                    } else if (parser.getName().equalsIgnoreCase(
                            "ServiceIdentification")) {
                        parseService(parser);
                    } else if (parser.getName().equalsIgnoreCase(
                            "OperationsMetadata")) {
                        parseGetTile(parser);
                    } else if (parser.getName().equalsIgnoreCase(
                            "TileMatrixSet")) {
                        parseTileMatrixSet(parser);
                    }
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);
        } catch (XmlPullParserException e) {
            throw new IOException(
                    "Could not parse WMTS capabilities for: " + url, e);
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                    ((XmlResourceParser) parser).close();
            }

            if (input != null)
                input.close();
        }
    }

    /**
     * Parse a <Layer> tag of the WMTS GetCapabilities response.
     */
    private WebMapLayer parseLayer(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        String name = null;
        String title = null;
        GeoBounds bounds = null;
        Collection<Style> styles = new ArrayList<>();

        Set<String> mimeTypes = new HashSet<>();

        List<String> tileMatrixSetLinks = new ArrayList<>();

        int eventType;
        String inTag;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    inTag = parser.getName();
                    if (inTag.equalsIgnoreCase("WGS84BoundingBox")
                            || inTag.equalsIgnoreCase("boundingBox")) {
                        GeoBounds parsedBounds = parseBoundingBox(parser);
                        if (parsedBounds != null) {
                            bounds = parsedBounds;
                        }
                    } else if (inTag.equalsIgnoreCase("Style")) {
                        styles.add(parseStyle(parser));
                    } else if (inTag.equalsIgnoreCase("TileMatrixSetLink")) {
                        tileMatrixSetLinks.add(parseTileMatrixSetLinks(parser));
                    } else {
                        tagStack.push(inTag);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("Identifier")) {
                        name = parser.getText();
                    } else if (inTag.equalsIgnoreCase("Title")) {
                        title = parser.getText();
                    } else if (inTag.equalsIgnoreCase("Format")) {
                        mimeTypes.add(parser.getText());
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }

        } while (!tagStack.isEmpty());

        if (title == null)
            throw new IOException("Invalid WMTS Capabilities: No Title found.");

        // Construct a new WebMapLayer from the parsed results.
        WMTSWebMapLayer layer = new WMTSWebMapLayer(name, title, this, bounds,
                styles);
        layer.setTileMatrixSetLinks(tileMatrixSetLinks);
        layer.setGetTileProtocol(supportedProtocol);

        this.mimeTypes = mimeTypes;

        return layer;
    }

    /**
     * Parse a <TileMatrixSetLink> tag of the WMS GetCapabilities response.
     */
    private String parseTileMatrixSetLinks(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        String tagName = parser.getName();
        tagStack.push(tagName);

        String returnTileMatrixSet = "";

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    String inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("TileMatrixSet")) {
                        returnTileMatrixSet = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        return returnTileMatrixSet;
    }

    /**
     * Parse a <TileMatrixSet> tag of the WMS GetCapabilities response.
     */
    private void parseTileMatrixSet(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        String tagName = parser.getName();
        tagStack.push(tagName);

        String tileMatrixIdentifier = null;
        List<TileMatrix> tileMatricies = new ArrayList<>();

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (parser.getName().equalsIgnoreCase("TileMatrix")) {
                        TileMatrix parsedMatrix = parseTileMatrix(parser);
                        if (parsedMatrix != null) {
                            tileMatricies.add(parsedMatrix);
                        }
                    } else {
                        tagStack.push(parser.getName());
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    String inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("Identifier")) {
                        tileMatrixIdentifier = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        if (tileMatrixIdentifier != null) {
            matrixMap.put(tileMatrixIdentifier, tileMatricies);
        }
    }

    /**
     * Parse a <TileMatrix> tag of the WMS GetCapabilities response.
     */
    private TileMatrix parseTileMatrix(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        String tagName = parser.getName();
        tagStack.push(tagName);

        // If we find some invalid data while we read the TileMatrix,
        // We still need to walk the rest of the tree so that
        // we don't get the tagStack messed up in a parent function.
        // When invalid data shows up, set this flag to true. If
        // it is true at the end of the function, return a null value.
        boolean invalidMatrix = false;

        TileMatrix returnMatrix = new TileMatrix();

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    String inTag = tagStack.peek();
                    try {
                        if (inTag.equalsIgnoreCase("Identifier")) {
                            returnMatrix.identifier = parser.getText();
                        } else if (inTag.equalsIgnoreCase("ScaleDenominator")) {
                            returnMatrix.scaleDenominator = Double
                                    .parseDouble(parser.getText());
                        } else if (inTag.equalsIgnoreCase("TopLeftCorner")) {
                            returnMatrix.topLeftCorner = parser.getText();
                        } else if (inTag.equalsIgnoreCase("TileWidth")) {
                            returnMatrix.tileWidth = Integer.parseInt(parser
                                    .getText());
                        } else if (inTag.equalsIgnoreCase("TileHeight")) {
                            returnMatrix.tileHeight = Integer.parseInt(parser
                                    .getText());
                        } else if (inTag.equalsIgnoreCase("MatrixWidth")) {
                            returnMatrix.matrixWidth = Integer.parseInt(parser
                                    .getText());
                        } else if (inTag.equalsIgnoreCase("MatrixHeight")) {
                            returnMatrix.matrixHeight = Integer.parseInt(parser
                                    .getText());
                        }
                    } catch (NumberFormatException e) {
                        invalidMatrix = true;
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        if (invalidMatrix) {
            return null;
        }

        return returnMatrix;
    }

    /**
     * Parse a <Style> tag of the WMS GetCapabilities response.
     */
    private Style parseStyle(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        String tagName = parser.getName();
        tagStack.push(tagName);

        Style returnStyle = null;

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    String inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("Identifier")) {
                        returnStyle = new Style(parser.getText(),
                                parser.getText());
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        return returnStyle;
    }

    /**
     * Parse a <BoundingBox> or <WGS84BoundingBox> tag of the WMS GetCapabilities response.
     */
    private GeoBounds parseBoundingBox(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        String tagName = parser.getName();
        tagStack.push(tagName);

        String upperCorner = null;
        String lowerCorner = null;
        String crs = parser.getAttributeValue(null, "crs");
        String dimensions = parser.getAttributeValue(null, "dimensions");

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    String inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("UpperCorner")) {
                        upperCorner = parser.getText();
                    } else if (inTag.equalsIgnoreCase("LowerCorner")) {
                        lowerCorner = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        // TODO: Add support for non WGS84 bounding boxes to WMTS server support.
        if (!tagName.equalsIgnoreCase("WGS84BoundingBox") &&
                (crs != null || dimensions != null)) {
            Log.d(TAG,
                    "Non WGS84 bounding boxes are not supported yet for WMTS Servers.");
            return null;
        } else if (upperCorner == null || lowerCorner == null) {
            Log.d(TAG,
                    "Not enough corner information in bounding box for WMTS Server.");
            return null;
        }

        String[] uc = upperCorner.split(" ");
        String[] lc = lowerCorner.split(" ");

        if (uc.length < 2 || lc.length < 2) {
            Log.d(TAG,
                    "WMTS Corners require at least two items to parse lat/lon");
            return null;
        }

        GeoBounds result = new GeoBounds(Double.parseDouble(lc[1]),
                Double.parseDouble(lc[0]),
                Double.parseDouble(uc[1]), Double.parseDouble(uc[0]));

        return result;
    }

    /**
     * Parse <Service> tag.
     */
    private void parseService(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        int eventType;
        String inTag;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    if (inTag.equalsIgnoreCase("Title")) {
                        serviceTitle = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

    }

    /**
     * Parse out information related to the GetTile operation, including its url and
     * supported protocols.
     */
    private void parseGetTile(XmlPullParser parser) throws IOException,
            XmlPullParserException {

        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        List<String[]> availableUrls = new ArrayList<>();

        int eventType;
        String lastOperation = "";
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String inTag = parser.getName();
                    if (inTag.equals("Operation")) {
                        lastOperation = parser.getAttributeValue(null, "name");
                        tagStack.push(inTag);
                    } else if (inTag.equalsIgnoreCase("Get") &&
                            tagStack.size() == 4 &&
                            lastOperation.equalsIgnoreCase("GetTile")) {
                        availableUrls.add(parseGetTileGet(parser));
                    } else {
                        tagStack.push(inTag);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    inTag = tagStack.pop();

                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        // Check the available URLs for their supported protocols. Favor 
        // REST over KVP since REST requests are more strictly specified
        // and therefore more likely to have been cached by the server.
        for (String[] parts : availableUrls) {
            if (parts[0].toLowerCase(LocaleUtil.getCurrent())
                    .contains("rest")) {
                this.getMapURL = parts[1];
                supportedProtocol = GetTileProtocol.REST;
                return;
            }
        }

        for (String[] parts : availableUrls) {
            if (parts[0].toLowerCase(LocaleUtil.getCurrent()).contains("kvp")) {
                this.getMapURL = parts[1];
                supportedProtocol = GetTileProtocol.KVP;
                return;
            }
        }

        // TODO: Add support for the SOAP protocol for WMTS servers.

        // If we get to here, the server doesn't provide any supported protocols.
        throw new IllegalStateException(
                "WMTS only supports REST and KVP protocols for GetTile operation.");
    }

    /**
     * Parse the <Get> tag for a GetTile operation.
     */
    private String[] parseGetTileGet(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        // Parts of the URL to return. The first element in this array will be the protocol name
        // (RESTful, KVP, SOAP) and the second element will be the url which serves requests
        // of the specified type.
        String[] returnUrl = new String[2];

        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        // We should be starting at the <Get> tag, which contains an href attribute
        // that contains the URL for accessing this service.
        returnUrl[1] = parser.getAttributeValue(null, "href");

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());

                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();

                    break;
                case XmlPullParser.TEXT:
                    String tagName = tagStack.peek();
                    if (tagName.equalsIgnoreCase("Value")) {
                        returnUrl[0] = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        return returnUrl;
    }

    // ////////////////// WebMapLayer Implementation //////////////////

    /**
     * Concrete implementation of the WebMapLayer abstract class that may
     * be used for specifying Layers served by a WMTS. 
     */
    private static class WMTSWebMapLayer extends WebMapLayer {

        private List<String> tileMatrixSetLinks = new ArrayList<>();

        private GetTileProtocol protocol = GetTileProtocol.KVP;

        public WMTSWebMapLayer(String name, String title,
                WMTSQueryLayers queryLayer,
                GeoBounds bounds,
                Collection<Style> styles) {
            super(name, title, queryLayer, bounds, null, null, styles);
        }

        public void setTileMatrixSetLinks(List<String> setLinks) {
            tileMatrixSetLinks = setLinks;
        }

        public void setGetTileProtocol(GetTileProtocol protocol) {
            this.protocol = protocol;
        }

        /**
         * This function makes
         * sure the user-provided URL ends with ? or & (as appropriate) so that this appending works
         * properly.
         */
        private String fixupURL(String url) {
            try {
                URL u = new URL(url);
                String queryString = u.getQuery();
                if (queryString == null)
                    url = url + "?";
                else if (!queryString.isEmpty() && !queryString.endsWith("&"))
                    url = url + "&";
            } catch (MalformedURLException mfe) {
                Log.d("CustomWmsMobacMapSource",
                        "Malformed GetMap URL (" + url + "): "
                                + mfe.getMessage());
            }

            return url;
        }

        /**
         * Searches for the greatest common prefix shared between two strings. For example,
         * if passed the two strings "EPSG:3857:17" and "EPSG:3857:18" this method will return
         * the value "EPSG:3857:", which is shared by both strings. If the passed strings
         * do not have a shared prefix, this method will return "".
         */
        private String greatestCommonPrefix(String a, String b) {
            int minLength = Math.min(a.length(), b.length());
            for (int i = 0; i < minLength; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return a.substring(0, i);
                }
            }
            return a.substring(0, minLength);
        }

        /**
         * Searches through all of the TileMatricies within a TileMatrixSet and finds
         * a common prefix shared by all of them. It also checks to make sure that
         * the values that follow the shared prefix are parameterizable so that these
         * names can be used in a MOBAC template. For example, if the TileMatrixSet consists
         * of three TileMatricies, "EPSG:3857:0", "EPSG:3857:1", and "EPSG:3857:2" this method
         * will return "EPSG:3857:", which will allow a template like "EPSG:3857:{$z}" to be 
         * constructed from it. 
         * 
         * NOTE: The WMTS spec allows for values that don't allow for templating. Currently, 
         * if the TileMatrixSet contains unique, non-templatable names 
         * (i.e. something like "50cm", "1m", "10m") that cannot be inserted into a MOBAC xml
         * file, then this method will throw an IllegalStateException.
         */
        private String findCommonPrefix(String tileMatrixSet) {
            // It's OK to make this cast because the constructor for WMTSWebMapLayer only accepts
            // WMTSQueryLayers objects.
            Map<String, List<TileMatrix>> matrixMap = ((WMTSQueryLayers) queryLayer)
                    .getMatrixMap();
            List<TileMatrix> matricies = matrixMap.get(tileMatrixSet);

            // Check to see if all of the zoom levels have a shared prefix.
            String sharedPrefix = "";
            if (matricies.size() > 0) {
                sharedPrefix = matricies.get(0).identifier;
                for (int i = 1; i < matricies.size(); i++) {
                    sharedPrefix = greatestCommonPrefix(sharedPrefix,
                            matricies.get(i).identifier);
                }
            }

            // Check to see if we can insert numbers after the prefix so that these names can be used
            // as a URL template in a MOBAC xml file.
            boolean templatable = true;
            if (matricies.size() > 0) {
                for (int i = 0; i < matricies.size(); i++) {
                    templatable = templatable
                            && matricies.get(i).identifier.matches(sharedPrefix
                                    + "\\d+$");
                }
            } else {
                templatable = false;
            }

            // If we can't create a template from the TileMatrix names, throw an exception.
            if (!templatable) {
                throw new IllegalStateException(
                        "Unique TileMatrix identifiers that are not indexed are not yet supported");
            }

            return sharedPrefix;
        }

        /**
         * Create a templated URL for a RESTful WMTS service. A RESTful URL is of the format
         * http://www.someservice.com/WMTS/tile/1.0.0/{Layer}/{Style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.{format}
         */
        private String getWMTSRestUrl(Style style, String format,
                String tileMatrixSet) {
            String fullUrl = queryLayer.getGetMapURL();

            if (!fullUrl.endsWith("/")) {
                fullUrl += "/";
            }

            fullUrl += name + "/";

            String styleName;
            if (style == null)
                styleName = "default";
            else
                styleName = style.getName();

            fullUrl += styleName + "/";

            fullUrl += tileMatrixSet + "/";

            String sharedPrefix = findCommonPrefix(tileMatrixSet);

            fullUrl += sharedPrefix + "{$z}/";
            fullUrl += "{$y}/{$x}";

            if (format.equalsIgnoreCase("image/png")) {
                fullUrl += ".png";
            } else if (format.equalsIgnoreCase("image/jpeg")
                    || format.equalsIgnoreCase("image/jpg")) {
                fullUrl += ".jpg";
            } else {
                throw new IllegalStateException(
                        "No suitable image formats supported by this server");
            }

            return fullUrl;
        }

        /**
         * Create a templated URL for a KVP WMTS service. A KVP URL is of the format
         * http://www.someservice.com?service=WMTS&request=GetTile&version=1.0.0&layer={Layer}&
         *     style={Style}&format={Format}&TileMatrixSet={TileMatrixSet}&TileMatrix={TileMatrix}&
         *     TileRow={TileRow}&TileCol={TileCol}
         */
        private String getWMTSKVPUrl(Style style, String format,
                String tileMatrixSet) {
            String fullUrl = fixupURL(queryLayer.getGetMapURL());

            fullUrl += "service=WMTS&request=GetTile&version=1.0.0";

            fullUrl += "&layer=" + name;

            String styleName;
            if (style == null)
                styleName = "default";
            else
                styleName = style.getName();
            fullUrl += "&style=" + styleName;

            fullUrl += "&format=" + format;

            fullUrl += "&TileMatrixSet=" + tileMatrixSet;

            String sharedPrefix = findCommonPrefix(tileMatrixSet);

            fullUrl += "&TileMatrix=" + sharedPrefix + "{$z}";
            fullUrl += "&TileRow={$y}";
            fullUrl += "&TileCol={$x}";

            fullUrl = fullUrl.replaceAll("&", "&amp;");

            return fullUrl;
        }

        /**
         * Write an XML file describing this layer that may be used with the Mobac map layer reader.
         * The layer will be written out with the given style. If 'style' is null, the style name
         * "default" will be used.
         * 
         * @param style the style with which this layer should be displayed in the created XML file.
         * @return the File that was created.
         * @throws IOException in case of error
         */
        @Override
        public File writeMobacXML(Style style) throws IOException {
            if (!isDisplayable())
                throw new IllegalStateException("Layer is not displayable");

            // pick a suitable mime type ...
            Set<String> types = queryLayer.getMimeTypes();
            String format;
            if (types.contains("image/jpeg") || types.contains("image/jpg"))
                format = "image/jpeg";
            else if (types.contains("image/png"))
                format = "image/png";
            else
                throw new IllegalStateException(
                        "No suitable image formats supported by this server");

            // generate a filename and get rid of non-standard characters
            String baseFile = queryLayer.getServiceTitle() +
                    "-" +
                    title;
            baseFile = baseFile.replaceAll("\\W+", "-");

            // handle the possibility that the generated filename already exists
            File f;
            int index = 0;
            String suffix = "";
            while (true) {
                String filename = baseFile + suffix +
                        ".xml";
                f = new File(FileSystemUtils
                        .getItem("imagery/mobile/mapsources"),
                        FileSystemUtils.sanitizeWithSpacesAndSlashes(filename));
                if (IOProviderFactory.exists(f)) {
                    suffix = String.valueOf(index++);

                    // give up after a while
                    if (index >= 100)
                        throw new IllegalStateException(
                                "Could not generate filename for map server layer output");
                } else {
                    break;
                }
            }

            String tileMatrixSet = "";
            if (tileMatrixSetLinks.contains("EPSG:3857")) {
                // Favor web mercator projection where available.
                tileMatrixSet = "EPSG:3857";
            } else if (tileMatrixSetLinks.size() > 0) {
                tileMatrixSet = tileMatrixSetLinks.get(0);
            } else {
                throw new IllegalStateException(
                        "No suitable tile matrix sets supported by this server");
            }

            // Find out how many levels are in the TileMatrixSet. It's OK to make this 
            //cast because the constructor for WMTSWebMapLayer only accepts
            // WMTSQueryLayers objects.
            Map<String, List<TileMatrix>> matrixMap = ((WMTSQueryLayers) queryLayer)
                    .getMatrixMap();
            int numMatrixLevels = matrixMap.get(tileMatrixSet).size();

            // Generate a URL that matches the TileProtocol used by this
            // Layer.
            String url = "";
            if (protocol.equals(GetTileProtocol.KVP)) {
                url = getWMTSKVPUrl(style, format, tileMatrixSet);
            } else if (protocol.equals(GetTileProtocol.REST)) {
                url = getWMTSRestUrl(style, format, tileMatrixSet);
            } else {
                throw new IllegalStateException(
                        "Currently only KVP and REST protocols are supported for WMTS servers.");
            }

            // and print out the XML itself.
            try (Writer w = IOProviderFactory.getFileWriter(f);
                    PrintWriter out = new PrintWriter(w)) {
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                out.println("<customMapSource>");
                out.println("    <name>" + title + " on "
                        + queryLayer.getServiceTitle()
                        + "</name>");
                out.println("    <minZoom>0</minZoom>");
                out.println("    <maxZoom>" + numMatrixLevels + "</maxZoom>");
                out.println("    <tileType>" + format + "</tileType>");
                out.println("    <url>" + url + "</url>");
                out.println("    <tileUpdate>false</tileUpdate>");
                out.println("    <backgroundColor>#000000</backgroundColor>");
                out.println("    <ignoreErrors>false</ignoreErrors>");
                out.println("    <serverParts></serverParts>");
                out.println("</customMapSource>");
            }

            return f;
        }
    }

    private static class TileMatrix {
        public String identifier;
        public double scaleDenominator;
        public String topLeftCorner;
        public int tileWidth;
        public int tileHeight;
        public int matrixWidth;
        public int matrixHeight;
    }

}
