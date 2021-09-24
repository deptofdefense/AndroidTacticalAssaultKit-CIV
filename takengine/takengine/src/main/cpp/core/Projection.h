#ifndef ATAKMAP_CORE_PROJECTION_H_INCLUDED
#define ATAKMAP_CORE_PROJECTION_H_INCLUDED

#include "math/Point.h"

namespace atakmap {
namespace core {

class GeoPoint;

class Projection {
public :
    virtual ~Projection() {}
public :
    virtual int getSpatialReferenceID() = 0;

    virtual void forward(const GeoPoint *geo, atakmap::math::Point<double> *proj) = 0;
    virtual void inverse(const atakmap::math::Point<double> *proj, GeoPoint *geo) = 0;
    virtual double getMinLatitude() = 0;
    virtual double getMaxLatitude() = 0;
    virtual double getMinLongitude() = 0;
    virtual double getMaxLongitude() = 0;

    virtual bool is3D() = 0;
}; // end class Projection

} // end namespace atakmap::core
} // end namespace atakmap

#endif // ATAKMAP_CORE_PROJECTION_H_INCLUDED
