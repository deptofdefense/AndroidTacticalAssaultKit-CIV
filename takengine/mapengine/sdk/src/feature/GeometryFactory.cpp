#include "feature/GeometryFactory.h"

#include <cstdlib>
#include <sstream>

#include "feature/LegacyAdapters.h"
#include "feature/ParseGeometry.h"
#include "feature/GeometryCollection2.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "feature/Polygon2.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

namespace
{
    TAKErr parseSpatiaLiteGeometry(Geometry2Ptr &value,
                                   DataInput2 &strm,
                                   const size_t dimension,
                                   const int typeRestriction,
                                   const bool hasMeasure,
                                   const bool isCompressed) NOTHROWS;
    TAKErr parseSpatiaLiteLineString(Geometry2Ptr &value,
                                     DataInput2 &strm,
                                     const std::size_t dimension,
                                     const bool hasMeasure,
                                     const bool isCompressed) NOTHROWS;
    TAKErr parseSpatiaLitePolygon(Geometry2Ptr &value,
                                  DataInput2 &strm,
                                  const std::size_t dim,
                                  const bool hasMeasure,
                                  const bool isCompressed) NOTHROWS;
    TAKErr parseWKB_LineString(Geometry2Ptr &value,
                               DataInput2 &strm,
                               const std::size_t dim,
                               const bool hasMeasure) NOTHROWS;
    TAKErr parseWKB_Point(Geometry2Ptr &value,
                          DataInput2 &strm,
                          const std::size_t dim,
                          const bool hasMeasure) NOTHROWS;

    TAKErr parseWKB_Polygon(Geometry2Ptr &value,
                            DataInput2 &strm,
                            const std::size_t dim,
                            const bool hasMeasure) NOTHROWS;

    TAKErr packWKB_writeHeader(DataOutput2 &strm,
                               const TAKEndian order,
                               const Geometry2 &geom,
                               const uint32_t baseType) NOTHROWS;
    TAKErr packWKB_Point(DataOutput2 &strm,
                         const TAKEndian order,
                         const Point2 &point) NOTHROWS;
    TAKErr packWKB_LineString(DataOutput2 &strm,
                              const TAKEndian order,
                              const LineString2 &linestring) NOTHROWS;
    TAKErr packWKB_writePoints(DataOutput2 &strm,
                               const LineString2 &linestring) NOTHROWS;
    TAKErr packWKB_Polygon(DataOutput2 &strm,
                           const TAKEndian order,
                           const Polygon2 &polygon) NOTHROWS;
    TAKErr packWKB_GeometryCollection(DataOutput2 &strm,
                                      const TAKEndian order,
                                      const GeometryCollection2 &c) NOTHROWS;

}

