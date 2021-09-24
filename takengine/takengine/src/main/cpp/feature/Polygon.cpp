////============================================================================
////
////    FILE:           Polygon.cpp
////
////    DESCRIPTION:    Implementation of Polygon class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 11, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/Polygon.h"

#include <algorithm>
#include <functional>
#include <numeric>
#include <ostream>

#include "util/IO.h"


#define MEM_FN( fn )    "atakmap::feature::Polygon::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

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


using namespace atakmap::feature;


inline
std::size_t
addWKB_Size (std::size_t sum,
             const LineString& lineString)
  { return sum + lineString.computeWKB_Size (); }


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



Polygon::Polygon (const LineString& exteriorRing)
  : Geometry (Geometry::POLYGON, exteriorRing.getDimension ())
  { addRing (exteriorRing); }


Polygon::Polygon (const LineString& exteriorRing,
                  const std::vector<LineString>& interiorRings)
  : Geometry (Geometry::POLYGON, exteriorRing.getDimension ())
  {
    addRing (exteriorRing);

	std::for_each(interiorRings.begin(),
		interiorRings.end(),
		std::bind(&Polygon::addRing, this, std::placeholders::_1));
  }

Polygon::~Polygon ()
    NOTHROWS
    { }

void
Polygon::addRing (const LineString& ring)
  {
#if 0
    // XXX - see US Special Use Air Space.kml
    if (!ring.isClosed ())
      {
        throw std::invalid_argument (MEM_FN ("addRing")
                                     "Received open LineString");
      }
#endif
    if (ring.getDimension () != getDimension ())
      {
        throw std::invalid_argument (MEM_FN ("addRing")
                                     "Inconsistent dimensions");
      }

    rings.push_back (ring);
  }


LineString
Polygon::getExteriorRing ()
    const
    throw (std::length_error)
  {
    if (rings.empty ())
      {
        throw std::length_error (MEM_FN ("getExteriorRing") "Polygon is empty");
      }

    return *rings.begin ();
  }


std::pair<std::vector<LineString>::const_iterator,
          std::vector<LineString>::const_iterator>
Polygon::getInteriorRings ()
    const
  {
    return std::make_pair (rings.empty () ? rings.begin () : ++rings.begin (),
                           rings.end ());
  }


///
///  atakmap::feature::Geometry member functions.
///

Envelope
Polygon::getEnvelope ()
    const
  {
    if (rings.empty ())
      {
        throw std::length_error (MEM_FN ("getEnvelope") "Polygon is empty");
      }

    return rings.begin ()->getEnvelope ();
  }


std::size_t
Polygon::computeWKB_Size ()
    const
  {
    return std::accumulate (rings.begin (),
                            rings.end (),
                            util::WKB_HEADER_SIZE + sizeof (uint32_t),
                            addWKB_Size)
        - util::WKB_HEADER_SIZE * rings.size ();
  }


void
Polygon::toBlob (std::ostream& strm,
                 BlobFormat format)
    const
  {
    switch (format)
      {
      case GEOMETRY:

        insertBlobHeader (strm, getEnvelope ());
        util::write<uint32_t> (strm, getDimension () == _2D ? 3 : 1003);
        break;

      case ENTITY:

        strm.put (ENTITY_START_BYTE);
        util::write<uint32_t> (strm, getDimension () == _2D ? 3 : 1003);
        break;

      default:

        break;
      }

    util::write<uint32_t> (strm, static_cast<uint32_t>(rings.size ()));

    const std::vector<LineString>::const_iterator end (rings.end ());

    for (auto iter (rings.begin ());
         iter != end;
         ++iter)
      {
        iter->toBlob (strm, INTERNAL);
      }

    if (format == GEOMETRY)
      {
        strm.put (BLOB_END_BYTE);
      }
  }


void
Polygon::toWKB (std::ostream& strm,
                bool includeHeader)
    const
  {
    if (includeHeader)
      {
        util::write<uint32_t> (strm.put (util::ENDIAN_BYTE),
                               getDimension () == _2D ? 3 : 1003);
      }
    util::write<uint32_t> (strm, static_cast<uint32_t>(rings.size ()));

    const std::vector<LineString>::const_iterator end (rings.end ());

    for (auto iter (rings.begin ());
         iter != end;
         ++iter)
      {
        iter->toWKB (strm, false);
      }
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


///
///  atakmap::feature::Geometry member functions.
///


void
Polygon::changeDimension (Dimension dim)
  {
    std::for_each (rings.begin (),
                   rings.end (),
                   std::bind2nd (std::mem_fun_ref (&LineString::setDimension),
                                 dim));
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
