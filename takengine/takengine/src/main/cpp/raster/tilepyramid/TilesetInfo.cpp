


#include <cstring>
#include <unordered_map>
#include <unordered_set>
#include <sqlite3.h>

#include "raster/tilepyramid/TilesetInfo.h"

#include "core/GeoPoint.h"
#include "feature/Geometry.h"
#include "feature/Envelope.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/osm/OSMUtils.h"
//#include "raster/tilepyramid/SimpleUriTilesetSupport.h"
#include "db/Database.h"
#include "db/SpatialiteDb.h"
#include "db/Cursor.h"
#include "util/IO.h"
#include "util/logging.h"
//#include "private/Util.h"

using namespace atakmap::raster::tilepyramid;

using namespace atakmap::core;
using namespace atakmap::db;
using namespace atakmap::feature;
using namespace atakmap::raster;
using namespace atakmap::raster::osm;
using namespace atakmap::util;

namespace {
    GeoPoint parseGeoPoint(const char *str);
    bool equalColumnNames(const std::vector<PGSC::String> &columns, const char * const * columnNames);
}

const char * const TilesetInfo::NETT_WARRIOR_TILES_COLUMN_NAMES[] = {
    "zoom_tile",
    "tile_column",
    "tile_row",
    "tile_data",
    NULL
};

TilesetInfo::TilesetInfo(const atakmap::raster::ImageDatasetDescriptor *info) :
TilesetInfo(info, nullptr) { }

TilesetInfo::TilesetInfo(const atakmap::raster::ImageDatasetDescriptor *info, void (*layerInfoDeleter)(const atakmap::raster::DatasetDescriptor *)) :
layerInfo(info),
layerInfoDeleter(layerInfoDeleter),
_isArchiveUri(isZipArchive(info->getURI())) { }

TilesetInfo::~TilesetInfo() {
    if (layerInfoDeleter) {
        layerInfoDeleter(this->layerInfo);
    }
}

const ImageDatasetDescriptor *TilesetInfo::getInfo() const {
    return this->layerInfo;
}

atakmap::raster::DatasetDescriptor *TilesetInfo::parse(const char *file) {
    std::string absFile = atakmap::util::getFileAsAbsolute(file);
    if (isZipArchive(absFile.c_str())) {
        return parseZip(absFile.c_str());
    } else if (isSQLiteDb(absFile.c_str())) {
        return parseSQLiteDb(absFile.c_str());
    }
    return nullptr;
}

/*TODO(bergeronj)--DatasetDescriptor *TilesetInfo::parseZip(System::String *file)
{
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
 long s = System.currentTimeMillis();
 ZipVirtualFile zf = new ZipVirtualFile(file);
 long e = System.currentTimeMillis();
 
 Log.d(TAG, "zipvirtualfile in " + (e - s));
 File[] children = zf.listFiles();
 
 final Set<String> supportedFormats = new HashSet<String>();
 supportedFormats.add("MAPNIK");
 supportedFormats.add("USGS NATIONAL MAP TOPO");
 supportedFormats.add("4UMAPS");
 
 // XXX - will cache type always be base directory???
 File basedir = null;
 for (int i = 0; i < children.length; i++) {
 if (supportedFormats.contains(children[i].getName().toUpperCase(Locale.US))) {
 basedir = children[i];
 break;
 }
 }
 if (basedir != null)
 return OSMDroidZipLayerInfoSpi.parseOSMDroidZipTilecache(file, basedir);
 
 return null;
 }
 } finally {
 zFile.close();
 }
}*/

DatasetDescriptor *TilesetInfo::parseZip(const char *file) {
    return nullptr;
}