TAKErr TAK::Engine::Feature::GeometryFactory_fromWkb(Geometry2Ptr &value, DataInput2 &src) NOTHROWS
{
    TAKErr code(TE_Ok);
    //unsigned char byteOrder (static_cast<unsigned char> (strm.get ()));
    uint8_t byteOrder;
    code = src.readByte(&byteOrder);

    //if (byteOrder > 1)
    //  {
    //    throw util::IO_Error (MEM_FN ("parseWKB") "Invalid byte order");
    // }
    if (byteOrder > 1)
        return TE_InvalidArg;
    //byteOrder ^= ENDIAN_BYTE;           // True if endian swaps are needed.
    src.setSourceEndian2(byteOrder ? TE_LittleEndian : TE_BigEndian);

    //std::size_t type (read<uint32_t> (strm, byteOrder));
    int type;
    code = src.readInt(&type);
    TE_CHECKRETURN_CODE(code);

    //Geometry::Dimension dim (type / 1000 & 1 ? Geometry::_3D : Geometry::_2D);
    std::size_t dim = ((unsigned)type / 1000u & 1u ? 3u : 2u);
    const bool hasMeasure (type / 2000 > 0);

    switch (type % 1000)
      {
      case 1:                           // Point

        //result = parseWKB_Point (strm, byteOrder, dim, hasMeasure);
        code = parseWKB_Point(value, src, dim, hasMeasure);
        break;

      case 2:                           // LineString
      case 13:                          // Curve

        //result = parseWKB_LineString (strm, byteOrder, dim, hasMeasure);
        code = parseWKB_LineString(value, src, dim, hasMeasure);
        break;

      case 3:                           // Polygon
      case 14 :                         // Surface
      case 17:                          // Triangle

        //result = parseWKB_Polygon (strm, byteOrder, dim, hasMeasure);
        code = parseWKB_Polygon(value, src, dim, hasMeasure);
        break;

      case 4:                           // MultiPoint
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);
            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);

            for (std::size_t i (0); i < count; ++i)
              {
//#ifdef MSVC
//                std::auto_ptr<Geometry> g
//                    (parseWKB_Point (strm, byteOrder, dim, hasMeasure));//
//
//                collection->add (g.get ());
//#else
//                collection->add (parseWKB_Point (strm, byteOrder, dim,
//                                                 hasMeasure));
//#endif
                  Geometry2Ptr g(NULL, NULL);
                  code = parseWKB_Point(g, src, dim, hasMeasure);
                  TE_CHECKBREAK_CODE(code);

                  code = collection->addGeometry(std::move(g));
                  TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      case 5:                           // MultiLineString
      case 11:                          // MultiCurve
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);
            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);

            for (std::size_t i (0); i < count; ++i)
              {
//#ifdef MSVC
//                std::auto_ptr<Geometry> g
//                    (parseWKB_LineString (strm, byteOrder, dim, hasMeasure));
//
//                collection->add (g.get ());
//#else
//                collection->add (parseWKB_LineString (strm, byteOrder, dim,
//                                                      hasMeasure));
//
//#endif
                  Geometry2Ptr g(NULL, NULL);
                  code = parseWKB_LineString(g, src, dim, hasMeasure);
                  TE_CHECKBREAK_CODE(code);

                  code = collection->addGeometry(std::move(g));
                  TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      case 6:                           // MultiPolygon
      case 15:                          // PolyhedralSurface
      case 16:                          // TIN
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);
            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);

            for (std::size_t i (0); i < count; ++i)
              {
//                std::auto_ptr<Geometry> g
//                    (parseWKB_Polygon (strm, byteOrder, dim, hasMeasure));
                  Geometry2Ptr g(NULL, NULL);
                  code = parseWKB_Polygon(g, src, dim, hasMeasure);
                  TE_CHECKBREAK_CODE(code);

                  code = collection->addGeometry(std::move(g));
                  TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      case 7:                           // GeometryCollection
      case 12:                         // MultiSurface
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);
            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);

            for (std::size_t i (0); i < count; ++i)
              {
//                std::auto_ptr<Geometry> g (parseWKB (strm));
                  Geometry2Ptr g(NULL, NULL);
                  code = GeometryFactory_fromWkb(g, src);
                  TE_CHECKBREAK_CODE(code);

                  code = collection->addGeometry(std::move(g));
                  TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      default:
          {
            //std::ostringstream errStrm;
            //
            //errStrm << MEM_FN ("parseWKB") "Invalid geometry type: " << type;
            //throw util::IO_Error (errStrm.str ().c_str ());
            return TE_InvalidArg;
          }
      }

    //return result;
    return code;
}
TAKErr TAK::Engine::Feature::GeometryFactory_fromWkb(Geometry2Ptr &value, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS
{
    TAKErr code(TE_Ok);
    MemoryInput2 strm;
    code = strm.open(wkb, wkbLen);
    TE_CHECKRETURN_CODE(code);
    return GeometryFactory_fromWkb(value, strm);
}
TAKErr TAK::Engine::Feature::GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, Util::DataInput2 &src) NOTHROWS
{
    return GeometryFactory_fromSpatiaLiteBlob(value, NULL, src);
}
TAKErr TAK::Engine::Feature::GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS
{
    return GeometryFactory_fromSpatiaLiteBlob(value, NULL, wkb, wkbLen);
}
TAKErr TAK::Engine::Feature::GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, int *srid, Util::DataInput2 &src) NOTHROWS
{
    TAKErr code(TE_Ok);
    uint8_t octet;
    //if ((strm.get() & 0xFF) != 0x00)
    //  {
    //    throw util::IO_Error(MEM_FN("parseBlob") "Bad BLOB_START byte");
    //  }
    code = src.readByte(&octet);
    TE_CHECKRETURN_CODE(code);
    if ((octet & 0xFF) != 0x00)
        return TE_InvalidArg;

    //unsigned char byteOrder (static_cast<unsigned char> (strm.get ()));
    uint8_t byteOrder;
    code = src.readByte(&byteOrder);
    TE_CHECKRETURN_CODE(code);

    //if (byteOrder > 1)
    //  {
    //    throw util::IO_Error (MEM_FN ("parseBlob") "Invalid byte order");
    //  }
    if (byteOrder > 1u)
        return TE_InvalidArg;

    //byteOrder ^= ENDIAN_BYTE;           // True if endian swaps are needed.
    src.setSourceEndian2(byteOrder ? TE_LittleEndian : TE_BigEndian);
    //strm.ignore (36);                   // Skip SRID & MBR.
    if (srid) {
        code = src.readInt(srid);
        TE_CHECKRETURN_CODE(code);
    } else {
        code = src.skip(4u);
        TE_CHECKRETURN_CODE(code);
    }
    code = src.skip(32);
    TE_CHECKRETURN_CODE(code);

    //if ((strm.get () & 0xFF) != 0x7C)
    //  {
    //    throw util::IO_Error (MEM_FN ("parseBlob") "Bad MBR_END byte");
    //  }
    code = src.readByte(&octet);
    TE_CHECKRETURN_CODE(code);
    if ((octet & 0xFF) != 0x7C)
        return TE_InvalidArg;

    //std::size_t type (read<uint32_t> (strm, byteOrder));
    int type;
    code = src.readInt(&type);
    TE_CHECKRETURN_CODE(code);

    if (type < 0)
        return TE_InvalidArg;

    //Geometry::Dimension dim (type / 1000 & 1 ? Geometry::_3D : Geometry::_2D);
    const std::size_t dim = ((unsigned)type / 1000u & 1u ? 3u : 2u);
    const bool hasMeasure (type / 1000 % 1000 > 1);
    const bool isCompressed (type / 1000000 == 1);

    switch (type % 1000u)
      {
      case 1u:                           // Point (SpatiaLite is same as WKB)

        //result = parseWKB_Point (strm, byteOrder, dim, hasMeasure);
        code = parseWKB_Point(value, src, dim, hasMeasure);
        break;

      case 2u:                           // LineString

        //result = parseSpatiaLiteLineString (strm, byteOrder, dim,
        //                                    hasMeasure, isCompressed);
          code = parseSpatiaLiteLineString(value, src, dim, hasMeasure, isCompressed);
        break;

      case 3u:                           // Polygon

        //result = parseSpatiaLitePolygon (strm, byteOrder, dim,
        //                                 hasMeasure, isCompressed);
        code = parseSpatiaLitePolygon(value, src, dim, hasMeasure, isCompressed);
        break;

      case 4u:                           // MultiPoint
      case 5u:                           // MultiLineString
      case 6u:                           // MultiPolygon
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);

            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);
            if (count < 0)
                return TE_InvalidArg;

            for (std::size_t i (0); i < count; ++i)
              {
                //std::auto_ptr<Geometry> g
                //    (parseSpatiaLiteGeometry (strm, byteOrder, dim,
                //                              (type % 1000) - 3,
                //                              hasMeasure,
                //                              isCompressed));
                Geometry2Ptr g(NULL, NULL);
                code = parseSpatiaLiteGeometry(g, src, dim, (type % 1000) - 3, hasMeasure, isCompressed);
                TE_CHECKBREAK_CODE(code);

                //collection->add (g.get ());
                code = collection->addGeometry(std::move(g));
                TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      case 7u:                           // GeometryCollection
          {
            //std::auto_ptr<GeometryCollection> collection
            //    (new GeometryCollection (dim));
            std::unique_ptr<GeometryCollection2> collection(new GeometryCollection2());
            code = collection->setDimension(dim);
            TE_CHECKRETURN_CODE(code);

            //std::size_t count (read<uint32_t> (strm, byteOrder));
            int count;
            code = src.readInt(&count);
            TE_CHECKRETURN_CODE(code);
            if (count < 0)
                return TE_InvalidArg;

            for (std::size_t i (0); i < count; ++i)
              {
                //std::auto_ptr<Geometry> g
                //    (parseSpatiaLiteGeometry (strm, byteOrder, dim,
                //                              0,    // unrestricted
                //                              hasMeasure,
                //                              isCompressed));
                Geometry2Ptr g(NULL, NULL);
                code = parseSpatiaLiteGeometry(g, src, dim, (type % 1000) - 3, hasMeasure, isCompressed);
                TE_CHECKBREAK_CODE(code);

                //collection->add (g.get ());
                code = collection->addGeometry(std::move(g));
                TE_CHECKBREAK_CODE(code);
              }
            TE_CHECKRETURN_CODE(code);

            //result = collection.release ();
            value = Geometry2Ptr(collection.release(), Memory_deleter_const<Geometry2>);
          }
        break;

      default:
          {
            //std::ostringstream errStrm;
            //
            //errStrm << MEM_FN ("parseBlob") "Invalid geometry type: "
            //        << type;
            //throw util::IO_Error (errStrm.str ().c_str ());
            return TE_InvalidArg;
          }
      }

    //return result;
    return code;
}
TAKErr TAK::Engine::Feature::GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, int *srid, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS
{
    TAKErr code(TE_Ok);
    MemoryInput2 strm;
    code = strm.open(wkb, wkbLen);
    TE_CHECKRETURN_CODE(code);
    return GeometryFactory_fromSpatiaLiteBlob(value, srid, strm);
}

