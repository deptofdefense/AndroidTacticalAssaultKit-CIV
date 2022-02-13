#include "core/Ellipsoid2.h"

using namespace TAK::Engine::Core;

Ellipsoid2 Ellipsoid2::createWGS84() NOTHROWS {
    return Ellipsoid2(6378137.0, 1.0 / 298.257223563);
}

const Ellipsoid2 Ellipsoid2::WGS84 = createWGS84();

Ellipsoid2::Ellipsoid2(double semiMajor, double f) :
    semiMajorAxis(semiMajor),
    flattening(f),
    semiMinorAxis(semiMajor*(1.0 - f))
{}

Ellipsoid2::~Ellipsoid2()
{}
