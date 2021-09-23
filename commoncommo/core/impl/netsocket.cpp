#define __STDC_LIMIT_MACROS
#include "netsocket.h"

#include <stddef.h>
#include <string.h>
#include <sys/types.h>
#include <stdint.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

// Define to try supporting ipv6
//#define COMMO_USE_IPV6 1

/***************************************************************/
// InternalHWAddress

InternalHwAddress::InternalHwAddress(
        const uint8_t* hwAddr, size_t len) : HwAddress(new uint8_t[len], len)
{
    memcpy(const_cast<uint8_t * const>(this->hwAddr), hwAddr, len);
}



InternalHwAddress::InternalHwAddress(
        const HwAddress* srcAddr) : HwAddress(new uint8_t[srcAddr->addrLen], srcAddr->addrLen)
{
    memcpy(const_cast<uint8_t * const>(this->hwAddr), srcAddr->hwAddr, addrLen);
}



InternalHwAddress::~InternalHwAddress()
{
    delete[] hwAddr;
}






/***************************************************************/
// NetAddress


namespace {
    class NetAddress4 : public NetAddress
    {
    public:
        NetAddress4(const struct sockaddr *sockaddr) : NetAddress(NA_TYPE_INET4)
        {
            const struct sockaddr_in *s = (const struct sockaddr_in *)sockaddr;
            memcpy(&addr_storage, s, sizeof(sockaddr_in));
        }
        NetAddress4(const NetAddress4 &src) : NetAddress(src)
        {
        }

        virtual NetAddress *deriveNewAddress(const uint16_t port)
        {
            struct sockaddr_storage dst = addr_storage;
            struct sockaddr_in *s = (struct sockaddr_in *)&dst;
            (*s).sin_port = htons(port);
            return new NetAddress4((struct sockaddr *)&dst);
        }

        virtual NetAddress *deriveMagtabAddress()
        {
            struct sockaddr_in newAddr;
            memcpy(&newAddr, &addr_storage, sizeof(sockaddr_in));

            if (isMulticast()) {
                // remove 100 from the first octet of the address
                char *addrBytes = (char *)(&newAddr.sin_addr.s_addr);
                addrBytes[0] -= 100;
            } // else leave as is

            return new NetAddress4((struct sockaddr *)&newAddr);
        }

        virtual uint16_t getPort() const
        {
            struct sockaddr_in *s = (struct sockaddr_in *)&addr_storage;
            return ntohs(s->sin_port);
        }

        virtual socklen_t getSockAddrLen() const
        {
            return sizeof(sockaddr_in);
        }

        virtual void getIPString(std::string *str) const
        {
            char buf[256];
            struct sockaddr_in *s = (struct sockaddr_in *)&addr_storage;
            const char *res = inet_ntop(addr_storage.ss_family, &s->sin_addr, buf, 256);
            if (!res)
                *str = "";
            else
                *str = buf;
        }

        virtual bool isMulticast() const 
        {
            struct sockaddr_in *s = (struct sockaddr_in *)&addr_storage;
            const char *addrBytes = (const char *)(&s->sin_addr.s_addr);
            return ((addrBytes[0] & 0xf0) == 0xe0);
        }

        virtual bool isSameAddress(const NetAddress *otherAddr) const
        {
            if (otherAddr->family != NA_TYPE_INET4)
                return false;

            NetAddress4 * other4 = (NetAddress4 *)otherAddr;
            struct sockaddr_in *s = (struct sockaddr_in *)&addr_storage;
            struct sockaddr_in *os = (struct sockaddr_in *)(&(other4->addr_storage));
            return memcmp(s, os, sizeof(struct sockaddr_in)) == 0;
        }
    };

#ifdef COMMO_USE_IPV6
    class NetAddress6 : public NetAddress
    {
    public:
        NetAddress6(const struct sockaddr *sockaddr) : NetAddress(NA_TYPE_INET6)
        {
            const struct sockaddr_in6 *s = (const struct sockaddr_in6 *)sockaddr;
            memcpy(&addr_storage, s, sizeof(sockaddr_in6));
        }
        NetAddress6(const NetAddress6 &src) : NetAddress(src)
        {
        }

