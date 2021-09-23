#define __STDC_LIMIT_MACROS
#include "resolverqueue.h"
#include "internalutils.h"
#include "commothread.h"

#include <sstream>
#include <limits.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;
using namespace atakmap::commoncommo::impl::thread;


/*************************************************************************/
// ResolverQueue constructor/destructor


ResolverQueue::ResolverQueue(CommoLogger *logger, ResolverListener *listener,
                             float retrySeconds, int nTries) :
        ThreadedHandler(2), logger(logger),
        listener(listener),
        retrySeconds(retrySeconds),
        nTries(nTries),
        requests(), requestsMutex(),
        requestsMonitor(),
        doneQueue(), dispatchMutex(),
        dispatchMonitor()
{
    startThreads();
}

ResolverQueue::~ResolverQueue()
{
    stopThreads();

    // With the threads all joined, we can remove everything safely
    while (!doneQueue.empty()) {
        RequestImpl *r = doneQueue.back();
        doneQueue.pop_back();
        delete r;
    }

    RequestSet::iterator iter;
    for (iter = requests.begin(); iter != requests.end(); ++iter)
        delete *iter;
}


/**********************************************************************/
// Public API: Request management

ResolverQueue::Request *ResolverQueue::queueForResolution(
                                const std::string &name)
{
    Lock rLock(requestsMutex);
    RequestImpl *r = new RequestImpl(name);
    requests.insert(r);
    requestsMonitor.broadcast(rLock);
    return r;
}

void ResolverQueue::cancelResolution(ResolverQueue::Request *request)
{
    Lock rLock(requestsMutex);
    RequestImpl *rimpl = (RequestImpl *)request;
    if (requests.erase(rimpl) == 1)
        delete rimpl;
}



/*************************************************************************/
// ResolverQueue - ThreadedHandler impl

void ResolverQueue::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case DISPATCH_THREADID:
        dispatchThreadProcess();
        break;
    case RESOLUTION_THREADID:
        resolutionThreadProcess();
        break;
    }
}

void ResolverQueue::threadStopSignal(size_t threadNum)
{
    switch (threadNum) {
    case DISPATCH_THREADID:
        {
            Lock dLock(dispatchMutex);
            dispatchMonitor.broadcast(dLock);
            break;
        }
        break;
    case RESOLUTION_THREADID:
        {
            Lock rLock(requestsMutex);
            requestsMonitor.broadcast(rLock);
            break;
        }
    }
}



/*************************************************************************/
// ResolverQueue - dispatch done queue processing thread

void ResolverQueue::dispatchThreadProcess()
{
    while (!threadShouldStop(DISPATCH_THREADID)) {
        RequestImpl *qitem;
        std::string host;
        bool isErr;
        {
            Lock dLock(dispatchMutex);
            
            if (errQueue.empty() && doneQueue.empty()) {
                dispatchMonitor.wait(dLock);
                continue;
            }

            // Send failures out first
            if (!errQueue.empty()) {
                qitem = errQueue.back();
                isErr = true;
                errQueue.pop_back();
            } else {
                qitem = doneQueue.back();
                isErr = false;
                doneQueue.pop_back();
            }
            // Copy now while we hold the lock, else
            // the request could be cancelled and deleted (in the err case
            // anyway since err queue doesn't "own" the request)
            // Done requests are safe to use qitem
            host = qitem->host;
        }

        if (!isErr) {
            if (listener->resolutionComplete(qitem, host,
                                         qitem->result))
                // Caller wants the result - null it out so we don't free
                qitem->result = NULL;
            delete qitem;
        } else {
            listener->resolutionAttemptFailed(qitem, host);
        }
    }
}


/*************************************************************************/
// ResolverQueue - name resolution processing thread

