#include "versionimpl.h"
#include "commoversion.h"

#define CV_STR_EX(a) #a
#define CV_STR(a) CV_STR_EX(a)

#ifndef COMMO_BUILD_STAMP
 #define COMMO_BUILD_STAMP "undefined"
#endif


const char *atakmap::commoncommo::impl::getVersionString()
{
    return CV_STR(COMMO_MAJOR) "."
           CV_STR(COMMO_MINOR) "."
           CV_STR(COMMO_PATCH) "+"
           COMMO_BUILD_STAMP;
}

namespace atakmap {
namespace commoncommo {
namespace impl {

// Version id string that is more unique for symbolic identification
// of a built binary.
const char *versionIdString = "CommoVersionId-"
           CV_STR(COMMO_MAJOR) "."
           CV_STR(COMMO_MINOR) "."
           CV_STR(COMMO_PATCH) "+"
           COMMO_BUILD_STAMP;

}
}
}

