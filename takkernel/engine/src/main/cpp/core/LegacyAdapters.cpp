#include "core/LegacyAdapters.h"

#include <map>

#include "core/GeoPoint.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Memory.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::math;

namespace {
    class Layer2Adapter : public Layer2
    {
    private :
        class VisibilityListenerAdapter;
    public :
        Layer2Adapter(const std::shared_ptr<Layer> &impl) NOTHROWS;
        ~Layer2Adapter() NOTHROWS override;
    public:
        const char *getName() const NOTHROWS override;
        bool isVisible() const NOTHROWS override;
        void setVisible(const bool v) NOTHROWS override;
        TAKErr addVisibilityListener(Layer2::VisibilityListener *l) NOTHROWS override;
        TAKErr removeVisibilityListener(Layer2::VisibilityListener *l) NOTHROWS override;
        TAKErr getExtension(void **value, const char *extensionName) const NOTHROWS override;
    public :
        std::shared_ptr<Layer> impl;
    private :
        Mutex mutex;
        std::map<Layer2::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>> listeners;
    };

    class Layer2Adapter::VisibilityListenerAdapter : public Layer::VisibilityListener
    {
    public:
        VisibilityListenerAdapter(Layer2 &owner, Layer2::VisibilityListener &impl) NOTHROWS;
    public:
        void visibilityChanged(Layer &layer) override;
    private:
        Layer2 &owner;
        Layer2::VisibilityListener &impl;
    };

    class LayerAdapter : public Layer
    {
    private:
        class VisibilityListenerAdapter;
    public:
        LayerAdapter(const std::shared_ptr<Layer2> &impl) NOTHROWS;
        ~LayerAdapter() NOTHROWS override;
    public:
        const char *getName() const NOTHROWS override;
        bool isVisible() const NOTHROWS override;
        void setVisible(bool v) NOTHROWS override;
        void addVisibilityListener(Layer::VisibilityListener *l) NOTHROWS override;
        void removeVisibilityListener(Layer::VisibilityListener *l) NOTHROWS override;
    public:
        std::shared_ptr<Layer2> impl;
    private:
        Mutex mutex;
        std::map<Layer::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>> listeners;
    };

    class LayerAdapter::VisibilityListenerAdapter : public Layer2::VisibilityListener
    {
    public:
        VisibilityListenerAdapter(Layer &owner, Layer::VisibilityListener &impl) NOTHROWS;
    public:
        TAKErr layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS override;
    private:
        Layer &owner;
        Layer::VisibilityListener &impl;
    };

    class ProjectionAdapter_V1toV2 : public Projection2
    {
    public :
        ProjectionAdapter_V1toV2(const std::shared_ptr<Projection> &impl) NOTHROWS;
    public :
        int getSpatialReferenceID() const NOTHROWS override;
        TAKErr forward(TAK::Engine::Math::Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS override;
        TAKErr inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS override;
        double getMinLatitude() const NOTHROWS override;
        double getMaxLatitude() const NOTHROWS override;
        double getMinLongitude() const NOTHROWS override;
        double getMaxLongitude() const NOTHROWS override;
        bool is3D() const NOTHROWS override;
    private :
        std::shared_ptr<Projection> impl;
    };

    class ProjectionAdapter_V2toV1 : public Projection
    {
    public:
        ProjectionAdapter_V2toV1(Projection2Ptr &&impl) NOTHROWS;
    public:
        int getSpatialReferenceID() override;
        void forward(const GeoPoint *geo, atakmap::math::Point<double> *proj) override;
        void inverse(const atakmap::math::Point<double> *proj, GeoPoint *geo) override;
        double getMinLatitude() override;
        double getMaxLatitude() override;
        double getMinLongitude() override;
        double getMaxLongitude() override;
        bool is3D() override;
    private:
        Projection2Ptr impl;
    };

    class ProjectionSpiAdapter_V1toV3 : public ProjectionSpi3
    {
    public :
        ProjectionSpiAdapter_V1toV3(std::unique_ptr<ProjectionSpi, void(*)(const ProjectionSpi *)> &&impl) NOTHROWS;
    public :
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS override;
    private :
        std::unique_ptr<ProjectionSpi, void(*)(const ProjectionSpi *)> impl;
    };

