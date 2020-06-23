#include "util/MathUtils.h"

#include <cmath>

namespace
{
    const double LOG2 = log(2.0);
}

using namespace TAK::Engine::Util;

bool TAK::Engine::Util::MathUtils_isPowerOf2(int value) NOTHROWS
{
    return value ? ((value&(value - 1)) == 0) : false;
}

int TAK::Engine::Util::MathUtils_nextPowerOf2(int value) NOTHROWS
{
    return (int)(log(value) / LOG2) + 1;
}

bool TAK::Engine::Util::MathUtils_hasBits(const int64_t value, const int64_t mask) NOTHROWS
{
    return ((value&mask) == mask);
}
