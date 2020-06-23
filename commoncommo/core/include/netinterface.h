#ifndef NETINTERFACE_H_
#define NETINTERFACE_H_


#include "commoutils.h"
#include <stdint.h>
#include <stddef.h>

namespace atakmap {
namespace commoncommo {

namespace netinterfaceenums {
    typedef enum  {
        // Name resolution for a hostname failed
        ERR_CONN_NAME_RES_FAILED,
        // Connection to remote host actively refused
        ERR_CONN_REFUSED,
        // Connection to remote host timed out
        ERR_CONN_TIMEOUT,
        // Remote host is known to be unreachable at this time
        ERR_CONN_HOST_UNREACHABLE,
        // Remote host was expected to present an SSL certificate but didn't
        ERR_CONN_SSL_NO_PEER_CERT,
        // Remote host's SSL certificate was not trusted
        ERR_CONN_SSL_PEER_CERT_NOT_TRUSTED,
        // SSL handshake with remote host encountered an error
        ERR_CONN_SSL_HANDSHAKE,
        // Some other, non-specific, error occurred during attempting
        // to connect to a remote host
        ERR_CONN_OTHER,
        // No data was received and the connection was considered
        // in error/timed out and is being reset
        ERR_IO_RX_DATA_TIMEOUT,
        // A general IO error occurred
        ERR_IO,
        // Some internal error occurred (out of memory, etc)
        ERR_INTERNAL,
        // Some unclassified error has occurred
        ERR_OTHER
    } NetInterfaceErrorCode;
    
    typedef enum  {
        // HwAddress's represent mac/ethernet addresses
        MODE_PHYS_ADDR,
        // HwAddress's represent interface names
        MODE_NAME
    } NetInterfaceAddressMode;
}



struct HwAddress
{
    const size_t addrLen;
    const uint8_t * const hwAddr;
    HwAddress(const uint8_t *hwAddr, const size_t len) : addrLen(len), hwAddr(hwAddr) {
    }
private:
    COMMO_DISALLOW_COPY(HwAddress);
};



class NetInterface
{
protected:
    NetInterface() {};
    virtual ~NetInterface() {};
    COMMO_DISALLOW_COPY(NetInterface);
};



class PhysicalNetInterface : public NetInterface
{
public:
    const HwAddress * const addr;

protected:
    PhysicalNetInterface(const HwAddress *addr) : addr(addr) {};
    virtual ~PhysicalNetInterface() {};
    COMMO_DISALLOW_COPY(PhysicalNetInterface);
};



class TcpInboundNetInterface : public NetInterface
{
public:
    const int port;

protected:
    TcpInboundNetInterface(const int port) : port(port) {};
    virtual ~TcpInboundNetInterface() {};
    COMMO_DISALLOW_COPY(TcpInboundNetInterface);
};



class StreamingNetInterface : public NetInterface
{
public:
    // Has null at end for convenience, which is not included in len
    const char * const remoteEndpointId;
    const size_t remoteEndpointLen;

protected:
    StreamingNetInterface(const char *remoteEndpoint,
                          const size_t remoteEndpointLen) :
                              remoteEndpointId(remoteEndpoint),
                              remoteEndpointLen(remoteEndpointLen) {};
    virtual ~StreamingNetInterface() {};
    COMMO_DISALLOW_COPY(StreamingNetInterface);
};

class InterfaceStatusListener
{
public:
    virtual void interfaceUp(NetInterface *iface) = 0;
    virtual void interfaceDown(NetInterface *iface) = 0;
    virtual void interfaceError(NetInterface *iface,
             netinterfaceenums::NetInterfaceErrorCode err) {};

protected:
    InterfaceStatusListener() {};
    virtual ~InterfaceStatusListener() {};

private:
    COMMO_DISALLOW_COPY(InterfaceStatusListener);
};


}
}




#endif /* NETINTERFACE_H_ */
