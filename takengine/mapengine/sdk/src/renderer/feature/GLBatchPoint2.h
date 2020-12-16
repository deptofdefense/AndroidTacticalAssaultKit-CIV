#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT2_H_INCLUDED

#include "util/FutureTask.h"
#include "core/AtakMapView.h"
#include "feature/Point.h"
#include "renderer/AsyncBitmapLoader2.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/feature/GLBatchGeometry2.h"
#include "renderer/GLNinePatch.h"
#include "thread/Mutex.h"
#include "util/IO.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                class ENGINE_API GLBatchGeometryRenderer;

                class ENGINE_API GLBatchPoint2 : public GLBatchGeometry2
                {
                private :
                    struct StyleUpdater;
                public:
                    static atakmap::renderer::GLNinePatch *small_nine_patch_;

                    static float iconAtlasDensity;// = atakmap::core::AtakMapView::DENSITY;

                    static const double defaultLabelRenderScale;// = (1.0 / 250000.0);

                    //TODO--static String ^defaultIconUri = "asset:/icons/reference_point.png";

                    /*TODO--static IDictionary<String^, System::Tuple<atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>^, array<int>^>^> ^iconLoaders = gcnew Dictionary<String^, System::Tuple<atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>^, array<int>^>^>();

                    static GLText ^defaultText = nullptr;*/

                    struct IconLoaderEntry {
                        TAK::Engine::Renderer::AsyncBitmapLoader2::Task task;
                        int serialNumber = 0;
                    };

                    typedef std::map<std::string, IconLoaderEntry> IconLoadersMap;
                    static IconLoadersMap iconLoaders;

                    /// <summary>
                    ///*********************************************************************** </summary>

                    Util::TAKErr getIconSize(size_t *width, size_t *height) const NOTHROWS;
                    
                public:
                    double latitude = 0;
                    double longitude = 0;

                protected:
                    friend class ENGINE_API GLBatchGeometryRenderer2;
                public :
                    int color = 0;
                    float colorR = 0;
                    float colorG = 0;
                    float colorB = 0;
                    float colorA = 0;
                public :
                    Port::String iconUri;
                    bool iconDirty;
                    float iconRotation;
                    float labelRotation;
                    bool labelBackground;
                    bool absoluteLabelRotation;
                    bool absoluteIconRotation;
                    int64_t labelFadeTimer;
                public :
                    int64_t textureKey = 0;
                    int textureId = 0;
                    int textureIndex = 0;
                protected:
                    TAK::Engine::Renderer::AsyncBitmapLoader2::Task iconLoader;
                public :
                    std::string iconLoaderUri;

                    atakmap::util::MemBufferT<float> texCoords;
                    atakmap::util::MemBufferT<float> verts;

                public:
                    GLBatchPoint2(TAK::Engine::Core::RenderContext &surface);
                    virtual ~GLBatchPoint2();

                    int64_t getTextureKey() const;

                private:
                    Util::TAKErr setIcon(const char *uri, int icon_color) NOTHROWS;

                    atakmap::renderer::GLNinePatch *getSmallNinePatch() NOTHROWS;

                protected:
                    virtual Util::TAKErr checkIcon(TAK::Engine::Core::RenderContext &render_context) NOTHROWS;

                public:
                    virtual void draw(const atakmap::renderer::map::GLMapView *ortho) override;

                    virtual void release() override;

                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS;
                public:
                    Util::TAKErr setGeometry(const atakmap::feature::Point &point) NOTHROWS;
                public :
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS override;

                private:
                    Util::TAKErr setStyleImpl(const atakmap::feature::Style *value) NOTHROWS;

                    /// <summary>
                    ///*********************************************************************** </summary>
                    // GL Map Batchable
                    
                public:
                    // determine if the point is non-batchable base on attributes alone without the need of testing on the renderer
                    bool hasBatchProhibitiveAttributes() const NOTHROWS;

                    virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

                    virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch) override;

                    /// <summary>
                    ///*********************************************************************** </summary>

                public:
                    static Util::TAKErr getOrFetchIcon(TAK::Engine::Core::RenderContext &surface, GLBatchPoint2 *point) NOTHROWS;

                private:
                    static Util::TAKErr dereferenceIconLoader(const char *iconUri) NOTHROWS;

                    static Util::TAKErr dereferenceIconLoaderNoSync(const char *iconUri) NOTHROWS;

                    static void setStyleRunnable(void *opaque) NOTHROWS;

                    static Thread::Mutex staticMutex;

                    static float defaultFontSize;
                private :
                    TAK::Engine::Renderer::GLTextureAtlas2 *iconAtlas;
                };

                struct GLBatchPoint2::StyleUpdater : GLBatchGeometry2::Updater
                {
                public:
                    StyleUpdater(GLBatchPoint2 &owner, std::shared_ptr<const atakmap::feature::Style> &style);

                    std::shared_ptr<const atakmap::feature::Style> style;
                };

                typedef std::unique_ptr<GLBatchPoint2, void(*)(const GLBatchGeometry2 *)> GLBatchPointPtr;
                typedef std::unique_ptr<const GLBatchPoint2, void(*)(const GLBatchGeometry2 *)> GLBatchPointPtr_const;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT2_H_INCLUDED
