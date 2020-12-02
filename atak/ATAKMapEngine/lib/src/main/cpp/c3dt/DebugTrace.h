

#ifndef CESIUM3DTILES_DEBUGTRACE_H
#define CESIUM3DTILES_DEBUGTRACE_H

//#define TRACE_ENABLED
#ifdef TRACE_ENABLED
#include <android/log.h>

#define debug_trace(fn) \
    class Trace##__LINE__ \
    { \
    public : \
        Trace##__LINE__() { __android_log_print(ANDROID_LOG_VERBOSE, "debugtrace", "enter [%s]", "" #fn); } \
        ~Trace##__LINE__() { __android_log_print(ANDROID_LOG_VERBOSE, "debugtrace", "exit [%s]", "" #fn); } \
    }; \
    Trace##__LINE__ trace##__LINE__;
#else
#define debug_trace(fmt, ...)
#endif

#endif //ATAK_DEBUGTRACE_H
