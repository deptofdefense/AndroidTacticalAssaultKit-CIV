
#include "formats/cesium3dtiles/C3DTTileset.h"
#include "util/DataInput2.h"
#include "port/StringBuilder.h"
#include "feature/Envelope2.h"
#include "math/Utils.h"
#include "math/Point2.h"
#include "math/Matrix2.h"
#include "core/ProjectionFactory3.h"
#include "util/URI.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Feature;
using namespace atakmap::math;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Core;

// Use tinygltf's copy of JSON for Modern C++
#include <tinygltf/json.hpp>
using json = nlohmann::json;

namespace {
    // Adapt to DataInput2 to std::streambuf
    class DataInput2Streambuf : public std::streambuf {
    public:
        DataInput2Streambuf(TAK::Engine::Util::DataInput2* input);

        int_type underflow() override;

    private:
        TAK::Engine::Util::DataInput2* input;
        char curr;
    };

    double parseDouble(const json& obj, const char* name, double def) NOTHROWS;

    std::string parseString(const json& obj, const char* name, const char* def);

    TAKErr parseVolume(C3DTVolume* result, const json& obj) NOTHROWS;

    TAKErr parseContent(C3DTContent* result, const json& obj) NOTHROWS;

    TAKErr parseTile(const C3DTTileset *tileset, const C3DTTile *parent, const json& obj, void* opaque, C3DTTilesetVisitor visitor) NOTHROWS;

    bool isFileSystemTileset(
        TAK::Engine::Port::String* filePath,
        TAK::Engine::Port::String* dirPath,
        TAK::Engine::Port::String* tilesetPath,
        C3DTFileType* type,
        const char* UR) NOTHROWS;

    bool isStreamingTileset(C3DTFileType* type,
        TAK::Engine::Port::String* filePath,
        TAK::Engine::Port::String* dirPath,
        TAK::Engine::Port::String* tilesetPath,
        const char* URI) NOTHROWS;
}

C3DTAsset::C3DTAsset()
{}

C3DTAsset::~C3DTAsset() NOTHROWS
{}

C3DTBox::C3DTBox() NOTHROWS
{}

C3DTBox::~C3DTBox() NOTHROWS
{}

C3DTRegion::C3DTRegion() NOTHROWS
{}

C3DTRegion::~C3DTRegion() NOTHROWS
{}

D3DTSphere::D3DTSphere() NOTHROWS
{}

D3DTSphere::~D3DTSphere() NOTHROWS
{}

C3DTVolume::C3DTVolume() NOTHROWS
    : type(Undefined)
{}

C3DTVolume::~C3DTVolume() NOTHROWS
{}

C3DTVolume::VolumeObject::VolumeObject() NOTHROWS
{}

C3DTVolume::VolumeObject::~VolumeObject() NOTHROWS
{}

C3DTContent::C3DTContent()
{}

C3DTContent::~C3DTContent() NOTHROWS
{}


struct C3DTExtras::Impl {
    json::iterator it;
    json::iterator end;
};

TAKErr C3DTExtras::getString(String* result, const char* name) const NOTHROWS {
    
    if (!result)
        return TE_InvalidArg;
    if (!name || !impl)
        return TE_BadIndex;

    if (impl->it != impl->end && impl->it->is_object()) {
        *result = parseString(*impl->it, name, "").c_str();
        return TE_Ok;
    }

    return TE_BadIndex;
}

C3DTTile::C3DTTile() 
    : parent(nullptr),
    geometricError(NAN),
    refine(C3DTRefine::Undefined),
    childCount(0),
    hasTransform(false) {
    memset(&transform, 0, sizeof(transform));
    transform[0] = transform[5] = transform[10] = transform[15] = 1.0;
}

C3DTTile::~C3DTTile() NOTHROWS 
{}

C3DTExtras::C3DTExtras() NOTHROWS
    : impl(nullptr)
{}

C3DTExtras::~C3DTExtras() NOTHROWS
{}

