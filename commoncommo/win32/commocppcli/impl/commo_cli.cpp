#include <sstream>
#include "commo_cli.h"
#include "loggerimpl.h"
#include "missionpackageimpl.h"
#include "simplefileioimpl.h"
#include "cotmessageioimpl.h"
#include "contactuidimpl.h"
#include "netinterfaceimpl.h"
#include "commo.h"
#include "commoimpl.h"
#include "cloudioimpl.h"
#include "commoresult.h"

#include <msclr/marshal.h>

using namespace TAK::Commo;

namespace {
    atakmap::commoncommo::CoTSendMethod methodToNative(CoTSendMethod method)
    {
        atakmap::commoncommo::CoTSendMethod m = atakmap::commoncommo::SEND_ANY;
        switch (method) {
        case CoTSendMethod::SendAny:
            m = atakmap::commoncommo::SEND_ANY;
            break;
        case CoTSendMethod::SendPointToPoint:
            m = atakmap::commoncommo::SEND_POINT_TO_POINT;
            break;
        case CoTSendMethod::SendTAKServer:
            m = atakmap::commoncommo::SEND_TAK_SERVER;
            break;
        }
        return m;
    }

    char *toUTF8(System::String ^srcString)
    {
        array<System::Byte> ^utf8 = System::Text::Encoding::UTF8->GetBytes(srcString);
        char *buf = new char[utf8->Length + 1];
        System::Runtime::InteropServices::Marshal::Copy(utf8, 0, System::IntPtr(buf), utf8->Length);
        buf[utf8->Length] = 0;
        return buf;
    }

}

namespace TAK {
    namespace Commo {
        namespace impl {

            private ref class CommoImpl
            {
            public:
                CommoImpl(TAK::Commo::ICommoLogger ^logger, System::String ^ourUID, System::String ^ourCallsign) : commo(NULL), loggerImpl(NULL), mpImpl(NULL), fioImpl(NULL)
                {
                    loggerImpl = new LoggerImpl(logger);

                    {
                        const char *uidBuf = toUTF8(ourUID);
                        atakmap::commoncommo::ContactUID uid((const uint8_t *)uidBuf, ourUID->Length);
                        const char *csBuf = toUTF8(ourCallsign);

                        commo = new atakmap::commoncommo::Commo(loggerImpl, &uid, csBuf, atakmap::commoncommo::netinterfaceenums::MODE_NAME);
                        cotListeners = gcnew System::Collections::Concurrent::ConcurrentDictionary<ICoTMessageListener ^, System::IntPtr>();
                        genDataListeners = gcnew System::Collections::Concurrent::ConcurrentDictionary<IGenericDataListener ^, System::IntPtr>();
                        sendFailureListeners = gcnew System::Collections::Concurrent::ConcurrentDictionary<ICoTSendFailureListener ^, System::IntPtr>();
                        contactListeners = gcnew System::Collections::Concurrent::ConcurrentDictionary<IContactPresenceListener ^, System::IntPtr>();
                        interfaceListeners = gcnew System::Collections::Concurrent::ConcurrentDictionary<IInterfaceStatusListener ^, System::IntPtr>();
                        cloudClients = gcnew System::Collections::Concurrent::ConcurrentDictionary<CloudClient ^, System::IntPtr>();
                        netInterfaceRegistry = gcnew InterfaceStatusListenerImpl::InterfaceRegistry();

                        delete[] uidBuf;
                        delete[] csBuf;
                    }
                }

                ~CommoImpl()
                {
                    if (commo != NULL) {
                        delete commo;
                        delete loggerImpl;
                        delete mpImpl;
                        delete fioImpl;
                        loggerImpl = NULL;
                        commo = NULL;
                        mpImpl = NULL;
                        fioImpl = NULL;

                        // Clean out all listener impls
                        for each(System::Collections::Generic::KeyValuePair<ICoTMessageListener ^, System::IntPtr> ^kvp in cotListeners) {
                            impl::CoTMessageListenerImpl *listener = (impl::CoTMessageListenerImpl *)kvp->Value.ToPointer();
                            delete listener;
                        }
                        for each(System::Collections::Generic::KeyValuePair<IGenericDataListener ^, System::IntPtr> ^kvp in genDataListeners) {
                            impl::GenericDataListenerImpl *listener = (impl::GenericDataListenerImpl *)kvp->Value.ToPointer();
                            delete listener;
                        }
                        for each(System::Collections::Generic::KeyValuePair<ICoTSendFailureListener ^, System::IntPtr> ^kvp in sendFailureListeners) {
                            impl::CoTSendFailureListenerImpl *listener = (impl::CoTSendFailureListenerImpl *)kvp->Value.ToPointer();
                            delete listener;
                        }
                        for each(System::Collections::Generic::KeyValuePair<IContactPresenceListener ^, System::IntPtr> ^kvp in contactListeners) {
                            impl::ContactPresenceListenerImpl *listener = (impl::ContactPresenceListenerImpl *)kvp->Value.ToPointer();
                            delete listener;
                        }
                        for each(System::Collections::Generic::KeyValuePair<IInterfaceStatusListener ^, System::IntPtr> ^kvp in interfaceListeners) {
                            impl::InterfaceStatusListenerImpl *listener = (impl::InterfaceStatusListenerImpl *)kvp->Value.ToPointer();
                            delete listener;
                        }
                        for each(System::Collections::Generic::KeyValuePair<CloudClient ^, System::IntPtr> ^kvp in cloudClients)
                        {
                            impl::CloudIOImpl *io = (impl::CloudIOImpl *)kvp->Value.ToPointer();
                            delete io;
                        }

                    }

                    this->!CommoImpl();
                }
                !CommoImpl()
                {
                }

                atakmap::commoncommo::Commo *commo;
                LoggerImpl *loggerImpl;
                MissionPackageIOImpl *mpImpl;
                SimpleFileIOImpl *fioImpl;

                System::Collections::Concurrent::ConcurrentDictionary<ICoTMessageListener ^, System::IntPtr> ^cotListeners;
                System::Collections::Concurrent::ConcurrentDictionary<IGenericDataListener ^, System::IntPtr> ^genDataListeners;
                System::Collections::Concurrent::ConcurrentDictionary<ICoTSendFailureListener ^, System::IntPtr> ^sendFailureListeners;
                System::Collections::Concurrent::ConcurrentDictionary<IContactPresenceListener ^, System::IntPtr> ^contactListeners;
                System::Collections::Concurrent::ConcurrentDictionary<IInterfaceStatusListener ^, System::IntPtr> ^interfaceListeners;
                System::Collections::Concurrent::ConcurrentDictionary<CloudClient ^, System::IntPtr> ^cloudClients;
                InterfaceStatusListenerImpl::InterfaceRegistry ^netInterfaceRegistry;
            };


        }
    }
}