        virtual NetAddress *deriveNewAddress(const uint16_t port)
        {
            struct sockaddr_storage dst = addr_storage;
            struct sockaddr_in6 *s = (struct sockaddr_in6 *)&dst;
            s->sin6_port = htons(port);
            return new NetAddress6((struct sockaddr *)dst);
        }

        virtual NetAddress *deriveMagtabAddress()
        {
            return new NetAddress6(this);
        }
        
        virtual uint16_t getPort() const
        {
            struct sockaddr_in6 *s = (struct sockaddr_in6 *)&addr_storage;
            return ntohs(s->sin6_port);
        }

        virtual socklen_t getSockAddrLen() const
        {
            return sizeof(sockaddr_in6);
        }

        virtual void getIPString(std::string *str) const
        {
            // tbi
            *str = "";
        }

        virtual bool isMulticast() const 
        {
            // tbi
            return false;
        }
    };
#endif
}

NetAddress::NetAddress(const NetAddressType type) :
        family(type), addr_storage()
{
}

NetAddress::NetAddress(const NetAddress &addr) :
        family(addr.family), addr_storage(addr.addr_storage)
{
}

NetAddress::~NetAddress()
{
}

bool NetAddress::isSupportedFamily(int f) {
    return f == NA_TYPE_INET4
#ifdef COMMO_USE_IPV6
            || f == NA_TYPE_INET6
#endif
            ;
}


NetAddress* NetAddress::create(
        const struct sockaddr* sockaddr) COMMO_THROW (std::invalid_argument)
{
    NetAddress *ret;

    switch (sockaddr->sa_family) {
    case AF_INET:
        ret = new NetAddress4(sockaddr);
        break;
    case AF_INET6:
#ifdef COMMO_USE_IPV6
        ret = new NetAddress6(sockaddr);
        break;
#endif
    default:
        throw std::invalid_argument("Unknown address family");
    }
    return ret;
}


NetAddress* NetAddress::create(const char* addr,
    const uint16_t port)
{
    struct in_addr binaddr4;
#ifdef COMMO_USE_IPV6
    struct in6_addr binaddr6;
#endif

    if (inet_pton(AF_INET, addr, &binaddr4) == 1) {
        struct sockaddr_in sockaddr4;
        memset(&sockaddr4, 0, sizeof(struct sockaddr_in));
        sockaddr4.sin_addr = binaddr4;
        sockaddr4.sin_family = AF_INET;
        sockaddr4.sin_port = htons(port);
        return new NetAddress4((struct sockaddr *)&sockaddr4);
#ifdef COMMO_USE_IPV6
    } else if (inet_pton(AF_INET6, addr, &binaddr6) == 1) {
        struct sockaddr_in6 sockaddr6;
        memset(&sockaddr6, 0, sizeof(struct sockaddr_in6));
        sockaddr6.sin6_family = AF_INET6;
        sockaddr6.sin6_port = htons(port);
        sockaddr6.sin6_addr = binaddr6;
        return new NetAddress6((struct sockaddr *)&sockaddr6);
#endif
    }
    return NULL;

}

NetAddress* NetAddress::create(const char* addr,
    const int port)
{
    if (port > UINT16_MAX || port < 0)
        return NULL;
    uint16_t portShort = (uint16_t)port;
    return create(addr, portShort);
}

// Create a netaddress for the wildcard local address in
// the address family given and tied to the specified port.
NetAddress *NetAddress::create(NetAddressType type, const uint16_t port)
{
    switch (type) {
    case NA_TYPE_INET4:
        struct sockaddr_in s;
        memset(&s, 0, sizeof(sockaddr_in));
        s.sin_family = AF_INET;
        s.sin_addr.s_addr = INADDR_ANY;
        s.sin_port = htons(port);
        return create((const sockaddr *)&s);
        break;
#ifdef COMMO_USE_IPV6
    case NA_TYPE_INET6:
#error implement me
#endif
    default:
        return NULL;
        break;
    }

}

