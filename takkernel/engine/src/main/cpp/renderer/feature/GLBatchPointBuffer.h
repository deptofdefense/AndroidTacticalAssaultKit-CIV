#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINTBUFFER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINTBUFFER_H_INCLUDED

#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/GLTextureAtlas.h"
#include "util/MemBuffer2.h"
#include "util/Error.h"
#include "math/Point2.h"

#include <stack>

#define MAX_BUFFERED_3D_POINTS 20000

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                using namespace TAK::Engine::Renderer::Core;
                using namespace atakmap::renderer;

                class GLBatchPointBuffer {
                private:
                    static const int DEFAULT_BUFFER_SIZE;

                    struct BufferedPoint
                    {
                        BufferedPoint(GLBatchPoint3 * point, GLuint offset)
                            : batchPoint(point), bufferOffset(offset), dirty(true)
                        {

                        }

                        GLBatchPoint3 * batchPoint;
                        /** the offset into the buffer where the point data is stored */
                        GLuint bufferOffset;
                        /** if true, the buffer content does not match the current state */
                        bool dirty;
                    };

                    struct vbo_t
                    {
                        unsigned int handle;
                        std::size_t capacity;

                        vbo_t() NOTHROWS : handle(0u), capacity(0u)
                        {}
                    };
                public:
                    GLBatchPointBuffer();
                    ~GLBatchPointBuffer();

                    void invalidate();
                    void validate(const GLMapView2 & view, GLBatchPoint3 * point, GLTextureAtlas * iconAtlas, const TAK::Engine::Math::Matrix2 & invLocalFrame);
                    void purge();
                    /** */
                    void commit();
                    size_t size();
                    bool empty();
                    bool isDirty();
                    const void *get() const NOTHROWS;
                public:
                    unsigned int vbo;
                private:
                    vbo_t front;
                    vbo_t back;
                    size_t bufferSize;
                    /** the vertex data buffer, XYZUV, for each point */
                    std::vector<float> vertices;
                    std::vector<BufferedPoint> points;
                    std::vector<BufferedPoint> invalidPoints;
                    std::stack<size_t> offsetStack;
                    bool dirty;
                };
            }
        }
    }
}

#endif