#include "LAS.h"

#include <chrono>
#include <sstream>

#include "util/Tasking.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "db/DatabaseFactory.h"
#include "db/Query.h"
#include "db/Statement2.h"
#include "formats/cesium3dtiles/PNTS.h"
#include "liblas/capi/liblas.h"
#include "model/LASSceneInfoSpi.h"
#include "port/STLVectorAdapter.h"
#include "util/Memory.h"
#include "db/Query.h"
#include "util/AtomicCounter.h"
#include "model/LASSceneSpi.h"
#include "formats/gltf/GLTF.h"

#include <unistd.h>
#include <sstream>

// Use tinygltf's copy of JSON for Modern C++
#include <tinygltf/json.hpp>
using json = nlohmann::json;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::LAS;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util::Tasking;
using atakmap::util::AtomicCounter;

constexpr std::size_t subdivisionLevels = 5;
constexpr std::size_t tileWidth = 2 << (subdivisionLevels);
constexpr std::size_t smallThreshold = 20000;
constexpr std::size_t largeThreshold = 10000000;

constexpr const char* dbFileName = "laspoints.sqlite";
constexpr const char* chunkExt = ".chunk";

#define queryDb(database, result, sql, checkMoveToNext) \
    result.reset();                                     \
    code = database->query(result, sql);                \
    TE_CHECKRETURN_CODE(code)                           \
    if (checkMoveToNext) {                              \
        result->moveToNext();                           \
        TE_CHECKRETURN_CODE(code)                       \
    }

#define executeDb(database, sql)                   \
        code = database->execute(sql, nullptr, 0); \
        TE_CHECKRETURN_CODE(code)                  \

#define CREATE_TABLE_POINTS "CREATE TABLE points(id INTEGER PRIMARY KEY AUTOINCREMENT, x REAL, y REAL, z REAL, color INT)"
#define CREATE_INDEX_POINTS_XY "CREATE INDEX IF NOT EXISTS IdxXYZ ON points (x, y, z)"
#define INSERT_POINTS "INSERT INTO points (x, y, z, color) VALUES(?, ?, ?, ?)"
#define SELECT_POINTS_ALL "SELECT id, x, y, z, color from points"
#define SELECT_POINTS_LIMIT "SELECT id, x, y, z, color from points LIMIT "
#define SELECT_POINTS_ENVELOPE "SELECT Min(x), Min(y), Min(z), Max(x), Max(y), Max(z) FROM points"

std::string combinePath(const std::string& p1, const std::string& p2) {
    char sep = Platform_pathSep();
    std::stringstream statement;
    statement << p1 << (p1.back() == sep ? "" : std::string(1, sep)) << p2;
    return statement.str();
}

TAKErr queryPoints(QueryPtr& query, DatabasePtr& database, std::size_t& count, Envelope2Ptr& envelope) NOTHROWS {
    TAKErr code = TE_Ok;

    // Query for all points
    std::stringstream statement;

    if (count > 0) {
        statement << SELECT_POINTS_LIMIT << count;
    }
    else {
        statement << SELECT_POINTS_ALL;
    }

    queryDb(database, query, statement.str().c_str(), false);

    statement.str(std::string());

    QueryPtr countQuery(nullptr, nullptr);
    queryDb(database, countQuery, "SELECT COUNT(*) FROM points", true);

    int32_t tempint;
    code = countQuery->getInt(&tempint, 0);
    TE_CHECKRETURN_CODE(code)
    count = tempint;

    QueryPtr enevlopeQuery(nullptr, nullptr);
    queryDb(database, enevlopeQuery, SELECT_POINTS_ENVELOPE, true);

    code = enevlopeQuery->getDouble(&envelope->minX, 0);
    TE_CHECKRETURN_CODE(code)
    code = enevlopeQuery->getDouble(&envelope->minY, 1);
    TE_CHECKRETURN_CODE(code)
    code = enevlopeQuery->getDouble(&envelope->minZ, 2);
    TE_CHECKRETURN_CODE(code)
    code = enevlopeQuery->getDouble(&envelope->maxX, 3);
    TE_CHECKRETURN_CODE(code)
    code = enevlopeQuery->getDouble(&envelope->maxY, 4);
    TE_CHECKRETURN_CODE(code)
    code = enevlopeQuery->getDouble(&envelope->maxZ, 5);
    TE_CHECKRETURN_CODE(code)

    return TE_Ok;
}

bool lasHasColor(LASHeaderH header) {

    uint8_t dataFormat = LASHeader_GetDataFormatId(header);
    if (dataFormat == 0 || dataFormat == 1)
        return false;
    return true;
}

