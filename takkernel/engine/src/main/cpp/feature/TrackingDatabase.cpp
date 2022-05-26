//
//  BreadCrumbDatabase.cpp
//  TAKEngine
//
//  Created by Jeffrey Delawder Jr on 7/6/17.
//  Copyright © 2017 TAK. All rights reserved.
//

#include "TrackingDatabase.h"

#include <algorithm>
#include <sstream>

#include "threads/Lock.hh"

#include "db/Query.h"
#include "db/Statement2.h"
#include "port/STLVectorAdapter.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging.h"

#include "SegmentCursor.h"
#include "BreadCrumbCursor.h"


using namespace TAK::Engine::Tracking;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

#define TRACKING_DATABASE_VERSION 1

#define TBL_SEG             "segment"
#define TBL_BC              "breadcrumb"

#define COL_SEGMENT_TIMESTAMP   "s_timestamp"
#define COL_SEGMENT_TITLE   "s_title"
#define COL_SEGMENT_COLOR   "s_color"
#define COL_SEGMENT_STYLE   "s_style"
#define COL_SEGMENT_FID     "s_fid"
#define COL_SEGMENT_FTITLE  "s_ftitle"

#define COL_BREADCRUMB_ID   "b_id"
#define COL_BREADCRUMB_TIMESTAMP   "b_timestamp"
#define COL_SEGMENT_ID      "s_id"
#define COL_BREADCRUM_LAT   "b_lat"
#define COL_BREADCRUM_LON   "b_lon"
#define COL_BREADCRUM_ALT   "b_alt"
#define COL_BREADCRUM_CE    "b_ce"
#define COL_BREADCRUM_LE    "b_le"
#define COL_BREADCRUM_BEARING   "b_bearing"
#define COL_BREADCRUM_SPEED "b_speed"
#define COL_BREADCRUM_PTSOURCE  "b_pt_source"
#define COL_BREADCRUM_ALTSOURCE "b_alt_source"
#define COL_BREADCRUM_POINT_GEOMETRY    "b_pt_geom"



#define DEFAULT_SEGMENTS_CURSOR_QUERY \
"SELECT * FROM " \
TBL_SEG " WHERE " COL_SEGMENT_FID " = ?"

#define DEFAULT_BREADCRUMBS_CURSOR_QUERY \
"SELECT * FROM " \
TBL_BC " WHERE " COL_SEGMENT_ID " = ?"

const char * const TrackingDatabase::TABLE_SEGMENT = TBL_SEG;
const char * const TrackingDatabase::TABLE_BREADCRUMB = TBL_BC;

const char * const TrackingDatabase::COLUMN_SEGMENT_ID = COL_SEGMENT_ID;
const char * const TrackingDatabase::COLUMN_SEGMENT_TIMESTAMP = COL_SEGMENT_TIMESTAMP;
const char * const TrackingDatabase::COLUMN_SEGMENT_TITLE = COL_SEGMENT_TITLE;
const char * const TrackingDatabase::COLUMN_SEGMENT_COLOR = COL_SEGMENT_COLOR;
const char * const TrackingDatabase::COLUMN_SEGMENT_STYLE = COL_SEGMENT_STYLE;
const char * const TrackingDatabase::COLUMN_SEGMENT_FEATURE_UID = COL_SEGMENT_FID;
const char * const TrackingDatabase::COLUMN_SEGMENT_FEATURE_TITLE = COL_SEGMENT_FTITLE;


const char * const TrackingDatabase::COLUMN_BREADCRUMB_ID = COL_BREADCRUMB_ID;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_TIMESTAMP = COL_BREADCRUMB_TIMESTAMP;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_LAT = COL_BREADCRUM_LAT;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_LON = COL_BREADCRUM_LON;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_ALT = COL_BREADCRUM_ALT;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_CE = COL_BREADCRUM_CE;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_LE = COL_BREADCRUM_LE;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_BEARING = COL_BREADCRUM_BEARING;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_SPEED = COL_BREADCRUM_SPEED;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_PTSOURCE = COL_BREADCRUM_PTSOURCE;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_ALTSOURCE = COL_BREADCRUM_ALTSOURCE;
const char * const TrackingDatabase::COLUMN_BREADCRUMB_POINT_GEOMETRY = COL_BREADCRUM_POINT_GEOMETRY;

TrackingDatabase::TrackingDatabase() NOTHROWS :
    database(NULL, NULL),
    mutex(PGSC::Mutex::Attr(PGSC::Mutex::Attr::RECURSIVE))
{}

TrackingDatabase::~TrackingDatabase() NOTHROWS
{}



TAKErr TrackingDatabase::open(const char *databasePath) NOTHROWS
{
    PGSC::Lock lock(mutex);
    
    TAKErr code;
    
    DatabasePtr db(NULL, NULL);
    
    // Opens a connection to the database at the given path.
    code = Databases_openDatabase(db, databasePath, false);
    TE_CHECKRETURN_CODE(code);
    
    // With pointer to the database inialized we now valid the database. If needed we will have to create the database.
    return this->openImpl(std::move(db));
}

