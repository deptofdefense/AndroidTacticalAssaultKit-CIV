#include "renderer/core/GLLayerFactory2.h"

#include "thread/RWMutex.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Thread;

namespace
{
    struct Spi2Entry
    {
        std::shared_ptr<GLLayerSpi2> spi;
        std::size_t insert {0};
        int priority {0};
    };

    struct Spi2EntryComp
    {
        bool operator()(const Spi2Entry &a, const Spi2Entry &b) const
        {
            if (a.priority > b.priority)
                return true;
            else if (a.priority < b.priority)
                return false;
            else
                return (a.insert < b.insert);
        }
    };

    class GLMapRenderable2Layer : public GLLayer2, Layer2::VisibilityListener
    {
    public :
        GLMapRenderable2Layer(Layer2 &subject, GLMapRenderable2Ptr &&impl) NOTHROWS;
    public :
        void start() NOTHROWS override;
        void stop() NOTHROWS override;
        int getRenderPass() NOTHROWS override;
        void draw(const GLMapView2 &view, const int renderPass) NOTHROWS override;
        void release() NOTHROWS override;
    public :
        Layer2 &getSubject() NOTHROWS override;
    public :
        TAKErr layerVisibilityChanged(const Layer2 &layer_subject, const bool visible) NOTHROWS override;
    private :
        Layer2 &subject_;
        GLMapRenderable2Ptr impl_;
        bool visible_;
    };

    std::set<Spi2Entry, Spi2EntryComp> &spis()
    {
        static std::set<Spi2Entry, Spi2EntryComp> s;
        return s;
    }

    RWMutex &mutex()
    {
        static RWMutex m;
        return m;
    }

    std::size_t &inserts()
    {
        static std::size_t i;
        return i;
    }
}

TAKErr TAK::Engine::Renderer::Core::GLLayerFactory2_registerSpi(const std::shared_ptr<GLLayerSpi2> &spi, const int priority) NOTHROWS
{
    WriteLock lock(mutex());

    std::set<Spi2Entry, Spi2EntryComp> &registry = spis();

    Spi2Entry entry;
    entry.spi = spi;
    entry.priority = priority;
    entry.insert = inserts()++;

    registry.insert(entry);
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Core::GLLayerFactory2_unregisterSpi(const GLLayerSpi2 &spi) NOTHROWS
{
    WriteLock lock(mutex());

    std::set<Spi2Entry, Spi2EntryComp> &registry = spis();
    std::set<Spi2Entry, Spi2EntryComp>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++)
    {
        if ((*it).spi.get() == &spi)
        {
            registry.erase(it);
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}
TAKErr TAK::Engine::Renderer::Core::GLLayerFactory2_create(GLLayer2Ptr &value, GLMapView2 &view, TAK::Engine::Core::Layer2 &subject) NOTHROWS
{
    ReadLock lock(mutex());

    std::set<Spi2Entry, Spi2EntryComp> &registry = spis();
    std::set<Spi2Entry, Spi2EntryComp>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++)
    {
        if ((*it).spi->create(value, view, subject) == TE_Ok)
            return TE_Ok;
    }

    return TE_InvalidArg;
}

TAKErr TAK::Engine::Renderer::Core::GLLayerFactory2_create(GLLayer2Ptr &value, Layer2 &subject, GLMapRenderable2Ptr &&renderer) NOTHROWS
{
    if (!renderer.get())
        return TE_InvalidArg;
    value = GLLayer2Ptr(new GLMapRenderable2Layer(subject, std::move(renderer)), Memory_deleter_const<GLLayer2, GLMapRenderable2Layer>);
    return TE_Ok;
}

namespace
{
    GLMapRenderable2Layer::GLMapRenderable2Layer(Layer2 &subject_, GLMapRenderable2Ptr &&impl_) NOTHROWS :
        subject_(subject_),
        impl_(std::move(impl_)),
        visible_(subject_.isVisible())
    {}

    void GLMapRenderable2Layer::start() NOTHROWS
    {
        subject_.addVisibilityListener(this);
        this->visible_ = subject_.isVisible();
        impl_->start();
    }
    void GLMapRenderable2Layer::stop() NOTHROWS
    {
        subject_.removeVisibilityListener(this);
        impl_->stop();
    }
    int GLMapRenderable2Layer::getRenderPass() NOTHROWS
    {
        return impl_->getRenderPass();
    }
    void GLMapRenderable2Layer::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
    {
        if (!visible_)
            return;
        impl_->draw(view, renderPass);
    }
    void GLMapRenderable2Layer::release() NOTHROWS
    {
        impl_->release();
    }
    Layer2 &GLMapRenderable2Layer::getSubject() NOTHROWS
    {
        return subject_;
    }
    TAKErr GLMapRenderable2Layer::layerVisibilityChanged(const Layer2 &layer_subject, const bool visible) NOTHROWS
    {
        this->visible_ = visible;
        return TE_Ok;
    }
}