DatasetDescriptor *TilesetInfo::parseSQLiteDb(const char *file) {
    
    //XXX-- web mercator. way to get this globally?
    const int webMercatorProjSRID = 3857;
    
    atakmap::db::SpatiaLiteDB database(file, true);
    bool isSQLite = false;
    try {
        isSQLite = OSMUtils::getInstance().isOSMDroidSQLite(database);
    } catch (db::Cursor::CursorError &) { }
    
    if (isSQLite) {
        int minLevel = -1;
        std::string provider;
        std::unique_ptr<Cursor> result(database.query("SELECT key, provider FROM tiles ORDER BY key ASC LIMIT 1"));
        if (result && result->moveToNext()) {
            minLevel = OSMUtils::getOSMDroidSQLiteZoomLevel(result->getLong(0));
            provider = result->getString(1);
        }
        
        if (minLevel < 0) {
            return nullptr;
        }
        
        int srid = webMercatorProjSRID;
        std::vector<PGSC::String> columns;
        try {
            columns = db::getColumnNames(database, "ATAK_metadata");
        } catch (Cursor::CursorError &) { }
        if (columns.size() > 0) {
            result = std::unique_ptr<Cursor>(database.query("SELECT value FROM ATAK_metadata WHERE key = 'srid'"));
            if (result && result->moveToNext()) {
                srid = atoi(result->getString(0));
            }
        }
        
        // bounds discovery
        int gridMinX;
        int gridMinY;
        int gridMaxX;
        int gridMaxY;
        
        // XXX - brute force bounds -- reimplement to do 4 queries
        // for MBB discovery (min x,y / max x,y)
        result.reset();
        int64_t index;
        int tileX;
        int tileY;
        
        std::ostringstream ss;
        ss << "SELECT key FROM tiles WHERE key <= " << OSMUtils::getOSMDroidSQLiteMaxIndex(minLevel);
        result = std::unique_ptr<Cursor>(database.query(ss.str().c_str()));
        if (result && result->moveToNext()) {
            index = result->getLong(0);
            gridMinX = OSMUtils::getOSMDroidSQLiteTileX(index);
            gridMaxX = gridMinX;
            gridMinY = OSMUtils::getOSMDroidSQLiteTileY(index);
            gridMaxY = gridMinY;
            while (result->moveToNext()) {
                index = result->getLong(0);
                tileX = OSMUtils::getOSMDroidSQLiteTileX(index);
                if (tileX < gridMinX)
                    gridMinX = tileX;
                else if (tileX > gridMaxX)
                    gridMaxX = tileX;
                tileY = OSMUtils::getOSMDroidSQLiteTileY(index);
                if (tileY < gridMinY)
                    gridMinY = tileY;
                else if (tileY > gridMaxY)
                    gridMaxY = tileY;
            }
        }
        
        int maxLevel = -1;
        result = std::unique_ptr<Cursor>(database.query("SELECT key FROM tiles ORDER BY key DESC LIMIT 1"));
        if (result && result->moveToNext()) {
            maxLevel = OSMUtils::getOSMDroidSQLiteZoomLevel(result->getLong(0));
        } else {
            atakmap::util::Logger::log(Logger::Error, "illegal state parsing SpatialiteDB");
            return nullptr;
        }
        
        double tileWidth;
        double tileHeight;
        if (srid == webMercatorProjSRID) {
            tileWidth = (360.0 / (double) (1 << minLevel));
            tileHeight = 170.1022 / (double) (1 << minLevel);
        } else {
            tileWidth = (180.0 / (1 << minLevel));
            tileHeight = (180.0 / (1 << minLevel));
        }
        
        std::string fileName = getFileName(file);
        TilesetInfo::Builder builder("tileset", "osmdroid");
        builder.setSpatialReferenceID(srid);
        builder.setUri(getFileAsAbsolute(file).c_str());
        builder.setName(fileName.c_str());
        builder.setImageryType(provider.c_str());
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);
        builder.setLevelCount((maxLevel - minLevel) + 1);
        builder.setZeroWidth(tileWidth);
        builder.setZeroHeight(tileHeight);
        builder.setOverview(false);
        builder.setImageExt("");
        double south;
        double west;
        double north;
        double east;
        if (srid == webMercatorProjSRID) {
            south = OSMUtils::mapnikTileLat(minLevel, gridMaxY + 1);
            west = OSMUtils::mapnikTileLng(minLevel, gridMinX);
            north = OSMUtils::mapnikTileLat(minLevel, gridMinY);
            east = OSMUtils::mapnikTileLng(minLevel, gridMaxX + 1);
        } else {
            double tileSize = (180.0 / (1 << minLevel));
            south = 90.0 - (tileSize * (gridMaxY + 1));
            west = -180.0 + (tileSize * gridMinX);
            north = 90.0 - (tileSize * gridMinY);
            east = -180.0 + (tileSize * (gridMaxX + 1));
        }
        
        builder.setFourCorners(GeoPoint(south, west),
                               GeoPoint(north, west),
                               GeoPoint(north, east),
                               GeoPoint(south, east));
        
        builder.setPathStructure("OSM_DROID_SQLITE");
        builder.setLevelOffset(minLevel);
        if (srid == webMercatorProjSRID)
            builder.setGridOriginLat(-85.0511);
        else
            builder.setGridOriginLat(-90);
        
        builder.setGridOffsetX(gridMinX);
        builder.setGridOffsetY((1 << minLevel) - gridMaxY - 1);
        builder.setGridWidth(gridMaxX - gridMinX + 1);
        builder.setGridHeight(gridMaxY - gridMinY + 1);
        
        /*System.out.println("CREATING OSMDROID DATASET");
        System.out.println("minLevel=" + minLevel);
        System.out.println("maxLevel=" + maxLevel);
        System.out.println("gridMinX=" + gridMinX);
        System.out.println("gridMaxX=" + gridMaxX);
        System.out.println("gridMinY=" + gridMinY);
        System.out.println("gridMaxY=" + gridMaxY);*/
        atakmap::util::Logger::log(atakmap::util::Logger::Debug, "CREATING OSMDROID DATASET: %s\nminLevel=%d\nmaxLevel=%d\ngridMinX=%d\ngridMaxX=%d\ngridMinY=%d\ngridMaxY=%d",
                                   fileName.c_str(),
                                   minLevel, maxLevel,
                                   gridMinX, gridMaxX,
                                   gridMinY, gridMaxY);
        
        return builder.build();
    } else {
        typedef std::map<PGSC::String, std::vector<PGSC::String>, PGSC::StringLess> DBStructVector;
        DBStructVector databaseStructure = db::getColumnNames(database);
        
        DBStructVector::const_iterator it = databaseStructure.find("tiles");
        if (it != databaseStructure.end()) {
            const std::vector<PGSC::String> &tilesTable = it->second;
            if (equalColumnNames(tilesTable, NETT_WARRIOR_TILES_COLUMN_NAMES)) {
                
                int minLevel = -1;
                int maxLevel = -1;
                std::string format;
                std::unique_ptr<Cursor> result(database.query("SELECT name, value FROM metadata"));
                std::string name;
                
                while (result->moveToNext()) {
                    name = result->getString(0);
                    if (name == "maxzoom")
                        maxLevel = atoi(result->getString(1));
                    else if (name == "minzoom")
                        minLevel = atoi(result->getString(1));
                    else if (name == "format")
                        format = result->getString(1);
                }
                
                if (minLevel < 0) {
                    result = std::unique_ptr<Cursor>(database.query("SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1"));
                    if (result->moveToNext())
                        minLevel = result->getInt(0);
                }
            
                if (maxLevel < 0) {
                    result = std::unique_ptr<Cursor>(database.query("SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1"));
                    if (result->moveToNext())
                        maxLevel = result->getInt(0);
                    
                }
                // no tiles present in database
                if (minLevel < 0 || maxLevel < 0) {
                    return nullptr;
                }
                
                // bounds discovery
                int gridMinX;
                int gridMinY;
                int gridMaxX;
                int gridMaxY;
                
                // do 4 queries for MBB discovery (min x,y / max x,y)
                
                std::stringstream ss;
                ss << "SELECT tile_column FROM tiles WHERE zoom_level = " << minLevel << " ORDER BY tile_column ASC LIMIT 1";
                result = std::unique_ptr<Cursor>(database.query(ss.str().c_str()));
                if (result->moveToNext())
                    gridMinX = result->getInt(0);
                else {
                    Logger::log(Logger::Error, "runtime error parsing tileset");
                    return nullptr;
                }
                
                ss.clear();
                ss.str("");
                ss << "SELECT tile_column FROM tiles WHERE zoom_level = " << minLevel << " ORDER BY tile_column DESC LIMIT 1";
                
                result = std::unique_ptr<Cursor>(database.query(ss.str().c_str()));
                
                if (result->moveToNext()) {
                    gridMaxX = result->getInt(0);
                }
                else {
                    Logger::log(Logger::Error, "runtime error parsing tileset");
                    return nullptr;
                }
                
                ss.clear();
                ss.str("");
                ss << "SELECT tile_row FROM tiles WHERE zoom_level = " << minLevel << " ORDER BY tile_row ASC LIMIT 1";
                
                result = std::unique_ptr<Cursor>(database.query(ss.str().c_str()));
                if (result->moveToNext()) {
                    gridMinY = result->getInt(0);
                }
                else {
                    Logger::log(Logger::Error, "runtime error parsing tileset");
                    return nullptr;
                }
                
                ss.clear();
                ss.str("");
                ss << "SELECT tile_row FROM tiles WHERE zoom_level = " << minLevel << " ORDER BY tile_row DESC LIMIT 1";
                
                result = std::unique_ptr<Cursor>(database.query(ss.str().c_str()));
                
                if (result->moveToNext()) {
                    gridMaxY = result->getInt(0);
                }
                else {
                    Logger::log(Logger::Error, "runtime error parsing tileset");
                    return nullptr;
                }
                
                double tileWidth = (360.0 / (double) (1 << minLevel));
                double tileHeight = 170.1022 / (double) (1 << minLevel);
                
                TilesetInfo::Builder builder("tileset", "tileset");
                builder.setSpatialReferenceID(webMercatorProjSRID);
                builder.setUri(getFileAsAbsolute(file).c_str());
                builder.setName(getFileName(file).c_str());
                builder.setTilePixelWidth(256);
                builder.setTilePixelHeight(256);
                builder.setLevelCount((maxLevel - minLevel) + 1);
                builder.setZeroWidth(tileWidth);
                builder.setZeroHeight(tileHeight);
                builder.setOverview(false);
                builder.setImageExt(format.c_str());
                double south = OSMUtils::mapnikTileLat(minLevel, (1 << minLevel) - gridMinY);
                double west = OSMUtils::mapnikTileLng(minLevel, gridMinX);
                double north = OSMUtils::mapnikTileLat(minLevel, (1 << minLevel)
                                                            - gridMaxY - 1);
                double east = OSMUtils::mapnikTileLng(minLevel, gridMaxX + 1);
                builder.setFourCorners(GeoPoint(south, west),
                                       GeoPoint(north, west),
                                       GeoPoint(north, east),
                                       GeoPoint(south, east));
                
                builder.setPathStructure("NETT_WARRIOR_SQLITE");
                builder.setLevelOffset(minLevel);
                builder.setGridOriginLat(-85.0511);
                
                builder.setGridOffsetX(gridMinX);
                builder.setGridOffsetY(gridMinY);
                builder.setGridWidth(gridMaxX - gridMinX + 1);
                builder.setGridHeight(gridMaxY - gridMinY + 1);
                
                return builder.build();
            }
        }
    }
    
    return nullptr;
}

