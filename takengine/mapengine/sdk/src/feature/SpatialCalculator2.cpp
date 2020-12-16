#include "feature/SpatialCalculator2.h"

#include <cstdio>
#include <cinttypes>

#ifdef MSVC
#include "vscompat.h"
#endif

#include "feature/FeatureDatabase.h"
#include "feature/Geometry2.h"
#include "feature/GeometryCollection2.h"
#include "feature/GeometryFactory.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "feature/QuadBlob2.h"
#include "db/Database2.h"
#include "db/Query.h"
#include "db/Statement2.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace atakmap::db;


namespace {
    class AutoDeleteHandle
    {
    public :
        AutoDeleteHandle(SpatialCalculator2 &calc, const int64_t &handle);
        ~AutoDeleteHandle();
    private :
        SpatialCalculator2 &calc;
        int64_t handle;
    };

    typedef std::map<std::string, GeometryClass> GeometryClassMap;
    const GeometryClassMap geometryClassIndex = {
        { "POINT", TEGC_Point },
        { "LINESTRING", TEGC_LineString },
        { "POLYGON", TEGC_Polygon },
        { "MULTIPOINT", TEGC_GeometryCollection },
        { "MULTILINESTRING", TEGC_GeometryCollection },
        { "MULTIPOLYGON", TEGC_GeometryCollection },
        { "GEOMETRYCOLLECTION", TEGC_GeometryCollection }
    };
}


SpatialCalculator2::SpatialCalculator2(const char * path) :
    database(nullptr, nullptr),
    insertGeomWkb(nullptr, nullptr),
    insertGeomWkt(nullptr, nullptr),
    insertGeomBlob(nullptr, nullptr),
    bufferInsert(nullptr, nullptr),
    bufferUpdate(nullptr, nullptr),
    intersectionInsert(nullptr, nullptr),
    intersectionUpdate(nullptr, nullptr),
    unionInsert(nullptr, nullptr),
    unionUpdate(nullptr, nullptr),
    unaryUnionInsert(nullptr, nullptr),
    unaryUnionUpdate(nullptr, nullptr),
    differenceInsert(nullptr, nullptr),
    differenceUpdate(nullptr, nullptr),
    simplifyInsert(nullptr, nullptr),
    simplifyUpdate(nullptr, nullptr),
    simplifyPreserveTopologyInsert(nullptr, nullptr),
    simplifyPreserveTopologyUpdate(nullptr, nullptr),
    clearMem(nullptr, nullptr),
    deleteGeom(nullptr, nullptr),
    updateGeomBlob(nullptr, nullptr),
    updateGeomWkb(nullptr, nullptr),
    updateGeomWkt(nullptr, nullptr)
{
    TAKErr code(TE_Ok);

    if (!path)
        path = ":memory:";

    code = Databases_openDatabase(this->database, path);

    if (TE_Ok == code) {

        std::pair<int, int> spatialiteVersion = atakmap::feature::getSpatialiteVersion(*this->database);

        const int major = spatialiteVersion.first;
        const int minor = spatialiteVersion.second;

        const char *initSpatialMetadataSql;
        if (major > 4 || (major == 4 && minor >= 2))
            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1, \'WGS84\')";
        else if (major > 4 || (major == 4 && minor >= 1))
            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
        else
            initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

        QueryPtr cursor(nullptr, nullptr);

        code = this->database->query(cursor, initSpatialMetadataSql);
        code = cursor->moveToNext();
        cursor.reset();

        code = this->database->execute(
            "CREATE TABLE Calculator (id INTEGER PRIMARY KEY AUTOINCREMENT)", nullptr, 0);

        code = this->database->query(cursor,
                              "SELECT AddGeometryColumn(\'Calculator\', \'geom\', 4326, \'GEOMETRY\', \'XY\')");
        code = cursor->moveToNext();
        cursor.reset();
    }
}

SpatialCalculator2::~SpatialCalculator2()
{
    if (this->database.get()) {
        try {
            this->clearCache();
        }
        catch (std::exception &) {}

        this->database.reset();
    }
}


