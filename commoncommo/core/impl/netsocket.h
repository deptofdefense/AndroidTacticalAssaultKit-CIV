#ifndef IMPL_NETSOCKET_H_
#define IMPL_NETSOCKET_H_

#include "commoutils.h"
#include "internalutils.h"
#include "netinterface.h"
#include "platform.h"
#include "commothread.h"

#include <string>
#include <stdexcept>


namespace atakmap {
namespace commoncommo {
namespace impl
{

const size_t maxUDPMessageSize = 65536;


struct InternalHwAddress : public HwAddress
{
    InternalHwAddress(const uint8_t *hwAddr, size_t len);
    InternalHwAddress(const HwAddress *srcAddr);
    ~InternalHwAddress();
private:
    COMMO_DISALLOW_COPY(InternalHwAddress);

};


// Encapsulates address and address family
// Send into a socket factory to make sockets
class NetAddress
{
public:
    // A port of WILDCARD_PORT indicates
    // that this NetAddress does not specific any particular
    // port number
    static const uint16_t WILDCARD_PORT = 0;

    const NetAddressType family;

    // Test if the implementation supports the given address family.
    // Intended to test system-specific AF_XXX values.
    static bool isSupportedFamily(int f);

    virtual ~NetAddress();

    const struct sockaddr *getSockAddr() const;
    virtual socklen_t getSockAddrLen() const = 0;
    virtual uint16_t getPort() const = 0;
    virtual void getIPString(std::string *str) const = 0;
    virtual bool isMulticast() const = 0;
    virtual bool isSameAddress(const NetAddress *otherAddr) const = 0;


    // Returned value is alloc'd with new
    virtual NetAddress *deriveNewAddress(const uint16_t port) = 0;

    // Returned value is alloc'd with new
    virtual NetAddress *deriveMagtabAddress() = 0;

    // Throws if sockaddr has unknown address family
    static NetAddress *create(const struct sockaddr *sockaddr) COMMO_THROW (std::invalid_argument);

    // Returns NULL if cannot parse addr
    // addr MUST be a valid network address in string
    // form for one of the supported families.
    // Hostnames are not supported (see resolveAndCreate())
    static NetAddress *create(const char *addr, const uint16_t port);

    // Returns NULL if cannot parse addr
    // addr MUST be a valid network address in string
    // form for one of the supported families.
    // Hostnames are not supported (see resolveAndCreate())
    static NetAddress *create(const char *addr, const int port);

    // Create a netaddress for the wildcard local address in
    // the address family given and tied to the specified port.
    static NetAddress *create(NetAddressType type, const uint16_t port);

    // Returns NULL if cannot parse addr
    // addr MUST be a valid network address in string
    // form for one of the supported families.
    // Hostnames are not supported (see resolveAndCreate())
    static NetAddress *create(const char *addr);

    // Attempt to look up the provided host name string and
    // return a NetAddress suitable for use with the given socket
    // type. The forTcp argument should be specified true if the netaddress
    // will later be used for TCP communication, false otherwise.
    // The returned NetAddress is allocated with new.
    // Returns NULL if the hostname cannot be resolved to a valid NetAddress.
    static NetAddress *resolveAndCreate(const char *hostname, bool forTcp);

    // Create a net address of the specified family representing the wildcard
    // ip address and port
    static NetAddress *createWildcard(NetAddressType type);

    // Returned value is alloc'd with new
    static NetAddress *duplicateAddress(const NetAddress *addr);

protected:
    NetAddress(const NetAddressType type);
    COMMO_DISALLOW_COPY(NetAddress);

    struct sockaddr_storage addr_storage;

};



class SocketWouldBlockException : public SocketException
{
public:
    SocketWouldBlockException() : SocketException() {
    }
};


class Socket
{
public:
    virtual ~Socket();

    // closes the socket. No further use is allowed
    void close();
    // Obtain raw FD. Care must be taken by caller to avoid
    // external use at same time as any internal I/O functions.
    PlatformNet::SocketFD getFD();

