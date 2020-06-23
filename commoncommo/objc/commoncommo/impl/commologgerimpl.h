//
//  commologgerimpl.h
//  commoncommo
//
//  Created by Jeff Downs on 12/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef commologgerimpl_h
#define commologgerimpl_h

#import "commologger_objc.h"
#include <commologger.h>


namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class CommoLoggerImpl : public atakmap::commoncommo::CommoLogger
            {
            public:
                CommoLoggerImpl(id<CommoLogger> objcImpl);
                virtual ~CommoLoggerImpl();
                
                virtual void log(Level level, const char *message);
                
            private:
                id<CommoLogger> objcImpl;
            };
        }
    }
}

#endif /* commologgerimpl_h */
