
#include "renderer/core/GLOffscreenVertex.h"

#include <cmath>

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

GLOffscreenVertex::GLOffscreenVertex() NOTHROWS :
    proj(NAN, NAN, NAN),
    geo(NAN, NAN, NAN, AltitudeReference::HAE),
    altitudeAdjustedGeo(NAN, NAN, NAN, AltitudeReference::HAE)
{
    clearVersions();
}

GLOffscreenVertex::~GLOffscreenVertex()
{}

void GLOffscreenVertex::clearVersions() NOTHROWS
{
    this->geoVersion = 0;
    this->projVersion = -1;
    this->altVersion = -1;
    this->elevationOffset = 0.0;
    this->elevationScaleFactor = 1.0;
}

void  GLOffscreenVertex::adjust() NOTHROWS
{
    if (!isnan(geo.altitude)) {
        const double alt = this->geo.altitude;
        this->altitudeAdjustedGeo.altitude = (geo.altitude + elevationOffset)*elevationScaleFactor;
    } else {
        this->altitudeAdjustedGeo.altitude = NAN;
    }
}

double GLOffscreenVertex::getElevation() const NOTHROWS
{
    return this->geo.altitude;
}

double GLOffscreenVertex::getAdjustedElevation() const NOTHROWS
{
    return this->altitudeAdjustedGeo.altitude;
}

void GLOffscreenVertex::setLocation(const double latitude, const double longitude) NOTHROWS
{
    this->geo.latitude = latitude;
    this->geo.longitude = longitude;
    this->geo.altitude = NAN;
    this->altitudeAdjustedGeo.latitude = latitude;
    this->altitudeAdjustedGeo.longitude = longitude;
    this->altitudeAdjustedGeo.altitude = NAN;

    this->geoVersion++;
}

void GLOffscreenVertex::setElevation(const double elevation) NOTHROWS
{
    if (this->geo.altitude != elevation)
    {
        this->geo.altitude = elevation;
        this->adjust();
        this->geoVersion++;
    }
}

TAKErr TAK::Engine::Renderer::Core::GLOffscreenVertex_computeElevationStatistics(Statistics *value, const GLOffscreenVertex *vertices, const std::size_t count) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!count)
        return TE_Done;

    for (std::size_t i = 0u; i < count; i++) {
        const double el = vertices[i].getElevation();
        if (!isnan(el))
            value->observe(vertices[i].getElevation());
    }

    return code;
}
