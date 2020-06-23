#include "netinterfaceimpl.h"

using namespace TAK::Commo::impl;


namespace {
    TAK::Commo::NetInterfaceErrorCode errCodeToManaged(atakmap::commoncommo::netinterfaceenums::NetInterfaceErrorCode errCode)
    {
        using namespace atakmap::commoncommo::netinterfaceenums;

        TAK::Commo::NetInterfaceErrorCode ret = TAK::Commo::NetInterfaceErrorCode::ErrOther;
        switch (errCode) {
        case ERR_CONN_NAME_RES_FAILED:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnNameResFailed;
            break;
        case ERR_CONN_REFUSED:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnRefused;
            break;
        case ERR_CONN_TIMEOUT:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnTimeout;
            break;
        case ERR_CONN_HOST_UNREACHABLE:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnHostUnreachable;
            break;
        case ERR_CONN_SSL_NO_PEER_CERT:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnSSLNoPeerCert;
            break;
        case ERR_CONN_SSL_PEER_CERT_NOT_TRUSTED:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnSSLPeerCertNotTrusted;
            break;
        case ERR_CONN_SSL_HANDSHAKE:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnSSLHandshake;
            break;
        case ERR_CONN_OTHER:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrConnOther;
            break;
        case ERR_IO_RX_DATA_TIMEOUT:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrIORxDataTimeout;
            break;
        case ERR_IO:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrIO;
            break;
        case ERR_INTERNAL:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrInternal;
            break;
        case ERR_OTHER:
            ret = TAK::Commo::NetInterfaceErrorCode::ErrOther;
            break;
        }
        return ret;
    }
}


TAK::Commo::NetInterface::NetInterface()
{
}

TAK::Commo::NetInterface::~NetInterface()
{

}


TAK::Commo::PhysicalNetInterface::PhysicalNetInterface(atakmap::commoncommo::PhysicalNetInterface *impl, System::String ^ifaceName) : impl(impl), ifaceName(ifaceName)
{
}
TAK::Commo::PhysicalNetInterface::~PhysicalNetInterface()
{
}


TAK::Commo::TcpInboundNetInterface::TcpInboundNetInterface(atakmap::commoncommo::TcpInboundNetInterface *impl, const int port) : impl(impl), port(port)
{
}
TAK::Commo::TcpInboundNetInterface::~TcpInboundNetInterface()
{
}


TAK::Commo::StreamingNetInterface::StreamingNetInterface(atakmap::commoncommo::StreamingNetInterface *impl, System::String ^endpoint) : impl(impl), remoteEndpointId(endpoint)
{
}

TAK::Commo::StreamingNetInterface::~StreamingNetInterface()
{
}


InterfaceStatusListenerImpl::InterfaceStatusListenerImpl(InterfaceRegistry ^registry, TAK::Commo::IInterfaceStatusListener ^listener) : ifaceRegistry(registry), interfacelistenerCLI(listener)
{

}

InterfaceStatusListenerImpl::~InterfaceStatusListenerImpl()
{

}

void InterfaceStatusListenerImpl::interfaceUp(atakmap::commoncommo::NetInterface *iface)
{
    TAK::Commo::NetInterface ^cliIface;
    if (!ifaceRegistry->TryGetValue(System::IntPtr(iface), cliIface))
        return;
    interfacelistenerCLI->InterfaceUp(cliIface);
}


void InterfaceStatusListenerImpl::interfaceDown(atakmap::commoncommo::NetInterface *iface)
{
    TAK::Commo::NetInterface ^cliIface;
    if (!ifaceRegistry->TryGetValue(System::IntPtr(iface), cliIface))
        return;
    interfacelistenerCLI->InterfaceDown(cliIface);
}

void InterfaceStatusListenerImpl::interfaceError(atakmap::commoncommo::NetInterface *iface, atakmap::commoncommo::netinterfaceenums::NetInterfaceErrorCode errCode)
{
    TAK::Commo::NetInterface ^cliIface;
    if (!ifaceRegistry->TryGetValue(System::IntPtr(iface), cliIface))
        return;
    interfacelistenerCLI->InterfaceError(cliIface, errCodeToManaged(errCode));
}
