#ifndef TAK_ENGINE_CORE_MAPCAMERA2_H_INCLUDED
#define TAK_ENGINE_CORE_MAPCAMERA2_H_INCLUDED

#ifdef _MSC_VER
#ifdef near
#undef near
#endif
#ifdef far
#undef far
#endif
#endif

#include <cmath>

#include "math/Matrix2.h"
#include "math/Point2.h"

namespace TAK {
    namespace Engine {
        namespace Core {

            class MapCamera2
            {
            public :
                enum Mode
                {
                    Perspective,
                    Scale,
                };
            public:
                MapCamera2() NOTHROWS;
                ~MapCamera2() NOTHROWS;
            public:
                Math::Matrix2 projection;
                Math::Matrix2 modelView;
                Math::Point2<double> target;
                Math::Point2<double> location;
                double roll {0.0};
                double azimuth {0.0};
                double elevation {-90.0};
                double near {1.0};
                double nearMeters;
                double far {-1.0};
                double farMeters;
                double fov;
                double aspectRatio {1.0};
                /** If not `NAN`, indicates computed AGL of the camera */
                double agl {NAN};
                Mode mode;
            };

            inline MapCamera2::MapCamera2() NOTHROWS :
                roll(0.0),
                azimuth(0.0),
                elevation(0.0),
                near(0.0),
                far(0.0),
                fov(0.0),
                aspectRatio(0.0),
                mode(MapCamera2::Scale)
            {}
            inline MapCamera2::~MapCamera2() NOTHROWS {}

        };
    };
};
#endif // TAK_ENGINE_CORE_MAPCAMERA2_H_INCLUDED
