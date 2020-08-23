#ifndef TAK_ENGINE_FEATURE_GEOMETRYCOLLECTION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_GEOMETRYCOLLECTION2_H_INCLUDED

#include <vector>

#include "feature/Geometry2.h"
#include "port/Collection.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API GeometryCollection2 : public Geometry2
            {
            public :
                /**
                 * Creates a new GeometryCollection2 with a default dimension
                 * of '2'.
                 */
                GeometryCollection2() NOTHROWS;
                GeometryCollection2(const GeometryCollection2 &other) NOTHROWS;
            public :
                /**
                 * Adds a new geometry to this collection that is a copy of the
                 * specified geometry.
                 *
                 * @param geometry  The geometry
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr addGeometry(const Geometry2 &geometry) NOTHROWS;
                /**
                 * Adds the specified geometry to this collection.
                 *
                 * @param geometry  The geometry
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr addGeometry(Geometry2Ptr &&geometry) NOTHROWS;
                /**
                 * Removes the geometry at the specified index.
                 *
                 * @param i The index
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr removeGeometry(const std::size_t i) NOTHROWS;
                /**
                 * Removes the specified geometry from this collection.
                 *
                 * @param geometry  The geometry to be removed
                 *
                 * @return   TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr removeGeometry(const Geometry2 &geometry) NOTHROWS;
                /**
                 * Removes all geometries from this collection.
                 */
                void clear() NOTHROWS;

                Util::TAKErr getGeometries(Port::Collection<std::shared_ptr<Geometry2>> &value) const NOTHROWS;
                Util::TAKErr getGeometry(std::shared_ptr<Geometry2> &value, const std::size_t i) const NOTHROWS;
                std::size_t getNumGeometries() const NOTHROWS;
            public :
                virtual std::size_t getDimension() const NOTHROWS override;
                virtual Util::TAKErr getEnvelope(Envelope2 *value) const NOTHROWS override;
            private :
                virtual Util::TAKErr setDimensionImpl(const std::size_t dimension) NOTHROWS override;
                virtual bool equalsImpl(const Geometry2 &other) NOTHROWS override;
            private :
                std::size_t dimension;
                std::vector<std::shared_ptr<Geometry2>> geometries;
            };

            /**
             * Creates a new GeometryCollection2 that contains copies of all
             * geometries in the specified collection, with relative ordering
             * preserved. If any element of 'collection' is a
             * GeometryCollection2 copies of the children of that child, rather
             * than a copy of the child itself, will be added to the return
             * value.
             *
             * @param value         Returns the flattened geometry collection
             * @param collection    A geometry collection
             *
             * @return  TE_Ok on success; various codes on failure.
             */
            Util::TAKErr GeometryCollection_flatten(Geometry2Ptr &value, const GeometryCollection2 &collection) NOTHROWS;
        }
    }
}

#endif
