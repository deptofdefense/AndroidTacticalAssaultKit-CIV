#ifndef TAK_ENGINE_CORE_LAYER2_H_INCLUDED
#define TAK_ENGINE_CORE_LAYER2_H_INCLUDED

#include <memory>

#include "core/Layer.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Core
        {
            class ENGINE_API Layer2
            {
            public :
                class ENGINE_API VisibilityListener;
            protected :
                virtual ~Layer2() NOTHROWS = 0;
            public:
                virtual const char *getName() const NOTHROWS = 0;
                virtual bool isVisible() const NOTHROWS = 0;
                virtual void setVisible(const bool v) NOTHROWS = 0;
                virtual Util::TAKErr addVisibilityListener(VisibilityListener *l) NOTHROWS = 0;
                virtual Util::TAKErr removeVisibilityListener(VisibilityListener *l) NOTHROWS = 0;
                virtual Util::TAKErr getExtension(void **value, const char *extensionName) const NOTHROWS = 0;
            };

            typedef std::unique_ptr<Layer2, void(*)(const Layer2 *)> Layer2Ptr;

            class ENGINE_API Layer2::VisibilityListener
            {
            protected:
                virtual ~VisibilityListener() NOTHROWS = 0;
            public:
                /**
                 * This callback functionis invoked when there is a change to the layer's visibility.
                 *
                 * @param layer     The layer
                 * @param visible   The new visibility state of the layer
                 *
                 * @return  TE_Ok on success, TE_Done if the listener is no longer
                 *          interested in receiving events and should be
                 *          unsubscribed. Other codes may be logged but will
                 *          otherwise not be handled.
                 */
                virtual Util::TAKErr layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS = 0;
            };
        }
    }
}

#endif