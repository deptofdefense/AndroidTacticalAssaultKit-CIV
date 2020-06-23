//
//  cotmessageioimpl.h
//  commoncommo
//
//  Created by Jeff Downs on 12/11/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef cotmessageioimpl_h
#define cotmessageioimpl_h

#import "cotmessageio_objc.h"
#include <cotmessageio.h>


namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class CommoCoTMessageListenerImpl : public atakmap::commoncommo::CoTMessageListener
            {
            public:
                CommoCoTMessageListenerImpl(id<CommoCoTMessageListener> objcImpl);
                virtual ~CommoCoTMessageListenerImpl();
                virtual void cotMessageReceived(const char *cotMessage, const char *rxEndpointId);
                
            private:
                id<CommoCoTMessageListener> objcImpl;
            };

            class CommoCoTSendFailureListenerImpl : public atakmap::commoncommo::CoTSendFailureListener
            {
            public:
                CommoCoTSendFailureListenerImpl(id<CommoCoTSendFailureListener> objcImpl);
                virtual ~CommoCoTSendFailureListenerImpl();
                virtual void sendCoTFailure(const char *host, int port, const char *errorReason);
                
            private:
                id<CommoCoTSendFailureListener> objcImpl;
            };
        }
    }
}

#endif /* cotmessageioimpl_h */
