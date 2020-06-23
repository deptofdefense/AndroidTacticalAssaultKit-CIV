#include "feature/OGDIDriverDefinition.h"

#include <ogr_feature.h>
#include <ogr_geometry.h>
#include <ogrsf_frmts.h>

#include "feature/Style.h"
#include "util/ConfigOptions.h"
#include "util/Memory.h"

using namespace atakmap::feature;
using namespace TAK::Engine;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

#define OGDI_DRIVER_NAME "OGR_OGDI"

OGDIDriverDefinition::OGDIDriverDefinition() NOTHROWS :
DefaultDriverDefinition2(OGDI_DRIVER_NAME, "OGDI", 1)
{}

TAKErr OGDIDriverDefinition::getStyle(Port::String& value, const OGRFeature& feature, const OGRGeometry& g) NOTHROWS
{
	value = const_cast<OGRFeature&> (feature).GetStyleString();
	if (!value || !value[0])
	{
		const char* fCode = const_cast<OGRFeature&> (feature).GetFieldAsString("f_code");
		if (fCode != nullptr && strnlen_s(fCode, 5) == 5)
		{
			return getFaccCodeStyle(value, feature, g, fCode);
		}
		return DefaultDriverDefinition2::getStyle(value, feature, g);
	}

	return TE_Ok;
}

TAKErr OGDIDriverDefinition::getFaccCodeStyle(Port::String& value, const OGRFeature& feature, const OGRGeometry& g,
                                              const char* fCode) NOTHROWS {
    bool usePen = false;
    int color = getFaccCodeColor(fCode, usePen);
    switch (g.getGeometryType()) {
        case wkbPoint:
        case wkbMultiPoint:
        case wkbPoint25D:
        case wkbMultiPoint25D: {
            try {
                if (IconPointStyle(0xFF000000 | color, getFaccCodeIcon(fCode), 1.2f).toOGR(value) != TE_Ok)
                    return TE_Err;
                return TE_Ok;
            } catch (...) {
                return TE_Err;
            }
        }
        case wkbPolygon:
        case wkbMultiPolygon:
        case wkbPolygon25D:
        case wkbMultiPolygon25D:
            if (usePen)
                BasicStrokeStyle(0xFF000000 | color, 2).toOGR(value);
            else
                BasicFillStyle(0x88000000 | color).toOGR(value);
            if (!value)
                return TE_Err;
            return TE_Ok;
        case wkbLineString:
        case wkbMultiLineString:
        case wkbLinearRing:
        case wkbLineString25D:
        case wkbMultiLineString25D:
        default:
            try {
                if (BasicStrokeStyle(0xFF000000 | color, 2).toOGR(value) != TE_Ok)
                    return TE_Err;
                return TE_Ok;
            } catch (...) {
                return TE_Err;
            }
    }
}

