#ifndef ATAKMAP_RENDERER_GLRENDERCONTEXT_H_INCLUDED
#define ATAKMAP_RENDERER_GLRENDERCONTEXT_H_INCLUDED

namespace atakmap
{
    namespace renderer
    {
        class GLRenderContext {
        public:
            typedef void (*GLRunnable)(void *opaqueData);
            virtual ~GLRenderContext() {};
            virtual bool isGLThread() = 0;
            virtual void runOnGLThread(GLRunnable runnable, void *opaque) = 0;
        };
    }
}


#endif