    class ProjectionSpiAdapter_V2toV3 : public ProjectionSpi3
    {
    public:
        ProjectionSpiAdapter_V2toV3(std::unique_ptr<ProjectionSpi2, void(*)(const ProjectionSpi2 *)> &&impl) NOTHROWS;
    public:
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS override;
    private:
        std::unique_ptr<ProjectionSpi2, void(*)(const ProjectionSpi2 *)> impl;
    };

    Mutex &mutex() NOTHROWS;
    std::map<const Layer2 *, std::weak_ptr<Layer>> &layerAdapters() NOTHROWS;
    std::map<const Layer *, std::weak_ptr<Layer2>> &layer2Adapters() NOTHROWS;
}
#if 0
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(Layer2Ptr &value, LayerPtr &&layer) NOTHROWS
{
    if (!layer.get())
        return TE_InvalidArg;
    value = Layer2Ptr(new Layer2Adapter(std::move(layer)), Memory_deleter_const<Layer2, Layer2Adapter>);
    return TE_Ok;
}
#endif
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(std::shared_ptr<Layer2> &value, const std::shared_ptr<Layer> &layer) NOTHROWS
{
    if (!layer.get())
        return TE_InvalidArg;

    TAKErr code(TE_Ok);

    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<const Layer *, std::weak_ptr<Layer2>> &adapters = layer2Adapters();
    auto entry = adapters.find(layer.get());
    if (entry != adapters.end()) {
        value = entry->second.lock();
        if (value.get())
            return code;
    }

    if (const auto *adapter = dynamic_cast<const LayerAdapter *>(layer.get()))
        value = adapter->impl;
    else
        value = Layer2Ptr(new Layer2Adapter(layer), Memory_deleter_const<Layer2, Layer2Adapter>);

    adapters[layer.get()] = value;
    layerAdapters()[value.get()] = layer;

    return code;
}

#if 0
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(LayerPtr &value, Layer2Ptr &&layer) NOTHROWS
{
    if (!layer.get())
        return TE_InvalidArg;
    value = LayerPtr(new LayerAdapter(std::move(layer)), Memory_deleter_const<Layer, LayerAdapter>);
    return TE_Ok;
}
#endif
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(std::shared_ptr<Layer> &value, const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    if (!layer.get())
        return TE_InvalidArg;

    TAKErr code(TE_Ok);

    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<const Layer2 *, std::weak_ptr<Layer>> &adapters = layerAdapters();
    auto entry = adapters.find(layer.get());
    if (entry != adapters.end()) {
        value = entry->second.lock();
        if (value.get())
            return code;
    }
    if (const auto *adapter = dynamic_cast<const Layer2Adapter *>(layer.get()))
        value = adapter->impl;
    else
        value = LayerPtr(new LayerAdapter(layer), Memory_deleter_const<Layer, LayerAdapter>);

    adapters[layer.get()] = value;
    layer2Adapters()[value.get()] = layer;

    return code;
}

TAKErr TAK::Engine::Core::LegacyAdapters_find(std::shared_ptr<Layer> &value, const Layer2 &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (const auto *adapter = dynamic_cast<const Layer2Adapter *>(&layer)) {
        value = adapter->impl;
        return code;
    } else {
        Lock lock(mutex());
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        std::map<const Layer2 *, std::weak_ptr<Layer>> &adapters = layerAdapters();
        auto entry = adapters.find(&layer);
        if (entry == adapters.end())
            return TE_InvalidArg;

        value = entry->second.lock();
        if (!value.get()) {
            adapters.erase(entry);
            return TE_InvalidArg;
        }

        return TE_Ok;
    }
}
TAKErr TAK::Engine::Core::LegacyAdapters_find(std::shared_ptr<Layer2> &value, const Layer &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (const auto *adapter = dynamic_cast<const LayerAdapter *>(&layer)) {
        value = adapter->impl;
        return code;
    } else {
        Lock lock(mutex());
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        std::map<const Layer *, std::weak_ptr<Layer2>> &adapters = layer2Adapters();
        auto entry = adapters.find(&layer);
        if (entry == adapters.end())
            return TE_InvalidArg;

        value = entry->second.lock();
        if (!value.get()) {
            adapters.erase(entry);
            return TE_InvalidArg;
        }

        return TE_Ok;
    }
}

TAKErr TAK::Engine::Core::LegacyAdapters_adapt(Projection2Ptr &value, std::unique_ptr<Projection, void(*)(const Projection *)> &&proj) NOTHROWS
{
    value = Projection2Ptr(new ProjectionAdapter_V1toV2(std::move(proj)), Memory_deleter_const<Projection2, ProjectionAdapter_V1toV2>);
    return TE_Ok;
}
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(Projection2Ptr &value, std::unique_ptr<Projection, void(*)(Projection *)> &&proj) NOTHROWS
{
    value = Projection2Ptr(new ProjectionAdapter_V1toV2(std::move(proj)), Memory_deleter_const<Projection2, ProjectionAdapter_V1toV2>);
    return TE_Ok;
}
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(std::unique_ptr<Projection, void(*)(const Projection *)> &value, Projection2Ptr &&proj) NOTHROWS
{
    value = std::unique_ptr<Projection, void(*)(const Projection *)>(new ProjectionAdapter_V2toV1(std::move(proj)), Memory_deleter_const<Projection, ProjectionAdapter_V2toV1>);
    return TE_Ok;
}
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(std::unique_ptr<Projection, void(*)(Projection *)> &value, Projection2Ptr &&proj) NOTHROWS
{
    value = std::unique_ptr<Projection, void(*)(Projection *)>(new ProjectionAdapter_V2toV1(std::move(proj)), Memory_deleter<Projection, ProjectionAdapter_V2toV1>);
    return TE_Ok;
}

TAKErr TAK::Engine::Core::LegacyAdapters_adapt(ProjectionSpi3Ptr &value, std::unique_ptr<atakmap::core::ProjectionSpi, void(*)(const atakmap::core::ProjectionSpi *)> &&spi) NOTHROWS
{
    value = ProjectionSpi3Ptr(new ProjectionSpiAdapter_V1toV3(std::move(spi)), Memory_deleter_const<ProjectionSpi3, ProjectionSpiAdapter_V1toV3>);
    return TE_Ok;
}
TAKErr TAK::Engine::Core::LegacyAdapters_adapt(ProjectionSpi3Ptr &value, std::unique_ptr<ProjectionSpi2, void(*)(const ProjectionSpi2 *)> &&spi) NOTHROWS
{
    value = ProjectionSpi3Ptr(new ProjectionSpiAdapter_V2toV3(std::move(spi)), Memory_deleter_const<ProjectionSpi3, ProjectionSpiAdapter_V2toV3>);
    return TE_Ok;
}

namespace {

