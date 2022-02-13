#include "feature/FeatureSpatialDatabase.h"

#include "db/Bindable.h"
#include "db/Cursor2.h"
#include "db/Statement2.h"
#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"
#include "util/Logging.h"
#include "util/IO.h"
#include "util/NonCopyable.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Currency;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

#define SPATIAL_INDEX_ENABLED 1
#define DATABASE_VERSION 10

namespace
{
    class LocalBindings : Bindable,
                          TAK::Engine::Util::NonCopyable
    {
    public :
        LocalBindings(Bindable &stmt) NOTHROWS;
        ~LocalBindings() NOTHROWS override;
    public :
        TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS override;
        TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS override;
        TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS override;
        TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS override;
        TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS override;
        TAKErr bindNull(const std::size_t idx) NOTHROWS override;
        TAKErr clearBindings() NOTHROWS override;
    private :
        Bindable &stmt;
    };
}

FeatureSpatialDatabase::FeatureSpatialDatabase(CatalogCurrencyRegistry2 &r) NOTHROWS :
    CatalogDatabase2(r),
    insertFeatureBlobStatement(nullptr, nullptr),
    insertFeatureWktStatement(nullptr, nullptr),
    insertFeatureWkbStatement(nullptr, nullptr),
    insertStyleStatement(nullptr, nullptr)
{}

Database2 *FeatureSpatialDatabase::getDatabase() NOTHROWS
{
    return this->database.get();
}

TAKErr FeatureSpatialDatabase::insertGroup(int64_t *rowId, const int64_t catalogId, const char *provider, const char *type, const char *groupName, const int minLod, const int maxLod) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    StatementPtr stmt(nullptr, nullptr);

    code = this->database
        ->compileStatement(stmt, "INSERT INTO groups (file_id,  name, provider, type, visible, visible_version, visible_check, min_lod, max_lod) VALUES(?, ?, ?, ?, 1, 0, 0, ?, ?)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, groupName);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, provider);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(4, type);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(5, minLod);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(6, maxLod);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return Databases_lastInsertRowID(rowId, *this->database);
}