void SpatialCalculator2::clearCache()
{
#define CLOSE_STMT(stmt) \
    if(this->stmt.get()) { \
        this->stmt.reset(); \
    }

    CLOSE_STMT(insertGeomWkt);
    CLOSE_STMT(insertGeomBlob);
    CLOSE_STMT(bufferInsert);
    CLOSE_STMT(bufferUpdate);
    CLOSE_STMT(intersectionInsert);
    CLOSE_STMT(intersectionUpdate);
    CLOSE_STMT(unionInsert);
    CLOSE_STMT(unionUpdate);
    CLOSE_STMT(unaryUnionInsert);
    CLOSE_STMT(unaryUnionUpdate);
    CLOSE_STMT(differenceInsert);
    CLOSE_STMT(differenceUpdate);
    CLOSE_STMT(simplifyInsert);
    CLOSE_STMT(simplifyUpdate);
    CLOSE_STMT(simplifyPreserveTopologyInsert);
    CLOSE_STMT(simplifyPreserveTopologyUpdate);
    CLOSE_STMT(clearMem);
    CLOSE_STMT(deleteGeom);
    CLOSE_STMT(updateGeomBlob);
    CLOSE_STMT(updateGeomWkt);
#undef CLOSE_STMT
}


void SpatialCalculator2::clear()
{
    if (!this->clearMem)
        this->database->compileStatement(this->clearMem, "DELETE FROM Calculator");
    this->clearMem->execute();
}

void SpatialCalculator2::beginBatch()
{
    this->database->beginTransaction();
}

void SpatialCalculator2::endBatch(bool commit)
{
    if (commit)
        this->database->setTransactionSuccessful();
    this->database->endTransaction();
}

TAKErr SpatialCalculator2::createGeometry(int64_t *handle, const Geometry2 &geom)
{
    Util::TAKErr code(TE_Ok);

    Geometry2Ptr flattened(nullptr, nullptr);
    if (geom.getClass() == TEGC_GeometryCollection) {
        code = GeometryCollection_flatten(flattened, *(static_cast<const GeometryCollection2 *>(&geom)));
        TE_CHECKRETURN_CODE(code);
    }

    const Geometry2 &resolved = flattened ? *flattened : geom;

    Util::DynamicOutput sink;
    sink.open(128); // arbitrary size
    code = GeometryFactory_toSpatiaLiteBlob(sink, resolved, 4326);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *blob(nullptr);
    std::size_t len(NULL);
    code = sink.get(&blob, &len);
    TE_CHECKRETURN_CODE(code);

    code = this->createGeometry(handle, blob, len);

    return code;
}

TAKErr SpatialCalculator2::createGeometryFromBlob(int64_t *handle, const uint8_t *blob, const std::size_t &len)
{
    return this->createGeometry(handle, blob, len);
}

