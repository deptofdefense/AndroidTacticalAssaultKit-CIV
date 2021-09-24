
#include "renderer/GLWorkers.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

static std::shared_ptr<ControlWorker> makeGLControlWorker() NOTHROWS {
	std::shared_ptr<ControlWorker> worker;
	Worker_createControlWorker(worker);
	return worker;
}

static std::shared_ptr<ControlWorker> globalGLResourceControlWorker() NOTHROWS {
	static std::shared_ptr<ControlWorker> inst(makeGLControlWorker());
	return inst;
}

static std::shared_ptr<ControlWorker> globalGLThreadWorker() NOTHROWS {
	static std::shared_ptr<ControlWorker> inst(makeGLControlWorker());
	return inst;
}

SharedWorkerPtr TAK::Engine::Renderer::GLWorkers_resourceLoad() NOTHROWS {
	return globalGLResourceControlWorker();
}

TAKErr TAK::Engine::Renderer::GLWorkers_doResourceLoadingWork(size_t millisecondLimit) NOTHROWS {
	std::shared_ptr<ControlWorker> worker = globalGLResourceControlWorker();
	if (!worker)
		return TE_Err;
	worker->doAnyWork(millisecondLimit);
	return TE_Ok;
}

SharedWorkerPtr TAK::Engine::Renderer::GLWorkers_glThread() NOTHROWS {
	return globalGLThreadWorker();
}

TAKErr TAK::Engine::Renderer::GLWorkers_doGLThreadWork() NOTHROWS {
	std::shared_ptr<ControlWorker> worker = globalGLThreadWorker();
	if (!worker)
		return TE_Err;
	worker->doAnyWork(INT64_MAX);
	return TE_Ok;
}