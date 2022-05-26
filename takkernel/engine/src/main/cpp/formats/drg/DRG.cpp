#include "formats/drg/DRG.h"

using namespace TAK::Engine::Formats::DRG;

using namespace TAK::Engine::Util;

namespace
{
    TAKErr parseAsciiUInt(unsigned int *value, const char *str, const std::size_t numChars) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        if(!str)
            return TE_InvalidArg;
        if(!numChars)
            return TE_InvalidArg;
        std::size_t idx = 0u;
        *value = 0;
        do {
            const char digit = str[idx++];
            if(digit < '0' || digit > '9')
                return TE_InvalidArg;
            *value = ((*value)*10) + (unsigned)(digit-'0');
        } while(idx<numChars);
        return TE_Ok;
    }
}

TAKErr TAK::Engine::Formats::DRG::DRG_isDRG(bool *value, const char *filename) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!filename)
        return TE_InvalidArg;
    DRGInfo info;
    *value = (DRG_parseName(&info, filename) == TE_Ok);
    return TE_Ok;
}
TAKErr TAK::Engine::Formats::DRG::DRG_parseName(DRGInfo *value, const char *filename) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!filename)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    std::size_t idx = 0u;
    double extentLat;
    double extentLng;
    switch(filename[idx++]&~0x20u) {
        case 'R' :
            value->category.series = TEDS_7_5min;
            value->category.scale_denom = 20000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.125;
            extentLng = 0.125;
            break;
        case 'O' :
            value->category.series = TEDS_7_5min;
            value->category.scale_denom = 24000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.125;
            extentLng = 0.125;
            break;
        case 'P' :
            value->category.series = TEDS_7_5min;
            value->category.scale_denom = 24000;
            value->category.drg_class = TEDC_Orthopohoto;
            extentLat = 0.125;
            extentLng = 0.125;
            break;
        case 'L' :
            value->category.series = TEDS_7_5min;
            value->category.scale_denom = 25000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.125;
            extentLng = 0.125;
            break;
        case 'J' :
            value->category.series = TEDS_7_5min;
            value->category.scale_denom = 30000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.125;
            extentLng = 0.125;
            break;
        case 'K' :
            value->category.series = TEDS_7_5minx15min;
            value->category.scale_denom = 25000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.125;
            extentLng = 0.25;
            break;
        case 'I' :
            value->category.series = TEDS_Alaska;
            value->category.scale_denom = 63000;
            value->category.drg_class = TEDC_Topographic;
            // XXX - states cells vary in size, can't be handled by name
            return TE_Unsupported;
        case 'G' :
            value->category.series = TEDS_30min_60min;
            value->category.scale_denom = 100000;
            value->category.drg_class = TEDC_Planimetric;
            extentLat = 0.5;
            extentLng = 1.0;
            break;
        case 'F' :
            value->category.series = TEDS_30min_60min;
            value->category.scale_denom = 100000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 0.5;
            extentLng = 1.0;
            break;
        case 'C' :
            value->category.series = TEDS_1degx2deg;
            value->category.scale_denom = 250000;
            value->category.drg_class = TEDC_Topographic;
            extentLat = 1.0;
            extentLng = 2.0;
            break;
        default :
            return TE_InvalidArg;
    }

    unsigned int degLat;
    code = parseAsciiUInt(&degLat, filename+idx, 2u);
    TE_CHECKRETURN_CODE(code);
    idx += 2u;

    unsigned int degLng;
    code = parseAsciiUInt(&degLng, filename+idx, 3u);
    TE_CHECKRETURN_CODE(code);
    idx += 3u;

    if(degLat > 90)
        return TE_InvalidArg;
    if(degLng > 360)
        return TE_InvalidArg;

    value->secondaryCellLatitude = degLat;
    value->secondaryCellLongitude = degLng;
    if(value->secondaryCellLongitude < 180.0)
        value->secondaryCellLongitude *= -1.0;
    else
        value->secondaryCellLongitude = 360.0 - value->secondaryCellLongitude;

    const char mapIndexRowChar = (filename[idx++]&~0x20u);
    if(mapIndexRowChar < 'A' || mapIndexRowChar > 'H')
        return TE_InvalidArg;
    const char mapIndexColChar = filename[idx++];
    if(mapIndexColChar < '1' || mapIndexColChar > '8')
        return TE_InvalidArg;

    // validate extension and string length
    if(filename[idx++] != '.')
        return TE_InvalidArg;
    if((filename[idx++]&~0x20u) != 'T')
        return TE_InvalidArg;
    if((filename[idx++]&~0x20u) != 'I')
        return TE_InvalidArg;
    if((filename[idx++]&~0x20u) != 'F')
        return TE_InvalidArg;
    if(filename[idx++] != '\0')
        return TE_InvalidArg;

    const std::size_t mapIndexRow = (unsigned)(mapIndexRowChar-'A');
    const std::size_t mapIndexCol = (unsigned)(mapIndexColChar-'1');
    value->mapIndexNumber = (mapIndexRow * 8u) + mapIndexCol;

    value->minLat = value->secondaryCellLatitude + (mapIndexRow*0.125);
    value->maxLat = value->minLat + extentLat;
    value->maxLng = value->secondaryCellLongitude - (mapIndexCol*0.125);
    value->minLng = value->maxLng - extentLng;

    return TE_Ok;
}
