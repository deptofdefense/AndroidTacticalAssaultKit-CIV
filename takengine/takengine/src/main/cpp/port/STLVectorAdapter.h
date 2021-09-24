#ifndef TAK_ENGINE_PORT_STLVECTORADAPTER_H_INCLUDED
#define TAK_ENGINE_PORT_STLVECTORADAPTER_H_INCLUDED

#include <algorithm>
#include <memory>
#include <vector>
#include <stdexcept>

#include "port/STLIteratorAdapter.h"
#include "port/Vector.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T, class Allocator = std::allocator<T>>
            class STLVectorAdapter : public Vector<T>
            {
            private :
                typedef std::vector<T, Allocator> VectorImpl;
                typedef STLIteratorAdapter<T, VectorImpl> IteratorAdapter;
            private:
                typedef std::unique_ptr<VectorImpl, void(*)(const VectorImpl *)> VectorPtr;
            public:
                using typename Collection<T>::IteratorPtr;
            public:
                STLVectorAdapter();
                STLVectorAdapter(VectorImpl &impl);
            public:
                virtual TAK::Engine::Util::TAKErr add(T value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr remove(T &value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr insert(T value, const std::size_t idx) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr erase(const std::size_t idx) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr clear() NOTHROWS;

                virtual TAK::Engine::Util::TAKErr get(T &value, const std::size_t idx) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr set(T value, const std::size_t idx) NOTHROWS;

                virtual std::size_t size() NOTHROWS;
                virtual bool empty() NOTHROWS;

                virtual TAK::Engine::Util::TAKErr contains(bool *value, T &elem) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr iterator(IteratorPtr &iterator) NOTHROWS;
            private :
                static void deleteDefault(const VectorImpl *ptr);
                static void deleteDefault(const Iterator2<T> *ptr);
                static void deleteNone(const VectorImpl *ptr);
            private:
                VectorPtr impl;
            };

            template<class T, class Allocator>
            inline STLVectorAdapter<T, Allocator>::STLVectorAdapter() :
                impl(VectorPtr(new VectorImpl(), deleteDefault))
            {}

            template<class T, class Allocator>
            inline STLVectorAdapter<T, Allocator>::STLVectorAdapter(VectorImpl &impl_) :
                impl(VectorPtr(&impl_, deleteNone))
            {}

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::add(T value) NOTHROWS
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
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::remove(T &value) NOTHROWS
            {
                try {
                    typename VectorImpl::iterator entry;
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
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::insert(T value, const std::size_t idx) NOTHROWS
            {
                if (idx >= impl->size())
                    return TAK::Engine::Util::TE_BadIndex;
                typename VectorImpl::iterator it = impl->begin() + idx;
                impl->insert(it, value);
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::erase(const std::size_t idx) NOTHROWS
            {
                if (idx >= impl->size())
                return TAK::Engine::Util::TE_BadIndex;
                typename VectorImpl::iterator it = impl->begin() + idx;
                impl->erase(it);
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::clear() NOTHROWS
            {
                impl->clear();
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::get(T &value, const std::size_t idx) NOTHROWS
            {
                try {
                    value = (*impl)[idx];
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (std::out_of_range) {
                    return TAK::Engine::Util::TE_BadIndex;
                }
                catch (...){
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::set(T value, const std::size_t idx) NOTHROWS
            {
                try {
                    (*impl)[idx] = value;
                    return TAK::Engine::Util::TE_Ok;
                }
                catch (std::out_of_range) {
                    return TAK::Engine::Util::TE_BadIndex;
                }
                catch (...) {
                    return TAK::Engine::Util::TE_Err;
                }
            }

            template<class T, class Allocator>
            inline std::size_t STLVectorAdapter<T, Allocator>::size() NOTHROWS
            {
                return impl->size();
            }

            template<class T, class Allocator>
            inline bool STLVectorAdapter<T, Allocator>::empty() NOTHROWS
            {
                return impl->empty();
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::contains(bool *value, T &elem) NOTHROWS
            {
                typename VectorImpl::iterator entry;
                entry = std::find(impl->begin(), impl->end(), elem);
                *value = (entry != impl->end());
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline TAK::Engine::Util::TAKErr STLVectorAdapter<T, Allocator>::iterator(IteratorPtr &iterator) NOTHROWS
            {
                iterator = IteratorPtr(new IteratorAdapter(*impl), deleteDefault);
                return TAK::Engine::Util::TE_Ok;
            }

            template<class T, class Allocator>
            inline void STLVectorAdapter<T, Allocator>::deleteDefault(const VectorImpl *ptr)
            {
                delete ptr;
            }

            template<class T, class Allocator>
            inline void STLVectorAdapter<T, Allocator>::deleteNone(const VectorImpl * /*ptr*/)
            {}

            template<class T, class Allocator>
            inline void STLVectorAdapter<T, Allocator>::deleteDefault(const Iterator2<T> *ptr)
            {
                delete static_cast<const IteratorAdapter *>(ptr);
            }
        }
    }
}

#endif // TAK_ENGINE_PORT_STLVECTORADAPTER_H_INCLUDED
