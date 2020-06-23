#ifndef ATAKMAP_CORE_DATUM_H_INCLUDED
#define ATAKMAP_CORE_DATUM_H_INCLUDED

#include "core/Ellipsoid.h"

namespace atakmap {
namespace core {

class Datum
{
public :
    Datum(const Ellipsoid *reference, const double dx, const double dy, const double dz);
    ~Datum();
public :
    const Ellipsoid reference;
    const double deltaX;
    const double deltaY;
    const double deltaZ;
public :
    const static Datum WGS84;
};

}
}

#endif // ATAKMAP_CORE_DATUM_H_INCLUDED