    Layer2Adapter::Layer2Adapter(const std::shared_ptr<Layer> &impl_) NOTHROWS :
        impl(impl_)
    {}
    Layer2Adapter::~Layer2Adapter() NOTHROWS
    {
        Lock lock(mutex);
        std::map<Layer2::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator it;
        for (it = listeners.begin(); it != listeners.end(); it++)
            impl->removeVisibilityListener(it->second.get());
        listeners.clear();
    }
    const char *Layer2Adapter::getName() const NOTHROWS
    {
        return impl->getName();
    }
    bool Layer2Adapter::isVisible() const NOTHROWS
    {
        return impl->isVisible();
    }
    void Layer2Adapter::setVisible(bool v) NOTHROWS
    {
        impl->setVisible(v);
    }
    TAKErr Layer2Adapter::addVisibilityListener(Layer2::VisibilityListener *l2) NOTHROWS
    {
        TAKErr code(TE_Ok);
        Layer::VisibilityListener *l(nullptr);

        if (!l2)
            return TE_InvalidArg;

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        std::map<Layer2::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator entry;
        entry = listeners.find(l2);
        if (entry != listeners.end())
            return code;

        std::unique_ptr<VisibilityListenerAdapter> lPtr(new VisibilityListenerAdapter(*this, *l2));
        l = lPtr.get();
        listeners[l2] = std::move(lPtr);

        impl->addVisibilityListener(l);
        return code;
    }
    TAKErr Layer2Adapter::removeVisibilityListener(VisibilityListener *l2) NOTHROWS
    {
        if (!l2)
            return TE_InvalidArg;

        TAKErr code(TE_Ok);
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        std::map<Layer2::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator entry;
        entry = listeners.find(l2);
        if (entry == listeners.end())
            return code;

        impl->removeVisibilityListener(entry->second.get());
        listeners.erase(entry);
        return code;
    }
    TAKErr Layer2Adapter::getExtension(void **value, const char *extensionName) const NOTHROWS
    {
        return TE_InvalidArg;
    }

