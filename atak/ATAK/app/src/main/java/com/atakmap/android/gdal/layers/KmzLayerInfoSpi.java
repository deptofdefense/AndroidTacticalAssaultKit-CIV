
package com.atakmap.android.gdal.layers;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.mosaic.ATAKMosaicDatabase3;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseBuilder2;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryRasterLayer2;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.spi.InteractiveServiceProvider;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.xmlpull.v1.XmlPullParser;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

public class KmzLayerInfoSpi extends AbstractDatasetDescriptorSpi {

    public static final String GROUND_OVERLAY = "GroundOverlay";

    static {
        NativeImageryRasterLayer2.registerDatasetType("kmz");
    }

    public static final String TAG = "KmzLayerInfoSpi";

    private final static Set<String> LATLON_BOX_MINIMUM_TAGS = new HashSet<>();
    static {
        LATLON_BOX_MINIMUM_TAGS.add("north");
        LATLON_BOX_MINIMUM_TAGS.add("south");
        LATLON_BOX_MINIMUM_TAGS.add("east");
        LATLON_BOX_MINIMUM_TAGS.add("west");
    }

    private final static Set<String> LATLON_BOX_PARSE_TAGS = new HashSet<>();
    static {
        LATLON_BOX_PARSE_TAGS.add("north");
        LATLON_BOX_PARSE_TAGS.add("south");
        LATLON_BOX_PARSE_TAGS.add("east");
        LATLON_BOX_PARSE_TAGS.add("west");
        LATLON_BOX_PARSE_TAGS.add("rotation");
    }

    private final static Set<String> LATLON_QUAD_PARSE_TAGS = new HashSet<>();
    static {
        LATLON_QUAD_PARSE_TAGS.add("coordinates");
    }

