//
//  netinterface_objc.m
//  commoncommo
//
//  Created by Jeff Downs on 12/11/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "netinterfaceimpl.h"


@implementation CommoNetInterfaceImpl  {
     
}

-(instancetype)initWithNativeNetInterface:(atakmap::commoncommo::NetInterface *) nativeImpl
{
    self = [super init];
    if (self) {
        _nativeImpl = nativeImpl;
    }
    return self;
}

-(instancetype)init
{
    return nil;
}

@end





@implementation CommoPhysicalNetInterfaceImpl {

}
@synthesize hardwareAddress = _hardwareAddress;

-(instancetype)initWithNativePhysNetInterface:(atakmap::commoncommo::PhysicalNetInterface *) nativeImpl
{
    self = [super initWithNativeNetInterface:nativeImpl];
    if (self) {
        _physNativeImpl = nativeImpl;
        const atakmap::commoncommo::HwAddress *addr = nativeImpl->addr;
        _hardwareAddress = [[NSData alloc] initWithBytes:addr->hwAddr length:addr->addrLen];
    }
    return self;
}

-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl
{
    return nil;
}

@end






@implementation CommoTcpInboundNetInterfaceImpl {
    
}
@synthesize localPort = _localPort;

-(instancetype)initWithNativeTcpInboundNetInterface:(atakmap::commoncommo::TcpInboundNetInterface *)nativeImpl
{
    self = [super initWithNativeNetInterface:nativeImpl];
    if (self) {
        _tcpInboundNativeImpl = nativeImpl;
        _localPort = nativeImpl->port;

    }
    return self;
}

-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl
{
    return nil;
}

@end



@implementation CommoStreamingNetInterfaceImpl {
    
}
@synthesize streamId = _streamId;

-(instancetype)initWithNativeStreamingNetInterface:(atakmap::commoncommo::StreamingNetInterface *) nativeImpl
{
    self = [super initWithNativeNetInterface:nativeImpl];
    if (self) {
        _streamingNativeImpl = nativeImpl;
        _streamId = [[NSString alloc] initWithUTF8String:nativeImpl->remoteEndpointId];

    }
    return self;
}

-(instancetype)initWithNative:(atakmap::commoncommo::NetInterface *) nativeImpl
{
    return nil;
}

@end





using namespace atakmap::commoncommo;


namespace {
    CommoNetInterfaceErrorCode netErrToObjC(netinterfaceenums::NetInterfaceErrorCode errCode)
    {
        CommoNetInterfaceErrorCode ret = CommoNetErrOther;
        switch (errCode) {
            case netinterfaceenums::ERR_CONN_NAME_RES_FAILED:
                ret = CommoNetErrConnNameResFailed;
                break;
            case netinterfaceenums::ERR_CONN_REFUSED:
                ret = CommoNetErrConnRefused;
                break;
            case netinterfaceenums::ERR_CONN_TIMEOUT:
                ret = CommoNetErrConnTimeout;
                break;
            case netinterfaceenums::ERR_CONN_HOST_UNREACHABLE:
                ret = CommoNetErrConnHostUnreachable;
                break;
            case netinterfaceenums::ERR_CONN_SSL_NO_PEER_CERT:
                ret = CommoNetErrConnSSLNoPeerCert;
                break;
            case netinterfaceenums::ERR_CONN_SSL_PEER_CERT_NOT_TRUSTED:
                ret = CommoNetErrConnSSLPeerCertNotTrusted;
                break;
            case netinterfaceenums::ERR_CONN_SSL_HANDSHAKE:
                ret = CommoNetErrConnSSLHandshake;
                break;
            case netinterfaceenums::ERR_CONN_OTHER:
                ret = CommoNetErrConnOther;
                break;
            case netinterfaceenums::ERR_IO_RX_DATA_TIMEOUT:
                ret = CommoNetErrIORxDataTimout;
                break;
            case netinterfaceenums::ERR_IO:
                ret = CommoNetErrIO;
                break;
            case netinterfaceenums::ERR_INTERNAL:
                ret = CommoNetErrInternal;
                break;
            case netinterfaceenums::ERR_OTHER:
                ret = CommoNetErrOther;
                break;
        }
        return ret;
    }
}




objcimpl::CommoInterfaceStatusListenerImpl::CommoInterfaceStatusListenerImpl(NSMutableDictionary<NSValue *,
                                                                             id<CommoNetInterface> > *dict,
                                                                             id<CommoInterfaceStatusListener> objcImpl) :        objcImpl(objcImpl), registry(dict)
{
}

objcimpl::CommoInterfaceStatusListenerImpl::~CommoInterfaceStatusListenerImpl()
{
    
}

void objcimpl::CommoInterfaceStatusListenerImpl::interfaceUp(NetInterface *iface)
{
    @autoreleasepool {
        NSValue *v = [NSValue valueWithPointer:iface];
        id<CommoNetInterface> objid = [registry objectForKey:v];
        
        [objcImpl interfaceUp:objid];
    }
}

void objcimpl::CommoInterfaceStatusListenerImpl::interfaceDown(NetInterface *iface)
{
    @autoreleasepool {
        NSValue *v = [NSValue valueWithPointer:iface];
        id<CommoNetInterface> objid = [registry objectForKey:v];
        
        [objcImpl interfaceDown:objid];
    }
}

void objcimpl::CommoInterfaceStatusListenerImpl::interfaceError(NetInterface *iface,
                            netinterfaceenums::NetInterfaceErrorCode errCode)
{
    @autoreleasepool {
        NSValue *v = [NSValue valueWithPointer:iface];
        id<CommoNetInterface> objid = [registry objectForKey:v];
        
        [objcImpl interfaceError:objid errorCode:netErrToObjC(errCode)];
    }
}
