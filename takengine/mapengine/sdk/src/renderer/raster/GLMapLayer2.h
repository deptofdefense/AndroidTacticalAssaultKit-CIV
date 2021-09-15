#ifndef TAK_ENGINE_RENDERER_GLMAPLAYER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLMAPLAYER2_H_INCLUDED

#include "renderer/core/GLMapRenderable2.h"
#include "raster/DatasetDescriptor.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {

                class ENGINE_API GLMapLayer2 : public virtual Core::GLMapRenderable2 {
                  public:
                    virtual ~GLMapLayer2() NOTHROWS = 0;
                    virtual const char *getLayerUri() const NOTHROWS = 0;

                    virtual const atakmap::raster::DatasetDescriptor *getInfo() const NOTHROWS = 0;
                    virtual Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS = 0;
                };

                typedef std::unique_ptr<GLMapLayer2, void (*)(const GLMapLayer2 *)> GLMapLayer2Ptr;
            }
        }
    }
}




#endif

