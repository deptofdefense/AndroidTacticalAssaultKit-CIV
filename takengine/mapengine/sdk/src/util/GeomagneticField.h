#ifndef TAK_ENGINE_UTIL_GEOMAGNETICFIELD_H_INCLUDED
#define TAK_ENGINE_UTIL_GEOMAGNETICFIELD_H_INCLUDED

#include "core/GeoPoint2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            ENGINE_API TAKErr GeomagneticField_getDeclination(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getInclination(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getFieldStrength(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getHorizontalStrength(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getX(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getY(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
            ENGINE_API TAKErr GeomagneticField_getZ(double *value, const Core::GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
        }
    }
}

#endif
