#include "elevation/ElevationChunkFactory.h"

#include <algorithm>
#include <cmath>

#include "core/GeoPoint2.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "math/GeometryModel2.h"
#include "math/Mesh.h"
#include "model/MeshBuilder.h"
#include "model/MeshTransformer.h"
#include "raster/DefaultDatasetProjection.h"
#include "renderer/GLTexture2.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    class AbstractElevationChunk : public ElevationChunk
    {
    public :
        AbstractElevationChunk(const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative) NOTHROWS;
    public :
        TAKErr createData(ElevationChunkDataPtr &value) NOTHROWS override = 0;
        TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS override = 0;
    public :
        const char *getUri() const NOTHROWS override;
        const char *getType() const NOTHROWS override;
        double getResolution() const NOTHROWS override;
        const Polygon2 *getBounds() const NOTHROWS override;
        TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS override;
        double getCE() const NOTHROWS override;
        double getLE() const NOTHROWS override;
        bool isAuthoritative() const NOTHROWS override;
        unsigned int getFlags() const NOTHROWS override;
    private :
        TAK::Engine::Port::String type_;
        TAK::Engine::Port::String uri_;
        unsigned int flags_;
        double resolution_;
        Polygon2 bounds_;
        double ce_;
        double le_;
        bool authoritative_;
    };

    class DataElevationChunk : public AbstractElevationChunk
    {
    public :
        DataElevationChunk(const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative, DataLoaderPtr &&dataLoader) NOTHROWS;
    public :
        TAKErr createData(ElevationChunkDataPtr &value) NOTHROWS override;
        TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS override;
    private :
        DataLoaderPtr data_loader_;
        ElevationChunkDataPtr data_;
        GeometryModel2Ptr geom_model_;
        Projection2Ptr proj_;
        Mutex mutex_;
    };

    class SampledElevationChunk : public AbstractElevationChunk
    {
    public:
        SampledElevationChunk(const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative, SamplerPtr &&sampler) NOTHROWS;
    public:
        TAKErr createData(ElevationChunkDataPtr &value) NOTHROWS override;
        TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS override;
        TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS override;
    private:
        SamplerPtr sampler_;
        ElevationChunkDataPtr data_;
        Mutex mutex_;
    };

    TAKErr validateBounds(const Polygon2 &bounds) NOTHROWS;

    template<class T>
    TAKErr default_sample(double *value, T &source, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS;

    double distance(const LineString2 &bounds, const std::size_t a, const std::size_t b) NOTHROWS;
    double estimateDistance(const double metersDegLat, const double metersDegLng, const LineString2 &bounds, const std::size_t a, const std::size_t b) NOTHROWS;

    atakmap::core::GeoPoint getPoint(const LineString2 &bounds, const std::size_t i) NOTHROWS
    {
        atakmap::core::GeoPoint retval;
        bounds.getY(&retval.latitude, i);
        bounds.getX(&retval.longitude, i);
        return retval;
    }
}

Sampler::~Sampler() NOTHROWS
{}

TAKErr Sampler::sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS
{
    return default_sample(value, *this, count, srcLat, srcLng, srcLatStride, srcLngStride, dstStride);
}

DataLoader::~DataLoader() NOTHROWS
{}

TAKErr TAK::Engine::Elevation::ElevationChunkFactory_create(ElevationChunkPtr &value, const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative, DataLoaderPtr &&dataLoader) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!dataLoader.get()) return TE_InvalidArg;
    code = validateBounds(bounds);
    TE_CHECKRETURN_CODE(code);

    value = ElevationChunkPtr(new DataElevationChunk(type, uri, flags, resolution, bounds, ce, le, authoritative, std::move(dataLoader)),
                              Memory_deleter_const<ElevationChunk, DataElevationChunk>);
    return code;
}
TAKErr TAK::Engine::Elevation::ElevationChunkFactory_create(ElevationChunkPtr &value, const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative, SamplerPtr &&sampler) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!sampler.get()) return TE_InvalidArg;
    code = validateBounds(bounds);
    TE_CHECKRETURN_CODE(code);

    value = ElevationChunkPtr(new SampledElevationChunk(type, uri, flags, resolution, bounds, ce, le, authoritative, std::move(sampler)),
                              Memory_deleter_const<ElevationChunk, SampledElevationChunk>);
    return code;
}

