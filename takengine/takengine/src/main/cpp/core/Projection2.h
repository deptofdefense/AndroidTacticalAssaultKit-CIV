#ifndef TAK_ENGINE_CORE_PROJECTION2_H_INCLUDED
#define TAK_ENGINE_CORE_PROJECTION2_H_INCLUDED

#include <memory>

#include "core/GeoPoint2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API Projection2
            {
            protected:
                virtual ~Projection2() NOTHROWS {}
            public:
                virtual int getSpatialReferenceID() const NOTHROWS = 0;

                virtual Util::TAKErr forward(TAK::Engine::Math::Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS = 0;
                virtual Util::TAKErr inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS = 0;
                virtual double getMinLatitude() const NOTHROWS = 0;
                virtual double getMaxLatitude() const NOTHROWS = 0;
                virtual double getMinLongitude() const NOTHROWS = 0;
                virtual double getMaxLongitude() const NOTHROWS = 0;

                virtual bool is3D() const NOTHROWS = 0;
            }; // end class Projection

            typedef std::unique_ptr<Projection2, void(*)(const Projection2 *)> Projection2Ptr;
        }
    }
}
#endif // TAK_ENGINE_CORE_PROJECTION_H_INCLUDED
