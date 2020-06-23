#ifndef IMPL_COMMOTIME_H_
#define IMPL_COMMOTIME_H_

#include <string>
#include <stdexcept>
#include "internalutils.h"

namespace atakmap {
namespace commoncommo {
namespace impl
{

/**
 * A class to represent time. The class has a seconds portion and a
 * milliseconds portion. So, for example, a time of 3.142 seconds would
 * be represented as 3 seconds and 142 milliseconds.
 *
 * Comparison operations and some mathematical operations are provided.
 *
 * While there is a means by which to create an instance of this class from
 * the current system time, there is no requirement nor guarantee that
 * CommoTime objects somehow represent time relative to any specific
 * time base. It is all relative to some origin (represented by "time 0") of
 * the user's chosing; care must be taken externally that objects are relative
 * to a consistent base when comparing or performing arithmatic.
 */
class CommoTime
{
public:
    /**
     * Create from a whole seconds and some optional number of
     * additional milliseconds.
     */
    CommoTime(unsigned int seconds, unsigned int milliseconds = 0);

    /**
     * Create from a fractional seconds.
     */
    CommoTime(float fracSeconds);

    /**
     * Copy constructor
     */
    CommoTime(const CommoTime &t);
    ~CommoTime();

    /**
     * Simple assignment
     */
    void operator=(const CommoTime &t);
    /**
     * Sets this CommoTime to some number of fractional seconds.
     */
    void operator=(const float &fracSeconds);

    /**
     * Adds two CommoTime objects, returning the result as a new object.
     */
    CommoTime operator+(const CommoTime &t) const;
    /**
     * Adds some number of fractional seconds to this CommoTime, returning
     * the result in a new object
     */
    CommoTime operator+(const float &fracSeconds);
    /**
     * Adds a CommoTime to this one.
     */
    CommoTime &operator+=(const CommoTime &t);
    /**
     * Adds some number of fractional seconds to this CommoTime
     */
    CommoTime &operator+=(const float &fracSeconds);

    /**
     * Subtracts two CommoTime's, returning the result in a new CommoTime.
     * If the argument is the greater of the two, the result will be equal
     * to ZERO_TIME.
     */
    CommoTime operator-(const CommoTime &t) const;
    /**
     * Subtracts a CommoTime from this object.
     * If the argument is the greater of the two, this CommoTime will be set
     * to ZERO_TIME.
     */
    CommoTime &operator-=(const CommoTime &t);
    /**
     * Subtracts two CommoTime's, returning the result as a floating point
     * number of fractional seconds.  The return is signed; negative if the
     * argument is greater than the present object.
     */
    float minus(const CommoTime &t) const;

    // Comparison operators.
    bool operator==(const CommoTime &t) const;
    bool operator!=(const CommoTime &t) const;
    bool operator>(const CommoTime &t) const;
    bool operator<(const CommoTime &t) const;
    bool operator>=(const CommoTime &t) const;
    bool operator<=(const CommoTime &t) const;

    /**
     * Gets the whole seconds portion of this CommoTime
     */
    unsigned int getSeconds() const;
    /**
     * Gets the fractional seconds of this CommoTime, as milliseconds
     */
    unsigned int getMillis() const;

    /**
     * Gets the fractional seconds of this CommoTime as a floating point value.
     */
    float getFractionalSeconds() const;
    
    /**
     * Returns this time as the number of milliseconds elapsed since Jan 1
     * 1970 UTC
     */
    uint64_t getPosixMillis() const;

    /**
     * Returns a string representing this CommoTime as a UTC time in the
     * format YYYY-MM-DD'T'hh:mm::ss.sss'Z'.  An implementation-defined
     * method is used to convert CommoTime's origin (ZERO_TIME) to
     * real world time in the UTC zone and then is offset by the
     * value of this CommoTime object.
     */
    std::string toUTCString() const;

    /**
     * Returns a CommoTime instance representing the current time of the
     * system.
     */
    static CommoTime now();
    
    /**
     * Returns a CommoTime instance created by parsing the UTC time string
     * given.  Throws invalid_argument if the string is not valid
     */
    static CommoTime fromUTCString(const std::string &utcTime) COMMO_THROW (std::invalid_argument);

    /**
     * Returns a CommoTime instance created from the number of milliseconds
     * given offset from the POSIX epoch (Jan 1 1970 UTC)
     */
    static CommoTime fromPosixMillis(uint64_t posixMillis);

    /**
     * A constant instance representing a time of 0 seconds.
     */
    static const CommoTime ZERO_TIME;
private:
    unsigned int seconds;
    unsigned int millis;

    void fixTime();
    void setFromFloat(const float &f);
    static void subImpl(const CommoTime &a, const CommoTime &b, unsigned *s, unsigned *ms);
    static float toFracSecs(const unsigned &s, const unsigned &m);
};


}
}
}

#endif /* IMPL_COMMOTIME_H_ */
