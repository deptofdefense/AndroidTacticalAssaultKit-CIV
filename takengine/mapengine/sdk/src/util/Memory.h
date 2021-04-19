#ifndef TAK_ENGINE_UTIL_MEMORY_H_INCLUDED
#define TAK_ENGINE_UTIL_MEMORY_H_INCLUDED

#include <memory>

#include "port/Platform.h"

#define TAK_UNIQUE_PTR(a) \
    std::unique_ptr<a, void(*)(const a *)>

namespace TAK {
    namespace Engine {
        namespace Util {
#if 1
            template<class T>
            class array_ptr
            {
            public:
                array_ptr() NOTHROWS;
                array_ptr(T *value) NOTHROWS;
            private:
                array_ptr(const array_ptr &other) NOTHROWS;
            public:
                ~array_ptr() NOTHROWS;
            public:
                T *get() NOTHROWS;
                const T *get() const NOTHROWS;
                T *release() NOTHROWS;
                void reset(T *other = nullptr) NOTHROWS;
            public :
                T &operator[](const std::size_t i) NOTHROWS;
                T operator[](const std::size_t i) const NOTHROWS;
            private:
                T *value;
            };

            template<class T>
            array_ptr<T>::array_ptr() NOTHROWS :
            value(nullptr)
            {}

            template<class T>
            array_ptr<T>::array_ptr(T *value) NOTHROWS :
                value(value)
            {}


            template<class T>
            array_ptr<T>::~array_ptr() NOTHROWS
            {
                if (value) delete [] value;
            }

            template<class T>
            T *array_ptr<T>::get() NOTHROWS
            {
                return value;
            }

            template<class T>
            const T *array_ptr<T>::get() const NOTHROWS
            {
                return value;
            }

            template<class T>
            T *array_ptr<T>::release() NOTHROWS
            {
                T *r = value;
                value = NULL;
                return r;
            }

            template<class T>
            void array_ptr<T>::reset(T *other) NOTHROWS
            {
                if (value) {
                    delete[] value;
                }
                value = other;
            }

            template<class T>
            T &array_ptr<T>::operator[](const std::size_t index) NOTHROWS
            {
                return value[index];
            }

            template<class T>
            T array_ptr<T>::operator[](const std::size_t index) const NOTHROWS
            {
                return value[index];
            }
#else
            template<class T>
            void array_deleter(T *obj)
            {
                delete[] obj;
            }

            template<class T>
            using array_ptr = std::unique_ptr<T, array_deleter<T>>;
#endif

            template<class T>
            inline void Memory_deleter(T *obj)
            {
                delete obj;
            }

            template<class Iface, class Impl>
            inline void Memory_deleter(Iface *obj)
            {
#if defined(__ANDROID__) && !defined(RTTI_ENABLED)
                Impl *impl = static_cast<Impl *>(obj);
#else
                Impl *impl = dynamic_cast<Impl *>(obj);
#endif
                delete impl;
            }

            template<class T>
            inline void Memory_deleter_const(const T *obj)
            {
                delete obj;
            }

            template<class Iface, class Impl>
            inline void Memory_deleter_const(const Iface *obj)
            {
#if defined(__ANDROID__) && !defined(RTTI_ENABLED)
                const Impl *impl = static_cast<const Impl *>(obj);
#else
                const Impl *impl = dynamic_cast<const Impl *>(obj);
#endif
                delete impl;
            }

            template<class T>
            inline void Memory_void_deleter(void *obj)
            {
                const T *impl = static_cast<T *>(obj);
                delete impl;
            }

            template<class T>
            inline void Memory_void_deleter_const(const void *obj)
            {
                const T *impl = static_cast<const T *>(obj);
                delete impl;
            }

            template<class T>
            inline void Memory_void_array_deleter(void *obj)
            {
                const T *impl = static_cast<T *>(obj);
                delete[] impl;
            }

            template<class T>
            inline void Memory_void_array_deleter_const(const void *obj)
            {
                const T *impl = static_cast<const T *>(obj);
                delete[] impl;
            }

            template<class Iface>
            inline void Memory_leaker(Iface *obj)
            {}

            template<class Iface>
            inline void Memory_leaker_const(const Iface * /*obj*/)
            {}

            template<class T>
            inline void Memory_array_deleter(T * obj)
            {
                delete[] obj;
            }

            template<class T>
            inline void Memory_array_deleter_const(const T *obj)
            {
                delete[] obj;
            }

            template <typename T>
            inline void Memory_free(T *pod)
            {
                ::free(pod);
            }

            template <typename T>
            inline void Memory_free_const(const T *pod)
            {
                ::free(const_cast<T *>(pod));
            }
        }
    }
}

#endif
