#include "pch.h"
#include "contactuidimpl.h"

#include <string>

using namespace TAK::Commo::impl;

ContactPresenceListenerImpl::ContactPresenceListenerImpl(TAK::Commo::IContactPresenceListener ^listener) : _contactlistenerCx(listener)
{

}

ContactPresenceListenerImpl::~ContactPresenceListenerImpl()
{

}

void ContactPresenceListenerImpl::contactAdded(const atakmap::commoncommo::ContactUID *c)
{
    auto str = std::string((const char *)c->contactUID);
    auto wstr = std::wstring(str.begin(), str.end());
    auto cs = ref new Platform::String(wstr.c_str(), c->contactUIDLen);
    _contactlistenerCx->contactAdded(cs);
}


void ContactPresenceListenerImpl::contactRemoved(const atakmap::commoncommo::ContactUID *c)
{
    auto str = std::string((const char *)c->contactUID);
    auto wstr = std::wstring(str.begin(), str.end());
    auto cs = ref new Platform::String(wstr.c_str(), c->contactUIDLen);
    _contactlistenerCx->contactRemoved(cs);
}

