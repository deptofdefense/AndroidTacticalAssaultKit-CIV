#ifndef TAK_ENGINE_PORT_STRINGBUILDER_H_INCLUDED
#define TAK_ENGINE_PORT_STRINGBUILDER_H_INCLUDED

#include "port/String.h"

namespace TAK {
	namespace Engine {
		namespace Port {
			class ENGINE_API StringBuilder {
			public:
				StringBuilder() NOTHROWS;
				~StringBuilder() NOTHROWS;

				/**
				 * The null terminated characters
				 */
				const char *chars() const NOTHROWS;

				/**
				 * The null terminated characters
				 */
				const char *c_str() const NOTHROWS; // for easy porting of std::string

				/**
				 * Get a copy of the string
				 */
				Util::TAKErr str(String &out) const NOTHROWS;
				
				/**
				 * Any accumulated error from the operator<< style of calls
				 */
				Util::TAKErr error() const NOTHROWS;

				/**
				 * Number of characters in the string
				 */
				size_t length() const NOTHROWS;

				/**
				 * Capacity of the buffer (including null termination)
				 */
				size_t capacity() const NOTHROWS;
				
				/**
				 * Resize the builder to a given length. Capacity will be length + 1 for null
				 * termination. The new length of the buffer will remain the same or decrease
				 * when resized to a lesser length.
				 */
				Util::TAKErr resize(size_t length) NOTHROWS;

				/**
				 * Resizes to length if needed
				 */
				Util::TAKErr ensure(size_t length) NOTHROWS;


				Util::TAKErr append(const char *chars, size_t len) NOTHROWS;
				Util::TAKErr append(char v) NOTHROWS;
				Util::TAKErr append(unsigned char v) NOTHROWS;
				Util::TAKErr append(short v) NOTHROWS;
				Util::TAKErr append(unsigned short v) NOTHROWS;
				Util::TAKErr append(int v) NOTHROWS;
				Util::TAKErr append(unsigned int v) NOTHROWS;
				Util::TAKErr append(long v) NOTHROWS;
				Util::TAKErr append(unsigned long v) NOTHROWS;
				Util::TAKErr append(long long v) NOTHROWS;
				Util::TAKErr append(unsigned long long v) NOTHROWS;
				Util::TAKErr append(long double v) NOTHROWS;
				Util::TAKErr append(double v) NOTHROWS;
				Util::TAKErr append(float v) NOTHROWS;
				Util::TAKErr append(const char *str) NOTHROWS;

				StringBuilder &operator<<(char v) NOTHROWS;
				StringBuilder &operator<<(unsigned char v) NOTHROWS;
				StringBuilder &operator<<(short v) NOTHROWS;
				StringBuilder &operator<<(unsigned short v) NOTHROWS;
				StringBuilder &operator<<(int v) NOTHROWS;
				StringBuilder &operator<<(unsigned int v) NOTHROWS;
				StringBuilder &operator<<(long v) NOTHROWS;
				StringBuilder &operator<<(unsigned long v) NOTHROWS;
				StringBuilder &operator<<(long long v) NOTHROWS;
				StringBuilder &operator<<(unsigned long long v) NOTHROWS;
				StringBuilder &operator<<(long double v) NOTHROWS;
				StringBuilder &operator<<(double v) NOTHROWS;
				StringBuilder &operator<<(float v) NOTHROWS;
				StringBuilder &operator<<(const char *str) NOTHROWS;

			private:
				char *getBuf() NOTHROWS;

				// small string optimization
				enum {
					SMALL_STRING_CAP_MAX = 16
				};

				struct {
					char *dyn;
					char inl[SMALL_STRING_CAP_MAX];
				} buf_;

				size_t len_;
				size_t cap_;
				Util::TAKErr err_;
			};

			inline Util::TAKErr StringBuilder_combine(StringBuilder &sb) NOTHROWS  {
				return Util::TE_Ok;
			}

			template <typename T, typename ...Ts>
			Util::TAKErr StringBuilder_combine(StringBuilder &sb, T &&first, Ts &&...rest) NOTHROWS {
				sb << std::forward<T>(first);
				if (sb.error() != Util::TE_Ok)
					return sb.error();
				return StringBuilder_combine(sb, std::forward<Ts>(rest)...);
			}

			template <typename ...Ts>
			Util::TAKErr StringBuilder_combine(StringBuilder &sb, Ts &&...args) NOTHROWS {
				return StringBuilder_combine(sb, std::forward<Ts>(args)...);
			}
		}
	}
}

#endif