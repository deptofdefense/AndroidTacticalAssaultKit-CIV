#ifndef TAK_ENGINE_MODEL_CONTEXTCAPTURESCENESPI_H_INCLUDED
#define TAK_ENGINE_MODEL_CONTEXTCAPTURESCENESPI_H_INCLUDED

#include "model/Scene.h"

namespace TAK {
	namespace Engine {
		namespace Model {
			class ENGINE_API ContextCaptureSceneSpi : public SceneSpi {
			public:
				virtual ~ContextCaptureSceneSpi() NOTHROWS;

				virtual const char *getType() const NOTHROWS;
				virtual int getPriority() const NOTHROWS;
				virtual TAK::Engine::Util::TAKErr create(ScenePtr &scene, const char *URI, Util::ProcessingCallback *callbacks, const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS;
			};
		}
	}
}

#endif