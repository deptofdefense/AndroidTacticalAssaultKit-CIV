#include "pch.h"
#include "netinterfaceimpl.h"

using namespace TAK::Commo::impl;

//TAK::Commo::NetInterface::NetInterface()
//{
//}
//
//TAK::Commo::NetInterface::~NetInterface()
//{
//
//}

TAK::Commo::PhysicalNetInterface::PhysicalNetInterface(atakmap::commoncommo::PhysicalNetInterface *impl, const Platform::Array<uint8> ^addr) 
    : impl(impl)
{
    _addr = ref new Platform::Array<uint8>(addr);

}
TAK::Commo::PhysicalNetInterface::~PhysicalNetInterface()
{
}

TAK::Commo::StreamingNetInterface::StreamingNetInterface(atakmap::commoncommo::StreamingNetInterface *impl, Platform::String ^endpoint) 
    : impl(impl)
{
    _remoteEndpointId = endpoint;
}

TAK::Commo::StreamingNetInterface::~StreamingNetInterface()
{
}

InterfaceStatusListenerImpl::InterfaceStatusListenerImpl(InterfaceRegistry* registry, TAK::Commo::IInterfaceStatusListener ^listener) 
    : ifaceRegistry(registry), interfacelistenerCx(listener)
{

}

InterfaceStatusListenerImpl::~InterfaceStatusListenerImpl()
{

}

void InterfaceStatusListenerImpl::interfaceUp(atakmap::commoncommo::NetInterface *iface)
{
    if (ifaceRegistry->find(iface) == ifaceRegistry->end())
        return;
    NetInterface^ cxIface = ifaceRegistry->find(iface)->second.Get();
    interfacelistenerCx->interfaceUp(cxIface);
    //if (!ifaceRegistry->HasKey(Platform::IntPtr(iface)))
    //    return;
    //for(auto kvp : ifaceRegistry)
    //{
    //    if (kvp->Key.ToInt32() == (int)iface)
    //    {
    //        interfacelistenerCx->interfaceUp(kvp->Value);
    //        return;
    //    }
    //}
    //auto cxIface = ifaceRegistry->Lookup(Platform::IntPtr(iface));
    //interfacelistenerCx->interfaceUp(cxIface);
}


void InterfaceStatusListenerImpl::interfaceDown(atakmap::commoncommo::NetInterface *iface)
{
    if (ifaceRegistry->find(iface) == ifaceRegistry->end())
        return;
    NetInterface^ cxIface = ifaceRegistry->find(iface)->second.Get();
    interfacelistenerCx->interfaceDown(cxIface);
    //if (!ifaceRegistry->HasKey(Platform::IntPtr(iface)))
    //    return;
    //for (auto kvp : ifaceRegistry)
    //{
    //    if (kvp->Key.ToInt32() == (int)iface)
    //    {
    //        interfacelistenerCx->interfaceDown(kvp->Value);
    //        return;
    //    }
    //}
    //auto cxIface = ifaceRegistry->Lookup(Platform::IntPtr(iface));
    //interfacelistenerCx->interfaceDown(cxIface);
}
