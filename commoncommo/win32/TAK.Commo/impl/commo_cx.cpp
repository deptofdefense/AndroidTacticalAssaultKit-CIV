#include "pch.h"
#include "commo_cx.h"
#include "loggerimpl.h"
#include "missionpackageimpl.h"
#include "cotmessageioimpl.h"
#include "contactuidimpl.h"
#include "netinterfaceimpl.h"
#include "commo.h"
#include "commoresult.h"

using namespace TAK::Commo;
using namespace Platform;

namespace TAK {
    namespace Commo {
        namespace impl {
            private class CommoImpl
            {
            public:
                CommoImpl(TAK::Commo::ICommoLogger ^logger, Platform::String ^ourUID, Platform::String ^ourCallsign) 
                    : commo(nullptr), loggerImpl(nullptr), mpImpl(nullptr)
                {
                    loggerImpl = new LoggerImpl(logger);

                    {
                        auto wUid = std::wstring(ourUID->Data());
                        auto sUid = std::string(wUid.begin(), wUid.end());
                        auto wCallsign = std::wstring(ourCallsign->Data());
                        auto sCallsign = std::string(wCallsign.begin(), wCallsign.end());
                        const char *uidBuf = sUid.c_str();
                        atakmap::commoncommo::ContactUID uid((const uint8_t *)uidBuf, ourUID->Length());
                        const char *csBuf = sCallsign.c_str();

                        commo = new atakmap::commoncommo::Commo(loggerImpl, &uid, csBuf);
                    }
                }

                ~CommoImpl()
                {
                    if (commo != nullptr) {
                        delete loggerImpl;
                        delete commo;
                        delete mpImpl;
                        loggerImpl = nullptr;
                        commo = nullptr;
                        mpImpl = nullptr;

                        // Clean out all listener impls
                        for(auto kvp : cotListeners) {
                            impl::CoTMessageListenerImpl *listener = kvp.second;
                            delete listener;
                        }
                        for (auto kvp : contactListeners) {
                            impl::ContactPresenceListenerImpl *listener = kvp.second;
                            delete listener;
                        }
                        for (auto kvp : interfaceListeners) {
                            impl::InterfaceStatusListenerImpl *listener = kvp.second;
                            delete listener;
                        }
                    }
                }

                atakmap::commoncommo::Commo *commo;
                LoggerImpl *loggerImpl;
                MissionPackageIOImpl *mpImpl;

                std::map<Platform::Agile<ICoTMessageListener>, impl::CoTMessageListenerImpl*> cotListeners;
                std::map<Platform::Agile<IContactPresenceListener>, impl::ContactPresenceListenerImpl*> contactListeners;
                std::map<Platform::Agile<IInterfaceStatusListener>, impl::InterfaceStatusListenerImpl*> interfaceListeners;
                InterfaceStatusListenerImpl::InterfaceRegistry netInterfaceRegistry;
            };


            CommoResult nativeToCx(atakmap::commoncommo::CommoResult res)
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
                }
                return ret;
            }

            Platform::String^ NativeToCx(const char* cstring)
            {
                auto str = std::string(cstring);
                auto wStr = std::wstring(str.begin(), str.end());
                return ref new Platform::String(wStr.data());
            }

            std::string CxToNative(Platform::String^ cxString)
            {
                auto wStr = std::wstring(cxString->Data());
                auto str = std::string(wStr.begin(), wStr.end());
                return str;
            }
        }
    }
}

Commo::Commo(ICommoLogger ^logger, Platform::String ^ourUID, Platform::String ^ourCallsign)
{
    OutputDebugString(TEXT("Creating a commo object\n"));

    WSAData wsaData;
    WSAStartup(0x0202, &wsaData);

    xmlInitParser();

    SSL_library_init();
    SSL_load_error_strings();

    curl_global_init(0);

    this->impl = new impl::CommoImpl(logger, ourUID, ourCallsign);
}

