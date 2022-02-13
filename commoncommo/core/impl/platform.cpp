#include "platform.h"
#include "internalutils.h"
#include "netsocket.h"


#ifdef WIN32
 #include <winsock2.h>
 #include <iphlpapi.h>
 // Crazy windows uses char * for get/setsockopt()
 #define SOCKOPT_CAST (const char *)
 #define G_SOCKOPT_CAST (char *)
#else
 #include <stdlib.h>
 #include <sys/time.h>
 #include <sys/types.h>
 #include <sys/socket.h>
 #include <sys/stat.h>
 #ifndef __ANDROID__
  #include <ifaddrs.h>
 #endif
 #include <string.h>
 #include <fcntl.h>
 #include <errno.h>
 #include <unistd.h>
 #ifdef __linux__
  #include <sys/ioctl.h>
  #include <netinet/in.h>
  #include <net/if.h>
  #include <net/if_arp.h>
  #include <stdio.h>
  #include <linux/if_packet.h>
 #else
  // Mac/iOS/other BSD
  #include <net/if_dl.h>
 #endif
 #define SOCKOPT_CAST
 #define G_SOCKOPT_CAST
#endif

namespace atakmap {
namespace commoncommo {
namespace impl {

namespace {
    void checkChar(const std::string &s, int offset, 
                   char checkChar, const char *errString) 
                                   COMMO_THROW (std::invalid_argument)
    {
        if (s[offset] != checkChar)
            throw std::invalid_argument(errString);
    }
    
#if defined(__ANDROID__) && !defined(__LP64__) && (defined(__ANDROID_API__) && __ANDROID_API__  < 21)
    #include <time64.h>

    // 32-bit Android pre-21 has only timegm64() and not timegm().
    // (pre-21 is a simplification - see 
    // https://github.com/android/ndk/issues/130
    // We replicate the behaviour of timegm() when the result overflows time_t.
    time_t timegm(struct tm* const t) {
      // time_t is signed on Android.
      static const time_t kTimeMax = ~(1L << (sizeof(time_t) * CHAR_BIT - 1));
      static const time_t kTimeMin = (1L << (sizeof(time_t) * CHAR_BIT - 1));
      time64_t result = timegm64(t);
      if (result < kTimeMin || result > kTimeMax)
        return -1;
      return result;
    }
#endif
    
}

void getTimeFromString(unsigned int *seconds,
                       unsigned int *millis,
                       const std::string &timeString) COMMO_THROW (std::invalid_argument)
{
    size_t n = timeString.length();
    if (n < 20)
        throw std::invalid_argument("time string too short");

    // 0->3
    std::string yrs = timeString.substr(0, 4);
    int yr = InternalUtils::intFromString(yrs.c_str(), 1900, 9999);
    // [4] -
    checkChar(timeString, 4, '-', "Time string contains invalid date separator");
    // 5 -> 6
    std::string mons = timeString.substr(5, 2);
    int mon = InternalUtils::intFromString(mons.c_str(), 1, 99);
    // [7] -
    checkChar(timeString, 7, '-', "Time string contains invalid date separator");
    // 8 -> 9
    std::string days = timeString.substr(8, 2);
    int day = InternalUtils::intFromString(days.c_str(), 0, 99);
    // [10] T
    checkChar(timeString, 10, 'T', "Time string contains invalid date/time separator");
    // 11 -> 12
    std::string hrs = timeString.substr(11, 2);
    int hr = InternalUtils::intFromString(hrs.c_str(), 0, 99);
    // [13] :
    checkChar(timeString, 13, ':', "Time string contains invalid time separator");
    // 14 ->15
    std::string mins = timeString.substr(14, 2);
    int min = InternalUtils::intFromString(mins.c_str(), 0, 99);
    // [16] :
    checkChar(timeString, 16, ':', "Time string contains invalid time separator");
    // 17 -> 18
    std::string secs = timeString.substr(17, 2);
    int sec = InternalUtils::intFromString(secs.c_str(), 0, 99);
    
    // 19 could be . or Z - check for Z at end and convert any fractional
    // seconds
    unsigned int ms = 0;
    if (timeString[19] == '.') {
        if (timeString[n - 1] != 'Z')
            throw std::invalid_argument("time string lacks Zulu marking at end");

        // We only support up to milliseconds
        size_t fracLen = n - 20 - 1;
        if (fracLen > 3)
            fracLen = 3;

        if (fracLen > 0) {
            std::string fsecs = timeString.substr(20, fracLen);
            ms = (unsigned)InternalUtils::intFromString(fsecs.c_str(), 0, 999);
            for (size_t i = fracLen; i < 3; ++i)
                ms *= 10;
        }

    } else if (timeString[19] == 'Z' && n == 20) {
        // All good - nothing to do

    } else {
        throw std::invalid_argument("time string contains illegal data at end");
    }
        

    struct tm t;
    memset(&t, 0, sizeof(struct tm));
    t.tm_sec = sec;
    t.tm_min = min;
    t.tm_hour = hr;
    t.tm_mday = day;
    t.tm_mon = mon - 1;
    t.tm_year = yr - 1900;
    // Never dst for UTC so leave as 0
    // t.tm_isdst = 0;
    
    time_t tgm;
#ifdef WIN32
    tgm = _mkgmtime(&t);
#else
    tgm = timegm(&t);
#endif

    if (tgm == (time_t)-1 || tgm < 0 || (uint64_t)tgm > (uint64_t)UINT_MAX)
        throw std::invalid_argument("time string conversion failed - cause unknown");

    *seconds = (unsigned)tgm;
    *millis = ms;
}

std::string getTimeString(unsigned int seconds, unsigned int millis)
{
    const size_t timebufsize = 256;
    char timebuf[timebufsize];
    char timebuf2[timebufsize];

    if (millis >= 1000) {
        unsigned n = millis / 1000;
        millis = millis % 1000;
        seconds += n;
    }
    time_t sec = seconds;
    struct tm t;
    memset(&t, 0, sizeof(struct tm));
#ifdef WIN32
    gmtime_s(&t, &sec);
#else
    gmtime_r(&sec, &t);
#endif
    strftime(timebuf, timebufsize, "%Y-%m-%dT%H:%M:%S", &t);
    int r = snprintf(timebuf2, timebufsize, "%s.%03dZ", timebuf, millis);
    if (r < 0 || r >= (int)timebufsize) {
        // Not enough space (unlikely, but we check to be safe)
        // Null terminate to be safe.
        timebuf2[timebufsize - 1] = '\0';
    }
    return std::string(timebuf2);

}

void getCurrentTime(unsigned int *seconds, unsigned int *millis)
{
#ifdef WIN32
    FILETIME now;
    ULARGE_INTEGER now_uli;

    GetSystemTimeAsFileTime(&now);

    now_uli.u.LowPart = now.dwLowDateTime;
    now_uli.u.HighPart = now.dwHighDateTime;

    // Subtract off the number of ticks from Jan 1, 1601 to Jan 1, 1970 so
    // that the value returned will be relative to Jan 1, 1970.
    // We do this conversion for two reasons:
    // (1) our results will be consistent between Windows and Unix.
    // (2) we can't represent seconds since Jan 1, 1601 in a 32-bit integer,
    //     so we need to truncate to something. With (1) in mind, we pick
    //     Jan 1, 1970.
    now_uli.QuadPart -= 116444736000000000LL;

    *seconds = (unsigned)(now_uli.QuadPart / 10000000);
    *millis = (unsigned)((now_uli.QuadPart / 10000) % 1000);


#else
    struct timeval tval;
    gettimeofday(&tval, NULL);
    *seconds = tval.tv_sec;
    *millis = tval.tv_usec / 1000;
#endif
}



/******************************************************************************/
// File Size


bool getFileSize(uint64_t *fsize, const char *file)
{
#ifdef WIN32
    int64_t n;
    LARGE_INTEGER large;
    HANDLE fh;
    
    int r = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, file, -1, NULL, 0);
    if (!r)
        return false;
    wchar_t *wide = new wchar_t[r];
    r = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, file, -1, wide, r);
    if (!r)
        return false;

