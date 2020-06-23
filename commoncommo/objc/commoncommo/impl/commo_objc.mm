//
//  commo_objc.m
//  commoncommo
//
//  Created by Jeff Downs on 12/11/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "commo_objc.h"

#include <commo.h>

#import "commologgerimpl.h"
#import "netinterfaceimpl.h"
#import "cotmessageioimpl.h"
#import "contactuidimpl.h"
#import "missionpackageimpl.h"
#import "simplefileioimpl.h"



@implementation Commo {

    atakmap::commoncommo::Commo *nativeCommo;
    atakmap::commoncommo::objcimpl::CommoLoggerImpl *loggerImpl;
    atakmap::commoncommo::objcimpl::MissionPackageIOImpl *missionIOImpl;
    atakmap::commoncommo::objcimpl::SimpleFileIOImpl *sfIOImpl;

    NSMutableDictionary<NSValue *, id<CommoNetInterface> > *interfaceMap;
    NSMapTable<id<CommoInterfaceStatusListener>, NSValue *> *interfaceListenerMap;
    NSMapTable<id<CommoCoTMessageListener>, NSValue *> *messageListenerMap;
    NSMapTable<id<CommoContactPresenceListener>, NSValue *> *contactListenerMap;
    NSMapTable<id<CommoCoTSendFailureListener>, NSValue *> *sendFailureListenerMap;
}

using namespace atakmap::commoncommo;

namespace {
    CoTSendMethod nativeForSendMethod(CommoCoTSendMethod method)
    {
        CoTSendMethod ret = SEND_ANY;
        switch (method) {
            case CommoCoTSendMethodAny:
                ret = SEND_ANY;
                break;
            case CommoCoTSendMethodTakServer:
                ret = SEND_TAK_SERVER;
                break;
            case CommoCoTSendMethodPointToPoint:
                ret = SEND_POINT_TO_POINT;
                break;
        }
        return ret;
    }
    
    ::CommoResult nativeResultToObjc(atakmap::commoncommo::CommoResult nativeErrCode)
    {
        ::CommoResult ret = CommoResultIllegalArgument;
        switch (nativeErrCode) {
            case atakmap::commoncommo::COMMO_SUCCESS:
                ret = CommoResultSuccess;
                break;
            case atakmap::commoncommo::COMMO_CONTACT_GONE:
                ret = CommoResultContactGone;
                break;
            case atakmap::commoncommo::COMMO_INVALID_CERT:
                ret = CommoResultInvalidCert;
                break;
            case atakmap::commoncommo::COMMO_INVALID_CACERT:
                ret = CommoResultInvalidCACert;
                break;
            case atakmap::commoncommo::COMMO_ILLEGAL_ARGUMENT:
                ret = CommoResultIllegalArgument;
                break;
            case atakmap::commoncommo::COMMO_INVALID_CERT_PASSWORD:
                ret = CommoResultInvalidCertPass;
                break;
            case atakmap::commoncommo::COMMO_INVALID_CACERT_PASSWORD:
                ret = CommoResultInvalidCACertPass;
                break;
        }
        return ret;
    }
    
}

const int MPIO_LOCAL_PORT_DISABLE = -1;

-(void) dealloc
{
    [self close];
}

-(void) close
{
    if (nativeCommo) {
        delete nativeCommo;
        nativeCommo = nullptr;
        
        NSEnumerator<NSValue *> *enumerator = [interfaceListenerMap objectEnumerator];
        NSValue *value;
        while ((value = [enumerator nextObject])) {
            objcimpl::CommoInterfaceStatusListenerImpl *impl =
                (objcimpl::CommoInterfaceStatusListenerImpl *)value.pointerValue;
            delete impl;
        }
        
        enumerator = [messageListenerMap objectEnumerator];
        while ((value = [enumerator nextObject])) {
            objcimpl::CommoCoTMessageListenerImpl *impl =
                (objcimpl::CommoCoTMessageListenerImpl *)value.pointerValue;
            delete impl;
        }
        
        enumerator = [contactListenerMap objectEnumerator];
        while ((value = [enumerator nextObject])) {
            objcimpl::CommoContactPresenceListenerImpl *impl =
                (objcimpl::CommoContactPresenceListenerImpl *)value.pointerValue;
            delete impl;
        }
        
        enumerator = [sendFailureListenerMap objectEnumerator];
        while ((value = [enumerator nextObject])) {
            objcimpl::CommoCoTSendFailureListenerImpl *impl =
            (objcimpl::CommoCoTSendFailureListenerImpl *)value.pointerValue;
            delete impl;
        }
        
        [interfaceMap removeAllObjects];
        [interfaceListenerMap removeAllObjects];
        [messageListenerMap removeAllObjects];
        [contactListenerMap removeAllObjects];
        [sendFailureListenerMap removeAllObjects];
        
        if (missionIOImpl)
            delete missionIOImpl;
        missionIOImpl = nullptr;
        if (sfIOImpl)
            delete sfIOImpl;
        sfIOImpl = nullptr;
    }
}

