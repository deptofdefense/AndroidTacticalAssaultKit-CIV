#ifndef IMPL_PLATFORM_H_
#define IMPL_PLATFORM_H_

#include <map>
#include <vector>
#include <string>
#include <stdexcept>
#include <stddef.h>
#include "netinterface.h"
#include "internalutils.h"
#include "commologger.h"

#ifdef WIN32
#include <winsock2.h>
#include <WS2tcpip.h>
#else
#include <sys/socket.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/select.h>
#endif



#ifdef WIN32
    #define snprintf _snprintf
#endif


namespace atakmap {
namespace commoncommo {
namespace impl
{

// Forward declare
class NetAddress;
class Socket;

// Not platform-specific, but needed by some platform specifics as well
// as some platform independents, so it lives here for now.
struct HwComp
{
    bool operator()(const HwAddress * const &a, const HwAddress * const &b) const {
        const size_t n = a->addrLen < b->addrLen ? a->addrLen : b->addrLen;
        for (size_t i = 0; i < n; ++i) {
            if (a->hwAddr[i] < b->hwAddr[i])
                return true;
            if (a->hwAddr[i] > b->hwAddr[i])
                return false;
        }
        return a->addrLen < b->addrLen;
    }
};

// Similar to HwComp, this is not platform-specific, but needed by others here
class SocketException : public std::runtime_error
{
public:
    SocketException() : std::runtime_error("An unknown error occurred"),
                        errCode(netinterfaceenums::ERR_OTHER)
    {
    }
    SocketException(netinterfaceenums::NetInterfaceErrorCode errCode,
                    const std::string &what) : std::runtime_error(what),
                                               errCode(errCode)
    {
    }
    
    const netinterfaceenums::NetInterfaceErrorCode errCode;
};

typedef enum {
    NA_TYPE_INET4 = AF_INET,
    NA_TYPE_INET6 = AF_INET6
} NetAddressType;





void getCurrentTime(unsigned int *seconds, unsigned int *millis);
std::string getTimeString(unsigned int seconds,
                          unsigned int millis);
void getTimeFromString(unsigned int *seconds,
                       unsigned int *millis,
                       const std::string &timeString) 
                               COMMO_THROW (std::invalid_argument);

bool getFileSize(uint64_t *fsize, const char *file);



struct PlatformNet {

    typedef enum {
        SUCCESS,
        IO_WOULD_BLOCK,
        ADDR_BUF_TOO_SMALL,
        CONN_REFUSED,
        HOST_UNREACHABLE,
        CONN_TIMEDOUT,
        OTHER_ERROR
    } ErrorCode;

#ifdef WIN32
    typedef SOCKET SocketFD;
    static const SocketFD BAD_SOCKET = INVALID_SOCKET;
    static const int SOCKET_IO_ERROR = SOCKET_ERROR;
#else
    typedef int SocketFD;
    static const SocketFD BAD_SOCKET = -1;
    static const int SOCKET_IO_ERROR = -1;
#endif

    // BAD_SOCKET on error; socket is blocking by default.
    // Args are as for BSD socket()
    static SocketFD createSocket(int addressFamily, int type, int protocol);
    static ErrorCode setSocketNonblocking(SocketFD fd);
    static ErrorCode socketConnect(SocketFD fd, const struct sockaddr *addr,
                                   socklen_t slen);
    static void closeSocket(SocketFD fd);

    // len updated to indicate # bytes written (read),
    // which is always 0 for returns not equal to SUCCESS
    static ErrorCode socketWrite(SocketFD fd, const uint8_t *data, size_t *len);
    static ErrorCode socketRead(SocketFD fd, uint8_t *data, size_t *len);

    static ErrorCode socketWriteTo(SocketFD fd, const uint8_t *data, size_t *len,
                                    const struct sockaddr *addr, socklen_t slen);
    static ErrorCode socketReadFrom(SocketFD fd, uint8_t *data, size_t *len,
                                    struct sockaddr *addr, socklen_t *slen);

    static ErrorCode socketAccept(SocketFD *clientFD, SocketFD fd,
                                  struct sockaddr *addr, socklen_t *slen);


