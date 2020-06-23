//
//  netinterfaceimpl.h
//  commoncommo
//
//  Created by Jeff Downs on 12/11/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef netinterfaceimpl_h
#define netinterfaceimpl_h

#import "netinterface_objc.h"
#include <netinterface.h>

@interface CommoNetInterfaceImpl : NSObject <CommoNetInterface> {
}
@property (readonly) atakmap::commoncommo::NetInterface *nativeImpl;

-(instancetype)initWithNativeNetInterface:(atakmap::commoncommo::NetInterface *) nativeImpl;
-(instancetype)init;

@end



@interface CommoPhysicalNetInterfaceImpl : CommoNetInterfaceImpl <CommoPhysicalNetInterface> {
}
@property (readonly) atakmap::commoncommo::PhysicalNetInterface *physNativeImpl;

-(instancetype)initWithNativePhysNetInterface:(atakmap::commoncommo::PhysicalNetInterface *) nativeImpl;
-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl;

@end



@interface CommoTcpInboundNetInterfaceImpl : CommoNetInterfaceImpl <CommoTcpInboundNetInterface> {
    
}
@property (readonly) atakmap::commoncommo::TcpInboundNetInterface *tcpInboundNativeImpl;

-(instancetype)initWithNativeTcpInboundNetInterface:(atakmap::commoncommo::TcpInboundNetInterface *) nativeImpl;
-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl;

@end



@interface CommoStreamingNetInterfaceImpl : CommoNetInterfaceImpl <CommoStreamingNetInterface> {
    
}
@property (readonly) atakmap::commoncommo::StreamingNetInterface *streamingNativeImpl;

-(instancetype)initWithNativeStreamingNetInterface:(atakmap::commoncommo::StreamingNetInterface *) nativeImpl;
-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl;

@end



namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class CommoInterfaceStatusListenerImpl : public atakmap::commoncommo::InterfaceStatusListener
            {
            public:
                CommoInterfaceStatusListenerImpl(NSMutableDictionary<NSValue *, id<CommoNetInterface> > *registry, id<CommoInterfaceStatusListener> objcImpl);
                virtual ~CommoInterfaceStatusListenerImpl();
                virtual void interfaceUp(NetInterface *iface);
                virtual void interfaceDown(NetInterface *iface);
                virtual void interfaceError(NetInterface *iface, netinterfaceenums::NetInterfaceErrorCode errCode);
                
            private:
                id<CommoInterfaceStatusListener> objcImpl;
                NSMutableDictionary<NSValue *, id<CommoNetInterface> > *registry;
            };
        }
    }
}



#endif /* netinterfaceimpl_h */
