#ifndef TAK_ENGINE_FEATURE_POINT2_H_INCLUDED
#define TAK_ENGINE_FEATURE_POINT2_H_INCLUDED

#include "feature/Geometry2.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API Point2 : public Geometry2
            {
            public:
                /**
                 * Creates a new 2D point.
                 *
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 */
                Point2(const double x, const double y) NOTHROWS;
                /**
                 * Creates a new 3D point.
                 *
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 * @param z The z-coordinate
                 */
                Point2(const double x, const double y, const double z) NOTHROWS;
            public:
                virtual std::size_t getDimension() const NOTHROWS override;
                virtual Util::TAKErr getEnvelope(Envelope2 *value) const NOTHROWS override;
            private :
                virtual Util::TAKErr setDimensionImpl(const std::size_t dimension) NOTHROWS override;
                virtual bool equalsImpl(const Geometry2 &other) NOTHROWS override;
            public:
                /** the x-coordinate */
                double x;
                /** the y-coordinate */
                double y;
                /** the z-coordinate; ignored if point is 2D */
                double z;
            private:
                std::size_t dimension;
            };
        }
    }
}
#endif
