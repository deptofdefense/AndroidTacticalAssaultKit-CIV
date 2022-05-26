#include "ProtocolHandler.h"

#include <map>
#include <regex>
#include <sstream>
#include <vector>

#include "port/String.h"
#include "thread/RWMutex.h"
#include "util/IO2.h"
#include "util/Logging2.h"


using namespace TAK::Engine::Util;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;

namespace {
    TAKErr normalizePath(std::string &normalized, const std::string& source, const bool &preserve_leading_separator);
    RWMutex& decoderHandlerMutex() NOTHROWS
    {
        static RWMutex m;
        return m;
    }
    std::map<std::string, ProtocolHandler*>& protoHandlers() NOTHROWS
    {
        static std::map<std::string, ProtocolHandler*> m;
        return m;
    }
}

ProtocolHandler::~ProtocolHandler() NOTHROWS
{}

TAKErr TAK::Engine::Util::ProtocolHandler_registerHandler(const char *scheme, ProtocolHandler &handler) NOTHROWS
{
    if (!scheme)
        return TE_InvalidArg;
    WriteLock lock(decoderHandlerMutex());
    protoHandlers()[scheme] = &handler;
    return TE_Ok;
}

TAKErr TAK::Engine::Util::ProtocolHandler_unregisterHandler(const char *scheme) NOTHROWS
{
    if (!scheme)
        return TE_InvalidArg;
    WriteLock lock(decoderHandlerMutex());
    if (protoHandlers().find(scheme) == protoHandlers().end())
        return TE_InvalidArg;
    protoHandlers().erase(scheme);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::ProtocolHandler_unregisterHandler(const ProtocolHandler &handler) NOTHROWS
{
    TAKErr code(TE_Ok);
    WriteLock lock(decoderHandlerMutex());
    TE_CHECKRETURN_CODE(code);
    auto entry = protoHandlers().begin();
    code = TE_InvalidArg;
    while(entry != protoHandlers().end()) {
        if (entry->second != &handler) {
            entry++;
        } else {
            entry = protoHandlers().erase(entry);
            code = TE_Ok;
        }
    }

    return code;
}
TAKErr TAK::Engine::Util::ProtocolHandler_handleURI(DataInput2Ptr& ctx, const char* curi) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!curi)
        return TE_InvalidArg;
    std::string uri(curi);
    // First try to handle the protocol
    size_t cloc = uri.find_first_of(':');
    if (cloc == std::string::npos)
        return TE_InvalidArg;

    std::string scheme = uri.substr(0, cloc);

    ReadLock lock(decoderHandlerMutex());
    auto handler = protoHandlers().find(scheme);
    if (handler == protoHandlers().end())
        return TE_InvalidArg;

    return handler->second->handleURI(ctx, curi);
}
bool TAK::Engine::Util::ProtocolHandler_isHandlerRegistered(const char* scheme) NOTHROWS
{
    ReadLock lock(decoderHandlerMutex());
    auto handler = protoHandlers().find(scheme);
    return (handler != protoHandlers().end());
}

TAKErr FileProtocolHandler::handleURI(DataInput2Ptr &ctx, const char * uri) NOTHROWS
{
    std::string target(uri);
    static const std::regex rx("([[:alpha:]]+):\\/\\/(.+)");
    std::smatch matches;

#ifdef MSVC
    if (target.find("file:///") == 0u) {
        target.replace(target.find("file:///"), sizeof("file:///") - 1, "file://");
    }
#endif
    // URI encoding may encode a space character as %20
    while (target.find("%20") != std::string::npos) {
        target.replace(target.find("%20"), sizeof("%20") - 1, " ");
    }

    enum { match_full, match_scheme, match_path, match_quantity = match_path };

    if (std::regex_match(target, matches, rx)) {
        if (match_quantity > matches.size()) {
            Logger_log(TELL_Warning, "Malformed File URI: %s", uri);
            return TE_InvalidArg;
        }

        int fileCompare(0);
        String_compareIgnoreCase(&fileCompare, "file", matches[1].str().c_str());

        if (!!fileCompare) {
            Logger_log(TELL_Warning, "Incorrect URI scheme, \"%s,\" in FileProtocolHandler; full URI: %s", matches[match_scheme].str().c_str(), uri);
            return TE_Unsupported;
        }
    }

    return match_quantity < matches.size() ? IO_openFile(ctx, matches[match_path].str().c_str()) : IO_openFile(ctx, uri);
}

TAKErr ZipProtocolHandler::handleURI(DataInput2Ptr &ctx, const char * uri) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!uri)
        return TE_InvalidArg;

    const std::string target(uri);
    static const std::regex rx("([[:alpha:]]+):\\/\\/([^!]+)!?([^!]+)?");
    std::smatch matches;

    enum { match_full, match_scheme, match_archive, match_entry, match_quantity = match_entry };

    if (!std::regex_match(target, matches, rx) ||
        (match_quantity > matches.size())) {
        Logger_log(TELL_Warning, "Malformed zip URI: %s", uri);
        return TE_InvalidArg;
    }

    int zipCompare(0), arcCompare(0);
    const std::string &scheme = matches[match_scheme].str();
    String_compareIgnoreCase(&zipCompare, "zip", scheme.c_str());
    String_compareIgnoreCase(&arcCompare, "arc", scheme.c_str());

    if (!!zipCompare && !!arcCompare) {
        Logger_log(TELL_Warning, "Incorrect URI scheme, \"%s,\" in ZipProtocolHandler; full URI: %s", matches[match_scheme].str().c_str(), uri);
        return TE_Unsupported;
    }

    std::string archive;
    code = normalizePath(archive, matches[match_archive].str(), true);
    TE_CHECKRETURN_CODE(code);

    if (archive.empty()) {
        Logger_log(TELL_Warning, "Bad zip URI; missing archive: %s", uri);
        return TE_InvalidArg;
    }

    std::string entry;
    code = normalizePath(entry, matches[match_entry].str(), false);
    TE_CHECKRETURN_CODE(code);

    if (entry.empty()) {
        Logger_log(TELL_Warning, "Bad zip URI; missing entry: %s", uri);
        return TE_InvalidArg;
    }

    return IO_openZipEntry(ctx, archive.c_str(), entry.c_str());
}

namespace {

    TAKErr normalizePath(std::string &normalized, const std::string& source, const bool &preserve_leading_separator)
    {
        typedef std::vector<std::string> string_vec;
        std::string original(source);
        std::replace(original.begin(), original.end(), '\\', '/');
        std::istringstream is(original);
        string_vec tokens;
        std::string token;
        std::string result;
        while(std::getline(is, token, '/')) {
            if (token.empty()) {
                if (preserve_leading_separator)
                    result = "/";
                continue;
            }
            if (".." == token) {
                if (tokens.empty())
                    return TE_IllegalState;
                tokens.pop_back();
            } else if ("." != token) {
                tokens.push_back(token);
            }
        }

        for (string_vec::const_iterator itr = tokens.begin(); tokens.end() != itr; ++itr) {
            if (tokens.begin() != itr)
                result += "/";
            result += *itr;
        }

        normalized = std::move(result);

        return TE_Ok;
    }
}