    fh = CreateFile(wide, GENERIC_READ, 0, NULL, 
                    OPEN_EXISTING, 
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (fh == INVALID_HANDLE_VALUE)
        return false;

    if (!GetFileSizeEx(fh, &large)) {
        CloseHandle(fh);
        return false;
    }
    CloseHandle(fh);

    n = large.QuadPart;
#else
    struct stat statbuf;
    if (stat(file, &statbuf) != 0)
        return false;

    off_t n = statbuf.st_size;
#endif
    if (n < 0)
        n = 0;
    *fsize = (uint64_t)n;
    return true;
}





/******************************************************************************/
// PlatformNet

PlatformNet::SocketFD PlatformNet::createSocket(int addressFamily, int type, int protocol)
{
    PlatformNet::SocketFD newfd = ::socket(addressFamily, type, protocol);
    return newfd;
}

PlatformNet::ErrorCode PlatformNet::setSocketNonblocking(SocketFD fd)
{
#ifdef WIN32
    u_long nonblock = 1;
    if (ioctlsocket(fd, FIONBIO, &nonblock) != 0)
        return OTHER_ERROR;
#else
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags == -1 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) == -1)
        return OTHER_ERROR;
#endif

    return SUCCESS;
}

PlatformNet::ErrorCode PlatformNet::socketConnect(SocketFD fd,
                                                  const struct sockaddr *addr,
                                                  socklen_t addrlen)
{
    int r = ::connect(fd, addr, addrlen);
    if (r != 0) {
        PlatformNet::ErrorCode ret;
#ifdef WIN32
        switch (WSAGetLastError()) {
            case WSAEWOULDBLOCK:
                ret = IO_WOULD_BLOCK;
                break;
            case WSAECONNREFUSED:
                ret = CONN_REFUSED;
                break;
            case WSAENETUNREACH:
            case WSAEHOSTUNREACH:
                ret = HOST_UNREACHABLE;
                break;
            case WSAETIMEDOUT:
                ret = CONN_TIMEDOUT;
                break;
            default:
                ret = OTHER_ERROR;
                break;
        }

#else
        switch (errno) {
            case EINPROGRESS:
                ret = IO_WOULD_BLOCK;
                break;
            case ETIMEDOUT:
                ret = CONN_TIMEDOUT;
                break;
            case ENETUNREACH:
            case EHOSTUNREACH:
                ret = HOST_UNREACHABLE;
                break;
            case ECONNREFUSED:
                ret = CONN_REFUSED;
                break;
            default:
                ret = OTHER_ERROR;
                break;
        }
#endif
        return ret;
    }
    return SUCCESS;
}


void PlatformNet::closeSocket(SocketFD fd)
{
#ifdef WIN32
    ::closesocket(fd);
#else
    ::close(fd);
#endif
}


