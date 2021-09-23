
#include <algorithm>
#include <sstream>
#include <map>
#include <functional>
#include "util/URI.h"
#include "util/CopyOnWrite.h"
#include "port/StringBuilder.h"
#include "util/IO2.h"

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace {
    class URIFactoryRegistry {
    public:
        TAKErr registerProtocolHandler(std::shared_ptr<ProtocolHandler> protHandler, int priority);
        TAKErr unregisterProtocolHandler(const std::shared_ptr<ProtocolHandler>& protHandler);
        TAKErr open(DataInput2Ptr& result, const char* URI) const NOTHROWS;

    private:
        std::multimap<int, std::shared_ptr<ProtocolHandler>, std::greater<int>> priority_sorted_;
    };

    CopyOnWrite<URIFactoryRegistry>& sharedURIFactoryRegistry() {
        static CopyOnWrite<URIFactoryRegistry> impl;
        return impl;
    }

    TAKErr buildImpl(
        String* result,
        const char* scheme,
        const char* auth,
        const char* pathPrefix,
        const char* pathSuffix,
        const char* query,
        const char* frag) NOTHROWS;
}

TAKErr TAK::Engine::Util::URI_open(DataInput2Ptr& result, const char* URI) NOTHROWS {
    TAKErr code = sharedURIFactoryRegistry().read()->open(result, URI);

    // fallback on filesystem
    if (code == TE_Unsupported) {
        code = IO_openFileV(result, URI);
    }

    return code;
}

TAKErr TAK::Engine::Util::URI_registerProtocolHandler(const std::shared_ptr<ProtocolHandler>& protHandler, int priority) NOTHROWS {
    return sharedURIFactoryRegistry().invokeWrite(&URIFactoryRegistry::registerProtocolHandler, protHandler, priority);
}

TAKErr TAK::Engine::Util::URI_unregisterProtocolHandler(const std::shared_ptr<ProtocolHandler>& protHandler) NOTHROWS {
    return sharedURIFactoryRegistry().invokeWrite(&URIFactoryRegistry::unregisterProtocolHandler, protHandler);
}

TAKErr TAK::Engine::Util::URI_parse(
    String* scheme,
    String* authority,
    String* host,
    String* path,
    String* query,
    String* fragment,
    const char* URI) NOTHROWS {

    return URI_parse(scheme, authority, nullptr, host, nullptr, path, query, fragment, URI);
}

