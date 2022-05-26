////============================================================================
////
////    FILE:           Point.cpp
////
////    DESCRIPTION:    Concrete class representing a 2D or 3D point.
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


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/Point.h"

#include "util/IO.h"

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


std::size_t
Point::computeWKB_Size ()
        const
  { return util::WKB_HEADER_SIZE + sizeof (double) * getDimension (); }

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC DEFINITIONS                                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{

void
Point::set (double x_val,
            double y_val)
    NOTHROWS
  {
    Point::x = x_val;
    Point::y = y_val;
    if (getDimension () == _3D)
      {
        z = 0;
      }
  }


void
Point::set (double x_val,
            double y_val,
            double z_val)
    throw (std::out_of_range)
  {
    if (getDimension () == _2D)
      {
        throw std::out_of_range ("atakmap::feature::Point::set: "
                                 "No Z value in 2D point");
      }
    Point::x = x_val;
    Point::y = y_val;
    Point::z = z_val;
  }

///
///  atakmap::feature::Geometry member functions.
///


void
Point::toBlob (std::ostream& strm,
               BlobFormat format)
    const
  {
    if (format == GEOMETRY)
      {
        insertBlobHeader (strm, getEnvelope ());
      }
    else                                // Format as collection entity.
      {
        strm.put (ENTITY_START_BYTE);
      }

    switch (getDimension ())
      {
      case _2D:

        util::write<uint32_t> (strm, 1);
        util::write (strm, x);
        util::write (strm, y);
        break;

      case _3D:

        util::write<uint32_t> (strm, 1001);
        util::write (strm, x);
        util::write (strm, y);
        util::write (strm, z);
        break;
      }

    if (format == GEOMETRY)
      {
        strm.put (BLOB_END_BYTE);
      }
  }


void
Point::toWKB (std::ostream& strm,
              bool includeHeader)
    const
  {
    if (includeHeader)
      {
        util::write<uint32_t> (strm.put (util::ENDIAN_BYTE),
                               getDimension () == _2D ? 1 : 1001);
      }
    util::write (strm, x);
    util::write (strm, y);
    if (getDimension () == _3D)
      {
        util::write (strm, z);
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