bool TilesetInfo::operator==(const TilesetInfo &other) const {
    //TODO(bergeronj)--
    return false;
}

bool TilesetInfo::operator!=(const TilesetInfo &other) const {
    return !(*this == other);
}

/*TODO(bergeronj)--DatasetDescriptor *TilesetInfo::_parseXml(Stream *in, System::String *uri)
{
 try {
 DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 DocumentBuilder db = dbf.newDocumentBuilder();
 Document doc = db.parse(in);
 
 Builder builder = new Builder("tileset", "tileset");
 builder.setUri(uri);
 builder.setName(_fetchStringNode(doc, "name", ""));
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
 Locale.US));
 
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

void TilesetInfo::_parseCoverage(XmlNode *node, array<GeoPoint *> *_fourCorners)
{
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
}*/

/*TODO(bergeronj)--bool TilesetInfo::_sanityCheckCoverage(array<GeoPoint *> *_fourCorners, double _north, double _east, double _south, double _west) {
 
    if (_fourCorners[0] != nullptr &&
        _fourCorners[1] != nullptr &&
        _fourCorners[2] != nullptr &&
        _fourCorners[3] != nullptr) {
 
        if (_fourCorners[0]->latitude > _north ||
            _fourCorners[0]->longitude > _east ||
            _fourCorners[1]->latitude < _south ||
            _fourCorners[1]->longitude > _east ||
            _fourCorners[2]->latitude < _south ||
            _fourCorners[2]->longitude < _west ||
            _fourCorners[3]->latitude > _north ||
            _fourCorners[3]->longitude < _west) {
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

XmlNode *TilesetInfo::_fetchNode(XmlDocument *doc, System::String *name)
{
    XmlNodeList *nl = doc->GetElementsByTagName(name);
    XmlNode *n = nullptr;
    if (nl->Count > 0) {
        n = nl[0];
    }
    return n;
}

double TilesetInfo::_fetchDoubleNode(XmlDocument *doc, System::String *name, double fallback)
{
    double v = fallback;
    try {
        XmlNode *n = _fetchNode(doc, name);
        v = strtod(n->FirstChild->Value);
    } catch (Exception *ex) {
        // ignore
    }
    return v;
}

int TilesetInfo::_fetchIntNode(XmlDocument *doc, System::String *name, int fallback)
{
    int v = fallback;
    try {
        XmlNode *n = _fetchNode(doc, name);
        v = atoi(n->FirstChild->Value);
    } catch (Exception *ex) {
        // ignore
    }
    return v;
}

System::String *TilesetInfo::_fetchStringNode(XmlDocument *doc, System::String *name, System::String *fallback)
{
    System::String *v = fallback;
    try {
        XmlNode *n = _fetchNode(doc, name);
        v = n->FirstChild->Value;
    } catch (Exception *ex) {
        // ignore
    }
    return v;
}

bool TilesetInfo::_fetchBooleanNode(XmlDocument *doc, System::String *name, bool fallback)
{
    bool v = fallback;
    try {
        XmlNode *n = _fetchNode(doc, name);
        v = bool::Parse(n->FirstChild->Value);
    } catch (Exception *ex) {
        // ignore
    }
    return v;
}

array<double> *TilesetInfo::_fetchBounds(XmlDocument *doc, System::String *name, double s, double w, double n, double e) {
    array<double> *swne = gcnew array<double>(4);
    System::String *bounds = _fetchStringNode(doc, name, "");
    try {
        array<Char> *chars = gcnew array<Char>(1);
 
        chars[0] = ';';
        array<System::String *> *parts = bounds->Split(chars);
 
        chars[0] = ',';
        array<System::String *> *ll = parts[0]->Split(chars);
        swne[0] = strtod(ll[0]);
        swne[1] = strtod(ll[1]);
 
        chars[0] = ',';
        ll = parts[1]->Split(chars);
        swne[2] = strtod(ll[0]);
        swne[3] = strtod(ll[1]);
    } catch (Exception *ex) {
        swne[2] = n;
        swne[0] = s;
        swne[3] = e;
        swne[1] = w;
    }
 
    return swne;
}*/