TAKErr TAK::Engine::Util::URI_parse(
    String* scheme,
    String* authority,
    String* userInfo,
    String* host,
    String* port,
    String* path,
    String* query,
    String* fragment,
    const char* URI) NOTHROWS {

    if (!URI)
        return TE_InvalidArg;

    std::string sURI(URI);

    /*
    Parses URIs using the following syntax, https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax

    The URI generic syntax consists of a hierarchical sequence of five components : [22]

        URI = scheme:[//authority]path[?query][#fragment]
            where the authority component divides into three subcomponents :

            authority = [userinfo@]host[:port]

    begin / end pairs include the special character, ie queryBegin includes the '?' character, schemeEnd includes the ':' character

     */

    std::size_t schemeBegin = 0;
    std::size_t schemeEnd = sURI.find_first_of(':');
    if (schemeEnd == std::string::npos)
        schemeEnd = 0;
    else
        schemeEnd += 1;  // ':' char

    if (scheme && schemeEnd != 0)
    {
        *scheme = String(URI + schemeBegin, (schemeEnd - 1) - schemeBegin);
    }

    std::size_t queryBegin = sURI.find_first_of('?', schemeEnd);
    std::size_t fragmentBegin = sURI.find_first_of('#', schemeEnd);
    std::size_t authorityBegin = sURI.find("//");

    if (authorityBegin != schemeEnd)
        authorityBegin = std::string::npos;

    std::size_t authorityEnd = schemeEnd;
    if (authorityBegin != std::string::npos)
    {
        authorityEnd = sURI.find_first_of('/', authorityBegin + 2);
        if (authorityEnd == std::string::npos)
            authorityEnd = std::min(sURI.length(), std::min(queryBegin, fragmentBegin));
    }
    else
        authorityBegin = schemeEnd;

    if(authorityBegin != std::string::npos && authorityEnd > authorityBegin + 2)
    {
        if (authority)
        {
            *authority = String(URI+authorityBegin + 2, authorityEnd - (authorityBegin + 2));
        }
        std::size_t userEnd = authorityBegin + 2;
        std::size_t userBegin = authorityBegin + 2;

        userEnd = sURI.find_first_of('@', authorityBegin);
        if (userEnd > authorityEnd)
            userEnd = userBegin;
        else
            userEnd += 1;

        if (userInfo)
        {
            if (userEnd != std::string::npos && userEnd != userBegin)
            {
                *userInfo = String(URI+ userBegin, (userEnd - 1) - userBegin);
            }
        }

        std::size_t portBegin = sURI.find_first_of(':', userEnd);
        if (portBegin > authorityEnd)
            portBegin = authorityEnd;

        std::size_t hostBegin = userEnd;
        std::size_t hostEnd = portBegin;

        if (host && hostEnd > hostBegin + 1)
        {
            *host = String(URI + hostBegin, hostEnd - (hostBegin));
        }
        if (port && authorityEnd > portBegin + 1)
        {
            *port = String(URI + portBegin + 1, authorityEnd - (portBegin + 1));
        }
    }
        
    std::size_t pathBegin = authorityEnd;
    std::size_t pathEnd = std::min(sURI.length(), std::min(queryBegin, fragmentBegin));

    if (path && pathEnd > pathBegin)
    {
        *path = String(URI + pathBegin, pathEnd - pathBegin);
    }

    if (fragment && sURI.length() > fragmentBegin + 1)
    {
        if (fragmentBegin != std::string::npos)
        {
            *fragment = String(URI + fragmentBegin + 1, sURI.length() - (fragmentBegin + 1));
        }
    }
    if (query)
    {
        if (queryBegin != std::string::npos)
        {
            std::size_t queryEnd = std::min(sURI.length(), fragmentBegin);
            *query = String(URI + queryBegin + 1, queryEnd - (queryBegin + 1));
        }
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Util::URI_getParent(Port::String* result, const char* URI) NOTHROWS {

    if (!result)
        return TE_InvalidArg;

    String scheme;
    String auth;
    String path;
    String query;
    String frag;
    TAKErr code = URI_parse(&scheme, &auth, nullptr, &path, &query, &frag, URI);
    if (code != TE_Ok)
        return code;

    if (!path) {
        *result = URI;
        return TE_Done;
    }

    String parentPath;
    IO_getParentFile(parentPath, path.get());

    if (!parentPath || strcmp(parentPath, "/") == 0) {
        *result = URI;
        return TE_Done;
    }

    return buildImpl(result, scheme.get(), auth.get(), parentPath.get(), "", query.get(), frag.get());
}



TAKErr TAK::Engine::Util::URI_combine(Port::String* result, const char* base, const char* suffix) NOTHROWS {

    if (!result || !base || !suffix)
        return TE_InvalidArg;

    // deconstruct
    String scheme;
    String auth;
    String path;
    String query;
    String frag;

    TAKErr code = URI_parse(&scheme, &auth, nullptr, &path, &query, &frag, base);
    if (code == TE_Ok)
        return buildImpl(result, scheme.get(), auth.get(), path.get(), suffix, query.get(), frag.get());
    return buildImpl(result, nullptr, nullptr, base, suffix, nullptr, nullptr);
}

TAKErr TAK::Engine::Util::URI_getRelative(Port::String* result, const char* baseURI, const char* URI) NOTHROWS {

    if (!result)
        return TE_InvalidArg;

    String scheme, baseScheme;
    String auth, baseAuth;
    String path, basePath;
    String query, baseQuery;
    String frag, baseFrag;

    TAKErr code = URI_parse(&scheme, &auth, nullptr, &path, &query, &frag, URI);
    if (code != TE_Ok)
        return code;

    code = URI_parse(&baseScheme, &baseAuth, nullptr, &basePath, &baseQuery, &baseFrag, baseURI);
    if (code != TE_Ok)
        return code;

    if (!(scheme == baseScheme || String_strcasecmp(scheme, baseScheme) == 0))
        return TE_InvalidArg;
    if (!(auth == baseAuth || String_strcasecmp(scheme, baseScheme) == 0))
        return TE_InvalidArg;
    if (path == nullptr || basePath == nullptr)
        return TE_InvalidArg;

    const char* bp = basePath.get();
    const char* pp = path.get();
    while (*bp && *pp && *bp == *pp) {
        ++bp;
        ++pp;
    }

#define IS_PATH_SEP(c) \
    (c) == '\\' || (c) == '/' || (c) == ':'


    if (*bp == '\0' && bp > basePath.get() && pp > path.get()) {
        if (IS_PATH_SEP(pp[-1])) {
            *result = pp;
            return TE_Ok;
        }
        else if (IS_PATH_SEP(pp[0])) {
            *result = pp + 1;
            return TE_Ok;
        }
    }

    return TE_InvalidArg;
}

namespace {
    TAKErr URIFactoryRegistry::registerProtocolHandler(std::shared_ptr<ProtocolHandler> protHandler, int priority) {
        if (!protHandler)
            return TE_InvalidArg;
        priority_sorted_.insert(std::make_pair(priority, protHandler));
        return TE_Ok;
    }

    TAKErr URIFactoryRegistry::unregisterProtocolHandler(const std::shared_ptr<ProtocolHandler>& protHandler) {
        for (auto it = priority_sorted_.begin(); it != priority_sorted_.end();) {
            if (it->second == protHandler)
                it = priority_sorted_.erase(it);
            else
                ++it;
        }
        return TE_Ok;
    }

    TAKErr URIFactoryRegistry::open(DataInput2Ptr& result, const char* URI) const NOTHROWS {
        TAKErr code = TE_Unsupported;
        for (auto& entry : priority_sorted_) {
            std::shared_ptr<ProtocolHandler> handler = entry.second;
            code = handler->handleURI(result, URI);
            if (code != TE_Unsupported)
                break;
        }
        return code;
    }

    TAKErr buildImpl(
        String* result,
        const char* scheme,
        const char* auth,
        const char* pathPrefix,
        const char* pathSuffix,
        const char* query,
        const char* frag) NOTHROWS {

        if (scheme) {

            // assume path sep is '/'
            const char* sep = "/";
            if ((pathPrefix && String_endsWith(pathPrefix, "/")) || *pathSuffix == '/' || *pathSuffix == '\0')
                sep = "";

            StringBuilder sb;
            TAKErr code = StringBuilder_combine(sb,
                scheme,
                ":",
                auth ? "//" : "",
                auth ? auth : "",
                pathPrefix,
                sep,
                pathSuffix,
                query ? "?" : "",
                query ? query : "",
                frag ? "#" : "",
                frag ? frag : "");
            if (code != TE_Ok)
                return code;

            *result = sb.c_str();
            return TE_Ok;
        }

        // no scheme-- treat as just a path

        char sep[2] = { Platform_pathSep(), '\0' };

        // already ends with sep?
        if (String_endsWith(pathPrefix, "\\") ||
            String_endsWith(pathPrefix, "/")) {
            sep[0] = '\0';
        }

        StringBuilder sb;
        TAKErr code = StringBuilder_combine(sb, pathPrefix, sep, pathSuffix);
        if (code != TE_Ok)
            return code;

        // correct any seps not native to platform
        return IO_correctPathSeps(*result, sb.c_str());
    }
}