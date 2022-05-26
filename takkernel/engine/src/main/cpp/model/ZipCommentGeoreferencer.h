#ifndef TAK_ENGINE_MODEL_ZIPCOMMENTGEOREFERENCER_H_INCLUDED
#define TAK_ENGINE_MODEL_ZIPCOMMENTGEOREFERENCER_H_INCLUDED

#include "model/SceneInfo.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API ZipCommentGeoreferencer : public Georeferencer {
            public:
                ZipCommentGeoreferencer() NOTHROWS;
                virtual ~ZipCommentGeoreferencer() NOTHROWS;
                TAK::Engine::Util::TAKErr locate(SceneInfo &sceneInfo) NOTHROWS;
                static bool isGeoReferenced(const char *uri) NOTHROWS;
                static TAK::Engine::Util::TAKErr removeGeoReference(const char *uri) NOTHROWS;
            };
        }
    }
}

#endif
