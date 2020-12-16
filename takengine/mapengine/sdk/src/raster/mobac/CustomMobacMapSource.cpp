#include "raster/mobac/CustomMobacMapSource.h"

#include <sstream>

#include "util/HttpClient.h"

#include "thread/Lock.h"

using namespace atakmap::raster::mobac;

using namespace atakmap::util;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    std::string replaceFirst(std::string &s, std::string oldVal, std::string newVal);
    std::string replaceAll(std::string &s, std::string oldVal, std::string newVal);
    bool replaceFirstImpl(std::string &s, std::string oldVal, std::string newVal);
}

CustomMobacMapSource::CustomMobacMapSource(const char *name, int tileSize, int minZoom, int maxZoom, const char *type, const char *u, const char **sp, size_t nsp, int bg, bool invY) :
    AbstractMobacMapSource(name, tileSize, minZoom, maxZoom, type),
    url(NULL),
    serverParts(NULL),
    numServerParts(nsp),
    invertYCoordinate(invY),
    serverPartIdx(0),
    needToCheckConnectivity(true),
    disconnected(false),
    firstDnsLookup(true),
    authFailed(false)
{
    char *str;

    str = new char[strlen(u) + 1];
    memcpy(str, u, strlen(u) + 1);
    url = str;

    if (numServerParts > 0) {
        serverParts = new const char*[numServerParts];
        for (size_t i = 0; i < numServerParts; i++) {
            str = new char[strlen(sp[i]) + 1];
            memcpy(str, sp[i], strlen(sp[i]) + 1);
            serverParts[i] = str;
        }
    }
}

CustomMobacMapSource::~CustomMobacMapSource()
{
    if (url) {
        delete[] url;
        url = NULL;
    }

    if (serverParts) {
        for (size_t i = 0; i < numServerParts; i++)
            delete[] serverParts[i];
        numServerParts = 0;
        delete[] serverParts;
    }
}

size_t CustomMobacMapSource::getUrl(char *urlOut, int zoom, int x, int y)
{
    std::string urlStr(this->url);
    if (this->serverParts) {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("CustomMobacMapSource::getURL: Failed to acquire mutex");

        replaceAll(urlStr, "{$serverpart}", this->serverParts[this->serverPartIdx]);
        this->serverPartIdx = (this->serverPartIdx + 1) % this->numServerParts;
    }

    std::stringstream numstr;

    numstr << x;
    replaceAll(urlStr, "{$x}", numstr.str());
    numstr.str(std::string());
    numstr.clear();

    numstr << y;
    replaceAll(urlStr, "{$y}", numstr.str());
    numstr.str(std::string());
    numstr.clear();

    numstr << zoom;
    replaceAll(urlStr, "{$z}", numstr.str());
    numstr.str(std::string());
    numstr.clear();

    std::auto_ptr<char> quadKey(new char[getQuadKey(NULL, zoom, x, y)+1]);
    getQuadKey(quadKey.get(), zoom, x, y);
    replaceAll(urlStr, "{$q}", std::string(quadKey.get()));

    if (urlOut)
        sprintf(urlOut, "%s", urlStr.c_str());
    return urlStr.length();
}

size_t CustomMobacMapSource::getQuadKey(char *key, int zoom, int x, int y)
{
    std::stringstream strm;

    char digit;
    int mask;
    for (int i = zoom; i > 0; i--) {
        digit = '0';
        mask = 1 << (i - 1);
        if ((x & mask) != 0)
            digit++;
        if ((y & mask) != 0)
            digit += 2;
        strm << digit;
    }
    if (key)
        sprintf(key, "%s", strm.str().c_str());
    return strm.str().length();
}

void CustomMobacMapSource::configureConnection(HttpClient *conn)
{
    conn->setUserAgent("Spyglass/3.0");
    conn->setConnectTimeout(3000);
    conn->setReadTimeout(3000);
}

void CustomMobacMapSource::clearAuthFailed()
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CustomMobacMapSource::clearAuthFailed: Failed to acquire mutex");
    this->authFailed = false;
}

void CustomMobacMapSource::checkConnectivity()
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CustomMobacMapSource::checkConnectivity: Failed to acquire mutex");
    this->needToCheckConnectivity = !this->authFailed;
}

bool CustomMobacMapSource::loadTile(MobacMapTile *tile, int zoom, int x, int y/*, Options opts*/) /*throws IOException*/
{
    if (this->invertYCoordinate)
        y = ((1 << zoom) - 1) - y;

#if 0
    // XXX - DNS check from Java/Android
    final URL url = new URL(this.getUrl(zoom, x, y));

    {
        PGSC::Lock lock(this->mutex);

        if (this->authFailed) {
            throw gcnew System::IO::IOException("Not authorized");
        } else if (this->checkConnectivity) {
            this->disconnected = true;
            final InetAddress resolved;
            if (this.dnsCheck == nullptr)
                this.dnsCheck = new AsynchronousInetAddressResolver(url.getHost());
            try {
                long dnsLookupTimeout = config.dnsLookupTimeout;
                if (this.firstDnsLookup)
                    dnsLookupTimeout = Math.max(config.dnsLookupTimeout, 10000L);
                resolved = this.dnsCheck.get(dnsLookupTimeout);
                this.disconnected = (resolved == nullptr);
            } catch (IOException e) {
                this.dnsCheck = nullptr;
                throw e;
            } finally {
                this.checkConnectivity = false;
                this.firstDnsLookup = false;
            }
            if (resolved == nullptr)
                throw new System::Net::SocketTimeoutException("Timeout occurred performing DNS lookup.");
        } else if (this.disconnected) {
            throw new NoRouteToHostException();
        }
    }
#endif

    HttpClient conn;
    this->configureConnection(&conn);

    std::auto_ptr<char> url(new char[getUrl(NULL, zoom, x, y)+1]);
    getUrl(url.get(), zoom, x, y);
    const char *urlValue = url.get();
    const bool retval = AbstractMobacMapSource::load(tile, &conn, urlValue/*, opts*/);
    if (conn.getResponseCode() == 401)
        this->authFailed = true;
    return retval;
}

namespace
{
    std::string replaceFirst(std::string &s, std::string oldVal, std::string newVal)
    {
        replaceFirstImpl(s, oldVal, newVal);
        return s;
    }

    std::string replaceAll(std::string &s, std::string oldVal, std::string newVal)
    {
        while (replaceFirstImpl(s, oldVal, newVal))
            ;
        return s;
    }

    bool replaceFirstImpl(std::string &s, std::string oldVal, std::string newVal)
    {
        size_t idx = s.find(oldVal);
        if(idx == s.npos)
            return false;
        s.replace(idx, oldVal.length(), newVal);
        return true;
    }
}