-(instancetype)initWithLogger:(id<CommoLogger>)logger
                   deviceUid:(NSString *) uid
                     callSign:(NSString *) callSign
{
    self = [super init];
    if (self) {
        loggerImpl = new objcimpl::CommoLoggerImpl(logger);
        ContactUID uidNative((const uint8_t *)[uid UTF8String], [uid length]);
        const char *csNative = [callSign UTF8String];
        nativeCommo = new atakmap::commoncommo::Commo(loggerImpl, &uidNative, csNative, atakmap::commoncommo::netinterfaceenums::MODE_NAME);
        interfaceMap = [[NSMutableDictionary alloc] init];
        interfaceListenerMap = [NSMapTable strongToStrongObjectsMapTable];
        messageListenerMap = [NSMapTable strongToStrongObjectsMapTable];
        contactListenerMap = [NSMapTable strongToStrongObjectsMapTable];
        sendFailureListenerMap = [NSMapTable strongToStrongObjectsMapTable];
        
        _broadcastTTL = 1;
        _udpNoDataTimeoutSec = 30;
        _tcpConnTimeoutSec = 20;
        _missionPackageNumTries = 10;
        _missionPackageConnTimeoutSec = 90;
        _missionPackageTransferTimeoutSec = 120;
        _missionPackageViaServerEnabled = true;
        _streamMonitorEnabled = true;
        
        missionIOImpl = nullptr;
        sfIOImpl = nullptr;
    }
    return self;
}

-(instancetype)init
{
    return nil;
}

-(void)setBroadcastTTL:(int) ttl
{
    nativeCommo->setTTL(ttl);
    _broadcastTTL = ttl;
}

-(void)setUdpNoDataTimeoutSec:(int) udpNoDataTimeoutSec
{
    nativeCommo->setUdpNoDataTimeout(udpNoDataTimeoutSec);
    _udpNoDataTimeoutSec = udpNoDataTimeoutSec;
}

-(void)setTcpConnTimeoutSec:(int) tcpConnTimeoutSec
{
    nativeCommo->setTcpConnTimeout(tcpConnTimeoutSec);
    _tcpConnTimeoutSec = tcpConnTimeoutSec;
}

-(void)setMissionPackageNumTries:(int) missionPackageNumTries
{
    if (nativeCommo->setMissionPackageNumTries(missionPackageNumTries) == COMMO_SUCCESS)
        _missionPackageNumTries = missionPackageNumTries;
}

-(void)setMissionPackageConnTimeoutSec:(int) missionPackageConnTimeoutSec
{
    if (nativeCommo->setMissionPackageConnTimeout(missionPackageConnTimeoutSec) == COMMO_SUCCESS)
        _missionPackageConnTimeoutSec = missionPackageConnTimeoutSec;
}

-(void)setMissionPackageTransferTimeoutSec:(int) missionPackageTransferTimeoutSec
{
    if (nativeCommo->setMissionPackageTransferTimeout(missionPackageTransferTimeoutSec) == COMMO_SUCCESS)
        _missionPackageTransferTimeoutSec = missionPackageTransferTimeoutSec;
}

-(void)setMissionPackageViaServerEnabled:(bool)missionPackageViaServerEnabled
{
    nativeCommo->setMissionPackageViaServerEnabled(missionPackageViaServerEnabled);
    _missionPackageViaServerEnabled = missionPackageViaServerEnabled;
}

-(void)setStreamMonitorEnabled:(bool) streamMonitorEnabled
{
    nativeCommo->setStreamMonitorEnabled(streamMonitorEnabled);
    _streamMonitorEnabled = streamMonitorEnabled;
}

-(void)setCallsign:(NSString *)callsign
{
    nativeCommo->setCallsign([callsign UTF8String]);
}

-(::CommoResult)setCryptoKeysToAuth:(NSData *) auth crypt:(NSData *)crypt
{
    if ((auth == nil) ^ (crypt == nil))
        return CommoResultIllegalArgument;
    if (auth != nil && ([auth length] != 32 || [crypt length] != 32))
        return CommoResultIllegalArgument;
    const uint8_t *authNative = NULL;
    const uint8_t *cryptNative = NULL;
    if (auth != nil) {
        authNative = (const uint8_t *)[auth bytes];
        cryptNative = (const uint8_t *)[crypt bytes];
    }
    if (nativeCommo->setCryptoKeys(authNative, cryptNative) == COMMO_SUCCESS)
        return CommoResultSuccess;
    return CommoResultIllegalArgument;
}

-(::CommoResult)setMissionPackageLocalPort:(int)localWebPort
{
    if (nativeCommo->setMissionPackageLocalPort(localWebPort) == COMMO_SUCCESS)
        return CommoResultSuccess;
    return CommoResultIllegalArgument;
}

-(::CommoResult)setMissionPackageHttpsPort:(int)serverPort
{
    if (nativeCommo->setMissionPackageHttpsPort(serverPort) == COMMO_SUCCESS)
        return CommoResultSuccess;
    return CommoResultIllegalArgument;
}

-(int)getBroadcastProto
{
    return nativeCommo->getBroadcastProto();
}