TAKErr getEnvelope(Envelope2Ptr& envelope, LASHeaderH* header, SceneInfoPtr scene) NOTHROWS {
    TAKErr code = TE_Ok;

    Envelope2 e(LASHeader_GetMinX(*header), LASHeader_GetMinY(*header), LASHeader_GetMinZ(*header), LASHeader_GetMaxX(*header),
        LASHeader_GetMaxY(*header), LASHeader_GetMaxZ(*header));

    Point2<double> pmin(e.minX, e.minY, e.minZ), pmax(e.maxX, e.maxY, e.maxZ);

    Projection2Ptr ecefProjection(nullptr, nullptr);
    Projection2Ptr lasProjection(nullptr, nullptr);

    if (scene && scene->srid != 4978) {
        code = ProjectionFactory3_create(ecefProjection, 4978);
        TE_CHECKRETURN_CODE(code)
        code = ProjectionFactory3_create(lasProjection, scene->srid);
        TE_CHECKRETURN_CODE(code)
    }

    if (lasProjection) {
        GeoPoint2 geo;
        lasProjection->inverse(&geo, pmin);
        ecefProjection->forward(&pmin, geo);

        lasProjection->inverse(&geo, pmax);
        ecefProjection->forward(&pmax, geo);
    }

    envelope = Envelope2Ptr(new Envelope2(pmin.x, pmin.y, pmin.z, pmax.x, pmax.y, pmax.z), Memory_deleter_const<Envelope2>);

    return TE_Ok;
}

TAKErr loadLASIntoDatabase(LASReaderH* reader, LASHeaderH* header, Envelope2Ptr& envelope, DatabasePtr& database, std::size_t maxPoints,
    SceneInfoPtr scene) NOTHROWS {
    TAKErr code = TE_Ok;

    int count = LASHeader_GetPointRecordsCount(*header);
    std::size_t n = 1;
    if (maxPoints > 0) n = count / maxPoints;

    StatementPtr insertPointsStmt(nullptr, nullptr);
    code = database->compileStatement(insertPointsStmt, INSERT_POINTS);
    TE_CHECKRETURN_CODE(code)

    LASPointH point;

    bool has_color = lasHasColor(*header);

    double x, y, z;

    auto t1 = std::chrono::high_resolution_clock::now();

    Projection2Ptr ecefProjection(nullptr, nullptr);
    Projection2Ptr lasProjection(nullptr, nullptr);

    if (scene && scene->srid != 4978) {
        code = ProjectionFactory3_create(ecefProjection, 4978);
        TE_CHECKRETURN_CODE(code)
        code = ProjectionFactory3_create(lasProjection, scene->srid);
        TE_CHECKRETURN_CODE(code)
    }

    std::size_t a = 0;
    int i = 0;
    code = database->beginTransaction();
    TE_CHECKRETURN_CODE(code)
    while ((point = LASReader_GetNextPoint(*reader)) != nullptr && i++ < maxPoints) {
        x = LASPoint_GetX(point);
        y = LASPoint_GetY(point);
        z = LASPoint_GetZ(point);

        Point2<double> projectedPoint(x, y, z);

        if (lasProjection) {
            GeoPoint2 geo;
            lasProjection->inverse(&geo, projectedPoint);
            ecefProjection->forward(&projectedPoint, geo);
        }

        int rgba;
        if (has_color) {
            unsigned short rgb[3];
            LASPoint_GetColorRGB(point, rgb);
            uint8_t r = (uint8_t)rgb[0];
            uint8_t g = (uint8_t)rgb[1];
            uint8_t b = (uint8_t)rgb[2];
            rgba = (b << 24) + (g << 16) + (r << 8) + 255;
        }
        else {
            float fr, fg, fb;
            TAK::Engine::Model::LASSceneSPI_computeZColor(LASPoint_GetRawZ(point), &fr, &fg, &fb);

            uint8_t r = uint8_t(fr * 255.0);
            uint8_t g = uint8_t(fg * 255.0);
            uint8_t b = uint8_t(fb * 255.0);
            rgba = (b << 24) + (g << 16) + (r << 8) + 255;
        }

        code = insertPointsStmt->clearBindings();
        TE_CHECKRETURN_CODE(code)
        code = insertPointsStmt->bindDouble(1, projectedPoint.x);
        TE_CHECKRETURN_CODE(code)
        code = insertPointsStmt->bindDouble(2, projectedPoint.y);
        TE_CHECKRETURN_CODE(code)
        code = insertPointsStmt->bindDouble(3, projectedPoint.z);
        TE_CHECKRETURN_CODE(code)
        code = insertPointsStmt->bindInt(4, rgba);
        TE_CHECKRETURN_CODE(code)
        code = insertPointsStmt->execute();
        TE_CHECKRETURN_CODE(code)
    }

    code = database->setTransactionSuccessful();
    TE_CHECKRETURN_CODE(code)
    code = database->endTransaction();
    TE_CHECKRETURN_CODE(code)

    auto t2 = std::chrono::high_resolution_clock::now();
    Logger_log(LogLevel::TELL_Warning, "time to insert LAS points: %d ms",
        std::chrono::duration_cast<std::chrono::milliseconds>(t2 - t1).count());

    executeDb(database, CREATE_INDEX_POINTS_XY);

    auto t3 = std::chrono::high_resolution_clock::now();
    Logger_log(LogLevel::TELL_Warning, "time to index LAS points: %d ms",
        std::chrono::duration_cast<std::chrono::milliseconds>(t3 - t2).count());

    Logger_log(LogLevel::TELL_Warning, "total time to import LAS points: %d ms",
        std::chrono::duration_cast<std::chrono::milliseconds>(t3 - t1).count());
    return TE_Ok;
}

constexpr std::size_t getProjectedFileHeaderSize() {
    return sizeof(Envelope2);
}

struct PointData {
    float x, y, z;
    uint32_t color;
};

