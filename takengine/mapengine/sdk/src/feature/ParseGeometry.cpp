////============================================================================
////
////    FILE:           ParseGeometry.cpp
////
////    DESCRIPTION:    Implementation of Geometry parsing utility functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 16, 2014  scott           Created.
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


#include "feature/ParseGeometry.h"

#include <memory>
#include <sstream>

#include "feature/Geometry.h"
#include "feature/GeometryCollection.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"

#include "util/IO.h"


#define MEM_FN( fn )    "atakmap::feature::ParseGeometry::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


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


///
///  Forward declarations.
///


feature::LineString*
parseSpatiaLiteLineString (std::istream&,
                           bool swapEndian,
                           feature::Geometry::Dimension,
                           bool hasMeasure,
                           bool isCompressed);

feature::Polygon*
parseSpatiaLitePolygon (std::istream&,
                        bool swapEndian,
                        feature::Geometry::Dimension,
                        bool hasMeasure,
                        bool isCompressed);

feature::Point*
parseWKB_Point (std::istream&,
                bool swapEndian,
                feature::Geometry::Dimension,
                bool hasMeasure);


///
///  Function definitions.
///


template <typename T>
void
deleteVector (T* vec)
  { delete[] vec; }


feature::Geometry*
parseSpatiaLiteGeometry (std::istream& strm,
                         bool swapEndian,
                         feature::Geometry::Dimension dim,
                         int typeRestriction,
                         bool hasMeasure,
                         bool isCompressed)
  {
    feature::Geometry* result (nullptr);

    if ((strm.get () & 0xFF) != 0x69)
      {
        throw util::IO_Error (MEM_FN ("parseSpatiaLiteGeometry")
                              "Bad ENTITY byte");
      }

    int type (util::read<int32_t> (strm, swapEndian));

    if (typeRestriction && type != typeRestriction)
      {
        throw util::IO_Error (MEM_FN ("parseSpatiaLiteGeometry")
                              "Incorrect collection entity type");
      }
    switch (type % 1000)
      {
      case 1:                           // Point (SpatiaLite is same as WKB)

        result = parseWKB_Point (strm, swapEndian, dim, hasMeasure);
        break;

      case 2:                           // LineString

        result = parseSpatiaLiteLineString (strm, swapEndian, dim,
                                            hasMeasure, isCompressed);
        break;

      case 3:                           // Polygon

        result = parseSpatiaLitePolygon (strm, swapEndian, dim,
                                         hasMeasure, isCompressed);
        break;

      default:

        throw util::IO_Error (MEM_FN ("parseSpatiaLiteGeometry")
                              "Invalid collection entity type");
      }

    return result;
  }


