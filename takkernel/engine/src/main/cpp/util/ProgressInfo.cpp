
#include "util/ProgressInfo.h"
#include <climits>

using namespace TAK::Engine::Util;

ProgressInfo::ProgressInfo(ProcessingCallback* cb, size_t granularity)
    : callback(cb), parent(nullptr), granularity_(granularity == 0 ? SIZE_MAX : granularity) {
}

ProgressInfo& ProgressInfo::addSubinfo(size_t granularity) {
    TAK::Engine::Thread::WriteLock lock(this->rw);
    knownPercents.push_back(0.0);
    subinfos.emplace_back(nullptr, granularity);
    ProgressInfo& subinfo = subinfos.back();
    subinfo.index = knownPercents.size() - 1;
    subinfo.parent = this;
    return subinfo;
}

void ProgressInfo::finishSubinfo(ProgressInfo& subinfo) {
    size_t subIndex = SIZE_MAX;

    {
        TAK::Engine::Thread::WriteLock lock(this->rw);
        for (auto it = subinfos.begin(); it != subinfos.end(); ++it) {
            if (&(*it) == &subinfo) {
                subIndex = subinfo.index;
                subinfos.erase(it);
                break;
            }
        }
    }

    if (subIndex != SIZE_MAX)
        this->updateKnownPercent(subIndex, 1.0);
}

void ProgressInfo::finishSubinfos() {

    bool updateParent = false;

    {
        TAK::Engine::Thread::WriteLock lock(this->rw);
        for (auto it = subinfos.begin(); it != subinfos.end(); ++it) {
            knownPercents[it->index] = 1.0;
            updateParent = true;
        }
        subinfos.clear();
    }

    if (updateParent)
        this->updateParent();
}

void ProgressInfo::setMinMax(size_t min, size_t max) {
    if (this->max_val_ == max) {
        setMin(min);
    }
    else {
        this->min_val_ = min;
        this->max_val_ = max;
        this->next_update_ = (this->max_val_ / this->granularity_);
        double p = max == 0 ? 1.0 : std::min(1.0, static_cast<double>(min) / max);
        this->update(p);
    }
}

void ProgressInfo::setMin(size_t min) {
    this->min_val_ = min;
    if (min >= this->next_update_) {
        this->next_update_ += (this->max_val_ / this->granularity_);
        double p = this->max_val_ == 0 ? 1.0 : std::min(1.0, static_cast<double>(min) / this->max_val_);
        this->update(p);
    }
}

void ProgressInfo::update(double p) {

    size_t value = static_cast<size_t>(p * INT_MAX);

    // either has a parent, or has a progress callback
    if (this->parent) {
        this->parent->updateKnownPercent(this->index, p);
    }
    else if (this->callback && this->callback->progress) {
        int m = static_cast<int>(p * (INT_MAX >> 1));
        this->callback->progress(this->callback->opaque, m, (INT_MAX >> 1));
    }
}

void ProgressInfo::updateKnownPercent(size_t i, double p) {
    {
        TAK::Engine::Thread::WriteLock lock(this->rw);
        this->knownPercents[i] = p;
    }
    this->updateParent();
}

void ProgressInfo::updateParent() {
    double pp = 0.0;
    size_t count = 0;
    {
        TAK::Engine::Thread::ReadLock lock(this->rw);
        count = this->knownPercents.size();
        for (size_t i = 0; i < count; ++i)
            pp += this->knownPercents[i];
    }
    if (count) {
        pp /= count;
        this->update(pp);
    }
}