CommoResult TAK::Commo::impl::nativeToCLI(atakmap::commoncommo::CommoResult res)
{
    CommoResult ret;
    switch (res)
    {
    case atakmap::commoncommo::COMMO_SUCCESS:
        ret = CommoResult::CommoSuccess;
        break;
    case atakmap::commoncommo::COMMO_ILLEGAL_ARGUMENT:
        ret = CommoResult::CommoIllegalArgument;
        break;
    case atakmap::commoncommo::COMMO_CONTACT_GONE:
        ret = CommoResult::CommoContactGone;
        break;
    case atakmap::commoncommo::COMMO_INVALID_CACERT:
        ret = CommoResult::CommoInvalidTruststore;
        break;
    case atakmap::commoncommo::COMMO_INVALID_CACERT_PASSWORD:
        ret = CommoResult::CommoInvalidTruststorePassword;
        break;
    case atakmap::commoncommo::COMMO_INVALID_CERT:
        ret = CommoResult::CommoInvalidCert;
        break;
    case atakmap::commoncommo::COMMO_INVALID_CERT_PASSWORD:
        ret = CommoResult::CommoInvalidCertPassword;
    }
    return ret;
}



Commo::Commo(ICommoLogger ^logger, System::String ^ourUID, System::String ^ourCallsign) : impl(nullptr)
{
    this->impl = gcnew impl::CommoImpl(logger, ourUID, ourCallsign);
    m_InboundInterfaceLock = gcnew Object();
}

Commo::~Commo()
{
    if (this->impl != nullptr) {
        this->impl->commo->shutdown();
        delete impl;
        this->impl = nullptr;
    }
    this->!Commo();
}

Commo::!Commo()
{
}


CommoResult Commo::SetupMissionPackageIO(IMissionPackageIO ^missionPackageIO)
{
    impl::MissionPackageIOImpl *newMPImpl = new impl::MissionPackageIOImpl(missionPackageIO);
    CommoResult ret = impl::nativeToCLI(impl->commo->setupMissionPackageIO(newMPImpl));
    if (ret == CommoResult::CommoSuccess)
        impl->mpImpl = newMPImpl;
    else
        delete newMPImpl;
    return ret;
}

CommoResult Commo::EnableSimpleFileIO(ISimpleFileIO ^simpleIO)
{
    impl::SimpleFileIOImpl *newFIOImpl = new impl::SimpleFileIOImpl(simpleIO);
    CommoResult ret = impl::nativeToCLI(impl->commo->enableSimpleFileIO(newFIOImpl));
    if (ret == CommoResult::CommoSuccess)
        impl->fioImpl = newFIOImpl;
    else
        delete newFIOImpl;
    return ret;
}


void Commo::Shutdown()
{
    impl->commo->shutdown();
}


void Commo::SetCallsign(System::String ^callsign)
{
    const char *buf = toUTF8(callsign);
    impl->commo->setCallsign(buf);
    delete[] buf;
}

CommoResult Commo::SetCryptoKeys(array<System::Byte> ^auth, array<System::Byte> ^crypt)
{
    if ((auth == nullptr) ^ (crypt == nullptr))
        return CommoResult::CommoIllegalArgument;
    if (auth != nullptr && (auth->Length != 32 || crypt->Length != 32))
        return CommoResult::CommoIllegalArgument;

    pin_ptr<System::Byte> pinAuth = nullptr;
    pin_ptr<System::Byte> pinCrypt= nullptr;
    const uint8_t *nativeAuth = NULL;
    const uint8_t *nativeCrypt = NULL;

    if (auth != nullptr) {
        pinAuth = &auth[0];
        pinCrypt = &crypt[0];

        nativeAuth = (const uint8_t *)pinAuth;
        nativeCrypt = (const uint8_t *)pinCrypt;
    }
    CommoResult ret = impl::nativeToCLI(impl->commo->setCryptoKeys(nativeAuth, nativeCrypt));
    return ret;
}

void Commo::SetEnableAddressReuse(bool en)
{
    impl->commo->setEnableAddressReuse(en);
}

void Commo::SetTTL(int ttl)
{
    impl->commo->setTTL(ttl);
}

void Commo::SetUdpNoDataTimeout(int seconds)
{
    impl->commo->setUdpNoDataTimeout(seconds);
}

void Commo::SetTcpConnTimeout(int seconds)
{
    impl->commo->setTcpConnTimeout(seconds);
}

CommoResult Commo::SetMissionPackageLocalPort(int localWebPort)
{
    return impl::nativeToCLI(impl->commo->setMissionPackageLocalPort(localWebPort));
}

CommoResult Commo::SetMissionPackageLocalHttpsParams(int localWebPort,
    array<System::Byte> ^certificate, System::String ^certPass)
{
    if (certPass == nullptr)
        return CommoResult::CommoInvalidCertPassword;
    msclr::interop::marshal_context mctx;
    const char *certPassNative = mctx.marshal_as<const char *>(certPass);
    pin_ptr<System::Byte> certificateNative = &certificate[0];
    size_t len = certificate->Length;

    return impl::nativeToCLI(
        impl->commo->setMissionPackageLocalHttpsParams(localWebPort, 
            certificateNative, len, certPassNative));
}


void Commo::SetMissionPackageViaServerEnabled(bool enabled)
{
    impl->commo->setMissionPackageViaServerEnabled(enabled);
}

CommoResult Commo::SetMissionPackageHttpPort(int serverPort)
{
     return impl::nativeToCLI(impl->commo->setMissionPackageHttpPort(serverPort));
}