struct OctTreeNode {
    std::vector<OctTreeNode> children;
    TAK::Engine::Feature::Envelope2 aabb;
    uint32_t count = 0;
    std::vector<PointData> points;
    FileOutput2* chunkFile = nullptr;

    void clearAllPoints() {
        for (auto& child : children) {
            child.clearAllPoints();
        }
        points = std::move(std::vector<PointData>());
    }

    void subdivide() {
        if (children.empty()) {
            children.resize(8);
            double widthx = (aabb.maxX - aabb.minX) / 2.0;
            double widthy = (aabb.maxY - aabb.minY) / 2.0;
            double widthz = (aabb.maxZ - aabb.minZ) / 2.0;
            children[0].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX - widthx, aabb.maxY - widthy, aabb.maxZ - widthz));
            children[1].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX + widthx, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY - widthy, aabb.maxZ - widthz));
            children[2].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX, aabb.minY + widthy, aabb.minZ, aabb.maxX - widthx, aabb.maxY, aabb.maxZ - widthz));
            children[3].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX + widthx, aabb.minY + widthy, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ - widthz));

            children[4].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX, aabb.minY, aabb.minZ + widthz, aabb.maxX - widthx, aabb.maxY - widthy, aabb.maxZ));
            children[5].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX + widthx, aabb.minY, aabb.minZ + widthz, aabb.maxX, aabb.maxY - widthy, aabb.maxZ));
            children[6].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX, aabb.minY + widthy, aabb.minZ + widthz, aabb.maxX - widthx, aabb.maxY, aabb.maxZ));
            children[7].setAABB(TAK::Engine::Feature::Envelope2(aabb.minX + widthx, aabb.minY + widthy, aabb.minZ + widthz, aabb.maxX, aabb.maxY, aabb.maxZ));
        }
    }

    inline std::size_t childIndex(const PointData& point) {

        double widthx = (aabb.maxX - aabb.minX) / 2.0;
        double widthy = (aabb.maxY - aabb.minY) / 2.0;
        double widthz = (aabb.maxZ - aabb.minZ) / 2.0;

        return  (point.x > aabb.minX + widthx) | ((point.y > aabb.minY + widthy) << 1) | ((point.z > aabb.minZ + widthz) << 2);
    }

    void writeToChunk(const PointData& point) {
        if (children.empty()) {
            chunkFile->write(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&point)), sizeof(PointData));
            return;
        }

        children[childIndex(point)].writeToChunk(point);
    }

    void insert(const PointData& point) {
        if (children.empty()) {
            points.push_back(point);
            return;
        }

        children[childIndex(point)].insert(point);
    }

    void setAABB(const TAK::Engine::Feature::Envelope2& envelope) {
        aabb = envelope;
    }
};

json writeChildToJSON(OctTreeNode* root, std::size_t index, std::string prefix = "", float geometricError = 0) NOTHROWS {
    json json;
    auto& envelope = root->aabb;

    double centerX = (envelope.minX + envelope.maxX) / 2.0;
    double centerY = (envelope.minY + envelope.maxY) / 2.0;
    double centerZ = (envelope.minZ + envelope.maxZ) / 2.0;

    double halfX = envelope.maxX - centerX;
    double halfY = envelope.maxY - centerY;
    double halfZ = envelope.maxZ - centerZ;

    json["geometricError"] = geometricError;

    json["boundingVolume"]["box"] = {
        centerX, centerY, centerZ,
        halfX, 0, 0,
        0, halfY, 0,
        0, 0, halfZ };

    prefix = prefix + std::to_string(index);

    json["content"]["uri"] = "r_" + prefix + ".pnts";

    int childindex = 0;
    float childGeometricError = geometricError / 4.0f;
    if (!root->children.empty()) {
        nlohmann::json children;
        for (auto& child : root->children) {
            children.push_back(writeChildToJSON(&child, childindex++, prefix, childGeometricError));
        }
        json["children"] = children;

    }
    return json;
}

