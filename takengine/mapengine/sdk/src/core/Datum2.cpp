#include "core/Datum2.h"

using namespace TAK::Engine::Core;

static Datum2 createWGS84() {
    return Datum2(Ellipsoid2::createWGS84(), 0, 0, 0);
}

const Datum2 Datum2::WGS84 = createWGS84();

Datum2::Datum2(const Ellipsoid2 &ref, const double dx, const double dy, const double dz) :
    reference(ref),
    deltaX(dx),
    deltaY(dy),
    deltaZ(dz)
{}

Datum2::~Datum2()
{}