    Layer2Adapter::VisibilityListenerAdapter::VisibilityListenerAdapter(Layer2 &owner_, Layer2::VisibilityListener &impl_) NOTHROWS :
        owner(owner_),
        impl(impl_)
    {}
    void Layer2Adapter::VisibilityListenerAdapter::visibilityChanged(Layer &layer)
    {
        impl.layerVisibilityChanged(owner, owner.isVisible());
    }

    LayerAdapter::LayerAdapter(const std::shared_ptr<Layer2> &impl_) NOTHROWS :
        impl(impl_)
    {}
    LayerAdapter::~LayerAdapter() NOTHROWS
    {
        Lock lock(mutex);
        std::map<Layer::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator it;
        for (it = listeners.begin(); it != listeners.end(); it++)
            impl->removeVisibilityListener(it->second.get());
        listeners.clear();
    }
    const char *LayerAdapter::getName() const NOTHROWS
    {
        return impl->getName();
    }
    bool LayerAdapter::isVisible() const NOTHROWS
    {
        return impl->isVisible();
    }
    void LayerAdapter::setVisible(bool v) NOTHROWS
    {
        impl->setVisible(v);
    }
    void LayerAdapter::addVisibilityListener(Layer::VisibilityListener *l) NOTHROWS
    {
        if (!l)
            return;

        Layer2::VisibilityListener *l2(nullptr);

        Lock lock(mutex);

        std::map<Layer::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator entry;
        entry = listeners.find(l);
        if (entry != listeners.end())
            return;

        std::unique_ptr<VisibilityListenerAdapter> l2Ptr(new VisibilityListenerAdapter(*this, *l));
        l2 = l2Ptr.get();
        listeners[l] = std::move(l2Ptr);

        impl->addVisibilityListener(l2);
    }
    void LayerAdapter::removeVisibilityListener(VisibilityListener *l) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!l)
            return;

        Lock lock(mutex);
        code = lock.status;

        std::map<Layer::VisibilityListener *, std::unique_ptr<VisibilityListenerAdapter>>::iterator entry;
        entry = listeners.find(l);
        if (entry == listeners.end())
            return;

