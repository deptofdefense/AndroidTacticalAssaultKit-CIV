
#include "renderer/core/GLAntiMeridianHelper.h"

#include <algorithm>

#include "renderer/core/GLMapView2.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

GLAntiMeridianHelper::GLAntiMeridianHelper() :
    primaryHemisphere(GLAntiMeridianHelper::East),
    eastHemisphere(0, 0, NAN, 0, 0, NAN),
    westHemisphere(0, 0, NAN, 0, 0, NAN),
    wrap(false)
{}

void GLAntiMeridianHelper::update(TAK::Engine::Renderer::Core::GLMapView2& view) NOTHROWS {
    //TODO ADD THIS WHEN WE SWAP OVER TO MAPVIEW2
    //this->wrap = view.crossesIDL;

    this->primaryHemisphere = (view.drawLng < 0) ? GLAntiMeridianHelper::West : GLAntiMeridianHelper::East;

    if (this->wrap) {
        // eastern hemi
        
        this->eastHemisphere = Envelope2(std::min(view.westBound, 180.0), std::min(view.southBound, view.northBound), NAN, std::max(view.southBound, view.northBound), std::max(view.westBound, 180.0), NAN);
        //this->eastHemisphere.set(view.northBound, view.westBound,
        //    view.southBound, 180);
        // western hemi
        this->westHemisphere = Envelope2(std::min(view.westBound, -180.0), std::min(view.southBound, view.northBound), NAN, std::max(view.southBound, view.northBound), std::max(view.westBound, -180.0), NAN);
        //this->westHemisphere.set(view.northBound, -180,
        //   view.southBound, view.eastBound);
    } else {
        // eastern hemi
        this->eastHemisphere = Envelope2(std::min(view.westBound, view.eastBound), std::min(view.southBound, view.northBound), NAN, std::max(view.southBound, view.northBound), std::max(view.westBound, view.eastBound), NAN);
        //this->eastHemisphere.set(view.northBound, view.westBound,
        //    view.southBound, view.eastBound);
        // western hemi
        this->westHemisphere = Envelope2(std::min(view.westBound, view.eastBound), std::min(view.southBound, view.northBound), NAN, std::max(view.southBound, view.northBound), std::max(view.westBound, view.eastBound), NAN);
        //this->westHemisphere.set(view.northBound, view.westBound,
        //    view.southBound, view.eastBound);
    }
}

GLAntiMeridianHelper::Hemisphere GLAntiMeridianHelper::getPrimaryHemisphere() const NOTHROWS
{
    return this->primaryHemisphere;
}

TAKErr GLAntiMeridianHelper::getBounds(Envelope2 *value, const GLAntiMeridianHelper::Hemisphere hemisphere) const NOTHROWS
{
    switch (hemisphere) {
    case GLAntiMeridianHelper::East:
        *value = this->eastHemisphere;
        break;
    case GLAntiMeridianHelper::West:
        *value = this->westHemisphere;
        break;
    default:
        return TAKErr::TE_InvalidArg;
        //throw new IllegalArgumentException();
    }
    return TAKErr::TE_Ok;
}

void GLAntiMeridianHelper::getBounds(Envelope2 *westHemi, Envelope2 *eastHemi) const NOTHROWS
{
    *westHemi = this->westHemisphere;
    *eastHemi = this->eastHemisphere;
}

TAKErr GLAntiMeridianHelper::wrapLongitude(GeoPoint2 *value, const GLAntiMeridianHelper::Hemisphere hemisphere, const GeoPoint2 &src) const NOTHROWS{
    if (!this->wrap && hemisphere == this->primaryHemisphere && *value == src)
        return TE_Ok;
    
    double lng;
    TAKErr code = wrapLongitude(&lng, hemisphere, src.longitude);
    TE_CHECKRETURN_CODE(code);
    *value = src;
    value->longitude = lng;

    return code;
}

TAKErr GLAntiMeridianHelper::wrapLongitude(double *value, const GLAntiMeridianHelper::Hemisphere hemisphere, const double longitude) const NOTHROWS
{
    if (!this->wrap || hemisphere == this->primaryHemisphere) {
        *value = longitude;
    } else if (this->primaryHemisphere == GLAntiMeridianHelper::West) {
        *value = longitude - 360;
    } else if (this->primaryHemisphere == GLAntiMeridianHelper::East) {
        *value = longitude + 360;
    } else {
        //throw new IllegalArgumentException();
        return TAKErr::TE_InvalidArg;
    }
    return TAKErr::TE_Ok;
}

TAKErr GLAntiMeridianHelper::wrapLongitude(double *value, const double longitude) const NOTHROWS
{
    GLAntiMeridianHelper::Hemisphere hemisphere;
    if (!this->wrap) {
        *value = longitude;
        return TAKErr::TE_Ok;
    } else if ((longitude >= this->eastHemisphere.minX/*.getWest()*/ && longitude <= this->eastHemisphere.maxX/*.getEast()*/)) {
        hemisphere = GLAntiMeridianHelper::East;
    } else if ((longitude >= this->westHemisphere.minX/*.getWest()*/ && longitude <= this->westHemisphere.maxX/*.getEast()*/)) {
        hemisphere = GLAntiMeridianHelper::West;
    } else {
        *value = longitude; // out of bounds, don't wrap
        return TAKErr::TE_Ok;
    }

    return wrapLongitude(value, hemisphere, longitude);
}
