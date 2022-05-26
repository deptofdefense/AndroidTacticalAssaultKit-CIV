#include "core/MultiLayer2.h"

#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

class MultiLayer2::VisibilityUpdater : public Layer2::VisibilityListener
{
public :
    VisibilityUpdater(MultiLayer2 &owner) NOTHROWS;
public :
    TAKErr layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS override;
private :
    MultiLayer2 &owner_;
};

MultiLayer2::MultiLayer2(const char *name) NOTHROWS :
    AbstractLayer2(name)
{
    visibility_updater_.reset(new VisibilityUpdater(*this));
}
MultiLayer2::~MultiLayer2() NOTHROWS
{}

TAKErr MultiLayer2::addLayer(const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return this->addLayerNoSync(this->layers_.size(), layer);
}
TAKErr MultiLayer2::addLayer(Layer2Ptr &&layer) NOTHROWS
{
    return this->addLayer(std::shared_ptr<Layer2>(std::move(layer)));
}
TAKErr MultiLayer2::addLayer(const std::size_t position, const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return this->addLayerNoSync(position, layer);
}
TAKErr MultiLayer2::addLayer(const std::size_t position, Layer2Ptr &&layer) NOTHROWS
{
    return this->addLayer(position, std::shared_ptr<Layer2>(std::move(layer)));
}

