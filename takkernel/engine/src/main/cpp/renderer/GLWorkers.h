#ifndef ATAKMAP_RENDERER_GLWORKERS_H_INCLUDED
#define ATAKMAP_RENDERER_GLWORKERS_H_INCLUDED

#include "util/Work.h"
#include "core/MapRenderer.h"

namespace TAK {
	namespace Engine {
		namespace Renderer {
			/**
			 * Worker for OpenGL resource loading (i.e. texture loading and other buffer loading). Some or all of these
			 * tasks may be completed by the next render pump.
			 */
			ENGINE_API TAK::Engine::Util::SharedWorkerPtr GLWorkers_resourceLoad() NOTHROWS;

			/**
			 * Do some resource loading work with an upper time limit.
			 */
			ENGINE_API TAK::Engine::Util::TAKErr GLWorkers_doResourceLoadingWork(size_t millisecondLimit) NOTHROWS;


			ENGINE_API TAK::Engine::Util::SharedWorkerPtr GLWorkers_glThread() NOTHROWS;

			ENGINE_API TAK::Engine::Util::TAKErr GLWorkers_doGLThreadWork() NOTHROWS;


		}
	}
}

#endif