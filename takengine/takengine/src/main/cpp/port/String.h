#ifndef TAK_ENGINE_PORT_STRING_H_INCLUDED
#define TAK_ENGINE_PORT_STRING_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            class ENGINE_API String
            {
            public: // constructors
                String() NOTHROWS;
                String(const char*) NOTHROWS;          // Copies supplied C string.
                String(const String&) NOTHROWS;        // Copies supplied String.
                String(const char*, std::size_t size) NOTHROWS;        // Copies supplied String.
            public: // destructors
                ~String() NOTHROWS;
            public: // operator overload
                // Deletes old, copies String.
                String& operator= (const String&)NOTHROWS;
                // Deletes old, copies C string.
                String& operator= (const char*)NOTHROWS;
                // Uses string comparison.  NULL Strings compare equal.
                bool operator== (const String&) const NOTHROWS;
                // Inverse of operator==.
                bool operator!= (const String&) const NOTHROWS;
                bool operator== (const char *) const NOTHROWS;
                // Inverse of operator==.
                bool operator!= (const char *) const NOTHROWS;
                operator const char* () const NOTHROWS;
                // No bounds checking.
                char& operator[] (int) NOTHROWS;
                // No bounds checking.
                char operator[] (int) const NOTHROWS;
                // Returns a non-const pointer to the wrapped C string.  The String still manages the memory.
                char* get() NOTHROWS;
                const char *get() const NOTHROWS;
            private:
                char* data;
            };

            ENGINE_API Util::TAKErr String_parseDouble(double *value, const char *str) NOTHROWS;
            ENGINE_API Util::TAKErr String_parseInteger(int *value, const char *str, const int base = 10) NOTHROWS;


            /**
             * Cross-platform compare ignoring case
             */
            ENGINE_API Util::TAKErr String_compareIgnoreCase(int *result, const char *lhs, const char *rhs) NOTHROWS;

            ENGINE_API bool String_less(const char *a, const char *b) NOTHROWS;
            ENGINE_API bool String_equal(const char *a, const char *b) NOTHROWS;
            ENGINE_API int String_strcmp(const char *lhs, const char *rhs) NOTHROWS;
            ENGINE_API char* String_strcasestr(const char* lhs, const char* rhs) NOTHROWS;
            ENGINE_API int String_strcasecmp(const char *lhs, const char *rhs) NOTHROWS;
            ENGINE_API bool String_endsWith(const char *str, const char *suffix) NOTHROWS;
            /** returns false if output contains no characeters */
            ENGINE_API bool String_trim(TAK::Engine::Port::String &value, const char *str) NOTHROWS;

            struct ENGINE_API StringLess
            {
                bool operator()(const char *a, const char *b) const NOTHROWS
                {
                    return String_less(a, b);
                }
            };

            struct ENGINE_API StringEqual
            {
                StringEqual(const char *cstr) NOTHROWS :
                    compareTo(cstr)
                {}
                bool operator()(const char *other) const NOTHROWS
                {
                    return String_equal(compareTo, other);
                }
            private :
                String compareTo;
            };
        }
    }
}

#endif // TAK_ENGINE_PORT_STRING_H_INCLUDED