    // Checks the socket-level error flag (generally sockopt SO_ERROR).
    // Clears any pending error if underlying implementation allows.
    // Specifically this should check at least for errors in asynchronous
    // connections and i/o on non-blocking sockets
    // Returns true on error, false otherwise.
    // If false is returned and errCode is non-null, errCode is set
    // to give more information about the formerly pending error
    bool isSocketErrored(netinterfaceenums::NetInterfaceErrorCode *errCode = NULL);

protected:
    Socket();
    PlatformNet::SocketFD fd;
};


class TcpSocket : public Socket
{
public:
    // throw if socket can't be created properly.
    TcpSocket(NetAddressType addrType, bool isBlocking)
            COMMO_THROW (SocketException);

    // connect this socket to the remote endpoint
    // throws on error.
    // If the socket is non-blocking then this will always return immediately.
    // Returns true if connection succeeds (always true or exception for
    // blocking sockets), or false if this is a non-blocking socket and
    // the connection is proceeding asynchronously.
    bool connect(const NetAddress *remoteEndpoint) COMMO_THROW (SocketException);

    // bind this socket to a local address/port combo.  The address
    // can be the wildcard address to bind to all interfaces.
    // throws on error.
    void bind(const NetAddress *localAddr) COMMO_THROW (SocketException);

    // tells the socket to listen for connections passively.
    // the socket must have been previously bound
    // throws on error.
    void listen(int backlog) COMMO_THROW (SocketException);
    
    // Accepts a waiting connection.  This socket must be bound and listening.
    // On non-blocking sockets, this returns NULL if there is no waiting
    // connection. For blocking sockets, this never returns NULL and will
    // block waiting for a connection if none is immediately available.
    // If addr is non-NULL, it will be populated with the address of
    // the remote peer.
    // The returned socket will be set to non-blocking mode if this
    // socket is, else it will be in blocking mode.
    // Throws on error.
    TcpSocket *accept(NetAddress **addr) COMMO_THROW (SocketException);

    // Returns amount of data written, which may be zero. On non-blocking
    // sockets, if the write would block zero is returned. For any error
    // condition, an exception is thrown. Trying to write 0 bytes is
    // acceptable
    size_t write(const uint8_t *data, const size_t len) COMMO_THROW (SocketException);

    // Attempt to read up to 'len' bytes into the buffer 'data'.
    // Actual number of bytes read is returned (which can be zero if either
    // 'len' is zero or no data is available and this socket is non-blocking).
    // Throws exception for any error.
    size_t read(uint8_t *data, const size_t len) COMMO_THROW (SocketException);

private:
    TcpSocket(PlatformNet::SocketFD fd, NetAddressType addrType,
                           bool isBlocking) COMMO_THROW (SocketException);
    bool isBlocking;
    NetAddressType addrType;
};


class UdpSocket : public Socket
{
public:
    // Creates local UDP socket against the given NetAddress.
    // The socket will be bound to the given address, and configured
    // for blocking or non-blocking I/O depending on the isBlocking
    // argument. If localAddr uses a port of NetAddress::WILDCARD_PORT
    // the system will chose a port from the pool of free ports.
    // reuseAddr controls if the socket option for reusing local address/ports
    // should be set prior to binding
    // Throws if the socket can't be created or bound.
    UdpSocket(const NetAddress *localAddr, bool isBlocking,
              bool reuseAddr = false, bool mcastLooping = false) COMMO_THROW (SocketException);
    ~UdpSocket();

