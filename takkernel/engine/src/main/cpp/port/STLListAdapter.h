#ifndef TAK_ENGINE_PORT_STLLISTADAPTER_H_INCLUDED
#define TAK_ENGINE_PORT_STLLISTADAPTER_H_INCLUDED

#include "port/Collection.h"
#include "port/STLIteratorAdapter.h"

#include <algorithm>
#include <memory>
#include <list>

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T, class Allocator = std::allocator<T>>
            class STLListAdapter : public Collection<T>
            {
            private:
                typedef std::list<T, Allocator> ListImpl;
                typedef std::unique_ptr<ListImpl, void(*)(const ListImpl *)> ListPtr;
                typedef STLIteratorAdapter<T, ListImpl> IteratorAdapter;
            public:
                using typename Collection<T>::IteratorPtr;
            public:
                STLListAdapter();
                STLListAdapter(std::list<T, Allocator> &impl);
            public:
                virtual TAK::Engine::Util::TAKErr add(T elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr remove(T &elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr contains(bool *value, T &elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr clear() NOTHROWS;

                virtual std::size_t size() NOTHROWS;
                virtual bool empty() NOTHROWS;

                virtual TAK::Engine::Util::TAKErr iterator(IteratorPtr &iterator) NOTHROWS;
            private:
                static void deleteDefault(const ListImpl *ptr);
                static void deleteDefault(const Iterator2<T> *ptr);
                static void deleteNone(const ListImpl *ptr);
            private:
                ListPtr impl;
            };

            template<class T, class Allocator>
            inline STLListAdapter<T, Allocator>::STLListAdapter() :
                impl(ListPtr(new ListImpl(), deleteDefault))
            {}

            template<class T, class Allocator>
            inline STLListAdapter<T, Allocator>::STLListAdapter(ListImpl &impl_) :
                impl(ListPtr(&impl_, deleteNone))
            {}

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLListAdapter<T, Allocator>::add(T value) NOTHROWS
            {
                try {
                    impl->push_back(value);
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLListAdapter<T, Allocator>::remove(T &value) NOTHROWS
            {
                try {
                    typename ListImpl::iterator entry;
                    entry = std::find(impl->begin(), impl->end(), value);
                    if (entry != impl->end())
                        impl->erase(entry);
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLListAdapter<T, Allocator>::clear() NOTHROWS
            {
                try {
                    impl->clear();
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Allocator>
            inline std::size_t STLListAdapter<T, Allocator>::size() NOTHROWS
            {
                return impl->size();
            }

            template<class T, class Allocator>
            inline bool STLListAdapter<T, Allocator>::empty() NOTHROWS
            {
                return impl->empty();
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLListAdapter<T, Allocator>::contains(bool *value, T &elem) NOTHROWS
            {
                typename ListImpl::iterator entry;
                entry = std::find(impl->begin(), impl->end(), elem);
                *value = (entry != impl->end());
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLListAdapter<T, Allocator>::iterator(IteratorPtr &iterator) NOTHROWS
            {
                iterator = IteratorPtr(new IteratorAdapter(*impl), deleteDefault);
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline void STLListAdapter<T, Allocator>::deleteDefault(const ListImpl *ptr)
            {
                delete ptr;
            }

            template<class T, class Allocator>
            inline void STLListAdapter<T, Allocator>::deleteDefault(const Iterator2<T> *ptr)
            {
                delete static_cast<const IteratorAdapter *>(ptr);
            }

            template<class T, class Allocator>
            inline void STLListAdapter<T, Allocator>::deleteNone(const ListImpl *ptr)
            {}
        }
    }
}

#endif