NetAddress* NetAddress::create(const char* addr)
{
    return create(addr, (uint16_t)0);
}

NetAddress *NetAddress::createWildcard(NetAddressType type)
{
    switch (type) {
    case NA_TYPE_INET4:
        struct sockaddr_in s;
        memset(&s, 0, sizeof(sockaddr_in));
        s.sin_family = AF_INET;
        s.sin_addr.s_addr = INADDR_ANY;
        return create((const sockaddr *)&s);
        break;
#ifdef COMMO_USE_IPV6
    case NA_TYPE_INET6:
#error implement me
#endif
    default:
        return NULL;
        break;
    }
}


NetAddress* NetAddress::duplicateAddress(const NetAddress* addr)
{
    NetAddress *ret;

    switch (addr->family) {
    case NA_TYPE_INET4:
        ret = new NetAddress4(*((const NetAddress4 *)addr));
        break;
#ifdef COMMO_USE_IPV6
    case NA_TYPE_INET6:
        ret = new NetAddress6(*((const NetAddress6 *)addr));
        break;
#endif
    default:
        throw std::invalid_argument("Unknown address type");
    }
    return ret;
}

NetAddress *NetAddress::resolveAndCreate(const char *hostname, bool forTcp)
{
    struct addrinfo *res;
    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    if (forTcp) {
        hints.ai_socktype = SOCK_STREAM;
        hints.ai_protocol = IPPROTO_TCP;
    } else {
        hints.ai_socktype = SOCK_DGRAM;
        hints.ai_protocol = IPPROTO_UDP;
    }
    if (getaddrinfo(hostname, NULL, &hints, &res) != 0)
        return NULL;

    // Now walk the results looking for address family we support
    NetAddress *ret = NULL;
    while (res) {
        if (isSupportedFamily(res->ai_addr->sa_family)) {
            ret = NetAddress::create(res->ai_addr);
            if (ret)
                break;
        }
        res = res->ai_next;
    }

    freeaddrinfo(res);
    return ret;
}

const struct sockaddr* NetAddress::getSockAddr() const
{
    return (const struct sockaddr *)&addr_storage;
}



/***************************************************************/
// Socket base class




Socket::Socket() : fd(PlatformNet::BAD_SOCKET)
{
}

Socket::~Socket()
{
    close();
}

void Socket::close()
{
    if (fd != PlatformNet::BAD_SOCKET) {
        PlatformNet::closeSocket(fd);
        fd = PlatformNet::BAD_SOCKET;
    }
}

PlatformNet::SocketFD Socket::getFD()
{
    return fd;
}

bool Socket::isSocketErrored(netinterfaceenums::NetInterfaceErrorCode *errCode)
{
    PlatformNet::ErrorCode err = PlatformNet::socketCheckErrored(fd);
    
    if (errCode != NULL) {
        switch (err) {
        case PlatformNet::CONN_REFUSED:
            *errCode = netinterfaceenums::ERR_CONN_REFUSED;
            break;
        case PlatformNet::HOST_UNREACHABLE:
            *errCode = netinterfaceenums::ERR_CONN_HOST_UNREACHABLE;
            break;
        case PlatformNet::CONN_TIMEDOUT:
            *errCode = netinterfaceenums::ERR_CONN_TIMEOUT;
            break;
        default:
            *errCode = netinterfaceenums::ERR_OTHER;
            break;
        case PlatformNet::SUCCESS:
            break;
        }
    }
    return err != PlatformNet::SUCCESS;
}



/***************************************************************/
// UDP Socket

