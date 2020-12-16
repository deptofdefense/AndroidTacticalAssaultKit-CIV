#include "renderer/core/LegacyAdapters.h"

#include "core/LegacyAdapters.h"
#include "renderer/map/GLMapView.h"
#include "util/Memory.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::map::layer;

namespace
{
    class GLMapRenderable2Adapter : public virtual GLMapRenderable2
    {
    public :
        GLMapRenderable2Adapter(GLMapRenderablePtr &&value) NOTHROWS;
    public :
        void draw(const GLMapView2& view, const int renderPass) NOTHROWS override;
        void release() NOTHROWS override;
        int getRenderPass() NOTHROWS override;
        void start() NOTHROWS override;
        void stop() NOTHROWS override;
    public :
        GLMapRenderablePtr opaque;
    };

    class GLLayer2Adapter : public GLMapRenderable2Adapter,
                            public virtual GLLayer2
    {
    public:
        GLLayer2Adapter(GLLayerPtr &&value, const std::shared_ptr<Layer2> &subject, const std::shared_ptr<GLMapView> &view) NOTHROWS;
    public:
        Layer2 &getSubject() NOTHROWS override;
    public:
        GLLayerPtr opaque;
        std::shared_ptr<Layer2> subject;
        std::shared_ptr<GLMapView> view;
    };

    class GLLayerSpi2Adapter : public GLLayerSpi2
    {
    public:
        GLLayerSpi2Adapter(GLLayerSpiPtr &&opaque) NOTHROWS;
    public:
        TAKErr create(GLLayer2Ptr &value, GLMapView2 &renderer, Layer2 &subject) NOTHROWS override;
    public :
        GLLayerSpiPtr opaque;
    };
}

TAKErr TAK::Engine::Renderer::Core::LegacyAdapters_adapt(GLMapRenderable2Ptr &value, GLMapRenderablePtr &&legacy) NOTHROWS
{
    if (!legacy.get())
        return TE_InvalidArg;
    value = GLMapRenderable2Ptr(new GLMapRenderable2Adapter(std::move(legacy)), Memory_deleter_const<GLMapRenderable2, GLMapRenderable2Adapter>);
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Core::LegacyAdapters_adapt(GLLayer2Ptr &value, GLLayerPtr &&legacy) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!legacy.get())
        return TE_InvalidArg;
    std::shared_ptr<Layer2> subject;
    code = LegacyAdapters_adapt(subject, LayerPtr(legacy->getSubject(), Memory_leaker_const<Layer>));
    TE_CHECKRETURN_CODE(code);

    value = GLLayer2Ptr(new GLLayer2Adapter(std::move(legacy),
                                            subject,
                                            std::shared_ptr<GLMapView>()),
                        Memory_deleter_const<GLLayer2, GLLayer2Adapter>);
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Core::LegacyAdapters_adapt(GLLayerSpi2Ptr &value, GLLayerSpiPtr &&legacy) NOTHROWS
{
    if (!legacy.get())
        return TE_InvalidArg;
    value = GLLayerSpi2Ptr(new GLLayerSpi2Adapter(std::move(legacy)), Memory_deleter_const<GLLayerSpi2, GLLayerSpi2Adapter>);
    return TE_Ok;
}

namespace
{

    /*************************************************************************/
    // GLMapRenderable2Adapter

    GLMapRenderable2Adapter::GLMapRenderable2Adapter(GLMapRenderablePtr &&opaque_) NOTHROWS :
        opaque(std::move(opaque_))
    {}

    void GLMapRenderable2Adapter::draw(const GLMapView2& view, const int renderPass) NOTHROWS
    {
        if (!(renderPass&getRenderPass()))
            return;
       
        // XXX - don't like having to use const-cast here, but usage in
        //       constructor is const friendly and so is subsequent invocation
        std::shared_ptr<GLMapView> legacyView(new GLMapView(std::move(GLMapView2Ptr(&const_cast<GLMapView2 &>(view), Memory_leaker_const<GLMapView2>))));
        opaque->draw(legacyView.get());
    }
    void GLMapRenderable2Adapter::release() NOTHROWS
    {
        opaque->release();
    }
    int GLMapRenderable2Adapter::getRenderPass() NOTHROWS
    {
        return GLMapView2::Surface;
    }
    void GLMapRenderable2Adapter::start() NOTHROWS
    {
        opaque->start();
    }
    void GLMapRenderable2Adapter::stop() NOTHROWS
    {
        opaque->stop();
    }

    /*************************************************************************/
    // GLLayer2Adapter

    GLLayer2Adapter::GLLayer2Adapter(GLLayerPtr &&opaque_, const std::shared_ptr<Layer2> &subject_, const std::shared_ptr<GLMapView> &view_) NOTHROWS :
        GLMapRenderable2Adapter(GLMapRenderablePtr(opaque_.get(), Memory_leaker_const<GLMapRenderable>)),
        opaque(std::move(opaque_)),
        subject(subject_),
        view(view_)
    {}

    Layer2 &GLLayer2Adapter::getSubject() NOTHROWS
    {
        return *subject;
    }

    /*************************************************************************/
    // GLLayerSpi2Adapter : public GLLayerSpi2

    GLLayerSpi2Adapter::GLLayerSpi2Adapter(GLLayerSpiPtr &&opaque_) NOTHROWS :
        opaque(std::move(opaque_))
    {}
    
    TAKErr GLLayerSpi2Adapter::create(GLLayer2Ptr &value, GLMapView2 &renderer, Layer2 &subject) NOTHROWS
    {
        TAKErr code(TE_Ok);

        std::shared_ptr<Layer> legacyLayer(nullptr);
        code = LegacyAdapters_find(legacyLayer, subject);
        TE_CHECKRETURN_CODE(code);

        std::shared_ptr<GLMapView> legacyView(new GLMapView(std::move(GLMapView2Ptr(&renderer, Memory_leaker_const<GLMapView2>))));

        GLLayerPtr retval(opaque->create(GLLayerSpiArg(legacyView.get(), legacyLayer.get())), Memory_deleter_const<GLLayer>);
        if (!retval.get())
            return TE_Err;

        value = GLLayer2Ptr(new GLLayer2Adapter(std::move(retval),
                                                Layer2Ptr(&subject, Memory_leaker_const<Layer2>),
                                                legacyView),
                            Memory_deleter_const<GLLayer2, GLLayer2Adapter>);
        return code;
    }
}

#ifdef _MSC_VER
#pragma warning(pop)
#endif