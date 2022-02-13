#include "renderer/raster/tilereader/TileReadRequestPrioritizer.h"

#include "math/Rectangle.h"
#include "math/Utils.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Raster::TileReader;

namespace
{
    bool compareLevel(const bool prioritizeLoRes, const int a, const int b) NOTHROWS
    {
        if (a == b)
            return false;
        else
            return prioritizeLoRes ? (a < b) : (a > b);
    }
}

TileReadRequestPrioritizer::TileReadRequestPrioritizer(const GLQuadTileNode2::Options& opts) NOTHROWS :
    opts_(opts),
    focus_x_(0LL),
    focus_y_(0LL)
{}
void TileReadRequestPrioritizer::update(const int64_t focusX, const int64_t focusY, const atakmap::math::Rectangle<double> *rois, const std::size_t numRois) NOTHROWS
{
    this->focus_x_ = focusX;
    this->focus_y_ = focusY;
    this->rois_.clear();
    for (std::size_t i = 0u; i < numRois; i++)
        this->rois_.push_back(rois[i]);
}
bool TileReadRequestPrioritizer::compare(const TileReader2::ReadRequest& a, const TileReader2::ReadRequest& b) NOTHROWS
{
    const bool HI_PRI = false;
    const bool LO_PRI = true;

    bool aIsect = false;
    bool bIsect = false;
    for(std::size_t i = 0u; i < rois_.size(); i++) {
        aIsect |= atakmap::math::Rectangle<int64_t>::intersects((int64_t)rois_[i].x, (int64_t)rois_[i].y, (int64_t)(rois_[i].x+rois_[i].width), (int64_t)(rois_[i].y+rois_[i].height), a.srcX, a.srcY, a.srcX+a.srcW-1, a.srcY+a.srcH-1);
        bIsect |= atakmap::math::Rectangle<int64_t>::intersects((int64_t)rois_[i].x, (int64_t)rois_[i].y, (int64_t)(rois_[i].x+rois_[i].width), (int64_t)(rois_[i].y+rois_[i].height), b.srcX, b.srcY, b.srcX+b.srcW-1, b.srcY+b.srcH-1);
        if(aIsect && bIsect)
            break;
    }

    // if both requests intersect the ROI(s), prioritize based on level
    if(aIsect && bIsect && a.level != b.level)
        return compareLevel(opts_.progressiveLoad, a.level, b.level);
    else if(aIsect && !bIsect)
        return HI_PRI; // only A intersects ROI(s)
    else if(!aIsect && bIsect)
        return LO_PRI; // only B intersects ROI(s)

    // either A and B both intersect ROIs (at same level) or neither do. In
    // both cases, we're going to prioritize based on distance of the
    // requested tile from the POI
    const bool aContains = atakmap::math::Rectangle<int64_t>::contains(a.srcX, a.srcY, a.srcX+a.srcW-1, a.srcY+a.srcH-1, this->focus_x_, this->focus_y_);
    const bool bContains = atakmap::math::Rectangle<int64_t>::contains(b.srcX, b.srcY, b.srcX+b.srcW-1, b.srcY+b.srcH-1, this->focus_x_, this->focus_y_);
    if(aContains && bContains)
        return compareLevel(opts_.progressiveLoad, a.level, b.level); // both contains, prioritize based on level
    if(aContains && !bContains)
        return HI_PRI; // A contains
    else if(!aContains && bContains)
        return LO_PRI; // B contains
    const double aClosestX = (double)atakmap::math::clamp(focus_x_, a.srcX, a.srcX+a.srcW-1);
    const double aClosestY = (double)atakmap::math::clamp(focus_y_, a.srcY, a.srcY+a.srcH-1);
    const double bClosestX = (double)atakmap::math::clamp(focus_x_, b.srcX, b.srcX+b.srcW-1);
    const double bClosestY = (double)atakmap::math::clamp(focus_y_, b.srcY, b.srcY+b.srcH-1);
    const double aDistSq = (aClosestX-focus_x_)*(aClosestX-focus_x_) + (aClosestY-focus_y_)*(aClosestY-focus_y_);
    const double bDistSq = (bClosestX-focus_x_)*(bClosestX-focus_x_) + (bClosestY-focus_y_)*(bClosestY-focus_y_);
    if(aDistSq < bDistSq)
        return HI_PRI; // A is closer than B
    else if(aDistSq > bDistSq)
        return LO_PRI; // B is closer than A

    // prioritize based on level
    return compareLevel(opts_.progressiveLoad, a.level, b.level);
}
