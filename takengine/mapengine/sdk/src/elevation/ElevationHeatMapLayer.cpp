#include "elevation/ElevationHeatMapLayer.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

ElevationHeatMapLayer::ElevationHeatMapLayer(const char* name) NOTHROWS :
    AbstractLayer2(name),
    saturation_(0.5f),
    value_(0.5f),
    alpha_(0.9f),
    min_el_(-100.0),
    max_el_(8850.0)
{}
ElevationHeatMapLayer::~ElevationHeatMapLayer() NOTHROWS
{}

TAKErr ElevationHeatMapLayer::setSaturation(float v) NOTHROWS
{
    if (v > 1.0f || v < 0.0f)
        return TE_InvalidArg;
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);
    saturation_ = v;
    dispatchColorChanged();
    return TE_Ok;
}
TAKErr ElevationHeatMapLayer::setValue(float v) NOTHROWS
{
    if (v > 1.0f || v < 0.0f)
        return TE_InvalidArg;
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);
    value_ = v;
    dispatchColorChanged();
    return TE_Ok;
}
TAKErr ElevationHeatMapLayer::setAlpha(float v) NOTHROWS
{
    if (v > 1.0f || v < 0.0f)
        return TE_InvalidArg;
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);
    alpha_ = v;
    dispatchColorChanged();
    return TE_Ok;
}
float ElevationHeatMapLayer::getSaturation() const NOTHROWS
{
    Lock lock(mutex_);
    return saturation_;
}
float ElevationHeatMapLayer::getValue() const NOTHROWS
{
    Lock lock(mutex_);
    return value_;
}
float ElevationHeatMapLayer::getAlpha() const NOTHROWS
{
    Lock lock(mutex_);
    return alpha_;
}
void ElevationHeatMapLayer::setDynamicRange() NOTHROWS
{
    Lock lock(mutex_);
    min_el_ = NAN;
    max_el_ = NAN;
    dispatchRangeChanged();
}
TAKErr ElevationHeatMapLayer::setAbsoluteRange(const double min, const double max) NOTHROWS
{
    if (isnan(min) || isnan(max))
        return TE_InvalidArg;
    if (min >= max)
        return TE_InvalidArg;
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);
    min_el_ = min;
    max_el_ = max;
    dispatchRangeChanged();
    return TE_Ok;
}
bool ElevationHeatMapLayer::isDynamicRange() const NOTHROWS
{
    return isnan(min_el_) || isnan(max_el_);
}
TAKErr ElevationHeatMapLayer::getAbsoluteRange(double* min, double* max) const NOTHROWS
{
    if (!min || !max)
        return TE_InvalidArg;
    if (isnan(min_el_) || isnan(max_el_))
        return TE_IllegalState;
    *min = min_el_;
    *max = max_el_;
    return TE_Ok;
}
TAKErr ElevationHeatMapLayer::addListener(HeatMapListener &l) NOTHROWS
{
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);

    auto entry = listeners_.insert(&l);
    return (entry.second) ? TE_Ok : TE_InvalidArg;
}
TAKErr ElevationHeatMapLayer::removeListener(const HeatMapListener &l) NOTHROWS
{
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);

    for (auto it = listeners_.begin(); it != listeners_.end(); it++) {
        if (&l == (*it)) {
            listeners_.erase(it);
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}
void ElevationHeatMapLayer::dispatchColorChanged() NOTHROWS
{
    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onColorChanged(*this, saturation_, value_, alpha_) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}
void ElevationHeatMapLayer::dispatchRangeChanged() NOTHROWS
{
    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onRangeChanged(*this, min_el_, max_el_, isnan(min_el_)||isnan(max_el_)) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}

ElevationHeatMapLayer::HeatMapListener::~HeatMapListener() NOTHROWS
{}
