#ifndef TAK_ENGINE_PORT_COLLECTION_H_INCLUDED
#define TAK_ENGINE_PORT_COLLECTION_H_INCLUDED

#include "port/Iterator2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T>
            class Collection
            {
            public :
                typedef std::unique_ptr<Iterator2<T>, void(*)(const Iterator2<T> *)> IteratorPtr;
                typedef std::unique_ptr<Collection<T>, void(*)(const Collection<T> *)> Ptr;
            protected :
                virtual ~Collection() NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr add(T elem) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr remove(T &elem) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr contains(bool *value, T &elem) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr clear() NOTHROWS = 0;

                virtual std::size_t size() NOTHROWS = 0;
                virtual bool empty() NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr iterator(IteratorPtr &iterator) NOTHROWS = 0;
            };

            template<class T>
            inline Collection<T>::~Collection() NOTHROWS
            {}
        }
    }
}
#endif // TAK_ENGINE_PORT_SET_H_INCLUDED
