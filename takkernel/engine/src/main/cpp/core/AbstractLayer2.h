#ifndef TAK_ENGINE_CORE_ABSTRACTLAYER2_H_INCLUDED
#define TAK_ENGINE_CORE_ABSTRACTLAYER2_H_INCLUDED

#include <map>
#include <set>
#include <string>

#include "core/Layer2.h"
#include "port/String.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Core {

            class ENGINE_API AbstractLayer2 : public Layer2
            {
            protected :
                AbstractLayer2(const char * name) NOTHROWS;
                virtual ~AbstractLayer2() NOTHROWS;
            protected :
                /**
                 * Registers the specified extension with the layer.
                 *
                 * <P>This method should <B>ONLY</B> be invoked from within the constructor.
                 *
                 * @param e The extension to be registered.
                 */

                void registerExtension(const char *extensionName, std::unique_ptr<void, void(*)(const void *)> &&extension) NOTHROWS;

                Util::TAKErr setName(const char *name) NOTHROWS;
            public :
                virtual const char *getName() const NOTHROWS override;
                virtual bool isVisible() const NOTHROWS override;
                virtual void setVisible(const bool v) NOTHROWS override;
                virtual Util::TAKErr addVisibilityListener(VisibilityListener *l) NOTHROWS override;
                virtual Util::TAKErr removeVisibilityListener(VisibilityListener *l) NOTHROWS override;
                virtual Util::TAKErr getExtension(void **value, const char *extensionName) const NOTHROWS override;
            protected :
                void dispatchOnVisibleChangedNoSync() NOTHROWS;
            protected :
                mutable Thread::Mutex mutex_;
                Port::String name_;
                bool visible_;
            private :
                std::map<std::string, std::unique_ptr<void, void(*)(const void *)>> extensions;
                std::set<Layer2::VisibilityListener *> layerVisibleChangedListeners;
            };
        }
    }
}

#endif
