#include "formats/ogr/AutoStyleSchemaHandler.h"

#include "feature/Style.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"

using namespace TAK::Engine::Formats::OGR;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
#define DEFAULT_PALETTE_LIMIT 18u
    unsigned int DEFAULT_COLOR_PALETTE[DEFAULT_PALETTE_LIMIT] =
    {
        0xFFD5CF3C,
        0xFF729B6F,
        0xFFF3A6B2,
        0xFF8D5A99,
        0xFFE8718D,
        0xFFFF9E17,
        0xFFB7484B,
        0xFFE77148,
        0xFF987DB7,
        0xFFBECF50,
        0xFFE17DA2,
        0xFFE5B636,
        0xFFE23835,
        0xFFBEB297,
        0xFF783711,
        0xFF52828F,
        0xFF8EC478,
        0xFFCCA28D,
    };

    TAKErr createDefaultLineStringStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor, const float strokeWidth) NOTHROWS;
    TAKErr createDefaultPointStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor) NOTHROWS;
    TAKErr createDefaultPolygonStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor) NOTHROWS;
}

AutoStyleSchemaHandler::AutoStyleSchemaHandler() NOTHROWS :
    AutoStyleSchemaHandler(DEFAULT_COLOR_PALETTE, DEFAULT_PALETTE_LIMIT)
{}
AutoStyleSchemaHandler::AutoStyleSchemaHandler(const unsigned int *palette_, const std::size_t paletteSize_) NOTHROWS :
    palette(new(std::nothrow) unsigned int[paletteSize_]),
    paletteInUse(new(std::nothrow) bool[paletteSize_]),
    paletteSize(paletteSize_)
{
    memcpy(palette.get(), palette_, sizeof(unsigned int) * paletteSize_);
    memset(paletteInUse.get(), 0u, sizeof(bool) * paletteSize_);
}
AutoStyleSchemaHandler::~AutoStyleSchemaHandler() NOTHROWS
{}

TAKErr AutoStyleSchemaHandler::ignoreLayer(bool* value, OGRLayerH layer) const NOTHROWS
{
    *value = false;
    return TE_Ok;
}
bool AutoStyleSchemaHandler::styleRequiresAttributes() const NOTHROWS
{
    return false;
}
TAKErr AutoStyleSchemaHandler::getFeatureStyle(TAK::Engine::Feature::StylePtr_const& value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet& attribs) NOTHROWS
{
    const char* ogrstyle(OGR_F_GetStyleString(feature));
    if (ogrstyle && atakmap::feature::Style_parseStyle(value, ogrstyle) == TE_Ok)
        return TE_Ok;

    Lock lock(mutex);
    const char* cname = OGR_L_GetName(layer);
    auto entry = layerStyle.find(cname);
    LayerStyle *style;
    if (entry != layerStyle.end()) {
        style = &entry->second;
    } else {
        // hash the name
        unsigned int hashidx = std::hash<std::string>()(cname)%(unsigned int)paletteSize;

        // check for collision
        if (paletteInUse[hashidx]) {
            // use first free entry, if all entries are in use, the original hash index remains intact
            for (std::size_t i = 0u; i < paletteSize; i++) {
                if (!paletteInUse[i]) {
                    hashidx = (unsigned int)i;
                    break;
                }
            }
        }

        // add entry
        paletteInUse[hashidx] = true;

        // initialize entry
        style = &layerStyle[cname];

        // populate entry
        createDefaultPointStyleImpl(style->point, palette[hashidx]);
        createDefaultLineStringStyleImpl(style->linestring, palette[hashidx], 2.f);
        createDefaultPolygonStyleImpl(style->polygon, palette[hashidx]);
    }

    OGRGeometryH geometry = OGR_F_GetGeometryRef(feature);
    const atakmap::feature::Style* s = nullptr;
    switch (OGR_G_GetGeometryType(geometry))
    {
    case wkbPoint:
    case wkbMultiPoint:
    case wkbPoint25D:
    case wkbMultiPoint25D:
        s = style->point.get();
        break;
    case wkbLineString:
    case wkbMultiLineString:
    case wkbLinearRing:
    case wkbLineString25D:
    case wkbMultiLineString25D:
        s = style->linestring.get();
        break;
    case wkbPolygon:
    case wkbMultiPolygon:
    case wkbPolygon25D:
    case wkbMultiPolygon25D:
        s = style->polygon.get();
        break;
    default:
        s = style->point.get();
        break;
    }

    value = TAK::Engine::Feature::StylePtr_const(s, Memory_leaker_const<atakmap::feature::Style>);
    return TE_Ok;
}
TAKErr AutoStyleSchemaHandler::getFeatureName(TAK::Engine::Port::String& value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet& attribs) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Port::String nameCol;
    auto entry = nameColumn.find(OGR_L_GetName(layer));
    if (entry == nameColumn.end()) {
        OGRFeatureDefnH def = OGR_L_GetLayerDefn(layer);
        if (def) {
            int fieldCount = OGR_FD_GetFieldCount(def);
            std::list<std::string> candidates;
            for (int i = 0; i < fieldCount; i++) {
                OGRFieldDefnH fieldDef = OGR_FD_GetFieldDefn(def, i);
                if (!fieldDef)
                    continue;
                const char *cfieldName = OGR_Fld_GetNameRef(fieldDef);
                if (!cfieldName)
                    continue;

#ifdef MSVC
                if(_stricmp("name", cfieldName) == 0) {
#else
                if(strcasecmp("name", cfieldName) == 0) {
#endif
                    nameCol = cfieldName;
                    break;
                } else {
                    std::string fieldName(cfieldName);

                    std::transform(fieldName.begin(), fieldName.end(), fieldName.begin(), ::tolower);

                    if (strstr(fieldName.c_str(), "name"))
                        candidates.push_back(cfieldName);
                }
            }

            if (!nameCol && !candidates.empty())
                nameCol = (*candidates.begin()).c_str();
        }
        this->nameColumn[OGR_L_GetName(layer)] = nameCol;
    } else {
        nameCol = entry->second;
    }

    value = nullptr;

    try {
        if (nameCol &&
            attribs.containsAttribute(nameCol) &&
            (attribs.getAttributeType(nameCol) == atakmap::util::AttributeSet::STRING)) {

            value = attribs.getString(nameCol);
        }
        return code;
    } catch (...) {
        return TE_Err;
    }
}
TAKErr AutoStyleSchemaHandler::getFeatureSetName(TAK::Engine::Port::String& value, OGRLayerH layer) NOTHROWS
{
    value = OGR_L_GetName(layer);
    return TE_Ok;
}

