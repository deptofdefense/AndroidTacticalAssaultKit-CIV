#include "core/ProxyLayer2.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

ProxyLayer2::ProxyLayer2(const char *name_) NOTHROWS :
    AbstractLayer2(name_)
{}

ProxyLayer2::ProxyLayer2(const char *name_, const std::shared_ptr<Layer2> &subject_) NOTHROWS :
    AbstractLayer2(name_),
    subject_(subject_)
{}

ProxyLayer2::ProxyLayer2(const char *name_, Layer2Ptr &&subject_) NOTHROWS :
    AbstractLayer2(name_),
    subject_(std::move(subject_))
{}

ProxyLayer2::~ProxyLayer2() NOTHROWS
{}

TAKErr ProxyLayer2::get(std::shared_ptr<Layer2> &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    value = this->subject_;
    return code;
}
    
TAKErr ProxyLayer2::set(const std::shared_ptr<Layer2> &subject) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->subject_ = subject;
    this->dispatchOnProxySubjectChangedNoSync();
    return code;
}
TAKErr ProxyLayer2::set(Layer2Ptr &&subject) NOTHROWS
{
    return this->set(std::shared_ptr<Layer2>(std::move(subject)));
}
TAKErr ProxyLayer2::addSubjectChangedListener(ProxyLayer2::SubjectChangedListener *l) NOTHROWS
{
    if (!l)
    return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->proxy_subject_changed_listeners_.insert(l);
    return code;
}
TAKErr ProxyLayer2::removeSubjectChangedListener(ProxyLayer2::SubjectChangedListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->proxy_subject_changed_listeners_.erase(l);
    return code;
}
void ProxyLayer2::dispatchOnProxySubjectChangedNoSync() NOTHROWS
{
    std::set<ProxyLayer2::SubjectChangedListener *>::iterator it;
    for (it = this->proxy_subject_changed_listeners_.begin(); it != proxy_subject_changed_listeners_.end(); it++)
        (*it)->subjectChanged(*this, this->subject_);
}

ProxyLayer2::SubjectChangedListener::~SubjectChangedListener() NOTHROWS
{}