        impl->removeVisibilityListener(entry->second.get());
        listeners.erase(entry);
    }

    LayerAdapter::VisibilityListenerAdapter::VisibilityListenerAdapter(Layer &owner_, Layer::VisibilityListener &impl_) NOTHROWS :
        owner(owner_),
        impl(impl_)
    {}
    TAKErr LayerAdapter::VisibilityListenerAdapter::layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS
    {
        impl.visibilityChanged(owner);
        return TE_Ok;
    }

    ProjectionAdapter_V1toV2::ProjectionAdapter_V1toV2(const std::shared_ptr<Projection> &impl_) NOTHROWS :
        impl(std::move(impl_))
    {}
    int ProjectionAdapter_V1toV2::getSpatialReferenceID() const NOTHROWS
    {
        return impl->getSpatialReferenceID();
    }
    TAKErr ProjectionAdapter_V1toV2::forward(TAK::Engine::Math::Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS
    {
        if (!proj)
            return TE_InvalidArg;
        Point<double> lproj;
        GeoPoint lgeo(geo);
        impl->forward(&lgeo, &lproj);
        proj->x = lproj.x;
        proj->y = lproj.y;
        proj->z = lproj.z;
        return TE_Ok;
    }
    TAKErr ProjectionAdapter_V1toV2::inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS
    {
        if (!geo)
            return TE_InvalidArg;
        Point<double> lproj(proj.x, proj.y, proj.z);
        GeoPoint lgeo;
        impl->inverse(&lproj, &lgeo);
        GeoPoint_adapt(geo, lgeo);
        return TE_Ok;
    }
    double ProjectionAdapter_V1toV2::getMinLatitude() const NOTHROWS
    {
        return impl->getMinLatitude();
    }
    double ProjectionAdapter_V1toV2::getMaxLatitude() const NOTHROWS
    {
        return impl->getMaxLatitude();
    }
    double ProjectionAdapter_V1toV2::getMinLongitude() const NOTHROWS
    {
        return impl->getMinLongitude();
    }
    double ProjectionAdapter_V1toV2::getMaxLongitude() const NOTHROWS
    {
        return impl->getMaxLongitude();
    }
    bool ProjectionAdapter_V1toV2::is3D() const NOTHROWS
    {
        return impl->is3D();
    }

    ProjectionAdapter_V2toV1::ProjectionAdapter_V2toV1(Projection2Ptr &&impl_) NOTHROWS :
        impl(std::move(impl_))
    {}
    int ProjectionAdapter_V2toV1::getSpatialReferenceID()
    {
        return impl->getSpatialReferenceID();
    }
    void ProjectionAdapter_V2toV1::forward(const GeoPoint *lgeo, atakmap::math::Point<double> *lproj)
    {
        GeoPoint2 geo;
        GeoPoint_adapt(&geo, *lgeo);
        Point2<double> proj;
        if (impl->forward(&proj, geo) == TE_Ok) {
            lproj->x = proj.x;
            lproj->y = proj.y;
            lproj->z = proj.z;

        } else {
            lproj->x = NAN;
            lproj->y = NAN;
            lproj->z = NAN;
        }
    }
    void ProjectionAdapter_V2toV1::inverse(const atakmap::math::Point<double> *lproj, GeoPoint *lgeo)
    {
        GeoPoint2 geo;
        Point2<double> proj(lproj->x, lproj->y, lproj->z);
        if (impl->inverse(&geo, proj) == TE_Ok) {
            *lgeo = GeoPoint(geo);
        } else {
            lgeo->latitude = NAN;
            lgeo->longitude = NAN;
            lgeo->altitude = NAN;
            lgeo->ce90 = NAN;
            lgeo->le90 = NAN;
        }
    }
    double ProjectionAdapter_V2toV1::getMinLatitude()
    {
        return impl->getMinLatitude();
    }
    double ProjectionAdapter_V2toV1::getMaxLatitude()
    {
        return impl->getMaxLatitude();
    }
    double ProjectionAdapter_V2toV1::getMinLongitude()
    {
        return impl->getMinLongitude();
    }
    double ProjectionAdapter_V2toV1::getMaxLongitude()
    {
        return impl->getMaxLongitude();
    }
    bool ProjectionAdapter_V2toV1::is3D()
    {
        return impl->is3D();
    }

    ProjectionSpiAdapter_V1toV3::ProjectionSpiAdapter_V1toV3(std::unique_ptr<ProjectionSpi, void(*)(const ProjectionSpi *)> &&impl_) NOTHROWS :
        impl(std::move(impl_))
    {}

    TAKErr ProjectionSpiAdapter_V1toV3::create(Projection2Ptr &value, const int srid) NOTHROWS
    {
        std::unique_ptr<Projection, void(*)(const Projection *)> lproj(impl->create(srid), Memory_deleter_const<Projection>);
        if (!lproj.get())
            return TE_InvalidArg;
        return LegacyAdapters_adapt(value, std::move(lproj));
    }


    ProjectionSpiAdapter_V2toV3::ProjectionSpiAdapter_V2toV3(std::unique_ptr<ProjectionSpi2, void(*)(const ProjectionSpi2 *)> &&impl_) NOTHROWS :
        impl(std::move(impl_))
    {}

    TAKErr ProjectionSpiAdapter_V2toV3::create(Projection2Ptr &value, const int srid) NOTHROWS
    {
        TAKErr code(TE_Ok);
        ProjectionPtr2 lproj(nullptr, nullptr);
        code = impl->create(lproj, srid);
        TE_CHECKRETURN_CODE(code);
        if (!lproj.get())
            return TE_InvalidArg;
        return LegacyAdapters_adapt(value, std::move(lproj));
    }

    Mutex &mutex() NOTHROWS
    {
        static Mutex mtx;
        return mtx;
    }

    std::map<const Layer2 *, std::weak_ptr<Layer>> &layerAdapters() NOTHROWS
    {
        static std::map<const Layer2 *, std::weak_ptr<Layer>> m;
        return m;
    }
    std::map<const Layer *, std::weak_ptr<Layer2>> &layer2Adapters() NOTHROWS
    {
        static std::map<const Layer *, std::weak_ptr<Layer2>> m;
        return m;
    }
}