int TilesetInfo::getTilePixelWidth() const
{
    return atoi(this->layerInfo->getExtraData("_tilePixelWidth"));
}

int TilesetInfo::getTilePixelHeight() const
{
    return atoi(this->layerInfo->getExtraData("_tilePixelHeight"));
}

double TilesetInfo::getZeroWidth() const
{
    return strtod(this->layerInfo->getExtraData("_zeroWidth"), NULL);
}

double TilesetInfo::getZeroHeight() const
{
    return strtod(this->layerInfo->getExtraData("_zeroHeight"), NULL);
}

double TilesetInfo::getGridOriginLat() const
{
    return strtod(atakmap::raster::DatasetDescriptor::getExtraData(*this->layerInfo, "gridOriginLat", "-90"), NULL);
}

double TilesetInfo::getGridOriginLng() const
{
    return strtod(DatasetDescriptor::getExtraData(*this->layerInfo, "gridOriginLng", "-180"), NULL);
}

int TilesetInfo::getGridOffsetX() const
{
    return atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "_gridX", "-1"));
}

int TilesetInfo::getGridOffsetY() const
{
    return atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "_gridY", "-1"));
}

int TilesetInfo::getGridWidth() const
{
    return atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "_gridWidth", "-1"));
}

int TilesetInfo::getGridHeight() const
{
    return atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "_gridHeight", "-1"));
}