namespace
{
    TAKErr createDefaultLineStringStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor, const float strokeWidth) NOTHROWS
    {
        TAKErr code(TE_Ok);
        TAK::Engine::Feature::StylePtr style(nullptr, nullptr);
        code = atakmap::feature::BasicStrokeStyle_create(style, strokeColor, strokeWidth);
        TE_CHECKRETURN_CODE(code);

        value = std::move(style);
        return code;
    }

    TAKErr createDefaultPointStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor) NOTHROWS
    {
        TAK::Engine::Port::String defaultIconUri;
        if (ConfigOptions_getOption(defaultIconUri, "TAK.Engine.Feature.DefaultDriverDefinition2.defaultIconUri") == TE_Ok) {
            atakmap::feature::IconPointStyle style(strokeColor, defaultIconUri);
            value = TAK::Engine::Feature::StylePtr_const(style.clone(), atakmap::feature::Style::destructStyle);
        }
        else {
            atakmap::feature::BasicPointStyle style(strokeColor, static_cast<float>(TAK::Engine::Renderer::Core::GLMapRenderGlobals_getNominalIconSize()));
            value = TAK::Engine::Feature::StylePtr_const(style.clone(), atakmap::feature::Style::destructStyle);
        }
        return TE_Ok;
    }

    TAKErr createDefaultPolygonStyleImpl(TAK::Engine::Feature::StylePtr_const& value, const int strokeColor) NOTHROWS
    {
        TAKErr code(TE_Ok);
#if 0
        atakmap::feature::BasicFillStyle fill(strokeColor);
        atakmap::feature::BasicStrokeStyle stroke(0xFF000000u, 1.f);
        const atakmap::feature::Style* styles[2u] =
        {
            &fill,
            &stroke,
        };
                
        TAK::Engine::Feature::StylePtr style(nullptr, nullptr);
        code = atakmap::feature::CompositeStyle_create(style, styles, 2u);
        TE_CHECKRETURN_CODE(code);

        
#else
        TAK::Engine::Feature::StylePtr style(nullptr, nullptr);
        atakmap::feature::BasicFillStyle_create(style, strokeColor);

        value = std::move(style);
#endif
        return TE_Ok;
    }
}