namespace
{
    AbstractElevationChunk::AbstractElevationChunk(const char *type_, const char *uri_, const unsigned int flags_, const double resolution_, const Polygon2 &bounds_, const double ce_, const double le_, const bool authoritative_) NOTHROWS :
        type_(type_),
        uri_(uri_),
        flags_(flags_),
        resolution_(resolution_),
        bounds_(bounds_),
        ce_(ce_),
        le_(le_),
        authoritative_(authoritative_)
    {}

    const char *AbstractElevationChunk::getUri() const NOTHROWS
    {
        return uri_;
    }
    const char *AbstractElevationChunk::getType() const NOTHROWS
    {
        return type_;
    }
    double AbstractElevationChunk::getResolution() const NOTHROWS
    {
        return resolution_;
    }
    const Polygon2 *AbstractElevationChunk::getBounds() const NOTHROWS
    {
        return &bounds_;
    }
    TAKErr AbstractElevationChunk::sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS
    {
        return default_sample(value, *this, count, srcLat, srcLng, srcLatStride, srcLngStride, dstStride);
    }
    double AbstractElevationChunk::getCE() const NOTHROWS
    {
        return ce_;
    }
    double AbstractElevationChunk::getLE() const NOTHROWS
    {
        return le_;
    }
    bool AbstractElevationChunk::isAuthoritative() const NOTHROWS
    {
        return authoritative_;
    }
    unsigned int AbstractElevationChunk::getFlags() const NOTHROWS
    {
        return flags_;
    }

    DataElevationChunk::DataElevationChunk(const char *type, const char *uri, const unsigned int flags, const double resolution, const Polygon2 &bounds, const double ce, const double le, const bool authoritative, DataLoaderPtr &&dataLoader) NOTHROWS :
        AbstractElevationChunk(type, uri, flags, resolution, bounds, ce, le, authoritative),
        data_loader_(std::move(dataLoader)),
        data_(nullptr, nullptr),
        geom_model_(nullptr, nullptr),
        proj_(nullptr, nullptr)
    {}
    TAKErr DataElevationChunk::createData(ElevationChunkDataPtr &value) NOTHROWS
    {
        return data_loader_->createData(value);
    }
    TAKErr DataElevationChunk::sample(double *value, const double latitude, const double longitude) NOTHROWS
    {
        TAKErr code(TE_Ok);
        {
            Lock lock(mutex_);
            code = lock.status;
            TE_CHECKRETURN_CODE(code);

            if (!this->geom_model_.get()) {
                // initialize data if necessary
                if (!this->data_.get()) {
                    code = this->createData(this->data_);
                    TE_CHECKRETURN_CODE(code);
                }
                if (!data_.get() || !data_->value.get())
                    return TE_Err;
                // initialize projection if necessary
                if (data_->srid != 4326 && ProjectionFactory3_create(this->proj_, data_->srid) != TE_Ok)
                    return TE_Err;

                //this->geomModel = Models.createGeometryModel(data.value, data.localFrame);
                this->geom_model_ = GeometryModel2Ptr(new TAK::Engine::Math::Mesh(data_->value, &data_->localFrame), Memory_deleter_const<GeometryModel2, TAK::Engine::Math::Mesh>);
                if (!this->geom_model_.get())
                    return TE_Err;
            }

        }

        TAK::Engine::Math::Point2<double> rayOrg;
        TAK::Engine::Math::Point2<double> rayTgt;
        if (this->proj_.get()) {
            code = proj_->forward(&rayOrg, GeoPoint2(latitude, longitude, 30000.0, AltitudeReference::HAE));
            TE_CHECKRETURN_CODE(code);
            code = proj_->forward(&rayTgt, GeoPoint2(latitude, longitude, 0.0, AltitudeReference::HAE));
            TE_CHECKRETURN_CODE(code);
        } else {
            rayOrg = TAK::Engine::Math::Point2<double>(longitude, latitude, 30000.0);
            rayTgt = TAK::Engine::Math::Point2<double>(longitude, latitude, 0.0);
        }

        TAK::Engine::Math::Point2<double> isect;
        if (!this->geom_model_->intersect(&isect, Ray2<double>(rayOrg, Vector4<double>(rayTgt.x - rayOrg.x, rayTgt.y - rayOrg.y, rayTgt.z - rayOrg.z)))) {
            *value = NAN;
            return TE_InvalidArg;
        }

        double el;
        if (this->proj_.get()) {
            GeoPoint2 result;
            code = proj_->inverse(&result, isect);
            TE_CHECKRETURN_CODE(code);
            el = result.altitude;
        } else {
            el = isect.z;
        }
        *value = el;

        return code;
    }

