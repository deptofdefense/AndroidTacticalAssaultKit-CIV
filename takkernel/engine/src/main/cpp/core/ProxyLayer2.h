#ifndef TAK_ENGINE_CORE_PROXYLAYER2_H_INCLUDED
#define TAK_ENGINE_CORE_PROXYLAYER2_H_INCLUDED

#include <memory>

#include "core/Layer2.h"
#include "core/AbstractLayer2.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            /**
             * A {@link Layer} that acts as a proxy for another {@link Layer}.
             *
             * @author Developer
             */
            class ProxyLayer2 : public AbstractLayer2
            {
            public :
                class SubjectChangedListener;
            public :
                /**
                 * Creates a new instance with the specified name and no subject.
                 *
                 * @param name  the name
                 * @param name
                 */
                ProxyLayer2(const char *name) NOTHROWS;
                /**
                 * Creates a new instance with the specified name and subject.
                 *
                 * @param name      The name
                 * @param subject   The subject (may be <code>null</code>)
                 */
                ProxyLayer2(const char *name, const std::shared_ptr<Layer2> &subject) NOTHROWS;
                ProxyLayer2(const char *name, Layer2Ptr &&subject) NOTHROWS;
                ~ProxyLayer2() NOTHROWS;
            public :
                /**
                 * Returns the current subject
                 *
                 * @return  The current subject
                 */
                Util::TAKErr get(std::shared_ptr<Layer2> &subject) const NOTHROWS;

                /**
                 * Sets the current subject
                 *
                 * @param subject   The current subject
                 */
                Util::TAKErr set(const std::shared_ptr<Layer2> &subject) NOTHROWS;
                Util::TAKErr set(Layer2Ptr &&subject) NOTHROWS;

                /**
                 * Adds the specified {@link OnProxySubjectChangedListener}.
                 *
                 * @param l The listener
                 */
                Util::TAKErr addSubjectChangedListener(ProxyLayer2::SubjectChangedListener *l) NOTHROWS;

                /**
                 * Removes the specified {@link OnProxySubjectChangedListener}.
                 *
                 * @param l The listener
                 */
                Util::TAKErr removeSubjectChangedListener(ProxyLayer2::SubjectChangedListener *l) NOTHROWS;
            protected :
                /**
                 * Invokes the subject changed callback on all subscribed listeners.
                 *
                 * <P>This method should always be externally synchronized on
                 * <code>this</code>.
                 */
                void dispatchOnProxySubjectChangedNoSync() NOTHROWS;
            protected :
                /**
                * The subject.
                */
                std::shared_ptr<Layer2> subject_;
            private :
                /**
                * The subject changed listeners
                */
                std::set<SubjectChangedListener *> proxy_subject_changed_listeners_;
            };

            /**
            * Callback interface for proxy subject changes.
            *
            * @author Developer
            */
            class ProxyLayer2::SubjectChangedListener
            {
            protected :
                virtual ~SubjectChangedListener() NOTHROWS = 0;
            public :
                /**
                * This method is invoked when the proxy subject has changed.
                *
                * @param layer The layer whose subject changed
                */
                virtual void subjectChanged(ProxyLayer2 &layer, const std::shared_ptr<Layer2> &subject) NOTHROWS = 0;
            };
        }
    }
}

#endif
