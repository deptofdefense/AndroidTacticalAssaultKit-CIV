#include "elevation/ElevationSource.h"

#include <cstring>

#include "feature/AbstractFeatureDataStore2.h"
#include "math/Rectangle.h"
#include "port/Collections.h"
#include "port/STLListAdapter.h"
#include "util/Memory.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace
{
    bool cursor_compare(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
}

ElevationSource::~ElevationSource() NOTHROWS
{}

ElevationSource::OnContentChangedListener::~OnContentChangedListener() NOTHROWS
{}

ElevationSource::QueryParameters::QueryParameters() NOTHROWS :
    spatialFilter(nullptr, nullptr),
    targetResolution(NAN),
    maxResolution(NAN),
    minResolution(NAN),
    types(nullptr, nullptr),
    authoritative(nullptr, nullptr),
    minCE(NAN),
    minLE(NAN),
    order(nullptr, nullptr),
    flags(nullptr, nullptr)
{}

ElevationSource::QueryParameters::QueryParameters(const QueryParameters &other) NOTHROWS :
    spatialFilter(nullptr, nullptr),
    targetResolution(other.targetResolution),
    maxResolution(other.maxResolution),
    minResolution(other.minResolution),
    types(nullptr, nullptr),
    authoritative(nullptr, nullptr),
    minCE(other.minCE),
    minLE(other.minLE),
    order(nullptr, nullptr),
    flags(nullptr, nullptr)
{
    if(other.spatialFilter)
        Geometry_clone(this->spatialFilter, *other.spatialFilter);
    if(other.types) {
        using namespace TAK::Engine::Port;
        this->types = Collection<String>::Ptr(new STLListAdapter<String>(), Memory_deleter_const<Collection<String>, STLListAdapter<String>>);
        Collections_addAll(*this->types, *other.types);
    }
    if(other.authoritative)
        this->authoritative = std::unique_ptr<bool, void(*)(const bool *)>(new bool(*other.authoritative), Memory_deleter_const<bool>);
    if(other.order) {
        using namespace TAK::Engine::Port;
        this->order = Collection<QueryParameters::Order>::Ptr(new STLListAdapter<QueryParameters::Order>(), Memory_deleter_const<Collection<QueryParameters::Order>, STLListAdapter<QueryParameters::Order>>);
        Collections_addAll(*this->order, *other.order);
    }
    if(other.flags)
        this->flags = std::unique_ptr<unsigned int, void (*)(const unsigned int *)>(new unsigned int(*other.flags), Memory_deleter_const<unsigned int>);
}

ElevationSource::QueryParameters::~QueryParameters() NOTHROWS
{}

TAKErr TAK::Engine::Elevation::ElevationSource_accept(bool *value, ElevationChunkCursor &cursor, const ElevationSource::QueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (params.authoritative.get()) {
        bool authoritative;
        code = cursor.isAuthoritative(&authoritative);
        TE_CHECKRETURN_CODE(code);
        if (*params.authoritative != authoritative) {
            *value = false;
            return TE_Ok;
        }
    }

    if (!params.flags.get()) {
        unsigned int flags;
        code = cursor.getFlags(&flags);
        TE_CHECKRETURN_CODE(code);
        if (!(*params.flags&flags)) {
            *value = false;
            return TE_Ok;
        }
    }

    // note that comparison with possibly NaN cursor CE will always return
    // false, which is the desired behavior
    if (!TE_ISNAN(params.minCE)) {
        double ce;
        code = cursor.getCE(&ce);
        TE_CHECKRETURN_CODE(code);
        if (params.minCE < ce) {
            *value = false;
            return TE_Ok;
        }
    }

    // note that comparison with possibly NaN cursor CE will always return
    // false, which is the desired behavior
    if (!TE_ISNAN(params.minLE)) {
        double le;
        code = cursor.getLE(&le);
        TE_CHECKRETURN_CODE(code);
        if (params.minLE < le) {
            *value = false;
            return TE_Ok;
        }
    }

    if (params.spatialFilter.get()) {
        Envelope2 test;
        code = params.spatialFilter->getEnvelope(&test);
        TE_CHECKRETURN_CODE(code);

        const Polygon2 *geom;
        code = cursor.getBounds(&geom);
        TE_CHECKRETURN_CODE(code);

        // the geometry is bad, return false so that it will never be accepted.
        if (!geom) {
            *value = false;
            return TE_Ok;
        }

        Envelope2 c;
        code = geom->getEnvelope(&c);
        TE_CHECKRETURN_CODE(code);

        if (!atakmap::math::Rectangle<double>::intersects(test.minX, test.minY, test.maxX, test.maxY, c.minX, c.minY, c.maxX, c.maxY)) {
            *value = false;
            return TE_Ok;
        }
    }

    if (params.types.get()) {
        const char *type;
        code = cursor.getType(&type);
        TE_CHECKRETURN_CODE(code);

        code = AbstractFeatureDataStore2::matches(value, *params.types, type, '*');
        if (code == TE_Ok && !*value)
            return code;
    }

    if (!TE_ISNAN(params.minResolution)) {
        double res;
        code = cursor.getResolution(&res);
        TE_CHECKRETURN_CODE(code);
        if (params.minResolution < res) {
            *value = false;
            return TE_Ok;
        }
    }

    if (!TE_ISNAN(params.maxResolution)) {
        double res;
        code = cursor.getResolution(&res);
        TE_CHECKRETURN_CODE(code);
        if (params.maxResolution > res) {
            *value = false;
            return TE_Ok;
        }
    }

    *value = true;
    return TE_Ok;
}

bool TAK::Engine::Elevation::ElevationSource_resolutionDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getResolution(&va) != TE_Ok)
            break;
        if (b.getResolution(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return false;
        else if (TE_ISNAN(vb))
            return true;
        if (va < vb)
            return true;
        else if (va > vb)
            return false;
    } while (false);

    return cursor_compare(a, b);
}
bool TAK::Engine::Elevation::ElevationSource_resolutionAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getResolution(&va) != TE_Ok)
            break;
        if (b.getResolution(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return true;
        else if (TE_ISNAN(vb))
            return false;
        if (va < vb)
            return false;
        else if (va > vb)
            return true;
    } while (false);

    return cursor_compare(a, b);
}
bool TAK::Engine::Elevation::ElevationSource_ceDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getCE(&va) != TE_Ok)
            break;
        if (b.getCE(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return false;
        else if (TE_ISNAN(vb))
            return true;
        if (va < vb)
            return true;
        else if (va > vb)
            return false;
    } while (false);

    return cursor_compare(a, b);
}
bool TAK::Engine::Elevation::ElevationSource_ceAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getCE(&va) != TE_Ok)
            break;
        if (b.getCE(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return true;
        else if (TE_ISNAN(vb))
            return false;
        if (va < vb)
            return false;
        else if (va > vb)
            return true;
    } while (false);

    return cursor_compare(a, b);
}
bool TAK::Engine::Elevation::ElevationSource_leDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getLE(&va) != TE_Ok)
            break;
        if (b.getLE(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return false;
        else if (TE_ISNAN(vb))
            return true;
        if (va < vb)
            return true;
        else if (va > vb)
            return false;
    } while (false);

    return cursor_compare(a, b);
}
bool TAK::Engine::Elevation::ElevationSource_leAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
{
    do {
        double va, vb;
        if (a.getLE(&va) != TE_Ok)
            break;
        if (b.getLE(&vb) != TE_Ok)
            break;
        if (TE_ISNAN(va) && TE_ISNAN(vb))
            break;
        else if (TE_ISNAN(va))
            return true;
        else if (TE_ISNAN(vb))
            return false;
        if (va < vb)
            return false;
        else if (va > vb)
            return true;
    } while (false);

    return cursor_compare(a, b);
}
namespace
{
    bool cursor_compare(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS
    {
        do {
            const char *ua, *ub;
            if (a.getUri(&ua) != TE_Ok)
                break;
            if (b.getUri(&ub) != TE_Ok)
                break;
            if (!ua && !ub)
                return false;
            else if (!ua)
                return false;
            else if (!ub)
                return true;
            else
                return strcmp(ua, ub) < 0;
        } while (false);

        return &a < &b;
    }
}