#include "elevation/ElevationManager.h"

#include <list>

#include "elevation/ElevationSourceManager.h"
#include "elevation/MultiplexingElevationChunkCursor.h"
#include "feature/Geometry2.h"
#include "feature/Point2.h"
#include "feature/Polygon2.h"
#include "formats/egm/EGM96.h"
#pragma warning(push)
#pragma warning(disable : 4305 4838)
#include "formats/wmm/EGM9615.h"
#pragma warning(pop)
#include "formats/wmm/GeomagnetismHeader.h"
#include "raster/DatasetDescriptor.h"
#include "port/Collections.h"
#include "port/Vector.h"
#include "port/STLSetAdapter.h"
#include "port/STLListAdapter.h"
#include "port/STLVectorAdapter.h"
#include "port/String.h"
#include "thread/RWMutex.h"
#include "util/ConfigOptions.h"
#include "util/DataInput2.h"
#include "util/Memory.h"
#include "util/Error.h"
#include "elevation/ElevationData.h"
#include "core/GeoPoint2.h"
#include "raster/mosaic/MultiplexingMosaicDatabaseCursor2.h"
#include "raster/mosaic/FilterMosaicDatabaseCursor2.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Formats::EGM;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::Mosaic;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

typedef TAK_UNIQUE_PTR(Util::Filter<Mosaic::MosaicDatabase2::Cursor &>) ElevationModelFilterPtr;

namespace
{
    std::set<std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi>> &dataSpiRegistry()
    {
        static std::set<std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi>> r;
        return r;
    }

    RWMutex &spiMutex()
    {
        static RWMutex m;
        return m;
    }

    RWMutex &sourceMutex()
    {
        static RWMutex m;
        return m;
    }

    std::map<std::shared_ptr<MosaicDatabase2>, std::shared_ptr<ElevationSource>> &sources() {
        static std::map<std::shared_ptr<MosaicDatabase2>, std::shared_ptr<ElevationSource>> s;
        return s;
    }

    class ElevationModelFilter : public Util::Filter<Mosaic::MosaicDatabase2::Cursor &>
    {
    public :
        ElevationModelFilter(const int model) NOTHROWS;
    public :
        bool accept(Mosaic::MosaicDatabase2::Cursor &arg) NOTHROWS override ;
    private :
        const int model;
    };

    struct GeoMag
    {
        GeoMag() NOTHROWS
        {
            MAG_SetDefaults(&ellipsoid, &geoid);
            geoid.GeoidHeightBuffer = GeoidHeightBuffer;
            geoid.Geoid_Initialized = 1;
        }
        MAGtype_Geoid geoid;
        MAGtype_Ellipsoid ellipsoid;
    };
    struct GeoidContext_s
    {
        GeoidContext_s() NOTHROWS :
            dirty(true)
        {}

        GeoMag geomag;
        std::unique_ptr<EGM96> egm96;

        Mutex mutex;
        bool dirty;
    } GeoidContext;

    class MosaicDbStub : public ElevationSource
    {
    public:
        MosaicDbStub() NOTHROWS;
    public:
        const char *getName() const NOTHROWS override;
        TAKErr query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS override;
        Feature::Envelope2 getBounds() const NOTHROWS override;
        TAKErr addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS override;
        TAKErr removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS override;
    };
}

//**************** ElevationManagerQueryParameters **/

ElevationManagerQueryParameters::ElevationManagerQueryParameters() NOTHROWS :
    minResolution(NAN),
    maxResolution(NAN),
    elevationModel(ElevationData::MODEL_SURFACE | ElevationData::MODEL_TERRAIN),
    spatialFilter(nullptr, nullptr),
    types(nullptr, nullptr),
    preferSpeed(false),
    interpolate(false)
{}

