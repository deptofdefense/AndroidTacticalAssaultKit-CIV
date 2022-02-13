#include "feature/Envelope2.h"

#include <cmath>
#include <limits>

using namespace TAK::Engine::Feature;

Envelope2::Envelope2() NOTHROWS :
    minX(NAN),
    minY(NAN),
    minZ(NAN),
    maxX(NAN),
    maxY(NAN),
    maxZ(NAN)
{}

Envelope2::Envelope2(const double minX_, const double minY_, const double maxX_, const double maxY_) NOTHROWS :
    minX(minX_),
    minY(minY_),
    minZ(0.0),
    maxX(maxX_),
    maxY(maxY_),
    maxZ(0.0)
{}

Envelope2::Envelope2(const double minX_, const double minY_, const double minZ_, const double maxX_, const double maxY_, const double maxZ_) NOTHROWS :
    minX(minX_),
    minY(minY_),
    minZ(minZ_),
    maxX(maxX_),
    maxY(maxY_),
    maxZ(maxZ_)
{}

bool Envelope2::operator==(const Envelope2 &rhs) const NOTHROWS {
    constexpr double epsilon = std::numeric_limits<double>::epsilon();
    bool result = true;
    result = result && (fabs(minX - rhs.minX) < epsilon);
    result = result && (fabs(minY - rhs.minY) < epsilon);
    result = result && (fabs(minZ - rhs.minZ) < epsilon);
    result = result && (fabs(maxX - rhs.maxX) < epsilon);
    result = result && (fabs(maxY - rhs.maxY) < epsilon);
    result = result && (fabs(maxZ - rhs.maxZ) < epsilon);
    return result;
}
