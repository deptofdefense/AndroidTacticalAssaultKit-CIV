////============================================================================
////
////    FILE:           DataStore.h
////
////    DESCRIPTION:    Abstract base class for data stores.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 23, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_DATA_STORE_H_INCLUDED
#define ATAKMAP_DB_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cmath>
#include <cstddef>
#include <functional>
#include <memory>
#include <set>
#include <vector>

#include "core/GeoPoint.h"
#include "db/Cursor.h"
#include "port/String.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "util/Disposable.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///=============================================================================
///
///  class atakmap::db::DataStore
///
///     Abstract base class for data stores.
///
///=============================================================================


class DataStore
  : public util::Disposable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class QueryParameters;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~DataStore ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //

    //
    // Returns a flag indicating whether or not the data store is currently
    // available.  In the event that the data store resides on a remote server,
    // this method may return false when no connection with that server can be
    // established.
    //
    // This method should always return false after the dispose member function
    // has been invoked.
    //
    virtual
    bool
    isAvailable ()
        const
        = 0;

    //
    // Refreshes the data store.  Any invalid entries are dropped.
    //
    virtual
    void
    refresh ()
        = 0;
  };


class DataStore::QueryParameters
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    struct Order                        // Defines the result ordering.
      {
        virtual
        ~Order ()
            NOTHROWS
          { }

        virtual
        bool
        isValid ()
            const
            NOTHROWS
          { return true; }
      };


    struct ID                           // Sorts by "primary" ID.
      : Order
      {
        ~ID ()
            NOTHROWS
          { }
      };


    struct Name                         // Sorts by some naming string.
      : Order
      {
        ~Name ()
            NOTHROWS
          { }
      };


    struct Provider                     // Sorts by some provider name.
      : Order
      {
        ~Provider ()
            NOTHROWS
          { }
      };


    struct Resolution                   // Sorts by GSD of maximum "resolution".
      : Order
      {
        ~Resolution ()
            NOTHROWS
          { }
      };


    struct Type                         // Sorts by some type name.
      : Order
      {
        ~Type ()
            NOTHROWS
          { }
      };


    struct SpatialFilter                // Defines a geospatial condition.
      {
        virtual
        ~SpatialFilter ()
            NOTHROWS
          { }

        virtual
        bool
        isValid ()
            const
            NOTHROWS
          { return true; }
      };


    //
    // Accepts items within a specified distance (in meters) of a specified
    // geographic location.
    //
    struct RadiusFilter
      : SpatialFilter
      {
        RadiusFilter (const core::GeoPoint& point,
                      double radius)
          : point (point),
            radius (radius)
          { }

        ~RadiusFilter ()
            NOTHROWS
          { }

        bool
        isValid ()
            const
            NOTHROWS
          { return !isnan (radius) && radius >= 0 && point.isValid (); }

        core::GeoPoint point;
        double radius;                  // In meters.
      };


    struct PointFilter
      : RadiusFilter
      {
        PointFilter (const core::GeoPoint& point)
          : RadiusFilter (point, 0)
          { }

        ~PointFilter ()
            NOTHROWS
          { }
      };

    //
    // Accepts items intersecting a specified geographic region.
    //
    struct RegionFilter
      : SpatialFilter
      {
        RegionFilter (const core::GeoPoint& upperLeft,
                      const core::GeoPoint& lowerRight)
          : upperLeft (upperLeft),
            lowerRight (lowerRight)
          { }

        ~RegionFilter ()
            NOTHROWS
          { }

        bool
        isValid ()
            const
            NOTHROWS
          { return upperLeft.isValid () && lowerRight.isValid (); }

        core::GeoPoint upperLeft;
        core::GeoPoint lowerRight;
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    QueryParameters ()
      : maxResolution (0.0),
        minResolution (0.0),
        resultLimit (0),
        resultOffset (0)
      { }

    virtual
    ~QueryParameters ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    bool
    isEmpty ()
        const
        NOTHROWS
      {
        return IDs.empty ()
            && names.empty ()
            && providers.empty ()
            && types.empty ()
            && !maxResolution
            && !minResolution
            && !resultLimit
            && !resultOffset
            && !spatialFilter
            && orders.empty ();
      }


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    //
    // Empty vectors indicate that all values are acceptable.
    //
    std::vector<int64_t> IDs;
    std::vector<TAK::Engine::Port::String> names;
    std::vector<TAK::Engine::Port::String> providers;
    std::vector<TAK::Engine::Port::String> types;

    //
    // The maxResolution is the ground sample distance (in meters/pixel) of the
    // "highest resolution" for features returned by the query.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the value of
    //          maxResolution should be less than or equal to the value of
    //          minResolution.
    //
    // Any dataset with a "minimum resolution" that is higher than this (i.e.,
    // its minResolution is a smaller ground sample distance) will be excluded
    // from the query results.
    //
    // A value of 0.0 indicates no upper limit on resolution.
    //
    double maxResolution;

    //
    // The minResolution is the ground sample distance (in meters/pixel) of the
    // "lowest resolution" for features returned by the query.
    //
    // N.B.:    As "resolution" decreases (in the conventional sense), the
    //          number of meters/pixel increases; thus the value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Any dataset with a "maximum resolution" that is lower than this (i.e.,
    // its maxResolution is a larger ground sample distance) will be excluded
    // from the query results.
    //
    // A value of 0.0 indicates no lower limit resolution.
    //
    double minResolution;
    std::size_t resultLimit;            // If non-zero, limit on result count.
    std::size_t resultOffset;           // If non-zero, index into full results.
    std::shared_ptr<SpatialFilter> spatialFilter; // May be NULL.
    std::vector<std::shared_ptr<Order> > orders;  // May be empty.
  };


