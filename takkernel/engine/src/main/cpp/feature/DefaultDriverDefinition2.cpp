#include "feature/DefaultDriverDefinition2.h"

#include "feature/Style.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "util/ConfigOptions.h"
#include "util/IO.h"

#include "ogr_feature.h"
#include "ogr_geometry.h"
#include "ogrsf_frmts.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

unsigned int DefaultDriverDefinition2::currentColor = 0;
const unsigned int DefaultDriverDefinition2::colors[] = {
        0xFFFFFFFF, // White
        0xFFFFFF00, // Yellow
        0xffff7700, // Orange
        0xFFFF00FF, // Magenta
        0xFFFF0000, // Red
        0xff7f0000, // Brown
        0xff7f007f, // Purple
        0xff00007f, // Navy
        0xFF0000FF, // Blue
        0xFF00FFFF, // Cyan
        0xff007f7f, // Turquoise
        0xFF00FF00, // Green
        0xff007f00, // Forest
        0xff777777, // Gray
        0xFF000000, // Black
};

namespace
{
    TAKErr createDefaultLineStringStyleImpl(TAK::Engine::Port::String &value, const int strokeColor, const float strokeWidth) NOTHROWS;
    TAKErr createDefaultPointStyleImpl(TAK::Engine::Port::String &value, const int strokeColor) NOTHROWS;
    TAKErr createDefaultPolygonStyleImpl(TAK::Engine::Port::String &value, const int strokeColor) NOTHROWS;
}

DefaultDriverDefinition2::DefaultDriverDefinition2(const char* driverName_,
                                                   const char* driverType_,
                                                   unsigned int version_) NOTHROWS :
    driverName(driverName_),
    driverType(driverType_),
    version(version_),
    encoding(atakmap::feature::FeatureDataSource::FeatureDefinition::WKB),
    strokeWidth(2.0),
    strokeColor(0xFFFFFFFF)
{
    TAK::Engine::Port::String strokeWidthPref;
    if(ConfigOptions_getOption(strokeWidthPref, "TAK.Engine.Feature.DefaultDriverDefinition2.defaultStrokeWidth") == TE_Ok) {
        double d;
        if(TAK::Engine::Port::String_parseDouble(&d, strokeWidthPref) == TE_Ok)
            strokeWidth = (float)d;
    }
    TAK::Engine::Port::String strokeColorPref;
    if(ConfigOptions_getOption(strokeColorPref, "TAK.Engine.Feature.DefaultDriverDefinition2.defaultStrokeColor") == TE_Ok) {
        int i;
        if(TAK::Engine::Port::String_parseInteger(&i, strokeColorPref) == TE_Ok)
            strokeColor = i;

    }
}

DefaultDriverDefinition2::DefaultDriverDefinition2 (const char* driverName_,
                                                    const char* driverType_,
                                                    unsigned int version_,
                                                    atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding encoding_,
                                                    float strokeWidth_,
                                                    unsigned int strokeColor_) NOTHROWS :
    driverType(driverType_),
    version(version_),
    encoding(encoding_),
    strokeWidth(strokeWidth_),
    strokeColor(strokeColor_)
{}
 
TAKErr DefaultDriverDefinition2::setGeometry(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition>& featureDefinition,
                                             const OGRFeature& feature, const OGRGeometry& geometry) const NOTHROWS {
    switch (getFeatureEncoding()) {
        case atakmap::feature::FeatureDataSource::FeatureDefinition::WKB: {
            OGRwkbByteOrder byteOrder(atakmap::util::ENDIAN_BYTE ? wkbNDR : wkbXDR);
            std::size_t buffSize(geometry.WkbSize());
            TAK::Engine::Util::array_ptr<unsigned char> buff(new unsigned char[buffSize]);

            geometry.exportToWkb(byteOrder, buff.get(), wkbVariantIso);
            atakmap::feature::FeatureDataSource::FeatureDefinition::ByteBuffer wkb(buff.get(), buff.get() + buffSize);
            featureDefinition->setGeometry(wkb, atakmap::feature::FeatureDataSource::FeatureDefinition::WKB);
        }
            return TE_Ok;
        case atakmap::feature::FeatureDataSource::FeatureDefinition::WKT: {
            char* buff(nullptr);
            geometry.exportToWkt(&buff);
            std::unique_ptr<char, decltype(&OGRFree)> cleanup(buff, OGRFree);
            featureDefinition->setGeometry(buff);
        }
            return TE_Ok;
        default:
            return TE_InvalidArg;
    }
}

