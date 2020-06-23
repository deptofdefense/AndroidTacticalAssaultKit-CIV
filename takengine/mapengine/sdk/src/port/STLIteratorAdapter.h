#ifndef TAK_ENGINE_PORT_STLITERATORADAPTER_H_INCLUDED
#define TAK_ENGINE_PORT_STLITERATORADAPTER_H_INCLUDED

#include <memory>

#include "port/Iterator2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T, class C>
            class STLIteratorAdapter : public Iterator2<T>
            {
            public:
                STLIteratorAdapter(C &impl) NOTHROWS;
            public:
                virtual TAK::Engine::Util::TAKErr next() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr get(T &value) const NOTHROWS;
            private :
                typename C::iterator it;
                const typename C::iterator end;
            };

            template<class T, class C>
            inline STLIteratorAdapter<T, C>::STLIteratorAdapter(C &impl) NOTHROWS :
                it(impl.begin()),
                end(impl.end())
            {}

            template<class T, class C>
            inline TAK::Engine::Util::TAKErr STLIteratorAdapter<T, C>::next() NOTHROWS
            {
                if (it == end)
                return TAK::Engine::Util::TE_Done;
                try {
                    it++;
                    if (it == end)
                        return TAK::Engine::Util::TE_Done;
                    else
                        return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class C>
            inline TAK::Engine::Util::TAKErr STLIteratorAdapter<T, C>::get(T &value) const NOTHROWS
            {
                if (it == end)
                return TAK::Engine::Util::TE_Done;
                value = *it;
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class C>
            class STLIteratorAdapter_const : public Iterator2<T>
            {
            public:
                STLIteratorAdapter_const(C &impl) NOTHROWS;
            public:
                virtual TAK::Engine::Util::TAKErr next() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr get(T &value) const NOTHROWS;
            private :
                typename C::const_iterator it;
                const typename C::const_iterator end;
            };

            template<class T, class C>
            inline STLIteratorAdapter_const<T, C>::STLIteratorAdapter_const(C &impl) NOTHROWS :
                it(impl.begin()),
                end(impl.end())
            {}

            template<class T, class C>
            inline TAK::Engine::Util::TAKErr STLIteratorAdapter_const<T, C>::next() NOTHROWS
            {
                if (it == end)
                return TAK::Engine::Util::TE_Done;
                try {
                    it++;
                    if (it == end)
                        return TAK::Engine::Util::TE_Done;
                    else
                        return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class C>
            inline TAK::Engine::Util::TAKErr STLIteratorAdapter_const<T, C>::get(T &value) const NOTHROWS
            {
                if (it == end)
                    return TAK::Engine::Util::TE_Done;
                value = *it;
                return TAK::Engine::Util::TE_Ok;
            }
        }
    }
}
#endif // TAK_ENGINE_PORT_STLITERATORADAPTER_H_INCLUDED
