#ifndef TAK_ENGINE_FEATURE_GEOMETRY2_H_INCLUDED
#define TAK_ENGINE_FEATURE_GEOMETRY2_H_INCLUDED

#include <memory>

#include "feature/Envelope2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            enum GeometryClass
            {
                TEGC_Point,
                TEGC_LineString,
                TEGC_Polygon,
                TEGC_GeometryCollection,
            };

            /**
             * Base class of a non-extensible object model for geometries.
             * There are 4 classes of geometry:
             * <UL>
             *  <LI>Point</LI>
             *  <LI>LineString</LI>
             *  <LI>Polygon</LI>
             *  <LI>GeometryCollection</LI>
             * </UL>
             * Arbitrarily complex geometries may be constructed as the
             * composition of these basic types via the GeometryCollection2
             * class.
             *
             * <P>Mutability: Geometry objects are by nature mutable, and may
             * be made immutable via constness.
             * <P>Thread Safety: The various classes of the object model are
             * not thread-safe and must be externally synchronized if
             * concurrent access to non-const instances is required.
             */
            class ENGINE_API Geometry2
            {
            protected :
                Geometry2(const GeometryClass clazz) NOTHROWS;
                Geometry2(const Geometry2 &other) NOTHROWS;
            public :
                virtual ~Geometry2() NOTHROWS;
            public :
                /**
                 * Returns the geometry class.
                 *
                 * @return  The geometry class of this instance.
                 */
                GeometryClass getClass() const NOTHROWS;
                /**
                 * Sets the dimension of the geometry. A value of '2'
                 * represents 2D geometries (x,y coordinate pairs); a value of
                 * '3' represents 3D geometries (x,y,z coordinate triplets).
                 *
                 * @param dimension The new dimension for the geometry; values
                 *                  of '2' and '3' are supported.
                 *
                 * @return  TE_Ok on success; TE_InvalidArg if the dimension is
                 *          not a supported value. For geometries that are
                 *          "owned" by other geomtries (e.g. one of the rings
                 *          of a polygon), TE_IllegalState will be returned.
                 */
                Util::TAKErr setDimension(const std::size_t dimension) NOTHROWS;
            public :
                /**
                 * Returns the diemnsion of the geometry. A value of '2'
                 * represents 2D geometries (x,y coordinate pairs); a value of
                 * '3' represents 3D geometries (x,y,z coordinate triplets).
                 *
                 * @return  The dimension of the geometry
                 */
                virtual std::size_t getDimension() const NOTHROWS = 0;

                /**
                 * Returns the bounding box enclosing the geometry. If the
                 * geometry is empty an envelope of all NAN values shall be
                 * returned.
                 *
                 * <P>For 2D geometries, the 'minZ' and 'maxZ' values shall be
                 * set to '0'.
                 *
                 * @param value Returns the minimum bounding box of the geometry
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                virtual Util::TAKErr getEnvelope(Envelope2 *value) const NOTHROWS = 0;
            public :
                bool operator==(const Geometry2 &other) NOTHROWS;
                bool operator!=(const Geometry2 &other) NOTHROWS;
            private :
                virtual Util::TAKErr setDimensionImpl(const std::size_t dimension) NOTHROWS = 0;
                virtual bool equalsImpl(const Geometry2 &other) NOTHROWS = 0;
            private :
                GeometryClass clazz;
                bool owned;

                friend class Point2;
                friend class LineString2;
                friend class Polygon2;
                friend class GeometryCollection2;
            };

            typedef std::unique_ptr<Geometry2, void(*)(const Geometry2 *)> Geometry2Ptr;
            typedef std::unique_ptr<const Geometry2, void(*)(const Geometry2 *)> Geometry2Ptr_const;

            /**
             * Creates a copy of the specified geometry.
             *
             * @param value     Returns the clone
             * @param geometry  A geometry instance
             *
             * @return  TE_Ok on success; various codes on failure.
             */
			ENGINE_API Util::TAKErr Geometry_clone(Geometry2Ptr &value, const Geometry2 &geometry) NOTHROWS;

            /**
             * Creates a const copy of the specified geometry.
             *
             * @param value     Returns the clone
             * @param geometry  A geometry instance
             *
             * @return  TE_Ok on success; various codes on failure.
             */
			ENGINE_API Util::TAKErr Geometry_clone(Geometry2Ptr_const &value, const Geometry2 &geometry) NOTHROWS;
        }
    }
}

#endif
