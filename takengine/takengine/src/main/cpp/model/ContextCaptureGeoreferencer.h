
#ifndef TAK_ENGINE_MODEL_CONTEXTCAPTUREGEOREFERENCER_H_INCLUDED
#define TAK_ENGINE_MODEL_CONTEXTCAPTUREGEOREFERENCER_H_INCLUDED

#include "model/SceneInfo.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API ContextCaptureGeoreferencer : public Georeferencer {
            public:
                ContextCaptureGeoreferencer() NOTHROWS;
                virtual ~ContextCaptureGeoreferencer() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr locate(SceneInfo &sceneInfo) NOTHROWS;
				static TAK::Engine::Util::TAKErr locateMetadataFile(TAK::Engine::Port::String &outPath, const char *objPath) NOTHROWS;
				static TAK::Engine::Util::TAKErr getDatasetName(Port::String &outName, const char *objPath) NOTHROWS;
            };

            ENGINE_API Util::TAKErr ContextCapture_resolution(double *value, const std::size_t levelOfDetail) NOTHROWS;
            ENGINE_API Util::TAKErr ContextCapture_levelOfDetail(std::size_t *value, const double resolution, const int round) NOTHROWS;
        }
    }
}

#endif