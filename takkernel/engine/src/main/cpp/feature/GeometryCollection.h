////============================================================================
////
////    FILE:           GeometryCollection.h
////
////    DESCRIPTION:    Concrete class representing a collection of 2D or 3D
////                    geometric elements.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 12, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_GEOMETRY_COLLECTION_H_INCLUDED
#define ATAKMAP_FEATURE_GEOMETRY_COLLECTION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cstddef>
#include <stdexcept>
#include <utility>
#include <vector>

#include "feature/Geometry.h"
#include "port/Platform.h"

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
///  class atakmap::feature::GeometryCollection
///
///     An unordered collection of Geometry items.
///
///=============================================================================


class ENGINE_API GeometryCollection
  : public Geometry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef std::vector<Geometry *>  GeometryVector;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    explicit
    GeometryCollection (Dimension dim)
      : Geometry (Geometry::COLLECTION, dim)
      { }

    GeometryCollection(const GeometryCollection &other)
        : Geometry(Geometry::COLLECTION, other.getDimension())
    {
        std::pair<GeometryVector::const_iterator,
            GeometryVector::const_iterator> elems = other.contents();
        GeometryVector::const_iterator it;
        for (it = elems.first; it != elems.second; it++)
            this->add(*(*it));
    }

    ~GeometryCollection ()
        NOTHROWS
    { clear(); }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    //
    // Appends the supplied Geometry to the collection.  Ignores a NULL Geometry.
    // The supplied Geometry is adopted by the collection, but may be shared
    // using the returned reference.
    //
    // Throws std::invalid_argument element->getDimension() != getDimension().
    //
    Geometry *
    add (const Geometry*)
        throw (std::invalid_argument);

    //
    // Appends the supplied Geometry reference to the collection.  Ignores a
    // NULL reference.
    //
    // Throws std::invalid_argument element->getDimension() != getDimension().
    //
    Geometry *
    add (const Geometry&)
        throw (std::invalid_argument);

    void
    clear()
        NOTHROWS;

    std::pair<GeometryVector::const_iterator,
              GeometryVector::const_iterator>
    contents ()
        const
        NOTHROWS
      { return std::make_pair (elements.begin (), elements.end ()); }

    //
    // Removes the supplied Geometry reference from the collection.
    //
    void
    remove (const Geometry *)
        NOTHROWS;


    //==================================
    //  Geometry INTERFACE
    //==================================


    Geometry*
    clone ()
        const override
      { return new GeometryCollection (*this); }        // Shallow copy.

    std::size_t
    computeWKB_Size ()
        const override;

    //
    // Throws std::length_error if contents().first == contents().second.
    //
    Envelope
    getEnvelope ()
        const override;

    void
    toBlob (std::ostream&,
            BlobFormat)                 // Defaults to GEOMETRY.
        const override;

    void
    toWKB (std::ostream&,
           bool includeHeader)          // Defaults to true.
        const override;

	size_t
	size ()
		const
	{ return elements.size(); }

	bool
	empty ()
		const
	{ return elements.empty(); }

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  Geometry INTERFACE
    //==================================


    void
    changeDimension (Dimension) override;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    GeometryVector elements;
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

#endif  // #ifndef ATAKMAP_FEATURE_GEOMETRY_COLLECTION_H_INCLUDED
