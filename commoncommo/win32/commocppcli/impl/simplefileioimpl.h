#include "simplefileio_cli.h"
#include "simplefileio.h"

#include <msclr/marshal.h>

#ifndef SIMPLEFILEIOIMPL_H
#define SIMPLEFILEIOIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {
            class SimpleFileIOImpl : public atakmap::commoncommo::SimpleFileIO
            {
            public:
                SimpleFileIOImpl(TAK::Commo::ISimpleFileIO ^fio);
                ~SimpleFileIOImpl();

                virtual void fileTransferUpdate(const atakmap::commoncommo::SimpleFileIOUpdate *update);
                static SimpleFileIOStatus nativeToCLI(atakmap::commoncommo::SimpleFileIOStatus status);

            private:
                gcroot<TAK::Commo::ISimpleFileIO ^> fioCLI;
            };
        }
    }
}

#endif
