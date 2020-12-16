////============================================================================
////
////    FILE:           RasterDataStore.h
////
////    DESCRIPTION:    Abstract base class for raster data stores.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 9, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_RASTER_DATA_STORE_H_INCLUDED
#define ATAKMAP_RASTER_RASTER_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////

#include <memory>

#include "db/Cursor.h"
#include "db/DataStore.h"

#include "feature/Geometry.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


class GeoPoint;


}                                       // Close core namespace.
namespace raster                        // Open raster namespace.
{


class DatasetDescriptor;


}                                       // Close raster namespace.
namespace port                          // Open port namespace.
{


    template<class T>
    class Iterator;


}                                       // Close util namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{

///=============================================================================
///
///  class atakmap::raster::RasterDataStore
///
///     Abstract base class for raster data stores.
///
///=============================================================================


class RasterDataStore
  : public db::DataStoreImpl<RasterDataStore>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class DatasetDescriptorCursor;
    struct DatasetQueryParameters;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~RasterDataStore ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // Returns the coverage for the specified dataset and imagery type
    // combination.
    //
    // If dataset is non-NULL and type is non-NULL, the returned Geometry will
    // be the coverage for that imagery type for the specified dataset.
    //
    // If dataset is non-NULL and type is NULL, the returned Geometry will be
    // the union of the coverage for all imagery types for that dataset.
    //
    // If dataset is NULL and type is non-NULL, the returned Geometry will be
    // the union of the coverage for that imagery type across all datasets.
    //
    // Throws std::invalid_argument if both dataset and type are NULL.
    //
    virtual
    feature::UniqueGeometryPtr
    getCoverage (const char* dataset,   // May be NULL (if type is non-NULL).
                 const char* type)      // May be NULL (if dataset is non-NULL).
        = 0;

    //
    // Returns the names of datasets available in the data store.
    //
    virtual
    atakmap::port::Iterator<const char*> *
    getDatasetNames ()
        const
        = 0;

    //
    // Returns the data types available across all datasets in the data store.
    //
    virtual
    atakmap::port::Iterator<const char*> *
    getDatasetTypes ()
        const
        = 0;

    //
    // Returns the imagery types available across all datasets in the data store.
    //
    virtual
    atakmap::port::Iterator<const char *> *
    getImageryTypes ()
        const
        = 0;

    virtual
    double
    getMinimumResolution (const char* dataset,   // May be NULL (if type is non-NULL).
                          const char* type)      // May be NULL (if dataset is non-NULL).
        = 0;

    virtual
    double
    getMaximumResolution(const char* dataset,   // May be NULL (if type is non-NULL).
                         const char* type)      // May be NULL (if dataset is non-NULL).
        = 0;

    //
    // Returns the providers available across all datasets in the data store.
    //
    virtual
    atakmap::port::Iterator<const char*> *
    getProviders ()
        const
        = 0;

    //
    // Returns a cursor to all datasets in the data store.
    //
    DatasetDescriptorCursor*
    queryDatasets ()
        const;

    //
    // Queries the data store for all datasets matching the specified
    // DatasetQueryParameters.
    //
    virtual
    DatasetDescriptorCursor*
    queryDatasets (const DatasetQueryParameters&)
        const
        = 0;

    //
    // Returns the number of Features that would be returned by queryDatasets().
    //
    std::size_t
    queryDatasetsCount ()
        const;

    //
    // Returns the number of results for the specified query.
    //
    virtual
    std::size_t
    queryDatasetsCount (const DatasetQueryParameters&)
        const
        = 0;

    virtual
    void destroyStringIterator(atakmap::port::Iterator<const char*> *it)
        const
        = 0;
  };


///=============================================================================
///
///  class atakmap::raster::RasterDataStore::DatasetDescriptorCursor
///
///     Abstract base class for accessing DatasetDescriptor query results.
///
///=============================================================================


class RasterDataStore::DatasetDescriptorCursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~DatasetDescriptorCursor ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    //
    // Returns the DatasetDescriptor corresponding to the current row.
    //
    virtual
    DatasetDescriptor&
    get ()
        const
        throw (CursorError)
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    DatasetDescriptorCursor (std::unique_ptr<db::Cursor> &&cursor)
        throw (CursorError)
      : CursorProxy (std::move(cursor))
      { }
  };


///=============================================================================
///
///  struct atakmap::raster::RasterDataStore::DatasetQueryParameters
///
///     Specifies the common criteria that Datasets may be queried against.
///
///     DataStore::QueryParameters::types       = Dataset types
///
///     Supports the following DataStore::QueryParameters::SpatialFilter types:
///
///             PointFilter
///             RegionFilter
///
///     Supports the following DataStore::QueryParameters::Order types:
///
///             Name            sorts by dataset name
///             Provider        sorts by dataset provider
///             Resolution      sorts by (decreasing) level of detail
///             Type            sorts by dataset type
///
///=============================================================================


