
#include <cstring>
#include <cinttypes>
#include "port/StringBuilder.h"
#include "StringBuilder.h"

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace {
	
}

StringBuilder::StringBuilder() NOTHROWS
: len(0),
  cap(SMALL_STRING_CAP_MAX),
  err(TE_Ok) {
	buf.dyn = NULL;
	buf.inl[0] = 0;
}

StringBuilder::~StringBuilder() NOTHROWS {
	if (cap > SMALL_STRING_CAP_MAX)
		delete[] buf.dyn;
}

const char *StringBuilder::chars() const NOTHROWS {
	return const_cast<StringBuilder *>(this)->getBuf();
}

const char *StringBuilder::c_str() const NOTHROWS {
	return chars();
}

TAKErr StringBuilder::str(String &out) const NOTHROWS {
	out = chars();
	if (!out.get())
		return TE_OutOfMemory;
	return TE_Ok;
}

char *StringBuilder::getBuf() NOTHROWS {
	return cap > SMALL_STRING_CAP_MAX ? buf.dyn : buf.inl;
}

size_t StringBuilder::length() const NOTHROWS {
	return this->len;
}

size_t StringBuilder::capacity() const NOTHROWS {
	return this->cap;
}

TAKErr StringBuilder::error() const NOTHROWS {
	return this->err;
}

TAKErr StringBuilder::resize(size_t newLength) NOTHROWS {

	size_t newCap = newLength + 1;

	char *newBuf = buf.inl;
	if (newCap > SMALL_STRING_CAP_MAX)
		newBuf = new(std::nothrow) char[newCap];

	if (!newBuf)
		return TE_OutOfMemory;

	size_t copyLen = newLength < len ? newLength : len;
	char *oldBuf = getBuf();

	memcpy(newBuf, oldBuf, copyLen);
	newBuf[copyLen] = 0;

	if (oldBuf == buf.dyn)
		delete[] oldBuf;

	if (newBuf != buf.inl)
		buf.dyn = newBuf;

	len = copyLen;
	cap = newCap;

	return TE_Ok;
}

TAKErr StringBuilder::ensure(size_t length) NOTHROWS {
	if (this->len < length)
		return resize(length);
	return TE_Ok;
}

TAKErr StringBuilder::append(const char *chars, size_t count) NOTHROWS {

	TAKErr code(TE_Ok);

	size_t newLen = this->len + count;
	size_t maxLen = this->cap - 1;

	if (newLen >= maxLen) {
		size_t growLen = this->len + this->len / 2;
		if (growLen < newLen)
			growLen = newLen;
		code = resize(growLen);
	}

	if (code == TE_Ok) {
		char *buf = getBuf();
		memcpy(buf + this->len, chars, count);
		buf[newLen] = 0;
		this->len = newLen;
	}

	return code;
}

TAKErr StringBuilder::append(char v) NOTHROWS {
	return this->append(&v, 1);
}

TAKErr StringBuilder::append(unsigned char v) NOTHROWS {
	return this->append(static_cast<unsigned int>(v));
}

TAKErr StringBuilder::append(short v) NOTHROWS {
	return this->append(static_cast<int>(v));
}

TAKErr StringBuilder::append(unsigned short v) NOTHROWS {
	return this->append(static_cast<unsigned int>(v));
}

#define SNPRINTF_DIRECT(n) \
	TAKErr code (TE_Ok); \
	int required = snprintf(nullptr, 0, n, v); \
	if (required < 0) \
		return TE_Err; \
	code = ensure(this->len + required); \
	if (code == TE_Ok) { \
		snprintf(this->getBuf() + this->len, required + 1, n, v); \
		this->len += required; \
	} \
	return code;

TAKErr StringBuilder::append(int v) NOTHROWS {
	SNPRINTF_DIRECT("%d");
}

TAKErr StringBuilder::append(unsigned int v) NOTHROWS {
	SNPRINTF_DIRECT("%u");
}

TAKErr StringBuilder::append(long v) NOTHROWS {
	SNPRINTF_DIRECT("%ld");
}

TAKErr StringBuilder::append(unsigned long v) NOTHROWS {
	SNPRINTF_DIRECT("%lu");
}

TAKErr StringBuilder::append(long long v) NOTHROWS {
	SNPRINTF_DIRECT("%lld");
}

TAKErr StringBuilder::append(unsigned long long v) NOTHROWS {
	SNPRINTF_DIRECT("%llu");
}

TAKErr StringBuilder::append(double v) NOTHROWS {
	SNPRINTF_DIRECT("%f");
}

TAKErr StringBuilder::append(float vf) NOTHROWS {
	double v = vf;
	SNPRINTF_DIRECT("%f");
}

TAKErr StringBuilder::append(long double vl) NOTHROWS {
	double v = vl;
	SNPRINTF_DIRECT("%f");
}

TAKErr StringBuilder::append(const char *str) NOTHROWS {
	return this->append(str, strlen(str));
}

StringBuilder &StringBuilder::operator<<(char v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned char v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(short v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned short v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(int v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned int v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned long v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long long v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned long long v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long double v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(double v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(float v) NOTHROWS {
	this->err = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(const char *str) NOTHROWS {
	this->err = append(str, strlen(str));
	return *this;
}

