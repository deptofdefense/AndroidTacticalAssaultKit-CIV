#include "math/Utils.h"

using namespace atakmap::math;

bool
atakmap::math::isPowerOf2(int i)
{
    return i && !(i & (i - 1));
}

int
atakmap::math::nextPowerOf2(int i)
{
    return i > 0 ? 1 + static_cast<int> (std::log(i) / M_LN2) : 1;
}

double
atakmap::math::toDegrees(double rad)
{
    return rad / detail::DEG2RAD;
}

double
atakmap::math::toRadians(double deg)
{
    return deg * detail::DEG2RAD;
}
