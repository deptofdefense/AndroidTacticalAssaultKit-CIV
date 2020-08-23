#ifndef TAK_ENGINE_FEATURE_ALTITUDEMODE_H_INCLUDED
#define TAK_ENGINE_FEATURE_ALTITUDEMODE_H_INCLUDED

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            enum AltitudeMode
            {
                /** Clamped to terrain surface */
                TEAM_ClampToGround,
                /** Relative to terrain surface */
                TEAM_Relative,
                /** Absolute */
                TEAM_Absolute,
            };
        }
    }
}

#endif
