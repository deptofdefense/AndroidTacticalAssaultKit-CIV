#ifndef TAK_ENGINE_ELEVATION_ELEVATIONHEATMAPLAYER_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONHEATMAPLAYER_H_INCLUDED

#include "core/AbstractLayer2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class ENGINE_API ElevationHeatMapLayer : public TAK::Engine::Core::AbstractLayer2
            {
            public :
                class HeatMapListener;
            public :
                ElevationHeatMapLayer(const char* name = "Elevation Heat Map") NOTHROWS;
                ~ElevationHeatMapLayer() NOTHROWS;
            public :
                Util::TAKErr setSaturation(float v) NOTHROWS;
                Util::TAKErr setValue(float v) NOTHROWS;
                Util::TAKErr setAlpha(float v) NOTHROWS;
                float getSaturation() const NOTHROWS;
                float getValue() const NOTHROWS;
                float getAlpha() const NOTHROWS;
                void setDynamicRange() NOTHROWS;
                Util::TAKErr setAbsoluteRange(const double min, const double max) NOTHROWS;
                bool isDynamicRange() const NOTHROWS;
                Util::TAKErr getAbsoluteRange(double* min, double* max) const NOTHROWS;
                Util::TAKErr addListener(HeatMapListener &l) NOTHROWS;
                Util::TAKErr removeListener(const HeatMapListener &l) NOTHROWS;
            private :
                void dispatchColorChanged() NOTHROWS;
                void dispatchRangeChanged() NOTHROWS;
            private :
                float saturation_;
                float value_;
                float alpha_;
                double min_el_;
                double max_el_;
                std::set<HeatMapListener *> listeners_;
            };

            class ENGINE_API ElevationHeatMapLayer::HeatMapListener
            {
            public :
                virtual ~HeatMapListener() NOTHROWS = 0;
            public :
                virtual Util::TAKErr onColorChanged(const ElevationHeatMapLayer& subject, const float saturation, const float value, const float alpha) NOTHROWS = 0;
                virtual Util::TAKErr onRangeChanged(const ElevationHeatMapLayer& subject, const double minEl, const double maxEl, const bool dynamicRange) NOTHROWS = 0;
            };
        }
    }
}

#endif
