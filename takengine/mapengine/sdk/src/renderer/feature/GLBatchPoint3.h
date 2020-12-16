#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT3_H_INCLUDED

#include "core/AtakMapView.h"
#include "core/RenderContext.h"
#include "feature/AltitudeMode.h"
#include "feature/Point.h"
#include "renderer/AsyncBitmapLoader2.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/core/GLLabel.h"
#include "renderer/feature/GLBatchGeometry3.h"
#include "renderer/GLNinePatch.h"
#include "thread/Mutex.h"
#include "util/FutureTask.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                class GLBatchGeometryRenderer3;
                class GLBatchPointBuffer;

                class GLBatchPoint3 : public GLBatchGeometry3
                {
                public:
                    struct IconLoaderEntry {
                        TAK::Engine::Renderer::AsyncBitmapLoader2::Task task;
                        int serialNumber = 0;
                    };

                    typedef std::map<std::string, IconLoaderEntry> IconLoadersMap;
                    static IconLoadersMap iconLoaders;
                public:
                    GLBatchPoint3(TAK::Engine::Core::RenderContext &surface);
                    virtual ~GLBatchPoint3();

                    int64_t getTextureKey() const;

                private:
                    Util::TAKErr setIcon(const char *uri, int icon_color) NOTHROWS;

                    atakmap::renderer::GLNinePatch *getSmallNinePatch() NOTHROWS;

                protected:
                    virtual Util::TAKErr checkIcon(TAK::Engine::Core::RenderContext &render_context) NOTHROWS;

                public:
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val, TAK::Engine::Feature::GeometryPtr_const &&geom, const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS override;
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val, BlobPtr &&geomBlob, const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const int type, const int lod_val, const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS override;
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &ortho, const int render_pass) NOTHROWS override;
                    virtual void release() NOTHROWS override;
                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS override;
                    virtual Util::TAKErr setAltitudeModeImpl(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS override;
                public:
                    Util::TAKErr setGeometry(const atakmap::feature::Point &point) NOTHROWS;
                    virtual Util::TAKErr setNameImpl(const char* name_val) NOTHROWS override;
                public :
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS override;
                    virtual Util::TAKErr setVisible(const bool &visible) NOTHROWS override;

                private:
                    Util::TAKErr setStyleImpl(const atakmap::feature::Style *value) NOTHROWS;

                    /// <summary>
                    ///*********************************************************************** </summary>
                    // GL Map Batchable
                    
                public:
                    // determine if the point is non-batchable base on attributes alone without the need of testing on the renderer
                    bool hasBatchProhibitiveAttributes() const NOTHROWS;

                    virtual Util::TAKErr batch(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderpass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS override;
                    virtual Util::TAKErr batchLabels(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, GLRenderBatch2 & batch);
                private :
                    bool validateProjectedLocation(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;
                public :
                    /// <summary>
                    ///*********************************************************************** </summary>

                    Util::TAKErr getIconSize(size_t *width, size_t *height) const NOTHROWS;

                    /// <summary>
                    ///*********************************************************************** </summary>

                public:
                    static Util::TAKErr getOrFetchIcon(TAK::Engine::Core::RenderContext &surface, GLBatchPoint3 &point) NOTHROWS;
                private:
                    static Util::TAKErr dereferenceIconLoader(const char *iconUri) NOTHROWS;
                    static Util::TAKErr dereferenceIconLoaderNoSync(const char *iconUri) NOTHROWS;
                    static void setStyleRunnable(void *opaque);
                    static Thread::Mutex staticMutex;
                    static float defaultFontSize;
                public:
                    static atakmap::renderer::GLNinePatch *smallNinePatch;
                    static float iconAtlasDensity;// = atakmap::core::AtakMapView::DENSITY;
                    static const double defaultLabelRenderScale;// = (1.0 / 250000.0);
                public:
                    double latitude;
                    double longitude;
                    double screen_x;
                    double screen_y;
                    double altitude;
                    double posProjectedEl;
                    int posProjectedSrid;
                    Math::Point2<double> posProjected;
                    int surfaceProjectedSrid;
                    int terrainVersion;
                    Math::Point2<double> surfaceProjected;
                    int color;
                    float colorR;
                    float colorG;
                    float colorB;
                    float colorA;
                    Port::String iconUri;
                    bool iconDirty;
                    float iconRotation;
                    bool absoluteIconRotation;

                    std::vector<TAK::Engine::Renderer::Core::GLLabel> labels;
                    bool rotatedLabels;
                    
                    int64_t labelFadeTimer;
                    int64_t textureKey;
                    int textureId;
                    int textureIndex;
                    Port::String iconLoaderUri;
                    Util::array_ptr<float> texCoords;
                    Util::array_ptr<float> verts;
                    uint32_t labelId;
                    float iconOffsetX;
                    float iconOffsetY;
                protected:
                    TAK::Engine::Renderer::AsyncBitmapLoader2::Task iconLoader;
                private :
                    TAK::Engine::Renderer::GLTextureAtlas2 *iconAtlas;

                // friend declarations
                protected:
                    friend GLBatchGeometryRenderer3;
                    friend GLBatchPointBuffer;
                };

                typedef std::unique_ptr<GLBatchPoint3, void(*)(const GLBatchGeometry3 *)> GLBatchPoint3Ptr;
                typedef std::unique_ptr<const GLBatchPoint3, void(*)(const GLBatchGeometry3 *)> GLBatchPoint3Ptr_const;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOINT2_H_INCLUDED