UdpSocket::UdpSocket(const NetAddress *localAddr, bool isBlocking, bool reuseAddr, bool mcastLooping)
    COMMO_THROW (SocketException) :
        Socket(), boundAddr(NULL), currentTTL(1),
        outboundMcastIfSet(false)
{
    struct sockaddr_storage boundSAddrStor;
    struct sockaddr *boundSAddr = (struct sockaddr *)&boundSAddrStor;
    socklen_t boundSAddrLen = sizeof(boundSAddrStor);

    const struct sockaddr *saddr = localAddr->getSockAddr();

    PlatformNet::SocketFD newfd = PlatformNet::createSocket(saddr->sa_family, 
                                                            SOCK_DGRAM, 
                                                            IPPROTO_UDP);
    if (newfd == PlatformNet::BAD_SOCKET)
        throw SocketException(netinterfaceenums::ERR_OTHER, "Failed to create socket");

    netinterfaceenums::NetInterfaceErrorCode errcode = netinterfaceenums::ERR_OTHER;
    const char *msg = "";
    if (reuseAddr && PlatformNet::socketSetAddrReuse(newfd, true) != PlatformNet::SUCCESS) {
        msg = "Failed to set address reuse option for socket";
        goto fail;
    }

    if (bind(newfd, saddr, localAddr->getSockAddrLen()) != 0) {
        msg = "Failed to bind socket";
        goto fail;
    }

    if (PlatformNet::socketSetMcastLoop(newfd, localAddr->family, mcastLooping) != PlatformNet::SUCCESS) {
        msg = mcastLooping ? 
              "mcast loop failed to set to on" : 
              "mcast loop failed to set to off";
        goto fail;
    }
    
    if (!isBlocking) {
        if (PlatformNet::setSocketNonblocking(newfd) != PlatformNet::SUCCESS) {
            msg = "Failed to set socket to nonblocking";
            goto fail;
        }
    }

    // Read back local address
    if (getsockname(newfd, boundSAddr, &boundSAddrLen) != 0) {
        msg = "Failed to get bound socket address";
        goto fail;
    }

    fd = newfd;
    
    boundAddr = NetAddress::create(boundSAddr);
    return;

fail:
    PlatformNet::closeSocket(newfd);
    throw SocketException(errcode, msg);

}

UdpSocket::~UdpSocket()
{
    delete boundAddr;
}

void UdpSocket::recvfrom(NetAddress **source,
        uint8_t *data, size_t *len) COMMO_THROW (SocketException)
{
    struct sockaddr_storage saddr;
    socklen_t saddr_len = sizeof(sockaddr_storage);

    switch (PlatformNet::socketReadFrom(fd, data, len, 
                                        (struct sockaddr *)&saddr, &saddr_len)) {
    case PlatformNet::SUCCESS:
        break;
    case PlatformNet::IO_WOULD_BLOCK:
        throw SocketWouldBlockException();
    default:
        throw SocketException();
    }

    try {
        if (source)
            *source = NetAddress::create((struct sockaddr *)&saddr);
    } catch (std::invalid_argument &) {
        // This should never happen since we're receiving on a socket
        // made against a valid address family...
        throw SocketException();
    }
}

void UdpSocket::sendto(const NetAddress* dest,
        const uint8_t* data, size_t len) COMMO_THROW (SocketException)
{
    checkAddr(dest);

    PlatformNet::ErrorCode err = 
        PlatformNet::socketWriteTo(fd, data, &len, dest->getSockAddr(), 
                                                   dest->getSockAddrLen());
    switch (err) {
    case PlatformNet::SUCCESS:
        break;
    case PlatformNet::IO_WOULD_BLOCK:
        throw SocketWouldBlockException();
    default:
        throw SocketException();
    }
}

