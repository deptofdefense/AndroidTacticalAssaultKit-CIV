#include "raster/mobac/CustomWmsMobacMapSource.h"

#include <map>
#include <sstream>

#include "core/GeoPoint.h"
#include "core/Projection.h"
#include "core/ProjectionFactory.h"
#include "math/Point.h"
#include "raster/osm/OSMUtils.h"
#include "util/Logging.h"

using namespace atakmap::raster::mobac;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::math;
using namespace atakmap::raster::osm;
using namespace atakmap::util;

namespace
{
    std::map<PGSC::String, PGSC::String, PGSC::StringLess> mapTileFormatToWmsFormatMime();
    int lastIndexOf(const char *str, size_t from, const char c);
    void fixupUrl(const char **urlIn);
}

CustomWmsMobacMapSource::CustomWmsMobacMapSource(const char *name, int sr, int tileSize, int minZoom, int maxZoom, const char *type, const char *url, const char *l, const char *s, const char *v, const char *a, int backgroundColor, Envelope *b) :
    CustomMobacMapSource(name, tileSize, minZoom, maxZoom, type, url, NULL, 0, backgroundColor, false),
    layers(l),
    style(s),
    srid(sr),
    additionalParameters(a),
    version(v),
    bounds(NAN, NAN, NAN, NAN, NAN, NAN)
{
    this->formatMime = NULL;
    static std::map<PGSC::String, PGSC::String, PGSC::StringLess> TILE_FORMAT_TO_WMS_FORMAT_MIME = mapTileFormatToWmsFormatMime();

    std::map<PGSC::String, PGSC::String, PGSC::StringLess>::iterator entry;
    entry = TILE_FORMAT_TO_WMS_FORMAT_MIME.find(this->tileType);
    if (entry != TILE_FORMAT_TO_WMS_FORMAT_MIME.end())
        this->formatMime = entry->second;

    fixupUrl(&this->url);

    if (b)
        bounds = *b;
}

int CustomWmsMobacMapSource::getSRID()
{
    if (this->srid == 900913)
        //return WebMercatorProjection.INSTANCE.getSpatialReferenceID();
        return 3857;
    return this->srid;
}

bool CustomWmsMobacMapSource::getBounds(Envelope *bnds)
{
    *bnds = this->bounds;
    return true;
}

//TODO(bergeronj)- move this to some common place; also MSVC can have noexcept too. Check for it.
#ifdef __clang__
#define NOEXCEPT noexcept
#else
#define NOEXCEPT
#endif

// Get around MSVC's non-standard std::exception with message constructor
class IllegalStateException : public std::exception {
public:
    IllegalStateException() { }
    virtual ~IllegalStateException() { }
    virtual const char *what() const NOEXCEPT { return "Illegal State"; }
};

size_t CustomWmsMobacMapSource::getUrl(char *urlOut, int zoom, int x, int y)
{
    double south = OSMUtils::mapnikTileLat(zoom, y + 1);
    double west = OSMUtils::mapnikTileLng(zoom, x);
    double north = OSMUtils::mapnikTileLat(zoom, y);
    double east = OSMUtils::mapnikTileLng(zoom, x + 1);

    if (this->srid == 3857 || this->srid == 900913) {
        GeoPoint g;
        Point<double> p;
        std::auto_ptr<Projection> webMercatorProjection(ProjectionFactory::getProjection(3857));

        g.latitude = OSMUtils::mapnikTileLat(zoom, y + 1);
        g.longitude = OSMUtils::mapnikTileLng(zoom, x);
        webMercatorProjection->forward(&g, &p);
        south = p.y;
        west = p.x;

        g.latitude = OSMUtils::mapnikTileLat(zoom, y);
        g.longitude = OSMUtils::mapnikTileLng(zoom, x + 1);
        webMercatorProjection->forward(&g, &p);
        north = p.y;
        east = p.x;
    } else if (this->srid == 4326) {
        south = 90.0 - ((180.0 / (1 << zoom)) * (y + 1));
        west = -180.0 + ((180.0 / (1 << zoom)) * x);
        north = 90.0 - ((180.0 / (1 << zoom)) * y);
        east = -180.0 + ((180.0 / (1 << zoom)) * (x + 1));
    } else {
        throw IllegalStateException();
    }

    std::stringstream retval;
    retval << url;
    retval << "service=WMS&request=GetMap&layers=" << this->layers << "&srs=EPSG:"
            << this->srid;
    retval << "&format=" << this->formatMime << "&width=" << this->tileSize
            << "&height=" << this->tileSize;
    if (this->version.size())
        retval << "&version=" << this->version;
    else
        // version is required according to WMS spec
        retval << "&version=1.1.1";

    if (this->style.size())
        retval << "&styles=" << this->style;
    else
        // style is required according to WMS spec; "default" is default
        retval << "&styles=default";

    if (this->additionalParameters.size())
        retval << additionalParameters;
    retval << "&bbox=" << west << "," << south << ","
            << east << "," << north;

    if (urlOut)
        sprintf(urlOut, "%s", retval.str().c_str());
    return retval.str().length();
}

namespace
{
    std::map<PGSC::String, PGSC::String, PGSC::StringLess> mapTileFormatToWmsFormatMime()
    {
        std::map<PGSC::String, PGSC::String, PGSC::StringLess> TILE_FORMAT_TO_WMS_FORMAT_MIME;
        TILE_FORMAT_TO_WMS_FORMAT_MIME["JPG"] = "image/jpeg";
        TILE_FORMAT_TO_WMS_FORMAT_MIME["PNG"] = "image/png";
        return TILE_FORMAT_TO_WMS_FORMAT_MIME;
    }

    int lastIndexOf(const char *str, size_t from, const char c)
    {
        int retval = -1;
        for (size_t i = from; str[i]; i++)
            if (str[i] == c)
                retval = i;
        return retval;
    }

    /**
    * getUrl() simply appends query string arguments to the end of the URL. This function makes
    * sure the user-provided URL ends with ? or & (as appropriate) so that this appending works
    * properly.
    */
    void fixupUrl(const char **pUrl)
    {
        const char *url = *pUrl;
        int queryStartIdx = lastIndexOf(url, 0, '?');
        if (queryStartIdx < 0) {
            char *str = new char[strlen(url) + 2];
            sprintf(str, "%s?", url);
            delete url;
            url = str;
            *pUrl = url;
        }
        else if (!(url[strlen(url) - 1] == '&')) {
            char *str = new char[strlen(url) + 2];
            sprintf(str, "%s&", url);
            delete url;
            url = str;
            *pUrl = url;
        }
    }
}
