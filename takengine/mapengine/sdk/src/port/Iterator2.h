#ifndef TAK_ENGINE_PORT_ITERATOR2_H_INCLUDED
#define TAK_ENGINE_PORT_ITERATOR2_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T>
            class Iterator2
            {
            protected:
                virtual ~Iterator2() NOTHROWS ;
            public:
                virtual TAK::Engine::Util::TAKErr next() NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr get(T &value) const NOTHROWS = 0;
            };

            template<class T>
            inline Iterator2<T>::~Iterator2() NOTHROWS {}
        }
    }
}
#endif
