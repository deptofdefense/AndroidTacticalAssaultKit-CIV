#include "feature/SpatialCalculator.h"

#include <cstdio>
#include <cinttypes>

#ifdef MSVC
#include "vscompat.h"
#endif

#include "feature/FeatureDatabase.h"
#include "feature/FeatureDataSource.h"
#include "feature/Geometry.h"
#include "feature/GeometryCollection.h"
#include "feature/Linestring.h"
#include "feature/ParseGeometry.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/QuadBlob.h"
#include "db/Database.h"
#include "db/SpatiaLiteDB.h"
#include "db/Statement.h"
#include "util/Memory.h"

using namespace atakmap::feature;

using namespace TAK::Engine::Util;

using namespace atakmap::db;
using namespace atakmap::util;


namespace {
    class AutoDeleteHandle
    {
    public :
        AutoDeleteHandle(SpatialCalculator *calc, int64_t handle);
        ~AutoDeleteHandle();
    private :
        SpatialCalculator *calc;
        int64_t handle;
    };

    GeometryCollection *flatten(const GeometryCollection *c);
}


SpatialCalculator::SpatialCalculator(const char * path) :
    database(nullptr),
    insertGeomWkt(nullptr),
    insertGeomBlob(nullptr),
    bufferInsert(nullptr),
    bufferUpdate(nullptr),
    intersectionInsert(nullptr),
    intersectionUpdate(nullptr),
    unionInsert(nullptr),
    unionUpdate(nullptr),
    unaryUnionInsert(nullptr),
    unaryUnionUpdate(nullptr),
    differenceInsert(nullptr),
    differenceUpdate(nullptr),
    simplifyInsert(nullptr),
    simplifyUpdate(nullptr),
    simplifyPreserveTopologyInsert(nullptr),
    simplifyPreserveTopologyUpdate(nullptr),
    clearMem(nullptr),
    deleteGeom(nullptr),
    updateGeomBlob(nullptr),
    updateGeomWkt(nullptr)
{
    if (!path)
        path = ":memory:";

    this->database.reset(new SpatiaLiteDB(path));

    std::unique_ptr<Cursor> result(nullptr);

    std::pair<int, int> spatialiteVersion = getSpatialiteVersion(*this->database.get());

    const int major = spatialiteVersion.first;
    const int minor = spatialiteVersion.second;

    const char *initSpatialMetadataSql;
    if (major > 4 || (major == 4 && minor >= 2))
        initSpatialMetadataSql = "SELECT InitSpatialMetadata(1, \'WGS84\')";
    else if (major > 4 || (major == 4 && minor >= 1))
        initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
    else
        initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

    result.reset(this->database->query(initSpatialMetadataSql));
    result->moveToNext();
    result.reset(nullptr);

    this->database->execute(
        "CREATE TABLE Calculator (id INTEGER PRIMARY KEY AUTOINCREMENT)");

    result.reset(this->database->query(
            "SELECT AddGeometryColumn(\'Calculator\', \'geom\', 4326, \'GEOMETRY\', \'XY\')"));
    result->moveToNext();
    result.reset(nullptr);
}

SpatialCalculator::~SpatialCalculator()
{
    if (this->database.get()) {
        try {
            this->clearCache();
        }
        catch (std::exception &) {}

        this->database.reset(nullptr);
    }
}


void SpatialCalculator::clearCache()
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


void SpatialCalculator::clear()
{
    if (!this->clearMem.get())
        this->clearMem.reset(this->database->compileStatement("DELETE FROM Calculator"));
    this->clearMem->execute();
}

void SpatialCalculator::beginBatch()
{
    this->database->beginTransaction();
}

void SpatialCalculator::endBatch(bool commit)
{
    if (commit)
        this->database->setTransactionSuccessful();
    this->database->endTransaction();
}

int64_t SpatialCalculator::createGeometry(const Geometry *polygon)
{
    std::unique_ptr<GeometryCollection> flattened(nullptr);
    if (polygon->getType() == Geometry::COLLECTION) {
        flattened.reset(flatten(static_cast<const GeometryCollection *>(polygon)));
        if (flattened.get())
            polygon = flattened.get();
    }
    std::ostringstream strm;
    polygon->toBlob(strm);
    flattened.reset();

    std::string blobString(strm.str());
    const auto* blob = reinterpret_cast<const unsigned char*> (blobString.data());
    const size_t len = blobString.size();

    return this->createGeometry(Blob(blob, blob+len));
}

