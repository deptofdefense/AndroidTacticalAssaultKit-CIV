
package com.atakmap.android.maps.tilesets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.osm.OSMDroidZipLayerInfoSpi;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.database.Databases;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.math.MathUtils;

public class TilesetInfo {

    static {
        MobileImageryRasterLayer2.registerDatasetType("tileset");
        MobileImageryRasterLayer2.registerDatasetType("osmdroid");
        MobileImageryRasterLayer2.registerDatasetType("momap");
        MobileImageryRasterLayer2.registerDatasetType("mbtiles");
    }
    static void staticInit() {}

    public final static String IMAGERY_TYPE = "Tileset";
    
    public static final String TAG = "TilesetInfo";

    private final static Set<String> NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES = new HashSet<String>();
    static {
        NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES.add("zoom_level");
        NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES.add("tile_column");
        NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES.add("tile_row");
        NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES.add("tile_data");
    }
    
    private final static Set<String> MBTILES_TILES_TABLE_COLUMN_NAMES = new HashSet<String>();
    static {
        MBTILES_TILES_TABLE_COLUMN_NAMES.addAll(NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES);
        MBTILES_TILES_TABLE_COLUMN_NAMES.add("tile_alpha");
    }

    private final ImageDatasetDescriptor layerInfo;

    public TilesetInfo(ImageDatasetDescriptor layerInfo) {
//        if (!layerInfo.getDatasetType().equals("tileset"))
//            throw new IllegalArgumentException();
        this.layerInfo = layerInfo;
        String _uri = this.layerInfo.getUri();
        _isArchiveUri = isZipArchive(_uri);
    }

    public ImageDatasetDescriptor getInfo() {
        return this.layerInfo;
    }

    public static DatasetDescriptor parse(File file) throws IOException {
        final String path = file.getAbsolutePath();
        if (isZipArchive(path)) {
            return parseZip(file);
        } else if (IOProviderFactory.isDatabase(new File(path))) {
            return parseSQLiteDb(file);
        } else {
            return null;
        }
    }

    private static DatasetDescriptor parseZip(File file) throws IOException {
        ZipFile zFile = new ZipFile(file);
        try { 
            ZipEntry zEntry = zFile.getEntry("tileset.xml");
            InputStream in = null;

            if (zEntry != null) {
                try {
                    DatasetDescriptor lInfo =
                         _parseXml(in = zFile.getInputStream(zEntry), 
                                     file.toString());
                    return lInfo;
                } catch (ParserConfigurationException e) {
                    throw new IOException(e);
                } catch (SAXException e) {
                    throw new IOException(e);
                } finally {
                    if (in != null)
                        in.close();
                }
            } else {
                long s = android.os.SystemClock.elapsedRealtime();
                ZipVirtualFile zf = new ZipVirtualFile(file);
                long e = android.os.SystemClock.elapsedRealtime();
    
                Log.d(TAG, "zipvirtualfile in " + (e - s));
                File[] children = IOProviderFactory.listFiles(zf);
    
                final Set<String> supportedFormats = new HashSet<String>();
                supportedFormats.add("MAPNIK");
                supportedFormats.add("USGS NATIONAL MAP TOPO");
                supportedFormats.add("4UMAPS");

                // XXX - will cache type always be base directory???
                File basedir = null;
                if (children != null) { 
                    for (int i = 0; i < children.length; i++) {
                        if (supportedFormats.contains(children[i].getName().toUpperCase(LocaleUtil.getCurrent()))) {
                            basedir = children[i];
                            break;
                        }
                    }
                }
                if (basedir != null)
                    return OSMDroidZipLayerInfoSpi.parseOSMDroidZipTilecache(file, basedir);

                return null;
            }
        } finally { 
            zFile.close();
        }
    }

    @Override
    public int hashCode() {
        int result = layerInfo != null ? layerInfo.hashCode() : 0;
        result = 31 * result + (_isArchiveUri ? 1 : 0);
        return result;
    }

