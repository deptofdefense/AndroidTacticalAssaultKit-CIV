#include "formats/dted/DtedElevationSource.h"

#include "elevation/ElevationChunkFactory.h"
#include "elevation/ElevationData.h"
#include "formats/dted/DtedSampler.h"
#include "port/StringBuilder.h"
#include "util/IO2.h"
#include "util/Memory.h"

#include <algorithm>

using namespace TAK::Engine::Formats::DTED;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace
{
    struct DtedFormat
    {
        const char *type;
        double resolution;
        double ce;
        double le;
        const char *extension;

        DtedFormat(const char *type, const double resolution, const double ce, const double le, const char *ext) NOTHROWS;
    };

    class CursorImpl : public ElevationChunkCursor
    {
    public :
        CursorImpl(const char *dir, const ElevationSource::QueryParameters &params) NOTHROWS;
        ~CursorImpl() override = default;
    public :
        TAKErr moveToNext() NOTHROWS override;
    public :
        TAKErr get(ElevationChunkPtr &value) NOTHROWS override;
        TAKErr getResolution(double *value) NOTHROWS override;
        TAKErr isAuthoritative(bool *value) NOTHROWS override;
        TAKErr getCE(double *value) NOTHROWS override;
        TAKErr getLE(double *value) NOTHROWS override;
        TAKErr getUri(const char **value) NOTHROWS override;
        TAKErr getType(const char **value) NOTHROWS override;
        TAKErr getBounds(const Polygon2 **value) NOTHROWS override;
        TAKErr getFlags(unsigned int *value) NOTHROWS override;
    private :
        TAK::Engine::Port::String dir;
        ElevationSource::QueryParameters params;

        int idx;
        int limit;
        int numFormats;
        std::vector<DtedFormat> formats;
        int minCellLat;
        int maxCellLat;
        int minCellLng;
        int maxCellLng;

        TAK::Engine::Port::String cell;
        DtedFormat cellFormat;
        std::shared_ptr<const Geometry2> cellBounds;
    };

    TAKErr makeFileName(TAK::Engine::Port::String &value, const double lat, const double lng) NOTHROWS;
}

DtedElevationSource::DtedElevationSource(const char *dir) NOTHROWS :
    dir_(dir)
{}
const char *DtedElevationSource::getName() const NOTHROWS
{
    return "DTED";
}
TAKErr DtedElevationSource::query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS
{
    value = ElevationChunkCursorPtr(new CursorImpl(dir_, params), Memory_deleter_const<ElevationChunkCursor, CursorImpl>);
    return TE_Ok;
}
Envelope2 DtedElevationSource::getBounds() const NOTHROWS
{
    return Envelope2(-180.0, -90.0, 180.0, 90.0);
}
TAKErr DtedElevationSource::addOnContentChangedListener(ElevationSource::OnContentChangedListener *l) NOTHROWS
{
    // no-op, content not dynamically populated
    return TE_Ok;
}
TAKErr DtedElevationSource::removeOnContentChangedListener(ElevationSource::OnContentChangedListener *l) NOTHROWS
{
    // no-op, content not dynamically populated
    return TE_Ok;
}

namespace
{
    DtedFormat::DtedFormat(const char *type_, const double resolution_, const double ce_, const double le_, const char *ext_) NOTHROWS :
        type(type_),
        resolution(resolution_),
        ce(ce_),
        le(le_),
        extension(ext_)
    {}

