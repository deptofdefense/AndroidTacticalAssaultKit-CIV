#include <cmath>
#include "OGRUtils.h"
#include "math/Utils.h"

namespace {

    const double RADIANS (M_PI / 180.0);

    inline std::size_t mapnikTileX (std::size_t level, double lon)
    { return static_cast<std::size_t>((lon + 180.0) / 360.0 * (1 << level)); }

    inline std::size_t mapnikTileY (std::size_t level, double lat)
    {
        double radLat (lat * RADIANS);

        return static_cast<std::size_t>((1 << level)
            * (1.0 - std::log (std::tan (radLat) + 1.0 / std::cos (radLat)) / M_PI)
            / 2.0);
    }

    inline std::size_t mapnikPixelY(std::size_t level, std::size_t ytile, double lat)
    {
        return mapnikTileY(level + 8, lat) - (ytile << 8);
    }

    inline std::size_t mapnikPixelX(std::size_t level, std::size_t xtile, double lng)
    {
        return mapnikTileX(level + 8, lng) - (xtile << 8);
    }
    double computeMapnikArea (std::size_t level, const OGREnvelope& env)
    {
#if 0
        //
        // Tiles are 256 x 256, so by bumping the level by 8, we get pixel coords.
        //
        level += 8;

        double minPixX (mapnikTileX (level, env.MinX));
        double maxPixX (mapnikTileX (level, env.MaxX));
        double minPixY (mapnikTileY (level, env.MinY));
        double maxPixY (mapnikTileY (level, env.MaxY));

        return (maxPixX - minPixX) * (maxPixY - minPixY);
#else
        //
        // NB:      Since we only have two (corner) points, this quadrilateral
        //          computation is overkill.
        //
        double mbrULLon = env.MinX;
        double mbrULLat = env.MaxY;
        double mbrLRLon = env.MaxX;
        double mbrLRLat = env.MinY;

        std::size_t tileULx = mapnikTileX(level, mbrULLon);
        std::size_t tileULy = mapnikTileY(level, mbrULLat);
        std::size_t tileURx = mapnikTileX(level, mbrLRLon);
        std::size_t tileURy = mapnikTileY(level, mbrULLat);
        std::size_t tileLRx = mapnikTileX(level, mbrLRLon);
        std::size_t tileLRy = mapnikTileY(level, mbrLRLat);
        std::size_t tileLLx = mapnikTileX(level, mbrULLon);
        std::size_t tileLLy = mapnikTileY(level, mbrLRLat);

        int64_t pxULx = mapnikPixelX(level, tileULx, mbrULLon) + (tileULx * 256);
        int64_t pxULy = mapnikPixelY(level, tileULy, mbrULLat) + (tileULy * 256);
        int64_t pxURx = mapnikPixelX(level, tileURx, mbrLRLon) + (tileURx * 256);
        int64_t pxURy = mapnikPixelY(level, tileURy, mbrULLat) + (tileURy * 256);
        int64_t pxLRx = mapnikPixelX(level, tileLRx, mbrLRLon) + (tileLRx * 256);
        int64_t pxLRy = mapnikPixelY(level, tileLRy, mbrLRLat) + (tileLRy * 256);
        int64_t pxLLx = mapnikPixelX(level, tileLLx, mbrULLon) + (tileLLx * 256);
        int64_t pxLLy = mapnikPixelY(level, tileLLy, mbrLRLat) + (tileLLy * 256);

#ifdef _MSC_VER
#define te_llabs(x) _abs64(x)
#else
#define te_llabs(x) llabs(x)
#endif
        int64_t upperDx = te_llabs(pxURx - pxULx);
        int64_t upperDy = te_llabs(pxURy - pxULy);
        int64_t rightDx = te_llabs(pxLRx - pxURx);
        int64_t rightDy = te_llabs(pxLRy - pxURy);
        int64_t lowerDx = te_llabs(pxLRx - pxLLx);
        int64_t lowerDy = te_llabs(pxLRy - pxLLy);
        int64_t leftDx = te_llabs(pxLLx - pxLLx);
        int64_t leftDy = te_llabs(pxLRy - pxURy);

        auto upperSq = static_cast<double>((upperDx * upperDx) + (upperDy * upperDy));
        auto rightSq = static_cast<double>((rightDx * rightDx) + (rightDy * rightDy));
        auto lowerSq = static_cast<double>((lowerDx * lowerDx) + (lowerDy * lowerDy));
        auto leftSq = static_cast<double>((leftDx * leftDx) + (leftDy * leftDy));

        auto diag0sq = static_cast<double>(((pxLRx - pxULx) * (pxLRx - pxULx) + (pxLRy - pxULy) * (pxLRy - pxULy)));
        auto diag1sq = static_cast<double>(((pxURx - pxLLx) * (pxURx - pxLLx) + (pxURy - pxLLy) * (pxURy - pxLLy)));

        //
        // NB:      This formula is wrong!!!  The subtracted term under the radical
        //          is supposed to be squared.  Chris asked that I not fix it.
        //
        return 0.25 * sqrt((4 * diag0sq * diag1sq) - (rightSq + leftSq - upperSq - lowerSq));
#endif
    }
}

/**
* Assumes 96 DPI (for what?) and computes a ratio based on the supplied
* device DPI.  Google's default level of detail (LOD) threshold is usually
* 128 pixels.
*/
std::size_t TAK::Engine::Formats::OGR::OGRUtils_ComputeAreaThreshold(unsigned DPI)
{
    return static_cast<std::size_t>(64 * std::ceil(DPI * DPI / (96.0 * 96.0)));
}

std::size_t TAK::Engine::Formats::OGR::OGRUtils_ComputeLevelOfDetail(std::size_t threshold, OGREnvelope env)
{
    using namespace atakmap;
    
    // Clamp latitudes between +-85.0511.
    env.MinY = math::clamp(env.MinY, -85.0511, 85.0511);
    env.MaxY = math::clamp(env.MaxY, -85.0511, 85.0511);

    std::size_t level(0);
    double area(computeMapnikArea(level, env));

    // Too small at level 0.
    if (area < threshold) {
        if (!area) {
            area = 0.0002;
        }

        // Guess level (between 1 and 19) to bring area up above the threshold.
        // (Each increase in level quadruples pixel count.)
        static const double log_4(std::log(4));
        level = math::clamp<std::size_t>(static_cast<size_t>(std::ceil(std::log(128.0 / area) / log_4)), 1, 19);
        if (computeMapnikArea(level, env) >= threshold) {
            // Reduce the level to the lowest one that produces an area that
            // meets (or exceeds) the threshold.  (It's already known that level
            // 0 produces an area that's below threshold.)
            while (level > 1 && computeMapnikArea(level - 1, env) >= threshold) {
                --level;
            }
        } else {
            // Increase the level (to a max of 21) to the first one that
            // produces an area that meets (or exceeds) the threshold.
            while (level < 21 && computeMapnikArea(++level, env) < threshold) {
            }
        }
    }

    return level;
}