    public static final FileFilter KML_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return FileSystemUtils.checkExtension(f, "kml");
        }
    };

    public final static DatasetDescriptorSpi INSTANCE = new KmzLayerInfoSpi();

    private KmzLayerInfoSpi() {
        super("kmz", 3);
    }

    @Override
    protected Set<DatasetDescriptor> create(File file, File workingDir,
            InteractiveServiceProvider.Callback callback) {

        // Don't do anything with the callback in this method. This create method just
        // Reads data from a single item in the xml, and it will be fast
        // enough that it isn't worth reporting the completion of individual items.

        if (!(file instanceof ZipVirtualFile)) {
            try {
                file = new ZipVirtualFile(file);
            } catch (Throwable e) {
                if (file != null && IOProviderFactory.exists(file)) {
                    Log.w(TAG,
                            "Unable to open KMZ as a zip file: "
                                    + e.getMessage() + " "
                                    + file.getAbsolutePath());
                } else {
                    Log.w(TAG,
                            "Unable to open KMZ as a zip file: "
                                    + e.getMessage() + " null");
                }
                return null;
            }
        }

        Set<DatasetDescriptor> retval = new HashSet<>();

        XmlPullParser parser = null;
        InputStream inputStream = null;
        try {
            ZipVirtualFile docFile = findDocumentKML(file);
            if (docFile == null)
                return null;
            inputStream = docFile.openStream();
            parser = XMLUtils.getXmlPullParser();
            parser.setInput(inputStream, null);

            int eventType;
            DatasetDescriptor info;
            do {
                eventType = parser.next();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equals(GROUND_OVERLAY)) {
                            info = parseGroundOverlay((ZipVirtualFile) file,
                                    parser, workingDir);
                            if (info != null)
                                retval.add(info);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        break;
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

            if (retval.size() < 1)
                return null;
            else if (retval.size() == 1)
                return retval;

            DatasetDescriptor mosaic = createMosaic(file, retval, workingDir);
            if (mosaic == null)
                return null;
            return Collections.singleton(mosaic);
        } catch (XmlPullParserException | IllegalArgumentException
                | IOException e) {
            Log.e(TAG, "Unexpected XML error creating KMZ layer", e);
            return null;
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                    ((XmlResourceParser) parser).close();
            }
            if (inputStream != null)
                try {
                    inputStream.close();
                } catch (IOException ie) {
                    Log.e(TAG, "Unexpected Exception closing inputStream ", ie);
                }
        }
    }

    @Override
    protected boolean probe(File file,
            InteractiveServiceProvider.Callback callback) {
        return containsTag(file, GROUND_OVERLAY, callback.getProbeLimit());
    }

    public static boolean containsTag(File kmzFile, String tagName) {
        return containsTag(kmzFile, tagName,
                DatasetDescriptorFactory2.DEFAULT_PROBE);
    }

    private static boolean containsTag(File kmzFile, String tagName,
            int limit) {
        InputStream inputStream = null;
        XmlPullParser parser = null;
        try {

            // If we can successfully open the inputstream to doc.kml,
            // and it has the GroundOverlay element, then this is probably a KMZ layer file.

            ZipVirtualFile docFile = null;

            try {
                docFile = findDocumentKML(kmzFile);
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "not a kmz file: " + kmzFile);
                return false;
            }
            if (docFile == null)
                return false;

            inputStream = docFile.openStream();
            parser = XMLUtils.getXmlPullParser();

            if (parser == null)
                return false;

            parser.setInput(inputStream, null);

            AtomicInteger tagCount = new AtomicInteger(0);

            int eventType;
            do {
                eventType = parser.next();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (tagCount.getAndIncrement() > limit) {
                            return false;
                        }

                        if (parser.getName().equals(tagName)) {
                            return true;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        break;
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

            return false;
        } catch (Throwable e) {
            return false;
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                    ((XmlResourceParser) parser).close();
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    // Ignore error at this point.
                }
            }
        }
    }

    @Override
    public int parseVersion() {
        return 4;
    }

    private static DatasetDescriptor parseGroundOverlay(ZipVirtualFile file,
            XmlPullParser parser,
            File workingDir) throws XmlPullParserException, IOException {
        checkAtTag(parser, GROUND_OVERLAY);

        Stack<String> tagStack = new Stack<>();
        tagStack.push(parser.getName());

        ZipVirtualFile iconFile = null;
        int color = Color.WHITE;
        boolean visible = true;
        boolean coords = false;
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();

        int eventType;
        String inTag;
        boolean quadCoords = false;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (parser.getName().equals("LatLonBox") && !quadCoords) {
                        coords |= getCornerCoordsLatLonBox(parser, ul, ur, lr,
                                ll);
                    } else if (parser.getName().equals("gx:LatLonQuad")) {
                        quadCoords = getCornerCoordsLatLonQuad(parser, ul, ur,
                                lr,
                                ll);
                        coords |= quadCoords;
                    } else {
                        tagStack.push(parser.getName());
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    switch (inTag) {
                        // Image file/link
                        case "href":
                            iconFile = new ZipVirtualFile(file,
                                    parser.getText());
                            break;

                        // Color/alpha modulation
                        case "color": {
                            String colTxt = parser.getText();
                            if (!colTxt.startsWith("#"))
                                colTxt = "#" + colTxt;
                            try {
                                color = Color.parseColor(colTxt);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse color: " + colTxt,
                                        e);
                            }
                            break;
                        }

                        // Visibility toggle
                        case "visibility":
                            visible = parser.getText().equals("1");
                            break;
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if (iconFile == null || !coords)
            return null;

        Map<String, String> extraData = new HashMap<>();

        MosaicDatabase2.Frame frame = tryDecodeAndroid(iconFile);
        if (frame == null) {
            frame = tryDecodeGdal(iconFile);
            if (frame == null)
                return null;
            extraData.put("gdalSubdataset", frame.path);
        }

        extraData
                .put("tilecache", (new File(workingDir, "tilecache.sqlite"))
                        .getAbsolutePath());

        extraData.put("color", String.valueOf(color));
        extraData.put("visible", String.valueOf(visible));

        final double gsd = DatasetDescriptor.computeGSD(frame.width,
                frame.height, ul, ur, lr, ll);
        final int numLevels = 5;

        return new ImageDatasetDescriptor(file.getName(),
                "zip://" + iconFile.getAbsolutePath(),
                "kmz",
                frame.type,
                "kmz",
                frame.width,
                frame.height,
                gsd,
                numLevels,
                ul, ur, lr, ll,
                // XXX - assuming web mercator ???
                WebMercatorProjection.INSTANCE.getSpatialReferenceID(),
                false,
                workingDir,
                extraData);
    }

    private static MosaicDatabase2.Frame tryDecodeAndroid(ZipVirtualFile file) {
        InputStream stream = null;
        try {
            stream = file.openStream();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, opts);
            if (opts.outWidth < 1 || opts.outHeight < 1)
                return null;

            return new MosaicDatabase2.Frame(0,
                    GdalLayerInfo.getURI(file).toString(),
                    "kmz",
                    false,
                    0d, 0d, 0d, 0d,
                    null, null, null, null,
                    0d, 0d,
                    opts.outWidth, opts.outHeight,
                    3857);
        } catch (Throwable t) {
            Log.e("KmzLayerInfoSpi", "Failed to decode " + file.getName()
                    + " using Android graphics API", t);
            return null;
        } finally {
            if (stream != null)
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
        }
    }

    private static MosaicDatabase2.Frame tryDecodeGdal(ZipVirtualFile file) {
        Dataset dataset = null;
        try {
            // try icon first using Android graphics API
            dataset = gdal.Open("/vsizip" + file.getAbsolutePath());
            if (dataset == null)
                return null;

            return new MosaicDatabase2.Frame(0,
                    dataset.GetDescription(),
                    // XXX - if we are going to be part
                    //       of a mosaic, we want the
                    //       type to reflect the driver
                    //       otherwise let it be native
                    //       so we select the vanilla
                    //       GDAL layer renderer
                    //dataset.GetDriver().GetDescription(),
                    "native",
                    false,
                    0d, 0d, 0d, 0d,
                    null, null, null, null,
                    0d, 0d,
                    dataset.GetRasterXSize(),
                    dataset.getRasterYSize(),
                    3857);
        } catch (Throwable t) {
            Log.e("KmzLayerInfoSpi", "Failed to decode " + file.getName()
                    + " using GDAL", t);
            return null;
        } finally {
            if (dataset != null)
                dataset.delete();
        }
    }

    private static boolean getCornerCoordsLatLonBox(XmlPullParser parser,
            GeoPoint ul,
            GeoPoint ur, GeoPoint lr, GeoPoint ll)
            throws XmlPullParserException, IOException {
        final Map<String, String> latlonBox = getTextElements(parser,
                "LatLonBox",
                LATLON_BOX_PARSE_TAGS,
                null);
        if (!latlonBox.keySet().containsAll(LATLON_BOX_MINIMUM_TAGS))
            return false;

        final double north = Double.parseDouble(latlonBox.get("north"));
        final double south = Double.parseDouble(latlonBox.get("south"));
        final double east = Double.parseDouble(latlonBox.get("east"));
        final double west = Double.parseDouble(latlonBox.get("west"));

        ul.set(north, west);
        ur.set(north, east);
        lr.set(south, east);
        ll.set(south, west);

        if (latlonBox.containsKey("rotation")) {
            final double rotation = Double.parseDouble(latlonBox
                    .get("rotation"));

            PointD c = WebMercatorProjection.INSTANCE.forward(
                    GeoPoint.createMutable().set((north + south) / 2.0d,
                            (east + west) / 2.0d),
                    null);

            Matrix m = Matrix.getRotateInstance(Math.toRadians(rotation), c.x,
                    c.y);
            PointD ulp = m.transform(
                    WebMercatorProjection.INSTANCE.forward(ul, null), null);
            PointD urp = m.transform(
                    WebMercatorProjection.INSTANCE.forward(ur, null), null);
            PointD lrp = m.transform(
                    WebMercatorProjection.INSTANCE.forward(lr, null), null);
            PointD llp = m.transform(
                    WebMercatorProjection.INSTANCE.forward(ll, null), null);

            WebMercatorProjection.INSTANCE.inverse(ulp, ul);
            WebMercatorProjection.INSTANCE.inverse(urp, ur);
            WebMercatorProjection.INSTANCE.inverse(lrp, lr);
            WebMercatorProjection.INSTANCE.inverse(llp, ll);
        }

        return true;

    }

    private static boolean getCornerCoordsLatLonQuad(XmlPullParser parser,
            GeoPoint ul,
            GeoPoint ur, GeoPoint lr, GeoPoint ll)
            throws XmlPullParserException, IOException {
        Map<String, String> latlonQuad = getTextElements(parser,
                "gx:LatLonQuad",
                LATLON_QUAD_PARSE_TAGS,
                null);
        if (!latlonQuad.keySet().containsAll(LATLON_QUAD_PARSE_TAGS))
            return false;

        String coordString = latlonQuad.get("coordinates");
        if (coordString == null)
            coordString = "";

        final String[] coords = coordString.trim()
                .split("\\s");
        return coords.length == 4 && parseLatLonQuadCoord(coords[0], ll)
                && parseLatLonQuadCoord(coords[1], lr) &&
                parseLatLonQuadCoord(coords[2], ur) &&
                parseLatLonQuadCoord(coords[3], ul);

    }

    private static boolean parseLatLonQuadCoord(String str,
            GeoPoint coord) {
        String[] components = str.split(",");
        if (components.length < 2)
            return false;
        try {
            coord.set(Double.parseDouble(components[1]),
                    Double.parseDouble(components[0]));
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    private static Map<String, String> getTextElements(XmlPullParser parser,
            String tell,
            Set<String> tags, Map<String, String> results)
            throws XmlPullParserException,
            IOException {
        checkAtTag(parser, tell);

        if (results == null)
            results = new HashMap<>();

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
                    if (tags.contains(inTag) && !results.containsKey(inTag))
                        results.put(inTag, parser.getText());
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        return results;
    }

    /**
     * Determines if the parser name matches the tag name provided
     * @param parser the parser
     * @param tagName the tag name to match against
     * @throws XmlPullParserException occurs if there is an exception with the parsing
     * @throws IllegalStateException if the tag encountered is not the correct tag
     */
    public static void checkAtTag(XmlPullParser parser, String tagName)
            throws XmlPullParserException {
        if (parser == null || parser.getEventType() != XmlPullParser.START_TAG
                || !parser.getName().equals(tagName)) {
            String parserName = "null parser";
            if (parser != null)
                parserName = parser.getName();

            throw new IllegalStateException("Expected tag " + tagName
                    + ", encountered "
                    + parserName);

        }
    }

    private static void createMosaicDatabase(File dbname,
            Set<DatasetDescriptor> layers) {
        MosaicDatabaseBuilder2 database = null;
        try {
            database = ATAKMosaicDatabase3.create(dbname);
            database.beginTransaction();
            try {
                String type;
                ImageDatasetDescriptor image;
                for (DatasetDescriptor info : layers) {
                    image = (ImageDatasetDescriptor) info;
                    type = image.getImageryType();
                    database.insertRow(info.getUri(),
                            type,
                            image.isPrecisionImagery(),
                            image.getUpperLeft(),
                            image.getUpperRight(),
                            image.getLowerRight(),
                            image.getLowerLeft(),
                            info.getMinResolution(type),
                            info.getMaxResolution(type),
                            image.getWidth(),
                            image.getHeight(),
                            info.getSpatialReferenceID());
                }
                database.createIndices();
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            if (database != null)
                database.close();
        }
    }

    private static DatasetDescriptor createMosaic(File file,
            Set<DatasetDescriptor> layers, File workingDir) {
        if (layers.size() < 1)
            return null;

        MosaicDatabase2 database = null;
        try {
            File mosaicDatabaseFile = new File(workingDir, "mosaicdb.sqlite");
            Log.d(TAG,
                    "creating mosaic database file "
                            + mosaicDatabaseFile.getName()
                            + " for " + file.getName());
            long s = SystemClock.elapsedRealtime();
            createMosaicDatabase(mosaicDatabaseFile, layers);
            long e = SystemClock.elapsedRealtime();

            Log.d(TAG, "mosaic scan file: " + file);
            Log.d(TAG, "Generated Mosaic Database in " + (e - s) + " ms");

            database = new ATAKMosaicDatabase3();
            database.open(mosaicDatabaseFile);

            Map<String, String> extraData = new HashMap<>();
            extraData.put("relativePaths", "false");

            File tilecacheDir = new File(workingDir, "tilecache");
            FileSystemUtils.delete(tilecacheDir);
            if (IOProviderFactory.mkdirs(tilecacheDir))
                extraData.put("tilecacheDir", tilecacheDir.getAbsolutePath());

            Map<String, MosaicDatabase2.Coverage> dbCoverages = new HashMap<>();
            database.getCoverages(dbCoverages);
            Set<String> types = dbCoverages.keySet();
            Map<String, Pair<Double, Double>> resolutions = new HashMap<>();
            Map<String, Geometry> coverages = new HashMap<>();

            MosaicDatabase2.Coverage coverage;
            for (String type : types) {
                coverage = database.getCoverage(type);
                resolutions.put(
                        type,
                        Pair.create(coverage.minGSD,
                                coverage.maxGSD));
                coverages.put(type, coverage.geometry);
            }

            coverages.put(null, database.getCoverage().geometry);

            extraData.put("numFrames", String.valueOf(layers.size()));

            return new MosaicDatasetDescriptor(file.getName(),
                    GdalLayerInfo.getURI(file).toString(),
                    INSTANCE.getType(),
                    "native-mosaic",
                    mosaicDatabaseFile,
                    database.getType(),
                    dbCoverages.keySet(),
                    resolutions,
                    coverages,
                    EquirectangularMapProjection.INSTANCE
                            .getSpatialReferenceID(),
                    false,
                    workingDir,
                    extraData);
        } finally {
            if (database != null)
                database.close();
        }
    }

    /**
     * Given a KMZ file, find the main document KML (doc.kml)
     * @param kmzFile KMZ file
     * @return ZIP file pointer to the document KML or null if not found
     */
    public static ZipVirtualFile findDocumentKML(File kmzFile) {
        try {
            if (!(kmzFile instanceof ZipVirtualFile))
                kmzFile = new ZipVirtualFile(kmzFile);

            // Primary KML is usually named doc.kml
            ZipVirtualFile docFile = new ZipVirtualFile(kmzFile, "doc.kml");
            if (IOProviderFactory.exists(docFile))
                return docFile;

            // Look for other KML
            File[] files = IOProviderFactory.listFiles(kmzFile, KML_FILTER);
            if (files != null) {
                for (File f : files) {
                    if (f instanceof ZipVirtualFile)
                        return (ZipVirtualFile) f;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to find document KML file", e);
        }

        // Not found
        return null;
    }
}
