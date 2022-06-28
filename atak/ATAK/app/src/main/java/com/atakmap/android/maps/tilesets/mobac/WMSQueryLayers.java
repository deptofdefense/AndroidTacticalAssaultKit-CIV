
package com.atakmap.android.maps.tilesets.mobac;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.math.PointD;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.res.XmlResourceParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Arrays;
import java.util.Iterator;

/**
 * <p>
 * Concrete class extending the QueryLayers abstract class, providing an implementation for
 * querying a WMS server's GetCapabilities operation.
 * </p>
 * <p>
 * WMS layers may form a tree structure. Non-leaf nodes may be displayable as layers in their own
 * right, or not. The layer metadata this class provides is returned in a recursive structure to
 * represent that tree. The Layer.isDisplayable() method may be called to determine if a layer is a
 * displayable layer, rather than just a "container" layer.
 * </p>
 * <p>
 * The WMS specification can be found here:
 * http://cite.opengeospatial.org/teamengine/about/wms/1.1.1/site/
 * and 
 * http://cite.opengeospatial.org/teamengine/about/wms/1.3.0/site/
 * </p>
 *
 * 
 */
public class WMSQueryLayers extends QueryLayers {

    public final static String TAG = "WMSQueryLayers";

    /**
     * <p>
     * Create a new WMSQueryLayers object with the given URL string that points to a WMS server.
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
    public WMSQueryLayers(String baseURLStr) {
        if (!baseURLStr.contains("://"))
            baseURLStr = "http://" + baseURLStr;

        try {
            this.baseURL = new URL(baseURLStr);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL string");
        }
    }

    /**
     * <p>
     * Create a new WMSQueryLayers object with the given URL that points to a WMS server.
     * </p>
     * No processing or network access is done at this time except for URL syntax validation;
     * process() must be called to actually query the WMS.
     * 
     * @param baseURL the URL of the WMS server.
     */
    public WMSQueryLayers(URL baseURL) {
        this.baseURL = baseURL;
    }

