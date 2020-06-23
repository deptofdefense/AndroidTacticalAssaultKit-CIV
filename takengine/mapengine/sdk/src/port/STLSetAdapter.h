#ifndef TAK_ENGINE_PORT_STLSETADAPTER_H_INCLUDED
#define TAK_ENGINE_PORT_STLSETADAPTER_H_INCLUDED

#include "port/Set.h"
#include "port/STLIteratorAdapter.h"

#include <algorithm>
#include <memory>
#include <set>

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T, class Compare = std::less<T>, class Allocator = std::allocator<T>>
            class STLSetAdapter : public Set<T>
            {
            private:
                typedef std::set<T, Compare, Allocator> SetImpl;
                typedef std::unique_ptr<SetImpl, void(*)(const SetImpl *)> SetPtr;
                typedef STLIteratorAdapter<T, SetImpl> IteratorAdapter;
            public:
                using typename Collection<T>::IteratorPtr;
            public:
                STLSetAdapter();
                STLSetAdapter(std::set<T, Compare, Allocator> &impl);
            public:
                virtual TAK::Engine::Util::TAKErr add(T elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr remove(T &elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr contains(bool *value, T &elem) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr clear() NOTHROWS;

                virtual std::size_t size() NOTHROWS;
                virtual bool empty() NOTHROWS;

                virtual TAK::Engine::Util::TAKErr iterator(IteratorPtr &iterator) NOTHROWS;
            private :
                static void deleteDefault(const SetImpl *ptr);
                static void deleteDefault(const Iterator2<T> *ptr);
                static void deleteNone(const SetImpl *ptr);
            private:
                SetPtr impl;
            };

            template<class T, class Compare, class Allocator>
            inline STLSetAdapter<T, Compare, Allocator>::STLSetAdapter() :
                impl(SetPtr(new SetImpl(), deleteDefault))
            {}

            template<class T, class Compare, class Allocator>
            inline STLSetAdapter<T, Compare, Allocator>::STLSetAdapter(SetImpl &impl_) :
                impl(SetPtr(&impl_, deleteNone))
            {}

            template<class T, class Compare, class Allocator>
            inline TAK::Engine::Util::TAKErr STLSetAdapter<T, Compare, Allocator>::add(T value) NOTHROWS
            {
                try {
                    impl->insert(value);
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Compare, class Allocator>
            inline TAK::Engine::Util::TAKErr STLSetAdapter<T, Compare, Allocator>::remove(T &value) NOTHROWS
            {
                try {
                    impl->erase(value);
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Compare, class Allocator>
            inline TAK::Engine::Util::TAKErr STLSetAdapter<T, Compare, Allocator>::clear() NOTHROWS
            {
                try {
                    impl->clear();
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Compare, class Allocator>
            inline std::size_t STLSetAdapter<T, Compare, Allocator>::size() NOTHROWS
            {
                return impl->size();
            }

            template<class T, class Compare, class Allocator>
            inline bool STLSetAdapter<T, Compare, Allocator>::empty() NOTHROWS
            {
                return impl->empty();
            }

            template<class T, class Compare, class Allocator>
            inline TAK::Engine::Util::TAKErr STLSetAdapter<T, Compare, Allocator>::contains(bool *value, T &elem) NOTHROWS
            {
                typename SetImpl::iterator entry;
                entry = impl->find(elem);
                *value = (entry != impl->end());
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Compare, class Allocator>
            inline TAK::Engine::Util::TAKErr STLSetAdapter<T, Compare, Allocator>::iterator(IteratorPtr &iterator) NOTHROWS
            {
                iterator = IteratorPtr(new IteratorAdapter(*impl), deleteDefault);
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Compare, class Allocator>
            inline void STLSetAdapter<T, Compare, Allocator>::deleteDefault(const SetImpl *ptr)
            {
                delete ptr;
            }

            template<class T, class Compare, class Allocator>
            inline void STLSetAdapter<T, Compare, Allocator>::deleteDefault(const Iterator2<T> *ptr)
            {
                delete static_cast<const IteratorAdapter *>(ptr);
            }

            template<class T, class Compare, class Allocator>
            inline void STLSetAdapter<T, Compare, Allocator>::deleteNone(const SetImpl *ptr)
            {}
        }
    }
}

#endif
