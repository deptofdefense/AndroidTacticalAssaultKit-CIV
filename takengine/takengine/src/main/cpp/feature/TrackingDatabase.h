//
//  BreadCrumbDatabase.hpp
//  TAKEngine
//
//  Created by Jeffrey Delawder Jr on 7/5/17.
//  Copyright © 2017 TAK. All rights reserved.
//

#ifndef TrackingDatabase_hpp
#define TrackingDatabase_hpp

#include <stdio.h>


#include <cstdint>
#include <memory>

#include "threads/Mutex.hh"

#include "db/CursorWrapper2.h"
#include "db/DB_Error.h"
#include "db/Database2.h"
#include "port/Platform.h"
#include "util/Error.h"

/*
    Functionally this class is meant to be similar to CrumbDatabase in ATAK. Distinct differences are that from the begining this database will utilize segments references lists of crumbs. As such this class will be a more generic "Tracking Database" instead of "CrumbDatabase" to handle possible future schema changes. Interacting with this database will be handled by querying for segments based on a given feature id. Those segments can then be used to query for their respective breadcrumbs.
 */


namespace TAK {
    namespace Engine {
        namespace Tracking {
            class BreadCrumb;
            class CrumbTrail;
            class ContactSegments;
            class Segment;
            
            
            class TrackingDatabase : private PGSC::NonCopyable {
                
                public :
                
                // The SegmentCursor class handles accessing columns in the segment table.
                // Segments are a conituous tracking event. They contain the high level information about relevant tracking settings and are linked to features by featureID.
                class  SegmentCursor;
                typedef std::unique_ptr<SegmentCursor, void(*)(const SegmentCursor *)> SegmentCursorPtr;
                
                // The BreadCrumbCursor class handles accessing columns in the breadcrumb table
                class BreadCrumbCursor;
                typedef std::unique_ptr<BreadCrumbCursor, void(*)(const BreadCrumbCursor *)> BreadCrumbCursorPtr;
                
                
                public :
                TrackingDatabase() NOTHROWS;
                
                public :
                virtual ~TrackingDatabase() NOTHROWS;
                
                public :
                TAK::Engine::Util::TAKErr open(const char *path) NOTHROWS;
                
                protected :
                virtual TAK::Engine::Util::TAKErr openImpl(DB::DatabasePtr &&db) NOTHROWS;
                
                protected :
                /**
                 * Returns <code>true</code> if the database version is current, <code>false</code> otherwise.
                 * If this method returns <code>false</code>, the database will be rebuilt with a call to
                 * {@link #dropTables()} followed by {@link #buildTables()}. Subclasses may override this method
                 * to support their own versioning mechanism.
                 * <P>
                 * The default implementation compares the version of the database via
                 * <code>this.database</code>.{@link DatabaseIface#getVersion()} against
                 * {@link #TRACKING_DATABASE_VERSION}.
                 *
                 * @return <code>true</code> if the database version is current, <code>false</code> otherwise.
                 */
                virtual TAK::Engine::Util::TAKErr checkDatabaseVersion(bool *value) NOTHROWS;
                
                /**
                 * Sets the database versioning to the current version. Subclasses may override this method to
                 * support their own versioning mechanism.
                 * <P>
                 * The default implementation sets the version of the database via <code>this.database</code>.
                 * {@link DatabaseIface#setVersion(int)} with {@link #TRACKING_DATABASE_VERSION}.
                 */
                virtual TAK::Engine::Util::TAKErr setDatabaseVersion() NOTHROWS;
                
                /**
                 * Drops the tables present in the database. This method is invoked in the constructor when the
                 * database version is not current.
                 */
                virtual TAK::Engine::Util::TAKErr dropTables() NOTHROWS;
                
                /**
                 * Builds the tables for the database. This method is invoked in the constructor when the
                 * database lacks the catalog table or when if the database version is not current.
                 * <P>
                 * The default implementation invokes {@link #createTrackingTable()} and returns.
                 */
                virtual TAK::Engine::Util::TAKErr buildTables() NOTHROWS;
                
               
                /**
                 * Creates a record of the breadcrumb in the database.
                 *
                 */
                virtual TAK::Engine::Util::TAKErr createAndStoreBreadCrumb(double latitude, double longitude, const char *userID, const char *userCallsign, long timestamp, double speed, double bearing, int segmentID) NOTHROWS;
                
                
                /**
                 * Get list of users which have tracks in the local DB
                 *
                 */
                virtual TAK::Engine::Util::TAKErr getStoredContactSegments(const SegmentCursor *ptr) NOTHROWS;

                
                /**
                 * Creates the catalog table.
                 */
                TAK::Engine::Util::TAKErr createTrackingTables() NOTHROWS;
                  
                private :
                static void deleteSegmentCursor(const SegmentCursor *ptr);
                
                private :
                static void deleteBreadCrumbCursor(const BreadCrumbCursor *ptr);
                protected :
                static int trackingDatabaseSchemaVersion();
                
                // Below table names and column constants are defined. Ideally this won't need to be used by outside classes
                // unless a custom query is desired. Standard cursors are already provided by the class and specialization
                // shouldn't be necessary.
                
                static const char * const TABLE_SEGMENT;
                static const char * const TABLE_BREADCRUMB;
                
                
                static const char * const COLUMN_SEGMENT_ID; // unique id field
                static const char * const COLUMN_SEGMENT_TIMESTAMP; // the UTC time of this fix, in milliseconds since January 1, 1970
                static const char * const COLUMN_SEGMENT_TITLE; //label for segment
                static const char * const COLUMN_SEGMENT_COLOR; //int AARRGGBB
                static const char * const COLUMN_SEGMENT_STYLE; //line, dash, arrow
                static const char * const COLUMN_SEGMENT_FEATURE_UID; // unique identifier for the map item being logged
                static const char * const COLUMN_SEGMENT_FEATURE_TITLE; // callsign or human readable title of the map item being logged
        
          
                static const char * const COLUMN_BREADCRUMB_ID; // unique id field
                static const char * const COLUMN_BREADCRUMB_TIMESTAMP; // Time of the breadcrumb's creation
                static const char * const COLUMN_BREADCRUMB_LAT;    // latitude in degrees
                static const char * const COLUMN_BREADCRUMB_LON;    // longitude in degrees
                static const char * const COLUMN_BREADCRUMB_ALT;    // altitude in meters HAE
                static const char * const COLUMN_BREADCRUMB_CE;     // ce90 in meters
                static const char * const COLUMN_BREADCRUMB_LE;     // le90 in meters
                static const char * const COLUMN_BREADCRUMB_BEARING;// bearing in degrees
                static const char * const COLUMN_BREADCRUMB_SPEED;  // speed in meters / second
                static const char * const COLUMN_BREADCRUMB_PTSOURCE;   // source of the point information
                static const char * const COLUMN_BREADCRUMB_ALTSOURCE;  // source of the altitude/elevation information
                static const char * const COLUMN_BREADCRUMB_POINT_GEOMETRY; // searchable point entry
                
                
                
                static const char * const COLUMN_CATALOG_METADATA_KEY;
                static const char * const COLUMN_CATALOG_METADATA_VALUE;
                
                protected :
                TAK::Engine::DB::DatabasePtr database;
                PGSC::Mutex mutex;
            };
            
            
            
            
        }
    }
}

#endif /* TrackingDatabase_hpp */