const char *TilesetInfo::getImageExt() const
{
    return this->layerInfo->getExtraData("_imageExt");
}

bool TilesetInfo::isOverview() const
{
    return strcmp(DatasetDescriptor::getExtraData(*this->layerInfo, "_isOverview", "false"), "true") == 0;
}

int TilesetInfo::getLevelCount() const
{
    return atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "_levelCount", "1"));
}

TilesetInfo::Builder TilesetInfo::buildUpon()
{
    TilesetInfo::Builder builder(this->layerInfo->getProvider(), this->layerInfo->getDatasetType());
    builder.setName(this->layerInfo->getName());
    Envelope mbb = this->layerInfo->getCoverage(nullptr)->getEnvelope();
    builder.setFourCorners(GeoPoint(mbb.minY, mbb.minX),
                           GeoPoint(mbb.maxY, mbb.minX),
                           GeoPoint(mbb.maxY, mbb.maxX),
                           GeoPoint(mbb.minY, mbb.maxX));
    builder.setLevelCount(atoi(this->layerInfo->getExtraData("levelCount")));
    builder.setZeroWidth(this->getZeroWidth());
    builder.setZeroHeight(this->getZeroHeight());
    builder.setTilePixelWidth(this->getTilePixelWidth());
    builder.setTilePixelHeight(this->getTilePixelHeight());
    builder.setOverview(strcmp(DatasetDescriptor::getExtraData(*this->layerInfo, "_isOverview", "false"), "true") == 0);
    builder.setUri(this->layerInfo->getURI());
    builder.setImageExt(this->getImageExt());
    builder.setPathStructure(this->layerInfo->getExtraData("_pathStructure"));
    builder.setSpatialReferenceID(this->layerInfo->getSpatialReferenceID());
    builder.setLevelOffset(atoi(DatasetDescriptor::getExtraData(*this->layerInfo, "levelOffset", "0")));
    builder.setGridOriginLat(strtod(DatasetDescriptor::getExtraData(*this->layerInfo, "gridOriginLat", "-90"), NULL));
    builder.setGridOriginLng(strtod(DatasetDescriptor::getExtraData(*this->layerInfo, "gridOriginLng", "-180"), NULL));
    builder.setSubpath(DatasetDescriptor::getExtraData(*this->layerInfo, "subpath", ""));
    builder.setSupportSpi(DatasetDescriptor::getExtraData(*this->layerInfo, "supportSpi", nullptr));
    builder.setIsOnline(this->layerInfo->isRemote());
    
    return builder;
}

