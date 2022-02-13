////============================================================================
////
////    FILE:           LineString.cpp
////
////    DESCRIPTION:    Implementation of LineString class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 10, 2014  scott           Created.
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


#include "feature/LineString.h"

#include <cstdint>
#include <ostream>

#include "util/IO.h"


#define MEM_FN( fn )    "atakmap::feature::LineString::" fn ": "


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

void
LineString::addPoint (double x,
                      double y)
  {
    points.push_back (x);
    points.push_back (y);
    if (getDimension () == _3D)
      {
        points.push_back (0);
      }
    ++count;
  }

void
LineString::addPoint (double x,
                      double y,
                      double z)
    throw (std::out_of_range)
  {
    if (getDimension () == _2D)
      {
        throw std::out_of_range (MEM_FN ("addPoint")
                                 "Can't add 3D point to 2D string");
      }

    points.push_back (x);
    points.push_back (y);
    points.push_back (z);
    ++count;
  }

void
LineString::addPoint (Point point)
    throw (std::out_of_range)
  {
    if (point.getDimension () == _3D && getDimension () == _2D)
      {
        throw std::out_of_range (MEM_FN ("addPoint")
                                 "Can't add 3D point to 2D string");
      }

    points.push_back (point.x);
    points.push_back (point.y);
    if (getDimension () == _3D)
      {
        points.push_back (point.z);     // Should be 0 if point is _2D.
      }
    ++count;
  }

Point
LineString::getPoint (std::size_t index)
    const
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("getX") "Index out of range");
      }

    auto iter
        (points.begin () + index * getDimension ());

    return getDimension () == _2D
        ? Point (*iter, *(iter + 1))
        : Point (*iter, *(iter + 1), *(iter + 2));
  }

double
LineString::getX (std::size_t index)
    const
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("getX") "Index out of range");
      }

    return points[index * getDimension ()];
  }



double
LineString::getY (std::size_t index)
    const
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("getY") "Index out of range");
      }

    return points[index * getDimension () + 1];
  }

double
LineString::getZ (std::size_t index)
    const
    throw (std::out_of_range)
  {
    if (getDimension () == _2D)
      {
        throw std::out_of_range (MEM_FN ("getZ") "No Z values in 2D string");
      }
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("getZ") "Index out of range");
      }

    return points[index * getDimension () + 2];
  }

void
LineString::setPoint (std::size_t index,
                      const Point& point)
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("setPoint") "Index out of range");
      }
    if (point.getDimension () == _3D && getDimension () == _2D)
      {
        throw std::out_of_range (MEM_FN ("setPoint")
                                 "Can't set 3D point in 2D string");
      }

    auto iter
        (points.begin () + index * getDimension ());

    *iter = point.x;
    *++iter = point.y;
    if (getDimension () == _3D)
      {
        *++iter = point.z;              // Should be 0 if point is _2D.
      }
  }

void
LineString::setX (std::size_t index,
                  double value)
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("setX") "Index out of range");
      }

    points[index * getDimension ()] = value;
  }

void
LineString::setY (std::size_t index,
                  double value)
    throw (std::out_of_range)
  {
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("setY") "Index out of range");
      }

    points[index * getDimension () + 1] = value;
  }

void
LineString::setZ (std::size_t index,
                  double value)
    throw (std::out_of_range)
  {
    if (getDimension () == _2D)
      {
        throw std::out_of_range (MEM_FN ("setZ") "No Z values in 2D string");
      }
    if (index >= count)
      {
        throw std::out_of_range (MEM_FN ("setZ") "Index out of range");
      }

    points[index * getDimension () + 2] = value;
  }


void
LineString::addPoints (const double* begin,
                       const double* end,
                       Dimension dim)
    throw (std::invalid_argument,
           std::out_of_range)
  {
    if (dim > getDimension ())
      {
        throw std::out_of_range (MEM_FN ("addPoints")
                                 "Can't add 3D points to 2D string");
      }

    std::ptrdiff_t coordCount (end - begin);

    if (coordCount < 0 || coordCount % dim != 0)
      {
        throw std::invalid_argument (MEM_FN ("addPoints")
                                     "Invalid coordinate range");
      }
    if (dim == getDimension ())
      {
        points.insert<const double*> (points.end (), begin, end);
      }
    else                                // dim == _2D, dimension == _3D
      {
        //
        // Need to append 0 for z values.
        //

        while (begin < end)
          {
            points.push_back (*begin++);
            points.push_back (*begin++);
            points.push_back (0);
          }
      }
    count += (coordCount / dim);
  }


