#include "commotime.h"
#include "platform.h"

using namespace atakmap::commoncommo::impl;


const CommoTime CommoTime::ZERO_TIME(0, 0);


CommoTime::CommoTime(unsigned int seconds,
        unsigned int milliseconds) : seconds(seconds), millis(milliseconds)
{
    fixTime();
}

CommoTime::CommoTime(float fracSeconds)
{
    setFromFloat(fracSeconds);
}

CommoTime::CommoTime(const CommoTime& t) : seconds(t.seconds), millis(t.millis)
{
}

CommoTime::~CommoTime()
{
}

void CommoTime::operator =(const CommoTime& t)
{
    seconds = t.seconds;
    millis = t.millis;
}

void CommoTime::operator =(const float& fracSeconds)
{
    setFromFloat(fracSeconds);
}

CommoTime CommoTime::operator +(
        const CommoTime& t) const
{
    return CommoTime(seconds + t.seconds, millis + t.millis);
}

CommoTime CommoTime::operator +(
        const float& fracSeconds)
{
    CommoTime t(fracSeconds);
    t += *this;
    return t;
}

CommoTime& CommoTime::operator +=(
        const CommoTime& t)
{
    seconds += t.seconds;
    millis += t.millis;
    fixTime();
    return *this;
}

CommoTime& CommoTime::operator +=(
        const float& fracSeconds)
{
    CommoTime t(fracSeconds);
    *this += t;
    return *this;
}

CommoTime CommoTime::operator -(
        const CommoTime& t) const
{
    unsigned s, m;
    subImpl(*this, t, &s, &m);
    return CommoTime(s, m);
}

CommoTime& CommoTime::operator -=(
        const CommoTime& t)
{
    subImpl(*this, t, &seconds, &millis);
    return *this;
}

float atakmap::commoncommo::impl::CommoTime::minus(const CommoTime& t) const
{
    unsigned s, m;
    float sign;
    if (*this >= t) {
        subImpl(*this, t, &s, &m);
        sign = 1.0f;
    } else {
        subImpl(t, *this, &s, &m);
        sign = -1.0f;
    }
    return sign * toFracSecs(s, m);
}


bool CommoTime::operator ==(
        const CommoTime& t) const
{
    return seconds == t.seconds && millis == t.millis;
}

bool CommoTime::operator !=(
        const CommoTime& t) const
{
    return seconds != t.seconds || millis != t.millis;
}

bool CommoTime::operator >(const CommoTime& t) const
{
    return seconds > t.seconds || (seconds == t.seconds && millis > t.millis);
}

bool CommoTime::operator <(const CommoTime& t) const
{
    return seconds < t.seconds || (seconds == t.seconds && millis < t.millis);
}

bool CommoTime::operator >=(
        const CommoTime& t) const
{
    return seconds > t.seconds || (seconds == t.seconds && millis >= t.millis);
}

bool CommoTime::operator <=(
        const CommoTime& t) const
{
    return seconds < t.seconds || (seconds == t.seconds && millis <= t.millis);
}

unsigned int CommoTime::getSeconds() const
{
    return seconds;
}

unsigned int CommoTime::getMillis() const
{
    return millis;
}

float CommoTime::getFractionalSeconds() const
{
    return toFracSecs(seconds, millis);
}

uint64_t CommoTime::getPosixMillis() const
{
    return (uint64_t)seconds * 1000 + (uint64_t)millis;
}

CommoTime CommoTime::now()
{
    unsigned s, ms;
    getCurrentTime(&s, &ms);
    return CommoTime(s, ms);
}

CommoTime CommoTime::fromPosixMillis(uint64_t posixMillis)
{
    unsigned s = (unsigned)(posixMillis / 1000);
    unsigned millis = (unsigned)(posixMillis % 1000);
    return CommoTime(s, millis);
}

CommoTime CommoTime::fromUTCString(const std::string &timeString) 
                                   COMMO_THROW (std::invalid_argument)
{
    unsigned int s, m;
    getTimeFromString(&s, &m, timeString.c_str());

    return CommoTime(s, m);
}

std::string CommoTime::toUTCString() const
{
    return getTimeString(seconds, millis);
}


void CommoTime::fixTime()
{
    if (millis >= 1000) {
        seconds += millis / 1000;
        millis %= 1000;
    }
}

void CommoTime::setFromFloat(const float &fracSecs)
{
    seconds = (unsigned)fracSecs;
    millis = (unsigned)((fracSecs - seconds) * 1000);
}

/*
 * NOTE: s and ms can point to members of a or b!
 */
void CommoTime::subImpl(const CommoTime &a, const CommoTime &b, unsigned *s, unsigned *ms)
{
    if (a < b) {
      *s = *ms = 0;
    } else if (a.millis < b.millis) {
      *s = a.seconds - b.seconds - 1;
      *ms = 1000 + a.millis - b.millis;
    } else {
      *s = a.seconds - b.seconds;
      *ms = a.millis - b.millis;
    }
}

float CommoTime::toFracSecs(const unsigned &s, const unsigned &m)
{
    return s + (m / 1000.0f);
}
