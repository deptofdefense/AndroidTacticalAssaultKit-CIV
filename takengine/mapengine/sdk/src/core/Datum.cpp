#include "core/Datum.h"

using namespace atakmap::core;

static Datum createWGS84() {
    Ellipsoid ellipsoid = Ellipsoid::createWGS84();
    return Datum(&ellipsoid, 0, 0, 0);
}

const Datum Datum::WGS84 = createWGS84();


Datum::Datum(const Ellipsoid *ref, const double dx, const double dy, const double dz) :
    reference(ref->semiMajorAxis, ref->flattening),
    deltaX(dx),
    deltaY(dy),
    deltaZ(dz)
{}

Datum::~Datum()
{}

