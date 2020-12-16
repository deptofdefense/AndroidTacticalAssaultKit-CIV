
//NOTE: This is a modified version of FeatureDatabase.h intended to add temporary
//      support for storing AttributeSets until a more perminent solution arrives.


////============================================================================
////
////    FILE:           FeatureDatabase.h
////
////    DESCRIPTION:    Concrete class for database of spatial features.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 13, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_ATTRIBUTED_FEATURE_DATABASE_H_INCLUDED
#define ATAKMAP_FEATURE_ATTRIBUTED_FEATURE_DATABASE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <memory>
#include <stdint.h>
#include <utility>
#include <vector>

#include "db/Cursor.h"
#include "db/Database.h"
#include "db/Database2.h"
#include "db/DatabaseWrapper.h"
#include "feature/FeatureDataSource.h"
#include "thread/Thread.h"

#include "base/NonCopyable.hh"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


class Statement;


}                                       // Close db namespace.
}                                       // Close atakmap namespace.

namespace TAK
{
namespace Engine
{
namespace DB
{ 

class Database2;

}
}
}

////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::AttributedFeatureDatabase
///
///     Concrete class for a spatial features database.
///
///=============================================================================


class AttributedFeatureDatabase
  : public virtual db::DatabaseWrapper,
    private PGSC::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const bool SPATIAL_INDEX_ENABLED = true;


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Cursor;
    class Transaction;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~AttributedFeatureDatabase ()
        throw ()
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // Adds a Feature to the database.  Returns the ID of the new Feature.  The
    // optional values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the feature should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if minResolution or maxResolution is
    // negative or if featureName or geometryWKT is NULL.
    //
    int64_t
    addFeature (int64_t groupID,
                const FeatureDataSource::FeatureDefinition&,
                int64_t styleID = 0,            // No style.
                double minResolution = 0.0,     // No minimum.
                double maxResolution = 0.0);    // No maximum.

    //
    // Adds a group with the supplied provider, type, and groupName to the
    // database.  Returns the ID of the new group.  The optional values for
    // minResolution and maxResolution are ground sample distances (in
    // meters/pixel) of the lowest and highest resolutions at which the group of
    // features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if minResolution or maxResolution is
    // negative or if provider, type or groupName is NULL.
    //
    int64_t
    addGroup (const char* provider,
              const char* type,
              const char* groupName,
              double minResolution = 0.0,       // No minimum.
              double maxResolution = 0.0);      // No maximum.

    //
    // Adds a style to the database.  Returns the ID of the new style.
    //
    // Throws std::invalid_argument if styleRep is NULL.
    //
    int64_t
    addStyle (const char* styleRep);

    //
    // Begins a database transaction.  Transactions may be nested within the
    // same thread.  Database changes are not committed (or rolled back) until
    // endTransaction is called for the outermost transaction.  Changes are
    // rolled back if setTransactionSuccessful is not called for each of the
    // nested transactions.
    //
    // Throws db::DB_Error on database errors or if beginTransaction is called
    // before a successful transaction is ended.
    //
    void
    beginTransaction ();

    db::Statement*
    compileStatement (const char* sql)
      { return getDatabase ().compileStatement (sql); }

    static
    AttributedFeatureDatabase*
    createDatabase (const char* filePath);

    void
    deleteFeature (int64_t featureID);

    //
    //
    void
    deleteGroup (int64_t groupID);

    //
    // Ends the most recently begun database transaction.  Database changes are
    // not committed (or rolled back) for nested transactions until
    // endTransaction is called for the outermost transaction.  Changes are
    // rolled back if setTransactionSuccessful is not called for each of the
    // nested transactions.
    //
    // Throws db::DB_Error on database errors or if no transaction is in effect.
    //
    void
    endTransaction ();

    void
    execute (const char* sql)
      { getDatabase ().execute (sql); }

    void
    execute (const char* sql,
             const std::vector<const char*>& args)
      { getDatabase ().execute (sql, args); }

    using DatabaseWrapper::query;

    db::Cursor*
    query (const char* sql,
           const std::vector<const char*>& args)
      { return getDatabase ().query (sql, args); }

    //
    // Performs a query for Features with the supplied geometry encoding.
    //
    Cursor
    queryFeatures (FeatureDataSource::FeatureDefinition::Encoding);

    //
    // Marks the most recently begun (but not ended) transaction as successful
    // so that database changes will be committed when the transaction is ended.
    // For nested transactions, setTransactionSuccessful must be called for each
    // transaction or database changes will be rolled back when the outermost
    // transaction is ended.
    //
    // Throws db::DB_Error if no transaction is in effect or if an unended
    // transaction has already been marked successful.
    //
    void
    setTransactionSuccessful ();

      static void serializeAttributeSet(std::vector<uint8_t> &buffer, const atakmap::util::AttributeSet &attrs);
      static void deserializeAttributeSet(atakmap::util::AttributeSet &attrSet, atakmap::util::BlobImpl &blob);
      

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED CONSTANTS
    //==================================


    static const char* const TABLE_GEO;
    static const char* const TABLE_GROUP;
    static const char* const TABLE_STYLE;

    static const char* const COLUMN_GEO_ID;
    static const char* const COLUMN_GEO_CATALOG_ID;
    static const char* const COLUMN_GEO_GROUP_ID;
    static const char* const COLUMN_GEO_STYLE_ID;
    static const char* const COLUMN_GEO_VERSION;
    static const char* const COLUMN_GEO_NAME;
    static const char* const COLUMN_GEO_SPATIAL_GEOMETRY;
    static const char* const COLUMN_GEO_MAX_GSD;
    static const char* const COLUMN_GEO_MIN_GSD;
    static const char* const COLUMN_GEO_VISIBILITY;
    static const char* const COLUMN_GEO_VISIBILITY_VERSION;

    static const char* const COLUMN_GROUP_ID;
    static const char* const COLUMN_GROUP_CATALOG_ID;
    static const char* const COLUMN_GROUP_VERSION;
    static const char* const COLUMN_GROUP_NAME;
    static const char* const COLUMN_GROUP_PROVIDER;
    static const char* const COLUMN_GROUP_TYPE;
    static const char* const COLUMN_GROUP_MAX_GSD;
    static const char* const COLUMN_GROUP_MIN_GSD;
    static const char* const COLUMN_GROUP_VISIBILITY;
    static const char* const COLUMN_GROUP_VISIBILITY_CHECK;
    static const char* const COLUMN_GROUP_VISIBILITY_VERSION;

    static const char* const COLUMN_STYLE_ID;
    static const char* const COLUMN_STYLE_CATALOG_ID;
    static const char* const COLUMN_STYLE_NAME;
    static const char* const COLUMN_STYLE_REPRESENTATION;


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    AttributedFeatureDatabase (db::Database*);

    //
    // Adds a Feature to the database.  Returns the ID of the new Feature.  The
    // optional values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the feature should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if minResolution or maxResolution is
    // negative or if FeatureDefinition::getRawGeometry() returns NULL.
    //
    int64_t
    addFeature (int64_t catalogID,              // Use 0 for no catalog entry.
                int64_t groupID,
                const FeatureDataSource::FeatureDefinition&,
                int64_t styleID = 0,            // No style.
                double minResolution = 0.0,     // No minimum.
                double maxResolution = 0.0);    // No maximum.

    //
    // Adds a group with the supplied catalogID, provider, type, and groupName
    // to the database.  Returns the ID of the new group.  The optional values
    // for minResolution and maxResolution are ground sample distances (in
    // meters/pixel) of the lowest and highest resolutions at which the group of
    // features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if minResolution or maxResolution is
    // negative or if provider, type or groupName is NULL.
    //
    int64_t
    addGroup (int64_t catalogID,                // Use 0 for no catalog entry.
              const char* provider,
              const char* type,
              const char* groupName,
              double minResolution = 0.0,       // No minimum.
              double maxResolution = 0.0);      // No maximum.

    //
    // Adds a style to the database.  Returns the ID of the new style.
    //
    // Throws std::invalid_argument if styleRep is NULL.
    //
    int64_t
    addStyle (int64_t catalogID,                // Use 0 for no catalog entry.
              const char* styleRep);

    //
    //
    void
    deleteGroup (int64_t catalogID,
                 const char* groupName);

    //
    // Throws std::invalid_argument for GEOMETRY Encoding.
    //
    Cursor
    queryFeaturesInternal (FeatureDataSource::FeatureDefinition::Encoding,
                           const char* where,   // If NULL, ignores whereArgs.
                           const std::vector<const char*>& whereArgs);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    class Factory;
    friend class Factory;

    typedef atakmap::util::BlobImpl
            ByteBuffer;


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    int64_t
    addFeatureBlob (int64_t catalogID,
                    int64_t groupID,
                    const char* featureName,
                    const ByteBuffer&,
                    const util::AttributeSet&,
                    int64_t styleID = 0,        // No style.
                    double minResolution = 0.0, // No minimum.
                    double maxResolution = 0.0);// No maximum.

    int64_t
    addFeatureWKB (int64_t catalogID,
                   int64_t groupID,
                   const char* featureName,
                   const ByteBuffer&,
                   const util::AttributeSet&,
                   int64_t styleID = 0,         // No style.
                   double minResolution = 0.0,  // No minimum.
                   double maxResolution = 0.0); // No maximum.

    int64_t
    addFeatureWKT (int64_t catalogID,
                   int64_t groupID,
                   const char* featureName,
                   const char* geometryWKT,
                   const util::AttributeSet&,
                   int64_t styleID = 0,         // No style.
                   double minResolution = 0.0,  // No minimum.
                   double maxResolution = 0.0); // No maximum.


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::auto_ptr<db::Statement> insertBlobStmt;
    std::auto_ptr<db::Statement> insertStyleStmt;
    std::auto_ptr<db::Statement> insertWKB_Stmt;
    std::auto_ptr<db::Statement> insertWKT_Stmt;
    std::size_t transCount;             // Depth of nested transactions.
    TAK::Engine::Thread::ThreadID transThread;              // Only valid when transCount > 0.
    bool transSuccess;                  // Final (i.e., outer) success.
    bool transInnerSuccess;             // Innermost transaction success.
  };