TAKErr DefaultDriverDefinition2::getStyle(TAK::Engine::Port::String &value, const OGRFeature& feature, const OGRGeometry& geometry) NOTHROWS
{
    const char* result(const_cast<OGRFeature&> (feature).GetStyleString());

    if (!result)
    {
        switch (geometry.getGeometryType())
        {
        case wkbPoint:
        case wkbMultiPoint:
        case wkbPoint25D:
        case wkbMultiPoint25D:
            result = getDefaultPointStyle();
            break;
        case wkbLineString:
        case wkbMultiLineString:
        case wkbLinearRing:
        case wkbLineString25D:
        case wkbMultiLineString25D:
            result = getDefaultLineStringStyle();
            break;
        case wkbPolygon:
        case wkbMultiPolygon:
        case wkbPolygon25D:
        case wkbMultiPolygon25D:
            result = getDefaultPolygonStyle();
            break;
        default:
            result = getDefaultLineStringStyle();
        }
    }

    value = result;
    return TE_Ok;
}

TAKErr DefaultDriverDefinition2::createDefaultLineStringStyle(TAK::Engine::Port::String &value) const NOTHROWS
{
    return createDefaultLineStringStyleImpl(value, strokeColor, strokeWidth);
}

TAKErr DefaultDriverDefinition2::createDefaultPointStyle(TAK::Engine::Port::String &value) const NOTHROWS
{
    return createDefaultPointStyleImpl(value, strokeColor);
}

TAKErr DefaultDriverDefinition2::createDefaultPolygonStyle(TAK::Engine::Port::String &value) const NOTHROWS
{
    return createDefaultPolygonStyleImpl(value, strokeColor);
}

const char* DefaultDriverDefinition2::getDefaultLineStringStyle() const NOTHROWS
{
    if (!lineStringStyle)
    {
        TAKErr code = createDefaultLineStringStyle(lineStringStyle);
        if (code != TE_Ok)
            createDefaultLineStringStyleImpl(lineStringStyle, strokeColor, strokeWidth);
    }

    return lineStringStyle;
}


const char* DefaultDriverDefinition2::getDefaultPointStyle() const NOTHROWS
{
    if (!pointStyle)
    {
        TAKErr code = createDefaultPointStyle(pointStyle);
        if (code != TE_Ok)
            createDefaultPointStyleImpl(pointStyle, strokeColor);
    }

    return pointStyle;
}


const char* DefaultDriverDefinition2::getDefaultPolygonStyle() const NOTHROWS
{
    if (!polygonStyle)
    {
        TAKErr code = createDefaultPolygonStyle(polygonStyle);
        if (code != TE_Ok)
            createDefaultPolygonStyleImpl(polygonStyle, strokeColor);
    }

    return polygonStyle;
}

const char* DefaultDriverDefinition2::getDriverName() const NOTHROWS
{
    return driverName;
}

atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding DefaultDriverDefinition2::getFeatureEncoding() const NOTHROWS
{
    return encoding;
}

const char* DefaultDriverDefinition2::getType() const NOTHROWS
{
    return driverType;
}

bool DefaultDriverDefinition2::layerNameIsPath() const NOTHROWS
{
    return false;
}

unsigned int DefaultDriverDefinition2::parseVersion() const NOTHROWS
{
    return version;
}

TAKErr DefaultDriverDefinition2::skipFeature(bool *value, const OGRFeature&) NOTHROWS
{
    *value = false;
    return TE_Ok;
}

TAKErr DefaultDriverDefinition2::skipLayer(bool *value, const OGRLayer&) NOTHROWS
{
    currentColor = (currentColor + 1) % (sizeof(colors) / sizeof(unsigned int));
    strokeColor = colors[currentColor];
    *value = false;
    return TE_Ok;
}

namespace
{
    TAKErr createDefaultLineStringStyleImpl(TAK::Engine::Port::String &value, const int strokeColor, const float strokeWidth) NOTHROWS
    {
        return atakmap::feature::BasicStrokeStyle(strokeColor, strokeWidth).toOGR(value);
    }

    TAKErr createDefaultPointStyleImpl(TAK::Engine::Port::String &value, const int strokeColor) NOTHROWS
    {
        TAK::Engine::Port::String defaultIconUri;
        if(ConfigOptions_getOption(defaultIconUri, "TAK.Engine.Feature.DefaultDriverDefinition2.defaultIconUri") == TE_Ok) {
            std::ostringstream strm;
            strm << "SYMBOL(id:";
            strm << defaultIconUri;
            strm << ", c : #FFFFFFFF)";

            value = strm.str().c_str();
            return TE_Ok;
        } else {
            return atakmap::feature::BasicPointStyle(strokeColor, static_cast<float>(TAK::Engine::Renderer::Core::GLMapRenderGlobals_getNominalIconSize())).toOGR(value);
        }
    }

    TAKErr createDefaultPolygonStyleImpl(TAK::Engine::Port::String &value, const int strokeColor) NOTHROWS
    {
        return atakmap::feature::BasicFillStyle(strokeColor).toOGR(value);
    }
}
