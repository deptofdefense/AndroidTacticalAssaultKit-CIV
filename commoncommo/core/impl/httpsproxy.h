#ifndef IMPL_HTTPSPROXY_H_
#define IMPL_HTTPSPROXY_H_

#include "internalutils.h"
#include "commoutils.h"
#include "commologger.h"
#include "commoresult.h"
#include "netsocket.h"
#include "threadedhandler.h"

#include <openssl/ssl.h>


namespace atakmap {
namespace commoncommo {
namespace impl {


class HttpsProxy : public ThreadedHandler
{

public:
    HttpsProxy(CommoLogger *logger);
    virtual ~HttpsProxy();
    
    
    // Success, Invalid_cert, invalid_cert_password,
    // or illegal_argument (for cannot bind port or null cert info)
    // Server is shut down on any error or if localPort is 
    // MP_LOCAL_PORT_DISABLE
    // Server also stays down if local http port not yet set
    CommoResult setServerParams(int localPort, const uint8_t *cert,
                                size_t certLen,
                                const char *certPass);
    // throws when out of range.  On throw, nothing is changed
    void setLocalHttpPort(int port) COMMO_THROW (std::invalid_argument);
    // throws when out of range.  On throw, nothing is changed
    void setConnTimeoutSec(int connTimeoutSec) COMMO_THROW (std::invalid_argument);

protected:
    // ThreadedHandler impl
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);

private:
    enum { IO_THREADID };


    CommoLogger *logger;


    
    COMMO_DISALLOW_COPY(HttpsProxy);
    void ioThreadProcess();
    
    class IOThreadContext;
    std::unique_ptr<IOThreadContext> ioCtx;


};



}
}
}



#endif