Commo::~Commo()
{
    xmlCleanupParser();
    WSACleanup();
}

CommoResult Commo::enableMissionPackageIO(IMissionPackageIO ^missionPackageIO,
    int localWebPort)
{
    impl::MissionPackageIOImpl *newMPImpl = new impl::MissionPackageIOImpl(missionPackageIO);
    CommoResult ret = impl::nativeToCx(impl->commo->enableMissionPackageIO(newMPImpl, localWebPort));
    if (ret == CommoResult::CommoSuccess)
        impl->mpImpl = newMPImpl;
    else
        delete newMPImpl;
    return ret;
}


void Commo::shutdown()
{
    impl->commo->shutdown();
}


void Commo::setCallsign(Platform::String ^callsign)
{
    auto wCallsign = std::wstring(callsign->Data());
    auto sCallsign = std::string(wCallsign.begin(), wCallsign.end());
    const char *buf = sCallsign.c_str();
    impl->commo->setCallsign(buf);
}

void Commo::setTTL(int ttl)
{
    impl->commo->setTTL(ttl);
}

PhysicalNetInterface ^Commo::addBroadcastInterface(const Platform::Array<uint8> ^hwAddress, const Platform::Array<CoTMessageType> ^types, Platform::String ^mcastAddr, int destPort)
{
    atakmap::commoncommo::HwAddress hwAddrNative((uint8_t *)hwAddress->Data, hwAddress->Length);
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

    auto wMcastAddr = std::wstring(mcastAddr->Data());
    auto sMcastAddr = std::string(wMcastAddr.begin(), wMcastAddr.end());
    const char *nativeMcast = sMcastAddr.c_str();
    atakmap::commoncommo::PhysicalNetInterface *iface = impl->commo->addBroadcastInterface(&hwAddrNative, nativeTypes, n, nativeMcast, destPort);
    delete[] nativeTypes;
    if (!iface)
        return nullptr;

    PhysicalNetInterface ^cliIface = ref new PhysicalNetInterface(iface, hwAddress);
    impl->netInterfaceRegistry.emplace(iface, Platform::Agile<NetInterface>(cliIface));

    return cliIface;
}

