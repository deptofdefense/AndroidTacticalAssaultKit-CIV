#ifndef TAK_ENGINE_RENDERER_URI_LOADER_ASYNCBITMAPLOADER2_PROTOCOL_HANDLER_H_INCLUDED
#define TAK_ENGINE_RENDERER_URI_LOADER_ASYNCBITMAPLOADER2_PROTOCOL_HANDLER_H_INCLUDED

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
            class URILoaderBitmapLoader2ProtocolHandler : public AsyncBitmapLoader2::ProtocolHandler {
            public:
                URILoaderBitmapLoader2ProtocolHandler() NOTHROWS;
                virtual ~URILoaderBitmapLoader2ProtocolHandler();
                virtual Util::TAKErr handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS;
            };
        }
    }
}

#endif