#ifndef ATAKMAP_UTIL_MEMBUFFER_H_INCLUDED
#define ATAKMAP_UTIL_MEMBUFFER_H_INCLUDED

#include <cstdint>

#include <stdexcept>

#include <algorithm>

#include "port/Platform.h"

namespace atakmap {
    namespace util {
        //template<class T>
        //class MemBufferT;

        class ENGINE_API MemBufferImpl
        {
        public :
            MemBufferImpl();
            MemBufferImpl(size_t size);
            ~MemBufferImpl();
        public :
            void resize(size_t size);
            size_t getCapacity() const;
            void *get();
            const void *get() const;
        private :
            void *buffer;
            size_t capacity;
#if 0
#define PUB_API_FRIEND_DEF(type) \
    template<> friend class MemBufferT<type>

            PUB_API_FRIEND_DEF(uint8_t);
            PUB_API_FRIEND_DEF(int8_t);
            PUB_API_FRIEND_DEF(uint16_t);
            PUB_API_FRIEND_DEF(int16_t);
            PUB_API_FRIEND_DEF(uint32_t);
            PUB_API_FRIEND_DEF(int32_t);
            PUB_API_FRIEND_DEF(uint64_t);
            PUB_API_FRIEND_DEF(int64_t);
            PUB_API_FRIEND_DEF(float);
            PUB_API_FRIEND_DEF(double);
#undef PUB_API_FRIEND_DEF
#endif
        };

        template<class T>
        class MemBufferT
        {
        public :
            MemBufferT();
            MemBufferT(size_t numElems);
            ~MemBufferT();
        public :
            T &operator[](const int idx);
            T &operator[](const std::size_t idx);
            const T &operator[](const int idx) const;
            const T &operator[](const std::size_t idx) const;
        public :
            size_t position() const;
            void position(size_t pos);

            size_t limit() const;
            void limit(size_t lim);

            size_t remaining() const;
            size_t capacity() const;

            void flip();
            void rewind();
            void clear();

            size_t get(T *buf, size_t count);
            size_t put(T *buf, size_t count);
            size_t put2(const T *buf, size_t count);

            T get();
            void put(T val);

            T *access();

            template<class V>
            V *accessAs();

            void resize(size_t numElems);
        private :
            MemBufferImpl impl;

            size_t pos;
            size_t lim;
        };

        template <class T>
        inline MemBufferT<T>::MemBufferT() :
            impl(),
            pos(0),
            lim(0) { }

        template<class T>
        inline MemBufferT<T>::MemBufferT(size_t numElems) :
            impl(numElems*sizeof(T)),
            pos(0),
            lim(numElems*sizeof(T))
        {}

        template<class T>
        inline MemBufferT<T>::~MemBufferT()
        {}

        template<class T>
        inline T &MemBufferT<T>::operator[](const int idx)
        {
            if (idx < 0 || static_cast<std::size_t>(idx) >= capacity())
                throw std::out_of_range("idx out of range");
            return reinterpret_cast<T *>(impl.get())[idx];
        }

        template<class T>
        inline const T &MemBufferT<T>::operator[](const int idx) const
        {
            if (idx < 0 || idx >= capacity())
                throw std::out_of_range("idx out of range");
            return reinterpret_cast<T *>(impl.get())[idx];
        }

        template<class T>
        inline T &MemBufferT<T>::operator[](const std::size_t idx)
        {
            if (idx >= capacity())
                throw std::out_of_range("idx out of range");
            return reinterpret_cast<T *>(impl.get())[idx];
        }

        template<class T>
        inline const T &MemBufferT<T>::operator[](const std::size_t idx) const
        {
            if (idx >= capacity())
                throw std::out_of_range("idx out of range");
            return reinterpret_cast<T *>(impl.get())[idx];
        }

        template<class T>
        inline size_t MemBufferT<T>::limit() const
        {
            return this->lim;
        }

        template<class T>
        inline void MemBufferT<T>::limit(size_t l)
        {
            if (l < 0 || l > this->capacity())
                throw std::out_of_range("limit out of range");
            this->lim = l;
            if (this->pos > this->lim)
                this->pos = this->lim;
        }

        template<class T>
        inline size_t MemBufferT<T>::position() const
        {
            return this->pos;
        }

        template<class T>
        inline void MemBufferT<T>::position(size_t p)
        {
            if (p < 0 || p > this->capacity())
                throw std::out_of_range("position out of range");
            this->pos = std::min(p, this->lim);
        }

        template<class T>
        inline size_t MemBufferT<T>::remaining() const
        {
            return (this->lim - this->pos);
        }

        template<class T>
        inline size_t MemBufferT<T>::capacity() const
        {
            return impl.getCapacity() / sizeof(T);
        }

        template<class T>
        inline void MemBufferT<T>::flip()
        {
            this->lim = this->pos;
            this->pos = 0;
        }

        template<class T>
        inline void MemBufferT<T>::rewind()
        {
            this->pos = 0;
        }

        template<class T>
        inline void MemBufferT<T>::clear()
        {
            this->pos = 0;
            this->lim = this->capacity();
        }

        template<class T>
        inline size_t MemBufferT<T>::get(T *buf, size_t count)
        {
            size_t toCopy = std::min(count, remaining());
            if (toCopy) {
                memcpy(buf, reinterpret_cast<T *>(impl.get()) + this->pos, toCopy);
                this->pos += toCopy;
            }
            return toCopy;
        }

        template<class T>
        inline size_t MemBufferT<T>::put(T *buf, size_t count)
        {
            size_t toCopy = std::min(count, remaining());
            if (toCopy) {
                memcpy(reinterpret_cast<T *>(impl.get()) + this->pos, buf, toCopy);
                this->pos += toCopy;
            }
            return toCopy;
        }

        template<class T>
        inline size_t MemBufferT<T>::put2(const T *buf, size_t count)
        {
            size_t toCopy = std::min(count, remaining());
            if (toCopy) {
                memcpy(reinterpret_cast<T *>(impl.get()) + this->pos, buf, toCopy);
                this->pos += toCopy;
            }
            return toCopy;
        }

        template<class T>
        inline T *MemBufferT<T>::access()
        {
            return static_cast<T *>(impl.get());
        }

        template<class T>
        inline T MemBufferT<T>::get()
        {
            if (!this->remaining())
                throw std::out_of_range("buffer underflow");
            return (*this)[this->pos++];
        }

        template<class T>
        inline void MemBufferT<T>::put(T val)
        {
            if (!this->remaining())
                throw std::out_of_range("buffer overflow");
            (*this)[this->pos++] = val;
        }

        template<class T>
        inline void MemBufferT<T>::resize(size_t numElems)
        {
            impl.resize(numElems*sizeof(T));
        }

        template<class T>
        template<class V>
        inline V *MemBufferT<T>::accessAs()
        {
            return reinterpret_cast<V *>(impl.get());
        }
    }
}
#endif // ATAKMAP_UTIL_MEMBUFFER_H_INCLUDED
