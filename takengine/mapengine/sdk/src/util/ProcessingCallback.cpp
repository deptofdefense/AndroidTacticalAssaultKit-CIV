#include "util/ProcessingCallback.h"

using namespace TAK::Engine::Util;

ProcessingCallback::ProcessingCallback() NOTHROWS :
    opaque(NULL),
    progress(NULL),
    error(NULL),
    cancelToken(NULL)
{}

bool TAK::Engine::Util::ProcessingCallback_isCanceled(ProcessingCallback *callback) NOTHROWS
{
    return callback && callback->cancelToken && *callback->cancelToken;
}