struct RasterDataStore::DatasetQueryParameters
  : db::DataStore::QueryParameters
  {
    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum Locality
      {
        LOCAL_ONLY,                     // Select only local datasets.
        REMOTE_ONLY,                    // Select only remote datasets.
        ALL
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    DatasetQueryParameters ()
      : locality (ALL)
      { }

    ~DatasetQueryParameters ()
        NOTHROWS
      { }

    void
    intersect (const DatasetQueryParameters& rhs)
      {
        intersect (IDs, rhs.IDs);
        intersect (names, rhs.names);
        intersect (providers, rhs.providers);
        intersect (types, rhs.types);

        //
        // The more restrictive maxResolution is the one that excludes more GSD
        // values, i.e., the larger of the two GSD values.  The more restrictive
        // minResolution is the smaller of the two GSD (modulo the value 0.0,
        // which represents no restriction on minimum resolutions).
        //

        maxResolution = std::max (maxResolution, rhs.maxResolution);
        minResolution = minResolution && rhs.minResolution
            ? std::min (minResolution, rhs.minResolution)
            : std::max (minResolution, rhs.minResolution);

        if (rhs.resultLimit
            && (!resultLimit || rhs.resultLimit < resultLimit))
          {
            resultLimit = rhs.resultLimit;
            resultOffset = rhs.resultOffset;
          }

        if (rhs.spatialFilter.get () && !spatialFilter.get ())
          {
            spatialFilter = rhs.spatialFilter;
          }

        //
        // Order intersection doesn't really make sense, since it doesn't
        // restrict the results of the query.
        //

        intersect (orders, rhs.orders);

        intersect (imageryTypes, rhs.imageryTypes);
        if (locality == ALL)
          {
            locality = rhs.locality;
          }
      }

    static
    DatasetQueryParameters
    intersect (const DatasetQueryParameters& lhs,
               const DatasetQueryParameters& rhs)
      {
        DatasetQueryParameters result (lhs);

        result.intersect (rhs);
        return result;
      }

    bool
    isEmpty ()
        const
        NOTHROWS
      { return QueryParameters::isEmpty () && imageryTypes.empty (); }


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    //
    // Empty vectors indicate that all values are acceptable.
    //
    std::vector<TAK::Engine::Port::String> imageryTypes;
    Locality locality;


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    template <typename ArgT>
    struct NotFoundIn
      : std::unary_function<ArgT, bool>
      {
        NotFoundIn (const std::vector<ArgT>& vec)
          : vec (vec)
          { }

        bool
        operator() (const ArgT& val)
            const
          { return vec.end () == std::find (vec.begin (), vec.end (), val); }

        const std::vector<ArgT>& vec;
      };


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    template <typename ArgT>
    static
    void
    intersect (std::vector<ArgT>& lhs,
               const std::vector<ArgT>& rhs)
      { lhs.erase(std::remove_if (lhs.begin (), lhs.end (), notFoundIn (rhs)), lhs.end()); }

    template <typename ArgT>
    static
    NotFoundIn<ArgT>
    notFoundIn (const std::vector<ArgT>& vec)
      { return NotFoundIn<ArgT> (vec); }
  };


template <>
struct RasterDataStore::DatasetQueryParameters::NotFoundIn<TAK::Engine::Port::String>
  : std::unary_function<TAK::Engine::Port::String, bool>
  {
    NotFoundIn (const std::vector<TAK::Engine::Port::String>& vec)
      : vec (vec)
      { }

    bool
    operator() (const TAK::Engine::Port::String& val)
        const
      {
        return vec.end () == std::find_if (vec.begin (), vec.end (),
                                           TAK::Engine::Port::StringEqual (val));
      }

    const std::vector<TAK::Engine::Port::String>& vec;
  };


template <>
struct RasterDataStore::DatasetQueryParameters::NotFoundIn
           <std::shared_ptr<db::DataStore::QueryParameters::Order> >
  : std::unary_function<std::shared_ptr<Order>, bool>
  {
    NotFoundIn (const std::vector<std::shared_ptr<Order> >& vec)
      : vec (vec)
      { }

    bool
    operator() (const std::shared_ptr<Order>& val)
        const
      {
        return vec.end () == std::find_if (vec.begin (), vec.end (),
                                           OrderTypeEqual
                                               (typeid (val.get ())));
      }

    struct OrderTypeEqual
      : std::unary_function<std::shared_ptr<Order>, bool>
      {
        OrderTypeEqual (const std::type_info& type)
          : type (type)
          { }

        bool
        operator() (const std::shared_ptr<Order>& val)
            const
          { return typeid (val.get ()) == type; }

        const std::type_info& type;
      };

    const std::vector<std::shared_ptr<Order> >& vec;
  };


}                                       // Close raster namespace.
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

#endif  // #ifndef ATAKMAP_RASTER_RASTER_DATA_STORE_H_INCLUDED
