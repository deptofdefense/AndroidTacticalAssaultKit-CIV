#ifndef ATAKMAP_PORT_ITERATOR_H_INCLUDED
#define ATAKMAP_PORT_ITERATOR_H_INCLUDED

namespace atakmap {
    namespace port {
        template<class T>
        class Iterator
        {
        public:
            virtual ~Iterator();
        public :
            virtual bool hasNext() = 0;
            virtual T next() = 0;
            virtual T get() = 0;
        };

        template<class T>
        inline Iterator<T>::~Iterator() {}
    }
}

#endif