///=============================================================================
///
///  class atakmap::feature::AttributedFeatureDatabase::Cursor
///
///     Concrete cursor for a AttributedFeatureDatabase catalog query result.
///
///=============================================================================


class AttributedFeatureDatabase::Cursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Cursor ()
        throw ()
      { }

    //
    // A private constructor is defined below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    FeatureDataSource::FeatureDefinition*
    getFeatureDefinition ()
        const
        throw (CursorError);

    int64_t
    getID ()
        const
        throw (CursorError)
      { return getLong (colID); }

    //
    // Returns the ground sample distance (in meters/pixel) of the "highest
    // resolution" at which the feature should be displayed.  A value of 0.0
    // indicates that there is no maximum.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the value returned by
    //          getMaxResolution will be less than or equal to the value
    //          returned by getMinResolution.
    //
    double
    getMaxResolution ()
        const
        throw (CursorError)
      { return getDouble (colMaxResolution); }

    //
    // Returns the ground sample distance (in meters/pixel) of the "lowest
    // resolution" at which the feature should be displayed.  A value of 0.0
    // indicates that there is no minimum.
    //
    // N.B.:    As "resolution" decreases (in the conventional sense), the
    //          number of meters/pixel increases; thus the value returned by
    //          getMinResolution will be greater than or equal to the value
    //          returned by getMaxResolution.
    //
    double
    getMinResolution ()
        const
        throw (CursorError)
      { return getDouble (colMinResolution); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class AttributedFeatureDatabase;


    Cursor (db::Cursor* subject,        // Adopts subject cursor.
            FeatureDataSource::FeatureDefinition::Encoding);


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    FeatureDataSource::FeatureDefinition::Encoding encoding;
    std::size_t colID;
    std::size_t colName;
    std::size_t colGeometry;
    std::size_t colStyle;
    std::size_t colMinResolution;
    std::size_t colMaxResolution;
  };


