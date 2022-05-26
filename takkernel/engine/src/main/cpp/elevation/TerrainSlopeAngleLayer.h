#ifndef TAK_ENGINE_ELEVATION_TERRAINSLOPEANGLELAYER_H_INCLUDED
#define TAK_ENGINE_ELEVATION_TERRAINSLOPEANGLELAYER_H_INCLUDED

#include "core/AbstractLayer2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class ENGINE_API TerrainSlopeAngleLayer : public TAK::Engine::Core::AbstractLayer2
            {
            public :
                class SlopeAngleListener;
            public :
                TerrainSlopeAngleLayer(const char* name = "Terrain Slope Angle") NOTHROWS;
                ~TerrainSlopeAngleLayer() NOTHROWS;
            public :
                Util::TAKErr setAlpha(float v) NOTHROWS;
                float getAlpha() const NOTHROWS;
                Util::TAKErr addListener(SlopeAngleListener &l) NOTHROWS;
                Util::TAKErr removeListener(const SlopeAngleListener &l) NOTHROWS;
            private :
                void dispatchColorChanged() NOTHROWS;
            private :
                float alpha_;
                std::set<SlopeAngleListener *> listeners_;
            };

            class ENGINE_API TerrainSlopeAngleLayer::SlopeAngleListener
            {
            public :
                virtual ~SlopeAngleListener() NOTHROWS = 0;
            public :
                virtual Util::TAKErr onColorChanged(const TerrainSlopeAngleLayer &subject, const float alpha) NOTHROWS = 0;
            };
        }
    }
}

#endif