TAKErr createProjectedFile(Envelope2& computedEnvelop, const char* lasFilePath, const char* dbFile, const SceneInfo& scene, bool has_color, std::size_t start, std::size_t numPoints) NOTHROWS {
    TAKErr code = TE_Ok;
    if (numPoints == 0)
        return TE_Ok;
    double indexMult = (double)(tileWidth);

    auto reader = LASReader_Create(lasFilePath);
    std::unique_ptr<LASReaderH, void (*)(LASReaderH*)> readerP(&reader, [](LASReaderH* r) { LASReader_Destroy(*r); });

    FileOutput2 file;
    code = file.open(dbFile, "rb+");
    TE_CHECKRETURN_CODE(code);

    Projection2Ptr ecefProjection(nullptr, nullptr), lasProjection(nullptr, nullptr);

    if (scene.srid != 4978) {
        code = ProjectionFactory3_create(ecefProjection, 4978);
        TE_CHECKRETURN_CODE(code)
        code = ProjectionFactory3_create(lasProjection, scene.srid);
        TE_CHECKRETURN_CODE(code)
    }
    Envelope2 e;
    TAK::Engine::Formats::GLTF::GLTF_initMeshAABB(e);

    file.seek(getProjectedFileHeaderSize() + start * sizeof(PointData));
    if (start != 0) {
        LASError err = LASReader_Seek(reader, (unsigned int)start);
        if (err != LE_None) {
            return TE_IO;
        }
    }
    LASPointH point;
    std::size_t i = 0;
    while ((point = LASReader_GetNextPoint(reader)) != nullptr && i++ < numPoints) {

        Point2<double> projectedPoint(LASPoint_GetX(point), LASPoint_GetY(point), LASPoint_GetZ(point));

        if (LASError_GetErrorCount() != 0) {
            auto error = LASError_GetLastErrorMsg();
        }
        if (lasProjection) {
            GeoPoint2 geo;
            lasProjection->inverse(&geo, projectedPoint);
            ecefProjection->forward(&projectedPoint, geo);
        }

        uint32_t rgba;
        if (has_color) {
            unsigned short rgb[3];
            LASPoint_GetColorRGB(point, rgb);
            uint8_t r = (uint8_t)((double(rgb[0]) / double(std::numeric_limits<uint16_t>::max()) * 255));
            uint8_t g = (uint8_t)((double(rgb[1]) / double(std::numeric_limits<uint16_t>::max()) * 255));
            uint8_t b = (uint8_t)((double(rgb[2]) / double(std::numeric_limits<uint16_t>::max()) * 255));
            rgba = (b << 24) + (g << 16) + (r << 8) + 255;
        }
        else {
            float fr, fg, fb;
            TAK::Engine::Model::LASSceneSPI_computeZColor(LASPoint_GetRawZ(point), &fr, &fg, &fb);

            uint8_t r = uint8_t(fr * 255.0);
            uint8_t g = uint8_t(fg * 255.0);
            uint8_t b = uint8_t(fb * 255.0);
            rgba = (b << 24) + (g << 16) + (r << 8) + 255;
        }

        e.maxX = std::max(e.maxX, projectedPoint.x);
        e.maxY = std::max(e.maxY, projectedPoint.y);
        e.maxZ = std::max(e.maxZ, projectedPoint.z);

        e.minX = std::min(e.minX, projectedPoint.x);
        e.minY = std::min(e.minY, projectedPoint.y);
        e.minZ = std::min(e.minZ, projectedPoint.z);

        PointData pointData{ (float)projectedPoint.x, (float)projectedPoint.y, (float)projectedPoint.z, rgba };
        file.write(reinterpret_cast<uint8_t*>(&pointData), (int)sizeof(PointData));
    }
    computedEnvelop = e;
    return TE_Ok;
}
/*
* Reproject points into sqlite database or temporary projection file
*/
TAKErr createDatabase(const char* lasFilePath, const char* outputPath, std::size_t maxPoints, LAS_Method method) NOTHROWS {
    TAKErr code = TE_Ok;

    // Create a sceneinfo
    STLVectorAdapter<SceneInfoPtr> scenes;
    SceneInfoPtr sceneInfo;
    code = LASSceneInfo_create(scenes, lasFilePath);
    TE_CHECKRETURN_CODE(code)
    if (scenes.size() != 1) return TE_Unsupported;
    scenes.get(sceneInfo, 0);

    // Open an LAS reader
    auto readerObj = LASReader_Create(lasFilePath);
    std::unique_ptr<LASReaderH, void (*)(LASReaderH*)> reader(&readerObj, [](LASReaderH* r) { LASReader_Destroy(*r); });
    if (*reader == nullptr) {
        LASError_Print(LASError_GetLastErrorMsg());
        LASError_Pop();
        return TE_Unsupported;
    }

    std::string dbFilePath = combinePath(outputPath, dbFileName);

    // Get the LAS header and envelope
    auto headerObj = LASReader_GetHeader(*reader);
    std::unique_ptr<LASHeaderH, void (*)(LASHeaderH*)> header(&headerObj, [](LASHeaderH* h) { LASHeader_Destroy(*h); });
    Envelope2Ptr envelope(nullptr, nullptr);
    code = getEnvelope(envelope, &headerObj, sceneInfo);
    TE_CHECKRETURN_CODE(code)
    bool has_color = lasHasColor(*header);
    Envelope2Ptr computedEnvelope(nullptr, nullptr);

    if (method == LAS_Method::SQLITE) {
        // Create database
        DatabasePtr database(nullptr, nullptr);
        code = DatabaseFactory_create(database, DatabaseInformation(dbFilePath.c_str()));
        TE_CHECKRETURN_CODE(code)
        executeDb(database, CREATE_TABLE_POINTS);

        code = loadLASIntoDatabase(reader.get(), header.get(), envelope, database, maxPoints, sceneInfo);
        TE_CHECKRETURN_CODE(code)
    }
    else {
        std::size_t totalPoints = LASHeader_GetPointRecordsCount(*header);
        Envelope2 cEnvelope;
        header.reset();
        reader.reset();

        code = IO_truncate(dbFilePath.c_str(), totalPoints * sizeof(PointData) + getProjectedFileHeaderSize());
        TE_CHECKRETURN_CODE(code);

        if (method == LAS_Method::CHUNK_PARALLEL) {
            std::size_t pointsPerThread = largeThreshold;
            std::size_t numThreads = totalPoints / pointsPerThread + 1;
            std::vector<Future<Envelope2>> futures;
            for (std::size_t i = 0; i < numThreads; ++i) {
                std::size_t start = pointsPerThread * i;
                if (start > totalPoints)
                    break;

                std::size_t points = std::min(totalPoints - start, pointsPerThread);
                const char* temp = nullptr;
                futures.push_back(Task_begin(GeneralWorkers_flex(), createProjectedFile, lasFilePath, dbFilePath.c_str(), *sceneInfo, has_color, start, points));
            }
            TAK::Engine::Formats::GLTF::GLTF_initMeshAABB(cEnvelope);

            for (auto& future : futures) {
                Envelope2 e;
                future.await(e, code);
                cEnvelope.maxX = std::max(cEnvelope.maxX, e.maxX);
                cEnvelope.maxY = std::max(cEnvelope.maxY, e.maxY);
                cEnvelope.maxZ = std::max(cEnvelope.maxZ, e.maxZ);

                cEnvelope.minX = std::min(cEnvelope.minX, e.minX);
                cEnvelope.minY = std::min(cEnvelope.minY, e.minY);
                cEnvelope.minZ = std::min(cEnvelope.minZ, e.minZ);
            }
        }
        else {
            createProjectedFile(cEnvelope, lasFilePath, dbFilePath.c_str(), *sceneInfo, has_color, 0, totalPoints);
        }
        FileOutput2 file;
        file.open(dbFilePath.c_str(), "rb+");
        file.write(reinterpret_cast<uint8_t*>(&cEnvelope), sizeof(Envelope2));
        file.close();
    }

    return TE_Ok;
}

