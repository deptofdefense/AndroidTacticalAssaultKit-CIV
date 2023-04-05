//
// Created by GeoDev on 10/30/2021.
//

#ifndef TAK_ENGINE_CORE_MAPRENDERER3_H
#define TAK_ENGINE_CORE_MAPRENDERER3_H

#include "core/MapRenderer2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API MapRenderer3 : public MapRenderer2
            {
            public :
                class OnCameraChangedListener2;
            public :
                virtual ~MapRenderer3() NOTHROWS = 0;
            public :
                virtual Util::TAKErr addOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS = 0;
                virtual Util::TAKErr removeOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS = 0;
            };

            class ENGINE_API MapRenderer3::OnCameraChangedListener2 : public MapRenderer2::OnCameraChangedListener
            {
            public :
                virtual ~OnCameraChangedListener2() NOTHROWS = 0;
            public :
                /**
                 * Invoked when a camera change is requested.
                 *
                 * @param renderer  The renderer whose camera has been requested to change
                 */
                virtual Util::TAKErr onCameraChangeRequested(const MapRenderer2 &renderer) NOTHROWS = 0;
            };
        }
    }
}
#endif //TAK_ENGINE_CORE_MAPRENDERER3_H