ElevationManagerQueryParameters::ElevationManagerQueryParameters(const ElevationManagerQueryParameters &other) NOTHROWS :
    minResolution(other.minResolution),
    maxResolution(other.maxResolution),
    elevationModel(other.elevationModel),
    spatialFilter(nullptr, nullptr),
    types(nullptr, nullptr),
    preferSpeed(other.preferSpeed),
    interpolate(other.interpolate)
{
    if (other.spatialFilter.get()) {
        Util::TAKErr code(Util::TE_Ok);
        code = Feature::Geometry_clone(spatialFilter, *other.spatialFilter);
        // XXX - clone failed
    }
    if (other.types.get()) {
        types = TAK_UNIQUE_PTR(Port::Set<Port::String>)(new Port::STLSetAdapter<Port::String, TAK::Engine::Port::StringLess>(), Util::Memory_deleter_const<Port::Set<Port::String>, Port::STLSetAdapter<Port::String, TAK::Engine::Port::StringLess>>);
        Port::Collections_addAll<Port::String>(*types, *other.types);
    }
}

//**************** ElevationManager **/


Util::TAKErr TAK::Engine::Elevation::ElevationManager_registerElevationSource(std::shared_ptr<TAK::Engine::Raster::Mosaic::MosaicDatabase2> database) NOTHROWS
{
    if (!database.get())
        return Util::TE_InvalidArg;
    WriteLock lock(sourceMutex());
    TE_CHECKRETURN_CODE(lock.status);
    std::map<std::shared_ptr<MosaicDatabase2>, std::shared_ptr<ElevationSource>> &src = sources();
    for (auto it = src.begin(); it != src.end(); it++) {
        if (it->first.get() == database.get())
            return TE_Ok;
    }
    std::shared_ptr<ElevationSource> stub = std::move(std::unique_ptr<ElevationSource, void(*)(const ElevationSource *)>(new MosaicDbStub(), Memory_deleter_const<ElevationSource, MosaicDbStub>));

    src[database] = stub;
    ElevationSourceManager_attach(stub);

    return Util::TE_Ok;
}

