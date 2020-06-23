#ifndef TAK_ENGINE_UTIL_PROTOCOLHANDLER_H_INCLUDED
#define TAK_ENGINE_UTIL_PROTOCOLHANDLER_H_INCLUDED

#include "port/Platform.h"
#include "util/DataInput2.h"
#include "util/Error.h"
#include "util/FutureTask.h"

namespace TAK
{
    namespace Engine
    {
        namespace Util
        {
            class ENGINE_API ProtocolHandler
            {
            protected :
                virtual ~ProtocolHandler() NOTHROWS = 0;
            public:
                // Return NULL if not able to handle, else return pointer to 
                // binding-level-specific IO context.
                virtual Util::TAKErr handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS = 0;
            };

            class ENGINE_API FileProtocolHandler : public ProtocolHandler
            {
            public:

                Util::TAKErr handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS override;
            };

            class ENGINE_API ZipProtocolHandler : public ProtocolHandler
            {
            public:

                Util::TAKErr handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS override;
            };
        }
    }
}


#endif