int OGDIDriverDefinition::getFaccCodeColor(const char* fCode, bool& usePen) NOTHROWS
{
	int code = atoi(fCode + 2);

	// https://www.dgiwg.org/digest/html/DIGEST_2-1_Part4.pdf
	switch (fCode[0])
	{
	case 'A': // Culture
	{
		switch (fCode[1])
		{
		case 'K': // Recreational
		{
			return 0xF1E9CB;
		}
		case 'L': // Miscelllaneous Features
		{
			switch (code)
			{
			case 20: // Built-Up Area
				return 0xFF0090;
			}
		}
		case 'M': // Storage
			return 0xC0C0C0;
		case 'N': // Transportation - Railroad
			return 0x000000;
		case 'P': // Transportation - Road
			return 0xA52A2A;
		case 'T': // Communication/Transmission
		{
			switch (code)
			{
			case 30: // Power Line
				return 0xA500FF;
			}
		}
		}
		return 0xFFFFFF;
	}
	case 'B': // Hydrography
		switch (fCode[1])
		{
		case 'A': // Coastal Hydrography
		{
			switch (code)
			{
			case 10: // Coastline/Shoreline
				return 0x00BFFF;
			case 30: // Island
				return 0xC8AA50;
			case 40:
				return 0x0000FF;
			}
		}
		case 'B': // Ports and Harbors
		case 'C': // NAVAIDS
			return 0x000000;
		case 'D': // Dangers/Hazards
			return 0xFF0000;
		case 'E': // Depth Information
		case 'F': // Bottom Features
		case 'G': // Tide and Current Information
			usePen = true;
			return 0x0000FF;
		case 'H': // Inland Water
			switch (code)
			{
			case 0: // Lake
			case 80:
				return 0xBFE1F7;
			case 90: // Land Subject to Inundation
				return 0x3F3FFF;
			case 140: // River/Stream
				return 0x1AC5FF;
			}
		    return 0x0000FF;
		case 'I': // Miscellaneous Inland Water
			switch (code)
			{
			case 20: // Dam
				return 0xBFE1F7;
			}
		case 'J': // Snow/Ice
			return 0xFFFFFF;
		case 'K': // Oceanographic or Geophysical
			usePen = true;
			return 0x0000FF;
		}
		return 0x0000FF;
	case 'C': // Hypsography
		switch (fCode[1])
		{
		case 'A': // Relief Portrayal
		{
			switch (code)
			{
			case 10: // Contour Line
				return 0xDECD8B;
			case 30: // Spot Elevation
				return 0x000000;
			}
		}
		}
		return 0x888888;
	case 'D': // Physiography
	{
		switch (fCode[1])
		{
		case 'A': // Exposed Surface Material
		{
			switch (code)
			{
			case 10:
				return 0xFFFFFF;
			}
		}
		}
		return 0xFFFFFF;
	}
	case 'E': // Vegetation
		switch (fCode[1])
		{
		case 'A': // Cropland
			return 0xFFF8DC;
		case 'B': // Rangeland
			return 0x90EE90;
		case 'C': // Woodland
			return 0x228B22;
		case 'D': // Wetlands
			return 0x556B2F;
		case 'E': // Miscellaneous Vegetation
			return 0x008000;
		}
		return 0x00FF00;
	case 'F': // Demarcation
		switch (fCode[1])
		{
		case 'A': // Boundaries/Limits/Zones (Topographic)
			switch (code)
			{
			case 0: // Administrative Boundary
				return 0x000000;
			case 1: // Administrative Areas
				return 0xA0AFB0;
			}
		}
		return 0x999999;
	case 'G': // Aeronautical Information
		switch (fCode[1])
		{
		case 'B': // Aerodrome
			switch (code)
			{
			case 5: // Airport/Airfield
				return 0x9664FF;
			}
		}
		return 0xFFFF00;
	case 'I': // Cadastral
		return 0xFFA500;
	case 'S': // Special Use
	case 'Z': // General
		return 0x000000;
	default:
		return 0xFFFFFF;
	}
}

const char* OGDIDriverDefinition::getFaccCodeIcon(const char* fCode) NOTHROWS
{
	int code = atoi(fCode + 2);

	switch (fCode[0])
	{
	case 'A':
	{
		switch (fCode[1])
		{
		case 'D':
		{
			switch (code)
			{
			case 10:
				return "http://maps.google.com/mapfiles/kml/shapes/placemark_square.png";
			case 30:
				return "http://maps.google.com/mapfiles/kml/shapes/open-diamond.png";
			}
		}
		case 'L':
		{
			switch (code)
			{
			case 105:
				return "http://maps.google.com/mapfiles/kml/shapes/placemark_square.png";
			}
		}
		}
		break;
	}
	case 'B':
	{
		switch (fCode[1])
		{
		case 'C':
			switch (code)
			{
			case 50:
				return "http://maps.google.com/mapfiles/kml/shapes/star.png";
			}
		}
		break;
	}
	case 'G':
		return "http://maps.google.com/mapfiles/kml/shapes/airports.png";
	}
	return "asset:/icons/reference_point.png";
}

TAKErr OGDIDriverDefinition::Spi::create(OGRDriverDefinition2Ptr& value, const char* path) NOTHROWS
{
	value = OGRDriverDefinition2Ptr(new OGDIDriverDefinition(), Memory_deleter_const<OGRDriverDefinition2, OGDIDriverDefinition>);
	return TE_Ok;
}

const char* OGDIDriverDefinition::Spi::getType() const NOTHROWS
{
	return OGDI_DRIVER_NAME;
}
