#include "core/Ellipsoid.h"

using namespace atakmap::core;

Ellipsoid Ellipsoid::createWGS84() {
    return Ellipsoid(6378137.0, 1.0/298.257223563);
}

const Ellipsoid Ellipsoid::WGS84 = createWGS84();


Ellipsoid::Ellipsoid(double semiMajor, double f) :
    semiMajorAxis(semiMajor),
    flattening(f),
    semiMinorAxis(semiMajor*(1.0 - f))
{}

Ellipsoid::~Ellipsoid()
{}
