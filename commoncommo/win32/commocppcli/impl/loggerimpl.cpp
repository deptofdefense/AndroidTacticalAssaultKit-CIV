#include "loggerimpl.h"

using namespace TAK::Commo::impl;

LoggerImpl::LoggerImpl(TAK::Commo::ICommoLogger ^logger) : loggerCLI(logger)
{
}

void LoggerImpl::log(atakmap::commoncommo::CommoLogger::Level level, const char *message)
{
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
    loggerCLI->Log(levelCLI, gcnew System::String(message));
}
