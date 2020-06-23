#include "pch.h"
#include "cotmessageioimpl.h"

#include <string>

using namespace TAK::Commo::impl;

CoTMessageListenerImpl::CoTMessageListenerImpl(TAK::Commo::ICoTMessageListener ^listener) : CoTMessageListener()
{
    _cotlistenerCx = listener;
}

CoTMessageListenerImpl::~CoTMessageListenerImpl()
{

}

void CoTMessageListenerImpl::cotMessageReceived(const char *cotMessage)
{
    auto str = std::string(cotMessage);
    auto wstr = std::wstring(str.begin(), str.end());
    _cotlistenerCx->cotMessageReceived(ref new Platform::String(wstr.c_str()));
}