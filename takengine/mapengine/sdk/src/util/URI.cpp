
#include <map>
#include <functional>
#include <regex>
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

    static const std::regex uri_regex_(
        "(?:([a-zA-Z][a-zA-Z0-9+.-]*):)?" // scheme
        "(?://([^/?#]*))?" // authority
        "(?:([^\\?#]*))?" // path
        "(?:\\?([^#]*))?" // query
        "(?:#(.*))?"); // fragment

    static const std::regex uri_auth_regex_(
        "(?:(.*)@)?" // userInfo
        "([^:]*)" // host
        "(?:\\:(.*))?"); // port

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

    std::string uriStr = URI;
    std::smatch baseMatch;

    if (!std::regex_match(uriStr, baseMatch, uri_regex_))
        return TE_InvalidArg;

    if (scheme && baseMatch.size() >= 2 && baseMatch[1].matched)
        *scheme = baseMatch[1].str().c_str();
    if (authority && baseMatch.size() >= 3 && baseMatch[2].matched)
        *authority = baseMatch[2].str().c_str();
    if (path && baseMatch.size() >= 4 && baseMatch[3].matched)
        *path = baseMatch[3].str().c_str();

    if ((host || userInfo || port) && baseMatch.size() >= 3 && baseMatch[2].matched) {
        std::smatch hostMatch;
        std::string auth = baseMatch[2].str();
        if (!std::regex_match(auth, hostMatch, uri_auth_regex_))
            return TE_InvalidArg;

        if (userInfo && hostMatch.size() >= 2 && hostMatch[1].matched)
            *userInfo = hostMatch[1].str().c_str();
        if (host && hostMatch.size() >= 3 && hostMatch[2].matched)
            *host = hostMatch[2].str().c_str();
        if (port && hostMatch.size() >= 4 && hostMatch[3].matched)
            *port = hostMatch[3].str().c_str();
    }

    if (query && baseMatch.size() >= 5 && baseMatch[4].matched)
        *query = baseMatch[4].str().c_str();
    if (fragment && baseMatch.size() >= 6 && baseMatch[5].matched)
        *fragment = baseMatch[5].str().c_str();

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