/*
* Write a nodes' points to PNTS file
*/

TAKErr writeNode(std::string base, OctTreeNode* root, std::string prefix) NOTHROWS {

    std::string filename = combinePath(base, "r_" + prefix + ".pnts");

    if (!root->points.empty())
        TAK::Engine::Formats::Cesium3DTiles::PNTS_write(filename.c_str(), reinterpret_cast<uint8_t*>(root->points.data()), root->points.size());

    int childindex = 0;
    for (auto& child : root->children) {
        writeNode(base, &child, prefix + std::to_string(childindex));
        ++childindex;
    }
    return TE_Ok;
}

/*
* Write the tree, starting with tileset.json, then recurisvely write all nodes PNTS files
*/
TAKErr writeTree(const char* base, OctTreeNode* root, std::string basePrefix = std::string()) NOTHROWS {

    constexpr std::size_t geometricError = 1024;
    json obj;
    obj["asset"]["version"] = "1.0";
    obj["geometricError"] = geometricError;

    json rootjson;

    auto& envelope = root->aabb;

    double centerX = (envelope.minX + envelope.maxX) / 2.0;
    double centerY = (envelope.minY + envelope.maxY) / 2.0;
    double centerZ = (envelope.minZ + envelope.maxZ) / 2.0;

    double halfX = envelope.maxX - centerX;
    double halfY = envelope.maxY - centerY;
    double halfZ = envelope.maxZ - centerZ;

    rootjson["boundingVolume"]["box"] = {
        centerX, centerY, centerZ,
        halfX, 0, 0,
        0, halfY, 0,
        0, 0, std::max(halfX, halfY) };

    rootjson["content"]["uri"] = "r_0.pnts";
    rootjson["refine"] = "ADD";
    std::string prefix = "0";
    int index = 0;
    if (!root->children.empty()) {
        nlohmann::json children;
        for (auto& child : root->children) {
            children.push_back(writeChildToJSON(&child, index++, prefix, geometricError));
        }
        rootjson["children"] = children;

    }
    rootjson["geometricError"] = geometricError;

    obj["root"] = rootjson;

    FileOutput2 fileout;
    std::string filename = combinePath(base, "tileset.json");
    fileout.open(filename.c_str());
    fileout.writeString(obj.dump(2).c_str());

    writeNode(std::string(base), root, basePrefix + std::string("0"));

    return TE_Ok;
}

/*
* Create a regular octree grid, and count the number of points in each grid
*/
TAKErr countingSort(std::vector<PointData>* allPoints, atakmap::util::AtomicCounter* counts, FileInput2& input, const TAK::Engine::Feature::Envelope2& envelope) NOTHROWS {

    double indexMult = (double)(tileWidth);

    const double widthX = (envelope.maxX - envelope.minX);
    const double widthY = (envelope.maxY - envelope.minY);
    const double widthZ = (envelope.maxZ - envelope.minZ);
    std::size_t read;
    PointData pointData;

    while (input.read(reinterpret_cast<uint8_t*>(&pointData), &read, sizeof(PointData)) == TE_Ok) {

        double indexx = std::min(((pointData.x - envelope.minX) / widthX) * indexMult, indexMult - 1);
        double indexy = std::min(((pointData.y - envelope.minY) / widthY) * indexMult, indexMult - 1);
        double indexz = std::min(((pointData.z - envelope.minZ) / widthZ) * indexMult, indexMult - 1);

        std::size_t index = (((int)indexx) * tileWidth + (int)indexy) * tileWidth + (int)indexz;

        counts[index].add(1);
        if (allPoints)
            allPoints->push_back(pointData);
    }
    return TE_Ok;
}

