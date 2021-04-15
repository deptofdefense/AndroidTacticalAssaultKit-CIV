#ifndef COMMOLOGGER_H_
#define COMMOLOGGER_H_

#include <cstdint>
#include <cstddef>

#include "commoutils.h"

namespace atakmap {
namespace commoncommo {


class COMMONCOMMO_API CommoLogger {
public:
    typedef enum {
        LEVEL_VERBOSE,
        LEVEL_DEBUG,
        LEVEL_WARNING,
        LEVEL_INFO,
        LEVEL_ERROR,
    } Level;

    typedef enum
    {
        // A general case / catch all type of log message.
        // Will not have a detail (detail will be NULL)
        TYPE_GENERAL,
        // Log message related to network message parsing.
        // Detail, if non-NULL, will be a ParsingDetail
        TYPE_PARSING,
        // Log message related to network interfaces.
        // Detail, if non-NULL, will be a NetworkDetail
        TYPE_NETWORK,
    } Type;

    struct COMMONCOMMO_API ParsingDetail
    {
        // Binary message data received. Never NULL, but may be 0 length.
        const uint8_t* const messageData;
        // Length of message data. May be 0.
        const size_t messageLen;
        // Human-readable detail of why the parse failed
        const char* const errorDetailString;
        // Identifier of Network Interface where message was received
        const char* const rxIfaceEndpointId;
    };

    // Detail on a TYPE_NETWORK log message. Currently
    // only used for non-tcp inbound network interfaces
    struct COMMONCOMMO_API NetworkDetail
    {
        // Port number of the (non-tcp) inbound interface
        // issuing the log message
        const int port;
    };

    CommoLogger() {};

    // MUST be able to handle multiple threads calling at once.
    virtual void log(Level level, Type type, const char* message, void* detail) = 0;


protected:
    virtual ~CommoLogger() {};

private:
    COMMO_DISALLOW_COPY(CommoLogger);
};


}
}


#endif /* COMMOLOGGER_H_ */
