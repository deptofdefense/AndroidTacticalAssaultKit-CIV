#include "core/AbstractLayer2.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::unique_ptr<void, void(*)(const void *)> ExtensionPtr;
}

AbstractLayer2::AbstractLayer2(const char *name_) NOTHROWS :
    name_(name_),
    visible_(true)

{}

AbstractLayer2::~AbstractLayer2() NOTHROWS
{}

void AbstractLayer2::registerExtension(const char *extensionName, ExtensionPtr &&extension) NOTHROWS
{
    if (!extensionName)
    {
        Logger_log(TELL_Warning, "%s: Failed to register extension with NULL name", this->name_);
        return;
    }
    if (!extension.get())
    {
        Logger_log(TELL_Warning, "%s: Failed to register NULL extension, %s", this->name_, extensionName);
        return;
    }

    this->extensions.erase(extensionName);
    this->extensions.insert(std::pair<std::string, ExtensionPtr>(extensionName, std::move(extension)));
}
TAKErr AbstractLayer2::setName(const char *name) NOTHROWS
{
    if (!name)
        return TE_InvalidArg;
    name_ = name;
    return TE_Ok;
}
void AbstractLayer2::setVisible(const bool visible) NOTHROWS
{
    Lock lock(mutex_);
    this->visible_ = visible;
    this->dispatchOnVisibleChangedNoSync();
}
bool AbstractLayer2::isVisible() const NOTHROWS
{
    Lock lock(mutex_);
    return this->visible_;
}
TAKErr AbstractLayer2::addVisibilityListener(Layer2::VisibilityListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->layerVisibleChangedListeners.insert(l);
    return code;
}
TAKErr AbstractLayer2::removeVisibilityListener(Layer2::VisibilityListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->layerVisibleChangedListeners.erase(l);
    return code;
}
const char *AbstractLayer2::getName() const NOTHROWS
{
    return this->name_;
}
TAKErr AbstractLayer2::getExtension(void **extension, const char *name) const NOTHROWS
{
    if (!name)
        return TE_InvalidArg;
    std::map<std::string, ExtensionPtr>::const_iterator entry;
    entry = this->extensions.find(name);
    if (entry == this->extensions.end())
        return TE_InvalidArg;
    *extension = entry->second.get();
    return TE_Ok;
}

void AbstractLayer2::dispatchOnVisibleChangedNoSync() NOTHROWS
{
    std::set<Layer2::VisibilityListener *>::iterator it;
    for (it = this->layerVisibleChangedListeners.begin(); it != this->layerVisibleChangedListeners.end(); it++)
        (*it)->layerVisibilityChanged(*this, this->visible_);
}

