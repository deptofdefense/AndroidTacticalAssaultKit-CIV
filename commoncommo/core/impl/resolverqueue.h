#ifndef IMPL_REOSLVERQUEUE_H_
#define IMPL_REOSLVERQUEUE_H_


#include "commoutils.h"
#include "netinterface.h"
#include "commologger.h"

#include "commotime.h"
#include "netsocket.h"
#include "threadedhandler.h"

#include <Mutex.h>
#include <Cond.h>

#include <set>
#include <deque>

namespace atakmap {
namespace commoncommo {
namespace impl {


class ResolverListener;


class ResolverQueue : public ThreadedHandler
{
public:
#define RESQ_INFINITE_TRIES -1
    class Request {
    protected:
        Request() {};
        virtual ~Request() {};

    private:
        COMMO_DISALLOW_COPY(Request);
    };

    ResolverQueue(CommoLogger *logger, ResolverListener *listener,
                  float retrySeconds, int nTries);
    virtual ~ResolverQueue();

    Request *queueForResolution(const std::string &name);
    // NOTE: slightly possible for a cancelled request to come back as done
    // or failed if resolution was already complete!
    void cancelResolution(Request *request);


protected:
    // ThreadedHandler impl
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);

private:
    enum { RESOLUTION_THREADID, DISPATCH_THREADID };

    class RequestImpl : public Request {
    public:
        CommoTime retryTime;
        int tryCount;
        const std::string host;
        NetAddress *result;

        RequestImpl(const std::string &host);
        virtual ~RequestImpl();
    };

    typedef std::set<RequestImpl *> RequestSet;
    typedef std::deque<RequestImpl *> RequestQueue;

    CommoLogger *logger;
    ResolverListener *listener;
    const float retrySeconds;
    const int nTries;

    RequestSet requests;
    PGSC::Thread::Mutex requestsMutex;
    PGSC::Thread::CondVar requestsMonitor;

    RequestQueue errQueue;
    RequestQueue doneQueue;

    PGSC::Thread::Mutex dispatchMutex;
    PGSC::Thread::CondVar dispatchMonitor;


    COMMO_DISALLOW_COPY(ResolverQueue);
    void resolutionThreadProcess();
    void dispatchThreadProcess();
};


class ResolverListener
{
public:
    // return true if "taking ownership" of the NetAddress
    // false if resolver keeps it
    // If resolution fails after all attempts, result is NULL
    virtual bool resolutionComplete(
        ResolverQueue::Request *identifier,
        const std::string &hostAddr,
        NetAddress *result) = 0;

    // Callback indicating a resolution attempt failed
    virtual void resolutionAttemptFailed(
        ResolverQueue::Request *identifier,
        const std::string &hostAddr) {};

protected:
    ResolverListener() {};
    virtual ~ResolverListener() {};

private:
    COMMO_DISALLOW_COPY(ResolverListener);

};



}
}
}

#endif /* IMPL_STREAMINGSOCKETMANAGEMENT_H_ */