    @Override
    public synchronized void process() throws IOException {
        layers = new LinkedList<>();

        String urlStr = baseURL.toString();

        // if the GetCapabilities is not specified as part of the url, then just go ahead and 
        // assume it is 1.1.1

        if (!baseURL.toString().contains("GetCapabilities")) {
            String queryStr = "service=WMS&request=GetCapabilities&version=1.1.1";

            String curQuery = baseURL.getQuery();

            // if the user-provided URL already has a query string, append to it
            if (curQuery == null || curQuery.trim().isEmpty())
                urlStr = baseURL.toString() + "?" + queryStr;
            else
                urlStr = baseURL.toString() + "&" + queryStr;
        }

        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid URL constructed? "
                    + e.getMessage());
        }

        parseCapabilities(url);

        isProcessed = true;
    }

    /**
     * Connect to WMS server, set up XML parser, and begin parsing
     */
    private void parseCapabilities(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "TAK");
        conn.setUseCaches(true);
        conn.setConnectTimeout(15000);
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

        String version = "1.1.1";
        XmlPullParser parser = null;
        try {
            parser = XMLUtils.getXmlPullParser();
            parser.setInput(input, null);

            int eventType;
            do {
                eventType = parser.next();
                if (eventType == XmlPullParser.START_TAG) {
                    switch (parser.getName()) {
                        case "WMS_Capabilities":
                            version = parser.getAttributeValue(null, "version");
                            break;
                        case "Layer":
                            WebMapLayer layer = parseLayer(parser, version);
                            layers.add(layer);
                            break;
                        case "Service":
                            parseService(parser);
                            break;
                        case "GetMap":
                            parseGetMap(parser);
                            break;
                    }
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);
        } catch (XmlPullParserException e) {
            throw new IOException("Could not parse WMS capabilities");
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                    ((XmlResourceParser) parser).close();
            }

            if (input != null)
                try {
                    input.close();
                } catch (IOException ignored) {
                }
        }

    }

    /**
     * Parse the <Layer> tag of the WMS response.
     */
    private WebMapLayer parseLayer(XmlPullParser parser, String version)
            throws IOException,
            XmlPullParserException {
        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        String name = null;
        String title = null;
        GeoBounds bounds = null;
        List<WebMapLayer> children = null;
        Collection<Style> styles = null;
        Set<Integer> srids = null;

        int eventType;
        String inTag;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    inTag = parser.getName();
                    switch (inTag) {
                        case "Layer":
                            if (children == null)
                                children = new LinkedList<>();

                            WebMapLayer layer = parseLayer(parser, version);
                            children.add(layer);
                            break;
                        case "Style":
                            Style style = parseStyle(parser);
                            if (style != null) {
                                if (styles == null)
                                    styles = new LinkedList<>();
                                styles.add(style);
                            }
                            break;
                        default:
                            tagStack.push(inTag);

                            if (inTag.equals("LatLonBoundingBox")
                                    || inTag.equals("BoundingBox")) {
                                try {
                                    double maxLat = Double.parseDouble(
                                            parser.getAttributeValue(null,
                                                    "maxy"));
                                    double minLat = Double.parseDouble(
                                            parser.getAttributeValue(null,
                                                    "miny"));
                                    double maxLong = Double.parseDouble(
                                            parser.getAttributeValue(null,
                                                    "maxx"));
                                    double minLong = Double.parseDouble(
                                            parser.getAttributeValue(null,
                                                    "minx"));
                                    bounds = new GeoBounds(minLat, minLong,
                                            maxLat, maxLong);
                                } catch (NumberFormatException ex) {
                                    Log.e(TAG, "Error parsing value: "
                                            + ex);
                                }
                            }
                            break;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    switch (inTag) {
                        case "Name":
                            name = parser.getText();
                            break;
                        case "Title":
                            title = parser.getText();
                            break;
                        case "SRS":
                        case "CRS":
                            String[] tokens = parser.getText().split(" ");
                            for (String token : tokens) {
                                // for this specific case rewrite the CRS to EPSG
                                if (token.equals("CRS:84"))
                                    token = "EPSG:4326";

                                if (token.startsWith("EPSG:")) {
                                    try {
                                        int srid = Integer.parseInt(token
                                                .substring(5));
                                        if (srids == null)
                                            srids = new HashSet<>();
                                        srids.add(srid);
                                    } catch (NumberFormatException e) {
                                        Log.e(TAG, "Invalid SRID: "
                                                + token);
                                    }
                                }
                            }
                            break;
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }

        } while (!tagStack.isEmpty());

        if (title == null)
            throw new IOException("Invalid WMS Capabilities");

        WebMapLayer layer = new WMSWebMapLayer(name, title, this, bounds,
                srids,
                children, styles, version);
        if (children != null) {
            for (WebMapLayer child : children)
                child.setParent(layer);
        }

        return layer;
    }

    /**
     * Parse <Style> tag
     */
    private Style parseStyle(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        String name = null;
        String title = null;

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
                    if (inTag.equals("Name"))
                        name = parser.getText();
                    else if (inTag.equals("Title"))
                        title = parser.getText();
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        if (name != null && title != null)
            return new Style(name, title);
        else
            return null;
    }

    /**
     * Parse <Service> tag.
     */
    private void parseService(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        String title = null;

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
                    if (inTag.equals("Name")) {
                        String name = parser.getText();

                        // XXX do anything about this?
                        if (!name.equals("OGC:WMS"))
                            Log.d(TAG, "WMS - Invalid Service Name!");
                    } else if (inTag.equals("Title")) {
                        title = parser.getText();
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        serviceTitle = title;
    }

    /**
     * Parse <GetMap> tag.
     */
    private void parseGetMap(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        String url = null;
        Set<String> mimeTypes = new HashSet<>();

        int urlTree = 0;

        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        int eventType;
        String inTag;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    inTag = tagStack.push(parser.getName());
                    // The URL to be used in a GetMap call is specified
                    // in the tree GetMap/DCPType/HTTP/Get/OnlineResource,
                    // so this is just a poor-man's state machine to keep
                    // track of our location
                    if ((urlTree == 0 && inTag.equals("DCPType")) ||
                            (urlTree == 1 && inTag.equals("HTTP")) ||
                            (urlTree == 2 && inTag.equals("Get"))) {
                        urlTree++;
                    } else if (urlTree == 3 && inTag.equals("OnlineResource")) {
                        url = parser.getAttributeValue(null, "xlink:href");
                    }
                    break;
                case XmlPullParser.END_TAG:
                    inTag = tagStack.pop();
                    // undo our GetMap URL "state machine"
                    if ((urlTree == 3 && inTag.equals("Get")) ||
                            (urlTree == 2 && inTag.equals("HTTP")) ||
                            (urlTree == 1 && inTag.equals("DCPType"))) {
                        urlTree--;
                    }
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    if (inTag.equals("Format")) {
                        mimeTypes.add(parser.getText());
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new IOException("Unexpected end of document");
                default:
                    break;
            }
        } while (!tagStack.isEmpty());

        this.mimeTypes = mimeTypes;
        this.getMapURL = url;
    }

    private static int computeMinZoom(int srid, GeoBounds bounds) {
        Projection proj = ProjectionFactory.getProjection(srid);
        if (proj == null)
            proj = WebMercatorProjection.INSTANCE;

        final PointD minProjUL = proj.forward(
                new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()),
                null);
        final PointD maxProjLR = proj.forward(
                new GeoPoint(proj.getMinLatitude(), proj.getMaxLongitude()),
                null);

        final PointD bndsProjUL = proj.forward(new GeoPoint(bounds.getNorth(),
                bounds.getWest()), null);
        final PointD bndsProjLR = proj.forward(new GeoPoint(bounds.getSouth(),
                bounds.getEast()), null);

        final double scaleX = Math.abs((bndsProjLR.x - bndsProjUL.x)
                / (maxProjLR.x - minProjUL.x));
        final double scaleY = Math.abs((bndsProjUL.y - bndsProjLR.y)
                / (minProjUL.y - maxProjLR.y));
        final double scale = Math.max(scaleX, scaleY);

        return (int) Math.ceil(Math.log(1d / scale) / Math.log(2d));
    }

    /**
     * Responsible for producing an aggregate WebMapLayer
     * Tested with: https://geoint.nrlssc.navy.mil/maritime/wms?REQUEST=GetCapabilities&VERSION=1.3.0&SERVICE=WMS
     * @param title supplied title for the aggregrate.
     * @param layers the layers to aggregate together.
     */
    public static WebMapLayer constructAggregate(final String title,
            final List<WebMapLayer> layers) {
        String name;
        String aStyle;

        if (layers == null || layers.size() == 0) {
            // do nothing
        } else if (layers.size() == 1) {
            WebMapLayer o = layers.get(0);
            if (o instanceof WMSWebMapLayer)
                return o;
        } else {
            WMSWebMapLayer first = null;
            StringBuilder aStyleBuilder = new StringBuilder();
            StringBuilder nameBuilder = new StringBuilder();
            for (Object o : layers) {
                if (o instanceof WMSWebMapLayer) {
                    WMSWebMapLayer layer = (WMSWebMapLayer) o;
                    if (first == null)
                        first = layer;

                    if (layer.getVersion().equals("1.3.0") &&
                            layer.queryLayer.getGetMapURL()
                                    .equals(first.queryLayer.getGetMapURL())
                            && compare(layer.getSRIDs(), first.getSRIDs())) {

                        // agregate the names
                        if (nameBuilder.length() != 0)
                            nameBuilder.insert(0, ",");
                        nameBuilder.insert(0, layer.getName());

                        // agregate the styles
                        Iterator<Style> styles = layer.getStyles().iterator();
                        String firstStyleValue = "";
                        if (styles.hasNext())
                            firstStyleValue = styles.next().getName();

                        if (aStyleBuilder.length() != 0)
                            aStyleBuilder.insert(0, ",");
                        aStyleBuilder.insert(0, firstStyleValue);

                    }
                }
            }
            name = nameBuilder.toString();
            aStyle = aStyleBuilder.toString();

            if (first != null && !(name.length() == 0)) {
                String t = title;
                if (t == null || t.length() == 0)
                    t = first.title;

                Collection<Style> styleCollection = new LinkedList<>();
                styleCollection.add(new Style(aStyle, aStyle));

                return new WMSWebMapLayer(name, t, first.queryLayer,
                        first.bounds, first.srids, first.children,
                        styleCollection, first.version);
            }
        }
        return null;
    }

    /**
     * Determine integer set equality
     */
    private static boolean compare(Set<Integer> first, Set<Integer> second) {
        return first.containsAll(second) && second.containsAll(first);
    }

    // ////////////////// WebMapLayer Implementation //////////////////

    /**
     * WebMapLayer implementation providing support for WMS services.
     */
    private static class WMSWebMapLayer extends WebMapLayer {
        private final String version;

        public WMSWebMapLayer(final String name,
                final String title,
                final QueryLayers queryLayer,
                final GeoBounds bounds,
                final Set<Integer> srids,
                final List<WebMapLayer> children,
                final Collection<Style> styles,
                final String version) {
            super(name, title, queryLayer, bounds, srids, children, styles);
            this.version = version;
        }

        public String getVersion() {
            return version;
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

            String styleName;
            if (style == null)
                styleName = "";
            else
                styleName = style.getName();

            // pick a suitable mime type ...
            Set<String> types = queryLayer.getMimeTypes();
            String format;
            if (types.contains("image/png"))
                format = "png";
            else if (types.contains("image/jpeg"))
                format = "jpg";
            else {
                Log.e(TAG,
                        "No suitable image formats supported by this server, found: "
                                + types);
                throw new IllegalStateException(
                        "No suitable image formats supported by this server");
            }

            // ... and a suitable srid
            Set<Integer> srids = getSRIDs();
            int srid;
            if (srids.contains(WebMercatorProjection.INSTANCE
                    .getSpatialReferenceID())) {
                srid = WebMercatorProjection.INSTANCE.getSpatialReferenceID();
            } else if (srids.contains(900913)) {
                srid = 900913;
            } else if (srids.contains(EquirectangularMapProjection.INSTANCE
                    .getSpatialReferenceID())) {
                srid = EquirectangularMapProjection.INSTANCE
                        .getSpatialReferenceID();
            } else {
                srid = -1;
                for (Integer candidateSrid : srids) {
                    Projection proj = ProjectionFactory
                            .getProjection(candidateSrid);
                    if (proj != null) {
                        srid = candidateSrid;
                        break;
                    }
                }

                if (srid == -1) {
                    Log.d(TAG,
                            "No suitable spatial reference systems supported by this server, found: "
                                    + Arrays.toString(srids.toArray()));
                    throw new IllegalStateException(
                            "No suitable spatial reference system supported by this server");
                }
            }

            final GeoBounds myBounds = getBounds();

            // XXX - want to configure the minimum zoom level for WMS that may
            //       have restricted coverage -- algorithm appears to yield an
            //       acceptable result, however, performance gets crushed.
            //       investigation into DatasetDescriptorSpi and renderer is
            //       needed before enabling
            //final int minZoom = computeMinZoom(srid, myBounds);
            final int minZoom = 0;

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

            // and print out the XML itself.
            try (FileWriter w = IOProviderFactory.getFileWriter(f);
                    PrintWriter out = new PrintWriter(w)) {
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                out.println("<customWmsMapSource>");
                out.println("    <name>" + title + " on "
                        + queryLayer.getServiceTitle()
                        + "</name>");
                out.println("    <version>" + version + "</version>");
                out.println("    <minZoom>" + minZoom + "</minZoom>");
                out.println(
                        "    <maxZoom>" + Math.max(minZoom, 23) + "</maxZoom>");
                out.println("    <tileType>" + format + "</tileType>");
                out.println("    <url>" + queryLayer.getGetMapURL() + "</url>");
                out.println("    <coordinatesystem>EPSG:" + srid
                        + "</coordinatesystem>");
                out.println("    <layers>" + name + "</layers>");
                out.println("    <styles>" + styleName + "</styles>");
                out.println("    <north>" + myBounds.getNorth() + "</north>");
                out.println("    <east>" + myBounds.getEast() + "</east>");
                out.println("    <south>" + myBounds.getSouth() + "</south>");
                out.println("    <west>" + myBounds.getWest() + "</west>");
                out.println("    <backgroundColor>#000000</backgroundColor>");
                if (format.equals("png"))
                    out.println(
                            "    <aditionalparameters>&amp;transparent=true</aditionalparameters>");
                out.println("</customWmsMapSource>");
            }

            return f;
        }
    }

    /**
        // ////////////////// Unit testing ///////////////////////
    
        public static void main(String[] args) throws Exception {
            WMSQueryLayers query = new WMSQueryLayers(args[0]);
            List<WebMapLayer> layers = query.getLayers();
            for (WebMapLayer layer : layers) {
                printLayer(layer, "");
            }
        }
    
        private static void printLayer(WebMapLayer layer, String prefix) {
    
            StringBuffer sb = new StringBuffer();
            sb.append(prefix);
            if (layer.isDisplayable())
                sb.append("+");
            sb.append(layer.getTitle());
            if (layer.isDisplayable())
                sb.append(" (").append(layer.getName()).append(")");
            sb.append(" ").append(layer.getSRIDs());
            Log.d(TAG, sb.toString());
    
            for (WebMapLayer child : layer.getChildren())
                printLayer(child, prefix + "  ");
        }
    **/
}