PlatformNet::ErrorCode PlatformNet::socketWrite(SocketFD fd, const uint8_t *data, size_t *len)
{
#ifdef WIN32
    if (*len > INT_MAX) {
        *len = 0;
        return OTHER_ERROR;
    }
    int lenInt = (int)*len;
    int n = ::send(fd, (const char *)data, lenInt, 0);
    if (n == SOCKET_ERROR) {
        *len = 0;
        if (WSAGetLastError() == WSAEWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    *len = n;
    return SUCCESS;
#else
    ssize_t n = ::write(fd, data, *len);
    if (n < 0) {
        *len = 0;

        if (errno == EAGAIN || errno == EWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    // Cast is safe; checked above
    *len = (size_t)n;
    return SUCCESS;
#endif
}

PlatformNet::ErrorCode PlatformNet::socketRead(SocketFD fd, uint8_t *data, size_t *len)
{
#ifdef WIN32
    if (*len > INT_MAX) {
        *len = 0;
        return OTHER_ERROR;
    }
    int lenInt = (int)*len;
    int n = ::recv(fd, (char *)data, lenInt, 0);
    if (n == SOCKET_ERROR || n == 0) {
        *len = 0;
        if (n == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    *len = n;
    return SUCCESS; 
#else
    ssize_t n = ::read(fd, data, *len);
    if (n <= 0) {
        *len = 0;

        if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    // Cast is safe; checked above
    *len = (size_t)n;
    return SUCCESS;
#endif
}

PlatformNet::ErrorCode PlatformNet::socketWriteTo(SocketFD fd, const uint8_t *data, size_t *len,
                        const struct sockaddr *addr, socklen_t slen)
{
#ifdef WIN32
    if (*len > INT_MAX) {
        *len = 0;
        return OTHER_ERROR;
    }
    int lenInt = (int)*len;
    int n = ::sendto(fd, (const char *)data, lenInt, 0, addr, slen);
    if (n == SOCKET_ERROR) {
        *len = 0;
        if (WSAGetLastError() == WSAEWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    *len = n;
    return SUCCESS;

#else
    ssize_t n = ::sendto(fd, data, *len, 0, addr, slen);
    if (n < 0) {
        *len = 0;

        if (errno == EAGAIN || errno == EWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    // Cast is safe; checked above
    *len = (size_t)n;
    return SUCCESS;
#endif
}

PlatformNet::ErrorCode PlatformNet::socketReadFrom(SocketFD fd, uint8_t *data, size_t *len,
                        struct sockaddr *addr, socklen_t *slen)
{
#ifdef WIN32
    if (*len > INT_MAX) {
        *len = 0;
        return OTHER_ERROR;
    }
    int lenInt = (int)*len;
    int n = ::recvfrom(fd, (char *)data, lenInt, 0, addr, slen);
    if (n == SOCKET_ERROR || n == 0) {
        *len = 0;
        if (n == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK)
            return IO_WOULD_BLOCK;
        else
            return OTHER_ERROR;
    }
    *len = n;
    return SUCCESS;
#else
    socklen_t slen0 = *slen;
    ssize_t n = ::recvfrom(fd, data, *len, 0, addr, slen);
    if (n < 0 || *slen > slen0) {
        *len = 0;
        if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
            return IO_WOULD_BLOCK;
        else if (*slen > slen0)
            return ADDR_BUF_TOO_SMALL;
        return OTHER_ERROR;
    }
    // Cast is safe; checked above
    *len = (size_t)n;
    return SUCCESS;
#endif
}


PlatformNet::ErrorCode PlatformNet::socketAccept(SocketFD *clientFD,
                                  SocketFD fd,
                                  struct sockaddr *addr, socklen_t *slen)
{
#ifdef WIN32
    bool retry;
    SocketFD newClientFD;
    do {
        retry = false;
        newClientFD = ::accept(fd, addr, slen);

        if (newClientFD == INVALID_SOCKET) {
            switch (WSAGetLastError()) {
            case WSAEWOULDBLOCK:
                return IO_WOULD_BLOCK;
            case WSAECONNRESET:
                retry = true;
                break;
            case WSAEFAULT:
                return ADDR_BUF_TOO_SMALL;
            default:
                return OTHER_ERROR;
            }
        } // else success
    } while (retry);

    *clientFD = newClientFD;
    return SUCCESS;
#else
    bool retry;
    int newClientFD;
    do {
        retry = false;
        socklen_t slen0 = *slen;
        newClientFD = ::accept(fd, addr, slen);

        if (newClientFD < 0) {
            // Not in switch because these are same on some platforms
            // and gcc flags duplicate cases in switch
            if (errno == EAGAIN || errno == EWOULDBLOCK)
                return IO_WOULD_BLOCK;
            
            switch (errno) {
            case ENETDOWN:
            case EPROTO:
            case ENOPROTOOPT:
            case EHOSTDOWN:
#ifdef __linux__
            case ENONET:
#endif
            case EHOSTUNREACH:
            case EOPNOTSUPP:
            case ENETUNREACH:
            case ECONNABORTED:
            case EINTR:
                retry = true;
                break;
            default:
                return OTHER_ERROR;
            }
        } else if (*slen > slen0) {
            closeSocket(newClientFD);
            return ADDR_BUF_TOO_SMALL;
        } // else success
    } while (retry);

    *clientFD = newClientFD;
    return SUCCESS;
#endif
}


PlatformNet::ErrorCode PlatformNet::socketSetAddrReuse(SocketFD fd, bool reuse)
{
#if defined(WIN32)
    DWORD reuseOptArg = reuse ? TRUE : FALSE;
#elif defined(__linux__)
    int reuseOptArg = reuse ? 1 : 0;
#else
    int reuseOptArg = 0;
    return OTHER_ERROR;
#endif

    if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, SOCKOPT_CAST &reuseOptArg, sizeof(reuseOptArg)) != 0)
        return OTHER_ERROR;
    else
        return SUCCESS;
}


PlatformNet::ErrorCode PlatformNet::socketSetMcastIf(SocketFD fd, 
                                                     const NetAddress *localAddr)
{
    switch (localAddr->family) {
    case NA_TYPE_INET4:
    {
        struct sockaddr_in *s4 = (struct sockaddr_in *)localAddr->getSockAddr();
        if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                SOCKOPT_CAST &s4->sin_addr, sizeof(s4->sin_addr)) != 0)
            return OTHER_ERROR;
        break;
    }
    default:
        return OTHER_ERROR;
    }
    return SUCCESS;
}

PlatformNet::ErrorCode PlatformNet::socketSetMcastLoop(SocketFD fd, 
                                                       NetAddressType type,
                                                       bool en)
{
    switch (type) {
    case NA_TYPE_INET4:
    {
#ifdef WIN32
        // On Windows, the system's socket-layer loopback setting
        // is for handling of inbound data only. We never
        // want to receive looped back data; this call is supposed to control
        // looping on the outbound, which Windows does not allow one to
        // control
        DWORD loop = FALSE;//en ? TRUE : FALSE;
#else
        // On other platforms we obey the passed arg as it is an indication
        // on if we want loopback behavior for *outbound* data and that is
        // what this setting does on those platforms
        u_char loop = en ? 1 : 0;
#endif
        if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_LOOP, SOCKOPT_CAST &loop, 
                                                            sizeof(loop)) != 0)
            return OTHER_ERROR;
        break;
    }
    default:
        return OTHER_ERROR;
    }
    return SUCCESS;
}


PlatformNet::ErrorCode PlatformNet::socketSetMcastTTL(SocketFD fd,
                                                      NetAddressType type,
                                                      uint8_t ttl)
{
    switch (type)
    {
    case NA_TYPE_INET4:
    {
#ifdef WIN32
        DWORD ttl_b = ttl;
#else
        u_char ttl_b = (u_char)ttl;
#endif
        if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_TTL, SOCKOPT_CAST &ttl_b,
                                                         sizeof(ttl_b)) != 0)
            return OTHER_ERROR;
        break;
    }
    default:
        return OTHER_ERROR;
    }
    return SUCCESS;
}

PlatformNet::ErrorCode PlatformNet::socketMcastMembership(CommoLogger *logger, SocketFD fd, bool join,
                                                          const NetAddress *iface,
                                                          const NetAddress *mcast)
{
    switch (iface->family)
    {
    case NA_TYPE_INET4:
    {
        struct sockaddr_in *s4 = (struct sockaddr_in *)iface->getSockAddr();
        struct sockaddr_in *m4 = (struct sockaddr_in *)mcast->getSockAddr();
        struct ip_mreq m;
        m.imr_interface = s4->sin_addr;
        m.imr_multiaddr = m4->sin_addr;
        int op = join ? IP_ADD_MEMBERSHIP : IP_DROP_MEMBERSHIP;
        if (setsockopt(fd, IPPROTO_IP, op, SOCKOPT_CAST &m, sizeof(m)) != 0) {
            int errCode;
#ifdef WIN32
            errCode = WSAGetLastError();
#else
            errCode = errno;
#endif

            std::string mcStr;
            std::string ifStr;
            mcast->getIPString(&mcStr);
            iface->getIPString(&ifStr);
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "mcast membership %s for %s on %s failed - err code %d", join ? "join" : "drop", mcStr.c_str(), ifStr.c_str(), errCode);
            return OTHER_ERROR;
        }
        break;
    }
    default:
        return OTHER_ERROR;
    }
    return SUCCESS;
}

PlatformNet::ErrorCode PlatformNet::socketCheckErrored(SocketFD fd)
{
#ifdef WIN32
    DWORD err = 0;
#else
    int err = 0;
#endif
    socklen_t len = sizeof(err);
    int rc = getsockopt(fd, SOL_SOCKET, SO_ERROR, G_SOCKOPT_CAST &err, &len);

    if (rc)
        return OTHER_ERROR;
    
    PlatformNet::ErrorCode ret;
#ifdef WIN32
    switch (err) {
        case WSAECONNREFUSED:
            ret = CONN_REFUSED;
            break;
        case WSAENETUNREACH:
        case WSAEHOSTUNREACH:
            ret = HOST_UNREACHABLE;
            break;
        case WSAETIMEDOUT:
            ret = CONN_TIMEDOUT;
            break;
        case 0:
            ret = SUCCESS;
            break;
        default:
            ret = OTHER_ERROR;
            break;
    }

#else
    switch (err) {
        case ETIMEDOUT:
            ret = CONN_TIMEDOUT;
            break;
        case ENETUNREACH:
        case EHOSTUNREACH:
            ret = HOST_UNREACHABLE;
            break;
        case ECONNREFUSED:
            ret = CONN_REFUSED;
            break;
        case 0:
            ret = SUCCESS;
            break;
        default:
            ret = OTHER_ERROR;
            break;
    }

#endif

    return ret;
}



 #if defined(__linux__)
namespace {
    // Interfaces which we only allow to be added by name
    // because they typically don't have real hw addresses
    struct FakeIfaceName {
        const char *ifaceName;
        const uint8_t *fakeAddr;
        const size_t fakeAddrLen;
    };
    const uint8_t tun0Fake[] = 
        { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 't', 'u', 'n', '0' };
    const uint8_t tun1Fake[] =
        { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 't', 'u', 'n', '1' };
#define PPP_FAKE(name, cn)                                            \
    const uint8_t name[] =                                            \
        { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 'p', 'p', 'p', cn };
    PPP_FAKE(ppp0Fake, '0')
    PPP_FAKE(ppp1Fake, '1')
    PPP_FAKE(ppp2Fake, '2')
    PPP_FAKE(ppp3Fake, '3')
    PPP_FAKE(ppp4Fake, '4')
    PPP_FAKE(ppp5Fake, '5')
    PPP_FAKE(ppp6Fake, '6')
    PPP_FAKE(ppp7Fake, '7')
    PPP_FAKE(ppp8Fake, '8')
    PPP_FAKE(ppp9Fake, '9')
#undef PPP_FAKE

#define MK_FAKE(name, fake)                                 \
        {                                                   \
            name,                                           \
            fake,                                           \
            sizeof(fake)                                    \
        },

    const size_t nFakeIfaces = 12;
    const FakeIfaceName fakeIfaces[nFakeIfaces] = {
        MK_FAKE("tun0", tun0Fake)
        MK_FAKE("tun1", tun1Fake)
        MK_FAKE("ppp0", ppp0Fake)
        MK_FAKE("ppp1", ppp1Fake)
        MK_FAKE("ppp2", ppp2Fake)
        MK_FAKE("ppp3", ppp3Fake)
        MK_FAKE("ppp4", ppp4Fake)
        MK_FAKE("ppp5", ppp5Fake)
        MK_FAKE("ppp6", ppp6Fake)
        MK_FAKE("ppp7", ppp7Fake)
        MK_FAKE("ppp8", ppp8Fake)
        MK_FAKE("ppp9", ppp9Fake)
    };
#undef MK_FAKE

}
#endif




#ifdef __ANDROID__

#include <linux/netlink.h>
#include <linux/rtnetlink.h>

namespace {

/*
 * Basic basic basic Implmentation of get/freeifaddrs() using
 * Linux Netlink as android lacks these functions
 * These emulate the Linux version of these functions, NOT
 * the BSD version!  (while similar the values returned in
 * ifaddrs are quite different)
 */

struct ifaddrs {
    struct ifaddrs *ifa_next;
    char ifa_name[IF_NAMESIZE];
    struct sockaddr *ifa_addr;
};


struct nlAddressRequest {
    nlmsghdr nlHeader;
    ifaddrmsg nlMessage;
};


struct nlLinkRequest {
    nlmsghdr nlHeader;
    ifinfomsg nlMessage;
};


int getifaddrsIP(int fd, struct ifaddrs **ifap)
{
    struct ifaddrs *ret = NULL;

    // build a request for addresses of all interfaces
    // across all address families
    nlAddressRequest aReq;
    memset(&aReq, 0, sizeof(nlAddressRequest));
    aReq.nlMessage.ifa_family = AF_UNSPEC;
    aReq.nlHeader.nlmsg_type = RTM_GETADDR;
    aReq.nlHeader.nlmsg_len = NLMSG_ALIGN(NLMSG_LENGTH(sizeof(ifaddrmsg)));
    aReq.nlHeader.nlmsg_flags = NLM_F_REQUEST | NLM_F_MATCH;

    // Send the request
    ssize_t sendRet = send(fd, &aReq, aReq.nlHeader.nlmsg_len, 0);
    if (sendRet < 0 || (size_t)sendRet != aReq.nlHeader.nlmsg_len) {
        *ifap = NULL;
        return -1;
    }
    
    // Read back the reply
    static const int max_bufsize = 65536;
    char buf[max_bufsize];
    ssize_t n;
    bool error = true;
    bool stop = false;
    while (!stop && (n = recv(fd, buf, max_bufsize, 0)) > 0 && (size_t)n > sizeof(nlmsghdr)) {
        nlmsghdr *replyhdr = (nlmsghdr *)buf;
        while (NLMSG_OK(replyhdr, n)) {
            if (replyhdr->nlmsg_type == NLMSG_DONE) {
                error = false;
                stop = true;
                break;
            } else if (replyhdr->nlmsg_type == RTM_NEWADDR) {
                ifaddrmsg *addr = (ifaddrmsg *)NLMSG_DATA(replyhdr);
                rtattr *rta = IFA_RTA(addr);
                ssize_t ifaLen = IFA_PAYLOAD(replyhdr);
                while (RTA_OK(rta, ifaLen)) {
                    if (rta->rta_type == IFA_LOCAL && addr->ifa_family == AF_INET) {
                        struct ifaddrs *newAddr = new ifaddrs;
                        newAddr->ifa_next = ret;
                        if (if_indextoname(addr->ifa_index, newAddr->ifa_name) != NULL) {
                            ret = newAddr;
                            sockaddr_storage *sa_store = new sockaddr_storage;
                            memset(sa_store, 0, sizeof(sockaddr_storage));
                            sockaddr_in *sa_in = (sockaddr_in *)sa_store;
                            sa_in->sin_family = AF_INET;
                            memcpy(&sa_in->sin_addr, RTA_DATA(rta), RTA_PAYLOAD(rta));
                            newAddr->ifa_addr = (struct sockaddr *)sa_in;
                        } else {
                            delete newAddr;
                        }
                    }
                    rta = RTA_NEXT(rta, ifaLen);
                }
            } else if (replyhdr->nlmsg_type == NLMSG_ERROR) {
                stop = true;
                break;
            }
            
            replyhdr = NLMSG_NEXT(replyhdr, n);
        }
    }
    
    if (error) {
        while (ret) {
            struct ifaddrs *next = ret->ifa_next;
            sockaddr_storage *s = (sockaddr_storage *)ret->ifa_addr;
            delete s;
            delete ret;
            ret = next;
        }
        *ifap = NULL;
        return -1;
    } else {
        *ifap = ret;
        return 0;
    }
}

// Appends to ifap. On error, ifap will have original content
int getifaddrsPKT(int fd, struct ifaddrs **ifap)
{
    struct ifaddrs *ret = NULL;

    // build a request for link info
    nlLinkRequest lReq;
    memset(&lReq, 0, sizeof(nlLinkRequest));
    lReq.nlMessage.ifi_family = AF_UNSPEC;
    lReq.nlHeader.nlmsg_type = RTM_GETLINK;
    lReq.nlHeader.nlmsg_len = NLMSG_ALIGN(NLMSG_LENGTH(sizeof(ifinfomsg)));
    lReq.nlHeader.nlmsg_flags = NLM_F_REQUEST | NLM_F_MATCH;

    // Send the request
    ssize_t sendRet = send(fd, &lReq, lReq.nlHeader.nlmsg_len, 0);
    if (sendRet < 0 || (size_t)sendRet != lReq.nlHeader.nlmsg_len) {
        return -1;
    }
    
    // Read back the reply
    static const int max_bufsize = 65536;
    char buf[max_bufsize];
    ssize_t n;
    bool error = true;
    bool stop = false;
    while (!stop && (n = recv(fd, buf, max_bufsize, 0)) > 0 && (size_t)n > sizeof(nlmsghdr)) {
        nlmsghdr *replyhdr = (nlmsghdr *)buf;
        while (NLMSG_OK(replyhdr, n)) {
            if (replyhdr->nlmsg_type == NLMSG_DONE) {
                error = false;
                stop = true;
                break;
            } else if (replyhdr->nlmsg_type == RTM_NEWLINK) {
                ifinfomsg *linkinfo = (ifinfomsg *)NLMSG_DATA(replyhdr);
                rtattr *rta = IFLA_RTA(linkinfo);
                ssize_t ifaLen = IFLA_PAYLOAD(replyhdr);
                while (RTA_OK(rta, ifaLen)) {
                    if (rta->rta_type == IFLA_ADDRESS) {
                        struct ifaddrs *newAddr = new ifaddrs;
                        newAddr->ifa_next = ret;
                        unsigned int payloadLen = RTA_PAYLOAD(rta);
                        sockaddr_ll sa_ll;

                        if (payloadLen <= sizeof(sa_ll.sll_addr) && 
                                if_indextoname(linkinfo->ifi_index,
                                               newAddr->ifa_name) != NULL) {
                            sa_ll.sll_family = AF_PACKET;
                            sa_ll.sll_halen = payloadLen;
                            memcpy(sa_ll.sll_addr, RTA_DATA(rta), payloadLen);
                            sockaddr_storage *nsa_ss = new sockaddr_storage;
                            memset(nsa_ss, 0, sizeof(sockaddr_storage));
                            sockaddr_ll *nsa_ll = (sockaddr_ll *)nsa_ss;

                            *nsa_ll = sa_ll;
                            newAddr->ifa_addr = (struct sockaddr *)nsa_ll;
                            ret = newAddr;
                        } else {
                            delete newAddr;
                        }
                    }
                    rta = RTA_NEXT(rta, ifaLen);
                }
            } else if (replyhdr->nlmsg_type == NLMSG_ERROR) {
                stop = true;
                break;
            }
            
            replyhdr = NLMSG_NEXT(replyhdr, n);
        }
    }
    
    if (error) {
        while (ret) {
            struct ifaddrs *next = ret->ifa_next;
            sockaddr_storage *s = (sockaddr_storage *)ret->ifa_addr;
            delete s;
            delete ret;
            ret = next;
        }
        return -1;
    } else {
        // Append our list to the original list, if there was one.
        struct ifaddrs *oif = *ifap;
        while (oif && oif->ifa_next) {
            oif = oif->ifa_next;
        }
        if (!oif)
            *ifap = ret;
        else
            oif->ifa_next = ret;
        return 0;
    }
}


int getifaddrs(struct ifaddrs **ifap)
{
    int fd = socket(PF_NETLINK, SOCK_DGRAM, NETLINK_ROUTE);
    if (fd < 0) {
        *ifap = NULL;
        return -1;
    }
    
    int ret = getifaddrsIP(fd, ifap);
    if (ret == 0) {
        getifaddrsPKT(fd, ifap);
    }
    
    ::close(fd);
    return ret;
}



void freeifaddrs(struct ifaddrs *ifa)
{
    while (ifa) {
        struct ifaddrs *next = ifa->ifa_next;
        sockaddr_storage *sa = (sockaddr_storage *)ifa->ifa_addr;
        delete sa;
        delete ifa;
        ifa = next;
    }
}

} // end empty namespace

#endif





struct InterfaceEnumerator::InterfaceEnumeratorContext
{
#ifdef WIN32
    IP_ADAPTER_ADDRESSES *win32AddrList;
    ULONG win32AddrListBufSize;
#else
    struct ifaddrs *addrList;
#endif

    std::map<const HwAddress *, const NetAddress *, HwComp> addrMap;
#ifdef __linux__
    int ioctlSock;
#endif


    InterfaceEnumeratorContext() : 
#ifdef WIN32
        win32AddrList(NULL),
        win32AddrListBufSize(15*1024),  // 15k good start value per MSDN
#else
        addrList(NULL), 
#endif
        addrMap() 
#ifdef __linux
        , ioctlSock(-1) 
#endif
    {};
};



#ifdef WIN32
namespace {
    const size_t WIN32_ADDR_LIST_MAX_SIZE = 1024 * 64;
}
#endif

InterfaceEnumerator::InterfaceEnumerator(CommoLogger* logger,
                netinterfaceenums::NetInterfaceAddressMode addrMode) COMMO_THROW (SocketException) :
        logger(logger), addrMode(addrMode), context(new InterfaceEnumeratorContext())
{
#ifdef __linux__
    context->ioctlSock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    if (context->ioctlSock == -1)
        throw SocketException();
#endif
}

InterfaceEnumerator::~InterfaceEnumerator()
{
    resetContext();
#ifdef WIN32
    if (context->win32AddrList)
        delete[] (uint8_t *)context->win32AddrList;
#endif
#ifdef __linux__
    if (context->ioctlSock != -1)
        close(context->ioctlSock);
#endif
    delete context;
}

void InterfaceEnumerator::rescanInterfaces()
{
    resetContext();

#define IFACE_ENUM_HWADDR
#if defined(WIN32)
    do {
        // Allocate space
        if (!context->win32AddrList) {
            context->win32AddrList = (IP_ADAPTER_ADDRESSES *)
                                        new uint8_t[context->win32AddrListBufSize];
        }
        switch (GetAdaptersAddresses(AF_INET, GAA_FLAG_SKIP_MULTICAST |
                    GAA_FLAG_SKIP_DNS_SERVER |
                    (addrMode == netinterfaceenums::MODE_NAME ? 0 : GAA_FLAG_SKIP_FRIENDLY_NAME),
                    NULL, context->win32AddrList, 
                    &context->win32AddrListBufSize)) {
            case ERROR_NO_DATA:
                // Simply no interfaces. That's fine! 
                // Just give up since nothing to do
                return;
            case ERROR_SUCCESS:
                break;
            case ERROR_BUFFER_OVERFLOW:
                // Not enough space. Clean out buffer. context->size now has desired
                // size
                delete[] (uint8_t *)context->win32AddrList;
                context->win32AddrList = NULL;
                break;
            default:
                // Some sort of error
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to get system hardware interfaces list.");
                return;
        }
    } while (!context->win32AddrList && context->win32AddrListBufSize < WIN32_ADDR_LIST_MAX_SIZE);
    // Either we've exceeded max size or we have an address list...

    if (!context->win32AddrList) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to get system hardware interfaces list - max buffer size exceeded! %lu", context->win32AddrListBufSize);
        // Reset back to default value for next invocation
        context->win32AddrListBufSize = 15 * 1024;
        // give up
        return;
    }

    // Look through the list for addresses we care about
    for (IP_ADAPTER_ADDRESSES *curAddr = context->win32AddrList; curAddr; 
                                                        curAddr = curAddr->Next) {
        if (curAddr->OperStatus != IfOperStatusUp || 
                  (addrMode == netinterfaceenums::MODE_PHYS_ADDR && !curAddr->PhysicalAddressLength) ||
                  (addrMode == netinterfaceenums::MODE_NAME && !curAddr->AdapterName) ||
                  !curAddr->FirstUnicastAddress)
            continue;

        try {
            // do netaddress first since it can throw!
            NetAddress *addr = NetAddress::create(curAddr->FirstUnicastAddress->Address.lpSockaddr);
            HwAddress *physAddr;
            if (addrMode == netinterfaceenums::MODE_PHYS_ADDR)
                physAddr = new HwAddress(curAddr->PhysicalAddress, curAddr->PhysicalAddressLength);
            else
                physAddr = new HwAddress((const uint8_t *)curAddr->AdapterName, strlen(curAddr->AdapterName));

            context->addrMap[physAddr] = addr;
        } catch (std::invalid_argument &) {
        }
    }


#else 
    if (getifaddrs(&context->addrList) != 0) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to get system hardware interfaces list.");
        return;
    }

    std::map<std::string, const HwAddress *> ifToHw;
    std::map<std::string, const HwAddress *>::iterator iter;

 #if defined(__linux__)
  #undef IFACE_ENUM_HWADDR
  #define IFACE_ENUM_HWADDR (InternalHwAddress *)
    if (addrMode == netinterfaceenums::MODE_PHYS_ADDR) {
        for (struct ifaddrs *interfaces = context->addrList; interfaces != NULL; interfaces = interfaces->ifa_next) {
            if (!interfaces->ifa_addr || interfaces->ifa_addr->sa_family != AF_PACKET)
                continue;
            
            struct sockaddr_ll* sdl = (struct sockaddr_ll *)interfaces->ifa_addr;
            std::string ifname(interfaces->ifa_name);
            if (ifToHw.find(ifname) != ifToHw.end()) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Multiple link-level interfaces with same name!");
            } else {
                HwAddress *hwAddr = new InternalHwAddress((uint8_t *)sdl->sll_addr, sdl->sll_halen);
                ifToHw[ifname] = hwAddr;
            }
        }

        // Replace any *real* hwaddress for ifaces with the names in our
        // fake address space and insert those fake mappings instead
        for (size_t i = 0; i < nFakeIfaces; ++i) {
            std::string iname_str(fakeIfaces[i].ifaceName);
            iter = ifToHw.find(iname_str);
            if (iter != ifToHw.end()) {
                delete IFACE_ENUM_HWADDR iter->second;
                ifToHw.erase(iter);
            }
            ifToHw[iname_str] = new InternalHwAddress(fakeIfaces[i].fakeAddr, fakeIfaces[i].fakeAddrLen);
        }
    }


    for (struct ifaddrs *interfaces = context->addrList; interfaces != NULL; interfaces = interfaces->ifa_next) {
        struct ifreq ifr;

        if (!interfaces->ifa_addr)
            continue;

        if (!NetAddress::isSupportedFamily(interfaces->ifa_addr->sa_family)) {
            if (interfaces->ifa_addr->sa_family != AF_PACKET)
                // Don't print this for packet level ifaces; we handled those
                // above
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "%s: Unsupported address family for interface IP address (%hd)", interfaces->ifa_name, interfaces->ifa_addr->sa_family);
            continue;
        }

        const HwAddress *hwAddr = NULL;

        if (addrMode == netinterfaceenums::MODE_PHYS_ADDR) {
            std::string iname_str(interfaces->ifa_name);
            iter = ifToHw.find(iname_str);
            if (iter != ifToHw.end()) {
                hwAddr = iter->second;
                ifToHw.erase(iter);
            }

            if (!hwAddr) {
                strncpy(ifr.ifr_name, interfaces->ifa_name, IFNAMSIZ - 1);
                ifr.ifr_name[IFNAMSIZ - 1] = '\0';
                if (ioctl(context->ioctlSock, SIOCGIFHWADDR, &ifr) == 0) {
                    if (ifr.ifr_hwaddr.sa_family == ARPHRD_ETHER) {
                        hwAddr = new InternalHwAddress((uint8_t *)ifr.ifr_hwaddr.sa_data, 6);
                    } else {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "%s: Unknown hardware address type (%hd)", ifr.ifr_name, ifr.ifr_hwaddr.sa_family);
                    }
                } else {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "%s: ioctl call has failed %d", ifr.ifr_name, errno);
                }
            }

            if (!hwAddr) {
                std::string fn = "/sys/class/net/";
                fn += interfaces->ifa_name;
                fn += "/address";
                FILE *f = fopen(fn.c_str(), "rb");
                if (f) {
                    static const size_t maxaddrsize = 1024;
                    uint8_t addrbuf[maxaddrsize];
                    char filebuf[maxaddrsize];
                    size_t n = fread(filebuf, 1, maxaddrsize - 1, f);
                    filebuf[n] = '\0';
                    fclose(f);
                    
                    size_t i = 0;
                    size_t addrlen = 0;
                    while ((n - i) >= sizeof(short)) {
                        short shrt;
                        sscanf(filebuf + i, "%2hx", &shrt);
                        addrbuf[addrlen++] = (uint8_t)shrt;
                        i += 2;
                        if (n == i || filebuf[i] == '\n')
                            break;
                        if (filebuf[i] != ':') {
                            addrlen = 0;
                            break;
                        }
                        i++;
                    }
                    if (addrlen)
                        hwAddr = new InternalHwAddress(addrbuf, addrlen);
                } else {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Failed to open sysfs iface file (%s) to get hwaddr", fn.c_str());
                }
            }
            
            if (!hwAddr) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Failed to get an interface (%s) hardware address", interfaces->ifa_name);
                continue;
            }
        } else {
            hwAddr = new InternalHwAddress(
                                   (const uint8_t *)interfaces->ifa_name,
                                   strlen(interfaces->ifa_name));
        }


        NetAddress *ipAddr = NetAddress::create(interfaces->ifa_addr);
        context->addrMap[hwAddr] = ipAddr;
    }

 #else
    if (addrMode == netinterfaceenums::MODE_PHYS_ADDR) {
        for (struct ifaddrs *interfaces = context->addrList; interfaces != NULL; interfaces = interfaces->ifa_next) {
            if (!interfaces->ifa_addr || interfaces->ifa_addr->sa_family != AF_LINK)
                continue;

            struct sockaddr_dl* sdl = (struct sockaddr_dl *)interfaces->ifa_addr;
            if (sdl->sdl_alen != 6)
                continue;
            std::string ifname(interfaces->ifa_name);
            if (ifToHw.find(ifname) != ifToHw.end()) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Multiple link-level interfaces with same name (%s)!", ifname.c_str());
            } else {
  #if 0
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Interface %s hwaddress %x%x%x%x%x%x", 
                             ifname.c_str(), (int)*((uint8_t *)LLADDR(sdl)),
                             (int)*((uint8_t *)LLADDR(sdl)+1),
                             (int)*((uint8_t *)LLADDR(sdl)+2),
                             (int)*((uint8_t *)LLADDR(sdl)+3),
                             (int)*((uint8_t *)LLADDR(sdl)+4),
                             (int)*((uint8_t *)LLADDR(sdl)+5));
  #endif
                HwAddress *hwAddr = new HwAddress((uint8_t *)LLADDR(sdl), 6);
                ifToHw[ifname] = hwAddr;
            }
        }
    }

    for (struct ifaddrs *interfaces = context->addrList; interfaces != NULL; interfaces = interfaces->ifa_next) {
        if (!interfaces->ifa_addr || interfaces->ifa_addr->sa_family == AF_LINK)
            continue;

        if (!NetAddress::isSupportedFamily(interfaces->ifa_addr->sa_family)) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Unsupported address family (%d) for interface IP address on iface %s", interfaces->ifa_addr->sa_family, interfaces->ifa_name);
            continue;
        }

        // See if we care about this interface
        std::string ifname(interfaces->ifa_name);
        const HwAddress *physAddr = NULL;
        if (addrMode == netinterfaceenums::MODE_PHYS_ADDR) {
            iter = ifToHw.find(ifname);
            if (iter != ifToHw.end()) {
                physAddr = iter->second;
                ifToHw.erase(iter);
            }
        } else {
            physAddr = new HwAddress((const uint8_t *)interfaces->ifa_name,
                                     strlen(interfaces->ifa_name));
        }
        if (physAddr) {
            NetAddress *ipAddr = NetAddress::create(interfaces->ifa_addr);
            context->addrMap[physAddr] = ipAddr;
        }
    }

 #endif
    // Reclaim HwAddresses for any interface not found
    for (iter = ifToHw.begin(); iter != ifToHw.end(); ++iter)
        delete IFACE_ENUM_HWADDR iter->second;


