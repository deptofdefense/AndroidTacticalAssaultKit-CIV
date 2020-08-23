#include "util/ProcessingCallback.h"

using namespace TAK::Engine::Util;

ProcessingCallback::ProcessingCallback() NOTHROWS :
    opaque(nullptr),
    progress(nullptr),
    error(nullptr),
    cancelToken(nullptr)
{}

bool TAK::Engine::Util::ProcessingCallback_isCanceled(ProcessingCallback *callback) NOTHROWS
{
    return callback && callback->cancelToken && *callback->cancelToken;
}
