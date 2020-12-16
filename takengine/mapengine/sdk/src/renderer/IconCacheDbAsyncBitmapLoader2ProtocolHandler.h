#ifndef TAK_ENGINE_RENDERER_ICON_CACHE_DB_ASYNCBITMAPLOADER2_PROTOCOL_HANDLER_H_INCLUDED
#define TAK_ENGINE_RENDERER_ICON_CACHE_DB_ASYNCBITMAPLOADER2_PROTOCOL_HANDLER_H_INCLUDED

#include "renderer/AsyncBitmapLoader2.h"

#include "db/SpatiaLiteDB.h"

#include "port/String.h"

#include "thread/Mutex.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            class IconCacheDbAsyncBitmapLoader2ProtocolHandler : public AsyncBitmapLoader2::ProtocolHandler {
            public:
                IconCacheDbAsyncBitmapLoader2ProtocolHandler(const char *iconCacheFilePath) NOTHROWS;
                virtual ~IconCacheDbAsyncBitmapLoader2ProtocolHandler();
                virtual Util::TAKErr handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS;
            private:
                Thread::Mutex mutex;
                
                TAK::Engine::Port::String iconCacheFile;
                
                //XXX-- use spatialite DB for now
                std::unique_ptr<atakmap::db::SpatiaLiteDB> iconCacheDb;
            };
        }
    }
}

#endif