#endif

}

void InterfaceEnumerator::resetContext()
{
#ifndef WIN32
    if (context->addrList) {
        freeifaddrs(context->addrList);
        context->addrList = NULL;
    }
#endif

    std::map<const HwAddress *, const NetAddress *, HwComp>::iterator iter;
    for (iter = context->addrMap.begin(); iter != context->addrMap.end(); ++iter) {
        delete IFACE_ENUM_HWADDR iter->first;
        delete iter->second;
    }
    context->addrMap.clear();
}

const std::map<const HwAddress*, const NetAddress*, HwComp>* InterfaceEnumerator::getInterfaces()
{
    return &context->addrMap;
}




NetSelector::NetSelector() : rSetPtr(NULL), wSetPtr(NULL), exSetPtr(NULL), maxfd(PlatformNet::BAD_SOCKET)
{
    FD_ZERO(&baseReadSet);
    FD_ZERO(&baseWriteSet);
    FD_ZERO(&rSet);
    FD_ZERO(&wSet);
#ifdef WIN32
    FD_ZERO(&baseExSet);
    FD_ZERO(&exSet);
#endif
}

NetSelector::~NetSelector()
{
}

void NetSelector::buildSet(fd_set *set, fd_set **sptr, const std::vector<Socket *> *sockets)
{
    FD_ZERO(set);

    if (!growSet(set, sptr, sockets))
        *sptr = NULL;
}

