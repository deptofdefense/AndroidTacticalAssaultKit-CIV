////============================================================================
////
////    FILE:           Feature.h
////
////    DESCRIPTION:    Definition of Feature class.
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


#ifndef ATAKMAP_FEATURE_FEATURE_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <memory>
#include <stdexcept>
#include <stdint.h>

#include "feature/AbstractFeatureDataStore2.h"
#include "feature/FeatureDataStore.h"
#include "feature/Geometry.h"
#include "feature/Style.h"
#include "port/String.h"
#include "util/AttributeSet.h"
#include "util/AtomicRefCountable.h"



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
///  class atakmap::feature::Feature
///
///     A map feature comprising immutable geometry, style, and attributes.
///
///=============================================================================


class Feature : public atakmap::util::AtomicRefCountable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Throws std::invalid_argument if name or Geometry is NULL.
    //
    Feature (int64_t ID,
             int64_t setID,
             unsigned long version,
             const char* name,
             Geometry*,                 // Adopted (and destroyed) by Feature.
             Style*,                    // Adopted (and destroyed) by Feature.
             const util::AttributeSet&);

    //
    // Throws std::invalid_argument if name or Geometry is NULL.
    //
    Feature (const char* name,
             Geometry*,                 // Adopted (and destroyed) by Feature.
             Style*,                    // Adopted (and destroyed) by Feature.
             const util::AttributeSet&);

    ~Feature()
        NOTHROWS;

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    const util::AttributeSet&
    getAttributes ()
        const
        NOTHROWS;

    int64_t
    getFeatureSetID ()
        const
        NOTHROWS;

    const Geometry&
    getGeometry ()
        const
        NOTHROWS;

    int64_t
    getID ()
        const
        NOTHROWS;

    const char*
    getName ()
        const
        NOTHROWS;

    const Style*
    getStyle ()
        const
        NOTHROWS;

    unsigned long
    getVersion ()
        const
        NOTHROWS;
      
      static bool isSame(const Feature &a, const Feature &b);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class FeatureDataStore;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    int64_t ID;
    int64_t setID;
    unsigned long version;
    TAK::Engine::Port::String name;
    std::shared_ptr<Geometry> geometry;
    std::shared_ptr<Style> style;
    util::AttributeSet attributes;
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

#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_H_INCLUDED
