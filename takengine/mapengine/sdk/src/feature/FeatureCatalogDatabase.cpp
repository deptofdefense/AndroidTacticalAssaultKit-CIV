////============================================================================
////
////    FILE:           FeatureCatalogDatabase.cpp
////
////    DESCRIPTION:    Implementation of FeatureCatalogDatabase class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Mar 18, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/FeatureCatalogDatabase.h"

#include <memory>
#include <sstream>
#include <vector>

#include "db/Database.h"
#include "db/Statement.h"


#define MEM_FN( fn )    "atakmap::feature::FeatureCatalogDatabase::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


}                                       // Close unnamed namespace.


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::FeatureCatalogDatabase::Factory
///
///     Concrete factory class for creating FeatureCatalogDatabase objects.
///
///=============================================================================


class FeatureCatalogDatabase::Factory
  : public CatalogDatabase::Factory
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Factory ()
        NOTHROWS override
      { }

    //
    // Returns an instance of FeatureCatalogDatabase for the supplied file path.
    // Returns a temporary database if the supplied file path is NULL.
    //
    FeatureCatalogDatabase*
    getDatabase (const char* filePath)
        const
        NOTHROWS
      {
        return dynamic_cast<FeatureCatalogDatabase*>
                   (getCatalogDatabase (db::openDatabase (filePath)));
      }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  CatalogDatabase::Factory IMPLEMENTATION
    //==================================


    //
    // Returns an instance of a class derived from CatalogDatabase for the
    // supplied Database and CurrencyRegistry (neither of which will be NULL).
    //
    CatalogDatabase*
    createCatalogDatabase (db::Database* db,
                           CurrencyRegistry* currency_registry)
        const
        NOTHROWS override
      { return new FeatureCatalogDatabase (db, currency_registry); }
  };


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


std::string
deleteCatalogedSQL (const char* featureTable,
                    const char* featureColumn)
  {
    std::ostringstream strm;

    strm << "DELETE FROM " << featureTable
         << " WHERE " << featureColumn << " = ?";

    return strm.str();
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


FeatureCatalogDatabase*
FeatureCatalogDatabase::createDatabase (const char* filePath)
  {
    static Factory factory;

    return factory.getDatabase (filePath);
  }


FeatureDatabase::Cursor
FeatureCatalogDatabase::queryFeatures
    (FeatureDataSource::FeatureDefinition::Encoding encoding,
     const char* filePath)
  {
    static const TAK::Engine::Port::String whereSQL (whereCatalogSQL ());
    const char* where (nullptr);
    std::vector<const char*> whereArgs;

    if (filePath)
      {
        where = whereSQL;
        whereArgs.push_back (filePath);
      }

    return queryFeaturesInternal (encoding, where, whereArgs);
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


FeatureCatalogDatabase::FeatureCatalogDatabase (db::Database* database,
                                                CurrencyRegistry* registry)
  : DatabaseWrapper (database),
    CatalogDatabase (database, registry),
    FeatureDatabase (database)
  { }


void
FeatureCatalogDatabase::catalogEntryRemoved (int64_t catalogID,
                                             bool automated)
  {
    db::Database& db (getDatabase ());
    std::unique_ptr<db::Statement> stmt;

    if (catalogID > 0)
      {
        static const TAK::Engine::Port::String deleteGeometries
            (deleteCatalogedSQL (TABLE_GEO, COLUMN_GEO_CATALOG_ID).c_str());
        static const TAK::Engine::Port::String deleteGroups
            (deleteCatalogedSQL (TABLE_GROUP, COLUMN_GROUP_CATALOG_ID).c_str());
        static const TAK::Engine::Port::String deleteStyles
            (deleteCatalogedSQL (TABLE_STYLE, COLUMN_STYLE_CATALOG_ID).c_str());

        stmt.reset (db.compileStatement (deleteGeometries));
        stmt->bind (1, catalogID);
        stmt->execute ();
        stmt.reset (db.compileStatement (deleteGroups));
        stmt->bind (1, catalogID);
        stmt->execute ();
        stmt.reset (db.compileStatement (deleteStyles));
        stmt->bind (1, catalogID);
        stmt->execute ();
      }
    else
      {
        static const TAK::Engine::Port::String deleteGeometries
            (deleteUnlinkedSQL (TABLE_GEO, COLUMN_GEO_CATALOG_ID));
        static const TAK::Engine::Port::String deleteGroups
            (deleteUnlinkedSQL (TABLE_GROUP, COLUMN_GROUP_CATALOG_ID));
        static const TAK::Engine::Port::String deleteStyles
            (deleteUnlinkedSQL (TABLE_STYLE, COLUMN_STYLE_CATALOG_ID));

        stmt.reset (db.compileStatement (deleteGeometries));
        stmt->execute ();
        stmt.reset (db.compileStatement (deleteGroups));
        stmt->execute ();
        stmt.reset (db.compileStatement (deleteStyles));
        stmt->execute ();
      }
  }


TAK::Engine::Port::String
FeatureCatalogDatabase::deleteUnlinkedSQL (const char* featureTable,
                                           const char* featureColumn)
  {
    std::ostringstream strm;

    strm << "DELETE FROM " << featureTable << " WHERE " << featureColumn
         << " IN (SELECT " << featureColumn
         << " FROM " << featureTable << " LEFT JOIN " << TABLE_CATALOG
         << " ON " << featureTable << "." << featureColumn
         << " = " << TABLE_CATALOG << "." << COLUMN_CATALOG_ID
         << " WHERE " << TABLE_CATALOG << "." << COLUMN_CATALOG_ID << " IS NULL)";

    return strm.str ().c_str ();
  }


TAK::Engine::Port::String
FeatureCatalogDatabase::whereCatalogSQL ()
  {
    std::ostringstream strm;

    strm << " WHERE " << TABLE_GEO << "." << COLUMN_GEO_CATALOG_ID
         << " IN (SELECT " << COLUMN_CATALOG_ID
                           << " FROM " << TABLE_CATALOG
                           << " WHERE " << COLUMN_CATALOG_PATH << " = ?)";

    return strm.str ().c_str ();
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
