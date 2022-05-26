#ifndef TAK_ENGINE_UTIL_URI_H_INCLUDED
#define TAK_ENGINE_UTIL_URI_H_INCLUDED

#include "util/ProtocolHandler.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            /**
             *
             */
            ENGINE_API TAKErr URI_open(DataInput2Ptr &result, const char *URI) NOTHROWS;

            /**
             *
             */
            ENGINE_API TAKErr URI_registerProtocolHandler(const std::shared_ptr<ProtocolHandler> &protHandler, int priority) NOTHROWS;

            /**
             *
             */
            ENGINE_API TAKErr URI_unregisterProtocolHandler(const std::shared_ptr<ProtocolHandler>& protHandler) NOTHROWS;

            /**
             * Parses parts of a URI.
             *
             * @param scheme [optional] the resulting scheme
             * @param authority [optional] the resulting authority
             * @param userInfo [optional] the resulting user info portion of the authority
             * @param host [optional] the resulting host
             * @param port [optional] the resulting port portion of the authority
             * @param path [optional] the resulting path
             * @param query [optional] the resulting query
             * @param fragment [optional] the resulting fragment
             * @param URI the uri to parse
             *
             * @return TE_Ok if the URI is well formed, else TE_InvalidArg
             */
            ENGINE_API TAKErr URI_parse(
                Port::String* scheme,
                Port::String* authority,
                Port::String* userInfo,
                Port::String* host,
                Port::String* port,
                Port::String* path,
                Port::String* query,
                Port::String* fragment,
                const char* URI) NOTHROWS;

            /**
             * Parses parts of a URI.
             *
             * @param scheme [optional] the resulting scheme
             * @param authority [optional] the resulting authority
             * @param host [optional] the resulting host
             * @param path [optional] the resulting path
             * @param query [optional] the resulting query
             * @param fragment [optional] the resulting fragment
             * @param URI the uri to parse
             *
             * @return TE_Ok if the URI is well formed, else TE_InvalidArg
             */
            ENGINE_API TAKErr URI_parse(
                Port::String *scheme,
                Port::String *authority,
                Port::String *host,
                Port::String *path,
                Port::String *query,
                Port::String *fragment,
                const char *URI) NOTHROWS;

            /**
             * Get the parent URI of a given URI
             * 
             * @param result [out] the resulting parent URI
             * @param URI the uri
             * 
             * @return TE_Ok if parent exists, TE_Done if no parent exists but URI is valid, else any possible TAKErr error
             */
            ENGINE_API TAKErr URI_getParent(Port::String *result, const char *URI) NOTHROWS;

            /**
             * Combine a URI with a path suffix
             * 
             * @param result [out] the resulting combined URI
             * @param base the URI to append to
             * @param suffix the path suffix
             */
            ENGINE_API TAKErr URI_combine(Port::String* result, const char *base, const char* suffix) NOTHROWS;

            /**
             *
             */
            ENGINE_API TAKErr URI_getRelative(Port::String* result, const char* baseURI, const char* URI) NOTHROWS;
        }
    }
}

#endif