///=============================================================================
///
///  class atakmap::db::DataStoreImpl
///
///     Abstract base class template for data stores.
///
///=============================================================================


template <class DerivedDataStore>
class DataStoreImpl
  : public DataStore
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class ContentListener;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~DataStoreImpl ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //

    //
    // Registers the supplied ContentListener for notifications when the
    // data store content changes.  Ignores a NULL ContentListener.
    //
    void
    addContentListener (ContentListener* listener)
      {
        if (listener)
          {
            TAK::Engine::Thread::Lock lock(getMutex()); // Provided by DerivedDataStore.

            listeners.insert (listener);
          }
      }

    //
    // Unregisters the supplied ContentListener from notifications of data store
    // content changes.  Ignores a NULL ContentListener.
    //
    void
    removeContentListener (ContentListener* listener)
      {
        if (listener)
          {
            TAK::Engine::Thread::Lock lock(getMutex());

            listeners.erase (listener);
          }
      }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    DataStoreImpl ()
      : mutex (TAK::Engine::Thread::TEMT_Recursive)
      { }

    TAK::Engine::Thread::Mutex&
    getMutex ()
        const
        NOTHROWS
      { return mutex; }

    virtual
    void
    notifyContentListeners ()
        const
      {
        //
        // Work with a copy of the set of listeners, in case a listener
        // unregisters itself during the callback, thereby invalidating the
        // iterator.
        //

        TAK::Engine::Thread::Lock lock(getMutex());
        std::set<ContentListener*> listenersCopy (listeners);

        std::for_each (listenersCopy.begin (),
                       listenersCopy.end (),
                       Notifier (const_cast<DerivedDataStore*>
                                     (static_cast<const DerivedDataStore*>
                                          (this))));
      }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    struct Notifier
      : std::unary_function<ContentListener*, void>
      {
        Notifier (DerivedDataStore* dataStore)
          : dataStore (dataStore)
          { }

        //
        // The compiler-generated copy constructor, destructor, and assignment
        // operator are acceptable.
        //

        void
        operator() (ContentListener* l)
        const
          { l->contentChanged (*dataStore); }

        DerivedDataStore* dataStore;
      };


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    mutable TAK::Engine::Thread::Mutex mutex;
    std::set<ContentListener*> listeners;
  };


///=============================================================================
///
///  class atakmap::db::DataStoreImpl::ContentListener
///
///     Abstract base class for data store content change callbacks.
///
///=============================================================================


template <class DerivedDataStore>
class DataStoreImpl<DerivedDataStore>::ContentListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~ContentListener ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the content of the supplied data store has changed.
    //
    virtual
    void
    contentChanged (DerivedDataStore&)
        = 0;
  };


template <class Type>                   // e.g., DatasetDescriptor, FeatureSet
class DS
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Cursor;
    class QueryParameters;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~DS ()
        NOTHROWS
      { }

    virtual
    Cursor*
    query ()                            // Retrieves all data elements.
        = 0;

    virtual
    Cursor*
    query (const QueryParameters&)
        = 0;

    virtual
    std::size_t
    queryCount ()
        = 0;

    virtual
    std::size_t
    queryCount (const QueryParameters&)
        = 0;
  };


template <class Type>
class DS<Type>::Cursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Cursor ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    //
    // Returns a Type corresponding to the current row.
    //
    virtual
    Type*
    get ()
        const
        throw (CursorError)
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    Cursor (std::unique_ptr<db::Cursor>&& cursor)
        throw (CursorError)
      : CursorProxy (std::move(cursor))
      { }
  };


}                                       // Close db namespace.
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


#endif  // #ifndef ATAKMAP_DB_DATA_STORE_H_INCLUDED
