#ifndef TAK_ENGINE_RENDERER_GLRESOLVABLE_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLRESOLVABLE_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {

                class GLResolvable {
                  protected:
                    virtual ~GLResolvable() NOTHROWS = 0;
                  public:
                    enum State {
                        /** resolution has not yet started */
                        UNRESOLVED,
                        /** resolution has started but not yet finished */
                        RESOLVING,
                        /** successfully resolved */
                        RESOLVED,
                        /** could not be resolved */
                        UNRESOLVABLE,
                        /** resolution suspended */
                        SUSPENDED,
                    };

                    virtual State getState() = 0;
                    virtual void suspend() = 0;
                    virtual void resume() = 0;
                    
                    static const char *getNameForState(State state) NOTHROWS;

                };


            }
        }
    }
}

#endif
