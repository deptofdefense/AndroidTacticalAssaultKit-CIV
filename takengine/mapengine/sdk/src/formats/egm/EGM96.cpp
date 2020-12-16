#include "formats/egm/EGM96.h"

#include <cmath>

using namespace TAK::Engine::Formats::EGM;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

#define TAG "EGM96"

// 15' angle delta
#define INTERVAL    (15.0 / 60.0)
#define NUM_ROWS    721u
#define NUM_COLS    1440u

namespace
{

    /**
     * Pull the delta from the table
     *
     * @param row Row to pull from
     * @param col Column to pull from
     * @return Delta at specified location or Double.NaN
     */
    double getPostOffset(const uint8_t *data, const std::size_t dataLen, const std::size_t row, const std::size_t col) NOTHROWS
    {
        const std::size_t k = 2u * ((row * NUM_COLS) + col); // as each value is specified as two
                                            // bytes

        if ((k + 1u) >= dataLen) {
            Logger_log(TELL_Warning, TAG ": Location outside bounds of world: %u", k);
            return NAN;
        }

        return (int16_t)(((data[k]) << 8u) | data[k + 1u]);
    }
}

EGM96::EGM96() NOTHROWS :
    size(0)
{}

EGM96::~EGM96() NOTHROWS
{}

TAKErr EGM96::open(const uint8_t *data_, const std::size_t dataLen_) NOTHROWS
{
    model.reset(new uint8_t[dataLen_]);
    memcpy(model.get(), data_, dataLen_);
    size = dataLen_;

    return TE_Ok;
}
TAKErr EGM96::getHeight(double *value, const GeoPoint2 &location) NOTHROWS
{
    // Return 0 for all offsets if the file failed to load. A log message of
    // the failure will have been generated
    // by the load method.
    if (!model.get()) {
        *value = NAN;
        return TE_IllegalState;
    }

    // wrap longitude
    double lon = location.longitude >= 0 ? location.longitude : location.longitude + 360;

    // throw out bad latitude values
    if(location.latitude > 90.0 || location.latitude < -90.0)
        return TE_InvalidArg;

    auto topRow = (std::size_t) ((90.0 - location.latitude) / INTERVAL);
    if (location.latitude <= -90)
        topRow = NUM_ROWS - 2u;
    std::size_t bottomRow = topRow + 1u;

    // Note that the number of columns does not repeat the column at 0
    // longitude, so we must force the right
    // column to 0 for any longitude that's less than one interval from 360,
    // and force the left column to the
    // last column of the grid.
    auto leftCol = (std::size_t) (lon / INTERVAL);
    std::size_t rightCol = leftCol + 1u;
    if (lon >= 360 - INTERVAL) {
        leftCol = NUM_ROWS - 1u;
        rightCol = 0u;
    }

    double latTop = 90.0 - (topRow * INTERVAL);
    double lonLeft = leftCol * INTERVAL;

    double ul = getPostOffset(model.get(), size, topRow, leftCol);
    double ll = getPostOffset(model.get(), size, bottomRow, leftCol);
    double lr = getPostOffset(model.get(), size, bottomRow, rightCol);
    double ur = getPostOffset(model.get(), size, topRow, rightCol);

    if (::isnan(ul) || ::isnan(ll) || ::isnan(lr)
            || ::isnan(ur)) {

        *value = NAN;
        return TE_InvalidArg;
    }

    double u = (lon - lonLeft) / INTERVAL;
    double v = (latTop - location.latitude) / INTERVAL;

    double pll = (1.0 - u) * (1.0 - v);
    double plr = u * (1.0 - v);
    double pur = u * v;
    double pul = (1.0 - u) * v;

    double offset = pll * ll + plr * lr + pur * ur + pul * ul;
    *value = offset / 100.0; // convert centimeters to meters
    return TE_Ok;
}
