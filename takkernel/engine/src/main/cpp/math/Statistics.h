#pragma once

#include "util/Memory.h"

#include <map>

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {
            class Statistics
            {
            public:
                uint64_t observations;
                double mean;
                double minimum;
                double maximum;
                double stddev;
                double mode;
            private:
                std::size_t modeCount;
                std::unique_ptr<std::map <double, std::size_t>> record;
            public:
                Statistics();
                Statistics(bool modeOrStddev);
                void observe(double v);
                void reset();
            };
        }
    }
}