/*
* Create a regular octree grid, and count the number of points in each grid
*/
TAKErr countingSort(std::vector<PointData>& allPoints, atakmap::util::AtomicCounter* counts, TAK::Engine::DB::QueryPtr& query, const TAK::Engine::Feature::Envelope2Ptr& envelope) NOTHROWS {
    TAKErr code = TE_Ok;

    double indexMult = (double)(tileWidth);

    const double widthX = (envelope->maxX - envelope->minX);
    const double widthY = (envelope->maxY - envelope->minY);
    const double widthZ = (envelope->maxZ - envelope->minZ);

    while (query->moveToNext() == TE_Ok) {
        double x, y, z;
        uint32_t color;
        code = query->getDouble(&x, 1);
        TE_CHECKRETURN_CODE(code)
        code = query->getDouble(&y, 2);
        TE_CHECKRETURN_CODE(code)
        code = query->getDouble(&z, 3);
        TE_CHECKRETURN_CODE(code)
        code = query->getInt((int32_t*)&color, 4);
        TE_CHECKRETURN_CODE(code)

        double indexx = std::min(((x - envelope->minX) / widthX) * indexMult, indexMult - 1);
        double indexy = std::min(((y - envelope->minY) / widthY) * indexMult, indexMult - 1);
        double indexz = std::min(((z - envelope->minZ) / widthZ) * indexMult, indexMult - 1);

        std::size_t index = (((int)indexx) * tileWidth + (int)indexy) * tileWidth + (int)indexz;

        counts[index].add(1);
        allPoints.push_back(PointData{ (float)x, (float)y, (float)z, color });
    }
    return TE_Ok;
}

/*
* Using a counted regular grid, create an Octree such that no node has more than threshold number of points
*/
TAKErr mergeTiles(OctTreeNode* root, AtomicCounter* counts, std::size_t threshold, std::size_t level = 1, std::size_t indexx = 0, std::size_t indexy = 0, std::size_t indexz = 0) NOTHROWS {

    std::size_t width = tileWidth >> (level - 1);

    TAKErr code = TE_Ok;

    if (width == 1) {
        root->count = counts[(indexx * tileWidth + indexy) * tileWidth + indexz].currentValue();
    }

    for (std::size_t i = indexx; i < width + indexx; ++i) {
        for (std::size_t j = indexy; j < width + indexy; ++j) {
            for (std::size_t k = indexz; k < width + indexz; ++k) {
                root->count += counts[(i * tileWidth + j) * tileWidth + k].currentValue();
                if (root->children.empty() && root->count > threshold) {
                    root->subdivide();
                    code = mergeTiles(&root->children[0], counts, threshold, level + 1, indexx, indexy, indexz);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[1], counts, threshold, level + 1, indexx + width / 2, indexy, indexz);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[2], counts, threshold, level + 1, indexx, indexy + width / 2, indexz);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[3], counts, threshold, level + 1, indexx + width / 2, indexy + width / 2, indexz);
                    TE_CHECKRETURN_CODE(code);

                    code = mergeTiles(&root->children[4], counts, threshold, level + 1, indexx, indexy, indexz + width / 2);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[5], counts, threshold, level + 1, indexx + width / 2, indexy, indexz + width / 2);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[6], counts, threshold, level + 1, indexx, indexy + width / 2, indexz + width / 2);
                    TE_CHECKRETURN_CODE(code);
                    code = mergeTiles(&root->children[7], counts, threshold, level + 1, indexx + width / 2, indexy + width / 2, indexz + width / 2);
                    TE_CHECKRETURN_CODE(code);

                    root->count = 0;
                    for (auto& child : root->children)
                        root->count += std::max(child.count, (uint32_t)child.points.size());

                    return TE_Ok;
                }
            }
        }
    }

    return TE_Ok;
}

/*
* insert all points into the octree
*/
TAKErr insertPoints(const std::vector<PointData>& allPoints, OctTreeNode* root) NOTHROWS {
    for (const PointData& point : allPoints) {
        root->insert(point);
    }
    return TE_Ok;
}

/*
* Subsample children nodes into current node, this algorithm could be revisted 
*/
TAKErr subsamplePoints(OctTreeNode* root, std::size_t threshold) {
    TAKErr code;
    if (!root->children.empty()) {
        std::size_t childCount = 0;
        for (auto& child : root->children) {
            code = subsamplePoints(&child, threshold);
            TE_CHECKRETURN_CODE(code);

            childCount += child.points.size();
        }

        std::size_t n = std::max(childCount / threshold, (std::size_t)1) + 1;

        for (auto& child : root->children) {

            std::vector<PointData> newChild;
            newChild.reserve(child.points.size());
            root->points.reserve(threshold);
            for (std::size_t i = 0; i < child.points.size(); ++i) {
                if ((i % n) == 0)
                    root->points.push_back(child.points[i]);
                else
                    newChild.push_back(child.points[i]);
            }
            child.points.swap(newChild);
        }
    }
    return TE_Ok;
}

