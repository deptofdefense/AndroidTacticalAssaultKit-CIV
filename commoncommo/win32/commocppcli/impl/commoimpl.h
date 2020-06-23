#ifndef COMMOIMPL_H
#define COMMOIMPL_H

#include "commoresult.h"
#include "commoresult_cli.h"

namespace TAK {
    namespace Commo {
        namespace impl {
            CommoResult nativeToCLI(atakmap::commoncommo::CommoResult res);
        }
    }
}

#endif