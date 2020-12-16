#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY3_H_INCLUDED

#include <memory>

#include "core/RenderContext.h"
#include "feature/Feature2.h"
#include "feature/Style.h"
#include "port/Platform.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLMapBatchable2.h"
#include "renderer/core/GLMapView2.h"
#include "thread/Mutex.h"
#include "util/DataInput2.h"
#include "util/AttributeSet.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometry3 : 
                    public TAK::Engine::Renderer::Core::GLMapRenderable2, 
                    public TAK::Engine::Renderer::Core::GLMapBatchable2
                {
                public :
                    typedef std::unique_ptr<TAK::Engine::Util::MemoryInput2, void(*)(const TAK::Engine::Util::MemoryInput2 *)> BlobPtr;
                protected:
                    TAK::Engine::Core::RenderContext &surface;
                public:
                    Port::String name;
                    int zOrder;
                    int64_t featureId;
                    int lod;
                    int subid;
                    int64_t version;
                    TAK::Engine::Feature::AltitudeMode altitudeMode;
                    double extrude;
                protected:
                    GLBatchGeometry3(TAK::Engine::Core::RenderContext &surface, const int zOrder) NOTHROWS;
                public: 
                    virtual ~GLBatchGeometry3() NOTHROWS;
                public:
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val, TAK::Engine::Feature::GeometryPtr_const &&geom,
                                              const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val,
                                              const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS;
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val, BlobPtr &&geomBlob,
                                              const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const int type,
                                              const int lod_val, const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS;

                    virtual Util::TAKErr setName(const char *name_val) NOTHROWS;

                    virtual Util::TAKErr setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS = 0;
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS = 0;

                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS;

                    virtual Util::TAKErr setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS;

                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderPass) NOTHROWS override = 0;
                    virtual int getRenderPass() NOTHROWS override;
                    virtual void start() NOTHROWS override;
                    virtual void stop() NOTHROWS override;

                    virtual void release() NOTHROWS override = 0;

                    virtual Util::TAKErr batch(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderPass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS  override= 0;

                public:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS = 0;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS = 0;
                    virtual Util::TAKErr setNameImpl(const char *name_val) NOTHROWS;
                    virtual Util::TAKErr setAltitudeModeImpl(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS;
                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS;
                public :
                    virtual Util::TAKErr setVisible(const bool &visible) NOTHROWS;
                protected :
                    int renderPass;
                };

                typedef std::unique_ptr<GLBatchGeometry3, void(*)(const GLBatchGeometry3 *)> GLBatchGeometry3Ptr;
                typedef std::unique_ptr<const GLBatchGeometry3, void(*)(const GLBatchGeometry3 *)> GLBatchGeometry3Ptr_const;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY2_H_INCLUDED