-(::CommoResult) setupMissionPackageIO:(id<CommoMissionPackageIO>) mpio
{
    if (missionIOImpl)
        return CommoResultIllegalArgument;
    objcimpl::MissionPackageIOImpl *m = new objcimpl::MissionPackageIOImpl(mpio);
    if (nativeCommo->setupMissionPackageIO(m) == COMMO_SUCCESS) {
        missionIOImpl = m;
        return CommoResultSuccess;
    }
    return CommoResultIllegalArgument;
}

-(::CommoResult) enableSimpleFileIO:(id<CommoSimpleFileIO>) fileio
{
    if (sfIOImpl)
        return CommoResultIllegalArgument;
    objcimpl::SimpleFileIOImpl *s = new objcimpl::SimpleFileIOImpl(fileio);
    if (nativeCommo->enableSimpleFileIO(s) == COMMO_SUCCESS) {
        sfIOImpl = s;
        return CommoResultSuccess;
    }
    return CommoResultIllegalArgument;
}


-(id<CommoPhysicalNetInterface>) addBroadcastInterfaceWithName:(NSString *) ifaceName
                                                               cotMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                                                  mcastAddress:(NSString *) mcast
                                                                      destPort:(int) destPort
{
    NSData *data = [ifaceName dataUsingEncoding:NSUTF8StringEncoding];
    HwAddress nameNative((uint8_t *)[data bytes], [data length]);
    NSUInteger nsn = [cotTypes count];
    if (nsn > SIZE_T_MAX)
        return nil;
    size_t n = (size_t)nsn;
    CoTMessageType *nativeTypes = new CoTMessageType[n];
    for (size_t i = 0; i < n ; ++i) {
        CoTMessageType nativeType = SITUATIONAL_AWARENESS;
        switch (cotTypes[i].intValue) {
            case CommoCoTMessageTypeSituationalAwareness:
                nativeType = SITUATIONAL_AWARENESS;
                break;
            case CommoCoTMessageTypeChat:
                nativeType = CHAT;
                break;
        }
        nativeTypes[i] = nativeType;
    }
    const char *nativeMcast = [mcast UTF8String];
    PhysicalNetInterface *iface = nativeCommo->addBroadcastInterface(&nameNative, nativeTypes, n, nativeMcast, destPort);
    delete[] nativeTypes;
    if (!iface)
        return nil;
    
    CommoPhysicalNetInterfaceImpl *objcIface = [[CommoPhysicalNetInterfaceImpl alloc] initWithNativePhysNetInterface:iface];
    
    NSValue *nativeValue = [NSValue valueWithPointer:iface];
    [interfaceMap setObject:objcIface forKey:nativeValue];
    return objcIface;
}

-(id<CommoPhysicalNetInterface>) addBroadcastInterfaceForMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                                           unicastAddress:(NSString *) unicast
                                                               destPort:(int) destPort
{
    NSUInteger nsn = [cotTypes count];
    if (nsn > SIZE_T_MAX)
        return nil;
    size_t n = (size_t)nsn;
    CoTMessageType *nativeTypes = new CoTMessageType[n];
    for (size_t i = 0; i < n ; ++i) {
        CoTMessageType nativeType = SITUATIONAL_AWARENESS;
        switch (cotTypes[i].intValue) {
            case CommoCoTMessageTypeSituationalAwareness:
                nativeType = SITUATIONAL_AWARENESS;
                break;
            case CommoCoTMessageTypeChat:
                nativeType = CHAT;
                break;
        }
        nativeTypes[i] = nativeType;
    }
    const char *nativeAddr = [unicast UTF8String];
    PhysicalNetInterface *iface = nativeCommo->addBroadcastInterface(nativeTypes, n, nativeAddr, destPort);
    delete[] nativeTypes;
    if (!iface)
        return nil;
    
    CommoPhysicalNetInterfaceImpl *objcIface = [[CommoPhysicalNetInterfaceImpl alloc] initWithNativePhysNetInterface:iface];
    
    NSValue *nativeValue = [NSValue valueWithPointer:iface];
    [interfaceMap setObject:objcIface forKey:nativeValue];
    return objcIface;
}

