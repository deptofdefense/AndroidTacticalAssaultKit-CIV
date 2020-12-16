#ifndef ATAKMAP_RENDERER_GLRESOLVABLEMAPRENDERABLE_H_INCLUDED
#define ATAKMAP_RENDERER_GLRESOLVABLEMAPRENDERABLE_H_INCLUDED

namespace atakmap
{
    namespace renderer
    {
        namespace map {
            class GLMapView;

            class GLResolvableMapRenderable {
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

                virtual ~GLResolvableMapRenderable() {};
                virtual State getState() = 0;
                virtual void suspend() = 0;
                virtual void resume() = 0;

                virtual void draw(const GLMapView *view) = 0;
                virtual void release() = 0;
            };
        }
    }
}

#endif