TAKErr MultiLayer2::addLayerNoSync(const std::size_t position, const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    if (position > this->layers_.size())
        return TE_BadIndex;
    if (!layer.get())
        return TE_InvalidArg;
    if (position == this->layers_.size())
        this->layers_.push_back(layer);
    else
        this->layers_.insert(this->layers_.begin() + position, layer);

    this->dispatchOnLayerAddedNoSync(layer);
        
    layer->addVisibilityListener(this->visibility_updater_.get());
    if(!this->visible_ && layer->isVisible()) {
        this->visible_ = true;
        this->dispatchOnVisibleChangedNoSync();
    } else if(this->visible_ && this->layers_.size() == 1 && !layer->isVisible()) {
        this->visible_ = false;
        this->dispatchOnVisibleChangedNoSync();
    }

    return TE_Ok;
}
TAKErr MultiLayer2::removeLayer(const Layer2 &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    for (std::size_t i = 0; i < this->layers_.size(); i++) {
        std::shared_ptr<Layer2> layerPtr = this->layers_[i];
        if (layerPtr.get() == &layer) {
            this->layers_.erase(this->layers_.begin()+i);
            STLVectorAdapter<std::shared_ptr<Layer2>> singleton;
            singleton.add(layerPtr);
            this->dispatchOnLayerRemovedNoSync(singleton);
            layerPtr->removeVisibilityListener(this->visibility_updater_.get());
            this->updateVisibility();
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}
TAKErr MultiLayer2::removeAllLayers() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::vector<std::shared_ptr<Layer2>> scratch(this->layers_);
    for (std::size_t i = 0u; i < this->layers_.size(); i++)
        this->layers_[i]->removeVisibilityListener(this->visibility_updater_.get());
    this->layers_.clear();

    STLVectorAdapter<std::shared_ptr<Layer2>> scratchC(scratch);
    this->dispatchOnLayerRemovedNoSync(scratchC);
    scratch.clear();
        
    this->updateVisibility();

    return TE_Ok;
}
TAKErr MultiLayer2::setLayerPosition(const Layer2 &layer, const std::size_t position) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (position >= this->layers_.size())
        return TE_BadIndex;

    std::size_t oldPos = this->layers_.size();
    for (std::size_t i = 0; i < this->layers_.size(); i++) {
        if (this->layers_[i].get() == &layer) {
            oldPos = i;
            break;
        }
    }
    // the specified layer is not tracked
    if (oldPos == this->layers_.size())
        return TE_InvalidArg;

    // no-op
    if(position == oldPos)
        return code;

    std::shared_ptr<Layer2> layerPtr(this->layers_[oldPos]);

    this->layers_.erase(this->layers_.begin()+oldPos);
    if(position > oldPos) {
        this->layers_.insert(this->layers_.begin() + (position-1), layerPtr);
    } else if(position < oldPos) {
        this->layers_.insert(this->layers_.begin() + position, layerPtr);
    } else {
        return TE_IllegalState;
    }
        
    this->dispatchOnLayerPositionChanged(layerPtr, oldPos, position);
    return code;
}
std::size_t MultiLayer2::getNumLayers() const NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    // XXX - no failover

    return this->layers_.size();
}
TAKErr MultiLayer2::getLayer(std::shared_ptr<Layer2> &value, const std::size_t i) const NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (i >= this->layers_.size())
        return TE_BadIndex;

    value = this->layers_[i];
    return code;
}
TAKErr MultiLayer2::getLayers(Collection<std::shared_ptr<Layer2>> &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    for (std::size_t i = 0u; i < this->layers_.size(); i++) {
        code = value.add(this->layers_[i]);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr MultiLayer2::addLayersChangedListener(LayersChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    this->layers_changed_listeners_.insert(l);
    return code;
}
TAKErr MultiLayer2::removeLayersChangedListener(LayersChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    this->layers_changed_listeners_.erase(l);
    return code;
}
TAKErr MultiLayer2::dispatchOnLayerAddedNoSync(const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::set<LayersChangedListener *>::iterator it;
    for (it = this->layers_changed_listeners_.begin(); it != this->layers_changed_listeners_.end(); it++)
        (*it)->layerAdded(*this, layer);
    return code;
}   
TAKErr MultiLayer2::dispatchOnLayerRemovedNoSync(Collection<std::shared_ptr<Layer2>> &layers) NOTHROWS
{
    if (layers.empty())
        return TE_Ok;

    TAKErr code(TE_Ok);
    Collection<std::shared_ptr<Layer2>>::IteratorPtr iter(nullptr, nullptr);
    code = layers.iterator(iter);
    TE_CHECKRETURN_CODE(code);

    do {
        std::shared_ptr<Layer2> layer;
        code = iter->get(layer);
        TE_CHECKBREAK_CODE(code);

        std::set<LayersChangedListener *>::iterator listener;
        for (listener = this->layers_changed_listeners_.begin(); listener != this->layers_changed_listeners_.end(); listener++)
            (*listener)->layerRemoved(*this, layer);

        code = iter->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}   
TAKErr MultiLayer2::dispatchOnLayerPositionChanged(const std::shared_ptr<Layer2> &l, const std::size_t oldPos, const std::size_t newPos) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::set<LayersChangedListener *>::iterator it;
    for (it = this->layers_changed_listeners_.begin(); it != this->layers_changed_listeners_.end(); it++)
        (*it)->layerPositionChanged(*this, l, oldPos, newPos);
    return code;
}
    
void MultiLayer2::updateVisibility() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;

    // XXX - we will strictly adhere to visibility being true only if one or
    //       more child layers are visible
    bool childrenVisible = false;
    for (std::size_t i = 0u; i < this->layers_.size(); i++)
        childrenVisible |= this->layers_[i]->isVisible();
        
    if(childrenVisible != this->visible_) {
        this->visible_ = childrenVisible;
        this->dispatchOnVisibleChangedNoSync();
    }
}
void MultiLayer2::setVisible(const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;

    for (std::size_t i = 0u; i < this->layers_.size(); i++) {
        this->layers_[i]->removeVisibilityListener(this->visibility_updater_.get());
        this->layers_[i]->setVisible(visible);
        this->layers_[i]->addVisibilityListener(this->visibility_updater_.get());
    }
    this->updateVisibility();
}
    
/**************************************************************************/
    
MultiLayer2::LayersChangedListener::~LayersChangedListener() NOTHROWS
{}

MultiLayer2::VisibilityUpdater::VisibilityUpdater(MultiLayer2 &owner) NOTHROWS :
    owner_(owner)
{}

TAKErr MultiLayer2::VisibilityUpdater::layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS
{
    owner_.updateVisibility();
    return TE_Ok;
}
