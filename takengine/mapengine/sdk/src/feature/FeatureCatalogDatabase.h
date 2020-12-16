////============================================================================
////
////    FILE:           FeatureCatalogDatabase.h
////
////    DESCRIPTION:    Concrete class for a spatial features database that uses
////                    a Catalog.
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


#ifndef ATAKMAP_FEATURE_FEATURE_CATALOG_DATABASE_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_CATALOG_DATABASE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "db/CatalogDatabase.h"
#include "feature/FeatureDatabase.h"
#include "feature/FeatureDataSource.h"
#include "port/String.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////

namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


class Database;


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


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
///  class atakmap::feature::FeatureCatalogDatabase
///
///     Concrete class for a spatial features database that uses a Catalog.
///
///=============================================================================


class FeatureCatalogDatabase
  : public db::CatalogDatabase,
    public FeatureDatabase
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~FeatureCatalogDatabase ()
        NOTHROWS
      { }

    //
    // A private constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // Make the protected FeatureDatabase member functions that accept catalog
    // IDs accessible.
    //
    using FeatureDatabase::addFeature;
    using FeatureDatabase::addGroup;
    using FeatureDatabase::addStyle;
    using FeatureDatabase::deleteGroup;

    static
    FeatureCatalogDatabase*
    createDatabase (const char* filePath);

    //
    // Performs a query for Features with the supplied geometry encoding that
    // are from the supplied filePath.
    //
    FeatureDatabase::Cursor
    queryFeatures (FeatureDataSource::FeatureDefinition::Encoding,
                   const char* filePath);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    class Factory;
    friend class Factory;


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    FeatureCatalogDatabase (db::Database*,
                            CurrencyRegistry*);


    static
    TAK::Engine::Port::String
    deleteUnlinkedSQL (const char* featureTable,
                       const char* featureColumn);

    static
    TAK::Engine::Port::String
    whereCatalogSQL ();


    //==================================
    //  CatalogDatabase IMPLEMENTATION
    //==================================


    void
    catalogEntryAdded (int64_t catalogID)
      { }

    void
    catalogEntryMarkedValid (int64_t catalogID)
      { }

    void
    catalogEntryRemoved (int64_t catalogID,
                         bool automated);


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


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

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_CATALOG_DATABASE_H_INCLUDED
