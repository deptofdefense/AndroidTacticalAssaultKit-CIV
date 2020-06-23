#include "pch.h"
#include "loggerimpl.h"

#include <string>

using namespace TAK::Commo::impl;

LoggerImpl::LoggerImpl(TAK::Commo::ICommoLogger ^logger) : _loggerCx(logger)
{
}

void LoggerImpl::log(atakmap::commoncommo::CommoLogger::Level level, const char *message)
{
    TAK::Commo::Level levelCx;
    switch (level) {
    case atakmap::commoncommo::CommoLogger::LEVEL_VERBOSE:
        levelCx = TAK::Commo::Level::LevelVerbose;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_DEBUG:
        levelCx = TAK::Commo::Level::LevelDebug;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_WARNING:
        levelCx = TAK::Commo::Level::LevelWarning;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_INFO:
        levelCx = TAK::Commo::Level::LevelInfo;
        break;
    case atakmap::commoncommo::CommoLogger::LEVEL_ERROR:
        levelCx = TAK::Commo::Level::LevelError;
        break;
    }

    auto str = std::string(message);
    auto wstr = std::wstring(str.begin(), str.end());
    _loggerCx->log(levelCx, ref new Platform::String(wstr.c_str()));
}