    private static DatasetDescriptor parseSQLiteDb(File file) {
        if(file.getName().endsWith(".gpkg"))
            return null;

        DatabaseIface database = null;
        try {
            database = IOProviderFactory.createDatabase(file, DatabaseInformation.OPTION_READONLY);
            if (OSMUtils.isOSMDroidSQLite(database)) {
                // OSM Droid SQLite
                CursorIface result;

                String provider = null;
                int minLevel = -1;
                result = null;
                try {
                    result = database.query("SELECT key, provider FROM tiles ORDER BY key ASC LIMIT 1",
                            null);
                    if (result.moveToNext()) {
                        minLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(result.getLong(0));
                        provider = result.getString(1);
                    }
                } finally {
                    if (result != null)
                        result.close();
                }
                // no tiles present in database
                if (minLevel < 0)
                    return null;

                int srid = WebMercatorProjection.INSTANCE.getSpatialReferenceID();
                boolean hasAtakMetadata = false;
                if (Databases.getColumnNames(database, "ATAK_metadata") != null) {
                    hasAtakMetadata = true;

                    result = null;
                    try {
                        result = database.query(
                                "SELECT value FROM ATAK_metadata WHERE key = \'srid\'", null);
                        if (result.moveToNext())
                            srid = Integer.parseInt(result.getString(0));
                    } catch (Exception ignored) {
                        // quietly ignore
                    } finally {
                        if (result != null)
                            result.close();
                    }
                }

                // bounds discovery
                int gridMinX;
                int gridMinY;
                int gridMaxX;
                int gridMaxY;

                // XXX - brute force bounds -- reimplement to do 4 queries
                // for MBB discovery (min x,y / max x,y)
                result = null;
                long index;
                int tileX;
                int tileY;
                try {
                    result = database.query(
                            "SELECT key FROM tiles WHERE key <= "
                                    + OSMUtils.getOSMDroidSQLiteMaxIndex(minLevel), null);
                    result.moveToNext();
                    index = result.getLong(0);
                    gridMinX = OSMUtils.getOSMDroidSQLiteTileX(index);
                    gridMaxX = gridMinX;
                    gridMinY = OSMUtils.getOSMDroidSQLiteTileY(index);
                    gridMaxY = gridMinY;
                    while (result.moveToNext()) {
                        index = result.getLong(0);
                        tileX = OSMUtils.getOSMDroidSQLiteTileX(index);
                        if (tileX < gridMinX)
                            gridMinX = tileX;
                        else if (tileX > gridMaxX)
                            gridMaxX = tileX;
                        tileY = OSMUtils.getOSMDroidSQLiteTileY(index);
                        if (tileY < gridMinY)
                            gridMinY = tileY;
                        else if (tileY > gridMaxY)
                            gridMaxY = tileY;
                    }
                } finally {
                    if (result != null)
                        result.close();
                }

                int maxLevel = -1;
                result = null;
                try {
                    result = database.query("SELECT key FROM tiles ORDER BY key DESC LIMIT 1",
                            null);
                    if (result.moveToNext())
                        maxLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(result.getLong(0));
                    else
                        throw new IllegalStateException();
                } finally {
                    if (result != null)
                        result.close();
                }

                Projection projection = MobileImageryRasterLayer2.getProjection(srid);
                if(projection == null) {
                    Log.w(TAG, "SRID " + srid + " is not supported");
                    return null;
                }
                
                final double minLat = projection.getMinLatitude();
                final double minLng = projection.getMinLongitude();
                final double maxLat = projection.getMaxLatitude();
                final double maxLng = projection.getMaxLongitude();

                int zeroGridWidth = 1;
                int zeroGridHeight = 1;
                if(srid == 4326)
                    zeroGridWidth = 2;

                // NOTE: nominal tile size, in degrees
                final double tileWidth = (maxLng-minLng) / (zeroGridWidth << minLevel);
                final double tileHeight = (maxLat-minLat) / (zeroGridHeight << minLevel);

                final double south;
                final double west;
                final double north;
                final double east;
                if (srid == WebMercatorProjection.INSTANCE.getSpatialReferenceID()) {
                    south = OSMUtils.mapnikTileLat(minLevel, gridMaxY + 1);
                    west = OSMUtils.mapnikTileLng(minLevel, gridMinX);
                    north = OSMUtils.mapnikTileLat(minLevel, gridMinY);
                    east = OSMUtils.mapnikTileLng(minLevel, gridMaxX + 1);
                } else {
                    // XXX - should be done in projected coordinate space, then
                    //       can be used correctly for all projections
                    south = maxLat - (tileHeight * (gridMaxY + 1));
                    west = minLng + (tileWidth * gridMinX);
                    north = maxLat - (tileHeight * gridMinY);
                    east = minLng + (tileWidth * (gridMaxX + 1));
                }

                TilesetInfo.Builder builder = new TilesetInfo.Builder("tileset", "osmdroid");
                builder.setSpatialReferenceID(srid);
                builder.setUri(file.getAbsolutePath());
                builder.setName(file.getName());
                builder.setImageryType(provider);
                builder.setTilePixelWidth(256);
                builder.setTilePixelHeight(256);
                builder.setLevelCount((maxLevel - minLevel) + 1);
                builder.setZeroWidth(tileWidth);
                builder.setZeroHeight(tileHeight);
                builder.setOverview(false);
                builder.setImageExt("");

                builder.setFourCorners(new GeoPoint(south, west),
                        new GeoPoint(north, west),
                        new GeoPoint(north, east),
                        new GeoPoint(south, east));

                builder.setPathStructure("OSM_DROID_SQLITE");
                builder.setLevelOffset(minLevel);
                builder.setGridOriginLat(projection.getMinLatitude());

                builder.setGridOffsetX(gridMinX);
                builder.setGridOffsetY((1 << minLevel) - gridMaxY - 1);
                builder.setGridWidth(gridMaxX - gridMinX + 1);
                builder.setGridHeight(gridMaxY - gridMinY + 1);
                
                // XXX - think all projections can be supported now...verify
                
                // check to see if the projection is supported for aggregation
                switch(srid) {
                    case 3857 :
                    case 900913 :
                    case 4326 :
                    case 90094326 :
                        builder.setExtra("mobileimagery.aggregate", String.valueOf(1));
                        break;
                    default :
                        // XXX - until the consolidated tile renderer code has
                        //       been introduced, offer the legacy
                        //       "adjacent tileset" capability for pyramids from
                        //       the same provider.  The main drawback for the
                        //       legacy will be a lack of tile interleaving from
                        //       different datasets; specifically, tiles from
                        //       the dataset with the highest resolution tile(s)
                        //       will be rendered on top of the other datasets
                        break;
                }

                if(hasAtakMetadata)
                    builder.setExtra("mobileimagery.type", "osmdroid.atak");
                else
                    builder.setExtra("mobileimagery.type", "osmdroid");

                builder.setComputeDimensionFromCoverage(false);
                
                //System.out.println("CREATING OSMDROID DATASET");
                //System.out.println("name=" + file.getName());
                //System.out.println("type=" + provider + "(" + builder.imageryType + ")");
                //System.out.println("minLevel=" + minLevel);
                //System.out.println("maxLevel=" + maxLevel);
                //System.out.println("gridMinX=" + gridMinX);
                //System.out.println("gridMaxX=" + gridMaxX);
                //System.out.println("gridMinY=" + gridMinY);
                //System.out.println("gridMaxY=" + gridMaxY);

                return builder.build();
            } else {
                Map<String, Set<String>> databaseStructure;
                try { 
                   databaseStructure = Databases.getColumnNames(database);
                } catch (Exception e) { 
                    return null;
                }

                if (databaseStructure.get("tiles") != null) {
                    Set<String> tilesTable = databaseStructure.get("tiles");
                    if (tilesTable.equals(NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES)) {
                        return createMOMAPDataset(file, database);
                    } else if(tilesTable.equals(MBTILES_TILES_TABLE_COLUMN_NAMES)) {
                        return createMBTilesDataset(file, database);
                            }
                        }
                            }
                            return null;
                        } finally {
            if (database != null)
                database.close();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TilesetInfo)) {
            return false;
        }