TAKErr TrackingDatabase::openImpl(DatabasePtr &&db) NOTHROWS
{
    TAKErr code;
    this->database = std::move(db);
    
    std::vector<Port::String> tableNames;
    
    // Tries to retrieve table names from database if they exist
    Port::STLVectorAdapter<Port::String> tableNamesAdapter(tableNames);
    code = Databases_getTableNames(tableNamesAdapter, *this->database);
    TE_CHECKRETURN_CODE(code);
    
    // Check if the table needs to be created
    bool create = (std::find(tableNames.begin(), tableNames.end(), Port::String(TABLE_SEGMENT))==tableNames.end());
    
    
    // Check if valid database version
    bool versionValid;
    code = this->checkDatabaseVersion(&versionValid);
    TE_CHECKRETURN_CODE(code);
    
    // If not valid then drop the tables, and set create flag to true
    if (!versionValid) {
        code = this->dropTables();
        TE_CHECKRETURN_CODE(code);
        create = true;
    }
    
    if (create) {
        
        // Check if database location is marked as read only
        bool readOnly = false;
        code = this->database->isReadOnly(&readOnly);
        TE_CHECKRETURN_CODE(code);
        
        if (readOnly) {
            Logger::log(Logger::Error, "Database is read only");
            return TE_InvalidArg;
        }
        
        // Build the table schema
        code = this->buildTables();
        TE_CHECKRETURN_CODE(code);
        
        // Set the database version
        code = this->setDatabaseVersion();
        TE_CHECKRETURN_CODE(code);
    }
    
    return code;
}

TAKErr TrackingDatabase::checkDatabaseVersion(bool *value) NOTHROWS
{
    int version;
    TAKErr code = this->database->getVersion(&version);
    TE_CHECKRETURN_CODE(code);
    *value = (version == TRACKING_DATABASE_VERSION);
    return code;
}

TAKErr TrackingDatabase::setDatabaseVersion() NOTHROWS
{
    return this->database->setVersion(TRACKING_DATABASE_VERSION);
}

TAKErr TrackingDatabase::dropTables() NOTHROWS
{
    TAKErr code;
    code = this->database->execute("DROP TABLE IF EXISTS " TBL_SEG, NULL, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TABLE IF EXISTS " TBL_BC, NULL, 0);
    return code;
}

TAKErr TrackingDatabase::buildTables() NOTHROWS
{
    return this->createTrackingTables();
}

TAKErr TrackingDatabase::createTrackingTables() NOTHROWS
{
    TAKErr code;
    
    code = this->database->execute("CREATE TABLE " TBL_SEG " ("
                                   COL_SEGMENT_ID " INTEGER PRIMARY KEY AUTOINCREMENT, "
                                   COL_SEGMENT_TIMESTAMP " INTEGER, "
                                   COL_SEGMENT_TITLE " TITLE, "
                                   COL_SEGMENT_COLOR " INTEGER, "
                                   COL_SEGMENT_STYLE " TEXT, "
                                   COL_SEGMENT_FID " TEXT, "
                                   COL_SEGMENT_FTITLE " TITLE)", NULL, 0);
    TE_CHECKRETURN_CODE(code);
    
    code = this->database->execute("CREATE TABLE " TBL_BC " ("
                                   COL_BREADCRUMB_ID " INTEGER PRIMARY KEY AUTOINCREMENT, "
                                   COL_SEGMENT_ID " INTEGER, "
                                   COL_BREADCRUMB_TIMESTAMP " INTEGER, "
                                   COL_BREADCRUM_LAT " REAL, "
                                   COL_BREADCRUM_LON " REAL, "
                                   COL_BREADCRUM_ALT " REAL, "
                                   COL_BREADCRUM_CE " REAL, "
                                   COL_BREADCRUM_LE " REAL, "
                                   COL_BREADCRUM_BEARING " REAL, "
                                   COL_BREADCRUM_SPEED " REAL, "
                                   COL_BREADCRUM_PTSOURCE " TEXT, "
                                   COL_BREADCRUM_ALTSOURCE " TEXT)", NULL, 0);
    TE_CHECKRETURN_CODE(code);
    
    
    return code;
}

void TrackingDatabase::deleteSegmentCursor(const SegmentCursor *ptr)
{
    delete ptr;
}

void TrackingDatabase::deleteBreadCrumbCursor(const BreadCrumbCursor *ptr)
{
    delete ptr;
}

int trackingDatabaseSchemaVersion()
{
    return TRACKING_DATABASE_VERSION;
}

TAKErr TrackingDatabase::createAndStoreBreadCrumb(double latitude, double longitude, const char *userID, const char *userCallsign, long timestamp, double speed, double bearing, int segmentID) NOTHROWS
{
    TAKErr code = TE_Ok;
    
    
    return code;
}

TAKErr TrackingDatabase::getStoredContactSegments(const SegmentCursor *ptr) NOTHROWS
{
    TAKErr code = TE_Ok;
    
    
    return code;
}




