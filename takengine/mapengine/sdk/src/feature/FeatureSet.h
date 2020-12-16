////============================================================================
////
////    FILE:           FeatureSet.h
////
////    DESCRIPTION:    Definition of FeatureSet class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 14, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_FEATURE_SET_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_SET_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <stdexcept>

#include "feature/AbstractFeatureDataStore2.h"
#include "feature/FeatureDataStore.h"
#include "port/String.h"


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
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::FeatureSet
///
///     A grouping of map features.
///
///=============================================================================


class FeatureSet
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FeatureSet(int64_t id,
               unsigned long version,
               const char* provider,
               const char* type,
               const char* name,
               double minResolution = 0.0,     // No minimum.
               double maxResolution = 0.0);    // No maximum.

    //
    // The optional values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if provider, type, or name is NULL or if
    // minResolution or maxResolution is negative.
    //
    FeatureSet (const char* provider,
                const char* type,
                const char* name,
                double minResolution = 0.0,     // No minimum.
                double maxResolution = 0.0);    // No maximum.

    ~FeatureSet ()
        NOTHROWS;

    //
    // The compiler-generated copy constructor is acceptable.  The compiler
    // cannot generate an assignment operator (due to a reference data member).
    // This is acceptable.
    //

    FeatureDataStore::FeatureCursor*
    getFeatures ()
        const
        NOTHROWS;

    int64_t
    getID ()
        const
        NOTHROWS;

    //
    // Returns the ground sample distance (in meters/pixel) of the "highest
    // resolution" at which the features should be displayed.  A value of 0.0
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
        NOTHROWS;

    //
    // Returns the ground sample distance (in meters/pixel) of the "lowest
    // resolution" at which the features should be displayed.  A value of 0.0
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
        NOTHROWS;

    const char*
    getName ()
        const
        NOTHROWS;

    const char*
    getProvider ()
        const
        NOTHROWS;

    const char*
    getType ()
        const
        NOTHROWS;

    unsigned long
    getVersion ()
        const
        NOTHROWS;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class FeatureDataStore;
    friend class TAK::Engine::Feature::AbstractFeatureDataStore2;

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef FeatureDataStore::FeatureQueryParameters    QueryParams;
    typedef std::shared_ptr<QueryParams>  QueryParamsPtr;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    int64_t ID;
    unsigned long version;
    const FeatureDataStore* owner;
    const TAK::Engine::Port::String provider;
    const TAK::Engine::Port::String type;
    const TAK::Engine::Port::String name;
    const double minResolution;
    const double maxResolution;
    mutable QueryParamsPtr queryParams;
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


#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_SET_H_INCLUDED
