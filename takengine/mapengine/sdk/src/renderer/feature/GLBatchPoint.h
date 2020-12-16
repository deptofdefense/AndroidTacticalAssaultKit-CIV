#pragma once

#include "util/FutureTask.h"
#include "core/AtakMapView.h"
#include "renderer/GLText.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/feature/GLBatchGeometry.h"
#include "renderer/GLNinePatch.h"

namespace atakmap {
    
    namespace feature {
        class Point;
    }

    namespace renderer {
        namespace feature {
            
            class GLBatchGeometryRenderer;

            class GLBatchPoint: public GLBatchGeometry {
                
            public:
                static TAK::Engine::Renderer::GLTextureAtlas2 *ICON_ATLAS;// = gcnew GLTextureAtlas(1024, safe_cast<int>(Math::Ceiling(atakmap::cpp_cli::core::AtakMapView::DENSITY * 64)));
                
                static atakmap::renderer::GLNinePatch *smallNinePatch;

                static float iconAtlasDensity;// = atakmap::core::AtakMapView::DENSITY;

                static const double defaultLabelRenderScale;// = (1.0 / 250000.0);

                //TODO--static String ^defaultIconUri = "asset:/icons/reference_point.png";

                /*TODO--static IDictionary<String^, System::Tuple<atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>^, array<int>^>^> ^iconLoaders = gcnew Dictionary<String^, System::Tuple<atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>^, array<int>^>^>();
                 
                static GLText ^defaultText = nullptr;*/

                struct IconLoaderEntry {
                    atakmap::util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>> task;
                    int serialNumber;
                };
                
                typedef std::map<std::string, IconLoaderEntry> IconLoadersMap;
                static IconLoadersMap iconLoaders;
                
                /// <summary>
                ///*********************************************************************** </summary>

            public:
                double latitude = 0;
                double longitude = 0;

            protected:
                friend GLBatchGeometryRenderer;
                int color = 0;
                float colorR = 0;
                float colorG = 0;
                float colorB = 0;
                float colorA = 0;

                std::string iconUri;
                int64_t textureKey = 0;
                int textureId = 0;
                int textureIndex = 0;
                atakmap::util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>> iconLoader;
                std::string iconLoaderUri;

                atakmap::util::MemBufferT<float> texCoords;
                atakmap::util::MemBufferT<float> verts;

            public:
                GLBatchPoint(atakmap::renderer::GLRenderContext *surface);
                
                inline int64_t getTextureKey() const { return textureKey; }

            private:
                void setIcon(const char *uri, int color);

                atakmap::renderer::GLNinePatch *getSmallNinePatch();

            protected:
                virtual void checkIcon(atakmap::renderer::GLRenderContext *surface);

            public:
                virtual void draw(const atakmap::renderer::map::GLMapView *ortho) override;

                virtual void release() override;

            protected:
                virtual void setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type) override;

            public:
                void setGeometry(atakmap::feature::Point *point);
            private :
                static void setGeometryImpl(void *opaque);
            public:
                virtual void setStyle(atakmap::feature::Style *value) override;
                
            private:
                static void setStyleRunnable(void *args);
               
                /// <summary>
                ///*********************************************************************** </summary>
                // GL Map Batchable

            public:
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

                virtual void batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch) override;

                /// <summary>
                ///*********************************************************************** </summary>

            public:
                static void getOrFetchIcon(atakmap::renderer::GLRenderContext *surface, GLBatchPoint *point);

            private:
                static void dereferenceIconLoader(const char *iconUri);

                static void dereferenceIconLoaderNoSync(const char *iconUri);
                
                static TAK::Engine::Thread::Mutex staticMutex;
            };
        }
    }
}