bool NetSelector::growSet(fd_set *set, fd_set **sptr, const std::vector<Socket *> *sockets)
{
    if (sockets != NULL && sockets->size() > 0) {
        std::vector<Socket *>::const_iterator iter;
        for (iter = sockets->begin(); iter != sockets->end(); ++iter) {
            Socket *s = *iter;
            PlatformNet::SocketFD fd = s->getFD();
            FD_SET(fd, set);
            if (maxfd == PlatformNet::BAD_SOCKET || fd > maxfd)
                maxfd = fd;
        }
        *sptr = set;
        return true;
    }
    return false;
}

void NetSelector::setSockets(
    const std::vector<Socket *> *readSockets,
    const std::vector<Socket *> *writeSockets)
{
    setSockets(readSockets, writeSockets, NULL);
}

void NetSelector::setSockets(
    const std::vector<Socket *> *readSockets,
    const std::vector<Socket *> *writeSockets,
    const std::vector<Socket *> *connectingSockets)
{
    maxfd = PlatformNet::BAD_SOCKET;
    buildSet(&wSet, &wSetPtr, writeSockets);
    buildSet(&rSet, &rSetPtr, readSockets);
    growSet(&wSet, &wSetPtr, connectingSockets);
    baseWriteSet = wSet;
    baseReadSet = rSet;
#ifdef WIN32
    buildSet(&exSet, &exSetPtr, connectingSockets);
    baseExSet = exSet;
#endif
    if (maxfd != PlatformNet::BAD_SOCKET)
        maxfd++;
}

