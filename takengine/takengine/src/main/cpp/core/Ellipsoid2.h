#ifndef TAK_ENGINE_CORE_ELLIPSOID2_H_INCLUDED
#define TAK_ENGINE_CORE_ELLIPSOID2_H_INCLUDED

#include "port/Platform.h"
#include "util/Memory.h"

namespace TAK 
{
    namespace Engine
    {
        namespace Core 
        {
            class ENGINE_API Ellipsoid2
            {
            public:
                Ellipsoid2(double semiMajorAxis, double flattening);
                ~Ellipsoid2();
            public:
                const double semiMajorAxis;
                const double flattening;

                const double semiMinorAxis;
            public:
                const static Ellipsoid2 WGS84;
                static Ellipsoid2 createWGS84() NOTHROWS;
            };
        }
    }
}

#endif