    CursorImpl::CursorImpl(const char *dir_, const ElevationSource::QueryParameters &params_) NOTHROWS :
        dir(dir_),
        params(params_),
        cellFormat(nullptr, NAN, NAN, NAN, nullptr)
    {
        Envelope2 mbb(-180.0, -90.0, 180.0, 90.0);
        if (params.spatialFilter.get())
        {
            params.spatialFilter->getEnvelope(&mbb);
        }

        const DtedFormat dt0("DTED0", 900.0, NAN, NAN, ".dt0");
        const DtedFormat dt1("DTED1", 90.0, NAN, NAN, ".dt1");
        const DtedFormat dt2("DTED2", 30.0, NAN, NAN, ".dt2");
        const DtedFormat dt3("DTED3", 10.0, NAN, NAN, ".dt3");

        if (params.types.get() && !params.types->empty())
        {
            TAK::Engine::Port::Collection<TAK::Engine::Port::String>::IteratorPtr iter(nullptr, nullptr);
            params.types->iterator(iter);
            do {
                TAK::Engine::Port::String type;
                if (iter->get(type) != TE_Ok)
                    break;

                if (TAK::Engine::Port::String_equal(dt3.type, type)) {
                    formats.push_back(dt3);
                } else if (TAK::Engine::Port::String_equal(dt2.type, type)) {
                    formats.push_back(dt2);
                } else if (TAK::Engine::Port::String_equal(dt1.type, type)) {
                    formats.push_back(dt1);
                } else if (TAK::Engine::Port::String_equal(dt0.type, type)) {
                    formats.push_back(dt0);
                }

                if (iter->next() != TE_Ok)
                    break;
            } while (true);
        } else if (!params.types.get()) {
            formats.push_back(dt3);
            formats.push_back(dt2);
            formats.push_back(dt1);
            formats.push_back(dt0);
        }

        // if ordered, and order contains ResolutionAsc reverse the format args
        if (params.order.get() && !params.order->empty()) {
            bool reverse = false;
            TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::IteratorPtr iter(nullptr, nullptr);
            params.order->iterator(iter);
            do {
                ElevationSource::QueryParameters::Order order;
                if (iter->get(order) != TE_Ok)
                    break;

                reverse |= (order == ElevationSource::QueryParameters::ResolutionAsc);
                if (iter->next() != TE_Ok)
                    break;
            } while (true);

            if (reverse)
                std::reverse(formats.begin(), formats.end());
        }

        if (!TE_ISNAN(params.minResolution))
        {
            auto it = formats.begin();
            while(it != formats.end()) {
                if ((*it).resolution > params.minResolution)
                    it = formats.erase(it);
                else
                    it++;
            }
        }
        if (!TE_ISNAN(params.maxResolution))
        {
            auto it = formats.begin();
            while(it != formats.end()) {
                if ((*it).resolution < params.maxResolution)
                    it = formats.erase(it);
                else
                    it++;
            }
        }

        this->maxCellLat = (int)floor(mbb.maxY);
        this->minCellLat = (int)floor(mbb.minY);
        this->maxCellLng = (int)floor(mbb.maxX);
        this->minCellLng = (int)floor(mbb.minX);

        this->idx = -1;

        this->numFormats = static_cast<int>(formats.size());

        this->limit = (this->maxCellLat - this->minCellLat + 1)
            * (this->maxCellLng - this->minCellLng + 1)
            * this->numFormats;

        this->cell = nullptr;
    }

