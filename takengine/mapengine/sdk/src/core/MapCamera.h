#ifndef ATAKMAP_CORE_MAP_CAMERA_H_INCLUDED
#define ATAKMAP_CORE_MAP_CAMERA_H_INCLUDED

#include "math/Matrix.h"
#include "math/Point.h"
#include "port/Platform.h"

namespace atakmap {
namespace core {

class ENGINE_API MapCamera
{
public :
    MapCamera();
    ~MapCamera();
public :
    atakmap::math::Matrix projection;
    atakmap::math::Matrix modelView;
    atakmap::math::Point<double> target;
    atakmap::math::Point<double> location;
    double roll;
};

}; // end namespace core
}; // end namespace atakmap

#endif // ATAKMAP_CORE_MAP_CAMERA_H_INCLUDED
