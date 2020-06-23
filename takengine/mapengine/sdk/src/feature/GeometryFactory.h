#ifndef TAK_ENGINE_FEATURE_GEOMETRYFACTORY2_H_INCLUDED
#define TAK_ENGINE_FEATURE_GEOMETRYFACTORY2_H_INCLUDED

#include "feature/Geometry2.h"
#include "util/IO2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            /**
             * Deserializes a Geometry2 instance from OGC WKB binary data.
             *
             * @param value Returns the deserialized geometry.
             * @param src   The source data
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromWkb(Geometry2Ptr &value, Util::DataInput2 &src) NOTHROWS;
            /**
             * Deserializes a Geometry2 instance from OGC WKB binary data.
             *
             * @param value     Returns the deserialized geometry.
             * @param wkb       The source data
             * @param wkbLen    The source data length
             * 
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromWkb(Geometry2Ptr &value, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS;

            /**
             * Deserializes a Geometry2 instance from SpatiaLite blob format
             * binary data.
             *
             * @param value Returns the deserialized geometry.
             * @param src   The source data
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, Util::DataInput2 &src) NOTHROWS;
            /**
             * Deserializes a Geometry2 instance from SpatiaLite blob format
             * binary data.
             *
             * @param value     Returns the deserialized geometry.
             * @param wkb       The source data
             * @param wkbLen    The source data length
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS;

            /**
             * Deserializes a Geometry2 instance from SpatiaLite blob format
             * binary data.
             *
             * @param value Returns the deserialized geometry.
             * @param srid  If non-NULL, returns the SRID for the geometry
             * @param src   The source data
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, int *srid, Util::DataInput2 &src) NOTHROWS;
            /**
             * Deserializes a Geometry2 instance from SpatiaLite blob format
             * binary data.
             *
             * @param value     Returns the deserialized geometry.
             * @param srid      If non-NULL, returns the SRID for the geometry
             * @param wkb       The source data
             * @param wkbLen    The source data length
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_fromSpatiaLiteBlob(Geometry2Ptr &value, int *srid, const uint8_t *wkb, const std::size_t wkbLen) NOTHROWS;

            /**
             * Serializes the specified geometry as OGC WKB (Well Known Binary)
             * format. The host platform endian is used.
             *
             * @param sink      The output sink
             * @param geometry  The geometry
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_toWkb(Util::DataOutput2 &sink, const Geometry2 &geometry) NOTHROWS;
            /**
             * Serializes the specified geometry as OGC WKB (Well Known Binary)
             * format using the specified endianness.
             *
             * @param sink      The output sink
             * @param geometry  The geometry
             * @param endian    The encoding byte order
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_toWkb(Util::DataOutput2 &sink, const Geometry2 &geometry, const Util::TAKEndian endian) NOTHROWS;

            /**
             * Serializes the geometry to the SpatiaLite Blob format. The host
             * platform endian is used for encoding.
             *
             * @param sink      The data sink
             * @param geometry  The geometry
             * @param srid      The ID of the spatial reference associated with
             *                  the geometry
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_toSpatiaLiteBlob(Util::DataOutput2 &sink, const Geometry2 &geometry, const int srid) NOTHROWS;

            /**
             * Serializes the geometry to the SpatiaLite Blob format using the
             * specified endian for encoding.
             *
             * @param sink      The data sink
             * @param geometry  The geometry
             * @param srid      The ID of the spatial reference associated with
             *                  the geometry
             * @param endian    The encoding byte order
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            ENGINE_API Util::TAKErr GeometryFactory_toSpatiaLiteBlob(Util::DataOutput2 &sink, const Geometry2 &geometry, const int srid, const Util::TAKEndian endian) NOTHROWS;
        }
    }
}

#endif
