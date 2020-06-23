#include "cotmessageioimpl.h"

using namespace TAK::Commo::impl;

CoTMessageListenerImpl::CoTMessageListenerImpl(TAK::Commo::ICoTMessageListener ^listener) : CoTMessageListener()
{
    cotlistenerCLI = listener;
}

CoTMessageListenerImpl::~CoTMessageListenerImpl()
{

}

void CoTMessageListenerImpl::cotMessageReceived(const char *cotMessage, const char *rxEndpointId)
{
    cotlistenerCLI->CotMessageReceived(gcnew System::String(cotMessage, 0, strlen(cotMessage), System::Text::Encoding::UTF8), rxEndpointId ? gcnew System::String(rxEndpointId) : nullptr);
}

GenericDataListenerImpl::GenericDataListenerImpl(TAK::Commo::IGenericDataListener ^listener) : GenericDataListener()
{
    genericListenerCLI = listener;
}

GenericDataListenerImpl::~GenericDataListenerImpl()
{

}

void GenericDataListenerImpl::genericDataReceived(const uint8_t *data, size_t dataLen, const char *rxEndpointId)
{
    array<System::Byte> ^cliData = gcnew array<System::Byte>(dataLen);
    {
        pin_ptr<System::Byte> pinData = &cliData[0];
        uint8_t *nativeData = (uint8_t *)pinData;
        memcpy(pinData, data, dataLen);
    }

    genericListenerCLI->GenericDataReceived(cliData, rxEndpointId ? gcnew System::String(rxEndpointId) : nullptr);
}

CoTSendFailureListenerImpl::CoTSendFailureListenerImpl(TAK::Commo::ICoTSendFailureListener ^listener) : CoTSendFailureListener()
{
    cotlistenerCLI = listener;
}

CoTSendFailureListenerImpl::~CoTSendFailureListenerImpl()
{

}

void CoTSendFailureListenerImpl::sendCoTFailure(const char *host, int port, const char *errorReason)
{
    cotlistenerCLI->SendCoTFailure(gcnew System::String(host), port, gcnew System::String(errorReason));
}