#ifndef ATAKMAP_CORE_ELLIPSOID_H_INCLUDED
#define ATAKMAP_CORE_ELLIPSOID_H_INCLUDED

namespace atakmap {
namespace core {

class Ellipsoid
{
public :
    Ellipsoid(double semiMajorAxis, double flattening);
    ~Ellipsoid();
public :
    const double semiMajorAxis;
    const double flattening;

    const double semiMinorAxis;
public :
    const static Ellipsoid WGS84;
    static Ellipsoid createWGS84();
};

}
}

#endif // ATAKMAP_CORE_ELLIPSOID_H_INCLUDED