    TAKErr CursorImpl::moveToNext() NOTHROWS
    {
        do
        {
            this->idx = this->idx + 1;
            this->cell = nullptr;
            if (this->idx >= this->limit)
            {
                break;
            }
            const double minLat = this->maxCellLat - ((this->idx / this->numFormats) / (this->maxCellLng - this->minCellLng + 1));  
            const double minLon = this->minCellLng + ((this->idx / this->numFormats) % (this->maxCellLng - this->minCellLng + 1));
            const double maxLat = minLat + 1.0;
            const double maxLon = minLon + 1.0;

            TAK::Engine::Port::String filename;
            makeFileName(filename,
                (minLat + maxLat) / 2.0,
                (minLon + maxLon) / 2.0);
            for (int i = (this->idx % this->formats.size()); i < this->formats.size(); i++)
            {
                TAK::Engine::Port::StringBuilder f;
                f.append(this->dir);
                f.append(TAK::Engine::Port::Platform_pathSep());
                f.append(filename);
                f.append(this->formats[i].extension);

                bool exists;
                if (IO_exists(&exists, f.c_str()) != TE_Ok || !exists)
                {
                    this->idx = this->idx + 1;
                    continue;
                }
                this->cell = f.c_str();
                LineString2 line_string;
                line_string.addPoint(minLon, minLat);
                line_string.addPoint(minLon, maxLat);
                line_string.addPoint(maxLon, maxLat);
                line_string.addPoint(maxLon, minLat);
                line_string.addPoint(minLon, minLat);
                Polygon2 polygon(line_string);                
                this->cellBounds = std::make_shared<Polygon2>(polygon);
                this->cellFormat = this->formats[i];
                break;
            }
            if (this->cell != nullptr)
            {
                break;
            }
        } while (true);
        return (this->cell != nullptr) ? TE_Ok : TE_Done;
    }
    TAKErr CursorImpl::get(ElevationChunkPtr &value) NOTHROWS
    {
        if (!cell)
            return TE_IllegalState;

        const double minLat = this->maxCellLat - ((this->idx / this->numFormats) / (this->maxCellLng - this->minCellLng + 1));  
        const double minLon = this->minCellLng + ((this->idx / this->numFormats) % (this->maxCellLng - this->minCellLng + 1));
        const double maxLat = minLat + 1.0;
        const double maxLon = minLon + 1.0;

        return ElevationChunkFactory_create(value,
                                            cellFormat.type,
                                            cell,
                                            ElevationData::MODEL_TERRAIN,
                                            cellFormat.resolution,
                                            static_cast<const Polygon2 &>(*cellBounds),
                                            cellFormat.ce,
                                            cellFormat.le,
                                            true,
                                            SamplerPtr(new DtedSampler(cell, maxLat, minLon), Memory_deleter_const<Sampler, DtedSampler>));
    }
    TAKErr CursorImpl::getResolution(double *value) NOTHROWS
    {
        *value = cellFormat.resolution;
        return TE_Ok;
    }
    TAKErr CursorImpl::isAuthoritative(bool *value) NOTHROWS
    {
        *value = true;
        return TE_Ok;
    }
    TAKErr CursorImpl::getCE(double *value) NOTHROWS
    {
        *value = cellFormat.ce;
        return TE_Ok;
    }
    TAKErr CursorImpl::getLE(double *value) NOTHROWS
    {
        *value = cellFormat.le;
        return TE_Ok;
    }
    TAKErr CursorImpl::getUri(const char **value) NOTHROWS
    {
        *value = cell;
        return TE_Ok;
    }
    TAKErr CursorImpl::getType(const char **value) NOTHROWS
    {
        *value = cellFormat.type;
        return TE_Ok;
    }
    TAKErr CursorImpl::getBounds(const Polygon2 **value) NOTHROWS
    {
        if (!cellBounds.get()) {
            const double minLat = this->maxCellLat - ((this->idx / this->numFormats) / (this->maxCellLng - this->minCellLng + 1));  
            const double minLon = this->minCellLng + ((this->idx / this->numFormats) % (this->maxCellLng - this->minCellLng + 1));
            const double maxLat = minLat + 1.0;
            const double maxLon = minLon + 1.0;

            Geometry2Ptr_const bounds(nullptr, nullptr);
            Polygon2_fromEnvelope(bounds, Envelope2(minLon, minLat, maxLon, maxLat));
        }

        *value = static_cast<const Polygon2 *>(cellBounds.get());
        return TE_Ok;
    }
    TAKErr CursorImpl::getFlags(unsigned int *value) NOTHROWS
    {
        *value = ElevationData::MODEL_TERRAIN;
        return TE_Ok;
    }
    
    TAKErr makeFileName(TAK::Engine::Port::String &value, const double lat, const double lng) NOTHROWS
    {
        TAK::Engine::Port::StringBuilder p;
        int lngIndex = (int)lng;
        if (lngIndex >= 0)
        {
            p.append("e");
        }
        else
        {
            p.append("w");
            lngIndex = (lngIndex * -1) + 1;
        }
        if (lngIndex < 10)
        {
            p.append("00");
        }
        else if (lngIndex < 100)
        {
            p.append("0");
        }
        p.append(lngIndex);
        //This didn't do what I thought it did, currently not used
        //p = p->Append(Path::PathSeparator);
        p.append(TAK::Engine::Port::Platform_pathSep());
        int latIndex = (int)lat;

        if (lat >= 0)
        {
            p.append("n");
        }
        else
        {
            p.append("s");
            latIndex = (latIndex * -1) + 1;
        }

        if (latIndex < 10)
        {
            p.append("0");
        }

        p.append(latIndex);

        value = p.c_str();
        return TE_Ok;
    }
}