bool TilesetInfo::isArchive() const
{
    return _isArchiveUri;
}

bool TilesetInfo::isZipArchive(const char *path) {
    /*TODO(bergeronj)--if (path == nullptr)
        return false;
    else if (path->Length < 4)
        return false;
    else if (path->EndsWith(".zip"))
        return true;
    else
        return path->ToLower()->EndsWith(".zip");*/
    return false;
}

bool TilesetInfo::isSQLiteDb(const char *file) {
    const char *extPtr = strrchr(file, '.');
    return extPtr && strcmp(extPtr, ".sqlite") == 0;
}

TilesetInfo::Builder::Builder(const char *p, const char *datasetType) :
provider(p),
imageryType("Tileset"),
datasetType(datasetType),
_levelCount(1),
_zeroWidth(2.25),
_zeroHeight(2.25),
_tilePixelWidth(512),
_tilePixelHeight(512),
_isOverview(false),
_imageExt(".jpg"),
_pathStructure("NASAWW"),
srid(4326),
levelOffset(0),
gridOriginLat(-90),
gridOriginLng(-180),
gridOffsetX(-1),
gridOffsetY(-1),
gridWidth(-1),
gridHeight(-1),
subpath(""),
//TODO(bergeronj)--supportSpi(SimpleUriTilesetSupport::SPI->getName()),
isOnline(false)
{}

