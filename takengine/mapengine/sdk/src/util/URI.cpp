
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
        "([a-zA-Z][a-zA-Z0-9+.-]*):" // scheme
        "([^?#]*)" // authority
        "(?:\\?([^#]*))?" // query
        "(?:#(.*))?"); // fragment

    static const std::regex uri_auth_and_path_regex_(
        "//([^/]*)" // host
        "(/.*)?"); // path
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
    String *scheme,
    String *authority,
    String *host,
    String *path,
    String *query,
    String *fragment,
    const char *URI) NOTHROWS {
    
    if (!URI)
        return TE_InvalidArg;

    std::string uriStr = URI;
    std::smatch baseMatch;

    if (!std::regex_match(uriStr, baseMatch, uri_regex_))
        return TE_InvalidArg;

    if (scheme && baseMatch.size() >= 2 && baseMatch[1].matched)
        *scheme = baseMatch[1].str().c_str();
    if ((authority || host || path) && baseMatch.size() >= 3 && baseMatch[2].matched) {
        std::string auth = baseMatch[2].str();
        if (authority)
            *authority = auth.c_str();
        if (host || path) {
            std::smatch hostMatch;
            if (!std::regex_match(auth, hostMatch, uri_auth_and_path_regex_))
                return TE_InvalidArg;
            if (host && hostMatch.size() >= 2 && hostMatch[1].matched)
                *host = hostMatch[1].str().c_str();
            if (path && hostMatch.size() >= 3 && hostMatch[2].matched)
                *path = hostMatch[2].str().c_str();
        }
    }
    if (query && baseMatch.size() >= 4 && baseMatch[3].matched)
        *query = baseMatch[3].str().c_str();
    if (fragment && baseMatch.size() >= 5)
        *fragment = baseMatch[4].str().c_str();

    return TE_Ok;
}

TAKErr TAK::Engine::Util::URI_getParent(Port::String* result, const char* URI) NOTHROWS {
    
    if (!result)
        return TE_InvalidArg;

    String scheme;
    String authority;
    TAKErr code = URI_parse(&scheme, &authority, nullptr, nullptr, nullptr, nullptr, URI);
    if (code != TE_Ok)
        return code;

    if (!authority)
        return TE_InvalidArg;

    std::string authStr = authority.get();
    if (authStr.size() > 0 && authStr.back() == '/')
        authority = authStr.substr(0, authStr.size() - 1).c_str();

    String parentAuth;
    IO_getParentFile(parentAuth, authority.get());

    if (!parentAuth || strcmp(parentAuth, "/") == 0)
        return TE_Done;

    StringBuilder sb;
    if (scheme) {
        sb.append(scheme);
        sb.append(":");
    }
    sb.append(parentAuth);
    sb.append("/");
    *result = sb.c_str();

    return TE_Ok;
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

}