#ifndef TAK_ENGINE_RENDERER_CORE_GLDIRTYREGION_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLDIRTYREGION_H_INCLUDED

#include <vector>

#include "feature/Envelope2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLDirtyRegion
                {
                private :
                    struct Region
                    {
                        TAK::Engine::Feature::Envelope2 mbb;
                        std::vector<TAK::Engine::Feature::Envelope2> regions;
                    };
                public :
                    GLDirtyRegion() NOTHROWS;
                    ~GLDirtyRegion() NOTHROWS;
                public :
                    void clear() NOTHROWS;
                    void push_back(const double ulLat, const double ulLng, const double lrLat, const double lrLng) NOTHROWS;
                    void push_back(const TAK::Engine::Feature::Envelope2 &region) NOTHROWS;
                    bool intersects(const TAK::Engine::Feature::Envelope2 &region) const NOTHROWS;
                    bool empty() const NOTHROWS;
                    std::size_t size() const NOTHROWS;
                    void compact() NOTHROWS;
                public :
                    GLDirtyRegion& operator =(const GLDirtyRegion &other) NOTHROWS;
                private :
                    Region east;
                    Region west;
                };
            }
        }
    }
}
#endif // TAK_ENGINE_RENDERER_CORE_GLDIRTYREGION_H_INCLUDED