TAKErr FeatureSpatialDatabase::insertStyle(int64_t *rowId, const int64_t catalogId, const char *styleRep) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    int64_t styleId;
    code = Databases_getNextAutoincrementID(&styleId, *this->database, "Style");
    TE_CHECKRETURN_CODE(code);

    if (this->insertStyleStatement.get() == nullptr) {
        code = this->database
            ->compileStatement(this->insertStyleStatement, "INSERT INTO Style (file_id, style_rep) VALUES (?, ?)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertStyleStatement->clearBindings();
    TE_CHECKRETURN_CODE(code);

    LocalBindings bindings(*this->insertStyleStatement);
    code = bindings.bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = bindings.bindString(2, styleRep);
    TE_CHECKRETURN_CODE(code);

    code = this->insertStyleStatement->execute();
    TE_CHECKRETURN_CODE(code);

    *rowId = styleId;
    return code;
}

TAKErr FeatureSpatialDatabase::insertFeature(const int64_t catalogId, const int64_t groupId, const atakmap::feature::FeatureDataSource::FeatureDefinition &feature, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS
{
    TAKErr code;
    switch (feature.getEncoding()) {
    case atakmap::feature::FeatureDataSource::FeatureDefinition::BLOB:
    {
        atakmap::feature::FeatureDataSource::FeatureDefinition::ByteBuffer blob(feature.getGeometryByteBuffer());
        code = this->insertFeatureBlob(catalogId, groupId, feature.getName(), blob.first, (blob.second-blob.first),
            styleId, minLod, maxLod);
        break;
    }
    case atakmap::feature::FeatureDataSource::FeatureDefinition::WKB:
    {
        atakmap::feature::FeatureDataSource::FeatureDefinition::ByteBuffer wkb(feature.getGeometryByteBuffer());
        code = this->insertFeatureWkb(catalogId, groupId, feature.getName(), wkb.first, (wkb.second-wkb.first),
            styleId, minLod, maxLod);
        break;
    }
    case atakmap::feature::FeatureDataSource::FeatureDefinition::WKT:
    {
        code = this->insertFeatureWkt(catalogId, groupId, feature.getName(), static_cast<const char *>(feature.getRawGeometry()),
            styleId, minLod, maxLod);
        break;
    }
    default:
        code = TE_InvalidArg;
    }

    return code;
}

TAKErr FeatureSpatialDatabase::insertFeatureBlob(const int64_t catalogId, const int64_t groupId, const char *name, const uint8_t *blob, const std::size_t blobLen, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (this->insertFeatureBlobStatement.get() == nullptr) {
        code = this->database
            ->compileStatement(this->insertFeatureBlobStatement, "INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertFeatureBlobStatement->clearBindings();
    TE_CHECKRETURN_CODE(code);
    {
        LocalBindings bindings(*this->insertFeatureBlobStatement);

        int idx = 1;

        code = bindings.bindLong(idx++, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindLong(idx++, groupId);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindString(idx++, name);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindBlob(idx++, blob, blobLen);
        TE_CHECKRETURN_CODE(code);
        if (styleId > 0)
            code = bindings.bindLong(idx++, styleId);
        else
            code = bindings.bindNull(idx++);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindInt(idx++, minLod);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindInt(idx++, maxLod);
        TE_CHECKRETURN_CODE(code);

        code = this->insertFeatureBlobStatement->execute();
        TE_CHECKRETURN_CODE(code);
    }
    
    return code;
}

TAKErr FeatureSpatialDatabase::insertFeatureWkt(int64_t catalogId, int64_t groupId, const char *name, const char *wkt, int64_t styleId, int minLod, int maxLod) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (this->insertFeatureWktStatement.get() == nullptr) {
        code = this->database
            ->compileStatement(this->insertFeatureWktStatement, "INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, GeomFromText(?, 4326), ?, ?, ?, 1, 0)");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->insertFeatureWktStatement->clearBindings();
    TE_CHECKRETURN_CODE(code);
    {
        LocalBindings bindings(*this->insertFeatureWktStatement);
        int idx = 1;
        code = bindings.bindLong(idx++, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindLong(idx++, groupId);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindString(idx++, name);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindString(idx++, wkt);
        TE_CHECKRETURN_CODE(code);
        if (styleId > 0)
            code = bindings.bindLong(idx++, styleId);
        else
            code = bindings.bindNull(idx++);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindInt(idx++, minLod);
        TE_CHECKRETURN_CODE(code);
        code = bindings.bindInt(idx++, maxLod);
        TE_CHECKRETURN_CODE(code);

        code = this->insertFeatureWktStatement->execute();
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr FeatureSpatialDatabase::insertFeatureWkb(const int64_t catalogId, const int64_t groupId, const char *name, const uint8_t *wkb, const std::size_t wkbLen, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (this->insertFeatureWkbStatement.get() == nullptr) {
        code = this->database
            ->compileStatement(this->insertFeatureWkbStatement, "INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, GeomFromWkb(?, 4326), ?, ?, ?, 1, 0)");
        TE_CHECKRETURN_CODE(code);
    }


    code = this->insertFeatureWkbStatement->clearBindings();
    TE_CHECKRETURN_CODE(code);
    {
        LocalBindings bindings(*this->insertFeatureWkbStatement);

        int idx = 1;

        code = this->insertFeatureWkbStatement->bindLong(idx++, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = this->insertFeatureWkbStatement->bindLong(idx++, groupId);
        TE_CHECKRETURN_CODE(code);
        code = this->insertFeatureWkbStatement->bindString(idx++, name);
        TE_CHECKRETURN_CODE(code);
        code = this->insertFeatureWkbStatement->bindBlob(idx++, wkb, wkbLen);
        TE_CHECKRETURN_CODE(code);
        if (styleId > 0)
            code = this->insertFeatureWkbStatement->bindLong(idx++, styleId);
        else
            code = this->insertFeatureWkbStatement->bindNull(idx++);
        TE_CHECKRETURN_CODE(code);
        code = this->insertFeatureWkbStatement->bindInt(idx++, minLod);
        TE_CHECKRETURN_CODE(code);
        code = this->insertFeatureWkbStatement->bindInt(idx++, maxLod);
        TE_CHECKRETURN_CODE(code);

        code = this->insertFeatureWkbStatement->execute();
    }

    return code;
}

TAKErr FeatureSpatialDatabase::createIndicesNoSync() NOTHROWS
{
    TAKErr code;

    code = this->database
        ->execute(
        "CREATE INDEX IF NOT EXISTS IdxGeometryLevelOfDetail ON Geometry(min_lod, max_lod)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    //
    // Is IdxGeometryFileId really necessary given the index below on group_id & name?
    //
    code = this->database->execute("CREATE INDEX IF NOT EXISTS IdxGeometryFileId ON Geometry(file_id)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute(
        "CREATE INDEX IF NOT EXISTS IdxGeometryGroupIdName ON Geometry(group_id, name)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("CREATE INDEX IF NOT EXISTS IdxGroupName ON groups(name)", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    QueryPtr result(nullptr, nullptr);
    code = this->database->query(result, "SELECT CreateSpatialIndex(\'Geometry\', \'geom\')");
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    return code;
}

TAKErr FeatureSpatialDatabase::dropIndicesNoSync() NOTHROWS
{
    TAKErr code;

    code = this->database->execute("DROP INDEX IF EXISTS IdxGeometryLevelOfDetail", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP INDEX IF EXISTS IdxGeometryGroupIdName", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    //
    // Is IdxGeometryFileId really necessary?
    //
    code = this->database->execute("DROP INDEX IF EXISTS IdxGeometryFileId", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP INDEX IF EXISTS IdxGroupName", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    Port::STLVectorAdapter<Port::String> tableNames;
    code = Databases_getTableNames(tableNames, *this->database);
    TE_CHECKRETURN_CODE(code);

    bool haveGeomIdx;
    Port::String idxGeometryGeom("idx_Geometry_geom");
    code = tableNames.contains(&haveGeomIdx, idxGeometryGeom);
    TE_CHECKRETURN_CODE(code);
    if (haveGeomIdx) {
        QueryPtr result(nullptr, nullptr);
        code = this->database->query(result, "SELECT DisableSpatialIndex(\'Geometry\', \'geom\')");
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        result.reset();

        code = this->database->execute("DROP TABLE idx_Geometry_geom", nullptr, 0);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr FeatureSpatialDatabase::createTriggersNoSync() NOTHROWS
{
    TAKErr code;
    code = this->database->execute("CREATE TRIGGER IF NOT EXISTS Geometry_visible_update AFTER UPDATE OF visible ON Geometry "
        "BEGIN "
        "UPDATE groups SET visible_check = 1 WHERE id = OLD.group_id; "
        "UPDATE Geometry SET group_visible_version = (SELECT visible_version FROM groups WHERE id = OLD.group_id) WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("CREATE TRIGGER IF NOT EXISTS groups_visible_update AFTER UPDATE OF visible ON groups "
        "BEGIN "
        "UPDATE groups SET visible_version = (OLD.visible_version+1), visible_check = 0 WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FeatureSpatialDatabase::dropTriggersNoSync() NOTHROWS
{
    TAKErr code;

    code = this->database->execute("DROP TRIGGER IF EXISTS Geometry_visible_update", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TRIGGER IF EXISTS groups_visible_update", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FeatureSpatialDatabase::checkDatabaseVersion(bool *value) NOTHROWS
{
    TAKErr code;
    int dbVersion;
    int schemaVersion;

    code = this->database->getVersion(&dbVersion);
    TE_CHECKRETURN_CODE(code);

    schemaVersion = databaseVersion();

    *value = (dbVersion == schemaVersion);
    return code;
}

TAKErr FeatureSpatialDatabase::setDatabaseVersion() NOTHROWS
{
    return this->database->setVersion(databaseVersion());
}

TAKErr FeatureSpatialDatabase::dropTables() NOTHROWS
{
    TAKErr code;

    // it will be much quicker to simply delete the database if we need to
    // drop the tables. attempt to delete it; if this fails, perform a
    // legacy-style table drop
    TAK::Engine::Port::String dbPath(nullptr);
    code = Databases_getDatabaseFilePath(dbPath, *this->database);
    TE_CHECKRETURN_CODE(code);

    if (dbPath) {
        if (pathExists(dbPath)) {
            this->database.reset();
            const bool deleted = deletePath(dbPath);
            code = Databases_openDatabase(this->database, dbPath);
            TE_CHECKRETURN_CODE(code);
            if (deleted)
                return code;
        }
    }

    code = this->dropTablesLegacy();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FeatureSpatialDatabase::dropTablesLegacy() NOTHROWS
{
    TAKErr code;

    code = this->dropIndicesNoSync();
    TE_CHECKRETURN_CODE(code);
    code = this->dropTriggersNoSync();
    TE_CHECKRETURN_CODE(code);

    Port::STLVectorAdapter<Port::String> geometryColumns;
    code = Databases_getColumnNames(geometryColumns, *this->database, "Geometry");
    TE_CHECKRETURN_CODE(code);

    bool haveGeomColumn;
    Port::String geomStr("geom");
    code = geometryColumns.contains(&haveGeomColumn, geomStr);
    TE_CHECKRETURN_CODE(code);
    if (haveGeomColumn) {
        QueryPtr result(nullptr, nullptr);
        code = this->database->query(result,
            "SELECT DiscardGeometryColumn(\'Geometry\', \'geom\')");
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        result.reset();
    }

    code = this->database->execute("DROP TABLE IF EXISTS File", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TABLE IF EXISTS Geometry", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TABLE IF EXISTS Style", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TABLE IF EXISTS groups", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    return CatalogDatabase2::dropTables();
}

TAKErr FeatureSpatialDatabase::buildTables() NOTHROWS
{
    TAKErr code;
    QueryPtr result(nullptr, nullptr);

    int major;
    int minor;

    code = getSpatialiteMajorVersion(&major, *this->database);
    TE_CHECKRETURN_CODE(code);
    code = getSpatialiteMinorVersion(&minor, *this->database);
    TE_CHECKRETURN_CODE(code);

    const char *initSpatialMetadataSql;
    if (major > 4 || (major == 4 && minor >= 1))
        initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
    else
        initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

    result.reset();
    code = this->database->query(result, initSpatialMetadataSql);
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    code = CatalogDatabase2::buildTables();
    TE_CHECKRETURN_CODE(code);

    code = this->database
        ->execute(
        "CREATE TABLE Geometry (id INTEGER PRIMARY KEY AUTOINCREMENT, file_id INTEGER, group_id INTEGER, name TEXT COLLATE NOCASE, style_id INTEGER, min_lod INTEGER, max_lod INTEGER, visible INTEGER, group_visible_version INTEGER)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    result.reset();
    code = this->database->query(result,
        "SELECT AddGeometryColumn(\'Geometry\', \'geom\', 4326, \'GEOMETRY\', \'XY\')");
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    code = this->database
        ->execute(
        "CREATE TABLE Style (id INTEGER PRIMARY KEY AUTOINCREMENT, style_name TEXT, file_id INTEGER, style_rep TEXT)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database
        ->execute(
        "CREATE TABLE groups (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT COLLATE NOCASE, file_id INTEGER, provider TEXT, type TEXT, visible INTEGER, visible_version INTEGER, visible_check INTEGER, min_lod INTEGER, max_lod INTEGER)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->createIndicesNoSync();
    TE_CHECKRETURN_CODE(code);
    code = this->createTriggersNoSync();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FeatureSpatialDatabase::onCatalogEntryRemoved(int64_t catalogId, bool automated) NOTHROWS
{
    TAKErr code;

    if (catalogId > 0) {
        StatementPtr stmt(nullptr, nullptr);

        stmt.reset();
        code = this->database->compileStatement(stmt, "DELETE FROM Style WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();

        stmt.reset();
        code = this->database->compileStatement(stmt, "DELETE FROM Geometry WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();

        stmt.reset();
        code = this->database->compileStatement(stmt, "DELETE FROM groups WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();
    }
    else {
        StatementPtr stmt(nullptr, nullptr);

        std::ostringstream strm;

        stmt.reset();
        strm.str() = std::string();
        strm << "DELETE FROM Geometry WHERE file_id IN (SELECT file_id FROM Geometry LEFT JOIN ";
        strm << TABLE_CATALOG;
        strm << " on Geometry.file_id = ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID;
        strm << " WHERE ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID << " IS NULL)";

        code = this->database ->compileStatement(stmt, strm.str().c_str());
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();
    
        stmt.reset();
        strm.str() = std::string();
        strm << "DELETE FROM Style WHERE file_id IN (SELECT file_id FROM Style LEFT JOIN ";
        strm << TABLE_CATALOG;
        strm << " on Style.file_id = ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID;
        strm << " WHERE ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID << " IS NULL)";

        code = this->database->compileStatement(stmt, strm.str().c_str());
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();

        stmt.reset();
        strm.str() = std::string();
        strm << "DELETE FROM groups WHERE file_id IN (SELECT file_id FROM groups LEFT JOIN ";
        strm << TABLE_CATALOG;
        strm << " on groups.file_id = ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID;
        strm << " WHERE ";
        strm << TABLE_CATALOG;
        strm << ".";
        strm << COLUMN_CATALOG_ID << " IS NULL)";

        code = this->database
            ->compileStatement(stmt, strm.str().c_str());
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        stmt.reset();
    }
    
    return TE_Ok;
}

/**************************************************************************/

int FeatureSpatialDatabase::databaseVersion() NOTHROWS
{
    return (catalogSchemaVersion() | (DATABASE_VERSION << 16));
}

/**************************************************************************/

TAKErr FeatureSpatialDatabase::getSpatialiteMajorVersion(int *value, Database2 &db) NOTHROWS
{
    return getSpatialiteVersion(value, nullptr, db);
}

TAKErr FeatureSpatialDatabase::getSpatialiteMinorVersion(int *value, Database2 &db) NOTHROWS
{
    return getSpatialiteVersion(nullptr, value, db);
}

TAKErr FeatureSpatialDatabase::getSpatialiteVersion(int *major, int *minor, Database2 &db) NOTHROWS
{
    TAKErr code;
    QueryPtr cursor(nullptr, nullptr);
    code = db.query(cursor, "SELECT spatialite_version()");

    code = cursor->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const char *verStr;
    code = cursor->getString(&verStr, 0);
    TE_CHECKRETURN_CODE(code);

    std::istringstream strm(verStr);
    char dot(0);

    int majorVersion(-1);
    int minorVersion(-1);

    if (!(((strm >> majorVersion).get(dot)) >> minorVersion && dot == '.'))
    {
        majorVersion = minorVersion = -1;
        Logger::log(Logger::Error, "Failed to parse SpatiaLite version string %s", verStr);
        code = TE_Err;
    }
    else {
        if(major) *major = majorVersion;
        if(minor) *minor = minorVersion;

    }

    return code;
}

namespace
{
    LocalBindings::LocalBindings(Bindable &stmt_) NOTHROWS:
        stmt(stmt_)
    {}

    LocalBindings::~LocalBindings() NOTHROWS
    {
        TAKErr code;
        code = stmt.clearBindings();
        if (code != TE_Ok)
            Logger::log(Logger::Warning, "Failed to clear bindings arguments for local scope");
    }

    TAKErr LocalBindings::bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS
    {
        return stmt.bindBlob(idx, blob, size);
    }

    TAKErr LocalBindings::bindInt(const std::size_t idx, const int32_t value) NOTHROWS
    {
        return stmt.bindInt(idx, value);
    }

    TAKErr LocalBindings::bindLong(const std::size_t idx, const int64_t value) NOTHROWS
    {
        return stmt.bindLong(idx, value);
    }

    TAKErr LocalBindings::bindDouble(const std::size_t idx, const double value) NOTHROWS
    {
        return stmt.bindDouble(idx, value);
    }

    TAKErr LocalBindings::bindString(const std::size_t idx, const char *value) NOTHROWS
    {
        return stmt.bindString(idx, value);
    }

    TAKErr LocalBindings::bindNull(const std::size_t idx) NOTHROWS
    {
        return stmt.bindNull(idx);
    }

    TAKErr LocalBindings::clearBindings() NOTHROWS
    {
        return stmt.clearBindings();
    }
}
