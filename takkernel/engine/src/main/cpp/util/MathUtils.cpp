#include "util/MathUtils.h"

#include <cmath>

namespace
{
    const double LOG2 = log(2.0);
}

using namespace TAK::Engine::Util;

bool TAK::Engine::Util::MathUtils_isPowerOf2(const int value) NOTHROWS
{
    return value ? ((value&(value - 1)) == 0) : false;
}

bool TAK::Engine::Util::MathUtils_isPowerOf2(const size_t value) NOTHROWS
{
    return value ? ((value&(value - 1)) == 0) : false;
}

int TAK::Engine::Util::MathUtils_nextPowerOf2(const int value) NOTHROWS 
{
    return static_cast<int>((log(value) / LOG2) + 1);
}

size_t TAK::Engine::Util::MathUtils_nextPowerOf2(const size_t value) NOTHROWS 
{
    return static_cast<size_t>((log(value) / LOG2) + 1);
}

bool TAK::Engine::Util::MathUtils_hasBits(const int64_t value, const int64_t mask) NOTHROWS
{
    return ((value&mask) == mask);
}
