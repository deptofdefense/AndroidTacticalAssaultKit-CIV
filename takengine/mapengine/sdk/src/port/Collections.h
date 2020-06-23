#ifndef TAK_ENGINE_PORT_COLLECTIONS_H_INCLUDED
#define TAK_ENGINE_PORT_COLLECTIONS_H_INCLUDED

#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {

            template<class T>
            TAK::Engine::Util::TAKErr Collections_addAll(Collection<T> &sink, Collection<T> &src) NOTHROWS;
#if 0
            template<class T>
            TAK::Engine::Util::TAKErr Collections_retainAll(Collection<T> &sink, Collection<T> &src) NOTHROWS;
#endif
            template<class T>
            TAK::Engine::Util::TAKErr Collections_removeAll(Collection<T> &sink, Collection<T> &src) NOTHROWS;

            template<class T, class V, class Transmute>
            TAK::Engine::Util::TAKErr Collections_transmute(Collection<V> &sink, Collection<T> &src) NOTHROWS;

            template <typename T, typename F>
            inline TAK::Engine::Util::TAKErr Collections_forEach(Collection<T> &c, F &&func);
            
            /*****************************************************************/

            template<class T>
            inline TAK::Engine::Util::TAKErr Collections_addAll(Collection<T> &sink, Collection<T> &src) NOTHROWS
            {
                TAK::Engine::Util::TAKErr code;

                if (src.empty())
                    return TAK::Engine::Util::TE_Ok;

                typename Collection<T>::IteratorPtr iter(NULL, NULL);

                code = src.iterator(iter);
                TE_CHECKRETURN_CODE(code);

                do {
                    T arg;
                    code = iter->get(arg);
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    code = sink.add(arg);
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    code = iter->next();
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                } while (true);
                return (code == TAK::Engine::Util::TE_Done) ? TAK::Engine::Util::TE_Ok : code;
            }

#if 0
            template<class T>
            inline TAK::Engine::Util::TAKErr Collections_retainAll(Collection<T> &sink, Collection<T> &src) NOTHROWS
            {
                return TE_Unsupported;
            }
#endif

            template<class T>
            inline TAK::Engine::Util::TAKErr Collections_removeAll(Collection<T> &sink, Collection<T> &src) NOTHROWS
            {
                TAK::Engine::Util::TAKErr code;

                if (src.empty())
                    return TAK::Engine::Util::TE_Ok;

                typename Collection<T>::IteratorPtr iter(NULL, NULL);

                code = src.iterator(iter);
                TE_CHECKRETURN_CODE(code);

                do {
                    T arg;
                    code = iter->get(arg);
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    code = sink.remove(arg);
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    code = iter->next();
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                } while (true);
                return (code == TAK::Engine::Util::TE_Done) ? TAK::Engine::Util::TE_Ok : code;
            }

            template<class T, class V, class Transmute>
            TAK::Engine::Util::TAKErr Collections_transmute(Collection<V> &sink, Collection<T> &src) NOTHROWS
            {
                TAK::Engine::Util::TAKErr code;

                if (src.empty())
                    return TAK::Engine::Util::TE_Ok;

                typename Collection<T>::IteratorPtr iter(NULL, NULL);

                code = src.iterator(iter);
                TE_CHECKRETURN_CODE(code);

                do {
                    T arg;
                    code = iter->get(arg);
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    Transmute transmute;
                    code = sink.add(transmute(arg));
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                    code = iter->next();
                    if (code != TAK::Engine::Util::TE_Ok)
                        break;
                } while (true);
                return (code == TAK::Engine::Util::TE_Done) ? TAK::Engine::Util::TE_Ok : code;
            }
            
            template <typename T, typename F>
            TAK::Engine::Util::TAKErr Collections_forEach(Collection<T> &c, F &&func)
            {
                typename Collection<T>::IteratorPtr iter(NULL, NULL);
                TAK::Engine::Util::TAKErr code = c.iterator(iter);
                TE_CHECKRETURN_CODE(code);
                do {
                    T value;
                    code = iter->get(value);
                    TE_CHECKBREAK_CODE(code);
                    code = func(value);
                    TE_CHECKBREAK_CODE(code);
                } while ((code = iter->next()) == TAK::Engine::Util::TE_Ok);
                
                return code == TAK::Engine::Util::TE_Done ? TAK::Engine::Util::TE_Ok : code;
            }
        }
    }
}

#endif