/*
* Color nodes by octree level, used for debugging
*/
TAKErr colorTreeByLevel(OctTreeNode* root, std::size_t level = 0) {
    std::vector<uint8_t> levelColors =
    { 0, 255, 255,
    255,0,255,
    0,128,0,
    255, 128, 128,
    255,255,204,
    128,128,0,
    128,0,128,
    0,128,128,
    192,192,192,
    128,128,128,
    153,153,255,
    153,51,102,
    };

    if (!root->children.empty()) {
        for (auto& child : root->children) {
            colorTreeByLevel(&child, level + 1);
        }
    }
    uint8_t r = levelColors[level * 3 + 0];
    uint8_t g = levelColors[level * 3 + 1];
    uint8_t b = levelColors[level * 3 + 2];

    uint32_t color = (r << 24) + (g << 16) + (b << 8) + 0xFF;

    for (auto& point : root->points) {
        point.color = color;
    }

    return TE_Ok;
}

/*
* Process an sqlite chunk
*/
TAKErr processChunkSqlite(QueryPtr& query, std::size_t count, const Envelope2Ptr& envelope, const char* baseURI) NOTHROWS {
    std::vector<atakmap::util::AtomicCounter> counts(tileWidth * tileWidth * tileWidth);
    std::vector<PointData> allPoints;
    allPoints.reserve(count);
    TAKErr code;

    code = countingSort(allPoints, counts.data(), query, envelope);
    TE_CHECKRETURN_CODE(code);
    std::unique_ptr<OctTreeNode> root(new OctTreeNode());
    root->setAABB(*envelope);
    code = mergeTiles(root.get(), counts.data(), smallThreshold);
    TE_CHECKRETURN_CODE(code);

    code = insertPoints(allPoints, root.get());
    TE_CHECKRETURN_CODE(code);

    code = subsamplePoints(root.get(), smallThreshold);
    TE_CHECKRETURN_CODE(code);

    //colorTreeByLevel(root.get());

    return writeTree(baseURI, root.get());
}
/*
* Initialize the octree chunk files for writing
*/
TAKErr setChunkFiles(std::vector<std::unique_ptr<FileOutput2>>& chunkFiles, OctTreeNode* root, const char* baseURI, std::string prefix) NOTHROWS {
    TAKErr code;
    std::size_t count = 0;
    for (auto& child : root->children) {
        code = setChunkFiles(chunkFiles, &child, baseURI, prefix + std::to_string(count));
        TE_CHECKRETURN_CODE(code);
        ++count;
    }

    if (root->children.empty()) {
        std::unique_ptr<FileOutput2> file(std::make_unique<FileOutput2>());
        std::string filename = combinePath(baseURI, prefix + chunkExt);
        code = file->open(filename.c_str());
        TE_CHECKRETURN_CODE(code);
        root->chunkFile = file.get();
        chunkFiles.push_back(std::move(file));
    }
    return TE_Ok;
}

/*
* Write all points to approprite chunk files
*/
TAKErr writeChunks(FileInput2& input, OctTreeNode* root, const char* baseURI, std::size_t start, std::size_t numPoints) NOTHROWS {

    TAKErr code;
    std::vector<std::unique_ptr<FileOutput2>> chunkFiles;
    code = setChunkFiles(chunkFiles, root, baseURI, "0");
    TE_CHECKRETURN_CODE(code);
    code = input.seek(getProjectedFileHeaderSize());
    TE_CHECKRETURN_CODE(code);
    PointData pointData;
    std::size_t read;
    while (input.read(reinterpret_cast<uint8_t*>(&pointData), &read, sizeof(PointData)) == TE_Ok) {
        root->writeToChunk(pointData);
    }
    return TE_Ok;
}

TAKErr createChunks(FileInput2& input, OctTreeNode* root, std::size_t count, const Envelope2Ptr& envelope, const char* baseURI) NOTHROWS {
    std::vector<atakmap::util::AtomicCounter> counts(tileWidth * tileWidth * tileWidth);
    TAKErr code;

    code = countingSort(nullptr, counts.data(), input, *envelope);
    TE_CHECKRETURN_CODE(code);
    code = mergeTiles(root, counts.data(), largeThreshold);
    TE_CHECKRETURN_CODE(code);
    input.seek(0);
    code = writeChunks(input, root, baseURI, 0, count);
    TE_CHECKRETURN_CODE(code);
    return TE_Ok;
}

/* 
* perform the main chunk processing algorithm, see comment for LAS_createTiles
*/
TAKErr processChunk(int&, OctTreeNode* chunkRoot, const char* outputPath, std::string inputChunk, std::string prefix) {
    std::vector<atakmap::util::AtomicCounter> counts(tileWidth * tileWidth * tileWidth);
    std::vector<PointData> allPoints;
    allPoints.reserve(largeThreshold);
    TAKErr code;

    FileInput2 input;
    code = input.open(inputChunk.c_str());
    TE_CHECKRETURN_CODE(code);

    code = countingSort(&allPoints, counts.data(), input, chunkRoot->aabb);
    TE_CHECKRETURN_CODE(code);
    std::unique_ptr<OctTreeNode> root(new OctTreeNode());
    root->setAABB(chunkRoot->aabb);
    code = mergeTiles(root.get(), counts.data(), smallThreshold);
    TE_CHECKRETURN_CODE(code);

    code = insertPoints(allPoints, root.get());
    TE_CHECKRETURN_CODE(code);

    *chunkRoot = std::move(*root);

    code = subsamplePoints(chunkRoot, smallThreshold);
    TE_CHECKRETURN_CODE(code);

    //colorTreeByLevel(root.get());

    code = writeNode(outputPath, chunkRoot, prefix);
    TE_CHECKRETURN_CODE(code);

    for (auto& child : chunkRoot->children) {
        child.clearAllPoints();
    }

    return TE_Ok;
}