C3DTTileset::C3DTTileset() 
    : geometricError(NAN)
{}

C3DTTileset::~C3DTTileset() NOTHROWS
{}


TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DTTileset_parse(DataInput2 *input, void *opaque, C3DTTilesetVisitor visitor) NOTHROWS {

    if (!input)
        return TE_InvalidArg;

    DataInput2Streambuf buf(input);
    std::istream in(&buf);
    json obj = json::parse(in, nullptr, false);
    if (obj.is_discarded())
        return TE_Err;

    TAKErr code = TE_Ok;
    C3DTTileset tileset;

    auto asset = obj.find("asset");
    if (asset != obj.end()) {
        tileset.asset.version = parseString(*asset, "version", "").c_str();
        tileset.asset.tilesetVersion = parseString(*asset, "tileset", "").c_str();
    }
    tileset.geometricError = parseDouble(obj, "geometricError", 0.0);

    C3DTExtras::Impl extrasImpl;
    extrasImpl.it = obj.find("extras");
    extrasImpl.end = obj.end();
    tileset.extras.impl = &extrasImpl;

    auto root = obj.find("root");
    if (root != obj.end()) {
        code = parseTile(&tileset, nullptr, *root, opaque, visitor);
        if (code == TE_Done)
            code = TE_Ok;
    } else {
        code = TE_Err;
    }

    return code;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DTTileset_isSupported(bool* result, const char* URI) NOTHROWS {

    if (!result || !URI)
        return TE_InvalidArg;

    C3DTFileType type;
    *result = C3DT_probeSupport(&type, nullptr, nullptr, nullptr, nullptr, URI) == TE_Ok && 
        type == C3DTFileType_TilesetJSON;
    return TE_Ok;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DT_probeSupport(
    C3DTFileType* type,
    TAK::Engine::Port::String* fileURI,
    TAK::Engine::Port::String* tilesetURI,
    TAK::Engine::Port::String* baseURI,
    bool* isStreaming,
    const char* URI) NOTHROWS {

    if (!URI)
        return TE_InvalidArg;

    bool isStreamingValue = false;
    if (isFileSystemTileset(fileURI, baseURI, tilesetURI, type, URI) ||
        (isStreamingValue = isStreamingTileset(type, fileURI, baseURI, tilesetURI, URI)) == true) {

        if (isStreaming) *isStreaming = isStreamingValue;
        return TE_Ok;
    }

    return TE_Unsupported;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DTTileset_open(DataInput2Ptr& result, String* baseURI, bool* isStreaming, const char* URI) NOTHROWS {

    TAKErr code = TE_Ok;
    TAK::Engine::Port::String tilesetURI;
    TAK::Engine::Port::String fileURI;
    TAK::Engine::Port::String baseURIValue;
    C3DTFileType type;
    bool isStreamingValue = false;

    // catches nullptr URI
    code = C3DT_probeSupport(&type, &fileURI, &tilesetURI, &baseURIValue, &isStreamingValue, URI);
    if (code != TE_Ok)
        return code;
    if (type != C3DTFileType_TilesetJSON)
        return TE_Unsupported;

    code = URI_open(result, fileURI);
    if (code == TE_Ok) {
        if (isStreaming) *isStreaming = isStreamingValue;
        if (baseURI) *baseURI = baseURIValue;
    }

    return code;
}

double dist(TAK::Engine::Math::Point2<double> a, TAK::Engine::Math::Point2<double> b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double dz = b.z - a.z;
    return sqrt(dx*dx + dy*dy + dz*dz);
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DTTileset_accumulate(Matrix2* result, const C3DTTile* tile) NOTHROWS {

    if (!tile)
        return TE_Ok;

    Matrix2 parentMat;
    C3DTTileset_accumulate(&parentMat, tile->parent);
    
    if (!tile->hasTransform)
        return TE_Ok;

    Matrix2 transform(
        tile->transform[0], tile->transform[4], tile->transform[8], tile->transform[12],
        tile->transform[1], tile->transform[5], tile->transform[9], tile->transform[13],
        tile->transform[2], tile->transform[6], tile->transform[10], tile->transform[14],
        tile->transform[3], tile->transform[7], tile->transform[11], tile->transform[15]);

    transform.preConcatenate(parentMat);
    *result = transform;
    return TE_Ok;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::C3DTTileset_approximateTileBounds(Envelope2 *aabb, const C3DTTile* tile) NOTHROWS {

    if (!aabb || !tile)
        return TE_InvalidArg;

    TAK::Engine::Math::Point2<double> center;
    double radius;

    switch (tile->boundingVolume.type) {
    case C3DTVolume::Region: {
        const C3DTRegion& r = tile->boundingVolume.object.region;
        aabb->minX = toDegrees(r.west);
        aabb->minY = toDegrees(r.south);
        aabb->minZ = toDegrees(r.minimumHeight);
        aabb->maxX = toDegrees(r.east);
        aabb->maxY = toDegrees(r.north);
        aabb->maxZ = toDegrees(r.maximumHeight);
        return TE_Ok;
    }
    case C3DTVolume::Sphere: {
        const D3DTSphere& s = tile->boundingVolume.object.sphere;
        radius = s.radius;
        center.x = s.centerX;
        center.y = s.centerY;
        center.z = s.centerZ;
        break;
    }
    case C3DTVolume::Box: {
        const C3DTBox& b = tile->boundingVolume.object.box;
        radius = max(dist(TAK::Engine::Math::Point2<double>(b.xDirHalfLen[0], b.xDirHalfLen[1], b.xDirHalfLen[2]), TAK::Engine::Math::Point2<double>(0, 0, 0)),
            dist(TAK::Engine::Math::Point2<double>(b.yDirHalfLen[0], b.yDirHalfLen[1], b.yDirHalfLen[2]), TAK::Engine::Math::Point2<double>(0, 0, 0)),
            dist(TAK::Engine::Math::Point2<double>(b.zDirHalfLen[0], b.zDirHalfLen[1], b.zDirHalfLen[2]), TAK::Engine::Math::Point2<double>(0, 0, 0)));
        break;
    }
    default:
        return TE_IllegalState;
    }

    Matrix2 transform;
    C3DTTileset_accumulate(&transform, tile);

    transform.transform(&center, center);
    Projection2Ptr ecefProj(nullptr, nullptr);
    TAKErr code = ProjectionFactory3_create(ecefProj, 4978);
    TE_CHECKRETURN_CODE(code);
    GeoPoint2 centroid;
    code = ecefProj->inverse(&centroid, center);
    TE_CHECKRETURN_CODE(code);

    double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(centroid.latitude);
    double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(centroid.longitude);

    aabb->minX = centroid.longitude - (radius / metersDegLng);
    aabb->minY = centroid.latitude - (radius / metersDegLat);
    aabb->minZ = centroid.altitude - radius;
    aabb->maxX = centroid.longitude + (radius / metersDegLng);
    aabb->maxY = centroid.latitude + (radius / metersDegLat);
    aabb->maxZ = centroid.altitude + radius;

    return TE_Ok;
}

namespace {
    DataInput2Streambuf::DataInput2Streambuf(TAK::Engine::Util::DataInput2* input)
            : input(input),
            curr(std::char_traits<char>::eof()) {}

    DataInput2Streambuf::int_type DataInput2Streambuf::underflow() {
        size_t nr = 0;
        char ch;
        input->read((uint8_t*)&ch, &nr, 1);
        if (nr == 1) {
            curr = ch;
            setg(&curr, &curr, &curr);
            return std::char_traits<char>::to_int_type(static_cast<char>(curr));
        }
        return std::char_traits<char>::eof();
    }

    double parseDouble(const json& obj, const char* name, double def) NOTHROWS {
        auto it = obj.find(name);
        if (it != obj.end() && it->is_number()) {
            return it->get<double>();
        }
        return def;
    }

    std::string parseString(const json& obj, const char* name, const char* def) {
        auto it = obj.find(name);
        if (it != obj.end()) {
            return it->get<std::string>();
        }
        return def;
    }

    TAKErr parseVolume(C3DTVolume* result, const json& obj) NOTHROWS {
        auto it = obj.find("box");
        if (it != obj.end() && it->is_array() && it->size() == 12) {
            auto boxIt = it->begin();
            result->type = C3DTVolume::Box;
            result->object.box.centerX = boxIt->get<double>(); ++boxIt;
            result->object.box.centerY = boxIt->get<double>(); ++boxIt;
            result->object.box.centerZ = boxIt->get<double>(); ++boxIt;
            result->object.box.xDirHalfLen[0] = boxIt->get<double>(); ++boxIt;
            result->object.box.xDirHalfLen[1] = boxIt->get<double>(); ++boxIt;
            result->object.box.xDirHalfLen[2] = boxIt->get<double>(); ++boxIt;
            result->object.box.yDirHalfLen[0] = boxIt->get<double>(); ++boxIt;
            result->object.box.yDirHalfLen[1] = boxIt->get<double>(); ++boxIt;
            result->object.box.yDirHalfLen[2] = boxIt->get<double>(); ++boxIt;
            result->object.box.zDirHalfLen[0] = boxIt->get<double>(); ++boxIt;
            result->object.box.zDirHalfLen[1] = boxIt->get<double>(); ++boxIt;
            result->object.box.zDirHalfLen[2] = boxIt->get<double>(); ++boxIt;
            return TE_Ok;
        }
        it = obj.find("region");
        if (it != obj.end() && it->is_array() && it->size() == 6) {
            auto regionIt = it->begin();
            result->type = C3DTVolume::Region;
            result->object.region.west = regionIt->get<double>(); ++regionIt;
            result->object.region.south = regionIt->get<double>(); ++regionIt;
            result->object.region.east = regionIt->get<double>(); ++regionIt;
            result->object.region.north = regionIt->get<double>(); ++regionIt;
            result->object.region.minimumHeight = regionIt->get<double>(); ++regionIt;
            result->object.region.maximumHeight = regionIt->get<double>(); ++regionIt;
            return TE_Ok;
        }
        it = obj.find("sphere");
        if (it != obj.end() && it->is_array() && it->size() == 4) {
            auto sphereIt = it->begin();
            result->type = C3DTVolume::Sphere;
            result->object.sphere.centerX = sphereIt->get<double>(); ++sphereIt;
            result->object.sphere.centerY = sphereIt->get<double>(); ++sphereIt;
            result->object.sphere.centerZ = sphereIt->get<double>(); ++sphereIt;
            result->object.sphere.radius = sphereIt->get<double>(); ++sphereIt;
            return TE_Ok;
        }
        return TE_InvalidArg;
    }

    TAKErr parseContent(C3DTContent* result, const json& obj) NOTHROWS {
        TAKErr code = TE_Ok;
        result->uri = parseString(obj, "uri", parseString(obj, "url", "").c_str()).c_str();
        auto it = obj.find("boundingVolume");
        if (it != obj.end() && it->is_object()) {
            code = parseVolume(&result->boundingVolume, *it);
        }
        return code;
    }

    TAKErr parseTile(const C3DTTileset* tileset, const C3DTTile* parent, const json& obj, void* opaque, C3DTTilesetVisitor visitor) NOTHROWS {

        C3DTTile tile;
        TAKErr code = TE_Ok;

        tile.parent = parent;

        auto it = obj.find("transform");
        if (it != obj.end() && it->is_array() && it->size() == 16) {
            tile.hasTransform = true;
            size_t i = 0;
            for (auto mi = it->begin(); mi != it->end(); ++mi) {
                tile.transform[i++] = mi->get<double>();
            }
        }

        it = obj.find("boundingVolume");
        if (it != obj.end() && it->is_object()) {
            code = parseVolume(&tile.boundingVolume, *it);
            if (code != TE_Ok)
                return code;
        }

        it = obj.find("viewerRequestVolume");
        if (it != obj.end() && it->is_object()) {
            code = parseVolume(&tile.viewerRequestVolume, *it);
            if (code != TE_Ok)
                return code;
        }

        tile.geometricError = parseDouble(obj, "geometricError", tile.geometricError); // default comes from default constructor
        std::string refine = parseString(obj, "refine", "");
        int addCmp = -1, replaceCmp = -1;
        String_compareIgnoreCase(&addCmp, refine.c_str(), "add");
        String_compareIgnoreCase(&replaceCmp, refine.c_str(), "replace");
        tile.refine = parent ? parent->refine : C3DTRefine::Undefined;
        if (addCmp == 0)
            tile.refine = C3DTRefine::Add;
        else if (replaceCmp == 0)
            tile.refine = C3DTRefine::Replace;

        it = obj.find("content");
        if (it != obj.end() && it->is_object()) {
            code = parseContent(&tile.content, *it);
            if (code != TE_Ok)
                return code;
        }

        auto children = obj.find("children");
        if (children != obj.end())
            tile.childCount = children->size();

        code = visitor(opaque, tileset, &tile);
        if (code != TE_Ok)
            return code;

        if (children != obj.end()) {
            for (auto child = children->begin(); child != children->end(); ++child) {
                code = parseTile(tileset, &tile, *child, opaque, visitor);
                if (code != TE_Ok)
                    return code;
            }
        }

        return code;
    }

    bool isZipURI(const char* URI) NOTHROWS {
        
        size_t len = strlen(URI);
        const char* end = URI + len;
        while (end > URI && isspace(end[-1]))
            --end;

        const char *ext = strchr(URI, '.');

        if (!ext)
            return false;

        int cmp = 0;
        String_compareIgnoreCase(&cmp, ext, ".zip");
        
        return cmp == 0 && (ext + 4 == end);
    }

    bool isZipTilesetWithRootFolder(
        TAK::Engine::Port::String* filePath,
        TAK::Engine::Port::String* dirPath,
        TAK::Engine::Port::String* tilesetPath,
        C3DTFileType *type,
        const char* zipURI) NOTHROWS {

        String name;
        IO_getName(name, zipURI);

        std::string justName;
        const char *ext = strchr(name.get(), '.');

        justName.insert(0, name.get(), (ext - name.get()));
        TAK::Engine::Port::StringBuilder sb;
        if (TAK::Engine::Port::StringBuilder_combine(sb, zipURI, TAK::Engine::Port::Platform_pathSep(), justName.c_str()) != TE_Ok) {
            return false;
        }

        return isFileSystemTileset(filePath, dirPath, tilesetPath, type, sb.c_str());
    }

    bool isExt(const char* URI, const char* ext) NOTHROWS {
        const char* uriExt = strrchr(URI, '.');
        if (uriExt) {
            int cmp = -1;
            TAK::Engine::Port::String_compareIgnoreCase(&cmp, uriExt, ext);
            if (cmp == 0) {
                return true;
            }
        }
        return false;
    }

    TAKErr determineFileType(C3DTFileType* type, const char* name) NOTHROWS {

        if (!name)
            return TE_InvalidArg;

        int cmp = -1;
        TAK::Engine::Port::String_compareIgnoreCase(&cmp, name, "tileset.json");
        if (cmp == 0) {
            if (type) *type = C3DTFileType_TilesetJSON;
            return TE_Ok;
        }
        
        if (isExt(name, ".b3dm")) {
            if (type) *type = C3DTFileType_B3DM;
            return TE_Ok;
        }

        //TODO-- i3dm, .pnt

        return TE_Unsupported;
    }

    bool isFileSystemTileset(
        TAK::Engine::Port::String* filePath,
        TAK::Engine::Port::String* dirPath,
        TAK::Engine::Port::String* tilesetPath,
        C3DTFileType *type,
        const char* URI) NOTHROWS {

        TAK::Engine::Port::String dir;
        TAK::Engine::Port::String ts;

        bool isDir = false;
        IO_isDirectoryV(&isDir, URI);
        if (isDir) {
            dir = URI;
            TAK::Engine::Port::StringBuilder sb;
            if (TAK::Engine::Port::StringBuilder_combine(sb, URI, TAK::Engine::Port::Platform_pathSep(), "tileset.json") != TE_Ok) {
                return false;
            }
            bool exists = false;
            IO_existsV(&exists, sb.c_str());
            if (!exists) {
                return isZipURI(URI) && isZipTilesetWithRootFolder(filePath, dirPath, tilesetPath, type, URI);
            }
            ts = sb.c_str();
            if (filePath) *filePath = ts;
            if (type) *type = C3DTFileType_TilesetJSON;
        } else {
            TAK::Engine::Port::String name;
            IO_getName(name, URI);
            if (determineFileType(type, name) != TE_Ok)
                return false;

            ts = URI;
            IO_getParentFile(dir, URI);
            IO_isDirectoryV(&isDir, dir.get());
            if (!isDir)
                return false;

            TAK::Engine::Port::StringBuilder sb;
            TAK::Engine::Port::StringBuilder_combine(sb, dir, TAK::Engine::Port::Platform_pathSep(), "tileset.json");
            ts = sb.c_str();
            if (filePath) *filePath = URI;
        }

        if (dirPath)
            *dirPath = std::move(dir);
        if (tilesetPath)
            *tilesetPath = std::move(ts);
        return true;
    }

    bool isStreamingTileset(C3DTFileType* type,
        TAK::Engine::Port::String* filePath,
        TAK::Engine::Port::String* dirPath,
        TAK::Engine::Port::String* tilesetPath,
        const char* URI) NOTHROWS {

        TAK::Engine::Port::String scheme;
        TAK::Engine::Port::String path;
        TAK::Engine::Port::String query;
        TAK::Engine::Port::String fragment;
        TAK::Engine::Port::String authority;

        if (URI_parse(&scheme, &authority, nullptr, &path, &query, &fragment, URI) != TE_Ok || scheme.get() == nullptr)
            return false;

        // guard against nulls
        if (!query)
            query = "";
        if (!fragment)
            fragment = "";
        if (!path)
            path = "";
        if (!authority)
            authority = "";
        if (!scheme)
            scheme = "";

        int cmp = -1;
        TAK::Engine::Port::String_compareIgnoreCase(&cmp, "http", scheme);
        if (cmp != 0) {
            TAK::Engine::Port::String_compareIgnoreCase(&cmp, "https", scheme);
            if (cmp != 0)
                return false;
        }

        TAK::Engine::Port::String fileName;
        TAK::Engine::Port::String filePathValue, dirPathValue, tilesetPathValue;

        IO_getName(fileName, path.get());
        int tsCmp = -1;
        bool isB3Dm = false;
        if (fileName) {
            String_compareIgnoreCase(&tsCmp, fileName, "tileset.json");
            isB3Dm = String_endsWith(fileName, ".b3dm");
        }
        
        // Assume it is a base URI
        if (!fileName || (tsCmp != 0 && !isB3Dm)) {
            fileName = "tileset.json";
            StringBuilder sb;
            StringBuilder_combine(sb, scheme, ":", authority, "/tileset.json", query, fragment);
            filePathValue = sb.c_str();
            dirPathValue = URI;
            tilesetPathValue = filePathValue;
        } else {
            filePathValue = URI;
            URI_getParent(&dirPathValue, filePathValue);
            tilesetPathValue = filePathValue;
        }

        if (determineFileType(type, fileName) != TE_Ok)
            return false;

        if (filePath) *filePath = filePathValue;
        if (dirPath) *dirPath = dirPathValue;
        if (tilesetPath) *tilesetPath = tilesetPathValue;

        return true;
    }
}