CommoResult Commo::SetMissionPackageHttpsPort(int serverPort)
{
     return impl::nativeToCLI(impl->commo->setMissionPackageHttpsPort(serverPort));
}

void Commo::SetStreamMonitorEnabled(bool enable)
{
    impl->commo->setStreamMonitorEnabled(enable);
}

CommoResult Commo::SetMissionPackageNumTries(int nTries)
{
    return impl::nativeToCLI(impl->commo->setMissionPackageNumTries(nTries));
}

CommoResult Commo::SetMissionPackageConnTimeout(int seconds)
{
    return impl::nativeToCLI(impl->commo->setMissionPackageConnTimeout(seconds));
}

CommoResult Commo::SetMissionPackageTransferTimeout(int seconds)
{
    return impl::nativeToCLI(impl->commo->setMissionPackageTransferTimeout(seconds));
}

int Commo::GetBroadcastProto()
{
    return impl->commo->getBroadcastProto();
}

PhysicalNetInterface ^Commo::AddBroadcastInterface(System::String ^ifaceName, array<CoTMessageType> ^types, System::String ^mcastAddr, int destPort)
{
    msclr::interop::marshal_context mctx;
    const char *ifaceNameNative = mctx.marshal_as<const char *>(ifaceName);

    atakmap::commoncommo::HwAddress hwAddrNative((const uint8_t *)ifaceNameNative, strlen(ifaceNameNative));
    int sn = types->Length;
    if (sn < 0)
        return nullptr;

    size_t n = (size_t)sn;
    atakmap::commoncommo::CoTMessageType *nativeTypes = new atakmap::commoncommo::CoTMessageType[n];
    for (size_t i = 0; i < n; ++i) {
        atakmap::commoncommo::CoTMessageType nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
        switch (types[i]) {
        case CoTMessageType::SituationalAwareness:
            nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
            break;
        case CoTMessageType::Chat:
            nativeType = atakmap::commoncommo::CHAT;
            break;
        }
        nativeTypes[i] = nativeType;
    }

    const char *nativeMcast = mctx.marshal_as<const char *>(mcastAddr);
    atakmap::commoncommo::PhysicalNetInterface *iface = impl->commo->addBroadcastInterface(&hwAddrNative, nativeTypes, n, nativeMcast, destPort);
    delete[] nativeTypes;
    if (!iface)
        return nullptr;

    PhysicalNetInterface ^cliIface = gcnew PhysicalNetInterface(iface, ifaceName);

    impl->netInterfaceRegistry->TryAdd(System::IntPtr(iface), cliIface);

    return cliIface;
}

PhysicalNetInterface ^Commo::AddBroadcastInterface(
    array<CoTMessageType> ^types,
    System::String ^unicastAddr,
    int destPort)
{
    int sn = types->Length;
    if (sn < 0)
        return nullptr;

    size_t n = (size_t)sn;
    atakmap::commoncommo::CoTMessageType *nativeTypes = new atakmap::commoncommo::CoTMessageType[n];
    for (size_t i = 0; i < n; ++i) {
        atakmap::commoncommo::CoTMessageType nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
        switch (types[i]) {
        case CoTMessageType::SituationalAwareness:
            nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
            break;
        case CoTMessageType::Chat:
            nativeType = atakmap::commoncommo::CHAT;
            break;
        }
        nativeTypes[i] = nativeType;
    }

    msclr::interop::marshal_context mctx;
    const char *nativeAddr = mctx.marshal_as<const char *>(unicastAddr);
    atakmap::commoncommo::PhysicalNetInterface *iface = impl->commo->addBroadcastInterface(nativeTypes, n, nativeAddr, destPort);
    delete[] nativeTypes;
    if (!iface)
        return nullptr;

    PhysicalNetInterface ^cliIface = gcnew PhysicalNetInterface(iface, gcnew System::String(""));

    impl->netInterfaceRegistry->TryAdd(System::IntPtr(iface), cliIface);

    return cliIface;
}