    // 'source' is the (optional, can be NULL) NetAddress of the received data;
    // pointed-to value on entry is ignored.
    // If non-NULL, filled with new-allocated NetAddress. Caller owns.
    // data is pre-allocated data buffer of size indicated in 'len'.
    // The received data is copied to data and 'len' is set to the
    // resulting length. If data buffer is too small, parts of the received data
    // may be lost; partial messages will be returned. Use a buffer of 65536 (max UDP
    // size) to ensure reception.
    // Throws SocketException for errors receiving the data.
    // Throws SocketWouldBlockException if no data is immediately
    // available to read and socket is nonblocking
    void recvfrom(NetAddress **source, uint8_t *data, size_t *len) COMMO_THROW (SocketException);

    // Send data of size 'len' to 'dest'.
    // If multicasting, look at multicastto() instead.
    // Throws SocketException for errors in sending of the data.
    // Throws SocketWouldBlockException no data could be written and socket is
    // nonblocking
    void sendto(const NetAddress *dest, const uint8_t *data, size_t len) COMMO_THROW (SocketException);

    // Same as sendto, but will do required multicast setup for the
    // given TTL value prior to sending.  Assumes 'dest' is a multicast
    // address.
    // Currently only supports IPv4
    void multicastto(const NetAddress *dest, const uint8_t *data, size_t len, int ttl) COMMO_THROW (SocketException);

    // Issue a multicast join for the multicast addr in mcastAddr on
    // the local interface specified by ifaceAddr.
    // The port setting in mcastAddr, if any, is ignored.
    // Both addresses need to have the same address family as
    // this socket.
    // Throws if the join could not be performed for any reason.
    // Currently only supports IPv4
    void mcastJoin(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress *mcastAddr) COMMO_THROW (SocketException);

    void mcastLeave(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress *mcastAddr) COMMO_THROW (SocketException);

    // Obtain a copy of the locally bound network address
    NetAddress *getBoundAddr();

private:
    // Checks the given NetAddress to see if its family
    // matches that of the socket. Throws if not
    void checkAddr(const NetAddress *addr) COMMO_THROW (SocketException);

    void mcastMembershipChange(CommoLogger *logger, const NetAddress *ifaceAddr, const NetAddress *mcastAddr, bool add);

    NetAddress *boundAddr;
    int currentTTL;
    bool outboundMcastIfSet;
};


/**
 * Holds an internal socket that can be used in the read portion of a 
 * select() call and provides convenience methods to send small bits of 
 * arbitrary data to the socket to make it readable and wake the select
 * on demand.  The socket listens on loopback to prevent outside interference.
 * This class handles multiple threads being interrupters, but expects
 * only a single thread performing socket drains.
 */
class SelectInterrupter
{
public:
    /**
     * Throws if the internal socket cannot be created for any reason
     */
    SelectInterrupter() COMMO_THROW (SocketException);
    ~SelectInterrupter();
    
    /**
     * Send a small data packet to the internal socket to trigger
     * any select() waiting on it for read to wake and internally
     * increment the number of active interrupt triggers.
     * Each thread triggering an interrupt is expected to untrigger when
     * no longer needed.
     * This is safe to be called by multiple threads.
     */
    void trigger();

    /**
     * Untrigger the interrupt status.  This removes one held interrupt trigger.
     * If this was the last triggered interrupt, any thread waiting for
     * interrupt status to clear will be released.
     * This is safe to invoke by multiple threads.
     */    
    void untrigger();

    /**
     * Check if interrupted and, if so, wait for the interrupt status
     * to clear completely (all active triggers removed).
     */
    void waitUntilUntriggered();

    /**
     * Read all data on the socket and discard it.  This is not safe
     * for use by multiple threads.
     */
    void drain();
    
    /**
     * Obtain a pointer to the internal socket for use when doing select()
     * on it.  Returned socket is valid for the lifetime of this object.
     */
    Socket *getSocket();

private:
    COMMO_DISALLOW_COPY(SelectInterrupter);

    NetAddress *localAddr;
    UdpSocket *socket;
    uint8_t dataBuf[16];

    int interruptCount;
    thread::Monitor monitor;
};



}
}
}


#endif /* IMPL_NETSOCKET_H_ */
