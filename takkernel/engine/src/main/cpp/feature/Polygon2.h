#ifndef TAK_ENGINE_FEATURE_POLYGON2_H_INCLUDED
#define TAK_ENGINE_FEATURE_POLYGON2_H_INCLUDED

#include <vector>

#include "feature/Geometry2.h"
#include "feature/LineString2.h"
#include "port/Collection.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            /**
             * <P>Note: The polygon is malformed if any of its rings are not
             * closed.
             */
            class ENGINE_API Polygon2 : public Geometry2
            {
            public :
                /**
                 * Creates a new polygon with a default dimension of '2'.
                 */
                Polygon2() NOTHROWS;
                /**
                 * Creates a new polygon with its exterior ring initialized as
                 * a copy of 'exteriorRing'. The dimension of the polygon will
                 * be adopted from 'exteriorRing'.
                 */
                Polygon2(const LineString2 &exteriorRing) NOTHROWS;
                Polygon2(const Polygon2 &other) NOTHROWS;
            public :
                /**
                 * Adds a new, empty interior ring to the polygon.
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr addInteriorRing() NOTHROWS;
                /**
                 * Adds a new interior ring to the polygon that is a copy of
                 * the specified linestring.
                 *
                 * @return  TE_Ok on success
                 */
                Util::TAKErr addInteriorRing(const LineString2 &ring) NOTHROWS;
                Util::TAKErr removeInteriorRing(const std::size_t i) NOTHROWS;
                Util::TAKErr removeInteriorRing(const LineString2 &ring) NOTHROWS;

                /**
                 * Resets the polygon to an empty polygon. All points in the
                 * exterior ring are removed and all interior rings are
                 * removed.
                 */
                void clear() NOTHROWS;

                /**
                 * Returns the exterior ring.
                 *
                 * @param value Returns the exterior ring
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getExteriorRing(std::shared_ptr<LineString2> &value) const NOTHROWS;
                /**
                 * Returns the interior rings.
                 *
                 * @param value Returns the interior rings
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getInteriorRings(Port::Collection<std::shared_ptr<LineString2>> &value) const NOTHROWS;
                /**
                 * Returns the specified interior ring.
                 *
                 * @param value Returns the ring
                 * @param i     The ring index
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getInteriorRing(std::shared_ptr<LineString2> &value, const std::size_t i) const NOTHROWS;
                /**
                 * Returns the number of interior rings.
                 *
                 * @return  The number of interior rings.
                 */
                std::size_t getNumInteriorRings() const NOTHROWS;
            public :;
                virtual std::size_t getDimension() const NOTHROWS override;
                virtual Util::TAKErr getEnvelope(Envelope2 *value) const NOTHROWS override;
            private :
                virtual Util::TAKErr setDimensionImpl(const std::size_t dimension) NOTHROWS override;
                virtual bool equalsImpl(const Geometry2 &other) NOTHROWS override;
            private :
                std::shared_ptr<LineString2> exteriorRing;
                std::vector<std::shared_ptr<LineString2>> interiorRings;
                std::size_t dimension;
            };

            ENGINE_API Util::TAKErr Polygon2_fromEnvelope(Geometry2Ptr_const &value, const Envelope2 &e) NOTHROWS;
            ENGINE_API Util::TAKErr Polygon2_fromEnvelope(Geometry2Ptr &value, const Envelope2 &e) NOTHROWS;
        }
    }
}

#endif