void UdpSocket::multicastto(const NetAddress *dest, const uint8_t *data, size_t len, int ttl) COMMO_THROW (SocketException)
{
    if (!outboundMcastIfSet) {
        if (PlatformNet::socketSetMcastIf(fd, boundAddr) != PlatformNet::SUCCESS)
            throw SocketException();
        outboundMcastIfSet = true;
    }

    // Set TLL if needed
    if (ttl > 255)
        ttl = 255;
    if (ttl < 1)
        ttl = 1;
    if (ttl != currentTTL) {
        if (PlatformNet::socketSetMcastTTL(fd, boundAddr->family, (uint8_t)ttl)
                                                       != PlatformNet::SUCCESS)
            throw SocketException();
    }

    this->sendto(dest, data, len);
}

void UdpSocket::mcastMembershipChange(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress *mcastAddr, bool add)
{
    checkAddr(mcastAddr);
    checkAddr(ifaceAddr);
    if (PlatformNet::socketMcastMembership(logger, fd, add, ifaceAddr, mcastAddr) !=
                                                        PlatformNet::SUCCESS)
        throw SocketException();
}

void UdpSocket::mcastJoin(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress* mcastAddr) COMMO_THROW (SocketException)
{
    mcastMembershipChange(logger, ifaceAddr, mcastAddr, true);
}

void UdpSocket::mcastLeave(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress* mcastAddr) COMMO_THROW (SocketException)
{
    mcastMembershipChange(logger, ifaceAddr, mcastAddr, false);
}

void UdpSocket::checkAddr(const NetAddress* addr)
    COMMO_THROW (SocketException)
{
    if (addr->family != boundAddr->family)
        throw SocketException();
}

NetAddress *UdpSocket::getBoundAddr()
{
    return NetAddress::duplicateAddress(boundAddr);
}


TcpSocket::TcpSocket(PlatformNet::SocketFD fd,
                     NetAddressType type, bool isBlocking)
    COMMO_THROW (SocketException) : Socket(), isBlocking(isBlocking), addrType(type)
{
    if (!isBlocking) {
        if (PlatformNet::setSocketNonblocking(fd) != PlatformNet::SUCCESS) {
            PlatformNet::closeSocket(fd);
            throw SocketException();
        }
    }
    this->fd = fd;
}

TcpSocket::TcpSocket(NetAddressType type, bool isBlocking)
    COMMO_THROW (SocketException) : Socket(), isBlocking(isBlocking), addrType(type)
{

    PlatformNet::SocketFD newfd = PlatformNet::createSocket(type, SOCK_STREAM, IPPROTO_TCP);
    if (newfd == PlatformNet::BAD_SOCKET)
        throw SocketException(netinterfaceenums::ERR_OTHER,
                              "Unable to create socket");

    if (!isBlocking) {
        if (PlatformNet::setSocketNonblocking(newfd) != PlatformNet::SUCCESS)
            goto fail;
    }
    fd = newfd;
    return;

    fail:
        PlatformNet::closeSocket(newfd);
        throw SocketException();
}

bool TcpSocket::connect(const NetAddress *remoteEndpoint)
        COMMO_THROW (SocketException)
{
    switch (PlatformNet::socketConnect(fd, remoteEndpoint->getSockAddr(),
                    remoteEndpoint->getSockAddrLen()))
    {
    case PlatformNet::SUCCESS:
        return true;
    case PlatformNet::IO_WOULD_BLOCK:
        return false;
    case PlatformNet::CONN_REFUSED:
        throw SocketException(netinterfaceenums::ERR_CONN_REFUSED,
                              "Connection refused");
    case PlatformNet::HOST_UNREACHABLE:
        throw SocketException(netinterfaceenums::ERR_CONN_HOST_UNREACHABLE,
                              "Host is unreachable");
    case PlatformNet::CONN_TIMEDOUT:
        throw SocketException(netinterfaceenums::ERR_CONN_TIMEOUT,
                              "Connection timed out");
    default:
        throw SocketException();
    }
}

void TcpSocket::bind(const NetAddress *localAddr) COMMO_THROW (SocketException)
{
    int r = ::bind(fd, localAddr->getSockAddr(), localAddr->getSockAddrLen());

    if (r != 0)
        throw SocketException(netinterfaceenums::ERR_OTHER,
                              "Could not bind local TCP socket");
}