int64_t SpatialCalculator::createGeometryFromBlob(const Blob &blob)
{
    return this->createGeometry(blob);
}

int64_t SpatialCalculator::createGeometryFromWkb(const Blob &wkb)
{
    std::unique_ptr<Geometry> geom(atakmap::feature::parseWKB(wkb));
    return this->createGeometry(geom.get());
}

int64_t SpatialCalculator::createGeometryFromWkt(const char *wkt)
{
    std::unique_ptr<Geometry> geom(atakmap::feature::parseWKT(wkt));
    return this->createGeometry(geom.get());
}

int64_t SpatialCalculator::createPolygon(Point *a, Point *b, Point *c, Point *d)
{
    uint8_t *blob = this->quad.getBlob(a, b, c, d);
    return createGeometry(Blob(blob, blob+QuadBlob::getQuadBlobSize()));
}

void SpatialCalculator::deleteGeometry(int64_t handle)
{
    if (!this->deleteGeom.get())
        this->deleteGeom.reset(this->database->compileStatement("DELETE FROM Calculator WHERE id = ?"));
    this->deleteGeom->clearBindings();
    this->deleteGeom->bind(1, static_cast<int64_t>(handle));
    this->deleteGeom->execute();
}

Geometry::Type SpatialCalculator::getGeometryType(int64_t handle)
{
    const size_t arglen = snprintf(nullptr, 0, "%" PRId64, handle);
    array_ptr<char> arg(new char[arglen+1]);
    snprintf(arg.get(), arglen+1, "%" PRId64, handle);

    std::vector<const char *> args;
    args.push_back(arg.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT GeometryType((SELECT geom FROM Calculator WHERE id = ?))", args));
    if (!result->moveToNext())
        throw std::invalid_argument("no such handle");
    switch (result->getInt(0))
    {
    case 0x01 : // GEOM_TYPE_POINT
        return Geometry::POINT;
    case 0x02 : // GEOM_TYPE_LINESTRING 
        return Geometry::LINESTRING;
    case 0x03 : // GEOM_TYPE_POLYGON 
        return Geometry::POLYGON;
    case 0x04 : // GEOM_TYPE_MULTIPOINT 
    case 0x05 : // GEOM_TYPE_MULTILINESTRING 
    case 0x06 : // GEOM_TYPE_MULTIPOLYGON 
    case 0x07 : // GEOM_TYPE_GEOMETRYCOLLECTION 
        return Geometry::COLLECTION;
    default :
        return (Geometry::Type)-1;
    }
}

SpatialCalculator::Blob SpatialCalculator::getGeometryAsBlob(int64_t handle)
{
    const size_t arglen = snprintf(nullptr, 0, "%" PRId64, handle);
    array_ptr<char> arg(new char[arglen+1]);
    snprintf(arg.get(), arglen+1, "%" PRId64, handle);

    std::vector<const char *> args;
    args.push_back(arg.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT geom FROM Calculator WHERE id = ?", args));
    if (!result->moveToNext())
        return Blob(NULL, NULL);
    Cursor::Blob blob = result->getBlob(0);
    const size_t len = blob.second - blob.first;

    array_ptr<uint8_t> retval(new uint8_t[len]);
    memcpy(retval.get(), blob.first, len);
    const uint8_t *end = retval.get() + len;
    return Blob(retval.release(), end);
}

const char *SpatialCalculator::getGeometryAsWkt(int64_t handle)
{
    const size_t arglen = snprintf(nullptr, 0, "%" PRId64, handle);
    array_ptr<char> arg(new char[arglen+1]);
    snprintf(arg.get(), arglen+1, "%" PRId64, handle);

    std::vector<const char *> args;
    args.push_back(arg.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT AsText(geom) FROM Calculator WHERE id = ?", args));
    if (!result->moveToNext())
        return nullptr;
    const char *wkt = result->getString(0);
    if (!wkt)
        return nullptr;

    const size_t len = snprintf(nullptr, 0, "%s", wkt) + 1;
    array_ptr<char> retval(new char[len]);
    snprintf(retval.get(), len, "%s", wkt);

    return retval.release();
}

Geometry *SpatialCalculator::getGeometry(int64_t handle)
{
    const size_t arglen = snprintf(nullptr, 0, "%" PRId64, handle);
    array_ptr<char> arg(new char[arglen+1]);
    snprintf(arg.get(), arglen+1, "%" PRId64, handle);

    std::vector<const char *> args;
    args.push_back(arg.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT geom FROM Calculator WHERE id = ?", args));
    if (!result->moveToNext())
        return nullptr;

    return atakmap::feature::parseBlob(result->getBlob(0));
}

bool SpatialCalculator::intersects(int64_t geom1, int64_t geom2)
{
    size_t arglen;
    
    arglen = snprintf(nullptr, 0, "%" PRId64, geom1);
    array_ptr<char> arg1(new char[arglen+1]);
    snprintf(arg1.get(), arglen+1, "%" PRId64, geom1);

    arglen = snprintf(nullptr, 0, "%" PRId64, geom2);
    array_ptr<char> arg2(new char[arglen+1]);
    snprintf(arg2.get(), arglen+1, "%" PRId64, geom2);

    std::vector<const char *> args;
    args.push_back(arg1.get());
    args.push_back(arg2.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT Intersects((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))", args));
    if (!result->moveToNext())
        return false;
    return (result->getInt(0) == 1);
}


bool SpatialCalculator::contains(int64_t geom1, int64_t geom2)
{
    size_t arglen;

    arglen = snprintf(nullptr, 0, "%" PRId64, geom1);
    array_ptr<char> arg1(new char[arglen+1]);
    snprintf(arg1.get(), arglen+1, "%" PRId64, geom1);

    arglen = snprintf(nullptr, 0, "%" PRId64, geom2);
    array_ptr<char> arg2(new char[arglen+1]);
    snprintf(arg2.get(), arglen+1, "%" PRId64, geom2);

    std::vector<const char *> args;
    args.push_back(arg1.get());
    args.push_back(arg2.get());

    std::unique_ptr<Cursor> result(this->database->query("SELECT Contains((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))", args));
    if (!result->moveToNext())
        return false;
    return (result->getInt(0) == 1);
}

int64_t SpatialCalculator::intersection(int64_t geom1, int64_t geom2)
{
    size_t arglen;

    arglen = snprintf(nullptr, 0, "%" PRId64, geom1);
    array_ptr<char> arg1(new char[arglen+1]);
    snprintf(arg1.get(), arglen+1, "%" PRId64, geom1);

    arglen = snprintf(nullptr, 0, "%" PRId64, geom2);
    array_ptr<char> arg2(new char[arglen+1]);
    snprintf(arg2.get(), arglen+1, "%" PRId64, geom2);

    std::vector<const char *> args;
    args.push_back(arg1.get());
    args.push_back(arg2.get());

    if (!this->intersectionInsert.get())
        this->intersectionInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))"));

    this->intersectionInsert->clearBindings();
    this->intersectionInsert->bind(1, static_cast<int64_t>(geom1));
    this->intersectionInsert->bind(2, static_cast<int64_t>(geom2));

    this->intersectionInsert->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::intersection(int64_t geom1, int64_t geom2, int64_t result)
{
    if (!this->intersectionUpdate.get())
        this->intersectionUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?"));

    this->intersectionUpdate->clearBindings();
    this->intersectionUpdate->bind(1, static_cast<int64_t>(geom1));
    this->intersectionUpdate->bind(2, static_cast<int64_t>(geom2));
    this->intersectionUpdate->bind(3, static_cast<int64_t>(result));

    this->intersectionUpdate->execute();
}

int64_t SpatialCalculator::gunion(int64_t geom1, int64_t geom2)
{
    if (!this->unionInsert.get())
        this->unionInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT GUnion(geom) FROM Calculator WHERE id IN (?, ?)"));

    this->unionInsert->clearBindings();
    this->unionInsert->bind(1, static_cast<int64_t>(geom1));
    this->unionInsert->bind(2, static_cast<int64_t>(geom2));

    this->unionInsert->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::gunion(int64_t geom1, int64_t geom2, int64_t result)
{
    if (!this->unionUpdate.get())
        this->unionUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = GUnion((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?"));

    this->unionUpdate->clearBindings();
    this->unionUpdate->bind(1, static_cast<int64_t>(geom1));
    this->unionUpdate->bind(2, static_cast<int64_t>(geom2));
    this->unionUpdate->bind(3, static_cast<int64_t>(result));

    this->unionUpdate->execute();
}

int64_t SpatialCalculator::unaryUnion(int64_t geom)
{
    if (!this->unaryUnionInsert.get())
        this->unaryUnionInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT UnaryUnion(geom) FROM Calculator WHERE id  = ?"));

    this->unaryUnionInsert->clearBindings();
    this->unaryUnionInsert->bind(1, static_cast<int64_t>(geom));
    this->unaryUnionInsert->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::unaryUnion(int64_t geom, int64_t result)
{
    if (!this->unaryUnionUpdate.get())
        this->unaryUnionUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = UnaryUnion((SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?"));
    this->unaryUnionUpdate->clearBindings();
    this->unaryUnionUpdate->bind(1, geom);
    this->unaryUnionUpdate->bind(2, result);
    this->unaryUnionUpdate->execute();
}

int64_t SpatialCalculator::difference(int64_t geom1, int64_t geom2)
{
    if (!this->differenceInsert.get())
        this->differenceInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))"));

    this->differenceInsert->clearBindings();
    this->differenceInsert->bind(1, static_cast<int64_t>(geom1));
    this->differenceInsert->bind(2, static_cast<int64_t>(geom2));

    this->differenceInsert->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::difference(int64_t geom1, int64_t geom2, int64_t result)
{
    if (!this->differenceUpdate.get())
        this->differenceUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?"));
    this->differenceUpdate->clearBindings();
    this->differenceUpdate->bind(1, static_cast<int64_t>(geom1));
    this->differenceUpdate->bind(2, static_cast<int64_t>(geom2));
    this->differenceUpdate->bind(3, static_cast<int64_t>(result));

    this->differenceUpdate->execute();
}

int64_t SpatialCalculator::simplify(int64_t handle, double tolerance, bool preserveTopology)
{
    Statement *stmt = nullptr;

    if (preserveTopology) {
        if (!this->simplifyPreserveTopologyInsert.get())
            this->simplifyPreserveTopologyInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?)"));
        stmt = this->simplifyPreserveTopologyInsert.get();
    }
    else {
        if (!this->simplifyInsert.get())
            this->simplifyInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT Simplify((SELECT geom FROM Calculator WHERE id = ?), ?)"));
        stmt = this->simplifyInsert.get();
    }

    stmt->clearBindings();
    stmt->bind(1, static_cast<int64_t>(handle));
    stmt->bind(2, static_cast<double>(tolerance));

    stmt->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::simplify(int64_t handle, double tolerance, bool preserveTopology, int64_t result)
{
    Statement *stmt = nullptr;
    
    if (preserveTopology) {
        if (!this->simplifyPreserveTopologyUpdate.get())
            this->simplifyPreserveTopologyUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?"));
        stmt = this->simplifyPreserveTopologyUpdate.get();
    }
    else {
        if (!this->simplifyUpdate.get())
            this->simplifyUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = Simplify((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?"));
        stmt = this->simplifyUpdate.get();
    }

    stmt->clearBindings();
    stmt->bind(1, static_cast<int64_t>(handle));
    stmt->bind(2, static_cast<double>(tolerance));
    stmt->bind(3, static_cast<int64_t>(result));

    stmt->execute();
}

LineString *SpatialCalculator::simplify(LineString *linestring, double tolerance, bool preserveTopology)
{
    int64_t handle = this->createGeometry(linestring);
    AutoDeleteHandle handleDeleter(this, handle);

    this->simplify(handle, tolerance, preserveTopology, handle);

    Blob blob = this->getGeometryAsBlob(handle);
    if (blob.first) {
        std::unique_ptr<Geometry> simplified(parseBlob(blob));
        if (!simplified.get())
            return nullptr;
        if (simplified.get()->getType() != Geometry::LINESTRING)
            return nullptr;
        return static_cast<LineString *>(simplified.release());
    }
    else {
        return nullptr;
    }
}

int64_t SpatialCalculator::buffer(int64_t handle, double dist)
{
    if (!this->bufferInsert.get())
        this->bufferInsert.reset(this->database->compileStatement("INSERT INTO Calculator (geom) SELECT Buffer((SELECT geom FROM Calculator WHERE id = ?), ?)"));

    this->bufferInsert->clearBindings();
    this->bufferInsert->bind(1, static_cast<int64_t>(handle));
    this->bufferInsert->bind(2, static_cast<double>(dist));

    this->bufferInsert->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::buffer(int64_t handle, double dist, int64_t result)
{
    if (!this->bufferUpdate.get())
        this->bufferUpdate.reset(this->database->compileStatement("UPDATE Calculator SET geom = Buffer((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?"));

    this->bufferUpdate->clearBindings();
    this->bufferUpdate->bind(1, static_cast<int64_t>(handle));
    this->bufferUpdate->bind(2, static_cast<double>(dist));
    this->bufferUpdate->bind(3, static_cast<int64_t>(result));

    this->bufferUpdate->execute();
}

int64_t SpatialCalculator::createGeometry(Blob blob)
{
    if (!this->insertGeomBlob.get())
        this->insertGeomBlob.reset(this->database->compileStatement("INSERT INTO Calculator (geom) VALUES(?)"));

    this->insertGeomBlob->clearBindings();
    this->insertGeomBlob->bind(1, blob);

    this->insertGeomBlob->execute();

    return lastInsertRowID(*this->database);
}

void SpatialCalculator::updateGeometry(int64_t handle, Blob blob)
{
    if (!this->updateGeomBlob.get())
        this->updateGeomBlob.reset(this->database->compileStatement("UPDATE Calculator SET geom = ? WHERE id = ?"));

    this->updateGeomBlob->clearBindings();
    this->updateGeomBlob->bind(1, blob);
    this->updateGeomBlob->bind(2, static_cast<int64_t>(handle));

    this->updateGeomBlob->execute();
}

void SpatialCalculator::updateGeometry(int64_t handle, const Geometry *geom)
{
    std::ostringstream strm;
    geom->toBlob(strm);

    std::string blobString(strm.str());
    const auto* blob = reinterpret_cast<const unsigned char*> (blobString.data());
    const size_t len = blobString.size();

    this->updateGeometry(handle, Blob(blob, blob + len));
}

void SpatialCalculator::destroyBlob(SpatialCalculator::Blob blob)
{
    if (blob.first)
        delete [] blob.first;
}

void SpatialCalculator::destroyGeometry(const Geometry *geom)
{
    delete geom;
}

void SpatialCalculator::destroyWkt(const char *wkt)
{
    delete [] wkt;
}

SpatialCalculator::Batch::Batch(SpatialCalculator *calc_) :
    calc(calc_),
    success(false)
{
    calc->beginBatch();
}

SpatialCalculator::Batch::~Batch()
{
    calc->endBatch(this->success);
}

void SpatialCalculator::Batch::setSuccessful()
{
    success = true;
}

namespace {
    
AutoDeleteHandle::AutoDeleteHandle(SpatialCalculator *calc_, int64_t handle_) :
    calc(calc_),
    handle(handle_)
{}

AutoDeleteHandle::~AutoDeleteHandle()
{
    if (handle) {
        calc->deleteGeometry(handle);
        handle = 0;
    }
}

GeometryCollection *flatten(const GeometryCollection *c)
{
    typedef std::pair<GeometryCollection::GeometryVector::const_iterator, GeometryCollection::GeometryVector::const_iterator> GeomElems;
    typedef GeometryCollection::GeometryVector::const_iterator GeomIterator;

    bool isFlat = true;
    GeomElems elems = c->contents();
    for (auto it = elems.first; it != elems.second; it++) {
            isFlat &= ((*it)->getType() != Geometry::COLLECTION);
    }
    if (isFlat)
        return nullptr;
    std::unique_ptr<GeometryCollection> retval(new GeometryCollection(c->getDimension()));
    GeometryCollection *child;
    std::unique_ptr<GeometryCollection> flattened(nullptr);
    GeomElems childElems;
    for (auto it = elems.first; it != elems.second; it++) {
        if ((*it)->getType() == Geometry::COLLECTION) {
            child = static_cast<GeometryCollection *>(*it);
            flattened.reset(flatten(child));
            if (flattened.get())
                child = flattened.get();
            childElems = child->contents();
            for (auto cit = childElems.first; cit != childElems.second; cit++)
                retval->add(*cit);
            flattened.reset(nullptr);
        }
        else {
            retval->add(*it);
        }
    }
    return retval.release();
}

}