TAKErr processChunks(std::vector<Future<int>>& futures, OctTreeNode* root, const char* outputPath, std::string prefix, LAS_Method method) NOTHROWS {
    TAKErr code;
    for (int i = 0; i < root->children.size(); ++i) {
        code = processChunks(futures, &root->children[i], outputPath, prefix + std::to_string(i), method);
        TE_CHECKRETURN_CODE(code);
    }

    if (root->children.empty()) {
        std::string filename = combinePath(outputPath, prefix + chunkExt);
        if (method == LAS_Method::CHUNK_PARALLEL) {
            futures.push_back(TAK::Engine::Util::Task_begin(GeneralWorkers_flex(), processChunk, root, outputPath, filename, prefix));
        }
        else {
            int temp;
            processChunk(temp, root, outputPath, filename, prefix);
        }
    }
    return TE_Ok;
}

TAKErr createTiles(const char* outputPath, std::size_t maxPoints, LAS_Method method) NOTHROWS {
    TAKErr code;
    DatabasePtr database(nullptr, nullptr);
    std::string dbFilePath = combinePath(outputPath, dbFileName);
    code = DatabaseFactory_create(database, DatabaseInformation(dbFilePath.c_str()));
    TE_CHECKRETURN_CODE(code)

    std::string jsonURI = combinePath(outputPath, "tileset.json");
    std::string pntsURI = combinePath(outputPath, "pnts.pnts");

    Envelope2Ptr computedEnvelope(new Envelope2(), Memory_deleter_const<Envelope2>);

    if (method == LAS_Method::SQLITE) {
        QueryPtr lasPointQuery(nullptr, nullptr);
        code = queryPoints(lasPointQuery, database, maxPoints, computedEnvelope);
        TE_CHECKRETURN_CODE(code);
        code = processChunkSqlite(lasPointQuery, maxPoints, computedEnvelope, outputPath);
        TE_CHECKRETURN_CODE(code);
    }
    else {
        FileInput2 input;
        code = input.open(dbFilePath.c_str());
        TE_CHECKRETURN_CODE(code);
        std::size_t read;
        code = input.read(reinterpret_cast<uint8_t*>(computedEnvelope.get()), &read, (std::size_t)sizeof(Envelope2));
        TE_CHECKRETURN_CODE(code);
        std::unique_ptr<OctTreeNode> root(new OctTreeNode());
        root->setAABB(*computedEnvelope);

        code = createChunks(input, root.get(), maxPoints, computedEnvelope, outputPath);
        TE_CHECKRETURN_CODE(code);
        std::vector<Future<int>> futures;
        code = processChunks(futures, root.get(), outputPath, "0", method);
        for (auto& future : futures) {
            int res;
            future.await(res, code);
            TE_CHECKRETURN_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        code = writeTree(outputPath, root.get());
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}


TAKErr createTilesImpl(const char* lasFilePath, const char* outputPath, std::size_t maxPoints, LAS_Method method) NOTHROWS {
    auto code = createDatabase(lasFilePath, outputPath, maxPoints, method);
    TE_CHECKRETURN_CODE(code);
    code = createTiles(outputPath, maxPoints, method);
    TE_CHECKRETURN_CODE(code);
    return TE_Ok;
}

TAKErr cleanupDirectory(const char* outputPath) {
    return TAK::Engine::Util::IO_visitFiles([](void*, const char* path)->TAKErr
        {
            TAK::Engine::Port::String ext;
            auto code = IO_getExt(ext, path);
            TE_CHECKRETURN_CODE(code);
            if (ext == chunkExt || ext == ".sqlite")
                TE_CHECKRETURN_CODE(TAK::Engine::Util::IO_delete(path));

            return code;
        }, nullptr, outputPath, TELFM_ImmediateFiles);
}

/*  
*   Basic algorithm:
*       -reproject all points into sqlite or temporary file (depending on LAS_Method)
*       -for LAS_Method CHUNK:
*          -Count all points into an even grid (countingSort)
*          -Merge the above even grid into an octree such that each node has less than `threshold` number of points (mergeTiles)
*          -Redistrubute each point into a .chunk file based on the above octree
*       -repeat the above steps for each chunk, use countingSort, mergeTiles and insertPoints to create an octree in-core
*       -subsample each node so that interior nodes have a random subsample of their children
*       -write each node to a pnts file
*       -write the tileset.json file
* 
*   See also:
*   Fast Out-of-Core Octree Generation for Massive Point Clouds
*   https://www.cg.tuwien.ac.at/research/publications/2020/SCHUETZ-2020-MPC/SCHUETZ-2020-MPC-paper.pdf
*/

ENGINE_API TAKErr TAK::Engine::Formats::LAS::LAS_createTiles(const char* lasFilePath, const char* outputPath, std::size_t maxPoints, LAS_Method method) NOTHROWS {
    TAKErr code = createTilesImpl(lasFilePath, outputPath, maxPoints, method);
    TE_CHECKRETURN_CODE(cleanupDirectory(outputPath));
    return code;
}