bool NetSelector::doSelect(long timeoutMillis) COMMO_THROW (SocketException, std::invalid_argument)
{
    struct timeval tv;
    struct timeval *tvptr = &tv;
    if (timeoutMillis == NO_TIMEOUT) {
        if (maxfd == PlatformNet::BAD_SOCKET)
            throw std::invalid_argument("Cannot specify no FDs and no timeout for select!");
        tvptr = NULL;
    } else {
        tv.tv_sec = timeoutMillis / 1000;
        tv.tv_usec = (timeoutMillis % 1000) * 1000;
    }

    rSet = baseReadSet;
    wSet = baseWriteSet;

#ifdef WIN32
#define NFD_CAST (int)
    exSet = baseExSet;

    // Winsock select with no sets causes error return immediately instead of 
    // sleeping as one might expect...
    if (maxfd == PlatformNet::BAD_SOCKET) {
        Sleep(timeoutMillis);
        return false;
    }
#else
#define NFD_CAST
    if (maxfd == PlatformNet::BAD_SOCKET)
        maxfd = 0;
#endif

    int r = select(NFD_CAST maxfd, rSetPtr, wSetPtr, exSetPtr, tvptr);
    if (r == -1)
        throw SocketException();

    return r != 0;
}

NetSelector::SelectState NetSelector::getLastState(Socket *s)
{
    PlatformNet::SocketFD fd = s->getFD();
    SelectState ret = NO_IO;
    if (FD_ISSET(fd, &rSet))
        ret = READABLE;
    else if (FD_ISSET(fd, &wSet))
        ret = WRITABLE;
#ifdef WIN32
    else if (FD_ISSET(fd, &exSet))
        ret = WRITABLE;
#endif

    return ret;
}

NetSelector::SelectState NetSelector::getLastReadState(Socket *s)
{
    PlatformNet::SocketFD fd = s->getFD();
    SelectState ret = NO_IO;
    if (FD_ISSET(fd, &rSet))
        ret = READABLE;

    return ret;
}

NetSelector::SelectState NetSelector::getLastWriteState(Socket *s)
{
    PlatformNet::SocketFD fd = s->getFD();
    SelectState ret = NO_IO;
    if (FD_ISSET(fd, &wSet))
        ret = WRITABLE;

    return ret;
}

NetSelector::SelectState NetSelector::getLastConnectState(Socket *s)
{
    PlatformNet::SocketFD fd = s->getFD();
    SelectState ret = NO_IO;
#ifdef WIN32
    if (FD_ISSET(fd, &wSet) || FD_ISSET(fd, &exSet))
#else
    if (FD_ISSET(fd, &wSet))
#endif
        ret = WRITABLE;

    return ret;
}




}
}
}