TAKErr TAK::Engine::Feature::GeometryFactory_toWkb(DataOutput2 &sink, const Geometry2 &geometry) NOTHROWS
{
    return GeometryFactory_toWkb(sink, geometry, TE_PlatformEndian);
}

TAKErr TAK::Engine::Feature::GeometryFactory_toWkb(DataOutput2 &sink, const Geometry2 &geometry, const TAKEndian endian) NOTHROWS
{
    // set the endian
    sink.setSourceEndian2(endian);

    switch(geometry.getClass()) {
        case TEGC_Point :
            return packWKB_Point(sink, endian, static_cast<const Point2 &>(geometry));
        case TEGC_LineString :
            return packWKB_LineString(sink, endian, static_cast<const LineString2 &>(geometry));
        case TEGC_Polygon :
            return packWKB_Polygon(sink, endian, static_cast<const Polygon2 &>(geometry));
        case TEGC_GeometryCollection :
            return packWKB_GeometryCollection(sink, endian, static_cast<const GeometryCollection2 &>(geometry));
        default :
            return TE_IllegalState;
    }
}

TAKErr TAK::Engine::Feature::GeometryFactory_toSpatiaLiteBlob(DataOutput2 &sink, const Geometry2 &geometry, const int srid) NOTHROWS
{
    return GeometryFactory_toSpatiaLiteBlob(sink, geometry, srid, TE_PlatformEndian);
}

