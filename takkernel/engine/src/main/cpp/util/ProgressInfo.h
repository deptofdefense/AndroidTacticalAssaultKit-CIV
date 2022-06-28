#ifndef TAK_ENGINE_UTIL_PROGRESSINFO_H_INCLUDED
#define TAK_ENGINE_UTIL_PROGRESSINFO_H_INCLUDED

//
// Private API
//

#include "util/ProcessingCallback.h"
#include "thread/RWMutex.h"

#include <vector>
#include <list>

namespace TAK {
    namespace Engine {
        namespace Util {
            struct ProgressInfo {
                ProgressInfo(ProcessingCallback* cb, size_t granularity);

                // these are only called by the thread that is sure to be the single accessor
                void setMinMax(size_t min, size_t max);
                void setMin(size_t min);

                ProgressInfo& addSubinfo(size_t granularity = 100);
                void finishSubinfo(ProgressInfo& subinfo);
                void finishSubinfos();

                inline size_t granularity() const { return granularity_; }
            private:
                void update(double p);
                void updateKnownPercent(size_t i, double p);
                void updateParent();
                static TAKErr progressImpl(void* opaque, const int min, const int max) NOTHROWS;
                TAK::Engine::Thread::RWMutex rw;
                size_t index;
                ProcessingCallback* const callback;
                ProgressInfo* parent;
                std::vector<double> knownPercents;
                std::list<ProgressInfo> subinfos;

                // only accessed by owning thread
                size_t min_val_;
                size_t next_update_;
                size_t max_val_;
                size_t granularity_;
            };
        }
    }
}

#endif