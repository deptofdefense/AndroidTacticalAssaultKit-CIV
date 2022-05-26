#ifndef TAK_ENGINE_PORT_SET_H_INCLUDED
#define TAK_ENGINE_PORT_SET_H_INCLUDED

#include "port/Collection.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T>
            class Set : public Collection<T>
            {
            protected :
                virtual ~Set() NOTHROWS = 0;
            };

            template<class T>
            inline Set<T>::~Set() NOTHROWS
            {}
        }
    }
}

#endif
