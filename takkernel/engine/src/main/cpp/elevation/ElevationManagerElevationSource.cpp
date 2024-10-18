#include "elevation/ElevationManagerElevationSource.h"

#include <set>

#include "elevation/ElevationChunkFactory.h"
#include "elevation/ElevationManager.h"
#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "thread/Thread.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    typedef TAKErr(*GenerateHeightmap)(double* value, const double resolution, const TAK::Engine::Feature::Envelope2 &mbb, const std::size_t numPostsLat, const std::size_t numPostsLng);
    
    TAKErr generateHeightmapBestResolution(double* value, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS;
    TAKErr generateHeightmapBestLower(double* value, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS;
    TAKErr generateHeightmapBestLowerFillHoles(double* value, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS;

    class ElevationSourceImpl : public ElevationSource
    {
    public :
        ElevationSourceImpl(const std::size_t numPostsLat, const std::size_t numPostsLng, GenerateHeightmap heightmapGenerator) NOTHROWS;
    public :
        const char *getName() const NOTHROWS override;
        TAKErr query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS override;
        TAK::Engine::Feature::Envelope2 getBounds() const NOTHROWS override;
        TAKErr addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS override;
        TAKErr removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS override;
    private :
        std::size_t numPostsLat;
        std::size_t numPostsLng;
        GenerateHeightmap heightmapGenerator;
        std::set<OnContentChangedListener*> listeners;
        Mutex mutex;
        ThreadPtr dispatcher;
    };
}

TAKErr TAK::Engine::Elevation::ElevationManagerElevationSource_create(ElevationSourcePtr& value, const std::size_t numPostsLat, const std::size_t numPostLng, const HeightmapStrategy strategy) NOTHROWS
{
    return TE_Unsupported;
}

namespace
{   
    TAKErr generateHeightmapBestResolution(double *els, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
#if 0
        TAKErr code(TE_Ok);

        const TAK::Engine::Feature::Envelope2 mbb(upperLeft.longitude, lowerRight.latitude, 0.0, lowerRight.longitude, upperLeft.latitude, 0.0);
        const double cellHeightLat = ((mbb.maxY - mbb.minY) / (numPostsLat - 1));
        const double cellWidthLng = ((mbb.maxX - mbb.minX) / (numPostsLng - 1));
        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        const std::size_t numEdgeVertices = ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;

        // fetch requested region plus a one post border for normals generation
        double *pts = els + ((numPostsLat+2u) * (numPostsLng+2u));
        std::size_t numPosts = 0u;
        for (int postLat = -1; postLat < (int)(numPostsLat+1u); postLat++) {
            double ptLat =  mbb.minY + cellHeightLat * postLat;
            if(ptLat > 90.0)
                ptLat = 180.0 - ptLat;
            else if(ptLat < -90.0)
                ptLat = -180.0 - ptLat;
            for (int postLng = -1; postLng < (int)(numPostsLng+1u); postLng++) {
                double ptLng = mbb.minX + cellWidthLng * postLng;
                if(ptLng < -180.0)
                    ptLng += 360.0;
                else if(ptLng > 180.0)
                    ptLng -= 360.0;
                pts[numPosts*2u] = ptLng;
                pts[numPosts*2u+1u] = ptLat;
                numPosts++;
            }
        }

        ElevationSource::QueryParameters params;
        params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
        code = params.order->add(ElevationSource::QueryParameters::ResolutionDesc);
        TE_CHECKRETURN_CODE(code);
        if(constrainQueryRes)
            params.maxResolution = resolution;
        code = Polygon2_fromEnvelope(params.spatialFilter, mbb);
        TE_CHECKRETURN_CODE(code);

        // try to fill all values using high-to-low res elevation chunks
        // covering the AOI
        if(ElevationManager_getElevation(els, numPosts, pts+1u, pts, 2u, 2u, 1u, params) == TE_Done && constrainQueryRes) {
            // if there are holes, fill in using low-to-high res elevation
            // chunks covering the AOI
            params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
            code = params.order->add(ElevationSource::QueryParameters::ResolutionAsc);
            params.maxResolution = NAN;

            std::size_t numpts2 = 0u;
            array_ptr<double> pts2(new double[(numPostsLat*numPostsLng)*3u]);
            for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                if (TE_ISNAN(els[i])) {
                    pts2[numpts2*3u] = pts[i*2+1u];
                    pts2[numpts2*3u+1u] = pts[i*2+1u];
                    pts2[numpts2*3+2u] = NAN;

                    numpts2++;
                }
            }

            // fetch the elevations
            ElevationManager_getElevation(pts2.get()+2u, (numPostsLat*numPostsLng), pts2.get()+1u, pts2.get(), 3u, 3u, 3u, params);

            // fill the holes
            std::size_t pts2Idx = 0;
            for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                if (TE_ISNAN(els[i])) {
                    els[i] = pts2[pts2Idx*3u+2u];
                    pts2Idx++;
                    if(pts2Idx == numpts2)
                        break;
                }
            }
        }
#endif
        return TE_Unsupported;
    }
    TAKErr generateHeightmapBestLower(double* value, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr generateHeightmapBestLowerFillHoles(double* value, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
#if 0
        TAKErr code(TE_Ok);

        const TAK::Engine::Feature::Envelope2 mbb(upperLeft.longitude, lowerRight.latitude, 0.0, lowerRight.longitude, upperLeft.latitude, 0.0);
        const double cellHeightLat = ((mbb.maxY - mbb.minY) / (numPostsLat - 1));
        const double cellWidthLng = ((mbb.maxX - mbb.minX) / (numPostsLng - 1));
        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        const std::size_t numEdgeVertices = ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;

        // fetch requested region plus a one post border for normals generation
        double *pts = els + ((numPostsLat+2u) * (numPostsLng+2u));
        std::size_t numPosts = 0u;
        for (int postLat = -1; postLat < (int)(numPostsLat+1u); postLat++) {
            double ptLat =  mbb.minY + cellHeightLat * postLat;
            if(ptLat > 90.0)
                ptLat = 180.0 - ptLat;
            else if(ptLat < -90.0)
                ptLat = -180.0 - ptLat;
            for (int postLng = -1; postLng < (int)(numPostsLng+1u); postLng++) {
                double ptLng = mbb.minX + cellWidthLng * postLng;
                if(ptLng < -180.0)
                    ptLng += 360.0;
                else if(ptLng > 180.0)
                    ptLng -= 360.0;
                pts[numPosts*2u] = ptLng;
                pts[numPosts*2u+1u] = ptLat;
                numPosts++;
            }
        }

        ElevationSource::QueryParameters params;
        params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
        code = params.order->add(ElevationSource::QueryParameters::ResolutionDesc);
        TE_CHECKRETURN_CODE(code);
        // don't fetch any higher than target resolution
        params.maxResolution = resolution;

        code = Polygon2_fromEnvelope(params.spatialFilter, mbb);
        TE_CHECKRETURN_CODE(code);

        // try to fill all values using high-to-low res elevation chunks
        // covering the AOI
        if(ElevationManager_getElevation(els, numPosts, pts+1u, pts, 2u, 2u, 1u, params) == TE_Done) {
            // if there are holes, fill in using low-to-high res elevation
            // chunks covering the AOI
            params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
            code = params.order->add(ElevationSource::QueryParameters::ResolutionAsc);
            params.maxResolution = NAN;

            std::size_t numpts2 = 0u;
            array_ptr<double> pts2(new double[(numPostsLat*numPostsLng)*3u]);
            for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                if (TE_ISNAN(els[i])) {
                    pts2[numpts2*3u] = pts[i*2+1u];
                    pts2[numpts2*3u+1u] = pts[i*2+1u];
                    pts2[numpts2*3+2u] = NAN;

                    numpts2++;
                }
            }

            // fetch the elevations
            ElevationManager_getElevation(pts2.get()+2u, (numPostsLat*numPostsLng), pts2.get()+1u, pts2.get(), 3u, 3u, 3u, params);

            // fill the holes
            std::size_t pts2Idx = 0;
            for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                if (TE_ISNAN(els[i])) {
                    els[i] = pts2[pts2Idx*3u+2u];
                    pts2Idx++;
                    if(pts2Idx == numpts2)
                        break;
                }
            }
        }
#endif
        return TE_Unsupported;
    }

    ElevationSourceImpl::ElevationSourceImpl(const std::size_t numPostsLat_, const std::size_t numPostsLng_, GenerateHeightmap heightmapGenerator_) NOTHROWS :
        numPostsLat(numPostsLat_),
        numPostsLng(numPostsLng_),
        heightmapGenerator(heightmapGenerator_),
        dispatcher(nullptr, nullptr)
    {}
    
    const char* ElevationSourceImpl::getName() const NOTHROWS
    {
        return "ElevationManager";
    }
    TAKErr ElevationSourceImpl::query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAK::Engine::Feature::Envelope2 ElevationSourceImpl::getBounds() const NOTHROWS
    {
        return TAK::Engine::Feature::Envelope2(-180.0, -90.0, 0.0, 180.0, 90.0, 0.0);
    }
    TAKErr ElevationSourceImpl::addOnContentChangedListener(ElevationSource::OnContentChangedListener *l) NOTHROWS
    {
        if (!l)
            return TE_InvalidArg;
        Lock lock(mutex);
        listeners.insert(l);
        return TE_Ok;
    }
    TAKErr ElevationSourceImpl::removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS
    {
        if (!l)
            return TE_InvalidArg;
        Lock lock(mutex);
        listeners.erase(l);
        return TE_Ok;
    }
}
