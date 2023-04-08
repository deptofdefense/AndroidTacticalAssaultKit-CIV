#include "feature/Envelope.h"

#include <cmath>
#include <limits>

using atakmap::feature::Envelope;

bool Envelope::operator==(const Envelope& rhs) const NOTHROWS
{
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
