#include "missionpackage_cx.h"
#include "missionpackage.h"

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
                    const char *senderCallsign);

                virtual void missionPackageReceiveComplete(const char *destFile,
                    atakmap::commoncommo::MissionPackageTransferStatus status,
                    const char *error);

                virtual void missionPackageSendStatus(const atakmap::commoncommo::MissionPackageTransferStatusUpdate *update);
                virtual atakmap::commoncommo::CoTPointData getCurrentPoint();
                virtual void createUUID(char *uuidString);


            private:
                TAK::Commo::IMissionPackageIO ^ _mpioCLI;
            };
        }
    }
}
