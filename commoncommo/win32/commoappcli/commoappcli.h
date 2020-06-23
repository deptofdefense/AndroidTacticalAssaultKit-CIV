#ifndef COMMOAPPCLI_H
#define COMMOAPPCLI_H

using namespace TAK::Commo;
using namespace System;

#include <set>
#include <map>
#include <string>
#include <deque>
#include <stdio.h>



namespace TAK {
    namespace Commo {
        namespace test {


            public ref class CommoAppCLI : public ICoTMessageListener, public IContactPresenceListener,
                public ICommoLogger, public IInterfaceStatusListener,
                public IMissionPackageIO, public ICloudIO
            {
            public:
                CommoAppCLI();
                virtual ~CommoAppCLI();

                void run(array<System::String ^> ^args);

                virtual void CotMessageReceived(System::String ^cotMsg, System::String^ endpointId);
                virtual void ContactAdded(System::String ^ const c);
                virtual void ContactRemoved(System::String ^const c);

                virtual void Log(ICommoLogger::Level level, System::String ^message);

                virtual void InterfaceDown(NetInterface ^iface);
                virtual void InterfaceUp(NetInterface ^iface);
                virtual void InterfaceError(NetInterface ^iface, NetInterfaceErrorCode err);


                virtual MissionPackageTransferStatus MissionPackageReceiveInit(System::String ^% destFile,
                                                                                System::String ^transferName, System::String ^sha256hash,
                                                                                System::Int64 totalSize,
                                                                                System::String ^senderCallsign);
                virtual void MissionPackageReceiveStatusUpdate(MissionPackageReceiveStatusUpdate ^update);
                virtual void MissionPackageSendStatusUpdate( MissionPackageSendStatusUpdate ^update);
                virtual CoTPointData GetCurrentPoint();

                virtual void cloudOperationUpdate(CloudIOUpdate ^update);


            private:
                String ^myUID;
                String ^ myCallsign;
                time_t nextCmdLineTime;

                Collections::Generic::Dictionary<NetInterface ^, String ^> ^ifaceDescs;
                Collections::Generic::SortedSet<String ^> ^contactList;
                Threading::Mutex ^contactMutex;

                Threading::Mutex ^loggerMutex;
                Commo ^commo;
                FILE *logFile;
                FILE *messageLog;

                System::Collections::Generic::List<CloudClient ^> cloudclients;

                Threading::Mutex ^logMutex;
                Threading::Mutex ^msgMutex;

                std::string buildSAMessage();
                void sendSA();

            };


        }
    }
}


#endif