void ResolverQueue::resolutionThreadProcess()
{
    std::string resolveMe;

    while (!threadShouldStop(RESOLUTION_THREADID)) {
        // Get context with lowest retry time that is past "now"
        RequestImpl *resolveMeRequest = NULL;
        {
            Lock lock(requestsMutex);
            int64_t wakeMillis;
            bool wakeTimeValid = false;

            RequestSet::iterator iter;
            CommoTime earliestTime = CommoTime::ZERO_TIME;
            RequestImpl *lowestTimeCtx = NULL;
            for (iter = requests.begin(); iter != requests.end(); ++iter) {
                RequestImpl *ctx = *iter;
                if (!lowestTimeCtx || ctx->retryTime < earliestTime) {
                    earliestTime = ctx->retryTime;
                    lowestTimeCtx = ctx;
                }
            }
            if (lowestTimeCtx) {
                CommoTime nowTime = CommoTime::now();
                // We have something....
                if (earliestTime > nowTime) {
                    // ... but maybe not time yet
                    CommoTime t = earliestTime;
                    earliestTime -= nowTime;
                    unsigned int s = earliestTime.getSeconds();
                    // If under 1 second, just do it anyway - avoids
                    // busy spinning in our non-sub-1s resolution waits
                    if (s > 0) {
                        // Hold out and wait!
                        wakeMillis = s * 1000;
                        wakeTimeValid = true;
                    }
                }
                
                if (!wakeTimeValid) {
                    // our time has come. Process this item!
                    resolveMe = lowestTimeCtx->host;
                    resolveMeRequest = lowestTimeCtx;
                }
            } // else we have nothing - we'll wait until we are told we have something

            if (!resolveMeRequest) {
                InternalUtils::logprintf(logger,
                                CommoLogger::LEVEL_DEBUG,
                                "Resolver (%p) No hostname resolution requests - sleeping",
                                this);
                if (wakeTimeValid)
                    requestsMonitor.wait(lock, wakeMillis);
                else
                    requestsMonitor.wait(lock);

                // Go back to the top to check if thread should die; else
                // recompute everything
                continue;
            }
        }

        // If we get here, we know we have a name picked to resolve.
        // Try the resolution.
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                 "Resolver (%p) Trying to resolve address: %s",
                 this,
                 resolveMe.c_str());
        NetAddress *addr = NetAddress::resolveAndCreate(resolveMe.c_str(), true);

        // Reacquire the lock and be certain our context is still valid.
        // If so, let our listener know the result or retry later if failed
        // If not, toss it
        {
            Lock lock(requestsMutex);

            RequestSet::iterator iter = requests.find(resolveMeRequest);
            if (iter != requests.end()) {
                // Still valid
                if (addr || (nTries > 0 && ++resolveMeRequest->tryCount > nTries)) {
                    // Success or failure on last retry - call it quits
                    resolveMeRequest->result = addr;
                    {
                        // Move to done list and wake that guy up
                        Lock dLock(dispatchMutex);
                        doneQueue.push_front(resolveMeRequest);
                        requests.erase(iter);
                        dispatchMonitor.broadcast(dLock);
                    }
                } else {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                            "Resolver (%p) Unable to resolve address: %s - will try again.",
                            this, resolveMe.c_str());

                    // Failed.  We already incremented
                    // retry count above (if it matters).
                    // Set next time.
                    resolveMeRequest->retryTime = CommoTime::now() + retrySeconds;

                    // Copy over to error list - note that we retain
                    // ownership in requests list
                    {
                        Lock dLock(dispatchMutex);
                        errQueue.push_front(resolveMeRequest);
                        dispatchMonitor.broadcast(dLock);
                    }
                }

            } else {
                // Request was withdrawn; delete any result
                delete addr;
            }
        }
    }
}




/*************************************************************************/
// Internal utility classes

ResolverQueue::RequestImpl::RequestImpl(const std::string &host) :
        Request(), retryTime(CommoTime::now()),
        tryCount(0), host(host),
        result(NULL)
{
}

ResolverQueue::RequestImpl::~RequestImpl()
{
    if (result)
        delete result;
}

