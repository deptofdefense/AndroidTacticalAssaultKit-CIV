#ifndef COMMOLOGGER_H_
#define COMMOLOGGER_H_

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

    CommoLogger() {};

    // MUST be able to handle multiple threads calling at once.
    virtual void log(Level level, const char *message) = 0;


protected:
    virtual ~CommoLogger() {};

private:
    COMMO_DISALLOW_COPY(CommoLogger);
};


}
}


#endif /* COMMOLOGGER_H_ */
