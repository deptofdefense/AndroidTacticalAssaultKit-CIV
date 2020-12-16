#ifndef TAK_ENGINE_RENDERER_MODEL_SCENEOBJECTCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_SCENEOBJECTCONTROL_H_INCLUDED

#include "core/Control.h"
#include "core/GeoPoint2.h"
#include "feature/AltitudeMode.h"
#include "feature/Envelope2.h"
#include "math/Matrix2.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API SceneObjectControl
                {
                public :
                    class UpdateListener;
                public :
                    virtual ~SceneObjectControl() NOTHROWS = 0;
                public :
                    /**
                     * Updates the location, local frame and SRID of the scene
                     */
                    virtual Util::TAKErr setLocation(const TAK::Engine::Core::GeoPoint2 &location, const Math::Matrix2 *localFrame, const int srid, const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS = 0;

                    virtual Util::TAKErr getInfo(TAK::Engine::Model::SceneInfo *value) NOTHROWS = 0;

                    virtual Util::TAKErr addUpdateListener(UpdateListener *l) NOTHROWS = 0;
                    virtual Util::TAKErr removeUpdateListener(const UpdateListener &l) NOTHROWS = 0;

                    virtual Util::TAKErr clampToGround() NOTHROWS = 0;
                };

                class ENGINE_API SceneObjectControl::UpdateListener
                {
                public :
                    virtual ~UpdateListener() NOTHROWS = 0;
                    virtual Util::TAKErr onBoundsChanged(const TAK::Engine::Feature::Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS = 0;
                    virtual Util::TAKErr onClampToGroundOffsetComputed(const double tz) NOTHROWS = 0;
                };

                ENGINE_API const char *SceneObjectControl_getType() NOTHROWS;
            }
        }
    }
}
#endif