feature::LineString*
parseSpatiaLiteLineString (std::istream& strm,
                           bool swapEndian,
                           feature::Geometry::Dimension dim,
                           bool hasMeasure,
                           bool isCompressed)
  {
    std::unique_ptr<feature::LineString> result (new feature::LineString (dim));
    std::size_t count (util::read<uint32_t> (strm, swapEndian));

    if (!isCompressed)
      {
        if (!hasMeasure)                // !isCompressed && !hasMeasure
          {
            std::unique_ptr<double, void(*)(double *)> pointBuff (new double[count * dim],
                                                       deleteVector);
            double* points (pointBuff.get ());

            if (!swapEndian)
              {
                if (!strm.read (reinterpret_cast<char*> (points),
                                count * dim * sizeof (double)))
                  {
                    throw util::IO_Error (MEM_FN ("parseSpatiaLiteLineString")
                                          "Read of double buffer failed");
                  }
              }
            else
              {
                std::size_t coordCount (count * dim);
                double* coord (points);

                while (coordCount--)
                  {
                    *coord++ = util::read<double> (strm, true);
                  }
              }
            result->addPoints (points, points + count * dim, dim);
          }
        else                            // !isCompressed && hasMeasure
          {
            switch (dim)
              {
              case feature::Geometry::_2D:

                for (size_t i (0); i < count; ++i)
                  {
                    auto x (util::read<double> (strm, swapEndian));

                    result->addPoint (x, util::read<double> (strm, swapEndian));
                    strm.ignore (8);
                  }
                break;

              case feature::Geometry::_3D:

                for (size_t i (0); i < count; ++i)
                  {
                    auto x (util::read<double> (strm, swapEndian));
                    auto y (util::read<double> (strm, swapEndian));

                    result->addPoint (x, y, util::read<double> (strm, swapEndian));
                    strm.ignore (8);
                  }
                break;
              }
          }
      }
    else                                // isCompressed
      {
        switch (dim)
          {
          case feature::Geometry::_2D:

            if (!hasMeasure)            // isCompressed && !hasMeasure
              {
                auto x (util::read<double> (strm, swapEndian));
                auto y (util::read<double> (strm, swapEndian));

                result->addPoint (x, y);

                for (size_t i (1); i < count; ++i)
                  {
                    x += util::read<float> (strm, swapEndian);
                    y += util::read<float> (strm, swapEndian);
                    result->addPoint (x, y);
                  }
              }
            else                        // isCompressed && hasMeasure
              {
                auto x (util::read<double> (strm, swapEndian));
                auto y (util::read<double> (strm, swapEndian));

                result->addPoint (x, y);
                strm.ignore (8);

                for (size_t i (1); i < count; ++i)
                  {
                    x += util::read<float> (strm, swapEndian);
                    y += util::read<float> (strm, swapEndian);
                    result->addPoint (x, y);
                    strm.ignore (4);
                  }
              }
            break;

          case feature::Geometry::_3D:

            if (!hasMeasure)            // isCompressed && !hasMeasure
              {
                auto x (util::read<double> (strm, swapEndian));
                auto y (util::read<double> (strm, swapEndian));
                auto z (util::read<double> (strm, swapEndian));

                result->addPoint (x, y, z);

                for (size_t i (1); i < count; ++i)
                  {
                    x += util::read<float> (strm, swapEndian);
                    y += util::read<float> (strm, swapEndian);
                    z += util::read<float> (strm, swapEndian);
                    result->addPoint (x, y, z);
                  }
              }
            else
              {
                auto x (util::read<double> (strm, swapEndian));
                auto y (util::read<double> (strm, swapEndian));
                auto z (util::read<double> (strm, swapEndian));

                result->addPoint (x, y, z);
                strm.ignore (8);

                for (size_t i (1); i < count; ++i)
                  {
                    x += util::read<float> (strm, swapEndian);
                    y += util::read<float> (strm, swapEndian);
                    z += util::read<float> (strm, swapEndian);
                    result->addPoint (x, y, z);
                    strm.ignore (4);
                  }
              }
            break;
          }
      }

    if (!strm)
      {
        throw util::IO_Error (MEM_FN ("parseSpatiaLiteLineString")
                              "Parse of LineString failed");
      }

    return result.release ();
  }


feature::Polygon*
parseSpatiaLitePolygon (std::istream& strm,
                        bool swapEndian,
                        feature::Geometry::Dimension dim,
                        bool hasMeasure,
                        bool isCompressed)
  {
    std::unique_ptr<feature::Polygon> result (new feature::Polygon (dim));
    std::size_t count (util::read<uint32_t> (strm, swapEndian));

    for (std::size_t i (0); i < count; ++i)
      {
        std::unique_ptr<feature::LineString> ring
            (parseSpatiaLiteLineString (strm, swapEndian, dim,
                                        hasMeasure, isCompressed));

        result->addRing (*ring);
      }

    if (!strm)
      {
        throw util::IO_Error (MEM_FN ("parseSpatiaLitePolygon")
                              "Parse of Polygon failed");
      }

    return result.release ();
  }


feature::LineString*
parseWKB_LineString (std::istream& strm,
                     bool swapEndian,
                     feature::Geometry::Dimension dim,
                     bool hasMeasure)
  {
    std::unique_ptr<feature::LineString> result (new feature::LineString (dim));
    std::size_t count (util::read<uint32_t> (strm, swapEndian));

    if (!hasMeasure)
      {
        std::unique_ptr<double, void(*)(double *)> pointBuff (new double[count * dim],
                                                   deleteVector);
        double* points (pointBuff.get ());

        if (!swapEndian)
          {
            if (!strm.read (reinterpret_cast<char*> (points),
                            count * dim * sizeof (double)))
              {
                throw util::IO_Error (MEM_FN ("parseWKB_LineString")
                                      "Read of double buffer failed");
              }
          }
        else
          {
            std::size_t coordCount (count * dim);
            double* coord (points);

            while (coordCount--)
              {
                *coord++ = util::read<double> (strm, true);
              }
          }
        result->addPoints (points, points + count * dim, dim);
      }
    else
      {
        switch (dim)
          {
          case feature::Geometry::_2D:

            for (size_t i (0); i < count; ++i)
              {
                auto x (util::read<double> (strm, swapEndian));

                result->addPoint (x, util::read<double> (strm, swapEndian));
                strm.ignore (8);
              }
            break;

          case feature::Geometry::_3D:

            for (size_t i (0); i < count; ++i)
              {
                auto x (util::read<double> (strm, swapEndian));
                auto y (util::read<double> (strm, swapEndian));

                result->addPoint (x, y, util::read<double> (strm, swapEndian));
                strm.ignore (8);
              }
            break;
          }
      }

    if (!strm)
      {
        throw util::IO_Error (MEM_FN ("parseWKB_LineString")
                              "Parse of LineString failed");
      }

    return result.release ();
  }


