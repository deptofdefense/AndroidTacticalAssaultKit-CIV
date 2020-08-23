
#include <cstring>
#include <cinttypes>
#include "port/StringBuilder.h"

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace {
	
}

StringBuilder::StringBuilder() NOTHROWS
: len_(0),
  cap_(SMALL_STRING_CAP_MAX),
  err_(TE_Ok) {
	buf_.inl[0] = 0;
    buf_.dyn = nullptr;
}

StringBuilder::~StringBuilder() NOTHROWS {
	if (cap_ > SMALL_STRING_CAP_MAX)
		delete[] buf_.dyn;
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
	return cap_ > SMALL_STRING_CAP_MAX ? buf_.dyn : buf_.inl;
}

size_t StringBuilder::length() const NOTHROWS {
	return this->len_;
}

size_t StringBuilder::capacity() const NOTHROWS {
	return this->cap_;
}

TAKErr StringBuilder::error() const NOTHROWS {
	return this->err_;
}

TAKErr StringBuilder::resize(size_t newLength) NOTHROWS {

	size_t newCap = newLength + 1;

	char *newBuf = buf_.inl;
	if (newCap > SMALL_STRING_CAP_MAX)
		newBuf = new(std::nothrow) char[newCap];

	if (!newBuf)
		return TE_OutOfMemory;

	size_t copyLen = newLength < len_ ? newLength : len_;
	char *oldBuf = getBuf();

	memcpy(newBuf, oldBuf, copyLen);
	newBuf[copyLen] = 0;

	if (oldBuf == buf_.dyn)
		delete[] oldBuf;

	if (newBuf != buf_.inl)
		buf_.dyn = newBuf;

	len_ = copyLen;
	cap_ = newCap;

	return TE_Ok;
}

TAKErr StringBuilder::ensure(size_t length) NOTHROWS {
	if (this->len_ < length)
		return resize(length);
	return TE_Ok;
}

TAKErr StringBuilder::append(const char *chars, size_t count) NOTHROWS {

	TAKErr code(TE_Ok);

	size_t newLen = this->len_ + count;
	size_t maxLen = this->cap_ - 1;

	if (newLen >= maxLen) {
		size_t growLen = this->len_ + this->len_ / 2;
		if (growLen < newLen)
			growLen = newLen;
		code = resize(growLen);
	}

	if (code == TE_Ok) {
		char *buf = getBuf();
		memcpy(buf + this->len_, chars, count);
		buf[newLen] = 0;
		this->len_ = newLen;
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
	code = ensure(this->len_ + required); \
	if (code == TE_Ok) { \
		snprintf(this->getBuf() + this->len_, required + 1, n, v); \
		this->len_ += required; \
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
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned char v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(short v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned short v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(int v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned int v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned long v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long long v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(unsigned long long v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(long double v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(double v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(float v) NOTHROWS {
	this->err_ = this->append(v);
	return *this;
}

StringBuilder &StringBuilder::operator<<(const char *str) NOTHROWS {
	this->err_ = append(str, strlen(str));
	return *this;
}

