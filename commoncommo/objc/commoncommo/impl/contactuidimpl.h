//
//  contactimpl.h
//  commoncommo
//
//  Created by Jeff Downs on 12/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef contactimpl_h
#define contactimpl_h

#import "contactuid_objc.h"
#include <contactuid.h>


namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class CommoContactPresenceListenerImpl : public atakmap::commoncommo::ContactPresenceListener
            {
            public:
                CommoContactPresenceListenerImpl(id<CommoContactPresenceListener> objcImpl);
                virtual ~CommoContactPresenceListenerImpl();
                
                virtual void contactAdded(const atakmap::commoncommo::ContactUID *c);
                virtual void contactRemoved(const atakmap::commoncommo::ContactUID *c);
                
            private:
                id<CommoContactPresenceListener> objcImpl;
            };
        }
    }
}

#endif /* contactimpl_h */
