#ifndef TAK_ENGINE_RASTER_TILECLIENT_H_INCLUDED
#define TAK_ENGINE_RASTER_TILECLIENT_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"
#include "port/String.h"
#include "feature/Geometry2.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {

                enum CacheMode {
                    TECM_Create,
                    TECM_Append
                };

                struct ENGINE_API CacheRequest {

                    CacheRequest();
                    CacheRequest(const CacheRequest &other);
                    ~CacheRequest() NOTHROWS;

                    double minResolution;
                    double maxResolution;
                    Feature::Geometry2Ptr_const region;
                    int64_t timeSpanStart;
                    int64_t timeSpanEnd;
                    Port::String cacheFilePath;
                    CacheMode mode;
                    bool canceled;
                    bool countOnly;
                    int maxThreads;
                    int64_t expirationOffset;
                    Port::String preferredContainerProvider;
                };

                class ENGINE_API CacheRequestListener {
                public:
                    virtual ~CacheRequestListener() NOTHROWS;
                    virtual Util::TAKErr onRequestStarted() NOTHROWS = 0;
                    virtual Util::TAKErr onRequestComplete() NOTHROWS = 0;
                    virtual Util::TAKErr onRequestProgress(int taskNum, int numTasks, int taskProgress, int maxTaskProgress, int totalProgress, int maxTotalProgress) NOTHROWS = 0;
                    virtual Util::TAKErr onRequestError(const char *message, bool fatal) NOTHROWS = 0;
                    virtual Util::TAKErr onRequestCanceled() NOTHROWS = 0;
                };
                typedef std::unique_ptr<CacheRequestListener, void (*)(const CacheRequestListener *)> CacheRequestListenerPtr;

                class ENGINE_API TileClient : public TileMatrix {
                public:
                    virtual ~TileClient() NOTHROWS;
                    virtual Util::TAKErr clearAuthFailed() NOTHROWS = 0;
                    virtual Util::TAKErr checkConnectivity() NOTHROWS = 0;
                    virtual Util::TAKErr cache(const CacheRequest &request, std::shared_ptr<CacheRequestListener> &listener) NOTHROWS = 0;
                    virtual Util::TAKErr estimateTilecount(int *count, const CacheRequest &request) NOTHROWS = 0;
                };

                typedef std::unique_ptr<TileClient, void (*)(const TileClient *)> TileClientPtr;

            }
        }
    }
}

#endif