TilesetInfo::Builder &TilesetInfo::Builder::setImageryType(const char *imageryType) {
    this->imageryType = imageryType;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setName(const char *name) {
    this->name = name;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setLevelCount(int levelCount) {
    this->_levelCount = levelCount;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setZeroWidth(double zeroWidth) {
    this->_zeroWidth = zeroWidth;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setZeroHeight(double zeroHeight) {
    this->_zeroHeight = zeroHeight;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setOverview(bool isOverview) {
    this->_isOverview = isOverview;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setUri(const char *uri) {
    this->_uri = uri;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setTilePixelWidth(int tilePixelWidth) {
    this->_tilePixelWidth = tilePixelWidth;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setTilePixelHeight(int tilePixelHeight) {
    this->_tilePixelHeight = tilePixelHeight;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setImageExt(const char *imageExt) {
    this->_imageExt = imageExt;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setPathStructure(const char *pathStructure) {
    this->_pathStructure = pathStructure;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setFourCorners(const atakmap::core::GeoPoint &sw,
                                                           const atakmap::core::GeoPoint &nw,
                                                           const atakmap::core::GeoPoint &ne,
                                                           const atakmap::core::GeoPoint &se)
{
    this->sw = sw;
    this->nw = nw;
    this->ne = ne;
    this->se = se;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setSpatialReferenceID(int srid)
{
    this->srid = srid;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setLevelOffset(int l)
{
    this->levelOffset = l;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridOriginLat(double off)
{
    this->gridOriginLat = off;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridOriginLng(double off)
{
    this->gridOriginLng = off;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridOffsetX(int off)
{
    this->gridOffsetX = off;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridOffsetY(int off)
{
    this->gridOffsetY = off;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridWidth(int w)
{
    this->gridWidth = w;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setGridHeight(int h)
{
    this->gridHeight = h;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setSubpath(const char *p)
{
    this->subpath = p;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setExtra(const char *key, const char *value)
{
    this->extra[key] = value;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setSupportSpi(const char *spiClass)
{
    this->supportSpi = spiClass;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setIsOnline(bool isOnline)
{
    this->isOnline = isOnline;
    return *this;
}

TilesetInfo::Builder &TilesetInfo::Builder::setWorkingDir(const char *workingDir)
{
    this->workingDir = workingDir;
    return *this;
}

DatasetDescriptor *TilesetInfo::Builder::build()
{
    const double west = std::min(std::min(sw.longitude, nw.longitude), std::min(ne.longitude, se.longitude));
    const double east = std::max(std::max(sw.longitude, nw.longitude), std::max(ne.longitude, se.longitude));
    const double south = std::min(std::min(sw.latitude, nw.latitude), std::min(ne.latitude, se.latitude));
    const double north = std::max(std::max(sw.latitude, nw.latitude), std::max(ne.latitude, se.latitude));
    
    const double r0Width = _zeroWidth / (double)(1 << (_levelCount - 1));
    const double r0Height = _zeroHeight / (double)(1 << (_levelCount - 1));
    
    const int stx = (int)(west / r0Width);
    const int ftx = (int)ceil(east / r0Width);
    const int sty = (int)(south / r0Height);
    const int fty = (int)ceil(north / r0Height);
    
    std::map<PGSC::String, PGSC::String, PGSC::StringLess> extraData(this->extra);
    std::stringstream ss;
    
#define SET_EXTRA_N(name, val) ss.clear(); ss << val; extraData[#name] = ss.str().c_str(); ss.str("");
#define SET_EXTRA(name) SET_EXTRA_N(name, name)
    SET_EXTRA(_zeroHeight);
    SET_EXTRA(_zeroWidth);
    SET_EXTRA(_tilePixelHeight);
    SET_EXTRA(_tilePixelWidth);
    SET_EXTRA(levelOffset);
    SET_EXTRA(gridOriginLat);
    SET_EXTRA(gridOriginLng);
    SET_EXTRA_N(_gridX, gridOffsetX);
    SET_EXTRA_N(_gridY, gridOffsetY);
    SET_EXTRA_N(_gridWidth, gridWidth);
    SET_EXTRA_N(_gridHeight, gridHeight);
    if (_isOverview)
        SET_EXTRA_N(_isOverview, 1);
    SET_EXTRA_N(width, ((ftx - stx) * _tilePixelWidth));
    SET_EXTRA_N(height, ((fty - sty) * _tilePixelHeight));
    SET_EXTRA(_levelCount);
#undef SET_EXTRA
#undef SET_EXTRA_N
    
    if (_imageExt)
        extraData["_imageExt"] = _imageExt;
    if (_pathStructure)
        extraData["_pathStructure"] = _pathStructure;
    if (subpath)
        extraData["subpath"] = subpath;
    if (this->supportSpi)
        extraData["supportSpi"] = this->supportSpi;
    
    // XXX -
    const double gsd = ((east - west)*111319.458) / strtod(extraData["width"], NULL);
    
    return new ImageDatasetDescriptor(name,
                                    _uri,
                                    provider,
                                    datasetType,
                                    imageryType,
                                    (ftx - stx) * _tilePixelWidth,
                                    (fty - sty) * _tilePixelHeight,
                                    gsd,
                                    _levelCount,
                                    nw, ne, se, sw,
                                    srid,
                                    this->isOnline,
                                    this->workingDir, // workingDir
                                    extraData);
}

TilesetInfoDatasetDescriptorFactory::TilesetInfoDatasetDescriptorFactory()
: DatasetDescriptor::Factory("tileset", 1) { }

TilesetInfoDatasetDescriptorFactory::~TilesetInfoDatasetDescriptorFactory() throw() {
    
}

TilesetInfoDatasetDescriptorFactory::DescriptorSet *
TilesetInfoDatasetDescriptorFactory::createImpl(const char* filePath, const char* workingDir, CreationCallback*) const {
    
    TilesetInfoDatasetDescriptorFactory::DescriptorSet *result = new TilesetInfoDatasetDescriptorFactory::DescriptorSet;
    
    if (atakmap::util::isDirectory(filePath)) {
        atakmap::util::Logger::log(atakmap::util::Logger::Info, "processing directory: %s", filePath);
        std::vector<std::string> tilesets = atakmap::util::getDirContents(filePath);

        for (size_t i = 0; i < tilesets.size(); ++i) {
            std::string tilesetAbs = getFileAsAbsolute(tilesets[i].c_str());
            if (TilesetInfo::isZipArchive(tilesetAbs.c_str())) {
                DatasetDescriptor *dd = TilesetInfo::parse(tilesetAbs.c_str());
                if (dd != nullptr) {
                    atakmap::util::Logger::log(atakmap::util::Logger::Info, "adding descriptor for '%s'", tilesetAbs.c_str());
                    result->insert(dd);
                } else {
                    atakmap::util::Logger::log(atakmap::util::Logger::Error, "error creating DatasetDescriptor '%s'", tilesetAbs.c_str());
                }
            }
        }
    } else {
        DatasetDescriptor *dd = TilesetInfo::parse(filePath);
        if (dd != nullptr) {
            result->insert(dd);
        } else {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "error creating DatasetDescriptor ", filePath);
        }
    }
    
    if (result->size() == 0) {
        delete result;
        result = nullptr;
    }
    
    return result;
}

bool TilesetInfoDatasetDescriptorFactory::probeFile(const char* filePath, CreationCallback &callback) const {
    
    // Since tileset files are either an XML or database,
    // and you can't really tell if they are valid without trying
    // to parse the data, we are going  to call create here to test
    // if they are made. It shouldn't be too expensive, since only
    // a single file is being parsed, and data isn't actually being pulled
    // out, only boundaries, etc.
    
    // Pass Null for the ResourceSpi and CreateLayersCallback since they
    // aren't used in this call to create.
    TilesetInfoDatasetDescriptorFactory::DescriptorSet *layers = createImpl(filePath, nullptr, nullptr);
    const bool success = (layers != nullptr && layers->size() > 0);
    callback.setProbeResult(success);
    delete layers;
    return success;
}


unsigned short TilesetInfoDatasetDescriptorFactory::getVersion() const throw() {
    return 4;
}

namespace {
    GeoPoint parseGeoPoint(const char *str) {
        if (str == nullptr) {
            return GeoPoint(NAN, NAN);
        }
        
        double vals[6];
        int i = 0;
        
        const char *pos = str;
        char *end; // not const because strod is is messed up
        
        do {
            vals[i++] = strtod(pos, &end);
            pos = strchr(end, ',');
            if (pos) ++pos;
        } while (pos && i < 6);
        
        // for some reason the point has an invalid // format
        if (i != 2 && i != 3 && i != 6)
            return GeoPoint(NAN, NAN);
        
#if 1
        return GeoPoint(vals[0], vals[1]);
#else
        /*TODO(bergeronj)--if (parts->Length == 6) { // latitude,longitude,altitude,ce,le,altsource
            if (""->Equals(parts[2]))
                return nullptr;
            
            try {
                final double alt = Double.parseDouble(parts[2].trim());
                final double ce = Double.parseDouble(parts[3].trim());
                final double le = Double.parseDouble(parts[4].trim());
                AltitudeSource src = AltitudeSource.findFromAbbreviation(parts[5].trim());
                return new GeoPoint(lat, lon,
                                    new Altitude(alt,
                                                 AltitudeReference.HAE,
                                                 src),
                                    CE90_UNKNOWN,
                                    LE90_UNKNOWN
                                    );
            }
            catch (Exception *e) {
                Log.d(TAG, "error occurred parsing the 6 element variant of a geopoint", e);
                return nullptr;
            }
        }
        else if (parts.length == 3) { // latitude,longitude,altitude
            String altStr = parts[2].trim();
            if ("".equals(altStr))
                return nullptr;
            
            try {
                return new GeoPoint(lat, lon,
                                    new Altitude(Double.parseDouble(altStr),
                                                 AltitudeReference.HAE,
                                                 AltitudeSource.UNKNOWN),
                                    CE90_UNKNOWN,
                                    LE90_UNKNOWN);
            }
            catch (Exception e) {
                Log.d(TAG, "error occurred parsing the 3 element variant of a geopoint", e);
                return nullptr;
            }
        }
        else { // latitude,longitude
            return new GeoPoint(lat, lon, Altitude.UNKNOWN, CE90_UNKNOWN, LE90_UNKNOWN);
        }*/
#endif
    }
    
    bool equalColumnNames(const std::vector<PGSC::String> &columns, const char * const * columnNames) {
        
        const char * const *namePtr = columnNames;
        size_t i = 0;
        
        while (namePtr && i < columns.size()) {
            
            if (strcmp(columns[i], *namePtr) != 0) {
                return false;
            }
            
            ++namePtr;
            ++i;
        }
        
        return true;
    }

}