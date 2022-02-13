#ifndef ATAKMAP_MATH_UTILS_H_INCLUDED
#define ATAKMAP_MATH_UTILS_H_INCLUDED

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <stdexcept>

#include "port/Platform.h"

namespace atakmap
{
namespace math
{


template <typename T>
inline
    T
    min(T a, T b)
{
    return std::min(a, b);
}

template <typename T>
inline
T
min (T a, T b, T c)
  { return std::min (a, std::min (b, c)); }


template <typename T>
inline
T
min (T a, T b, T c, T d)
  { return std::min (std::min (a, b), std::min (c, d)); }


template <typename T>
inline
T
min (const T* vals,
     std::size_t count)
  {
    if (!vals || !count)
      {
        throw std::invalid_argument ("atakmap::math::Utils::min invalid args");
      }
    return *std::min_element (vals, vals + count);
  }


template <typename T>
inline
T
max(T a, T b)
{
    return std::max(a, b);
}

template <typename T>
inline
T
max (T a, T b, T c)
  { return std::max (a, std::max (b, c)); }


template <typename T>
inline
T
max (T a, T b, T c, T d)
  { return std::max (std::max (a, b), std::max (c, d)); }


template <typename T>
inline
T
max (const T* vals,
     std::size_t count)
  {
    if (!vals || !count)
      {
        throw std::invalid_argument ("atakmap::math::Utils::max invalid args");
      }
    return *std::max_element (vals, vals + count);
  }


template <typename T>
inline
T
clamp (T v, T min, T max)
  { return v < min ? min : v > max ? max : v; }


bool
isPowerOf2(int i);


inline
int
nextPowerOf2(int i);


namespace detail
{

const double DEG2RAD (M_PI / 180.0);

}


double
ENGINE_API toDegrees(double rad);


double
ENGINE_API toRadians(double deg);

}                                       // Close math namespace.
}                                       // Close atakmap namespace.


#endif
