#include "missionpackage_cli.h"
#include "missionpackage.h"

#include <msclr/marshal.h>

#ifndef MISSIONPACKAGEIMPL_H
#define MISSIONPACKAGEIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {
            class MissionPackageIOImpl : public atakmap::commoncommo::MissionPackageIO
            {
            public:
                MissionPackageIOImpl(TAK::Commo::IMissionPackageIO ^mpio);
                ~MissionPackageIOImpl();

                virtual atakmap::commoncommo::MissionPackageTransferStatus missionPackageReceiveInit(
                    char *destFile, size_t destFileSize,
                    const char *transferName, const char *sha256hash,
                    uint64_t expectedByteSize,
                    const char *senderCallsign);

                virtual void missionPackageReceiveStatusUpdate(const atakmap::commoncommo::MissionPackageReceiveStatusUpdate *update);
                virtual void missionPackageSendStatusUpdate(const atakmap::commoncommo::MissionPackageSendStatusUpdate *update);
                virtual atakmap::commoncommo::CoTPointData getCurrentPoint();
                virtual void createUUID(char *uuidString);


            private:
                gcroot<TAK::Commo::IMissionPackageIO ^> mpioCLI;
            };
        }
    }
}

#endif
