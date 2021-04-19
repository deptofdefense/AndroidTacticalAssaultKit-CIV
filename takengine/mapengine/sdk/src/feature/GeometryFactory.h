#ifndef TAK_ENGINE_FEATURE_GEOMETRYFACTORY2_H_INCLUDED
#define TAK_ENGINE_FEATURE_GEOMETRYFACTORY2_H_INCLUDED

#include "feature/Envelope2.h"
#include "feature/Geometry2.h"
#include "math/Point2.h"
#include "renderer/Tessellate.h"
#include "util/IO2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {

            /**
            * Default extrusion, or TEEH_None, will provide top face, no bottom
            * face, and return a GeometryCollection2. TEEH_GeneratePolygons
            * changes the specialized Geometry2 output to Polygon2, whereas other flags
            * control the production of top and bottom faces as part of the
            * GeometryCollection2 or Polygon2 return value.
            */
			enum ExtrusionHints
            {
                TEEH_None,
                TEEH_IncludeBottomFace = 1,
                TEEH_OmitTopFace = 1 << 1,
                TEEH_GeneratePolygons = 1 << 2
            };

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

            /**
             * Extrudes the source geometry along the z-axis by the specified amount. If the
             * geometry is 2D, the source z coordinate is assumed to be 0.0.
             * <P>The behavior is as follows for the various geometry types:
             * <UL>
             *  <LI>Point2. Extrudes into a segment, represented by a LineString2.
             *  <LI>LineString2. Extrudes into a GeometryCollection2. Each "face" is represented
             *          by a distinct Polygon2.
             *  <LI>Polygon2. Extrudes into a GeometryCollection2. Each "face" is represented by
             *          a distinct Polygon2. A face for the bottom of the extrusion is included.
             * </UL>
             * @param value Returns the extruded geometry on success
             * @param src The input geometry
             * @param extrude The value to extrude along the z axis
             *
             * @return TE_Ok on success, various codes on failure
             */
            ENGINE_API Util::TAKErr GeometryFactory_extrude(Geometry2Ptr &value, const Geometry2 &src, const double extrude, const int hints = TEEH_None) NOTHROWS;

            /**
             * Extrudes the source geometry along the z-axis by the specified
             * per-vertex amounts. If the  geometry is 2D, the source z
             * coordinate is assumed to be 0.0.
             * <P>The behavior is as follows for the various geometry types:
             * <UL>
             *  <LI>Point2. Extrudes into a segment, represented by a LineString2.
             *  <LI>LineString2. Extrudes into a GeometryCollection2. Each "face" is represented
             *          by a distinct Polygon2.
             *  <LI>Polygon2. Extrudes into a GeometryCollection2. Each "face" is represented by
             *          a distinct Polygon2. A face for the bottom of the extrusion is included.
             * </UL>
             * @param value Returns the extruded geometry on success
             * @param src The input geometry
             * @param extrude The value to extrude along the z axis
             *
             * @return TE_Ok on success, various codes on failure
             */
            ENGINE_API Util::TAKErr GeometryFactory_extrude(Geometry2Ptr &value, const Geometry2 &src, const double *extrude, const std::size_t count, const int hints = TEEH_None) NOTHROWS;

            /**
            * Creates a two  dimensional ellipse centered on location with a clockwise orientation
            * sized by major and minor axes. Cartesian, WGS84 or other conventions are determined by
            * a supplied set of algorithms.
            * @param value Returns the generated geometry on success
            * @param location Center location for the generated geometry
            * @param orientation Clocwise orientataion of generated geometry's major axis
            * @param major Major axis dimension for generated geometry
            * @param minor Minor axis dimension for generated geometry
            * @param algo Distance, direction, and interpolation functions in chosen basis
            **/
            ENGINE_API Util::TAKErr GeometryFactory_createEllipse(Geometry2Ptr& value, const Math::Point2<double> &location, const double orientation, const double major, const double minor, const Renderer::Algorithm &algo) NOTHROWS;

            /**
            * Creates a two  dimensional ellipse located, oriented and sized by
            * a bounding rectangle. Cartesian, WGS84 or other conventions are determined by
            * a supplied set of algorithms.
            * @param value Returns the generated geometry on success
            * @param bounds Bounding rectangle for the generated ellipse
            * @param algo Distance, direction, and interpolation functions in chosen basis
            **/
            ENGINE_API Util::TAKErr GeometryFactory_createEllipse(Geometry2Ptr& value, const Feature::Envelope2 &bounds, const Renderer::Algorithm& algo) NOTHROWS;

            /**
            * Creates a rectangle geometry from two points representing the
            * start and end corners. Should the z value of the corners differ,
			* the maximum z value will be applied to all output geometries.
            * @param value Returns the rectangular geometry on success
            * @param corner1 Start corner
            * @param corner2 End corner
            * @param algo Distance, direction, and interpolation functions in chosen basis
            **/
            ENGINE_API Util::TAKErr GeometryFactory_createRectangle(Geometry2Ptr& value, const Math::Point2<double>& corner1, const Math::Point2<double>& corner2, const Renderer::Algorithm& algo) NOTHROWS;

            /**
            * Creates a rectangle geometry from three points. Should the z value of the
			* points differ, the maximum z value will be applied to all output geometries.
            * @param value Returns the rectangular geometry on success
            * @param point1 First corner point
            * @param point2 Second corner point
            * @param point3 Third point offset from the base formed be the two corners
            * @param algo Distance, direction, and interpolation functions in chosen basis
            **/
            ENGINE_API Util::TAKErr GeometryFactory_createRectangle(Geometry2Ptr& value, const Math::Point2<double>& corner1, const Math::Point2<double>& corner2, const Math::Point2<double>& point3, const Renderer::Algorithm& algo) NOTHROWS;


            /**
            * Creates a rectangle geometry based on a center, width, heightand angle.
            * @param value Returns the rectangular geometry on success
            * @param location  the center point of the rectangular geometry
            * @param length dimension along the bearing axis
            * @param width dimension perpendicular to the bearing axis
            * @param angle  the bearing angle of rotation around the center
            **/
            ENGINE_API Util::TAKErr  GeometryFactory_createRectangle(Geometry2Ptr& value, const Math::Point2<double>& location, const double orientation, const double length, const double width, const Renderer::Algorithm& algo) NOTHROWS;

        }
    }
}

#endif
