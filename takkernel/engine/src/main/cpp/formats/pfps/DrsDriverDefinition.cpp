#include "formats/pfps/DrsDriverDefinition.h"

#include <ogr_feature.h>
#include <ogr_geometry.h>
#include <ogrsf_frmts.h>

#include "feature/LineString.h"
#include "feature/ParseGeometry.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/SQLiteDriverDefinition.h"
#include "feature/Style.h"
#include "util/IO.h"
#include "util/Memory.h"

using namespace atakmap::feature;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::Pfps;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

#define DRS_DRIVER_TYPE "drs"

DrsDriverDefinition::DrsDriverDefinition() NOTHROWS : DefaultDriverDefinition2(SQLITE_DRIVER_NAME, DRS_DRIVER_TYPE, 1) {}

TAKErr DrsDriverDefinition::setGeometry(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition>& featureDefinition,
                                        const OGRFeature& feature, const OGRGeometry& geometry) const NOTHROWS {
    const char* font = feature.GetFieldAsString("fontname");
    if (font != nullptr && std::strlen(font) > 0) {
        double latitude = feature.GetFieldAsDouble("latitude");
        double longitude = feature.GetFieldAsDouble("longitude");
        featureDefinition->setGeometry(new atakmap::feature::Point(longitude, latitude));
        return TE_Ok;
    } else {
        OGRwkbByteOrder byteOrder(atakmap::util::ENDIAN_BYTE ? wkbNDR : wkbXDR);
        std::size_t buffSize(geometry.WkbSize());
        TAK::Engine::Util::array_ptr<unsigned char> buff(new unsigned char[buffSize]);

        geometry.exportToWkb(byteOrder, buff.get(), wkbVariantIso);
        atakmap::feature::FeatureDataSource::FeatureDefinition::ByteBuffer wkb(buff.get(), buff.get() + buffSize);
        atakmap::feature::GeometryPtr geo(atakmap::feature::parseWKB(wkb), atakmap::feature::destructGeometry);
        if (geo->getType() == atakmap::feature::Geometry::Type::LINESTRING) {
            atakmap::feature::LineString* ls = static_cast<atakmap::feature::LineString*>(geo.get());
            if (ls->isClosed()) {
                atakmap::feature::Polygon* poly = new atakmap::feature::Polygon(*ls);
                featureDefinition->setGeometry(poly);
                return TE_Ok;
            }
        }
        featureDefinition->setGeometry(geo.release());
        return TE_Ok;
    }
}

TAKErr DrsDriverDefinition::getStyle(TAK::Engine::Port::String& value, const OGRFeature& feature, const OGRGeometry& g) NOTHROWS {
    value = const_cast<OGRFeature&>(feature).GetStyleString();
    if (!value || !value[0]) {
        if (getStyleImpl(value, feature, g) == TE_Ok) return TE_Ok;
    }

    return DefaultDriverDefinition2::getStyle(value, feature, g);
}

TAKErr DrsDriverDefinition::getStyleImpl(TAK::Engine::Port::String& value, const OGRFeature& feature, const OGRGeometry& g) NOTHROWS {
    TAKErr code;
    bool has_fill = false;
    unsigned int stroke_color{0};
    int stroke_width{2};
    std::string string_color(feature.GetFieldAsString("color"));
    if (string_color.empty()) {
        string_color = feature.GetFieldAsString("fontcolor");
        if (string_color.length() >= 7) {
            int stroke{-1};
            code = String_parseInteger(&stroke, string_color.substr(1, 6).c_str(), 16);
            TE_CHECKRETURN_CODE(code);
            int bg_color{-1};
            std::string string_background_color(feature.GetFieldAsString("fontbackgroundcolor"));
            if (string_background_color.length() >= 7) {
                code = String_parseInteger(&bg_color, string_background_color.substr(1, 6).c_str(), 16);
                TE_CHECKRETURN_CODE(code);
            }
            return LabelPointStyle(feature.GetFieldAsString("text"), 0xFF000000 | stroke, 0xFF000000 | bg_color,
                                   LabelPointStyle::ScrollMode::DEFAULT)
                .toOGR(value);
        }
    }
    if (string_color.length() >= 10) {
        int stroke{-1};
        code = String_parseInteger(&stroke, string_color.substr(1, 6).c_str(), 16);
        TE_CHECKRETURN_CODE(code);
        stroke_color = static_cast<unsigned int>(stroke);
        code = String_parseInteger(&stroke_width, string_color.substr(8, 2).c_str(), 16);
        TE_CHECKRETURN_CODE(code);
    } else {
        return TE_InvalidArg;
    }
    int fill_type = feature.GetFieldAsInteger("filltype");
    std::string string_fill_color(feature.GetFieldAsString("fillcolor"));
    int fill{0};
    if (string_fill_color.length() >= 10 && fill_type > 0) {
        code = String_parseInteger(&fill, string_fill_color.substr(1, 6).c_str(), 16);
        TE_CHECKRETURN_CODE(code);
        has_fill = true;
    }
    if (has_fill) {
        std::vector<atakmap::feature::Style*> style_vector;
        style_vector.push_back(new BasicStrokeStyle(0xFF000000 | stroke_color, static_cast<float>(stroke_width)));
        unsigned int alpha = 0x22000000;
        switch (fill_type) {
            case 2:
                alpha = 0x44000000;
                break;
            case 3:
                alpha = 0x66000000;
                break;
            case 4:
                alpha = 0x88000000;
                break;
        }
        style_vector.push_back(new BasicFillStyle(alpha | fill));
        code = CompositeStyle(style_vector).toOGR(value);
        if (code == TE_Ok) return TE_Ok;
    }
    return BasicStrokeStyle(0xFF000000 | stroke_color, static_cast<float>(stroke_width)).toOGR(value);
}