TAKErr TAK::Engine::Feature::GeometryFactory_toSpatiaLiteBlob(DataOutput2 &sink, const Geometry2 &geometry, const int srid, const TAKEndian endian) NOTHROWS
{
    if (srid == 4326 && endian == TE_PlatformEndian) {
        TAKErr code(TE_Ok);
        atakmap::feature::UniqueGeometryPtr legacy(NULL, NULL);
        code = LegacyAdapters_adapt(legacy, geometry);
        TE_CHECKRETURN_CODE(code);

        try {
            std::ostringstream strstream;
            legacy->toBlob(strstream);
            std::string s = strstream.str();
            for (std::size_t i = 0u; i < s.length(); ++i) {
                code = sink.writeByte(s[i]);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        } catch (...) {
            return TE_Err;
        }

        return code;
    } else {
        return TE_Unsupported;
    }
}


namespace
{
    TAKErr parseSpatiaLiteGeometry(Geometry2Ptr &value,
                                   DataInput2 &strm,
                                   const size_t dim,
                                   const int typeRestriction,
                                   const bool hasMeasure,
                                   const bool isCompressed) NOTHROWS
    {
        TAKErr code(TE_Ok);
        //if ((strm.get() & 0xFF) != 0x69)
        uint8_t octet;
        code = strm.readByte(&octet);
        TE_CHECKRETURN_CODE(code);
        if((octet&0xFF) != 0x69)
        {
            //throw util::IO_Error(MEM_FN("parseSpatiaLiteGeometry")
            //    "Bad ENTITY byte");
            return TE_InvalidArg;
        }

        //int type(util::read<int32_t>(strm, swapEndian));
        int type;
        code = strm.readInt(&type);
        int typeModulo = type % 1000;
        TE_CHECKRETURN_CODE(code);

        if (typeRestriction && typeModulo != typeRestriction)
        {
            //throw util::IO_Error(MEM_FN("parseSpatiaLiteGeometry")
            //    "Incorrect collection entity type");
            return TE_InvalidArg;
        }
        switch (typeModulo)
        {
        case 1:                           // Point (SpatiaLite is same as WKB)

            //result = parseWKB_Point(strm, swapEndian, dim, hasMeasure);
            code = parseWKB_Point(value, strm, dim, hasMeasure);
            break;

        case 2:                           // LineString

            //result = parseSpatiaLiteLineString(strm, swapEndian, dim,
            //    hasMeasure, isCompressed);
            code = parseSpatiaLiteLineString(value, strm, dim, hasMeasure, isCompressed);
            break;

        case 3:                           // Polygon

            //result = parseSpatiaLitePolygon(strm, swapEndian, dim,
            //    hasMeasure, isCompressed);
            code = parseSpatiaLitePolygon(value, strm, dim, hasMeasure, isCompressed);
            break;

        default:

            //throw util::IO_Error(MEM_FN("parseSpatiaLiteGeometry")
            //    "Invalid collection entity type");
            return TE_InvalidArg;
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }


    TAKErr parseSpatiaLiteLineString(Geometry2Ptr &value,
                                     DataInput2 &strm,
                                     const std::size_t dim,
                                     const bool hasMeasure,
                                     const bool isCompressed) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<LineString2> result(new LineString2());
        code = result->setDimension(dim);
        TE_CHECKRETURN_CODE(code);
        //std::size_t count(util::read<uint32_t>(strm, swapEndian));
        int count;
        code = strm.readInt(&count);
        TE_CHECKRETURN_CODE(code);

        if (!isCompressed)
        {
            if (!hasMeasure)                // !isCompressed && !hasMeasure
            {
                //stlsoft::scoped_handle<double*> pointBuff(new double[count * dim],
                //    deleteVector);
                array_ptr<double> pointBuff(new double[count*dim]);
                double* points(pointBuff.get());

                if (strm.getSourceEndian() == TE_PlatformEndian)
                {
                    //if (!strm.read(reinterpret_cast<char*> (points),
                    //    count * dim * sizeof(double)))
                    //{
                    //    throw util::IO_Error(MEM_FN("parseSpatiaLiteLineString")
                    //        "Read of double buffer failed");
                    //}
                    std::size_t numRead;
                    code = strm.read(reinterpret_cast<uint8_t *>(points), &numRead, count * dim * sizeof(double));
                    TE_CHECKRETURN_CODE(code);

                    if (numRead < (count * dim * sizeof(double)))
                        return TE_EOF;
                }
                else
                {
                    std::size_t coordCount(count * dim);
                    double* coord(points);

                    //while (coordCount--)
                    //{
                    //    *coord++ = util::read<double>(strm, true);
                    //}
                    for (std::size_t i = 0u; i < coordCount; i++) {
                        code = strm.readDouble(coord + i);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                }

                //result->addPoints(points, points + count * dim, dim);
                code = result->addPoints(points, count, dim);
                TE_CHECKRETURN_CODE(code);
            }
            else                            // !isCompressed && hasMeasure
            {
                switch (dim)
                {
                case 2u:

                    for (size_t i(0); i < count; ++i)
                    {
                        //double x(util::read<double>(strm, swapEndian));

                        //result->addPoint(x, util::read<double>(strm, swapEndian));
                        //strm.ignore(8);
                        double x;
                        code = strm.readDouble(&x);
                        TE_CHECKBREAK_CODE(code);
                        double y;
                        code = strm.readDouble(&y);
                        TE_CHECKBREAK_CODE(code);

                        code = result->addPoint(x, y);
                        TE_CHECKBREAK_CODE(code);

                        code = strm.skip(8);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                    break;

                case 3u:

                    for (size_t i(0); i < count; ++i)
                    {
                        //double x(util::read<double>(strm, swapEndian));
                        //double y(util::read<double>(strm, swapEndian));

                        //result->addPoint(x, y, util::read<double>(strm, swapEndian));
                        //strm.ignore(8);
                        double x;
                        code = strm.readDouble(&x);
                        TE_CHECKBREAK_CODE(code);
                        double y;
                        code = strm.readDouble(&y);
                        TE_CHECKBREAK_CODE(code);
                        double z;
                        code = strm.readDouble(&z);
                        TE_CHECKBREAK_CODE(code);

                        code = result->addPoint(x, y, z);
                        TE_CHECKBREAK_CODE(code);

                        code = strm.skip(8);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                    break;
                default :
                    return TE_InvalidArg;
                }
            }
        }
        else                                // isCompressed
        {
            switch (dim)
            {
            //case feature::Geometry::_2D:
            case 2u:

                if (!hasMeasure)            // isCompressed && !hasMeasure
                {
                    //double x(util::read<double>(strm, swapEndian));
                    //double y(util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    code = strm.readDouble(&x);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKRETURN_CODE(code);

                    code = result->addPoint(x, y);
                    TE_CHECKRETURN_CODE(code);

                    for (size_t i(1); i < count; ++i)
                    {
                        //x += util::read<float>(strm, swapEndian);
                        //y += util::read<float>(strm, swapEndian);
                        float xoff;
                        float yoff;
                        code = strm.readFloat(&xoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&yoff);
                        TE_CHECKBREAK_CODE(code);
                        x += xoff;
                        y += yoff;
                        code = result->addPoint(x, y);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                }
                else                        // isCompressed && hasMeasure
                {
                    //double x(util::read<double>(strm, swapEndian));
                    //double y(util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    code = strm.readDouble(&x);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKRETURN_CODE(code);

                    code = result->addPoint(x, y);
                    TE_CHECKRETURN_CODE(code);

                    //strm.ignore(8);
                    code = strm.skip(8u);
                    TE_CHECKRETURN_CODE(code);

                    for (size_t i(1); i < count; ++i)
                    {
                        //x += util::read<float>(strm, swapEndian);
                        //y += util::read<float>(strm, swapEndian);
                        float xoff;
                        float yoff;
                        code = strm.readFloat(&xoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&yoff);
                        TE_CHECKBREAK_CODE(code);
                        x += xoff;
                        y += yoff;
                        code = result->addPoint(x, y);
                        TE_CHECKBREAK_CODE(code);
                        //strm.ignore(4);
                        code = strm.skip(4u);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                }
                break;

            //case feature::Geometry::_3D:
            case 3u:

                if (!hasMeasure)            // isCompressed && !hasMeasure
                {
                    //double x(util::read<double>(strm, swapEndian));
                    //double y(util::read<double>(strm, swapEndian));
                    //double z(util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    double z;

                    code = strm.readDouble(&x);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&z);
                    TE_CHECKRETURN_CODE(code);

                    code = result->addPoint(x, y, z);
                    TE_CHECKRETURN_CODE(code);

                    for (size_t i(1); i < count; ++i)
                    {
                        //x += util::read<float>(strm, swapEndian);
                        //y += util::read<float>(strm, swapEndian);
                        //z += util::read<float>(strm, swapEndian);
                        float xoff;
                        float yoff;
                        float zoff;
                        code = strm.readFloat(&xoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&yoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&zoff);
                        TE_CHECKBREAK_CODE(code);
                        x += xoff;
                        y += yoff;
                        z += zoff;

                        code = result->addPoint(x, y, z);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                }
                else
                {
                    //double x(util::read<double>(strm, swapEndian));
                    //double y(util::read<double>(strm, swapEndian));
                    //double z(util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    double z;

                    code = strm.readDouble(&x);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKRETURN_CODE(code);
                    code = strm.readDouble(&z);
                    TE_CHECKRETURN_CODE(code);

                    code = result->addPoint(x, y, z);
                    TE_CHECKRETURN_CODE(code);

                    //strm.ignore(8);
                    code = strm.skip(8u);
                    TE_CHECKRETURN_CODE(code);

                    for (size_t i(1); i < count; ++i)
                    {
                        //x += util::read<float>(strm, swapEndian);
                        //y += util::read<float>(strm, swapEndian);
                        //z += util::read<float>(strm, swapEndian);
                        float xoff;
                        float yoff;
                        float zoff;
                        code = strm.readFloat(&xoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&yoff);
                        TE_CHECKBREAK_CODE(code);
                        code = strm.readFloat(&zoff);
                        TE_CHECKBREAK_CODE(code);
                        x += xoff;
                        y += yoff;
                        z += zoff;

                        code = result->addPoint(x, y, z);
                        TE_CHECKBREAK_CODE(code);

                        code = strm.skip(4u);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                }
                break;

            default :
                return TE_InvalidArg;
            }
        }

        //if (!strm)
        //{
        //    throw util::IO_Error(MEM_FN("parseSpatiaLiteLineString")
        //        "Parse of LineString failed");
        //}

        //return result.release();
        value = Geometry2Ptr(result.release(), Memory_deleter_const<Geometry2>);
        return code;
    }


    TAKErr parseSpatiaLitePolygon(Geometry2Ptr &value,
                                  DataInput2 &strm,
                                  const std::size_t dim,
                                  const bool hasMeasure,
                                  const bool isCompressed) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<Polygon2> result;

        //std::size_t count(util::read<uint32_t>(strm, swapEndian));
        int count;
        code = strm.readInt(&count);
        TE_CHECKRETURN_CODE(code);

        if (count < 1)
            return TE_InvalidArg;

        Geometry2Ptr exteriorRing(NULL, NULL);
        code = parseSpatiaLiteLineString(exteriorRing, strm, dim, hasMeasure, isCompressed);
        TE_CHECKRETURN_CODE(code);

        result.reset(new Polygon2(static_cast<LineString2 &>(*exteriorRing)));

        for (std::size_t i(1); i < count; ++i)
        {
            //std::auto_ptr<feature::LineString> ring
            //(parseSpatiaLiteLineString(strm, swapEndian, dim,
            //    hasMeasure, isCompressed));
            Geometry2Ptr ring(NULL, NULL);
            code = parseSpatiaLiteLineString(ring, strm, dim, hasMeasure, isCompressed);
            TE_CHECKBREAK_CODE(code);

            //result->addRing(*ring);
            code = result->addInteriorRing(static_cast<LineString2 &>(*ring));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        //if (!strm)
        //{
        //    throw util::IO_Error(MEM_FN("parseSpatiaLitePolygon")
        //        "Parse of Polygon failed");
        //}

        //return result.release();
        value = Geometry2Ptr(result.release(), Memory_deleter_const<Geometry2>);

        return code;
    }


    TAKErr parseWKB_LineString(Geometry2Ptr &value,
                               DataInput2 &strm,
                               const std::size_t dim,
                               const bool hasMeasure) NOTHROWS
    {
        TAKErr code(TE_Ok);
        //std::auto_ptr<feature::LineString> result(new feature::LineString(dim));
        std::unique_ptr<LineString2> result(new LineString2());
        code = result->setDimension(dim);
        TE_CHECKRETURN_CODE(code);
        //std::size_t count(util::read<uint32_t>(strm, swapEndian));

        int count;
        code = strm.readInt(&count);
        TE_CHECKRETURN_CODE(code);

        if (!hasMeasure)
        {
            //stlsoft::scoped_handle<double*> pointBuff(new double[count * dim],
            //    deleteVector);
            array_ptr<double> pointBuff(new double[count*dim]);
            double* points(pointBuff.get());

            //if (!swapEndian)
            if(strm.getSourceEndian() == TE_PlatformEndian)
            {
                //if (!strm.read(reinterpret_cast<char*> (points),
                //    count * dim * sizeof(double)))
                //{
                //    throw util::IO_Error(MEM_FN("parseWKB_LineString")
                //        "Read of double buffer failed");
                //}
                std::size_t numRead;
                code = strm.read(reinterpret_cast<uint8_t *>(points), &numRead, count * dim * sizeof(double));
                TE_CHECKRETURN_CODE(code);

                if (numRead != (count*dim * sizeof(double)))
                    return TE_EOF;
            }
            else
            {
                std::size_t coordCount(count * dim);
                double* coord(points);

                //while (coordCount--)
                //{
                //    *coord++ = util::read<double>(strm, true);
                //}
                for (std::size_t i = 0u; i < coordCount; i++) {
                    code = strm.readDouble(points + i);
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKRETURN_CODE(code);
            }
            //result->addPoints(points, points + count * dim, dim);
            code = result->addPoints(points, count, dim);
            TE_CHECKRETURN_CODE(code);
        }
        else
        {
            switch (dim)
            {
            //case feature::Geometry::_2D:
            case 2u :

                for (size_t i(0); i < count; ++i)
                {
                    //double x(util::read<double>(strm, swapEndian));

                    //result->addPoint(x, util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    code = strm.readDouble(&x);
                    TE_CHECKBREAK_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKBREAK_CODE(code);
                    code = result->addPoint(x, y);
                    TE_CHECKBREAK_CODE(code);
                    //strm.ignore(8);
                    code = strm.skip(8u);
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKRETURN_CODE(code);
                break;

            //case feature::Geometry::_3D:
            case 3u :

                for (size_t i(0); i < count; ++i)
                {
                    //double x(util::read<double>(strm, swapEndian));
                    //double y(util::read<double>(strm, swapEndian));

                    //result->addPoint(x, y, util::read<double>(strm, swapEndian));
                    double x;
                    double y;
                    double z;
                    code = strm.readDouble(&x);
                    TE_CHECKBREAK_CODE(code);
                    code = strm.readDouble(&y);
                    TE_CHECKBREAK_CODE(code);
                    code = strm.readDouble(&z);
                    TE_CHECKBREAK_CODE(code);
                    code = result->addPoint(x, y, z);
                    TE_CHECKBREAK_CODE(code);
                    //strm.ignore(8);
                    code = strm.skip(8u);
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKRETURN_CODE(code);
                break;
            default :
                return TE_InvalidArg;
            }
        }

        //if (!strm)
        //{
        //    throw util::IO_Error(MEM_FN("parseWKB_LineString")
        //        "Parse of LineString failed");
        //}

        //return result.release();
        value = Geometry2Ptr(result.release(), Memory_deleter_const<Geometry2>);
        return code;
    }


    TAKErr parseWKB_Point(Geometry2Ptr &value,
                          DataInput2 &strm,
                          const std::size_t dim,
                          const bool hasMeasure) NOTHROWS
    {
        TAKErr code(TE_Ok);

        //double x(util::read<double>(strm, swapEndian));
        //double y(util::read<double>(strm, swapEndian));
        double x;
        double y;
        code = strm.readDouble(&x);
        TE_CHECKRETURN_CODE(code);
        code = strm.readDouble(&y);
        TE_CHECKRETURN_CODE(code);
        //feature::Point* result
        //(dim == feature::Geometry::_2D
        //    ? new feature::Point(x, y)
        //    : new feature::Point(x, y, util::read<double>(strm, swapEndian)));

        std::unique_ptr<Point2> result(new Point2(x, y));
        if (dim == 3) {
            code = result->setDimension(dim);
            TE_CHECKRETURN_CODE(code);
            double z;
            code = strm.readDouble(&z);
            TE_CHECKRETURN_CODE(code);

            result->z = z;
        }
        if (hasMeasure)
        {
            //strm.ignore(8);
            code = strm.skip(8u);
            TE_CHECKRETURN_CODE(code);
        }

        //if (!strm)
        //{
        //    throw util::IO_Error(MEM_FN("parseWKB_Point") "Parse of Point failed");
        //}

        //return result;
        value = Geometry2Ptr(result.release(), Memory_deleter_const<Geometry2>);
        return code;
    }


    TAKErr parseWKB_Polygon(Geometry2Ptr &value,
                            DataInput2 &strm,
                            const std::size_t dim,
                            const bool hasMeasure) NOTHROWS
    {
        TAKErr code(TE_Ok);
        //std::auto_ptr<feature::Polygon> result(new feature::Polygon(dim));
        std::unique_ptr<Polygon2> result;
        //std::size_t count(util::read<uint32_t>(strm, swapEndian));
        int count;
        code = strm.readInt(&count);
        TE_CHECKRETURN_CODE(code);

        if (count < 1)
            return TE_InvalidArg;

        Geometry2Ptr exteriorRing(NULL, NULL);
        code = parseWKB_LineString(exteriorRing, strm, dim, hasMeasure);
        TE_CHECKRETURN_CODE(code);

        result.reset(new Polygon2(static_cast<LineString2 &>(*exteriorRing)));

        for (std::size_t i(1); i < count; ++i)
        {
            //std::auto_ptr<feature::LineString> ring
            //(parseWKB_LineString(strm, swapEndian, dim, hasMeasure));
            Geometry2Ptr ring(NULL, NULL);
            code = parseWKB_LineString(ring, strm, dim, hasMeasure);
            TE_CHECKBREAK_CODE(code);

            //result->addRing(*ring);
            code = result->addInteriorRing(static_cast<LineString2 &>(*ring));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        //if (!strm)
        //{
        //    throw util::IO_Error(MEM_FN("parseWKB_Polygon")
        //        "Parse of Polygon failed");
        //}

        //return result.release();
        value = Geometry2Ptr(result.release(), Memory_deleter_const<Geometry2>);
        return code;
    }

    TAKErr packWKB_writeHeader(DataOutput2 &strm, const TAKEndian order, const Geometry2 &geom, const uint32_t baseType) NOTHROWS
    {
        TAKErr code(TE_Ok);
        uint8_t orderFlag;
        if(order == TE_BigEndian)
            orderFlag = (uint8_t)0x00u;
        else if(order == TE_LittleEndian)
            orderFlag = (uint8_t)0x01u;
        else
            return TE_IllegalState;;
        code = strm.writeByte(orderFlag);
        TE_CHECKRETURN_CODE(code);

        std::size_t dim = geom.getDimension();
        uint32_t typeCode;
        if(dim == 2u)
            typeCode = baseType;
        else if(dim == 3u)
            typeCode = baseType + 1000u;
        else
            return TE_IllegalState;
        code = strm.writeInt((int)typeCode);
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr packWKB_Point(DataOutput2 &strm, const TAKEndian order, const Point2 &p) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = packWKB_writeHeader(strm, order, p, 1u);
        TE_CHECKRETURN_CODE(code);

        code = strm.writeDouble(p.x);
        TE_CHECKRETURN_CODE(code);
        code = strm.writeDouble(p.y);
        TE_CHECKRETURN_CODE(code);
        if(p.getDimension() == 3u) {
            code = strm.writeDouble(p.z);
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }

    TAKErr packWKB_LineString(DataOutput2 &strm, const TAKEndian order, const LineString2 &linestring) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = packWKB_writeHeader(strm, order, linestring, 2u);
        TE_CHECKRETURN_CODE(code);

        code = packWKB_writePoints(strm, linestring);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr packWKB_writePoints(DataOutput2 &strm, const LineString2 &linestring) NOTHROWS
    {
        TAKErr code(TE_Ok);
        const std::size_t numPoints = linestring.getNumPoints();
        const std::size_t dimension = linestring.getDimension();
        code = strm.writeInt(numPoints);
        TE_CHECKRETURN_CODE(code);
        if(dimension == 3u) {
            for(std::size_t i = 0; i < numPoints; i++) {
                double v;

                code = linestring.getX(&v, i);
                TE_CHECKBREAK_CODE(code);
                code = strm.writeDouble(v);
                TE_CHECKBREAK_CODE(code);

                code = linestring.getY(&v, i);
                TE_CHECKBREAK_CODE(code);
                code = strm.writeDouble(v);
                TE_CHECKBREAK_CODE(code);

                code = linestring.getZ(&v, i);
                TE_CHECKBREAK_CODE(code);
                code = strm.writeDouble(v);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        } else if(dimension == 2u) {
            for(std::size_t i = 0; i < numPoints; i++) {
                double v;

                code = linestring.getX(&v, i);
                TE_CHECKBREAK_CODE(code);
                code = strm.writeDouble(v);
                TE_CHECKBREAK_CODE(code);

                code = linestring.getY(&v, i);
                TE_CHECKBREAK_CODE(code);
                code = strm.writeDouble(v);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        } else {
            return TE_IllegalState;
        }

        return code;
    }
    TAKErr packWKB_Polygon(DataOutput2 &strm, const TAKEndian order, const Polygon2 &polygon) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = packWKB_writeHeader(strm, order, polygon, 3u);

        std::size_t numInteriorRings = polygon.getNumInteriorRings();
        code = strm.writeInt(numInteriorRings+1u);
        TE_CHECKRETURN_CODE(code);

        std::shared_ptr<LineString2> ring;

        code = polygon.getExteriorRing(ring);
        TE_CHECKRETURN_CODE(code);
        if(!ring.get())
            return TE_IllegalState;
        code = packWKB_writePoints(strm, *ring);
        TE_CHECKRETURN_CODE(code);

        for(std::size_t i = 0u; i < numInteriorRings; i++) {
            code = polygon.getInteriorRing(ring, i);
            TE_CHECKRETURN_CODE(code);
            if(!ring.get())
                return TE_IllegalState;
            code = packWKB_writePoints(strm, *ring);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr packWKB_GeometryCollection(DataOutput2 &strm, const TAKEndian order, const GeometryCollection2 &c) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = packWKB_writeHeader(strm, order, c, 7u);
        TE_CHECKRETURN_CODE(code);

        const std::size_t numChildren = c.getNumGeometries();
        code = strm.writeInt(numChildren);
        TE_CHECKRETURN_CODE(code);

        for(std::size_t i = 0u; i < numChildren; i++) {
            std::shared_ptr<Geometry2> child;
            code = c.getGeometry(child, i);
            TE_CHECKRETURN_CODE(code);

            if(!child.get())
                return TE_IllegalState;

            code = GeometryFactory_toWkb(strm, *child, order);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}
