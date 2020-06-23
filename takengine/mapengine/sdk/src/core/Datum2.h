#ifndef TAK_ENGINE_CORE_DATUM2_H_INCLUDED
#define TAK_ENGINE_CORE_DATUM2_H_INCLUDED

#include "core/Ellipsoid2.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Core {

            class ENGINE_API Datum2
            {
            public:
                Datum2(const Ellipsoid2 &reference, const double dx, const double dy, const double dz);
                ~Datum2();
            public:
                const Ellipsoid2 reference;
                const double deltaX;
                const double deltaY;
                const double deltaZ;
            public:
                const static Datum2 WGS84;
            };
        }
    }
}

#endif