CommoResult Commo::RemoveBroadcastInterface(PhysicalNetInterface ^iface)
{
    atakmap::commoncommo::PhysicalNetInterface *phys = iface->impl;
    NetInterface ^lookupIface;

    if (!impl->netInterfaceRegistry->TryGetValue(System::IntPtr(phys), lookupIface) || lookupIface != iface)
        // Weird??
        return CommoResult::CommoIllegalArgument;
    if (impl->commo->removeBroadcastInterface(phys) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    TAK::Commo::NetInterface^ value = nullptr;
    impl->netInterfaceRegistry->TryRemove(System::IntPtr(phys), value);

    return CommoResult::CommoSuccess;
}


PhysicalNetInterface ^Commo::AddInboundInterface(System::String ^ifaceName, int port, array<System::String ^> ^mcastAddrs,
                                                 bool forGenericData)
{
    msclr::interop::marshal_context mctx;

    const char *ifaceNameNative = mctx.marshal_as<const char *>(ifaceName);

    atakmap::commoncommo::HwAddress hwAddrNative((const uint8_t *)ifaceNameNative, strlen(ifaceNameNative));
    int sn = mcastAddrs->Length;
    if (sn < 0)
        return nullptr;

    size_t n = (size_t)sn;
    const char **nativeMcastAddrs = new const char *[n];
    for (size_t i = 0; i < n; ++i) {
        nativeMcastAddrs[i] = mctx.marshal_as<const char *>(mcastAddrs[i]);
    }

    System::Threading::Monitor::Enter(m_InboundInterfaceLock);
    atakmap::commoncommo::PhysicalNetInterface *iface = impl->commo->addInboundInterface(&hwAddrNative, port, nativeMcastAddrs, n, forGenericData);
    delete[] nativeMcastAddrs;
    if (!iface) {
        System::Threading::Monitor::Exit(m_InboundInterfaceLock);
        return nullptr;
    }

    PhysicalNetInterface ^cliIface = gcnew PhysicalNetInterface(iface, ifaceName);
    impl->netInterfaceRegistry->TryAdd(System::IntPtr(iface), cliIface);
    System::Threading::Monitor::Exit(m_InboundInterfaceLock);

    return cliIface;
}

[System::Runtime::ExceptionServices::HandleProcessCorruptedStateExceptions]
CommoResult Commo::RemoveInboundInterface(PhysicalNetInterface ^iface)
{
    atakmap::commoncommo::PhysicalNetInterface *phys = iface->impl;
    NetInterface ^lookupIface;

    atakmap::commoncommo::CommoResult result;
    System::Threading::Monitor::Enter(m_InboundInterfaceLock);
    if (!impl->netInterfaceRegistry->TryGetValue(System::IntPtr(phys), lookupIface) || lookupIface != iface) {
        // Weird??
        System::Threading::Monitor::Exit(m_InboundInterfaceLock);
        return CommoResult::CommoIllegalArgument;
    }

    result = impl->commo->removeInboundInterface(phys);
    if (result == atakmap::commoncommo::COMMO_SUCCESS) {
        TAK::Commo::NetInterface^ value = nullptr;
        impl->netInterfaceRegistry->TryRemove(System::IntPtr(phys), value);
    }
    System::Threading::Monitor::Exit(m_InboundInterfaceLock);

    if (result != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    return CommoResult::CommoSuccess;
}


TcpInboundNetInterface ^Commo::AddTcpInboundInterface(int port)
{
    System::Threading::Monitor::Enter(m_InboundInterfaceLock);
    atakmap::commoncommo::TcpInboundNetInterface *iface = impl->commo->addTcpInboundInterface(port);
    if (!iface) {
        System::Threading::Monitor::Exit(m_InboundInterfaceLock);
        return nullptr;
    }

    TcpInboundNetInterface ^cliIface = gcnew TcpInboundNetInterface(iface, port);
    impl->netInterfaceRegistry->TryAdd(System::IntPtr(iface), cliIface);

    System::Threading::Monitor::Exit(m_InboundInterfaceLock);

    return cliIface;
}

[System::Runtime::ExceptionServices::HandleProcessCorruptedStateExceptions]
CommoResult Commo::RemoveTcpInboundInterface(TcpInboundNetInterface ^iface)
{
    atakmap::commoncommo::TcpInboundNetInterface *phys = iface->impl;
    NetInterface ^lookupIface;


    atakmap::commoncommo::CommoResult result;
    System::Threading::Monitor::Enter(m_InboundInterfaceLock);
    if (!impl->netInterfaceRegistry->TryGetValue(System::IntPtr(phys), lookupIface) || lookupIface != iface) {
        // Weird??
        System::Threading::Monitor::Exit(m_InboundInterfaceLock);
        return CommoResult::CommoIllegalArgument;
    }

    result = impl->commo->removeTcpInboundInterface(phys);

    if (result == atakmap::commoncommo::COMMO_SUCCESS) {
        TAK::Commo::NetInterface^ value = nullptr;
        impl->netInterfaceRegistry->TryRemove(System::IntPtr(phys), value);
    }
    System::Threading::Monitor::Exit(m_InboundInterfaceLock);

    if (result != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    return CommoResult::CommoSuccess;
}


StreamingNetInterface ^Commo::AddStreamingInterface(System::String ^hostname, int port,
                                             array<CoTMessageType> ^types,
                                             array<System::Byte> ^clientCert,
                                             array<System::Byte> ^caCert,
                                             System::String ^certPassword,
                                             System::String ^caCertPassword,
                                             System::String ^username,
                                             System::String ^password,
                                             CommoResult %errCode)
{
    const uint8_t *nativeClientCert = NULL;
    size_t clientCertLen = 0;
    const uint8_t *nativeCACert = NULL;
    size_t caCertLen = 0;
    const char *nativeHostname = NULL;
    const char *nativeCertPassword = NULL;
    const char *nativeCaCertPassword = NULL;
    const char *nativeUsername = NULL;
    const char *nativePassword = NULL;
    atakmap::commoncommo::CommoResult nativeErr;

    
    msclr::interop::marshal_context mctx;
    pin_ptr<System::Byte> pinClientCert = nullptr;
    pin_ptr<System::Byte> pinCACert = nullptr;

    if (hostname)
        nativeHostname = mctx.marshal_as<const char *>(hostname);

    if (clientCert) {
        pinClientCert = &clientCert[0];
        nativeClientCert = (const uint8_t *)pinClientCert;
        clientCertLen = clientCert->Length;
    }
    if (caCert) {
        pinCACert = &caCert[0];
        nativeCACert = (const uint8_t *)pinCACert;
        caCertLen = caCert->Length;
    }
    if (certPassword)
        nativeCertPassword = mctx.marshal_as<const char *>(certPassword);
    if (caCertPassword)
        nativeCaCertPassword = mctx.marshal_as<const char *>(caCertPassword);
    if (username)
        nativeUsername = mctx.marshal_as<const char *>(username);
    if (password)
        nativePassword = mctx.marshal_as<const char *>(password);

    int sn = types->Length;
    if (sn < 0) {
        errCode = CommoResult::CommoIllegalArgument;
        return nullptr;
    }

    size_t n = (size_t)sn;
    atakmap::commoncommo::CoTMessageType *nativeTypes = new atakmap::commoncommo::CoTMessageType[n];
    for (size_t i = 0; i < n; ++i) {
        atakmap::commoncommo::CoTMessageType nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
        switch (types[i]) {
        case CoTMessageType::SituationalAwareness:
            nativeType = atakmap::commoncommo::SITUATIONAL_AWARENESS;
            break;
        case CoTMessageType::Chat:
            nativeType = atakmap::commoncommo::CHAT;
            break;
        }
        nativeTypes[i] = nativeType;
    }

    atakmap::commoncommo::StreamingNetInterface *iface = impl->commo->addStreamingInterface(nativeHostname, port, nativeTypes, n, nativeClientCert, clientCertLen, nativeCACert, caCertLen, nativeCertPassword, nativeCaCertPassword, nativeUsername, nativePassword, &nativeErr);
    delete[] nativeTypes;
    errCode = impl::nativeToCLI(nativeErr);
    if (!iface)
        return nullptr;

    StreamingNetInterface ^cliIface = gcnew StreamingNetInterface(iface, gcnew System::String(iface->remoteEndpointId, 0, iface->remoteEndpointLen));
    impl->netInterfaceRegistry->TryAdd(System::IntPtr(iface), cliIface);

    return cliIface;
}

CommoResult Commo::RemoveStreamingInterface(StreamingNetInterface ^iface)
{
    atakmap::commoncommo::StreamingNetInterface *stream = iface->impl;
    NetInterface ^lookupIface;

    if (!impl->netInterfaceRegistry->TryGetValue(System::IntPtr(stream), lookupIface) || lookupIface != iface)
        // Weird??
        return CommoResult::CommoIllegalArgument;
    if (impl->commo->removeStreamingInterface(stream) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    TAK::Commo::NetInterface^ value = nullptr;
    impl->netInterfaceRegistry->TryRemove(System::IntPtr(stream), value);

    return CommoResult::CommoSuccess;
}


CommoResult Commo::AddInterfaceStatusListener(IInterfaceStatusListener ^listener)
{
    if (impl->interfaceListeners->ContainsKey(listener))
        return CommoResult::CommoIllegalArgument;

    impl::InterfaceStatusListenerImpl *listenerNative = new impl::InterfaceStatusListenerImpl(impl->netInterfaceRegistry, listener);
    if (impl->commo->addInterfaceStatusListener(listenerNative) != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerNative;
        return CommoResult::CommoIllegalArgument;
    }
    impl->interfaceListeners->TryAdd(listener, System::IntPtr(listenerNative));
    return CommoResult::CommoSuccess;
}

CommoResult Commo::RemoveInterfaceStatusListener(IInterfaceStatusListener ^listener)
{
    System::IntPtr listenerNativePtr;
    if (!impl->interfaceListeners->TryGetValue(listener, listenerNativePtr))
        return CommoResult::CommoIllegalArgument;

    impl::InterfaceStatusListenerImpl *listenerNative = (impl::InterfaceStatusListenerImpl *)listenerNativePtr.ToPointer();

    if (impl->commo->removeInterfaceStatusListener(listenerNative) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    System::IntPtr value;
    impl->interfaceListeners->TryRemove(listener, value);
    delete listenerNative;

    return CommoResult::CommoSuccess;
}


CommoResult Commo::AddCoTMessageListener(ICoTMessageListener ^listener)
{
    if (impl->cotListeners->ContainsKey(listener))
        return CommoResult::CommoIllegalArgument;

    impl::CoTMessageListenerImpl *listenerImpl = new impl::CoTMessageListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addCoTMessageListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->cotListeners->TryAdd(listener, System::IntPtr(listenerImpl));
    return CommoResult::CommoSuccess;
}

CommoResult Commo::RemoveCoTMessageListener(ICoTMessageListener ^listener)
{
    System::IntPtr ptr;
    if (!impl->cotListeners->TryGetValue(listener, ptr))
        return CommoResult::CommoIllegalArgument;

    impl::CoTMessageListenerImpl *nativePtr = (impl::CoTMessageListenerImpl *)ptr.ToPointer();
    atakmap::commoncommo::CommoResult ret = impl->commo->removeCoTMessageListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;
    
    System::IntPtr value;
    impl->cotListeners->TryRemove(listener, value);
    delete nativePtr;
    return CommoResult::CommoSuccess;
}

CommoResult Commo::AddGenericDataListener(IGenericDataListener ^listener)
{
    if (impl->genDataListeners->ContainsKey(listener))
        return CommoResult::CommoIllegalArgument;

    impl::GenericDataListenerImpl *listenerImpl = new impl::GenericDataListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addGenericDataListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->genDataListeners->TryAdd(listener, System::IntPtr(listenerImpl));
    return CommoResult::CommoSuccess;
}

CommoResult Commo::RemoveGenericDataListener(IGenericDataListener ^listener)
{
    System::IntPtr ptr;
    if (!impl->genDataListeners->TryGetValue(listener, ptr))
        return CommoResult::CommoIllegalArgument;

    impl::GenericDataListenerImpl *nativePtr = (impl::GenericDataListenerImpl *)ptr.ToPointer();
    atakmap::commoncommo::CommoResult ret = impl->commo->removeGenericDataListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    System::IntPtr value;
    impl->genDataListeners->TryRemove(listener, value);
    delete nativePtr;
    return CommoResult::CommoSuccess;
}

CommoResult Commo::AddCoTSendFailureListener(ICoTSendFailureListener^ listener)
{
    if (impl->sendFailureListeners->ContainsKey(listener))
        return CommoResult::CommoIllegalArgument;

    impl::CoTSendFailureListenerImpl *listenerImpl = new impl::CoTSendFailureListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addCoTSendFailureListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->sendFailureListeners->TryAdd(listener, System::IntPtr(listenerImpl));
    return CommoResult::CommoSuccess;
}

CommoResult Commo::RemoveCoTSendFailureListener(ICoTSendFailureListener ^listener)
{
    System::IntPtr ptr;
    if (!impl->sendFailureListeners->TryGetValue(listener, ptr))
        return CommoResult::CommoIllegalArgument;

    impl::CoTSendFailureListenerImpl *nativePtr = (impl::CoTSendFailureListenerImpl *)ptr.ToPointer();
    atakmap::commoncommo::CommoResult ret = impl->commo->removeCoTSendFailureListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    System::IntPtr value;
    impl->sendFailureListeners->TryRemove(listener, value);
    delete nativePtr;
    return CommoResult::CommoSuccess;
}

CommoResult Commo::SendCoT(System::Collections::Generic::List<System::String ^> ^destinations, System::String ^cotMessage)
{
    return SendCoT(destinations, cotMessage, CoTSendMethod::SendAny);
}

CommoResult Commo::SendCoT(System::Collections::Generic::List<System::String ^> ^destinations, System::String ^cotMessage, CoTSendMethod method)
{
    char *buf = toUTF8(cotMessage);

    const int n = destinations->Count;
    const atakmap::commoncommo::ContactUID **contacts = new const atakmap::commoncommo::ContactUID*[n];
    const atakmap::commoncommo::ContactUID **contactsCopy = new const atakmap::commoncommo::ContactUID*[n];
    for (int i = 0; i < n; ++i) {
        const char *cbuf = toUTF8(destinations[i]);
        contacts[i] = new atakmap::commoncommo::ContactUID((const uint8_t *)cbuf, destinations[i]->Length);
        contactsCopy[i] = contacts[i];
    }
    atakmap::commoncommo::ContactList list(n, contacts);

    CommoResult ret = impl::nativeToCLI(impl->commo->sendCoT(&list, buf, methodToNative(method)));

    if (ret == CommoResult::CommoContactGone) {
        destinations->Clear();

        for (size_t i = 0; i < list.nContacts; ++i) {
            destinations->Add(gcnew System::String((const char *)list.contacts[i]->contactUID, 0, list.contacts[i]->contactUIDLen, System::Text::Encoding::UTF8));
        }
    }

    for (int i = 0; i < n; ++i) {
        delete[] contactsCopy[i]->contactUID;
        delete contactsCopy[i];
    }
    delete[] contacts;
    delete[] contactsCopy;
    delete[] buf;
    return ret;
}

CommoResult Commo::BroadcastCoT(System::String ^cotMessage)
{
    return BroadcastCoT(cotMessage, CoTSendMethod::SendAny);
}

CommoResult Commo::BroadcastCoT(System::String ^cotMessage, CoTSendMethod method)
{
    const char *buf = toUTF8(cotMessage);
    CommoResult r = impl::nativeToCLI(impl->commo->broadcastCoT(buf, methodToNative(method)));
    delete[] buf;
    return r;
}

CommoResult Commo::SendCoTTcpDirect(System::String ^host, int port, System::String ^cotMessage)
{
    msclr::interop::marshal_context mctx;
    const char *hostaddr = mctx.marshal_as<const char *>(host);
    const char *buf = toUTF8(cotMessage);
    CommoResult r = impl::nativeToCLI(impl->commo->sendCoTTcpDirect(hostaddr, port, buf));
    delete[] buf;
    return r;
}

CommoResult Commo::SendCoTServerControl(System::String ^streamingRemoteId,
    System::String ^cotMessage)
{
    msclr::interop::marshal_context mctx;
    const char *buf = toUTF8(cotMessage);
    const char *streamid = NULL;
    if (streamingRemoteId != nullptr)
        streamid = mctx.marshal_as<const char *>(streamingRemoteId);
    CommoResult r= impl::nativeToCLI(impl->commo->sendCoTServerControl(streamid, buf));
    delete[] buf;
    return r;
}

CommoResult Commo::SendCoTToServerMissionDest(System::String ^streamingRemoteId,
    System::String ^mission,
    System::String ^cotMessage)
{
    msclr::interop::marshal_context mctx;
    const char *missionNative = toUTF8(mission);
    const char *buf = toUTF8(cotMessage);
    
    const char *streamid = NULL;
    if (streamingRemoteId != nullptr)
        streamid = mctx.marshal_as<const char *>(streamingRemoteId);
    CommoResult r = impl::nativeToCLI(impl->commo->sendCoTToServerMissionDest(streamid, missionNative, buf));
    delete[] buf;
    delete[] missionNative;
    return r;
}

CommoResult Commo::SimpleFileTransferInit(int % xferId,
                                       bool forUpload,
                                       System::String ^remoteURI,
                                       array<System::Byte> ^caCert,
                                       System::String ^caCertPassword,
                                       System::String ^user,
                                       System::String ^password,
                                       System::String ^localFile)
{
    int nativeId = 0;
    msclr::interop::marshal_context mctx;
    
    const char *remoteURINative = mctx.marshal_as<const char *>(remoteURI);
    const char *caCertPasswordNative = caCertPassword != nullptr ? 
                        mctx.marshal_as<const char *>(caCertPassword) :
                        NULL;
    const char *userNative = user != nullptr ? 
                        mctx.marshal_as<const char *>(user) :
                        NULL;
    const char *passNative = password != nullptr ? 
                        mctx.marshal_as<const char *>(password) :
                        NULL;
    const char *localFileNative = mctx.marshal_as<const char *>(localFile);

    pin_ptr<System::Byte> pinCACert = nullptr;
    const uint8_t *nativeCACert = NULL;
    size_t caCertLen = 0;
    if (caCert != nullptr) {
        pinCACert = &caCert[0];
        nativeCACert = (const uint8_t *)pinCACert;
        caCertLen = caCert->Length;
    }
    
    CommoResult rc = impl::nativeToCLI(impl->commo->simpleFileTransferInit(
        &nativeId,
        forUpload,
        remoteURINative,
        nativeCACert,
        caCertLen,
        caCertPasswordNative,
        userNative,
        passNative,
        localFileNative
    ));
    
    xferId = nativeId;
    return rc;
}

CommoResult Commo::SimpleFileTransferStart(int xferId)
{
    return impl::nativeToCLI(impl->commo->simpleFileTransferStart(xferId));
}

CommoResult Commo::CreateCloudClient(CloudClient ^%client,
    ICloudIO ^io,
    CloudIOProtocol proto,
    System::String ^host,
    int port,
    System::String ^basePath,
    System::String ^user,
    System::String ^password,
    array<System::Byte> ^caCerts,
    System::String ^caCertsPassword)
{
    msclr::interop::marshal_context mctx;
    atakmap::commoncommo::CloudIOProtocol protoNative;
    switch (proto) {
    case CloudIOProtocol::Http:
        protoNative = atakmap::commoncommo::CloudIOProtocol::CLOUDIO_PROTO_HTTP;
        break;
    case CloudIOProtocol::Https:
        protoNative = atakmap::commoncommo::CloudIOProtocol::CLOUDIO_PROTO_HTTPS;
        break;
    case CloudIOProtocol::Ftp:
        protoNative = atakmap::commoncommo::CloudIOProtocol::CLOUDIO_PROTO_FTP;
        break;
    case CloudIOProtocol::Ftps:
        protoNative = atakmap::commoncommo::CloudIOProtocol::CLOUDIO_PROTO_FTPS;
        break;
    }

    const char *hostBuf = mctx.marshal_as<const char *>(host);
    const char *basePathBuf = mctx.marshal_as<const char *>(basePath);
    const char *userBuf = NULL;
    if (user != nullptr)
        userBuf = mctx.marshal_as<const char *>(user);
    const char *passwordBuf = NULL;
    if (password != nullptr)
        passwordBuf = mctx.marshal_as<const char *>(password);
    const char *caCertsPasswordBuf = NULL;
    if (caCertsPassword != nullptr)
        caCertsPasswordBuf = mctx.marshal_as<const char *>(caCertsPassword);

    pin_ptr<System::Byte> pinCACerts = nullptr;
    const uint8_t *nativeCACerts = NULL;
    size_t caCertsLen = 0;
    if (caCerts != nullptr) {
        pinCACerts = &caCerts[0];
        nativeCACerts = (const uint8_t *)pinCACerts;
        caCertsLen = caCerts->Length;
    }
    
    impl::CloudIOImpl *ioimpl = new impl::CloudIOImpl(io);

    atakmap::commoncommo::CloudClient *nativeClient = NULL;
    atakmap::commoncommo::CommoResult r = impl->commo->createCloudClient(
                                    &nativeClient, ioimpl, 
                                    protoNative, hostBuf,
                                    port, basePathBuf, userBuf,
                                    passwordBuf, nativeCACerts,
                                    caCertsLen, caCertsPasswordBuf);
 
    if (r == atakmap::commoncommo::CommoResult::COMMO_SUCCESS) {
        CloudClient ^clientCLI = gcnew CloudClient(nativeClient);
        impl->cloudClients->TryAdd(clientCLI, System::IntPtr(ioimpl));
        client = clientCLI;
        return CommoResult::CommoSuccess;
    } else {
        delete ioimpl;
        return impl::nativeToCLI(r);
    }
}

CommoResult Commo::DestroyCloudClient(CloudClient ^client)
{
    System::IntPtr ioimplptr;

    if (!impl->cloudClients->TryGetValue(client, ioimplptr))
        return CommoResult::CommoIllegalArgument;
    impl::CloudIOImpl *ioimpl = (impl::CloudIOImpl *)ioimplptr.ToPointer();
    impl->commo->destroyCloudClient(client->impl);
    client->impl = NULL;
    delete ioimpl;
    
    impl->cloudClients->TryRemove(client, ioimplptr);
    return CommoResult::CommoSuccess;
}

CommoResult Commo::SendMissionPackageInit(int % xferId,
                               System::Collections::Generic::List<System::String ^> ^destinations,
                               System::String ^filePath,
                               System::String ^fileName,
                               System::String ^name)
{
    int nativeId = 0;
    msclr::interop::marshal_context mctx;
    const char *filePathBuf = mctx.marshal_as<const char *>(filePath);
    array<System::Byte> ^utfFileName = System::Text::Encoding::UTF8->GetBytes(fileName + "\0");
    pin_ptr<System::Byte> utfFileNamePin = &utfFileName[0];
    const char *fileNameBuf = (const char *)utfFileNamePin;

    array<System::Byte> ^utfName = System::Text::Encoding::UTF8->GetBytes(name + "\0");
    pin_ptr<System::Byte> utfNamePin = &utfName[0];
    const char *nameBuf = (const char *)utfNamePin;

    size_t n = destinations->Count;
    const atakmap::commoncommo::ContactUID **contacts = new const atakmap::commoncommo::ContactUID*[n];
    const atakmap::commoncommo::ContactUID **contactsCopy = new const atakmap::commoncommo::ContactUID*[n];
    for (size_t i = 0; i < n; ++i) {
        const char *cbuf = toUTF8(destinations[i]);
        contacts[i] = new atakmap::commoncommo::ContactUID((const uint8_t *)cbuf, destinations[i]->Length);
        contactsCopy[i] = contacts[i];
    }

    atakmap::commoncommo::ContactList contactList(n, contacts);
    CommoResult ret = impl::nativeToCLI(impl->commo->sendMissionPackageInit(&nativeId, &contactList, filePathBuf, fileNameBuf, nameBuf));

    if (ret == CommoResult::CommoContactGone) {
        destinations->Clear();

        for (size_t i = 0; i < contactList.nContacts; ++i) {
            destinations->Add(gcnew System::String((const char *)contactList.contacts[i]->contactUID, 0, contactList.contacts[i]->contactUIDLen, System::Text::Encoding::UTF8));
        }
    }

    for (size_t i = 0; i < n; ++i) {
        delete[] contactsCopy[i]->contactUID;
        delete contactsCopy[i];
    }
    delete[] contacts;
    delete[] contactsCopy;
    xferId = nativeId;

    return ret;
}


CommoResult Commo::SendMissionPackageInit(int % xferId,
    System::String ^streamingRemoteId,
    System::String ^filePath,
    System::String ^fileName)
{
    int nativeId = 0;
    msclr::interop::marshal_context mctx;
    const char *filePathBuf = mctx.marshal_as<const char *>(filePath);
    array<System::Byte> ^utfFileName = System::Text::Encoding::UTF8->GetBytes(fileName + "\0");
    pin_ptr<System::Byte> utfFileNamePin = &utfFileName[0];
    const char *fileNameBuf = (const char *)utfFileNamePin;

    array<System::Byte> ^utfId= System::Text::Encoding::UTF8->GetBytes(streamingRemoteId + "\0");
    pin_ptr<System::Byte> utfIdPin = &utfId[0];
    const char *idBuf = (const char *)utfIdPin;

    CommoResult ret = impl::nativeToCLI(impl->commo->sendMissionPackageInit(&nativeId, idBuf, filePathBuf, fileNameBuf));

    xferId = nativeId;

    return ret;
}

CommoResult Commo::StartMissionPackageSend(int xferId)
{
    return impl::nativeToCLI(impl->commo->sendMissionPackageStart(xferId));
}



array<System::String ^> ^Commo::GetContactList()
{
    const atakmap::commoncommo::ContactList *clist = impl->commo->getContactList();
    array<System::String ^> ^ret = gcnew array<System::String ^>(clist->nContacts);
    for (size_t i = 0; i < clist->nContacts; ++i) {
        ret[i] = gcnew System::String((const char *)clist->contacts[i]->contactUID,
                                      0, clist->contacts[i]->contactUIDLen, System::Text::Encoding::UTF8);
    }
    impl->commo->freeContactList(clist);
    return ret;
}

CommoResult Commo::ConfigKnownEndpointContact(System::String ^contact,
                                              System::String ^callsign,
                                              System::String ^ipAddr,
                                              int port)
{
    if (contact == nullptr)
        return CommoResult::CommoIllegalArgument;

    msclr::interop::marshal_context mctx;
    const char *contactBuf = toUTF8(contact);
    const char *callsignBuf = callsign == nullptr ? NULL : toUTF8(callsign);
    const char *ipAddrBuf = ipAddr == nullptr ? NULL : mctx.marshal_as<const char *>(ipAddr);

    atakmap::commoncommo::ContactUID contactuid((const uint8_t *)contactBuf, contact->Length);
    atakmap::commoncommo::CommoResult ret =
        impl->commo->configKnownEndpointContact(&contactuid,
                                                callsignBuf,
                                                ipAddrBuf,
                                                port);
    delete[] contactBuf;
    delete[] callsignBuf;
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;
    else
        return CommoResult::CommoSuccess;
}

CommoResult Commo::AddContactPresenceListener(IContactPresenceListener ^listener)
{
    if (impl->contactListeners->ContainsKey(listener))
        return CommoResult::CommoIllegalArgument;

    impl::ContactPresenceListenerImpl *listenerImpl = new impl::ContactPresenceListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addContactPresenceListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->contactListeners->TryAdd(listener, System::IntPtr(listenerImpl));
    return CommoResult::CommoSuccess;
}

CommoResult Commo::RemoveContactPresenceListener(IContactPresenceListener ^listener)
{
    System::IntPtr ptr;
    if (!impl->contactListeners->TryGetValue(listener, ptr))
        return CommoResult::CommoIllegalArgument;

    impl::ContactPresenceListenerImpl *nativePtr = (impl::ContactPresenceListenerImpl *)ptr.ToPointer();
    atakmap::commoncommo::CommoResult ret = impl->commo->removeContactPresenceListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    System::IntPtr value;
    impl->contactListeners->TryRemove(listener, value);
    delete nativePtr;
    return CommoResult::CommoSuccess;
}


System::String ^Commo::GenerateKeyCryptoString(System::String ^password, int keyLength)
{
    if (password == nullptr)
        return nullptr;

    msclr::interop::marshal_context mctx;
    const char *pwNative = mctx.marshal_as<const char *>(password);

    char *resultNative = impl->commo->generateKeyCryptoString(pwNative,
                                                              keyLength);
    System::String ^result = nullptr;
    if (resultNative) {
        result = gcnew System::String(resultNative);
        impl->commo->freeCryptoString(resultNative);
    }
    return result;
}


System::String ^Commo::GenerateCSRCryptoString(
    System::Collections::Generic::IDictionary<System::String ^, System::String ^>
        ^csrDnEntries,
    System::String ^pkeyPem, System::String ^password)
{
    if (password == nullptr || csrDnEntries == nullptr || pkeyPem == nullptr)
        return nullptr;

    msclr::interop::marshal_context mctx;
    const char *pwNative = mctx.marshal_as<const char *>(password);
    const char *keyNative = mctx.marshal_as<const char *>(pkeyPem);

    int nEntries = csrDnEntries->Count;
    const char **keys = new const char *[nEntries];
    const char **values = new const char *[nEntries];
    int i = 0;
    for each (System::Collections::Generic::KeyValuePair<System::String ^, System::String ^>
                            ^entry in csrDnEntries) {
        keys[i] = mctx.marshal_as<const char *>(entry->Key);
        values[i] = mctx.marshal_as<const char *>(entry->Value);
        i++;
    }

    char *resultNative = impl->commo->generateCSRCryptoString(keys, values,
        nEntries, keyNative, pwNative);
    delete[] keys;
    delete[] values;

    System::String ^result = nullptr;
    if (resultNative) {
        result = gcnew System::String(resultNative);
        impl->commo->freeCryptoString(resultNative);
    }
    return result;
}

System::String ^Commo::GenerateKeystoreCryptoString(System::String ^certPem,
    array<System::String ^> ^caPem,
    System::String ^pkeyPem, System::String ^password,
    System::String ^friendlyName)
{
    if (password == nullptr || certPem == nullptr || caPem == nullptr ||
            pkeyPem == nullptr || friendlyName == nullptr)
        return nullptr;

    msclr::interop::marshal_context mctx;
    const char *certNative = mctx.marshal_as<const char *>(certPem);
    const char *keyNative = mctx.marshal_as<const char *>(pkeyPem);
    const char *pwNative = mctx.marshal_as<const char *>(password);
    const char *friendlyNative = mctx.marshal_as<const char *>(friendlyName);

    int nEntries = caPem->Length;
    const char **caNative = new const char *[nEntries];
    int i = 0;
    for each (System::String ^ca in caPem) {
        caNative[i] = mctx.marshal_as<const char *>(ca);
        i++;
    }

    char *resultNative = impl->commo->generateKeystoreCryptoString(certNative,
        caNative, nEntries, keyNative, pwNative, friendlyNative);
    delete[] caNative;

    System::String ^result = nullptr;
    if (resultNative) {
        result = gcnew System::String(resultNative);
        impl->commo->freeCryptoString(resultNative);
    }
    return result;
}


array<System::Byte> ^Commo::GenerateSelfSignedCert(System::String ^password)
{
    if (password == nullptr)
        return nullptr;

    msclr::interop::marshal_context mctx;
    const char *passwordNative = mctx.marshal_as<const char *>(password);

    uint8_t *cert = NULL;
    size_t len = impl->commo->generateSelfSignedCert(&cert, passwordNative);
    array<System::Byte> ^ret = gcnew array<System::Byte>(len);
    pin_ptr<System::Byte> pinRet = &ret[0];
    memcpy(pinRet, cert, len);
    impl->commo->freeSelfSignedCert(cert);
    return ret;
}