    SampledElevationChunk::SampledElevationChunk(const char *type_, const char *uri_, const unsigned int flags_, const double resolution_, const Polygon2 &bounds_, const double ce_, const double le_, const bool authoritative_, SamplerPtr &&sampler_) NOTHROWS :
        AbstractElevationChunk(type_, uri_, flags_, resolution_, bounds_, ce_, le_, authoritative_),
        sampler_(std::move(sampler_)),
        data_(nullptr, nullptr)
    {}

    TAKErr SampledElevationChunk::createData(ElevationChunkDataPtr &value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        {
            Lock lock(mutex_);
            code = lock.status;
            TE_CHECKRETURN_CODE(code);

            if (!this->data_.get()) {
                Envelope2 aabb;
                code = this->getBounds()->getEnvelope(&aabb);
                TE_CHECKRETURN_CODE(code);

                const double centroidX = (aabb.minX + aabb.maxX) / 2.0;
                const double centroidY = (aabb.minY + aabb.maxY) / 2.0;

                std::shared_ptr<LineString2> bounds;
                code = static_cast<const Polygon2 &>(*this->getBounds()).getExteriorRing(bounds);
                TE_CHECKRETURN_CODE(code);

                // approximate number of posts based on resolution
                std::size_t samplesX;
                std::size_t samplesY;
                if ((aabb.maxX - aabb.minX) <= 180.0) {
                    const double dx1 = distance(*bounds, 0u, 1u);
                    const double dx2 = distance(*bounds, 2u, 3u);
                    const double dy1 = distance(*bounds, 1u, 2u);
                    const double dy2 = distance(*bounds, 3u, 0u);

                    samplesX = std::max((unsigned int)ceil(std::max(dx1, dx2) / getResolution()), 2u);
                    samplesY = std::max((unsigned int)ceil(std::max(dy1, dy2) / getResolution()), 2u);
                }
                else {
                    // approximate meters-per-degree
                    const double rlat = centroidY / 180.0 * M_PI;
                    const double metersDegLat = 111132.92 - 559.82 * cos(2 * rlat) + 1.175 * cos(4 * rlat);
                    const double metersDegLng = 111412.84 * cos(rlat) - 93.5 * cos(3 * rlat);

                    const double dx1 = estimateDistance(
                        metersDegLat, metersDegLng,
                        *bounds, 0u, 1u);
                    const double dx2 = estimateDistance(
                        metersDegLat, metersDegLng,
                        *bounds, 2u, 3u);
                    const double dy1 = estimateDistance(
                        metersDegLat, metersDegLng,
                        *bounds, 1u, 2u);
                    const double dy2 = estimateDistance(
                        metersDegLat, metersDegLng,
                        *bounds, 3u, 0u);

                    samplesX = std::max((unsigned int)ceil(std::max(dx1, dx2) / getResolution()), 2u);
                    samplesY = std::max((unsigned int)ceil(std::max(dy1, dy2) / getResolution()), 2u);
                }

                // construct function to convert between post and lat/lon

                atakmap::raster::DefaultDatasetProjection proj(
                    4326,
                    static_cast<int>(samplesX), static_cast<int>(samplesY),
                    getPoint(*bounds, 0u),
                    getPoint(*bounds, 1u),
                    getPoint(*bounds, 2u),
                    getPoint(*bounds, 3u));


                // construct the model by sampling the coverage
                MeshBuilder builder(TEDM_TriangleStrip, TEVA_Position, TEDT_UInt16);
                code = builder.setWindingOrder(TEWO_CounterClockwise);
                TE_CHECKRETURN_CODE(code);

                for (std::size_t y = 0; y < samplesY; y++) {
                    for (std::size_t x = 0; x < samplesX; x++) {
                        atakmap::core::GeoPoint geo;
                        proj.imageToGround(atakmap::math::Point<double>(static_cast<double>(x), static_cast<double>(y)), &geo);

                        double ela;
                        if (this->sampler_->sample(&ela, geo.latitude, geo.longitude) != TE_Ok)
                            ela = NAN;
                        code = builder.addVertex(geo.longitude - centroidX, geo.latitude - centroidY, ela,
                            0.0, 0.0,
                            0.0, 0.0, 1.0,
                            1.0, 1.0, 1.0, 1.0);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKRETURN_CODE(code);

                const std::size_t numIndices = TAK::Engine::Renderer::GLTexture2_getNumQuadMeshIndices(samplesX - 1, samplesY - 1);
                array_ptr<uint16_t> indices(new uint16_t[numIndices]);
                TAK::Engine::Renderer::GLTexture2_createQuadMeshIndexBuffer(indices.get(), samplesX - 1, samplesY - 1);
                code = builder.addIndices(indices.get(), numIndices);
                TE_CHECKRETURN_CODE(code);

                this->data_ = ElevationChunkDataPtr(new ElevationChunk::Data(), Memory_deleter_const<ElevationChunk::Data>);
                MeshPtr mesh(nullptr, nullptr);
                code = builder.build(mesh);
                TE_CHECKRETURN_CODE(code);
                this->data_->value = std::move(mesh);
                this->data_->srid = 4326;
                this->data_->interpolated = true;
                this->data_->localFrame.setToTranslate(centroidX, centroidY, 0.0);
            }
        }

        value = ElevationChunkDataPtr(new ElevationChunk::Data(), Memory_deleter_const<ElevationChunk::Data>);
        MeshPtr copy(nullptr, nullptr);
        code = Mesh_transform(copy, *data_->value, data_->value->getVertexDataLayout());
        TE_CHECKRETURN_CODE(code);

        value->value = std::move(copy);
        value->srid = data_->srid;
        value->interpolated = true;
        value->localFrame.set(data_->localFrame);
        
        return code;
    }
    TAKErr SampledElevationChunk::sample(double *value, const double latitude, const double longitude) NOTHROWS
    {
        return sampler_->sample(value, latitude, longitude);
    }
    TAKErr SampledElevationChunk::sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS
    {
        return sampler_->sample(value, count, srcLat, srcLng, srcLatStride, srcLngStride, dstStride);
    }

    template<class T>
    TAKErr default_sample(double *value, T &source, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!value)
            return TE_InvalidArg;

        for (std::size_t i = 0u; i < count; i++) {
            const double lat = srcLat[i*srcLatStride];
            const double lng = srcLng[i*srcLngStride];
            double *el = value + (i*dstStride);
            if (TE_ISNAN(*el)) {
                // XXX - break on errors here???
                if (source.sample(el, lat, lng) != TE_Ok)
                    code = TE_Done; // failed to fill atleast one sample
            }
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr validateBounds(const Polygon2 &bounds) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (bounds.getNumInteriorRings())
            return TE_InvalidArg;
        std::shared_ptr<LineString2> ring;
        code = bounds.getExteriorRing(ring);
        TE_CHECKRETURN_CODE(code);
        if (ring->getNumPoints() != 5u)
            return TE_InvalidArg;
        return code;
    }

    double distance(const LineString2 &bounds, const std::size_t a, const std::size_t b) NOTHROWS
    {
        double ax, ay;
        bounds.getX(&ax, a);
        bounds.getY(&ay, a);
        double bx, by;
        bounds.getX(&bx, b);
        bounds.getY(&by, b);

        return GeoPoint2_distance(
            GeoPoint2(ay, ax),
            GeoPoint2(by, bx), false);
    }

    double estimateDistance(const double metersDegLat, const double metersDegLng, const LineString2 &bounds, const std::size_t a, const std::size_t b) NOTHROWS
    {
        double ax, ay;
        bounds.getX(&ax, a);
        bounds.getY(&ay, a);
        double bx, by;
        bounds.getX(&bx, b);

        bounds.getY(&by, b);
        const double dlat = metersDegLat * (ay - by);
        const double dlng = metersDegLng * (ax - bx);
        return sqrt(dlat * dlat + dlng * dlng);
    }
}
