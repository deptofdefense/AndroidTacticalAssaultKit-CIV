
#import <Foundation/Foundation.h>

#include <algorithm>

#include "util/HttpClient.h"
#include "threads/Mutex.hh"
#include "threads/Lock.hh"

#define DEBUG_RESPONSE_SAVE_TO_DISK 0

using namespace atakmap::util;

class HttpClient::HttpClientImpl {
public:
    HttpClientImpl()
    : semaphore(dispatch_semaphore_create(0)),
    receivedData(nil),
    currentTask(nil),
    urlRequest(nil),
    userAgentStr(nil),
    timeoutInterval(10.0),
    readTimeoutDelta(INT64_MAX),
    readOffset(0) { }
    
    bool openConnectionImpl(const char *str);
    void closeConnectionImpl();
    
    void setReadTimeoutImpl(size_t readTimeout) {
        readTimeoutDelta = readTimeout;
    }
    
    void setConnectTimeoutImpl(size_t timeout) {
      //  timeoutInterval = static_cast<double>(timeout) / 1000.0;
    }
    
    void setUserAgentImpl(const char *userAgent) {
        @autoreleasepool {
            userAgentStr = [NSString stringWithUTF8String:userAgent];
        }
    }
    
    int readImpl(uint8_t *buf, size_t len) {
        
        //waitForRead();
        if (!hasResponse()) {
            waitForRead();
        }
        
        int result = 0;
        
        if (receivedData) {
            size_t readSize = std::min(std::min(len, receivedData.length - readOffset), (size_t)INT_MAX);
            memcpy(buf, static_cast<const uint8_t *>(receivedData.bytes) + readOffset, readSize);
            readOffset += readSize;
            result = readSize & INT_MAX; // hush compiler
        }
        
        // endRead();
        
        return result;
    }
    
    int getResponseCodeImpl() {
        //waitForRead();
        if (!hasResponse()) {
            waitForRead();
        }
        
        int responseCode = -1;
        if (receivedResponse) {
            NSHTTPURLResponse *httpResp = (NSHTTPURLResponse *)receivedResponse;
            responseCode = static_cast<int>(httpResp.statusCode);
        }
        endRead();
        return responseCode;
    }
    
    bool hasResponse() {
        PGSC::Lock lock(mutex);
        return this->receivedResponse != nil || this->receivedError != nil;
    }
    
private:
    void waitForRead() {
        dispatch_time_t waitUntil = dispatch_time(DISPATCH_TIME_NOW, readTimeoutDelta);
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);
    }
    
    void endRead() {
        dispatch_semaphore_signal(semaphore);
    }
    
private:
    PGSC::Mutex mutex;
    dispatch_semaphore_t semaphore;
    NSData *receivedData;
    NSURLResponse *receivedResponse;
    NSError *receivedError;
    NSURLSessionDataTask *currentTask;
    NSURLRequest *urlRequest;
    NSString *userAgentStr;
    NSTimeInterval timeoutInterval;
    int64_t readTimeoutDelta;
    size_t readOffset;
};

namespace {
#if 0
    void saveDataToDocument(NSData *data, NSString *fileName) {
        NSFileManager *fileManager = [NSFileManager defaultManager];
        NSArray<NSURL *> *supportURLs = [fileManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask];
        NSString *fullPath = [[supportURLs objectAtIndex:0].path stringByAppendingPathComponent:fileName];
        [data writeToFile:fullPath atomically:YES];
    }
#endif
}

bool HttpClient::HttpClientImpl::openConnectionImpl(const char *url) {
    
    @autoreleasepool {
    
        NSString *urlStr = [NSString stringWithUTF8String:url];
        
        NSMutableURLRequest *mutableRequest = [[NSMutableURLRequest alloc] initWithURL:[NSURL URLWithString:urlStr]
                                                                           cachePolicy:NSURLRequestReloadIgnoringLocalCacheData
                                                                       timeoutInterval:timeoutInterval];
        if (userAgentStr) {
            [mutableRequest setValue:userAgentStr forHTTPHeaderField:@"User-Agent"];
        }
        urlRequest = mutableRequest;
        
        currentTask = [[NSURLSession sharedSession] dataTaskWithRequest:urlRequest completionHandler:
            ^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
                {
                PGSC::Lock lock(mutex);
                receivedData = data;
                receivedResponse = response;
                receivedError = error;
#if DEBUG_RESPONSE_SAVE_TO_DISK
                if (data) {
                    saveDataToDocument(data, @"tile.png");
                }
#endif
                }
                dispatch_semaphore_signal(semaphore);
        }];
        
        [currentTask resume];
    }
    
    this->waitForRead();
    
    return this->receivedError == nil;
}

void HttpClient::HttpClientImpl::closeConnectionImpl() {
    @autoreleasepool {
        [currentTask cancel];
    }
}

HttpClient::HttpClient() :
opaque(new HttpClientImpl())
{ }

HttpClient::~HttpClient() {
    closeConnection();
}

void HttpClient::setReadTimeout(const size_t readTimeout) {
    opaque->setReadTimeoutImpl(readTimeout);
}

void HttpClient::setConnectTimeout(const size_t timeout) {
    opaque->setConnectTimeoutImpl(timeout);
}

void HttpClient::setUserAgent(const char *agent) {
    opaque->setUserAgentImpl(agent);
}

bool HttpClient::openConnection(const char *url) {
    return opaque->openConnectionImpl(url);
}

void HttpClient::closeConnection() {
    if (opaque) {
        opaque->closeConnectionImpl();
        delete opaque;
        opaque = NULL;
    }
}

int HttpClient::read(uint8_t *buf, const size_t len) {
    return opaque->readImpl(buf, len);
}

int HttpClient::getResponseCode() {
    return opaque->getResponseCodeImpl();
}
