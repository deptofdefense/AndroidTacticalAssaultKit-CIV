////============================================================================
////
////    FILE:           GeometryCollection.cpp
////
////    DESCRIPTION:    Implementation of GeometryCollection class.
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


#include "feature/GeometryCollection.h"

#include <algorithm>
#include <cstdint>
#include <functional>
#include <numeric>
#include <ostream>

#include "util/IO.h"


#define MEM_FN( fn )    "atakmap::feature::GeometryCollection::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap::feature;


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


struct DimensionSetter
  : std::unary_function<Geometry *, void>
  {
    DimensionSetter (Geometry::Dimension dim)
      : dim (dim)
      { }

    void
    operator() (Geometry *geometry)
        const
      { geometry->setDimension (dim); }

  private:

    Geometry::Dimension dim;
  };


}                                       // Close unnamed namespace.


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


inline
std::size_t
addWKB_Size (std::size_t sum,
             Geometry *geometry)
  { return sum + geometry->computeWKB_Size (); }


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

Geometry *
GeometryCollection::add (const Geometry* element)
    throw (std::invalid_argument)
  {
    Geometry *result (nullptr);

    if (element)
      {
        if (element->getDimension () != getDimension ())
          {
            throw std::invalid_argument (MEM_FN ("add")
                                         "Inconsistent dimensions");
          }
        result = element->clone();
        elements.push_back (result);
      }

    return result;
  }

Geometry *
GeometryCollection::add (const Geometry &element)
    throw (std::invalid_argument)
  {
    Geometry *result(nullptr);
    if (&element)
      {
        if (element.getDimension () != getDimension ())
          {
            throw std::invalid_argument (MEM_FN ("add")
                                         "Inconsistent dimensions");
          }
        result = element.clone();
        elements.push_back (result);
      }

    return result;
  }

void
GeometryCollection::clear()
    NOTHROWS
  {
    GeometryVector::iterator it;
    for (it = elements.begin(); it != elements.end(); it++)
        delete *it;
    elements.clear();
  }

void
GeometryCollection::remove (const Geometry *ref)
    NOTHROWS
  { elements.erase (std::remove (elements.begin (), elements.end (), ref)); }


///
///  atakmap::feature::Geometry member functions.
///


std::size_t
GeometryCollection::computeWKB_Size ()
    const
  {
    return std::accumulate (elements.begin (),
                            elements.end (),
                            util::WKB_HEADER_SIZE + sizeof (uint32_t),
                            addWKB_Size);
  }


Envelope
GeometryCollection::getEnvelope ()
    const
  {
    if (elements.empty ())
      {
        throw std::length_error (MEM_FN ("getEnvelope") "Empty collection");
      }

    const GeometryVector::const_iterator end (elements.end ());
    auto iter (elements.begin ());
    Envelope env ((*iter)->getEnvelope ());
    double minX (env.minX);
    double minY (env.minY);
    double minZ (env.minZ);
    double maxX (env.maxX);
    double maxY (env.maxY);
    double maxZ (env.maxZ);

    while (++iter != end)
      {
        env = (*iter)->getEnvelope ();
        minX = std::min (minX, env.minX);
        minY = std::min (minY, env.minY);
        minZ = std::min (minZ, env.minZ);
        maxX = std::max (maxX, env.maxX);
        maxY = std::max (maxY, env.maxY);
        maxZ = std::max (maxZ, env.maxZ);
      }

    return Envelope (minX, minY, minZ, maxX, maxY, maxZ);
  }


void
GeometryCollection::toBlob (std::ostream& strm,
                            BlobFormat) // Can only be GEOMETRY.
    const
  {
    insertBlobHeader (strm, getEnvelope ());
    util::write<uint32_t> (strm, getDimension () == _2D ? 7 : 1007);
    util::write<uint32_t> (strm, static_cast<uint32_t>(elements.size ()));

    const GeometryVector::const_iterator end (elements.end ());

    for (auto iter (elements.begin ());
         iter != end;
         ++iter)
      {
        (*iter)->toBlob (strm, ENTITY);
      }
    strm.put (BLOB_END_BYTE);
  }


void
GeometryCollection::toWKB (std::ostream& strm,
                           bool includeHeader)
    const
  {
    if (includeHeader)
      {
        util::write<uint32_t> (strm.put (util::ENDIAN_BYTE),
                               getDimension () == _2D ? 7 : 1007);
      }
    util::write<uint32_t> (strm, static_cast<uint32_t>(elements.size ()));

    const GeometryVector::const_iterator end (elements.end ());

    for (auto iter (elements.begin ());
         iter != end;
         ++iter)
      {
        (*iter)->toWKB (strm);          // Include headers for each element.
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
GeometryCollection::changeDimension (Dimension dim)
  { std::for_each (elements.begin (), elements.end (), DimensionSetter (dim)); }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