CommoResult Commo::removeBroadcastInterface(PhysicalNetInterface ^iface)
{
    atakmap::commoncommo::PhysicalNetInterface *phys = iface->impl;

    NetInterface ^lookupIface = impl->netInterfaceRegistry.find(phys)->second.Get();

    if (lookupIface == nullptr || lookupIface != iface)
        // Weird??
        return CommoResult::CommoIllegalArgument;
    if (impl->commo->removeBroadcastInterface(phys) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->netInterfaceRegistry.erase(phys);

    return CommoResult::CommoSuccess;
}

PhysicalNetInterface ^Commo::addInboundInterface(const Platform::Array<uint8> ^hwAddress, int port, const Platform::Array<Platform::String ^> ^mcastAddrs)
{
    atakmap::commoncommo::HwAddress hwAddrNative((uint8_t *)hwAddress->Data, hwAddress->Length);
    int sn = mcastAddrs->Length;
    if (sn < 0)
        return nullptr;

    size_t n = (size_t)sn;
    const char **nativeMcastAddrs = new const char *[n];
    for (size_t i = 0; i < n; ++i) {
        int len = WideCharToMultiByte(CP_UTF8, 0, mcastAddrs[i]->Data(), mcastAddrs[i]->Length(), 0, 0, NULL, NULL);
        char* pRBuf = new char[len];
        WideCharToMultiByte(CP_UTF8, 0, mcastAddrs[i]->Data(), mcastAddrs[i]->Length(), pRBuf, len, NULL, NULL);
        pRBuf[len] = '\0';
        nativeMcastAddrs[i] = pRBuf;
    }
    atakmap::commoncommo::PhysicalNetInterface *iface = impl->commo->addInboundInterface(&hwAddrNative, port, (const char**)nativeMcastAddrs, n);
    delete[] nativeMcastAddrs;
    if (!iface)
        return nullptr;

    PhysicalNetInterface ^cliIface = ref new PhysicalNetInterface(iface, hwAddress);
    impl->netInterfaceRegistry.emplace(iface, Platform::Agile<NetInterface>(cliIface));

    return cliIface;
}

CommoResult Commo::removeInboundInterface(PhysicalNetInterface ^iface)
{
    atakmap::commoncommo::PhysicalNetInterface *phys = iface->impl;

    NetInterface ^lookupIface = impl->netInterfaceRegistry.find(phys)->second.Get();

    if (lookupIface == nullptr || lookupIface != iface)
        // Weird??
        return CommoResult::CommoIllegalArgument;
    if (impl->commo->removeInboundInterface(phys) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->netInterfaceRegistry.erase(phys);

    return CommoResult::CommoSuccess;
}

StreamingNetInterface ^Commo::addStreamingInterface(Platform::String ^hostname, int port,
    const Platform::Array<CoTMessageType> ^types,
    const Platform::Array<uint8> ^clientCert,
    const Platform::Array<uint8> ^caCert,
    Platform::String ^certPassword,
    Platform::String ^username, Platform::String ^password)
{
    const uint8_t *nativeClientCert = nullptr;
    size_t clientCertLen = 0;
    const uint8_t *nativeCACert = nullptr;
    size_t caCertLen = 0;
    char *nativeHostname = nullptr;
    char *nativeCertPassword = nullptr;
    char *nativeUsername = nullptr;
    char *nativePassword = nullptr;

    if (hostname)
    {
        auto sHostname = impl::CxToNative(hostname);
        nativeHostname = new char[sHostname.length() + 1];
        strncpy_s(nativeHostname, strlen(nativeHostname), sHostname.c_str(), sHostname.length());
    }

    if (clientCert) {
        nativeClientCert = (const uint8_t *)clientCert->Data;
        clientCertLen = clientCert->Length;
    }
    if (caCert) {
        nativeCACert = (const uint8_t *)caCert->Data;
        caCertLen = caCert->Length;
    }
    if (certPassword)
    {
        auto sCertPassword = impl::CxToNative(certPassword);
        nativeCertPassword = new char[sCertPassword.length() + 1];
        strncpy_s(nativeCertPassword, strlen(nativeCertPassword), sCertPassword.c_str(), sCertPassword.length());
    }
    if (username)
    {
        auto sUsername = impl::CxToNative(username);
        nativeUsername = new char[sUsername.length() + 1];
        strncpy_s(nativeUsername, strlen(nativeUsername), sUsername.c_str(), sUsername.length());
    }
    if (password)
    {
        auto sPassword = impl::CxToNative(password);
        nativePassword = new char[sPassword.length() + 1];
        strncpy_s(nativePassword, strlen(nativePassword), sPassword.c_str(), sPassword.length());
    }

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

    atakmap::commoncommo::StreamingNetInterface *iface = impl->commo->addStreamingInterface(nativeHostname, port, nativeTypes, n, nativeClientCert, clientCertLen, nativeCACert, caCertLen, nativeCertPassword, nativeUsername, nativePassword);
    delete[] nativeTypes;
    if (!iface)
        return nullptr;

    StreamingNetInterface ^cliIface = ref new StreamingNetInterface(iface, impl::NativeToCx(iface->remoteEndpointId));
    impl->netInterfaceRegistry.emplace(iface, Platform::Agile<NetInterface>(cliIface));

    return cliIface;
}

CommoResult Commo::removeStreamingInterface(StreamingNetInterface ^iface)
{
    atakmap::commoncommo::StreamingNetInterface *stream = iface->impl;

    NetInterface ^lookupIface = impl->netInterfaceRegistry.find(stream)->second.Get();
    if (lookupIface == nullptr || lookupIface != iface)
        // Weird??
        return CommoResult::CommoIllegalArgument;
    if (impl->commo->removeStreamingInterface(stream) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->netInterfaceRegistry.erase(stream);

    return CommoResult::CommoSuccess;
}


CommoResult Commo::addInterfaceStatusListener(IInterfaceStatusListener ^listener)
{
    auto agileListener = Platform::Agile<IInterfaceStatusListener>(listener);
    if (impl->interfaceListeners.find(agileListener) != impl->interfaceListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::InterfaceStatusListenerImpl *listenerNative = new impl::InterfaceStatusListenerImpl(&impl->netInterfaceRegistry, listener);
    if (impl->commo->addInterfaceStatusListener(listenerNative) != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerNative;
        return CommoResult::CommoIllegalArgument;
    }
    impl->interfaceListeners.emplace(agileListener, listenerNative);
    return CommoResult::CommoSuccess;
}

CommoResult Commo::removeInterfaceStatusListener(IInterfaceStatusListener ^listener)
{
    if (impl->interfaceListeners.find(Platform::Agile<IInterfaceStatusListener>(listener)) == impl->interfaceListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::InterfaceStatusListenerImpl *listenerNative = impl->interfaceListeners.find(Platform::Agile<IInterfaceStatusListener>(listener))->second;

    if (impl->commo->removeInterfaceStatusListener(listenerNative) != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->interfaceListeners.erase(Platform::Agile<IInterfaceStatusListener>(listener));
    delete listenerNative;

    return CommoResult::CommoSuccess;
}


CommoResult Commo::addCoTMessageListener(ICoTMessageListener ^listener)
{
    auto agileListener = Platform::Agile<ICoTMessageListener>(listener);
    if (impl->cotListeners.find(agileListener) != impl->cotListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::CoTMessageListenerImpl *listenerImpl = new impl::CoTMessageListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addCoTMessageListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->cotListeners.emplace(agileListener, listenerImpl);
    return CommoResult::CommoSuccess;
}

CommoResult Commo::removeCoTMessageListener(ICoTMessageListener ^listener)
{
    if (impl->cotListeners.find(Platform::Agile<ICoTMessageListener>(listener)) == impl->cotListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::CoTMessageListenerImpl *nativePtr = impl->cotListeners.find(Platform::Agile<ICoTMessageListener>(listener))->second;
    atakmap::commoncommo::CommoResult ret = impl->commo->removeCoTMessageListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->cotListeners.erase(Platform::Agile<ICoTMessageListener>(listener));
    delete nativePtr;
    return CommoResult::CommoSuccess;
}


CommoResult Commo::sendCoT(Windows::Foundation::Collections::IVector<Platform::String ^> ^destinations, Platform::String ^cotMessage)
{
    const char *buf = impl::CxToNative(cotMessage).c_str();

    const int n = destinations->Size;
    const atakmap::commoncommo::ContactUID **contacts = new const atakmap::commoncommo::ContactUID*[n];
    const atakmap::commoncommo::ContactUID **contactsCopy = new const atakmap::commoncommo::ContactUID*[n];
    for (int i = 0; i < n; ++i) {
        const char *cbuf = impl::CxToNative(destinations->GetAt(i)).c_str();
        contacts[i] = new atakmap::commoncommo::ContactUID((const uint8_t *)cbuf, destinations->GetAt(i)->Length());
        contactsCopy[i] = contacts[i];
    }
    atakmap::commoncommo::ContactList list(n, contacts);

    CommoResult ret = impl::nativeToCx(impl->commo->sendCoT(&list, buf));

    if (ret == CommoResult::CommoContactGone) {
        destinations->Clear();

        for (size_t i = 0; i < list.nContacts; ++i) {
            destinations->Append(impl::NativeToCx((const char *)list.contacts[i]->contactUID));
        }
    }

    for (int i = 0; i < n; ++i) {
        delete contactsCopy[i];
    }
    delete[] contacts;
    delete[] contactsCopy;
    return ret;
}

CommoResult Commo::broadcastCoT(Platform::String ^cotMessage)
{
    auto sCotMessage = impl::CxToNative(cotMessage);
    return impl::nativeToCx(impl->commo->broadcastCoT(sCotMessage.c_str()));
}

CommoResult Commo::sendMissionPackage(int* xferId,
    Windows::Foundation::Collections::IVector<Platform::String ^> ^destinations,
    Platform::String ^filePath,
    Platform::String ^fileName,
    Platform::String ^name)
{
    int nativeId = 0;
    auto sFilePath = impl::CxToNative(filePath);
    auto sFileName = impl::CxToNative(fileName);
    auto sName = impl::CxToNative(name);

    size_t n = destinations->Size;
    const atakmap::commoncommo::ContactUID **contacts = new const atakmap::commoncommo::ContactUID*[n];
    const atakmap::commoncommo::ContactUID **contactsCopy = new const atakmap::commoncommo::ContactUID*[n];
    for (size_t i = 0; i < n; ++i) {
        const char *cbuf = impl::CxToNative(destinations->GetAt(i)).c_str();
        contacts[i] = new atakmap::commoncommo::ContactUID((const uint8_t *)cbuf, destinations->GetAt(i)->Length());
        contactsCopy[i] = contacts[i];
    }

    atakmap::commoncommo::ContactList contactList(n, contacts);
    CommoResult ret = impl::nativeToCx(impl->commo->sendMissionPackage(&nativeId, &contactList, sFilePath.c_str(), sFileName.c_str(), sName.c_str()));

    if (ret == CommoResult::CommoContactGone) {
        destinations->Clear();

        for (size_t i = 0; i < contactList.nContacts; ++i) {
            destinations->Append(impl::NativeToCx((const char *)contactList.contacts[i]->contactUID));
        }
    }

    for (size_t i = 0; i < n; ++i) {
        delete contactsCopy[i];
    }
    delete[] contacts;
    delete[] contactsCopy;
    xferId = &nativeId;

    return ret;
}

Platform::Array<Platform::String ^> ^Commo::getContactList()
{
    const atakmap::commoncommo::ContactList *clist = impl->commo->getContactList();
    Platform::Array<Platform::String ^> ^ret = ref new Platform::Array<Platform::String ^>(clist->nContacts);
    for (size_t i = 0; i < clist->nContacts; ++i) {
        ret[i] = impl::NativeToCx((const char *)clist->contacts[i]->contactUID);
    }
    impl->commo->freeContactList(clist);
    return ret;
}


CommoResult Commo::addContactPresenceListener(IContactPresenceListener ^listener)
{
    auto agileListener = Platform::Agile<IContactPresenceListener>(listener);
    if (impl->contactListeners.find(agileListener) != impl->contactListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::ContactPresenceListenerImpl *listenerImpl = new impl::ContactPresenceListenerImpl(listener);
    atakmap::commoncommo::CommoResult ret = impl->commo->addContactPresenceListener(listenerImpl);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS) {
        delete listenerImpl;
        return CommoResult::CommoIllegalArgument;
    }
    impl->contactListeners.emplace(agileListener, listenerImpl);
    return CommoResult::CommoSuccess;
}

CommoResult Commo::removeContactPresenceListener(IContactPresenceListener ^listener)
{
    auto agileListener = Platform::Agile<IContactPresenceListener>(listener);
    if (impl->contactListeners.find(agileListener) == impl->contactListeners.end())
        return CommoResult::CommoIllegalArgument;

    impl::ContactPresenceListenerImpl *nativePtr = impl->contactListeners.find(agileListener)->second;
    atakmap::commoncommo::CommoResult ret = impl->commo->addContactPresenceListener(nativePtr);
    if (ret != atakmap::commoncommo::COMMO_SUCCESS)
        return CommoResult::CommoIllegalArgument;

    impl->contactListeners.erase(agileListener);
    delete nativePtr;
    return CommoResult::CommoSuccess;
}