bool
LineString::isClosed ()
    const
    NOTHROWS
  {
    bool closed (false);

    if (count)
      {
        auto head (points.begin ());
        auto tail (points.end () - getDimension ());

        closed = getDimension () == _2D
            ? *head == *tail && *++head == *++tail
            : *head == *tail && *++head == *++tail && *++head ==*++tail;
      }

    return closed;
  }


///
///  atakmap::feature::Geometry member functions.
///


Envelope
LineString::getEnvelope ()
    const
  {
    if (points.empty ())
      {
        throw std::length_error (MEM_FN ("getEnvelope") "Empty line string");
      }

    auto iter (points.begin ());
    double minX (*iter++);
    double minY (*iter++);
    double minZ (getDimension () == _3D ? *iter++ : 0);
    double maxX (minX);
    double maxY (minY);
    double maxZ (minZ);
    const std::vector<double>::const_iterator end (points.end ());

    if (getDimension () == _2D)
      {
        while (iter != end)
          {
            if (*iter < minX)           { minX = *iter; }
            else if (*iter > maxX)      { maxX = *iter; }
            iter++;
            if (*iter < minY)           { minY = *iter; }
            else if (*iter > maxY)      { maxY = *iter; }
            iter++;
          }
      }
    else
      {
        while (iter != end)
          {
            if (*iter < minX)           { minX = *iter; }
            else if (*iter > maxX)      { maxX = *iter; }
            iter++;
            if (*iter < minY)           { minY = *iter; }
            else if (*iter > maxY)      { maxY = *iter; }
            iter++;
            if (*iter < minZ)           { minZ = *iter; }
            else if (*iter > maxZ)      { maxZ = *iter; }
            iter++;
          }
      }

    return Envelope (minX, minY, minZ, maxX, maxY, maxZ);
  }


void
LineString::toBlob (std::ostream& strm,
                    BlobFormat format)
    const
  {
    Dimension dim (getDimension ());

    switch (format)
      {
      case GEOMETRY:

        insertBlobHeader (strm, getEnvelope ());
        util::write<uint32_t> (strm, dim == _2D ? 2 : 1002);
        break;

      case ENTITY:

        strm.put (ENTITY_START_BYTE);
        util::write<uint32_t> (strm, dim == _2D ? 2 : 1002);
        break;

      default:

        break;
      }

    util::write<uint32_t> (strm, static_cast<uint32_t>(count));
    strm.write (reinterpret_cast<const char*> (&*points.begin ()),
                points.size () * sizeof (double));

    if (format == GEOMETRY)
      {
        strm.put (BLOB_END_BYTE);
      }
  }


void
LineString::toWKB (std::ostream& strm,
                   bool includeHeader)
    const
  {
    if (includeHeader)
      {
        util::write<uint32_t> (strm.put (util::ENDIAN_BYTE),
                               getDimension () == _2D ? 2 : 1002);
      }
    util::write<uint32_t> (strm, static_cast<uint32_t>(count));
    strm.write (reinterpret_cast<const char*> (&*points.begin ()),
                points.size () * sizeof (double));
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
LineString::changeDimension (Dimension dim)
  {
    Dimension dimension (getDimension ());

    if (dim < dimension)                // Change from 3D to 2D.
      {
        auto dst (points.begin () + _2D);
        std::vector<double>::const_iterator src (points.begin () + _3D);
        const std::vector<double>::const_iterator end (points.end ());

        while (src != end)
          {
            *dst++ = *src++;
            *dst++ = *src++;
            ++src;                      // Skip the Z value.
          }
        points.resize (count * dim);
      }
    else if (dim > dimension)           // Change from 2D to 3D.
      {
        std::vector<double> newPoints (count * dim);
        auto dst (newPoints.begin ());
        std::vector<double>::const_iterator src (points.begin ());
        const std::vector<double>::const_iterator end (points.end ());

        while (src != end)
          {
            *dst++ = *src++;
            *dst++ = *src++;
            ++dst;                      // Skip the 0.0 Z value.
          }
        points.swap (newPoints);
      }
  }


///
///  atakmap::feature::Geometry member functions.
///


std::size_t
LineString::computeWKB_Size()
    const
  {
    return util::WKB_HEADER_SIZE
        + sizeof(uint32_t)
        + sizeof(double) * points.size();
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
