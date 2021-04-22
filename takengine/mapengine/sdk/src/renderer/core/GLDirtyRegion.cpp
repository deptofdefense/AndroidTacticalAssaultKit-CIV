#include "renderer/core/GLDirtyRegion.h"

#include "feature/SpatialCalculator2.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Feature;

namespace
{
    bool intersects(const Envelope2& a, const Envelope2* b, const std::size_t n) NOTHROWS
    {
        for (std::size_t i = 0; i < n; i++)
            if (SpatialCalculator_intersects(a, b[i]))
                return true;
        return false;
    }
}

GLDirtyRegion::GLDirtyRegion() NOTHROWS
{}
GLDirtyRegion::~GLDirtyRegion() NOTHROWS
{}
void GLDirtyRegion::clear() NOTHROWS
{
    east.regions.clear();
    west.regions.clear();
}
void GLDirtyRegion::push_back(const double ulLat, const double ulLng, const double lrLat, const double lrLng) NOTHROWS
{
    if (ulLng > lrLng) {
        push_back(Envelope2(-180.0, lrLat, 0.0, lrLng, ulLat, 0.0));
        push_back(Envelope2(ulLng, lrLat, 0.0, 180.0, ulLat, 0.0));
    } else {
        push_back(Envelope2(ulLng, lrLat, 0.0, lrLng, ulLat, 0.0));
    }
}
void GLDirtyRegion::push_back(const Envelope2 &region) NOTHROWS
{
    if (region.minX < 0.0) {
        if (west.regions.empty())
            west.mbb = region;
        else
            west.mbb = SpatialCalculator_union(west.mbb, region);
        west.regions.push_back(region);
    }
    if (region.maxX > 0.0) {
        if (east.regions.empty())
            east.mbb = region;
        else
            east.mbb = SpatialCalculator_union(east.mbb, region);
        east.regions.push_back(region);
    }
}
bool GLDirtyRegion::intersects(const Envelope2& region) const NOTHROWS
{
    if (west.regions.size() && SpatialCalculator_intersects(west.mbb, region) && ::intersects(region, &west.regions.at(0), west.regions.size()))
        return true;
    if (east.regions.size() && SpatialCalculator_intersects(east.mbb, region) && ::intersects(region, &east.regions.at(0), east.regions.size()))
        return true;
    return false;
}
bool GLDirtyRegion::empty() const NOTHROWS
{
    return (west.regions.empty() && east.regions.empty());
}
std::size_t GLDirtyRegion::size() const NOTHROWS
{
    return (west.regions.size() + east.regions.size());
}
void GLDirtyRegion::compact() NOTHROWS
{
    if (empty())
        return;
    west.regions.clear();
    west.regions.push_back(west.mbb);
    east.regions.clear();
    east.regions.push_back(east.mbb);
}
GLDirtyRegion& GLDirtyRegion::operator =(const GLDirtyRegion& other) NOTHROWS
{
    this->east = other.east;
    this->west = other.west;
    return *this;
}