-(::CommoResult) removeBroadcastInterface:(id<CommoPhysicalNetInterface>) iface
{
    if (![iface isKindOfClass:[CommoPhysicalNetInterfaceImpl class]])
        return CommoResultIllegalArgument;
    
    CommoPhysicalNetInterfaceImpl *objcPhys = (CommoPhysicalNetInterfaceImpl *)iface;
    PhysicalNetInterface *phys = objcPhys.physNativeImpl;
    NSValue *physValue = [NSValue valueWithPointer:phys];

    id<CommoNetInterface> idFromMap = [interfaceMap objectForKey:physValue];
    if (idFromMap != iface)
        // Weird??
        return CommoResultIllegalArgument;
    if (nativeCommo->removeBroadcastInterface(phys) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [interfaceMap removeObjectForKey:physValue];
    
    return CommoResultSuccess;
}

-(id<CommoPhysicalNetInterface>) addInboundInterfaceWithName:(NSString *) ifaceName
                                                                   localPort:(int) port
                                                                  mcastAddrs:(NSArray<NSString *> *) mcastAddrs
{
    NSData *data = [ifaceName dataUsingEncoding:NSUTF8StringEncoding];
    HwAddress nameNative((uint8_t *)[data bytes], [data length]);
    NSUInteger nsn = [mcastAddrs count];
    if (nsn > SIZE_T_MAX)
        return nil;
    size_t n = (size_t)nsn;
    const char **nativeMcastAddrs = new const char *[n];
    for (size_t i = 0; i < n ; ++i) {
        nativeMcastAddrs[i] = [mcastAddrs[i] UTF8String];
    }
    PhysicalNetInterface *iface = nativeCommo->addInboundInterface(&nameNative, port, nativeMcastAddrs, n, false);
    delete[] nativeMcastAddrs;
    if (!iface)
        return nil;
    
    CommoPhysicalNetInterfaceImpl *objcIface = [[CommoPhysicalNetInterfaceImpl alloc] initWithNativePhysNetInterface:iface];
    
    NSValue *nativeValue = [NSValue valueWithPointer:iface];
    [interfaceMap setObject:objcIface forKey:nativeValue];
    return objcIface;
}

-(::CommoResult) removeInboundInterface:(id<CommoPhysicalNetInterface>) iface
{
    if (![iface isKindOfClass:[CommoPhysicalNetInterfaceImpl class]])
        return CommoResultIllegalArgument;
    
    CommoPhysicalNetInterfaceImpl *objcPhys = (CommoPhysicalNetInterfaceImpl *)iface;
    PhysicalNetInterface *phys = objcPhys.physNativeImpl;
    NSValue *physValue = [NSValue valueWithPointer:phys];
    
    id<CommoNetInterface> idFromMap = [interfaceMap objectForKey:physValue];
    if (idFromMap != iface)
        // Weird??
        return CommoResultIllegalArgument;
    if (nativeCommo->removeInboundInterface(phys) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [interfaceMap removeObjectForKey:physValue];
    
    return CommoResultSuccess;
}

-(id<CommoTcpInboundNetInterface>) addTcpInboundInterfaceWithLocalPort:(int) port
{
    TcpInboundNetInterface *iface = nativeCommo->addTcpInboundInterface(port);
    if (!iface)
        return nil;
    
    CommoTcpInboundNetInterfaceImpl *objcIface = [[CommoTcpInboundNetInterfaceImpl alloc]initWithNativeTcpInboundNetInterface:iface];
    
    NSValue *nativeValue = [NSValue valueWithPointer:iface];
    [interfaceMap setObject:objcIface forKey:nativeValue];
    return objcIface;
}

-(::CommoResult) removeTcpInboundInterface:(id<CommoTcpInboundNetInterface>) iface
{
    if (![iface isKindOfClass:[CommoTcpInboundNetInterfaceImpl class]])
        return CommoResultIllegalArgument;
    
    CommoTcpInboundNetInterfaceImpl *objcTcp = (CommoTcpInboundNetInterfaceImpl *)iface;
    TcpInboundNetInterface *tcp = objcTcp.tcpInboundNativeImpl;
    NSValue *tcpValue = [NSValue valueWithPointer:tcp];
    
    id<CommoNetInterface> idFromMap = [interfaceMap objectForKey:tcpValue];
    if (idFromMap != iface)
        // Weird??
        return CommoResultIllegalArgument;
    if (nativeCommo->removeTcpInboundInterface(tcp) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [interfaceMap removeObjectForKey:tcpValue];
    
    return CommoResultSuccess;
}


-(id<CommoStreamingNetInterface>) addStreamingInterfaceWithHostname:(NSString *)hostname
                                                           destPort:(int) port
                                                    cotMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                                    clientCertBytes:(NSData *) clientCert
                                                    trustStoreBytes:(NSData *) trustStore
                                                       certPassword:(NSString *) certPassword
                                                 trustStorePassword:(NSString *) trustStorePassword
                                                           username:(NSString *) username
                                                           password:(NSString *) password
                                                          errorCode:(::CommoResult *)errorCode
{
    NSUInteger nsn = [cotTypes count];
    if (nsn > SIZE_T_MAX)
        return nil;
    size_t n = (size_t)nsn;
    CoTMessageType *nativeTypes = new CoTMessageType[n];
    for (size_t i = 0; i < n ; ++i) {
        CoTMessageType nativeType = SITUATIONAL_AWARENESS;
        switch (cotTypes[i].intValue) {
            case CommoCoTMessageTypeSituationalAwareness:
                nativeType = SITUATIONAL_AWARENESS;
                break;
            case CommoCoTMessageTypeChat:
                nativeType = CHAT;
                break;
        }
        nativeTypes[i] = nativeType;
    }
    const char *nativeHostname = [hostname UTF8String];
    const char *nativeCertPassword = certPassword == nil ? nullptr : [certPassword UTF8String];
    const char *nativeCaCertPassword = trustStorePassword == nil ? nullptr : [trustStorePassword UTF8String];
    const char *nativeUsername = username == nil ? nullptr : [username UTF8String];
    const char *nativePassword = password == nil ? nullptr : [password UTF8String];
    const uint8_t *nativeCert = nullptr;
    atakmap::commoncommo::CommoResult nativeErrCode;
    size_t certLen = 0;
    if (clientCert != nil) {
        nativeCert = (uint8_t *)[clientCert bytes];
        certLen = [clientCert length];
    }
    const uint8_t *nativeTrust = nullptr;
    size_t trustLen = 0;
    if (trustStore != nil) {
        nativeTrust = (uint8_t *)[trustStore bytes];
        trustLen = [trustStore length];
    }
    
    StreamingNetInterface *iface = nativeCommo->addStreamingInterface(nativeHostname,
                                                                     port,
                                                                     nativeTypes,
                                                                     n,
                                                                     nativeCert, certLen,
                                                                     nativeTrust, trustLen,
                                                                     nativeCertPassword,
                                                                     nativeCaCertPassword,
                                                                     nativeUsername,
                                                                     nativePassword,
                                                                     &nativeErrCode);
    delete[] nativeTypes;
    if (errorCode != nil)
        *errorCode = nativeResultToObjc(nativeErrCode);
    if (!iface)
        return nil;
    
    CommoStreamingNetInterfaceImpl *objcIface = [[CommoStreamingNetInterfaceImpl alloc]
                                                  initWithNativeStreamingNetInterface:iface];
    
    NSValue *nativeValue = [NSValue valueWithPointer:iface];
    [interfaceMap setObject:objcIface forKey:nativeValue];
    return objcIface;
}

-(::CommoResult) removeStreamingInterface:(id<CommoStreamingNetInterface>) iface
{
    if (![iface isKindOfClass:[CommoStreamingNetInterfaceImpl class]])
        return CommoResultIllegalArgument;
    
    CommoStreamingNetInterfaceImpl *objcPhys = (CommoStreamingNetInterfaceImpl *)iface;
    StreamingNetInterface *nativePtr = objcPhys.streamingNativeImpl;
    NSValue *nativeValue = [NSValue valueWithPointer:nativePtr];
    
    id<CommoNetInterface> idFromMap = [interfaceMap objectForKey:nativeValue];
    if (idFromMap != iface)
        // Weird??
        return CommoResultIllegalArgument;
    if (nativeCommo->removeStreamingInterface(nativePtr) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [interfaceMap removeObjectForKey:nativeValue];
    
    return CommoResultSuccess;
}

-(::CommoResult) addInterfaceStatusListener:(id<CommoInterfaceStatusListener>) listener
{
    if ([interfaceListenerMap objectForKey:listener] != nil)
        return CommoResultIllegalArgument;
    
    objcimpl::CommoInterfaceStatusListenerImpl *impl =
            new objcimpl::CommoInterfaceStatusListenerImpl(interfaceMap, listener);
    if (nativeCommo->addInterfaceStatusListener(impl) != COMMO_SUCCESS) {
        delete impl;
        return CommoResultIllegalArgument;
    }
    NSValue *nativeVal = [NSValue valueWithPointer:impl];
    [interfaceListenerMap setObject:nativeVal forKey:listener];
    return CommoResultSuccess;
}

-(::CommoResult) removeInterfaceStatusListener:(id<CommoInterfaceStatusListener>) listener
{
    NSValue *val = [interfaceListenerMap objectForKey:listener];
    if (val == nil)
        return CommoResultIllegalArgument;
    objcimpl::CommoInterfaceStatusListenerImpl *impl = (objcimpl::CommoInterfaceStatusListenerImpl *)val.pointerValue;
    
    if (nativeCommo->removeInterfaceStatusListener(impl) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [interfaceListenerMap removeObjectForKey:listener];
    delete impl;
    
    return CommoResultSuccess;
}

-(::CommoResult) addCoTMessageListener:(id<CommoCoTMessageListener>) listener
{
    if ([messageListenerMap objectForKey:listener] != nil)
        return CommoResultIllegalArgument;
    
    objcimpl::CommoCoTMessageListenerImpl *impl = new objcimpl::CommoCoTMessageListenerImpl(listener);
    if (nativeCommo->addCoTMessageListener(impl) != COMMO_SUCCESS) {
        delete impl;
        return CommoResultIllegalArgument;
    }
    NSValue *nativeVal = [NSValue valueWithPointer:impl];
    [messageListenerMap setObject:nativeVal forKey:listener];
    return CommoResultSuccess;
}

// OK or ILLEGAL_ARGUMENT if listener was not already added
-(::CommoResult) removeCoTMessageListener:(id<CommoCoTMessageListener>) listener
{
    NSValue *val = [messageListenerMap objectForKey:listener];
    if (val == nil)
        return CommoResultIllegalArgument;
    objcimpl::CommoCoTMessageListenerImpl *impl = (objcimpl::CommoCoTMessageListenerImpl *)val.pointerValue;
    
    if (nativeCommo->removeCoTMessageListener(impl) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [messageListenerMap removeObjectForKey:listener];
    delete impl;
    
    return CommoResultSuccess;
}

-(::CommoResult) addCoTSendFailureListener:(id<CommoCoTSendFailureListener>) listener
{
    if ([sendFailureListenerMap objectForKey:listener] != nil)
        return CommoResultIllegalArgument;
    
    objcimpl::CommoCoTSendFailureListenerImpl *impl = new objcimpl::CommoCoTSendFailureListenerImpl(listener);
    if (nativeCommo->addCoTSendFailureListener(impl) != COMMO_SUCCESS) {
        delete impl;
        return CommoResultIllegalArgument;
    }
    NSValue *nativeVal = [NSValue valueWithPointer:impl];
    [sendFailureListenerMap setObject:nativeVal forKey:listener];
    return CommoResultSuccess;
}

-(::CommoResult) removeCoTSendFailureListener:(id<CommoCoTSendFailureListener>) listener
{
    NSValue *val = [sendFailureListenerMap objectForKey:listener];
    if (val == nil)
        return CommoResultIllegalArgument;
    objcimpl::CommoCoTSendFailureListenerImpl *impl = (objcimpl::CommoCoTSendFailureListenerImpl *)val.pointerValue;
    
    if (nativeCommo->removeCoTSendFailureListener(impl) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [sendFailureListenerMap removeObjectForKey:listener];
    delete impl;
    
    return CommoResultSuccess;
}


-(::CommoResult) sendCoTToContactUIDs:(NSMutableArray<NSString *> *) destinations messageBytes:(NSString *) cotMessage
{
    return [self sendCoTToContactUIDs:destinations messageBytes:cotMessage viaMethod:CommoCoTSendMethodAny];
}

-(::CommoResult) sendCoTToContactUIDs:(NSMutableArray<NSString *> *) destinations messageBytes:(NSString *) cotMessage viaMethod:(CommoCoTSendMethod) method
{
    const char *nativeMessage = [cotMessage UTF8String];
    
    NSUInteger nsn = [destinations count];
    if (nsn > SIZE_T_MAX)
        return CommoResultIllegalArgument;

    size_t n = (size_t)nsn;
    const ContactUID **contacts = new const ContactUID *[n];
    const ContactUID **contactsCopy = new const ContactUID *[n];
    for (size_t i = 0; i < n; ++i) {
        const char *utf8 = [destinations[i] UTF8String];
        contacts[i] = new ContactUID((uint8_t *)utf8, strlen(utf8));
        contactsCopy[i] = contacts[i];
    }
    ContactList list(n, contacts);
    atakmap::commoncommo::CommoResult r = nativeCommo->sendCoT(&list, nativeMessage, nativeForSendMethod(method));

    ::CommoResult ret = CommoResultIllegalArgument;
    if (r == COMMO_CONTACT_GONE) {
        NSMutableArray *destCopy = [NSMutableArray arrayWithArray:destinations];
        [destinations removeAllObjects];
        
        for (size_t i = 0; i < list.nContacts; ++i) {
            size_t j;
            for (j = 0; j < n; ++j) {
                if (list.contacts[i] == contactsCopy[j])
                    break;
            }
            if (j == n)
                // problem with underlying impl as everything should be returned as-was!
                continue;
            
            [destinations addObject:destCopy[j]];
        }
        
        ret = CommoResultContactGone;
    } else if (r == COMMO_SUCCESS) {
        ret = CommoResultSuccess;
    }
    
    for (size_t i = 0; i < n; ++i)
        delete contactsCopy[i];
    delete[] contacts;
    delete[] contactsCopy;
    
    return ret;

}

-(::CommoResult) sendCoTTcpDirectToHostname:(NSString *) hostname port:(int) port messageBytes:(NSString *) cotMessage
{
    const char *nativeString = [cotMessage UTF8String];
    const char *nativeHostname = [hostname UTF8String];
    
    if (nativeCommo->sendCoTTcpDirect(nativeHostname, port, nativeString) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    return CommoResultSuccess;
}

-(::CommoResult) broadcastCoTWithMessageBytes:(NSString *) cotMessage
{
    return [self broadcastCoTWithMessageBytes:cotMessage viaMethod:CommoCoTSendMethodAny];
}

-(::CommoResult) broadcastCoTWithMessageBytes:(NSString *) cotMessage viaMethod:(CommoCoTSendMethod)method
{
    const char *nativeString = [cotMessage UTF8String];
    if (nativeCommo->broadcastCoT(nativeString, nativeForSendMethod(method)) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    return CommoResultSuccess;
}

-(::CommoResult) sendCoTControlToServer:(NSString *) server message:(NSString *) cotMessage
{
    const char *nativeStreamingId = server == nil ? nullptr : [server UTF8String];
    const char *nativeString = [cotMessage UTF8String];
    if (nativeCommo->sendCoTServerControl(nativeStreamingId, nativeString) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    return CommoResultSuccess;
}

-(::CommoResult) sendCoTToMissionDestOnServer:(NSString *) streamingRemoteId mission:(NSString *) mission messageBytes:(NSString *) cotMessage
{
    const char *nativeStreamingId = streamingRemoteId == nil ? nullptr : [streamingRemoteId UTF8String];
    const char *nativeString = [cotMessage UTF8String];
    const char *nativeMission = [mission UTF8String];
    
    if (nativeCommo->sendCoTToServerMissionDest(nativeStreamingId, nativeMission, nativeString) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    return CommoResultSuccess;
}

// Obtains a list of ContactUID strings known at this moment in time.
-(NSArray<NSString *> *) getContactList
{
    const ContactList *nativeContacts = nativeCommo->getContactList();
    NSMutableArray<NSString *> *objcStrings = [NSMutableArray arrayWithCapacity:nativeContacts->nContacts];
    for (size_t i = 0; i < nativeContacts->nContacts; ++i) {
        objcStrings[i] = [[NSString alloc] initWithBytes:nativeContacts->contacts[i]->contactUID
                                                  length:nativeContacts->contacts[i]->contactUIDLen
                                                encoding:NSUTF8StringEncoding];
    }
    nativeCommo->freeContactList(nativeContacts);
    return objcStrings;
}

-(::CommoResult) configKnownEndpointContactWithUid:(NSString *) uid callsign:(NSString *) callsign ipAddr:(NSString *) ipAddr port:(int) destPort
{
    if (uid == nil)
        return CommoResultIllegalArgument;

    const char *nativeString = [uid UTF8String];
    const char *nativeCallsign = NULL;
    const char *nativeIpAddr = NULL;
    if (callsign != nil)
        nativeCallsign = [callsign UTF8String];
    if (ipAddr != nil)
        nativeIpAddr = [ipAddr UTF8String];
    
    ContactUID contact((const uint8_t *)nativeString, [uid length]);

    if (nativeCommo->configKnownEndpointContact(&contact, nativeCallsign, nativeIpAddr, destPort) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    return CommoResultSuccess;
}

-(::CommoResult) addContactPresenceListener:(id<CommoContactPresenceListener>) listener
{
    if ([contactListenerMap objectForKey:listener] != nil)
        return CommoResultIllegalArgument;
    
    objcimpl::CommoContactPresenceListenerImpl *impl = new objcimpl::CommoContactPresenceListenerImpl(listener);
    if (nativeCommo->addContactPresenceListener(impl) != COMMO_SUCCESS) {
        delete impl;
        return CommoResultIllegalArgument;
    }
    NSValue *nativeVal = [NSValue valueWithPointer:impl];
    [contactListenerMap setObject:nativeVal forKey:listener];
    return CommoResultSuccess;
}

-(::CommoResult) removeContactPresenceListener:(id<CommoContactPresenceListener>) listener
{
    NSValue *val = [contactListenerMap objectForKey:listener];
    if (val == nil)
        return CommoResultIllegalArgument;
    objcimpl::CommoContactPresenceListenerImpl *impl = (objcimpl::CommoContactPresenceListenerImpl *)val.pointerValue;
    
    if (nativeCommo->removeContactPresenceListener(impl) != COMMO_SUCCESS)
        return CommoResultIllegalArgument;
    
    [contactListenerMap removeObjectForKey:listener];
    delete impl;
    
    return CommoResultSuccess;
}

-(::CommoResult) simpleFileTransferInit:(NSString *)localFile
                              forUpload:(bool)forUpload
                              remoteURI:(NSString *)remoteURI
                                 caCert:(NSData *)caCert
                         caCertPassword:(NSString *)caCertPassword
                                   user:(NSString *)user
                               password:(NSString *)password
                             transferId:(int *)transferId
{
    const char *fileNative = [localFile UTF8String];
    const char *uriNative = [remoteURI UTF8String];
    const char *caCertPassNative = caCertPassword == nil ? nullptr : [caCertPassword UTF8String];
    const char *userNative = user == nil ? nullptr : [user UTF8String];
    const char *passNative = password == nil ? nullptr : [password UTF8String];
    const uint8_t *caCertNative = NULL;
    size_t caCertLen = 0;
    if (caCert != nil) {
        caCertNative = (const uint8_t *)[caCert bytes];
        caCertLen = [caCert length];
    }
    
    return nativeResultToObjc(nativeCommo->simpleFileTransferInit(transferId, forUpload, uriNative, caCertNative, caCertLen, caCertPassNative, userNative, passNative, fileNative));
}

-(::CommoResult) simpleFileTransferStart:(int) xferId
{
    return nativeResultToObjc(nativeCommo->simpleFileTransferStart(xferId));
}

-(::CommoResult) sendMissionPackageInitToContacts:(NSMutableArray<NSString *> *) destinations
                                         filePath:(NSString *) filePath
                                         fileName:(NSString *) fileName
                                     transferName:(NSString *) transferName
                                       transferId:(int *) transferId
{
    const char *fpNative = [filePath UTF8String];
    const char *fnNative = [fileName UTF8String];
    const char *tNative = [transferName UTF8String];

    
    NSUInteger nsn = [destinations count];
    if (nsn > SIZE_T_MAX)
        return CommoResultIllegalArgument;
    
    size_t n = (size_t)nsn;
    const ContactUID **contacts = new const ContactUID *[n];
    const ContactUID **contactsCopy = new const ContactUID *[n];
    for (size_t i = 0; i < n; ++i) {
        const char *utf8 = [destinations[i] UTF8String];
        contacts[i] = new ContactUID((uint8_t *)utf8, strlen(utf8));
        contactsCopy[i] = contacts[i];
    }
    ContactList list(n, contacts);

    atakmap::commoncommo::CommoResult r = nativeCommo->sendMissionPackageInit(transferId, &list, fpNative, fnNative, tNative);
    
    ::CommoResult ret = CommoResultIllegalArgument;
    
    if (r == COMMO_CONTACT_GONE) {
        NSMutableArray *destCopy = [NSMutableArray arrayWithArray:destinations];
        [destinations removeAllObjects];
        
        for (size_t i = 0; i < list.nContacts; ++i) {
            size_t j;
            for (j = 0; j < n; ++j) {
                if (list.contacts[i] == contactsCopy[j])
                    break;
            }
            if (j == n)
                // problem with underlying impl as everything should be returned as-was!
                continue;
            
            [destinations addObject:destCopy[j]];
        }
        
        ret = CommoResultContactGone;
    } else if (r == COMMO_SUCCESS) {
        ret = CommoResultSuccess;
    }
    
    for (size_t i = 0; i < n; ++i)
        delete contactsCopy[i];
    delete[] contacts;
    delete[] contactsCopy;

    return ret;
}


-(::CommoResult) sendMissionPackageInitToServer:(NSString *) streamId
                                 filePath:(NSString *) filePath
                                 fileName:(NSString *) fileName
                               transferId:(int *) transferId
{
    const char *fpNative = [filePath UTF8String];
    const char *fnNative = [fileName UTF8String];
    const char *streamIdNative = [streamId UTF8String];

    atakmap::commoncommo::CommoResult r = nativeCommo->sendMissionPackageInit(transferId, streamIdNative, fpNative, fnNative);
    
    ::CommoResult ret = CommoResultIllegalArgument;
    
    if (r == COMMO_CONTACT_GONE) {
        ret = CommoResultContactGone;
    } else if (r == COMMO_SUCCESS) {
        ret = CommoResultSuccess;
    }
    
    return ret;
}

-(::CommoResult) startMissionPackageSend:(int)transferId
{
    return nativeResultToObjc(nativeCommo->sendMissionPackageStart(transferId));
}

-(NSString *)generateKeyCryptoStringWithPassword:(NSString *) password keyLen:(int) keyLen
{
    const char *pwNative = [password UTF8String];
    char *result = nativeCommo->generateKeyCryptoString(pwNative, keyLen);
    NSString *ret = nil;
    if (result) {
        ret = [[NSString alloc] initWithUTF8String:result];
        nativeCommo->freeCryptoString(result);
    }
    return ret;
}

-(NSString *) generateCSRCryptoStringWithDnEntries:(NSDictionary<NSString *, NSString *> *) dnEntries 
                                           pkeyPEM:(NSString *) pkeyPEM
                                          password:(NSString *) password
{
    const char *pwNative = [password UTF8String];
    const char *pkeyNative = [pkeyPEM UTF8String];
    
    int nEntries = [dnEntries count];
    const char **keys = new const char *[nEntries];
    const char **vals = new const char *[nEntries];
    int i = 0;
    for (NSString *key in dnEntries) {
        keys[i] = [key UTF8String];
        vals[i] = [dnEntries[key] UTF8String];
        i++;
    }

    char *result = nativeCommo->generateCSRCryptoString(keys, vals, nEntries,
                                                        pkeyNative, pwNative);

    delete[] keys;
    delete[] vals;

    NSString *ret = nil;
    if (result) {
        ret = [[NSString alloc] initWithUTF8String:result];
        nativeCommo->freeCryptoString(result);
    }
    return ret;
}

-(NSString *)generateKeystoreCryptoStringWithPEMCert:(NSString *) certPEM
                                          caCertsPEM:(NSArray<NSString *> *) caCertsPEM
                                          privKeyPEM:(NSString *) privKeyPEM
                                            password:(NSString *) password
                                        friendlyName:(NSString *) friendlyName
{
    const char *pwNative = [password UTF8String];
    const char *friendlyNameNative = [friendlyName UTF8String];
    const char *pkeyNative = [privKeyPEM UTF8String];
    const char *certNative = [certPEM UTF8String];

    int nCaCerts = [caCertsPEM count];
    const char **caCertsNative = new const char *[nCaCerts];
    int i = 0;
    for (NSString *ca in caCertsPEM) {
        caCertsNative[i] = [ca UTF8String];
        i++;
    }
    
    char *result = nativeCommo->generateKeystoreCryptoString(certNative,
                                   caCertsNative, nCaCerts, pkeyNative, pwNative,
                                   friendlyNameNative);
    delete[] caCertsNative;
    NSString *ret = nil;
    if (result) {
        ret = [[NSString alloc] initWithUTF8String:result];
        nativeCommo->freeCryptoString(result);
    }
    return ret;
}


@end

