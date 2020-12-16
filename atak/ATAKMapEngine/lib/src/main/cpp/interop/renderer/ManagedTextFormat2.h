#ifndef TAKENGINEJNI_INTEROP_RENDERER_MANAGEDTEXTFORMAT2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_MANAGEDTEXTFORMAT2_H_INCLUDED

#include <jni.h>

#include <renderer/GLText2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Renderer {
            class ManagedTextFormat2 : public TAK::Engine::Renderer::TextFormat2
            {
            public :
                ManagedTextFormat2(JNIEnv &env, jobject mtextformat, jobject mglyphrenderer = NULL) NOTHROWS;
                ~ManagedTextFormat2() NOTHROWS;
            public:
                float getStringWidth(const char *text) NOTHROWS;
                float getCharPositionWidth(const char *text, int position) NOTHROWS;
                float getCharWidth(const unsigned int chr) NOTHROWS;
                float getCharHeight() NOTHROWS;
                float getDescent() NOTHROWS;
                float getStringHeight(const char *text) NOTHROWS;
                float getBaselineSpacing() NOTHROWS;
                int getFontSize() NOTHROWS;

                TAK::Engine::Util::TAKErr loadGlyph(TAK::Engine::Renderer::BitmapPtr &value, const unsigned int c) NOTHROWS;
            private :
                jobject mimpl;
                jobject mglyphRenderer;
            };
        }
    }
}

#endif