///=============================================================================
///
///  class atakmap::feature::AttributedFeatureDatabase::Transaction
///
///     A concrete class that implements a scope-based database transaction.
///     Transactions are not nestable, so they are not copyable.
///
///=============================================================================


class AttributedFeatureDatabase::Transaction
  : PGSC::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    Transaction (AttributedFeatureDatabase& db)
      : db (db)
      { db.beginTransaction (); }

    ~Transaction ()
        throw ()
      {
        try
          { db.endTransaction (); }
        catch (...)
          { }
      }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    AttributedFeatureDatabase& db;
  };

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


inline
int64_t
AttributedFeatureDatabase::addFeature (int64_t groupID,
                             const FeatureDataSource::FeatureDefinition& def,
                             int64_t styleID,   // 0 = no style.
                             double minRes,     // 0 = no minimum.
                             double maxRes)     // 0 = no maximum.
  { return addFeature (0, groupID, def, styleID, minRes, maxRes); }


inline
int64_t
AttributedFeatureDatabase::addGroup (const char* provider,
                           const char* type,
                           const char* groupName,
                           double minRes,       // 0 = no minimum.
                           double maxRes)       // 0 = no maximum.
  { return addGroup (0, provider, type, groupName, minRes, maxRes); }


inline
int64_t
AttributedFeatureDatabase::addStyle (const char* styleRep)
  { return addStyle (0, styleRep); }


inline
AttributedFeatureDatabase::Cursor
AttributedFeatureDatabase::queryFeatures
    (FeatureDataSource::FeatureDefinition::Encoding encoding)
  {
    static std::vector<const char*> noArgs;

    return queryFeaturesInternal (encoding, NULL, noArgs);
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_ATTRIBUTED_FEATURE_DATABASE_H_INCLUDED
