#include "elevation/TerrainSlopeAngleLayer.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

TerrainSlopeAngleLayer::TerrainSlopeAngleLayer(const char* name) NOTHROWS :
    AbstractLayer2(name),
    alpha_(0.9f)
{}
TerrainSlopeAngleLayer::~TerrainSlopeAngleLayer() NOTHROWS
{}

TAKErr TerrainSlopeAngleLayer::setAlpha(float v) NOTHROWS
{
    if (v > 1.0f || v < 0.0f)
        return TE_InvalidArg;
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);
    alpha_ = v;
    dispatchColorChanged();
    return TE_Ok;
}
float TerrainSlopeAngleLayer::getAlpha() const NOTHROWS
{
    Lock lock(mutex_);
    return alpha_;
}
TAKErr TerrainSlopeAngleLayer::addListener(SlopeAngleListener &l) NOTHROWS
{
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);

    auto entry = listeners_.insert(&l);
    return (entry.second) ? TE_Ok : TE_InvalidArg;
}
TAKErr TerrainSlopeAngleLayer::removeListener(const SlopeAngleListener &l) NOTHROWS
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
void TerrainSlopeAngleLayer::dispatchColorChanged() NOTHROWS
{
    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onColorChanged(*this, alpha_) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}

TerrainSlopeAngleLayer::SlopeAngleListener::~SlopeAngleListener() NOTHROWS
{}
