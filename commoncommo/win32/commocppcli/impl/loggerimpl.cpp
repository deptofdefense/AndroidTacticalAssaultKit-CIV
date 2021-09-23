#include "loggerimpl.h"

using namespace TAK::Commo::impl;

LoggerImpl::LoggerImpl(TAK::Commo::ICommoLogger ^logger) : loggerCLI(logger)
{
}

void LoggerImpl::log(atakmap::commoncommo::CommoLogger::Level level, atakmap::commoncommo::CommoLogger::Type type, const char* message, void* detail)
{
    System::Object^ dataCLI = nullptr;

    TAK::Commo::ICommoLogger::Level levelCLI;
    switch (level) {
    case atakmap::commoncommo::CommoLogger::LEVEL_VERBOSE:
        levelCLI = TAK::Commo::ICommoLogger::Level::LevelVerbose;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_DEBUG:
        levelCLI = TAK::Commo::ICommoLogger::Level::LevelDebug;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_WARNING:
        levelCLI = TAK::Commo::ICommoLogger::Level::LevelWarning;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_INFO:
        levelCLI = TAK::Commo::ICommoLogger::Level::LevelInfo;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_ERROR:
        levelCLI = TAK::Commo::ICommoLogger::Level::LevelError;
        break;
    }

    TAK::Commo::ICommoLogger::Type typeCLI;
    switch (type) {
    case atakmap::commoncommo::CommoLogger::TYPE_GENERAL:
        typeCLI = TAK::Commo::ICommoLogger::Type::General;
        break;
    case atakmap::commoncommo::CommoLogger::TYPE_PARSING: {
        typeCLI = TAK::Commo::ICommoLogger::Type::Parsing;
        const auto parsingDetail = static_cast<atakmap::commoncommo::CommoLogger::ParsingDetail*>(detail);
        if (parsingDetail)
        {
            array<System::Byte>^ cliData = gcnew array<System::Byte>(parsingDetail->messageLen);
            {
                pin_ptr<System::Byte> pinData = &cliData[0];
                uint8_t* nativeData = (uint8_t*)pinData;
                memcpy(pinData, parsingDetail->messageData, parsingDetail->messageLen);
            }
            dataCLI = gcnew TAK::Commo::ICommoLogger::ParsingDetail(cliData, gcnew System::String(parsingDetail->errorDetailString), gcnew System::String(parsingDetail->rxIfaceEndpointId));
        }
    }
        break;
    case atakmap::commoncommo::CommoLogger::TYPE_NETWORK: {
        typeCLI = TAK::Commo::ICommoLogger::Type::Network;
        const auto networkDetail = static_cast<atakmap::commoncommo::CommoLogger::NetworkDetail*>(detail);
        if (networkDetail)
        {
            dataCLI = gcnew TAK::Commo::ICommoLogger::NetworkDetail(networkDetail->port);
        }
    }
        break;
    }


    loggerCLI->Log(levelCLI, typeCLI, gcnew System::String(message), dataCLI);
}