TAKErr SpatialCalculator2::createGeometryFromWkb(int64_t *handle, const uint8_t *wkb, const std::size_t &len)
{
    TAKErr code(TE_Ok);

    if (!this->insertGeomWkb.get()) {
        code = this->database->compileStatement(this->insertGeomWkb, "INSERT INTO Calculator (geom) VALUES(GeomFromWKB(?, 4326))");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertGeomWkb->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->insertGeomWkb->bindBlob(1, wkb, len);
    TE_CHECKRETURN_CODE(code);

    code = this->insertGeomWkb->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(handle, *this->database);

    return code;
}

TAKErr SpatialCalculator2::createGeometryFromWkt(int64_t *handle, const char *wkt)
{
    TAKErr code(TE_Ok);

    if (!this->insertGeomWkt.get()) {
        code = this->database->compileStatement(this->insertGeomWkt, "INSERT INTO Calculator (geom) VALUES(GeomFromText(?, 4326))");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertGeomWkt->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->insertGeomWkt->bindString(1, wkt);
    TE_CHECKRETURN_CODE(code);

    code = this->insertGeomWkt->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(handle, *this->database);

    return code;
}

TAKErr SpatialCalculator2::createPolygon(int64_t *handle, const Point2 &a, const Point2 &b, const Point2 &c, const Point2 &d)
{
    TAKErr code(TE_Ok);

    uint8_t blob[TE_QUADBLOB_SIZE];
    code = QuadBlob2_get(blob, TE_QUADBLOB_SIZE, TE_PlatformEndian, a, b, c, d);
    TE_CHECKRETURN_CODE(code);

    code = createGeometry(handle, blob, TE_QUADBLOB_SIZE);

    return code;
}

TAKErr SpatialCalculator2::deleteGeometry(const int64_t &handle)
{
    TAKErr code(TE_Ok);

    if (!this->deleteGeom.get()) {
        code = this->database->compileStatement(this->deleteGeom, "DELETE FROM Calculator WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->deleteGeom->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->deleteGeom->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);

    code = this->deleteGeom->execute();

    return code;
}

TAKErr SpatialCalculator2::getGeometryType(GeometryClass *geom_type, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT GeometryType((SELECT geom FROM Calculator WHERE id = ?))");
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const char *result(nullptr);
    code = query->getString(&result, 0);
    TE_CHECKRETURN_CODE(code);

    if (!result)
        return Util::TE_BadIndex;

    const ::GeometryClassMap::const_iterator itr = geometryClassIndex.find(result);
    if (geometryClassIndex.end() == itr)
        return Util::TE_BadIndex;

    *geom_type = itr->second;

    return code;
}

TAKErr SpatialCalculator2::getGeometryAsBlob(BlobPtr &blob, std::size_t *len, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT geom FROM Calculator WHERE id = ?");
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const uint8_t *buf(nullptr);
    code = query->getBlob(&buf, len, 0);
    TE_CHECKRETURN_CODE(code);

    if (!buf) {
        blob.reset();
    } else {
        // memory is not ours; copy
        blob = BlobPtr(new uint8_t[*len], Memory_array_deleter_const<uint8_t>);
        memcpy(const_cast<uint8_t*>(blob.get()), buf, *len);
    }

    return code;
}

TAKErr SpatialCalculator2::getGeometryAsWkt(TAK::Engine::Port::String *wkt, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT AsText(geom) FROM Calculator WHERE id = ?");
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const char *result(nullptr);
    code = query->getString(&result, 0);
    TE_CHECKRETURN_CODE(code);

    *wkt = result;

    return code;
}

TAKErr SpatialCalculator2::getGeometry(Geometry2Ptr &geom, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    BlobPtr blob(nullptr, nullptr);
    std::size_t len(0);

    code = this->getGeometryAsBlob(blob, &len, handle);
    TE_CHECKRETURN_CODE(code);

    // check for NULL geometry
    if (!blob.get()) {
        geom.reset();
        return code;
    }

    MemoryInput2 src;
    code = src.open(blob.get(), len);
    TE_CHECKRETURN_CODE(code);

    code = GeometryFactory_fromSpatiaLiteBlob(geom, src);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr SpatialCalculator2::intersects(bool *intersected, const int64_t &geom1, const int64_t &geom2)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT Intersects((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    int32_t result(NULL);
    code = query->getInt(&result, 0);
    TE_CHECKRETURN_CODE(code);

    *intersected = 1 == result;

    return code;
}


TAKErr SpatialCalculator2::contains(bool *contained, const int64_t &geom1, const int64_t &geom2)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT Contains((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);

    code = query->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    int32_t result(NULL);
    code = query->getInt(&result, 0);
    TE_CHECKRETURN_CODE(code);

    *contained = 1 == result;

    return code;
}

TAKErr SpatialCalculator2::createIntersection(int64_t *handle, const int64_t& geom1, const int64_t& geom2)
{
    TAKErr code(TE_Ok);

    if (!this->intersectionInsert.get()) {
        code = this->database->compileStatement(this->intersectionInsert, "INSERT INTO Calculator (geom) SELECT Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->intersectionInsert->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionInsert->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionInsert->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionInsert->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(handle, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateIntersection(const int64_t &geom1, const int64_t &geom2, const int64_t &result)
{
    TAKErr code(TE_Ok);

    if (!this->intersectionUpdate.get()) {
        code = this->database->compileStatement(this->intersectionUpdate, "UPDATE Calculator SET geom = Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->intersectionUpdate->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionUpdate->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionUpdate->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionUpdate->bindLong(3, result);
    TE_CHECKRETURN_CODE(code);

    code = this->intersectionUpdate->execute();

    return code;
}

TAKErr SpatialCalculator2::createUnion(int64_t *result, const int64_t &geom1, const int64_t &geom2)
{
    TAKErr code(TE_Ok);

    if (!this->unionInsert.get()) {
        code = this->database->compileStatement(this->unionInsert, "INSERT INTO Calculator (geom) SELECT GUnion(geom) FROM Calculator WHERE id IN (?, ?)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->unionInsert->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->unionInsert->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);
    code = this->unionInsert->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = this->unionInsert->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(result, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateUnion(const int64_t &geom1, const int64_t &geom2, const int64_t &result)
{
    TAKErr code(TE_Ok);
    if (!this->unionUpdate.get()) {
        code = this->database->compileStatement(this->unionUpdate, "UPDATE Calculator SET geom = GUnion((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->unionUpdate->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->unionUpdate->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);
    code = this->unionUpdate->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);
    code = this->unionUpdate->bindLong(3, result);
    TE_CHECKRETURN_CODE(code);

    code = this->unionUpdate->execute();

    return code;
}

TAKErr SpatialCalculator2::createUnaryUnion(int64_t *result, const int64_t &geom)
{
    TAKErr code(TE_Ok);
    if (!this->unaryUnionInsert.get()) {
        code = this->database->compileStatement(this->unaryUnionInsert, "INSERT INTO Calculator (geom) SELECT UnaryUnion(geom) FROM Calculator WHERE id  = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->unaryUnionInsert->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->unaryUnionInsert->bindLong(1, geom);
    TE_CHECKRETURN_CODE(code);
    code = this->unaryUnionInsert->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(result, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateUnaryUnion(const int64_t &geom, const int64_t &result)
{
    TAKErr code(TE_Ok);
    if (!this->unaryUnionUpdate.get()) {
        code = this->database->compileStatement(this->unaryUnionUpdate, "UPDATE Calculator SET geom = UnaryUnion((SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->unaryUnionUpdate->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->unaryUnionUpdate->bindLong(1, geom);
    TE_CHECKRETURN_CODE(code);
    code = this->unaryUnionUpdate->bindLong(2, result);
    TE_CHECKRETURN_CODE(code);

    code = this->unaryUnionUpdate->execute();

    return code;
}

TAKErr SpatialCalculator2::createDifference(int64_t *result, const int64_t &geom1, const int64_t &geom2)
{
    TAKErr code(TE_Ok);
    if (!this->differenceInsert.get()) {
        code = this->database->compileStatement(this->differenceInsert, "INSERT INTO Calculator (geom) SELECT Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->differenceInsert->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->differenceInsert->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);
    code = this->differenceInsert->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);

    code = this->differenceInsert->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(result, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateDifference(const int64_t &geom1, const int64_t &geom2, const int64_t &result)
{
    TAKErr code(TE_Ok);
    if (!this->differenceUpdate.get()) {
        code = this->database->compileStatement(this->differenceUpdate, "UPDATE Calculator SET geom = Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }
    code = this->differenceUpdate->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->differenceUpdate->bindLong(1, geom1);
    TE_CHECKRETURN_CODE(code);
    code = this->differenceUpdate->bindLong(2, geom2);
    TE_CHECKRETURN_CODE(code);
    code = this->differenceUpdate->bindLong(3, result);
    TE_CHECKRETURN_CODE(code);

    code = this->differenceUpdate->execute();

    return code;
}

TAKErr SpatialCalculator2::createSimplify(int64_t *result, const int64_t &handle, const double &tolerance, const bool &preserveTopology)
{
    TAKErr code(TE_Ok);
    Statement2 *stmt = nullptr;

    if (preserveTopology) {
        if (!this->simplifyPreserveTopologyInsert.get()) {
            code = this->database->compileStatement(this->simplifyPreserveTopologyInsert, "INSERT INTO Calculator (geom) SELECT SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?)");
            TE_CHECKRETURN_CODE(code);
        }
        stmt = this->simplifyPreserveTopologyInsert.get();
    }
    else {
        if (!this->simplifyInsert.get()) {
            code = this->database->compileStatement(this->simplifyInsert, "INSERT INTO Calculator (geom) SELECT Simplify((SELECT geom FROM Calculator WHERE id = ?), ?)");
            TE_CHECKRETURN_CODE(code);
        }
        stmt = this->simplifyInsert.get();
    }

    code = stmt->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindDouble(2, tolerance);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(result, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateSimplify(const int64_t &handle, const double &tolerance, const bool &preserveTopology, const int64_t &result)
{
    TAKErr code(TE_Ok);
    Statement2 *stmt = nullptr;

    if (preserveTopology) {
        if (!this->simplifyPreserveTopologyUpdate.get()) {
            code = this->database->compileStatement(this->simplifyPreserveTopologyUpdate, "UPDATE Calculator SET geom = SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");
            TE_CHECKRETURN_CODE(code);
        }
        stmt = this->simplifyPreserveTopologyUpdate.get();
    }
    else {
        if (this->simplifyUpdate.get()) {
            code = this->database->compileStatement(this->simplifyUpdate, "UPDATE Calculator SET geom = Simplify((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");
            TE_CHECKRETURN_CODE(code);
        }
        stmt = this->simplifyUpdate.get();
    }

    code = stmt->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindDouble(2, tolerance);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(3, result);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();

    return code;
}

TAKErr SpatialCalculator2::createSimplify(LineString2Ptr &result, const LineString2 &linestring, const double &tolerance, const bool &preserveTopology)
{
    TAKErr code(TE_Ok);

    int64_t handle(NULL);

    code = this->createGeometry(&handle, linestring);
    TE_CHECKRETURN_CODE(code);

    AutoDeleteHandle handleDeleter(*this, handle);

    code = this->createSimplify(&handle, handle, tolerance, preserveTopology);
    TE_CHECKRETURN_CODE(code);

    Geometry2Ptr geom(nullptr, nullptr);
    code = getGeometry(geom, handle);
    TE_CHECKRETURN_CODE(code);

    switch(geom->getClass()) {
    case TEGC_LineString:
        result = LineString2Ptr(static_cast<LineString2*>(geom.release()), Memory_deleter_const<LineString2>);
        break;
    case TEGC_Point:
    case TEGC_Polygon:
    case TEGC_GeometryCollection:
    default:
        code = Util::TE_BadIndex;
        break;
    }

    return code;
}

TAKErr SpatialCalculator2::createBuffer(int64_t *result, const int64_t &handle, const double &dist)
{
    TAKErr code(TE_Ok);

    if (!this->bufferInsert.get()) {
        code = this->database->compileStatement(this->bufferInsert, "INSERT INTO Calculator (geom) SELECT Buffer((SELECT geom FROM Calculator WHERE id = ?), ?)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->bufferInsert->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->bufferInsert->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);
    code = this->bufferInsert->bindDouble(2, dist);
    TE_CHECKRETURN_CODE(code);

    code = this->bufferInsert->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(result, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateBuffer(const int64_t &handle, const double &dist, const int64_t &result)
{
    TAKErr code(TE_Ok);

    if (!this->bufferUpdate.get()) {
        code = this->database->compileStatement(this->bufferUpdate, "UPDATE Calculator SET geom = Buffer((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->bufferUpdate->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->bufferUpdate->bindLong(1, handle);
    TE_CHECKRETURN_CODE(code);
    code = this->bufferUpdate->bindDouble(2, dist);
    TE_CHECKRETURN_CODE(code);
    code = this->bufferUpdate->bindLong(3, result);
    TE_CHECKRETURN_CODE(code);

    code = this->bufferUpdate->execute();

    return code;
}


TAKErr SpatialCalculator2::createGeometry(int64_t *handle, const uint8_t *blob, const std::size_t &len)
{
    TAKErr code(TE_Ok);

    if (!this->insertGeomBlob.get()) {
        code = this->database->compileStatement(this->insertGeomBlob, "INSERT INTO Calculator (geom) VALUES(?)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertGeomBlob->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->insertGeomBlob->bindBlob(1, blob, len);
    TE_CHECKRETURN_CODE(code);

    code = this->insertGeomBlob->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(handle, *this->database);

    return code;
}

TAKErr SpatialCalculator2::updateGeometry(const uint8_t *blob, const std::size_t &len, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    if (!this->updateGeomBlob.get()) {
        code = this->database->compileStatement(this->updateGeomBlob, "UPDATE Calculator SET geom = ? WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->updateGeomBlob->clearBindings();
    TE_CHECKRETURN_CODE(code);
    code = this->updateGeomBlob->bindBlob(1, blob, len);
    TE_CHECKRETURN_CODE(code);
    code = this->updateGeomBlob->bindLong(2, handle);
    TE_CHECKRETURN_CODE(code);

    code = this->updateGeomBlob->execute();

    return code;
}

TAKErr SpatialCalculator2::updateGeometry(const Geometry2 &geom, const int64_t &handle)
{
    TAKErr code(TE_Ok);

    BlobPtr blob(nullptr, nullptr);
    std::size_t len(0);
    code = this->getGeometryAsBlob(blob, &len, handle);
    TE_CHECKRETURN_CODE(code);

    code = this->updateGeometry(blob.get(), len, handle);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr SpatialCalculator2::getGEOSVersion(TAK::Engine::Port::String *version)
{
    TAKErr code(TE_Ok);

    QueryPtr query(nullptr, nullptr);

    code = this->database->compileQuery(query, "SELECT geos_version()");
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const char *result(nullptr);
    code = query->getString(&result, 0);
    TE_CHECKRETURN_CODE(code);

    if (!result)
        return Util::TE_Unsupported;

    *version = result;

    return code;
}

SpatialCalculator2::Batch::Batch(SpatialCalculator2 &calc_) :
    calc(calc_),
    success(false)
{
    calc.beginBatch();
}

SpatialCalculator2::Batch::~Batch()
{
    calc.endBatch(this->success);
}

void SpatialCalculator2::Batch::setSuccessful()
{
    success = true;
}

namespace {

AutoDeleteHandle::AutoDeleteHandle(SpatialCalculator2 &calc_, const int64_t &handle_) :
    calc(calc_),
    handle(handle_)
{}

AutoDeleteHandle::~AutoDeleteHandle()
{
    if (handle) {
        calc.deleteGeometry(handle);
        handle = 0;
    }
}

}