void TcpSocket::listen(int backlog) COMMO_THROW (SocketException)
{
    int r = ::listen(fd, backlog);

    if (r != 0)
        throw SocketException();
}

TcpSocket *TcpSocket::accept(NetAddress **addr) COMMO_THROW (SocketException)
{
    struct sockaddr_storage saddr;
    socklen_t saddr_len = sizeof(sockaddr_storage);
    PlatformNet::SocketFD clientFD;

    switch (PlatformNet::socketAccept(&clientFD, fd, 
                                      (struct sockaddr *)&saddr, &saddr_len)) {
    case PlatformNet::SUCCESS:
        break;
    case PlatformNet::IO_WOULD_BLOCK:
        return NULL;
    default:
        throw SocketException();
    }

    if (addr) {
        try {
            *addr = NetAddress::create((struct sockaddr *)&saddr);
        } catch (std::invalid_argument &) {
            // This should never happen since we're receiving on a socket
            // made against a valid address family...
            throw SocketException();
        }
    }
    
    try{
        return new TcpSocket(clientFD, addrType, isBlocking);
    } catch (SocketException &e) {
        PlatformNet::closeSocket(clientFD);
        throw e;
    }
}

size_t TcpSocket::write(const uint8_t *data, const size_t len)
        COMMO_THROW (SocketException)
{
    size_t ret = len;
    switch (PlatformNet::socketWrite(fd, data, &ret)) {
    case PlatformNet::SUCCESS:
        return ret;
    case PlatformNet::IO_WOULD_BLOCK:
        return 0;
    default:
        throw SocketException();
    }
}

size_t TcpSocket::read(uint8_t *data, const size_t len)
        COMMO_THROW (SocketException)
{
    size_t ret = len;
    switch (PlatformNet::socketRead(fd, data, &ret)) {
    case PlatformNet::SUCCESS:
        return ret;
    case PlatformNet::IO_WOULD_BLOCK:
        return 0;
    default:
        throw SocketException();
    }
}



SelectInterrupter::SelectInterrupter() COMMO_THROW (SocketException) : 
        localAddr(NULL), socket(NULL), dataBuf(), interruptCount(0), monitor()
{
    localAddr = NetAddress::create("127.0.0.1", NetAddress::WILDCARD_PORT);
    if (localAddr == NULL)
        throw SocketException(netinterfaceenums::ERR_OTHER, "Could not create local address");
    socket = new UdpSocket(localAddr, false);
    // Update to bound address
    delete localAddr;
    localAddr = socket->getBoundAddr();
}

SelectInterrupter::~SelectInterrupter()
{
    if (socket) {
        delete socket;
        socket = NULL;
    }
    if (localAddr) {
        delete localAddr;
        localAddr = NULL;
    }
}

void SelectInterrupter::trigger()
{
    thread::MonitorLockPtr mLock;
    thread::MonitorLock::create(mLock, monitor);
    try {
        socket->sendto(localAddr, dataBuf, 1);
    } catch (SocketException &) {
    }
    interruptCount++;
}

void SelectInterrupter::waitUntilUntriggered()
{
    thread::MonitorLockPtr mLock;
    thread::MonitorLock::create(mLock, monitor);
    while (interruptCount != 0)
        mLock->wait();
}

void SelectInterrupter::untrigger()
{
    thread::MonitorLockPtr mLock;
    thread::MonitorLock::create(mLock, monitor);
    if (interruptCount == 0)
        // invalid
        return;
    
    interruptCount--;
    if (interruptCount == 0)
        mLock->broadcast();
}

void SelectInterrupter::drain()
{
    try {
        size_t len;
        while (true) {
            len = 16;
            // Read until emptied (exception thrown)
            socket->recvfrom(NULL, dataBuf, &len);
        }
    } catch (SocketException &) {
    }
}

Socket *SelectInterrupter::getSocket()
{
    return socket;
}