        return this.layerInfo.equals(((TilesetInfo) other).layerInfo);
    }

    private static DatasetDescriptor _parseXml(InputStream in, String uri)
            throws ParserConfigurationException, SAXException, IOException {
        try {
            DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

 
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(in);

            Builder builder = new Builder("tileset", "tileset");
            builder.setUri(uri);
            builder.setName(_fetchStringNode(doc, "name", ""));
            builder.setImageryType(_fetchStringNode(doc, "name", ""));
            builder.setTilePixelWidth(_fetchIntNode(doc, "imagePixelWidth", 512));
            builder.setTilePixelHeight(_fetchIntNode(doc, "imagePixelHeight", 512));
            builder.setLevelCount(_fetchIntNode(doc, "tileLevels", 1));
            builder.setZeroWidth(_fetchDoubleNode(doc, "tileZeroWidth", 2.25));
            builder.setZeroHeight(_fetchDoubleNode(doc, "tileZeroHeight", 2.25));
            builder.setOverview(_fetchBooleanNode(doc, "isOverview", false));
            builder.setImageExt(_fetchStringNode(doc, "imageExtension", ".jpg"));
            final double[] swne = _fetchBounds(doc, "tileBounds", -90d, -180d, 90d, 180d);

            builder.setFourCorners(new GeoPoint(swne[0], swne[1]),
                    new GeoPoint(swne[2], swne[1]),
                    new GeoPoint(swne[2], swne[3]),
                    new GeoPoint(swne[0], swne[3]));

            builder.setPathStructure(_fetchStringNode(doc, "pathStructure", "NASAWW").toUpperCase(
                    LocaleUtil.getCurrent()));

            final GeoPoint[] _fourCorners = new GeoPoint[4];
            try {
                NodeList coverage = doc.getElementsByTagName("coverage");
                _parseCoverage(coverage.item(0), _fourCorners);

                if (_sanityCheckCoverage(_fourCorners, swne[2], swne[3], swne[0], swne[1]))
                    builder.setFourCorners(_fourCorners[0], _fourCorners[1], _fourCorners[2],
                            _fourCorners[3]);
            } catch (Exception ignored) {
            }

            return builder.build();
        } finally {
            in.close();
        }
    }

    private static void _parseCoverage(Node node, GeoPoint[] _fourCorners) {
        Node c = node.getFirstChild();
        while (c != null) {
            String name = c.getNodeName();
            if (name.equals("southWest")) {
                _fourCorners[0] = GeoPoint.parseGeoPoint(c.getFirstChild().getNodeValue());
            }
            else if (name.equals("northWest")) {
                _fourCorners[1] = GeoPoint.parseGeoPoint(c.getFirstChild().getNodeValue());
            }
            else if (name.equals("northEast")) {
                _fourCorners[2] = GeoPoint.parseGeoPoint(c.getFirstChild().getNodeValue());
            }
            else if (name.equals("southEast")) {
                _fourCorners[3] = GeoPoint.parseGeoPoint(c.getFirstChild().getNodeValue());
            }
            c = c.getNextSibling();
        }
    }

    private static boolean _sanityCheckCoverage(GeoPoint[] _fourCorners, double _north,
            double _east, double _south, double _west) {

        if (_fourCorners[0] != null &&
                _fourCorners[1] != null &&
                _fourCorners[2] != null &&
                _fourCorners[3] != null) {

            if (_fourCorners[0].getLatitude() > _north ||
                    _fourCorners[0].getLongitude() > _east ||
                    _fourCorners[1].getLatitude() < _south ||
                    _fourCorners[1].getLongitude() > _east ||
                    _fourCorners[2].getLatitude() < _south ||
                    _fourCorners[2].getLongitude() < _west ||
                    _fourCorners[3].getLatitude() > _north ||
                    _fourCorners[3].getLongitude() < _west) {
                // insane
                return false;
            }
        }
        else {
            // insane
            return false;
        }

        return true;
    }

    private static Node _fetchNode(Document doc, String name) {
        NodeList nl = doc.getElementsByTagName(name);
        Node n = null;
        if (nl.getLength() > 0) {
            n = nl.item(0);
        }
        return n;
    }

    private static double _fetchDoubleNode(Document doc, String name, double fallback) {
        double v = fallback;
        try {
            Node n = _fetchNode(doc, name);
            v = Double.parseDouble(n.getFirstChild().getNodeValue());
        } catch (Exception ex) {
            // ignore
        }
        return v;
    }

    private static int _fetchIntNode(Document doc, String name, int fallback) {
        int v = fallback;
        try {
            Node n = _fetchNode(doc, name);
            v = Integer.parseInt(n.getFirstChild().getNodeValue());
        } catch (Exception ex) {
            // ignore
        }
        return v;
    }

    private static String _fetchStringNode(Document doc, String name, String fallback) {
        String v = fallback;
        try {
            Node n = _fetchNode(doc, name);
            v = n.getFirstChild().getNodeValue();
        } catch (Exception ex) {
            // ignore
        }
        return v;
    }

    private static boolean _fetchBooleanNode(Document doc, String name, boolean fallback) {
        boolean v = fallback;
        try {
            Node n = _fetchNode(doc, name);
            v = Boolean.parseBoolean(n.getFirstChild().getNodeValue());
        } catch (Exception ex) {
            // ignore
        }
        return v;
    }

    private static double[] _fetchBounds(Document doc, String name, double s, double w, double n,
            double e) {
        double[] swne = new double[4];
        String bounds = _fetchStringNode(doc, name, "");
        try {
            String[] parts = bounds.split(";");
            String[] ll = parts[0].split(",");
            swne[0] = Double.parseDouble(ll[0]);
            swne[1] = Double.parseDouble(ll[1]);
            ll = parts[1].split(",");
            swne[2] = Double.parseDouble(ll[0]);
            swne[3] = Double.parseDouble(ll[1]);
        } catch (Exception ex) {
            swne[2] = n;
            swne[0] = s;
            swne[3] = e;
            swne[1] = w;
        }

        return swne;
    }

    public int getTilePixelWidth() {
        return Integer.parseInt(this.layerInfo.getExtraData("_tilePixelWidth"));
    }

    public int getTilePixelHeight() {
        return Integer.parseInt(this.layerInfo.getExtraData("_tilePixelHeight"));
    }

    /**
     * Returns the longitudinal extent of a tile, at native resolution, in degrees.
     * 
     * @return The longitudinal extent of a tile, at native resolution, in degrees.
     */
    public double getZeroWidth() {
        return Double.parseDouble(this.layerInfo.getExtraData("_zeroWidth"));
    }

    /**
     * Returns the latitudinal extent of a tile, at native resolution, in degrees.
     * 
     * @return The latitudinal extent of a tile, at native resolution, in degrees.
     */
    public double getZeroHeight() {
        return Double.parseDouble(this.layerInfo.getExtraData("_zeroHeight"));
    }

    public double getGridOriginLat() {
        return Double.parseDouble(DatasetDescriptor.getExtraData(this.layerInfo, "gridOriginLat", "-90"));
    }

    public double getGridOriginLng() {
        return Double.parseDouble(DatasetDescriptor.getExtraData(this.layerInfo, "gridOriginLng", "-180"));
    }

    public int getGridOffsetX() {
        return Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo, "_gridX", "-1"));
    }

    public int getGridOffsetY() {
        return Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo, "_gridY", "-1"));
    }

    public int getGridWidth() {
        return Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo, "_gridWidth", "-1"));
    }

    public int getGridHeight() {
        return Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo, "_gridHeight", "-1"));
    }

    public String getImageExt() {
        return this.layerInfo.getExtraData("_imageExt");
    }

    public boolean isOverview() {
        return DatasetDescriptor.getExtraData(this.layerInfo, "_isOverview", "false").equals("true");
    }
    
    public int getLevelCount() {
        return Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo, "_levelCount", "1"));
    }

    public static class Builder {
        private final String provider;
        private final String datasetType;
        private String name;
        private GeoPoint sw = GeoPoint.ZERO_POINT;
        private GeoPoint nw = GeoPoint.ZERO_POINT;
        private GeoPoint ne = GeoPoint.ZERO_POINT;
        private GeoPoint se = GeoPoint.ZERO_POINT;
        private int _levelCount = 1;
        private double _zeroWidth = 2.25d;
        private double _zeroHeight = 2.25d;
        private int _tilePixelWidth = 512;
        private int _tilePixelHeight = 512;
        private boolean _isOverview = false;
        private String _uri;
        private String _imageExt = ".jpg";
        private String _pathStructure = "NASAWW";
        private int srid = EquirectangularMapProjection.INSTANCE.getSpatialReferenceID();
        private int levelOffset = 0;
        private double gridOriginLat = -90;
        private double gridOriginLng = -180;
        private int gridOffsetX = -1;
        private int gridOffsetY = -1;
        private int gridWidth = -1;
        private int gridHeight = -1;
        private String subpath = "";
        private String supportSpi = SimpleUriTilesetSupport.Spi.INSTANCE.getName();
        private boolean isOnline = false;
        private File workingDir;
        private String imageryType = TilesetInfo.IMAGERY_TYPE;
        private boolean dimsFromCoverage = true;

        private Map<String, String> extra = new HashMap<String, String>();

        public Builder(String provider, String datasetType) {
            this.provider = provider;
            this.datasetType = datasetType;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setLevelCount(int levelCount) {
            this._levelCount = levelCount;
            return this;
        }

        public Builder setZeroWidth(double zeroWidth) {
            this._zeroWidth = zeroWidth;
            return this;
        }

        public Builder setZeroHeight(double zeroHeight) {
            this._zeroHeight = zeroHeight;
            return this;
        }

        public Builder setOverview(boolean isOverview) {
            this._isOverview = isOverview;
            return this;
        }

        public Builder setUri(String uri) {
            this._uri = uri;
            return this;
        }

        public Builder setTilePixelWidth(int tilePixelWidth) {
            this._tilePixelWidth = tilePixelWidth;
            return this;
        }

        public Builder setTilePixelHeight(int tilePixelHeight) {
            this._tilePixelHeight = tilePixelHeight;
            return this;
        }

        public Builder setImageExt(String imageExt) {
            this._imageExt = imageExt;
            return this;
        }

        public Builder setPathStructure(String pathStructure) {
            this._pathStructure = pathStructure;
            return this;
        }

        public Builder setFourCorners(GeoPoint sw, GeoPoint nw, GeoPoint ne, GeoPoint se) {
            this.sw = sw;
            this.nw = nw;
            this.ne = ne;
            this.se = se;
            return this;
        }

        public Builder setSpatialReferenceID(int srid) {
            this.srid = srid;
            return this;
        }

        public Builder setLevelOffset(int l) {
            this.levelOffset = l;
            return this;
        }

        public Builder setGridOriginLat(double off) {
            this.gridOriginLat = off;
            return this;
        }

        public Builder setGridOriginLng(double off) {
            this.gridOriginLng = off;
            return this;
        }

        public Builder setGridOffsetX(int off) {
            this.gridOffsetX = off;
            return this;
        }

        public Builder setGridOffsetY(int off) {
            this.gridOffsetY = off;
            return this;
        }

        public Builder setGridWidth(int w) {
            this.gridWidth = w;
            return this;
        }

        public Builder setGridHeight(int h) {
            this.gridHeight = h;
            return this;
        }

        public Builder setSubpath(String p) {
            this.subpath = p;
            return this;
        }

        public Builder setExtra(String key, String value) {
            this.extra.put(key, value);
            return this;
        }

        public Builder setSupportSpi(String spiClass) {
            this.supportSpi = spiClass;
            return this;
        }

        public Builder setIsOnline(boolean isOnline) {
            this.isOnline = isOnline;
            return this;
        }
        
        public Builder setWorkingDir(File workingDir) {
            this.workingDir = workingDir;
            return this;
        }
        
        public Builder setImageryType(String imageryType) {
            this.imageryType = imageryType;
            return this;
        }

        public Builder setComputeDimensionFromCoverage(boolean dimsFromCoverage) {
            this.dimsFromCoverage = dimsFromCoverage;
            return this;
        }

        public DatasetDescriptor build() {
            final int stx;
            final int ftx;
            final int sty;
            final int fty;

            final double west = MathUtils.min(sw.getLongitude(), nw.getLongitude(),
                    ne.getLongitude(), se.getLongitude());
            final double east = MathUtils.max(sw.getLongitude(), nw.getLongitude(),
                    ne.getLongitude(), se.getLongitude());
            final double south = MathUtils.min(sw.getLatitude(), nw.getLatitude(),
                    ne.getLatitude(), se.getLatitude());
            final double north = MathUtils.max(sw.getLatitude(), nw.getLatitude(),
                    ne.getLatitude(), se.getLatitude());

            
            if(this.dimsFromCoverage) {
                final double r0Width = _zeroWidth / (double) (1 << (_levelCount - 1));
                final double r0Height = _zeroHeight / (double) (1 << (_levelCount - 1));
    
                stx = (int) (west / r0Width);
                ftx = (int) Math.ceil(east / r0Width);
                sty = (int) (south / r0Height);
                fty = (int) Math.ceil(north / r0Height);
            } else {
                stx = this.gridOffsetX << (_levelCount - 1);
                ftx = (this.gridOffsetX + this.gridWidth) << (_levelCount - 1);
                sty = this.gridOffsetY << (_levelCount - 1);
                fty = (this.gridOffsetY + this.gridHeight) << (_levelCount - 1);
            }
            Map<String, String> extraData = new HashMap<String, String>(this.extra);
            extraData.put("_zeroHeight", String.valueOf(_zeroHeight));
            extraData.put("_zeroWidth", String.valueOf(_zeroWidth));
            extraData.put("_tilePixelHeight", String.valueOf(_tilePixelHeight));
            extraData.put("_tilePixelWidth", String.valueOf(_tilePixelWidth));
            if (_imageExt != null)
                extraData.put("_imageExt", _imageExt);
            if (_pathStructure != null)
                extraData.put("_pathStructure", _pathStructure);
            extraData.put("levelOffset", String.valueOf(levelOffset));
            extraData.put("gridOriginLat", String.valueOf(gridOriginLat));
            extraData.put("gridOriginLng", String.valueOf(gridOriginLng));
            extraData.put("_gridX", String.valueOf(gridOffsetX));
            extraData.put("_gridY", String.valueOf(gridOffsetY));
            extraData.put("_gridWidth", String.valueOf(gridWidth));
            extraData.put("_gridHeight", String.valueOf(gridHeight));
            if (subpath != null)
                extraData.put("subpath", subpath);
            if (this.supportSpi != null)
                extraData.put("supportSpi", this.supportSpi);
            if (_isOverview)
                extraData.put("_isOverview", String.valueOf(_isOverview));
            
            final long width = (long)(ftx - stx) * (long)_tilePixelWidth;
            final long height = (long)(fty - sty) * (long)_tilePixelHeight;

            extraData.put("width", String.valueOf(width));
            extraData.put("height", String.valueOf(height));
            extraData.put("_levelCount", String.valueOf(_levelCount));
            
            // XXX - 
            final double gsd = ((east-west)*111319.458) / (double)width;

            return new ImageDatasetDescriptor(name,
                                              _uri,
                                              provider,
                                              this.datasetType,
                                              this.imageryType,
                                              (ftx - stx) * _tilePixelWidth,
                                              (fty - sty) * _tilePixelHeight,
                                              gsd,
                                              _levelCount,
                                              nw, ne, se, sw,
                                              srid,
                                              this.isOnline,
                                              this.workingDir, // workingDir
                                              extraData);
        }
    }

    public Builder buildUpon() {
        Builder builder = new Builder(this.layerInfo.getProvider(), this.layerInfo.getDatasetType());
        builder.setName(this.layerInfo.getName());
        Envelope mbb = this.layerInfo.getCoverage(null).getEnvelope();
        builder.setFourCorners(new GeoPoint(mbb.minY, mbb.minX),
                               new GeoPoint(mbb.maxY, mbb.minX),
                               new GeoPoint(mbb.maxY, mbb.maxX),
                               new GeoPoint(mbb.minY, mbb.maxX));
        builder.setLevelCount(Integer.parseInt(this.layerInfo.getExtraData("levelCount")));
        builder.setZeroWidth(this.getZeroWidth());
        builder.setZeroHeight(this.getZeroHeight());
        builder.setTilePixelWidth(this.getTilePixelWidth());
        builder.setTilePixelHeight(this.getTilePixelHeight());
        builder.setOverview(DatasetDescriptor.getExtraData(this.layerInfo, "_isOverview", "false").equals(
                "true"));
        builder.setUri(this.layerInfo.getUri());
        builder.setImageExt(this.getImageExt());
        builder.setPathStructure(this.layerInfo.getExtraData("_pathStructure"));
        builder.setSpatialReferenceID(this.layerInfo.getSpatialReferenceID());
        builder.setLevelOffset(Integer.parseInt(DatasetDescriptor.getExtraData(this.layerInfo,
                "levelOffset", "0")));
        builder.setGridOriginLat(Double.parseDouble(DatasetDescriptor.getExtraData(this.layerInfo,
                "gridOriginLat", "-90")));
        builder.setGridOriginLng(Double.parseDouble(DatasetDescriptor.getExtraData(this.layerInfo,
                "gridOriginLng", "-180")));
        builder.setSubpath(DatasetDescriptor.getExtraData(this.layerInfo, "subpath", ""));
        builder.setSupportSpi(DatasetDescriptor.getExtraData(this.layerInfo, "supportSpi", null));
        builder.setIsOnline(this.layerInfo.isRemote());

        return builder;
    }

    public boolean isArchive() {
        return _isArchiveUri;
    }

    private boolean _isArchiveUri = true;

    public static boolean isZipArchive(String path) {
        if (path == null)
            return false;
        else if (path.length() < 4)
            return false;
        else if (path.endsWith(".zip"))
            return true;
        else
            return path.toLowerCase(LocaleUtil.getCurrent()).endsWith(".zip");

    }
    
    private static DatasetDescriptor createMOMAPDataset(File file, DatabaseIface database) {
        // Nett Warrior

        CursorIface result;

        int minLevel = -1;
        int maxLevel = -1;
        String format = "";
        result = null;
        try {
            result = database.query("SELECT name, value FROM metadata", null);
            String name;
            while (result.moveToNext()) {
                name = result.getString(0);
                if (name.equals("maxzoom"))
                    maxLevel = Integer.parseInt(result.getString(1));
                else if (name.equals("minzoom"))
                    minLevel = Integer.parseInt(result.getString(1));
                else if (name.equals("format"))
                    format = result.getString(1);
            }
        } finally {
            if (result != null)
                result.close();
        }

        if (minLevel < 0) {
            result = null;
            try {
                result = database
                        .query(
                                "SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1",
                                null);
                if (result.moveToNext())
                    minLevel = result.getInt(0);
            } finally {
                if (result != null)
                    result.close();
            }
        }
        if (maxLevel < 0) {
            result = null;
            try {
                result = database
                        .query(
                                "SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1",
                                null);
                if (result.moveToNext())
                    maxLevel = result.getInt(0);
            } finally {
                if (result != null)
                    result.close();
            }
        }
        // no tiles present in database
        if (minLevel < 0 || maxLevel < 0) {
            return null;
        }

        // bounds discovery
        int gridMinX;
        int gridMinY;
        int gridMaxX;
        int gridMaxY;

        // do 4 queries for MBB discovery (min x,y / max x,y)
        result = null;
        try {
            result = database.query(
                    "SELECT tile_column FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_column ASC LIMIT 1", null);
            if (result.moveToNext())
                gridMinX = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_column FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_column DESC LIMIT 1", null);
            if (result.moveToNext())
                gridMaxX = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_row FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_row ASC LIMIT 1", null);
            if (result.moveToNext())
                gridMinY = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_row FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_row DESC LIMIT 1", null);
            if (result.moveToNext())
                gridMaxY = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        final double tileWidth = (360.0d / (double) (1 << minLevel));
        final double tileHeight = 170.1022d / (double) (1 << minLevel);

        TilesetInfo.Builder builder = new TilesetInfo.Builder("tileset", "momap");
        builder.setSpatialReferenceID(WebMercatorProjection.INSTANCE
                .getSpatialReferenceID());
        builder.setUri(file.getAbsolutePath());
        builder.setName(file.getName());
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);
        builder.setLevelCount((maxLevel - minLevel) + 1);
        builder.setZeroWidth(tileWidth);
        builder.setZeroHeight(tileHeight);
        builder.setOverview(false);
        builder.setImageExt(format);
        final double south = OSMUtils.mapnikTileLat(minLevel, (1 << minLevel)
                - gridMinY);
        final double west = OSMUtils.mapnikTileLng(minLevel, gridMinX);
        final double north = OSMUtils.mapnikTileLat(minLevel, (1 << minLevel)
                - gridMaxY - 1);
        final double east = OSMUtils.mapnikTileLng(minLevel, gridMaxX + 1);
        builder.setFourCorners(new GeoPoint(south, west),
                new GeoPoint(north, west),
                new GeoPoint(north, east),
                new GeoPoint(south, east));

        builder.setPathStructure("NETT_WARRIOR_SQLITE");
        builder.setLevelOffset(minLevel);
        builder.setGridOriginLat(OSMUtils.mapnikTileLat(0, 1));

        builder.setGridOffsetX(gridMinX);
        builder.setGridOffsetY(gridMinY);
        builder.setGridWidth(gridMaxX - gridMinX + 1);
        builder.setGridHeight(gridMaxY - gridMinY + 1);
        
        builder.setImageryType(file.getName());

        builder.setComputeDimensionFromCoverage(false);
        
        return builder.build();
    }
    
    private static DatasetDescriptor createMBTilesDataset(File file, DatabaseIface database) {
        // MBTiles

        CursorIface result;

        int minLevel = -1;
        int maxLevel = -1;
        String format = "";
        result = null;
        try {
                result = database.query("SELECT name, value FROM metadata", null);
            String name;
            while (result.moveToNext()) {
                name = result.getString(0);
                if (name.equals("maxZoomLevel"))
                    maxLevel = Integer.parseInt(result.getString(1));
                else if (name.equals("minZoomLevel"))
                    minLevel = Integer.parseInt(result.getString(1));
                else if (name.equals("format"))
                    format = result.getString(1);
            }
        } finally {
            if (result != null)
                result.close();
        }

        if (minLevel < 0) {
            result = null;
            try {
                result = database
                        .query(
                                "SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1",
                                null);
                if (result.moveToNext())
                    minLevel = result.getInt(0);
            } finally {
                if (result != null)
                    result.close();
            }
        }
        if (maxLevel < 0) {
            result = null;
            try {
                result = database
                        .query(
                                "SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1",
                                null);
                if (result.moveToNext())
                    maxLevel = result.getInt(0);
            } finally {
                if (result != null)
                    result.close();
            }
        }
        // no tiles present in database
        if (minLevel < 0 || maxLevel < 0) {
            return null;
        }

        // bounds discovery
        int gridMinX;
        int gridMinY;
        int gridMaxX;
        int gridMaxY;

        // do 4 queries for MBB discovery (min x,y / max x,y)
        result = null;
        try {
            result = database.query(
                    "SELECT tile_column FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_column ASC LIMIT 1", null);
            if (result.moveToNext())
                gridMinX = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_column FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_column DESC LIMIT 1", null);
            if (result.moveToNext())
                gridMaxX = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_row FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_row ASC LIMIT 1", null);
            if (result.moveToNext())
                gridMinY = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        result = null;
        try {
            result = database.query(
                    "SELECT tile_row FROM tiles WHERE zoom_level = "
                            + String.valueOf(minLevel)
                            + " ORDER BY tile_row DESC LIMIT 1", null);
            if (result.moveToNext())
                gridMaxY = result.getInt(0);
            else
                throw new RuntimeException();
        } finally {
            if (result != null)
                result.close();
        }

        final double tileWidth = (360.0d / (double) (1 << minLevel));
        final double tileHeight = 170.1022d / (double) (1 << minLevel);

        TilesetInfo.Builder builder = new TilesetInfo.Builder("tileset", "mbtiles");
        builder.setSpatialReferenceID(WebMercatorProjection.INSTANCE
                .getSpatialReferenceID());
        builder.setUri(file.getAbsolutePath());
        builder.setName(file.getName());
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);
        builder.setLevelCount((maxLevel - minLevel) + 1);
        builder.setZeroWidth(tileWidth);
        builder.setZeroHeight(tileHeight);
        builder.setOverview(false);
        builder.setImageExt(format);
        final double south = OSMUtils.mapnikTileLat(minLevel, (1 << minLevel)
                - gridMinY);
        final double west = OSMUtils.mapnikTileLng(minLevel, gridMinX);
        final double north = OSMUtils.mapnikTileLat(minLevel, (1 << minLevel)
                - gridMaxY - 1);
        final double east = OSMUtils.mapnikTileLng(minLevel, gridMaxX + 1);
        builder.setFourCorners(new GeoPoint(south, west),
                new GeoPoint(north, west),
                new GeoPoint(north, east),
                new GeoPoint(south, east));

        builder.setSupportSpi("MB_TILES");
        builder.setLevelOffset(minLevel);
        builder.setGridOriginLat(OSMUtils.mapnikTileLat(0, 1));

        builder.setGridOffsetX(gridMinX);
        builder.setGridOffsetY(gridMinY);
        builder.setGridWidth(gridMaxX - gridMinX + 1);
        builder.setGridHeight(gridMaxY - gridMinY + 1);
        
        builder.setImageryType(file.getName());
        
        builder.setComputeDimensionFromCoverage(false);

        return builder.build();
    }
}
