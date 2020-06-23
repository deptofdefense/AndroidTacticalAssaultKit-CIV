#include "contactuidimpl.h"

using namespace TAK::Commo::impl;

ContactPresenceListenerImpl::ContactPresenceListenerImpl(TAK::Commo::IContactPresenceListener ^listener) : contactlistenerCLI(listener)
{

}

ContactPresenceListenerImpl::~ContactPresenceListenerImpl()
{

}

void ContactPresenceListenerImpl::contactAdded(const atakmap::commoncommo::ContactUID *c)
{
    System::String ^cs = gcnew System::String((const char *)c->contactUID, 0, c->contactUIDLen, System::Text::Encoding::UTF8);
    contactlistenerCLI->ContactAdded(cs);
}


void ContactPresenceListenerImpl::contactRemoved(const atakmap::commoncommo::ContactUID *c)
{
    System::String ^cs = gcnew System::String((const char *)c->contactUID, 0, c->contactUIDLen, System::Text::Encoding::UTF8);
    contactlistenerCLI->ContactRemoved(cs);
}

