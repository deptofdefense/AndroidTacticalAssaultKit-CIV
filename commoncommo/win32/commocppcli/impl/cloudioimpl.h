#ifndef CLOUDIOIMPL_H_
#define CLOUDIOIMPL_H_

#include "cloudio.h"
#include "cloudio_cli.h"
#include <msclr/marshal.h>

namespace TAK {
    namespace Commo {
        namespace impl {

            class CloudIOImpl : public atakmap::commoncommo::CloudIO {
            public:
                CloudIOImpl(ICloudIO ^io);
                ~CloudIOImpl();

                virtual void cloudOperationUpdate(const atakmap::commoncommo::CloudIOUpdate *update);

                static CloudIOOperation nativeToCLI(atakmap::commoncommo::CloudIOOperation op);
                static CloudCollectionEntry::Type nativeToCLI(atakmap::commoncommo::CloudCollectionEntry::Type t);

            private:
                gcroot<ICloudIO ^> ioCLI;
            };
        }
    }
}



#endif