    static ErrorCode socketSetAddrReuse(SocketFD fd, bool reuse);
    static ErrorCode socketSetMcastIf(SocketFD fd, const NetAddress *localAddr);
    static ErrorCode socketSetMcastLoop(SocketFD fd, NetAddressType type, bool en);
    static ErrorCode socketSetMcastTTL(SocketFD fd, NetAddressType type, 
                                       uint8_t ttl);
    static ErrorCode socketMcastMembership(CommoLogger *logger, SocketFD fd, bool join,
                                           const NetAddress *iface,
                                           const NetAddress *mcastAddr);
    static ErrorCode socketCheckErrored(SocketFD fd);

};




class InterfaceEnumerator
{
public:
    InterfaceEnumerator(CommoLogger *logger, 
                        netinterfaceenums::NetInterfaceAddressMode addrMode) COMMO_THROW (SocketException);
    ~InterfaceEnumerator();

    netinterfaceenums::NetInterfaceAddressMode getAddrMode() {
        return addrMode;
    };
    void rescanInterfaces();
    const std::map<const HwAddress *, const NetAddress *, HwComp> *getInterfaces();

private:
    struct InterfaceEnumeratorContext;

    CommoLogger *logger;
    const netinterfaceenums::NetInterfaceAddressMode addrMode;
    InterfaceEnumeratorContext *context;

    void resetContext();
};




// No thread sync - assumes single thread access
class NetSelector {
public:
    static const long NO_TIMEOUT = -1L;

    NetSelector();
    ~NetSelector();

    // This copies - no need for vectors after call
    // Each call overwrites previous set.
    // Pass NULL or an empty vector
    // to have subsequent doSelect() calls not
    // look at anything in that category.
    // Throws if the socket limit has been exceeded
    void setSockets(const std::vector<Socket *> *readSockets,
                    const std::vector<Socket *> *writeSockets) COMMO_THROW (SocketException);

    // Same as above version, but specifically calls out connecting sockets.
    // Connecting sockets should be checked with getLastConnectState()
    // to return WRITEABLE
    // Throws if the socket limit has been exceeded
    void setSockets(const std::vector<Socket *> *readSockets,
                    const std::vector<Socket *> *writeSockets,
                    const std::vector<Socket *> *connectingSockets) COMMO_THROW (SocketException);

    // True if something is "ready" or false if timeout happened
    // or there were no sockets to watch.
    // Calling this with no sockets configured will result in
    // a simple "sleep" for the timeout given; doing this with
    // timeoutMillis set to NO_TIMEOUT will raise invalid_argument
    // Use NO_TIMEOUT to block forever.
    // Throws on error.
    bool doSelect(long timeoutMillis) COMMO_THROW (SocketException, std::invalid_argument);

    typedef enum {
        READABLE,       // Reading will succeed without blocking
        WRITABLE,       // Writing will succeed without blocking
        NO_IO           // IO will block
    } SelectState;

    // This gives state of the given socket after the most recent
    // invocation of doSelect().  It is assumed that 's' is
    // in one or both of the most recently given read/write sockets.
    SelectState getLastState(Socket *s);
    // Same but does not check for write status
    SelectState getLastReadState(Socket *s);
    // Same but does not check for read status
    SelectState getLastWriteState(Socket *s);
    // Same as getLastWriteState() but to be used with sockets specified
    // as connecting ONLY
    SelectState getLastConnectState(Socket *s);

private:
    COMMO_DISALLOW_COPY(NetSelector);
    void buildSet(fd_set *set, size_t *count, fd_set **sptr, const std::vector<Socket *> *sockets) COMMO_THROW (SocketException);
    bool growSet(fd_set *set, size_t *count, fd_set **sptr, const std::vector<Socket *> *sockets) COMMO_THROW (SocketException);

    fd_set baseReadSet;
    fd_set baseWriteSet;
    fd_set baseExSet;
    fd_set *rSetPtr;
    fd_set rSet;
    fd_set *wSetPtr;
    fd_set wSet;
    fd_set *exSetPtr;
    fd_set exSet;

    PlatformNet::SocketFD maxfd;
};



}
}
}



#endif /* IMPL_PLATFORM_H_ */
