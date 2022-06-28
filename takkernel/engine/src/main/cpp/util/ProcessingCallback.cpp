#include "util/ProcessingCallback.h"

using namespace TAK::Engine::Util;

ProcessingCallback::ProcessingCallback() NOTHROWS :
    opaque(nullptr),
    progress(nullptr),
    error(nullptr),
    sync_canceled(nullptr),
    cancelToken(nullptr)
{}

bool TAK::Engine::Util::ProcessingCallback_isCanceled(ProcessingCallback *callback) NOTHROWS
{
    if (!callback)
        return false;

    // if sync needed for cancel call it
    if (callback->sync_canceled && callback->sync_canceled(callback->opaque) != TE_Ok)
        return false;

    return callback->cancelToken && *callback->cancelToken;
}