Util::TAKErr TAK::Engine::Elevation::ElevationManager_unregisterElevationSource(std::shared_ptr<TAK::Engine::Raster::Mosaic::MosaicDatabase2> database) NOTHROWS
{
    if (!database.get())
        return Util::TE_InvalidArg;
    WriteLock lock(sourceMutex());
    TE_CHECKRETURN_CODE(lock.status);
    std::map<std::shared_ptr<MosaicDatabase2>, std::shared_ptr<ElevationSource>> &src = sources();
    for (auto it = src.begin(); it != src.end(); it++) {
        if (it->first.get() == database.get()) {
            ElevationSourceManager_detach(*it->second);
            src.erase(it);
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}

TAKErr TAK::Engine::Elevation::ElevationManager_queryElevationData(MosaicDatabase2::CursorPtr &cursor, const ElevationManagerQueryParameters &params) NOTHROWS
{
    Util::TAKErr code(Util::TE_Ok);
    Mosaic::MosaicDatabase2::QueryParameters mparams;
    bool isReject = false;

    TAK_UNIQUE_PTR(Port::Collection<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>>) filters(new Port::STLSetAdapter<std::shared_ptr<Util::Filter<Mosaic::MosaicDatabase2::Cursor &>>>(), Util::Memory_deleter_const<Port::Collection<std::shared_ptr<Util::Filter<Mosaic::MosaicDatabase2::Cursor &>>>, Port::STLSetAdapter<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>>>);

    mparams.minGsd = params.minResolution;
    mparams.maxGsd = params.maxResolution;
    if (params.spatialFilter) {
        code = Feature::Geometry_clone(mparams.spatialFilter, *params.spatialFilter);
        TE_CHECKRETURN_CODE(code);
    }
    if (params.types.get()) {
        mparams.types = TAK_UNIQUE_PTR(Port::Set<Port::String>)(new Port::STLSetAdapter<Port::String, TAK::Engine::Port::StringLess>(), Util::Memory_deleter_const<Port::Set<Port::String>, Port::STLSetAdapter<Port::String, TAK::Engine::Port::StringLess>>);
        Port::Collections_addAll(*mparams.types, *params.types);
    }
    if (params.elevationModel != (ElevationData::MODEL_SURFACE | ElevationData::MODEL_TERRAIN)) {
        ElevationModelFilterPtr filter(nullptr, nullptr);
        switch (params.elevationModel) {
        case 0:
            isReject = true;
            break;
        case ElevationData::MODEL_SURFACE:
        case ElevationData::MODEL_TERRAIN:
        default:
            filter = ElevationModelFilterPtr(new ElevationModelFilter(params.elevationModel), Util::Memory_deleter_const<Util::Filter<Mosaic::MosaicDatabase2::Cursor &>, ElevationModelFilter>);
            break;
        }

        if (filter.get())
            filters->add(std::move(filter));
    }

    std::unique_ptr<MultiplexingMosaicDatabaseCursor2> mcursor(new MultiplexingMosaicDatabaseCursor2());
    if (!isReject)
    {
        ReadLock lock(sourceMutex());
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        for (auto entry = sources().begin(); entry != sources().end(); entry++)
        {
            MosaicDatabase2::CursorPtr queryPtr(nullptr, nullptr);

            TAKErr success = entry->first->query(queryPtr, mparams);

            if (success == TE_Ok) {
                mcursor->add(move(queryPtr));
            }

        }
    }

    cursor = MosaicDatabase2::CursorPtr(mcursor.release(), Memory_deleter_const<MosaicDatabase2::Cursor, MultiplexingMosaicDatabaseCursor2>);

    if (!filters->empty())
    {
        MosaicDatabase2::CursorPtr filtered(nullptr, nullptr);
        code = FilterMosaicDatabaseCursor2_filter(filtered, std::move(cursor), std::move(filters));
        TE_CHECKRETURN_CODE(code);
        cursor = std::move(filtered);
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Elevation::ElevationManager_queryElevationSources(ElevationChunkCursorPtr &value, const ElevationSource::QueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::list<std::shared_ptr<ElevationSource>> sources;
    TAK::Engine::Port::STLListAdapter<std::shared_ptr<ElevationSource>> sources_w(sources);
    code = ElevationSourceManager_getSources(sources_w);
    TE_CHECKRETURN_CODE(code);

    std::list<std::shared_ptr<ElevationChunkCursor>> cursors;
    if(!sources.empty()) {
        for(auto it = sources.begin(); it != sources.end(); it++) {
            ElevationChunkCursorPtr cursor(nullptr, nullptr);
            code = (*it)->query(cursor, params);
            TE_CHECKBREAK_CODE(code);
            cursors.push_back(std::move(cursor));
        }
        TE_CHECKRETURN_CODE(code);
    }

    std::list<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS> order;
    if(params.order.get() && !params.order->empty()) {
        TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order >::IteratorPtr oit(nullptr, nullptr);
        code = params.order->iterator(oit);
        TE_CHECKRETURN_CODE(code);
        do {
            ElevationSource::QueryParameters::Order v;
            code = oit->get(v);
            TE_CHECKBREAK_CODE(code);
            switch(v) {
                case ElevationSource::QueryParameters::ResolutionAsc :
                    order.push_back(ElevationSource_resolutionAsc);
                    break;
                case ElevationSource::QueryParameters::ResolutionDesc :
                    order.push_back(ElevationSource_resolutionDesc);
                    break;
                case ElevationSource::QueryParameters::CEAsc :
                    order.push_back(ElevationSource_ceAsc);
                    break;
                case ElevationSource::QueryParameters::CEDesc :
                    order.push_back(ElevationSource_ceDesc);
                    break;
                case ElevationSource::QueryParameters::LEAsc :
                    order.push_back(ElevationSource_leAsc);
                    break;
                case ElevationSource::QueryParameters::LEDesc :
                    order.push_back(ElevationSource_leDesc);
                    break;
                default :
                    return TE_InvalidArg;
            }
            code = oit->next();
            TE_CHECKBREAK_CODE(code);
        } while(true);
        if(code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

    }

    TAK::Engine::Port::STLListAdapter<std::shared_ptr<ElevationChunkCursor>> cursors_w(cursors);
    TAK::Engine::Port::STLListAdapter<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS> order_w(order);
    value = ElevationChunkCursorPtr(new MultiplexingElevationChunkCursor(cursors_w, order_w), Memory_deleter_const<ElevationChunkCursor, MultiplexingElevationChunkCursor>);

    return code;
}
TAKErr TAK::Engine::Elevation::ElevationManager_queryElevationSourcesCount(std::size_t *value, const ElevationSource::QueryParameters &params) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    ElevationChunkCursorPtr result(nullptr, nullptr);
    code = ElevationManager_queryElevationSources(result, ElevationSource::QueryParameters());
    TE_CHECKRETURN_CODE(code);
    std::size_t retval = 0u;
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        retval++;
    } while(true);
    if(code == TE_Done)
        code = TE_Ok;
    *value = retval;
    return code;
}

TAKErr TAK::Engine::Elevation::ElevationManager_getElevation(double *value, Port::String *source, const double latitude, const double longitude, const ElevationManagerQueryParameters &filter) NOTHROWS
{
    TAKErr code(TE_Ok);
    ElevationManagerQueryParameters params(filter);

    params.spatialFilter = Feature::Geometry2Ptr(new Feature::Point2(longitude, latitude), Util::Memory_deleter_const<Feature::Geometry2>);

    MosaicDatabase2::CursorPtr cursor(nullptr, nullptr);
    code = ElevationManager_queryElevationData(cursor, params);
    TE_CHECKRETURN_CODE(code);

    *value = NAN;

    do
    {
        code = cursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        MosaicDatabase2::FramePtr_const frame(nullptr, nullptr);
        if (MosaicDatabase2::Frame::createFrame(frame, *cursor) != TE_Ok)
            continue;

        ElevationDataPtr elptr(nullptr, nullptr);
        if(ElevationManager_create(elptr, *frame) != TE_Ok)
            continue;

        double hae;
        TAKErr success = elptr->getElevation(&hae, latitude, longitude);
        if (isnan(hae) || success != TE_Ok)
            continue;
        if (source)
            success = elptr->getType(*source);
        *value = hae;
        break;
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    return code;
}

TAKErr TAK::Engine::Elevation::ElevationManager_getElevation(double *value, Port::String *source, const double latitude, const double longitude, const ElevationSource::QueryParameters &filter_) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);

    ElevationSource::QueryParameters filter(filter_);
    filter.spatialFilter = TAK::Engine::Feature::Geometry2Ptr(new TAK::Engine::Feature::Point2(longitude, latitude), Memory_deleter_const<TAK::Engine::Feature::Geometry2, TAK::Engine::Feature::Point2>);

    ElevationChunkCursorPtr result(nullptr, nullptr);
    code = ElevationManager_queryElevationSources(result, filter);
    TE_CHECKRETURN_CODE(code);

    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        ElevationChunkPtr chunk(nullptr, nullptr);
        if(result->get(chunk) != TE_Ok)
            continue;
        double hae;
        if(chunk->sample(&hae, latitude, longitude) != TE_Ok || isnan(hae))
            continue;
        *value = hae;
        if(source)
            *source = chunk->getType();
        return code;
    } while(true);
    if(code == TE_Done)
        code = TE_Ok;

    *value = NAN;
    // no elevation value found
    return TE_InvalidArg;
}

Util::TAKErr TAK::Engine::Elevation::ElevationManager_getElevation(
    double *elevations,
    Port::Collection<GeoPoint2>::IteratorPtr &points,
    const ElevationManagerQueryParameters &filter,
    const ElevationData::Hints &hint) NOTHROWS
{
    struct GeoPoint2_LT
    {
        bool operator()(const GeoPoint2 &a, const GeoPoint2 &b) const
        {
            if (a.latitude < b.latitude)
                return true;
            else if (a.latitude > b.latitude)
                return false;
            else
                return a.longitude < b.longitude;
        }
    };
    std::list<GeoPoint2> src;
    std::size_t srcSize;
    std::map<GeoPoint2, std::size_t, GeoPoint2_LT> sourceLocations;
    std::size_t idx = 0u;
    double north = -90;
    double south = 90;
    double east = -180;
    double west = 180;

    Util::TAKErr code;
    do {
        GeoPoint2 point;
        code = points->get(point);
        TE_CHECKBREAK_CODE(code);

        src.push_back(point);
        elevations[idx] = NAN;
        sourceLocations[point] = idx++;

        double lat = point.latitude;
        if (lat > north) { north = lat; }
        if (lat < south) { south = lat; }

        double lng = point.longitude;
        if (lng > east) { east = lng; }
        if (lng < west) { west = lng; }

        code = points->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);

    srcSize = idx;

    ElevationData::Hints hintCopy(hint);

    // TODO: Does this have issues when the envelope crosses the International Date Line?
    // This code copies what is done in ATAK, so that may need to be checked too.
    hintCopy.bounds = Feature::Envelope2(west, south, NAN, east, north, NAN);

    ElevationManagerQueryParameters filterCopy(filter);
    Feature::LineString2 ring;
    code = ring.addPoint(west, north);
    TE_CHECKRETURN_CODE(code);
    ring.addPoint(east, north);
    TE_CHECKRETURN_CODE(code);
    ring.addPoint(east, south);
    TE_CHECKRETURN_CODE(code);
    ring.addPoint(west, south);
    TE_CHECKRETURN_CODE(code);
    ring.addPoint(west, north);
    TE_CHECKRETURN_CODE(code);

    filterCopy.spatialFilter = Feature::Geometry2Ptr(
        new Feature::Polygon2(ring),
        Util::Memory_deleter_const<Feature::Geometry2>);

    Mosaic::MosaicDatabase2::CursorPtr cursor(nullptr, nullptr);

    code = ElevationManager_queryElevationData(cursor, filterCopy);
    TE_CHECKRETURN_CODE(code);
    code = Util::TE_Ok;
    do
    {
        code = cursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        ElevationDataPtr data(nullptr, nullptr);

        TAK::Engine::Raster::Mosaic::MosaicDatabase2::FramePtr_const frame(nullptr, nullptr);
        TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame::createFrame(frame, *cursor);

        code = ElevationManager_create(data, *frame);
        if (code != Util::TE_Ok || !data.get()) {
            continue;
        }

        Port::STLListAdapter<GeoPoint2> srcAdapter(src);
        Port::Collection<GeoPoint2>::IteratorPtr iterator(nullptr, nullptr);
        code = srcAdapter.iterator(iterator);
        if (code != Util::TE_Ok) {
            continue;
        }

        Util::array_ptr<double> dataEls(new double[srcSize]);
        code = data->getElevation(dataEls.get(), iterator, hintCopy);
        if (code != Util::TE_Ok) {
            continue;
        }

        std::list<GeoPoint2>::const_iterator srcIter = src.begin();
        const std::size_t limit = srcSize;
        for (std::size_t i = 0u; i < limit; i++) {
            const double value = dataEls[i];
            if (isnan(value))
            {
                srcIter++;
                continue;
            }

            elevations[sourceLocations[*srcIter]] = value;
            srcIter = src.erase(srcIter);
            srcSize--;
        }

        if (src.empty())
        {
            break;
        }
    } while (true);
    if(code == Util::TE_Done)
    {
        code = Util::TE_Ok;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Elevation::ElevationManager_getElevation(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride, const ElevationSource::QueryParameters &filter_) NOTHROWS {
    if (!value)
        return TE_InvalidArg;
    if (!srcLat)
        return TE_InvalidArg;
    if (!srcLng)
        return TE_InvalidArg;
    if (!count)
        return TE_Ok;

    TAKErr code(TE_Ok);

    ElevationSource::QueryParameters filter(filter_);
    if (!filter_.spatialFilter.get()) {
        TAK::Engine::Feature::Envelope2 mbr(srcLng[0], srcLat[0], 0.0, srcLng[0], srcLat[0], 0.0);
        for (std::size_t i = 1u; i < count; i++) {
            GeoPoint2 pt(srcLat[i * srcLatStride], srcLng[i * srcLngStride]);

            if (pt.longitude < mbr.minX)
                mbr.minX = pt.longitude;
            if (pt.latitude < mbr.minY)
                mbr.minY = pt.latitude;
            if (pt.longitude > mbr.maxX)
                mbr.maxX = pt.longitude;
            if (pt.latitude > mbr.maxY)
                mbr.maxY = pt.latitude;
        }

        // recompute spatial filter using MBR
        // XXX - should be intersection of user specified and computed ???
        code = TAK::Engine::Feature::Polygon2_fromEnvelope(filter.spatialFilter, mbr);
        TE_CHECKRETURN_CODE(code);
    }

    ElevationChunkCursorPtr result(nullptr, nullptr);
    code = ElevationManager_queryElevationSources(result, filter);
    TE_CHECKRETURN_CODE(code);

    // XXX - should we allow client to fill holes
    for(std::size_t i = 0u; i < count; i++)
        value[i*dstStride] = NAN;

    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        ElevationChunkPtr data(nullptr, nullptr);
        if(result->get(data) != TE_Ok)
            continue;
        if(data->sample(value, count, srcLat, srcLng, srcLatStride, srcLngStride, dstStride) == TE_Ok)
            return TE_Ok;
    } while(true);

    // all processing is done, but we haven't
    return TE_Done;
}


//**************** SPI Methods **/

Util::TAKErr TAK::Engine::Elevation::ElevationManager_create(ElevationDataPtr &value, const ImageInfo &info) NOTHROWS
{
    Util::TAKErr code(Util::TE_Ok);
    ReadLock lock(spiMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::set<std::shared_ptr<ElevationDataSpi>> &spis = dataSpiRegistry();
    std::set<std::shared_ptr<ElevationDataSpi>>::iterator spi;
    for (spi = spis.begin(); spi != spis.end(); spi++) {
        code = (*spi)->create(value, info);
        if (code == Util::TE_Ok)
            return code;
    }

    return Util::TE_InvalidArg;
}

Util::TAKErr TAK::Engine::Elevation::ElevationManager_registerDataSpi(std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi> spi) NOTHROWS
{
    if (!spi.get())
        return Util::TE_InvalidArg;

    Util::TAKErr code(Util::TE_Ok);
    WriteLock lock(spiMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    dataSpiRegistry().insert(spi);
    // XXX - invalid arg if already registered???
    return Util::TE_Ok;
}

Util::TAKErr TAK::Engine::Elevation::ElevationManager_unregisterDataSpi(std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi> spi) NOTHROWS
{
    if (!spi.get())
        return Util::TE_InvalidArg;

    Util::TAKErr code(Util::TE_Ok);
    WriteLock lock(spiMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    return dataSpiRegistry().erase(spi) ? Util::TE_Ok : Util::TE_InvalidArg;
}

Util::TAKErr TAK::Engine::Elevation::ElevationManager_getGeoidHeight(double *height, const double latitude, const double longitude) NOTHROWS
{
    do {
        TAKErr code(TE_Ok);
        Lock lock(GeoidContext.mutex);
        code = lock.status;
        TE_CHECKBREAK_CODE(code);
        if(GeoidContext.dirty) {
            GeoidContext.dirty = false;

            // initialize from the EGM96 file
            TAK::Engine::Port::String egmFilePath;
            code = ConfigOptions_getOption(egmFilePath, "egm96-file");
            if (code != TE_Ok) {
                Logger_log(TELL_Info, "EGM96 file is not configured");
                break;
            }

            // XXX - read the file into a byte array
            bool exists;
            code = IO_exists(&exists, egmFilePath);
            TE_CHECKBREAK_CODE(code);
            if(!exists) {
                Logger_log(TELL_Warning, "EGM96 file not found");
                break;
            }
            int64_t fileLen;
            code = IO_length(&fileLen, egmFilePath);
            TE_CHECKBREAK_CODE(code);

            if(fileLen > SIZE_MAX) {
                Logger_log(TELL_Warning, "EGM96 file size exceeds SIZE_MAX");
                break;
            }

            array_ptr<uint8_t> egmData(new uint8_t[(std::size_t)fileLen]);

            FileInput2 file;
            code = file.open(egmFilePath);
            TE_CHECKBREAK_CODE(code);
            std::size_t egmDataLen;
            code = file.read(egmData.get(), &egmDataLen, (std::size_t)fileLen);
            TE_CHECKBREAK_CODE(code);

            std::unique_ptr<EGM96> egm96(new EGM96());
            code = egm96->open(egmData.get(), egmDataLen);
            TE_CHECKBREAK_CODE(code);

            GeoidContext.egm96 = std::move(egm96);
        }
        if(GeoidContext.egm96.get()) {
            code = GeoidContext.egm96->getHeight(height, GeoPoint2(latitude, longitude));
            TE_CHECKBREAK_CODE(code);

            return code;
        }
    } while(false);

    // drop through to WMM EGM96 dataset
    if(MAG_GetGeoidHeight(latitude, longitude, height, &GeoidContext.geomag.geoid) != TRUE)
      return Util::TE_InvalidArg;
    return TE_Ok;
}

namespace
{
    ElevationModelFilter::ElevationModelFilter(int model_) NOTHROWS :
        model(model_)
    {}

    bool ElevationModelFilter::accept(Mosaic::MosaicDatabase2::Cursor &arg) NOTHROWS
    {
        Util::TAKErr code(Util::TE_Ok);

        Mosaic::MosaicDatabase2::FramePtr_const frame(nullptr, nullptr);
        code = Mosaic::MosaicDatabase2::Frame::createFrame(frame, arg);
        if (code != Util::TE_Ok)
            return false;

        ElevationDataPtr data(nullptr, nullptr);
        code = ElevationManager_create(data, *frame);
        if (code != Util::TE_Ok)
            return false;

        return ((data->getElevationModel()&this->model) != 0);
    }

    MosaicDbStub::MosaicDbStub() NOTHROWS
    {}
    const char *MosaicDbStub::getName() const NOTHROWS
    {
        return nullptr;
    }
    TAKErr MosaicDbStub::query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS
    {
        std::list<std::shared_ptr<ElevationChunkCursor>> empty;
        TAK::Engine::Port::STLListAdapter<std::shared_ptr<ElevationChunkCursor>> empty_w(empty);
        value = ElevationChunkCursorPtr(new MultiplexingElevationChunkCursor(empty_w), Memory_deleter_const<ElevationChunkCursor, MultiplexingElevationChunkCursor>);
        return TE_Ok;
    }
    Feature::Envelope2 MosaicDbStub::getBounds() const NOTHROWS
    {
        return Feature::Envelope2(0.0, 0.0, 0.0, 0.0);
    }
    TAKErr MosaicDbStub::addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS
    {
        return TE_Ok;
    }
    TAKErr MosaicDbStub::removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS
    {
        return TE_Ok;
    }
}
