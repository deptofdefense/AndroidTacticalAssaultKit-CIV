#include "math/Statistics.h"

using namespace TAK::Engine::Math;

Statistics::Statistics()
{
    reset();
}

Statistics::Statistics(bool modeOrStddev)
{
    if (modeOrStddev)
        this->record.reset(new std::map<double, std::size_t>());
    this->reset();
}

void Statistics::observe(double v) {
    mean = ((mean*observations) + v) / (observations + 1);
    if (observations == 0) {
        minimum = v;
        maximum = v;
    } else {
        if (v < minimum)
            minimum = v;
        else if (v > maximum)
            maximum = v;
    }
    observations++;

    if (record.get()) {
        const double key = v;
        auto entry = record->find(key);
        std::size_t cnt;
        if (entry == record->end()) 
            cnt = 0;
        else
            cnt = entry->second;
        (*record)[key] = cnt+1;
        //cnt[0]++;
        if (cnt > modeCount) {
            mode = v;
            modeCount = cnt;
        }

        // XXX -  dynamic update stddev
        // https://stats.stackexchange.com/questions/105773/how-to-calculate-sd-of-sample-for-one-new-observation
    }
}

void Statistics::reset()
{
    observations = 0L;
    mean = 0.0;
    minimum = 0.0;
    maximum = 0.0;
    stddev = 0.0;
    mode = 0.0;
    modeCount = 0;

    if (record != nullptr)
        record->clear();
}