feature::Point*
parseWKB_Point (std::istream& strm,
                bool swapEndian,
                feature::Geometry::Dimension dim,
                bool hasMeasure)
  {
    auto x (util::read<double> (strm, swapEndian));
    auto y (util::read<double> (strm, swapEndian));
    feature::Point* result
        (dim == feature::Geometry::_2D
             ? new feature::Point (x, y)
             : new feature::Point (x, y, util::read<double> (strm, swapEndian)));

    if (hasMeasure)
      {
        strm.ignore (8);
      }

    if (!strm)
      {
        throw util::IO_Error (MEM_FN ("parseWKB_Point") "Parse of Point failed");
      }

    return result;
  }


feature::Polygon*
parseWKB_Polygon (std::istream& strm,
                  bool swapEndian,
                  feature::Geometry::Dimension dim,
                  bool hasMeasure)
  {
    std::unique_ptr<feature::Polygon> result (new feature::Polygon (dim));
    std::size_t count (util::read<uint32_t> (strm, swapEndian));

    for (std::size_t i (0); i < count; ++i)
      {
        std::unique_ptr<feature::LineString> ring
            (parseWKB_LineString (strm, swapEndian, dim, hasMeasure));

        result->addRing (*ring);
      }

    if (!strm)
      {
        throw util::IO_Error (MEM_FN ("parseWKB_Polygon")
                              "Parse of Polygon failed");
      }

    return result.release ();
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


Geometry*
parseBlob (const ByteBuffer& buffer)
    throw (util::IO_Error)
  {
    if (!buffer.first)
        return nullptr;

    std::istringstream strm (std::string (buffer.first, buffer.second),
                             std::ios_base::in | std::ios_base::binary);

    return parseBlob (strm);
  }


Geometry*
parseBlob (std::istream& strm)
    throw (util::IO_Error)
  {
    using namespace util;

    Geometry* result (nullptr);

    if ((strm.get() & 0xFF) != 0x00)
      {
        throw util::IO_Error(MEM_FN("parseBlob") "Bad BLOB_START byte");
      }

    auto byteOrder (static_cast<unsigned char> (strm.get ()));

    if (byteOrder > 1)
      {
        throw util::IO_Error (MEM_FN ("parseBlob") "Invalid byte order");
      }
    byteOrder ^= ENDIAN_BYTE;           // True if endian swaps are needed.
    strm.ignore (36);                   // Skip SRID & MBR.
    if ((strm.get () & 0xFF) != 0x7C)
      {
        throw util::IO_Error (MEM_FN ("parseBlob") "Bad MBR_END byte");
      }

    bool swapEndian = byteOrder != 0;
    std::size_t type(read<uint32_t>(strm, swapEndian));
    Geometry::Dimension dim (type / 1000 & 1 ? Geometry::_3D : Geometry::_2D);
    bool hasMeasure (type / 1000 % 1000 > 1);
    bool isCompressed (type / 1000000 == 1);

    switch (type % 1000)
      {
      case 1:                           // Point (SpatiaLite is same as WKB)

        result = parseWKB_Point(strm, swapEndian, dim, hasMeasure);
        break;

      case 2:                           // LineString

        result = parseSpatiaLiteLineString(strm, swapEndian, dim,
                                            hasMeasure, isCompressed);
        break;

      case 3:                           // Polygon

        result = parseSpatiaLitePolygon(strm, swapEndian, dim,
                                         hasMeasure, isCompressed);
        break;

      case 4:                           // MultiPoint
      case 5:                           // MultiLineString
      case 6:                           // MultiPolygon
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count (read<uint32_t> (strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
                std::unique_ptr<Geometry> g
                    (parseSpatiaLiteGeometry (strm, swapEndian, dim,
                                              (type % 1000) - 3,
                                              hasMeasure,
                                              isCompressed));

                collection->add (g.get ());
              }
            result = collection.release ();
          }
        break;

      case 7:                           // GeometryCollection
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count(read<uint32_t>(strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
                std::unique_ptr<Geometry> g
                    (parseSpatiaLiteGeometry (strm, swapEndian, dim,
                                              0,    // unrestricted
                                              hasMeasure,
                                              isCompressed));

                collection->add (g.get ());
              }
            result = collection.release ();
          }
        break;

      default:
          {
            std::ostringstream errStrm;

            errStrm << MEM_FN ("parseBlob") "Invalid geometry type: "
                    << type;
            throw util::IO_Error (errStrm.str ().c_str ());
          }
      }

    return result;
  }


Geometry*
parseWKB (const ByteBuffer& buffer)
    throw (util::IO_Error)
  {
    std::istringstream strm (std::string (buffer.first, buffer.second),
                             std::ios_base::in | std::ios_base::binary);

    return parseWKB (strm);
  }

Geometry*
parseWKB (std::istream& strm)
    throw (util::IO_Error)
  {
    using namespace util;

    Geometry* result (nullptr);
    auto byteOrder (static_cast<unsigned char> (strm.get ()));

    if (byteOrder > 1)
      {
        throw util::IO_Error (MEM_FN ("parseWKB") "Invalid byte order");
      }
    byteOrder ^= ENDIAN_BYTE;           // True if endian swaps are needed.

    bool swapEndian = byteOrder != 0;
    std::size_t type(read<uint32_t>(strm, swapEndian));
    Geometry::Dimension dim (type / 1000 & 1 ? Geometry::_3D : Geometry::_2D);
    bool hasMeasure (type / 2000 > 0);

    switch (type % 1000)
      {
      case 1:                           // Point

        result = parseWKB_Point(strm, swapEndian, dim, hasMeasure);
        break;

      case 2:                           // LineString
      case 13:                          // Curve

        result = parseWKB_LineString(strm, swapEndian, dim, hasMeasure);
        break;

      case 3:                           // Polygon
      case 14 :                         // Surface
      case 17:                          // Triangle

        result = parseWKB_Polygon(strm, swapEndian, dim, hasMeasure);
        break;

      case 4:                           // MultiPoint
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count(read<uint32_t>(strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
#ifdef MSVC
                std::unique_ptr<Geometry> g
                    (parseWKB_Point (strm, swapEndian, dim, hasMeasure));

                collection->add (g.get ());
#else
                collection->add (parseWKB_Point (strm, byteOrder, dim,
                                                 hasMeasure));
#endif
              }
            result = collection.release ();
          }
        break;

      case 5:                           // MultiLineString
      case 11:                          // MultiCurve
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count(read<uint32_t>(strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
#ifdef MSVC
                std::unique_ptr<Geometry> g
                    (parseWKB_LineString (strm, swapEndian, dim, hasMeasure));

                collection->add (g.get ());
#else
                collection->add (parseWKB_LineString (strm, byteOrder, dim,
                                                      hasMeasure));

#endif
              }
            result = collection.release ();
          }
        break;

      case 6:                           // MultiPolygon
      case 15:                          // PolyhedralSurface
      case 16:                          // TIN
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count (read<uint32_t> (strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
                std::unique_ptr<Geometry> g
                    (parseWKB_Polygon (strm, swapEndian, dim, hasMeasure));

                collection->add (g.get ());
              }
            result = collection.release ();
          }
        break;

      case 7:                           // GeometryCollection
      case 12:                         // MultiSurface
          {
            std::unique_ptr<GeometryCollection> collection
                (new GeometryCollection (dim));
            std::size_t count(read<uint32_t>(strm, swapEndian));

            for (std::size_t i (0); i < count; ++i)
              {
                std::unique_ptr<Geometry> g (parseWKB (strm));

                collection->add (g.get ());
              }
            result = collection.release ();
          }
        break;

      default:
          {
            std::ostringstream errStrm;

            errStrm << MEM_FN ("parseWKB") "Invalid geometry type: " << type;
            throw util::IO_Error (errStrm.str ().c_str ());
          }
      }

    return result;
  }

//
// Parses a Geometry from the supplied string.
//
Geometry*
parseWKT(const char* input)
    throw (util::IO_Error)
  {
    throw util::IO_Error("atakmap::feature::parseWKT: Not supported");
  }

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


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
