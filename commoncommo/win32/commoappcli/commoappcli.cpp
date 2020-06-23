// commocli.cpp : main project file.

#include "stdafx.h"


#include <msclr/marshal.h>
#include "commoappcli.h"
#include <time.h>
#include <stdint.h>

#include "libxml/tree.h"
#include "libxml/xmlerror.h"
#include "openssl/ssl.h"
#include "openssl/crypto.h"
#include "curl/curl.h"

using namespace System;



namespace {
    const char UID_LABEL[] = "UID";

    const char *LEVEL_STRINGS[] = {
        "VERBOSE",
        "DEBUG",
        "WARNING",
        "INFO",
        "ERROR"
    };


    std::string getTimeString() {
        const size_t bufSize = 8;
        char buf[bufSize];
        time_t t = time(NULL);
        struct tm timeinfo;
#ifdef WIN32
        localtime_s(&timeinfo, &t);
#else
        localtime_r(&t, &timeinfo);
#endif
        if (strftime(buf, bufSize, "%H%M%S", &timeinfo) == 0)
            buf[0] = '\0';
        return std::string(buf);
    }

    array<Byte> ^readFile(String ^fileName)
    {
        msclr::interop::marshal_context mctx;
        const char *buf1 = mctx.marshal_as<const char *>(fileName);

        FILE *f = fopen(buf1, "rb");
        if (!f)
            return nullptr;
        fseek(f, 0, SEEK_END);
        long ln = ftell(f);
        if (ln < 0) {
            fclose(f);
            return nullptr;
        }
        size_t n = (size_t)ln;
        fseek(f, 0, SEEK_SET);
        array<Byte> ^buf = gcnew array<Byte>(n);
        pin_ptr<Byte> pbuf = &buf[0];

        n = fread(pbuf, 1, n, f);
        fclose(f);
        return buf;
    }

}

test::CommoAppCLI::CommoAppCLI() :
myUID("Commo-uninit"), myCallsign("CommoCS-uninit"), nextCmdLineTime(0),
ifaceDescs(gcnew Collections::Generic::Dictionary<NetInterface^, String^>()), contactList(gcnew Collections::Generic::SortedSet<String^>()),
contactMutex(gcnew Threading::Mutex()), loggerMutex(gcnew Threading::Mutex()), commo(nullptr),
logFile(NULL), messageLog(NULL), 
logMutex(gcnew Threading::Mutex()), msgMutex(gcnew Threading::Mutex())
{
    // Setup log first
    logFile = fopen("commo-log.txt", "w");
    if (!logFile)
        throw 1;
    messageLog = fopen("commo-xml.txt", "w");
    if (!messageLog)
        throw 2;

}

test::CommoAppCLI::~CommoAppCLI()
{
    if (commo != nullptr) {
        commo->Shutdown();
        delete commo;
    }
    fclose(messageLog);
    fclose(logFile);
}

void test::CommoAppCLI::run(array<System::String ^> ^args)
{
    bool quit = false;
    int curArg = 2;
    if (args->Length < 2 || (args->Length % 2) != 0) {
        Console::WriteLine("Usage: commoappcli <uid> <callsign> { <wait-seconds> <interface-addr> } ... ");
        return;
    }
    myUID = args[0];
    myCallsign = args[1];

    commo = gcnew Commo(this, myUID, myCallsign);
    commo->AddCoTMessageListener(this);
    commo->AddContactPresenceListener(this);
    commo->AddInterfaceStatusListener(this);
    commo->SetupMissionPackageIO(this);
    commo->SetMissionPackageLocalPort(8080);
    commo->SetEnableAddressReuse(false);

    if (args->Length > 2) {
        int n = Convert::ToInt32(args[2]);
        Console::WriteLine("sleeping for {0}", n);
        nextCmdLineTime = time(NULL) + n;
    }

    while (!quit) {
#ifdef WIN32
        Sleep(1000);
#else
        sleep(1);
#endif
        sendSA();

        if (curArg < args->Length && time(NULL) > nextCmdLineTime) {
            String ^desc = args[curArg + 1];
            String ^s = gcnew String(desc);
            Console::WriteLine("processing {0}", s);
            if (s == "quit") {
                Console::WriteLine("QUIT!", s);
                break;
            } else if (s->StartsWith("cloudc:")) {
                // Send a file!
                s = s->Substring(7);
                array<String ^> ^params = s->Split(':');

                if (params->Length != 7) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: need 7 args for cloudc!\n");
                } else {
                    CloudClient ^cloudc;
                    if (commo->CreateCloudClient(cloudc, this, CloudIOProtocol::Https, params[0], Convert::ToInt32(params[1]), params[2], params[3], params[4], nullptr, nullptr) == CommoResult::CommoSuccess) {
                        cloudclients.Add(cloudc);
                        fprintf(logFile, "Cloud create success\n");
                    } else {
                        fprintf(logFile, "Cloud create failed\n");
                    }

                }
            } else if (s->StartsWith("cloudl:")) {
                // Send a file!
                s = s->Substring(7);
                array<String ^> ^params = s->Split(':');
                int colon = s->IndexOf(":");

                if (params->Length != 2) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: need 2 args for cloudl!\n");
                } else {
                    CloudClient ^cloudc;
                    int n = Convert::ToInt32(params[0]);
                    cloudc = cloudclients[n];
                    
                    if (cloudc->listCollectionInit(n, params[1]) == CommoResult::CommoSuccess) {
                        cloudc->startOperation(n);
                        fprintf(logFile, "Cloud list success\n");
                    } else {
                        fprintf(logFile, "Cloud create failed\n");
                    }

                }
            } else if (s->StartsWith("filesend:")) {
                // Send a file!
                s = s->Substring(9);
                Console::WriteLine("Sending file {0}", s);
                int colon = s->IndexOf(":");
                String ^fn = s->Substring(0, colon);
                String ^uid = s->Substring(colon + 1);
                System::Collections::Generic::List<String ^> ^clist = gcnew Collections::Generic::List<String ^>();
                clist->Add(uid);
                int id;
                if (commo->SendMissionPackageInit(id, clist, fn, fn, fn) != CommoResult::CommoSuccess)
                    fprintf(logFile, "FAILED TO SEND MP\n");
                else {
                    fprintf(logFile, "MP send queued successfully\n");
                    commo->StartMissionPackageSend(id);
                }


            }
            else if (s->StartsWith("sendcot:")) {
                // Send a cot message
                s = s->Substring(8);
                Console::WriteLine("Sending cot to {0}", s);
                System::Collections::Generic::List<String ^> ^clist = gcnew Collections::Generic::List<String ^>();
                clist->Add(s);
                String ^cot = gcnew String(buildSAMessage().c_str());
                if (commo->SendCoT(clist, cot) != CommoResult::CommoSuccess)
                    fprintf(logFile, "FAILED TO SEND COT\n");
                else
                    fprintf(logFile, "COT sent successfully\n");
            }
            else if (s->StartsWith("udpunicast:")) {
                s = s->Substring(11);
                Console::WriteLine("Add udp unicast for {0}", s);
                int loc = s->IndexOf(':');
                String ^host = s->Substring(0, loc);
                s = s->Substring(loc + 1);
                int portNum = Convert::ToInt32(s);
                array<CoTMessageType> ^allTypes = {
                    CoTMessageType::SituationalAwareness
                };
                if (commo->AddBroadcastInterface(allTypes, host, portNum) != nullptr) {
                    fprintf(logFile, "Tcp iface added on port %d\n", portNum);
                }
                else {
                    fprintf(logFile, "Tcp iface failed to add on port %d\n", portNum);

                }
            }
            else if (s->StartsWith("tcpin:")) {
                s = s->Substring(6);
                Console::WriteLine("Add tcp in for port {0}", s);
                int portNum = Convert::ToInt32(s);
                if (commo->AddTcpInboundInterface(portNum) != nullptr) {
                    fprintf(logFile, "Tcp iface added on port %d\n", portNum);
                }
                else {
                    fprintf(logFile, "Tcp iface failed to add on port %d\n", portNum);

                }
            }
            else if (s->IndexOf("stream:") == 1 || s->IndexOf("stream:") == 0) {
                Console::WriteLine("Connecting to stream {0}", s);
                array<String ^> ^params = s->Split(':');
                array<Byte> ^caCert = nullptr;
                array<Byte> ^cert = nullptr;
                String ^certPw = nullptr;
                String ^user = nullptr;
                String ^userPw = nullptr;
                StreamingNetInterface ^iface = nullptr;
                int portNum = 0;
                int hostIdx = 1;
                if (params->Length < 3) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: need at least 2 args for streams!\n");
                    goto streamerr;
                }
                if (params[0]->Equals("sstream")) {
                    if (params->Length != 6 && params->Length != 8) {
                        fprintf(logFile, "ERROR IN COMMAND LINE: need 5 or 7 args for sstream. treating as plain stream\n");
                        goto streamerr;
                    } else {
                        hostIdx = 4;
                        cert = readFile(params[1]);
                        caCert = readFile(params[2]);
                        if (!cert || !caCert) {
                            msclr::interop::marshal_context mctx;
                            const char *buf1 = mctx.marshal_as<const char *>(params[1]);
                            const char *buf2 = mctx.marshal_as<const char *>(params[2]);

                            fprintf(logFile, "ERROR IN COMMAND LINE: cert %s or ca cert file %s not readable\n", buf1, buf2);
                            goto streamerr;
                        } else {
                            certPw = params[3];
                            if (params->Length == 8) {
                                user = params[4];
                                userPw = params[5];
                                hostIdx = 6;
                            }
                        }
                    }
                }

                portNum = Convert::ToInt32(params[hostIdx + 1]);

                array<CoTMessageType> ^allTypes = {
                    CoTMessageType::Chat,
                    CoTMessageType::SituationalAwareness
                };

                CommoResult errCode;
                iface = commo->AddStreamingInterface(
                    params[hostIdx], portNum, allTypes,
                    cert,
                    caCert, certPw, certPw, user, userPw, errCode);
                if (!iface) {
                streamerr:
                    msclr::interop::marshal_context mctx;
                    const char *buf1 = mctx.marshal_as<const char *>(desc);
                    fprintf(logFile, "Failed to add interface %s\n", buf1);
                    Console::WriteLine("Stream failed");
                }
                else {
                    ifaceDescs[iface] = desc;
                    Console::WriteLine("Added stream ok");
                }
            } else {
                Console::WriteLine("catch all");
                bool mcastIface = false;
                if (s->IndexOf("mcast:") == 0) {
                    s = s->Substring(6);
                    mcastIface = true;
                }

                int loc = s->IndexOf(':');
                String ^port = s->Substring(loc + 1);

                String ^addrString = s->Substring(0, loc);

                String ^mcast = "239.2.3.1";
                int portNum = Convert::ToInt32(port);
                PhysicalNetInterface ^iface;
                PhysicalNetInterface ^iface2 = nullptr;
                if (mcastIface) {
                    CoTMessageType t = CoTMessageType::SituationalAwareness;
                    array<CoTMessageType> ^tarr = gcnew array<CoTMessageType>(1);
                    tarr[0] = t;
                    Console::WriteLine("Adding mcast iface {0}", s);
                    iface = commo->AddBroadcastInterface(addrString, tarr, mcast, portNum);
                } else {
#if 1
                    array<String ^> ^mcastarr = gcnew array<String ^>(1);
                    mcastarr[0] = mcast;
                    Console::WriteLine("Adding inbound iface {0}", s);
                    iface = commo->AddInboundInterface(addrString, portNum, mcastarr, false);
                    mcastarr[0] = "224.10.10.1";
                    if (iface)
                        iface2 = commo->AddInboundInterface(addrString, 17012, mcastarr, false);
                    else
                        Console::WriteLine("add of iface failed");
#else
                    array<String ^> ^mcastarr = gcnew array<String ^>(0);
                    Console::WriteLine("Adding inbound iface {0}", s);
                    iface = commo->AddInboundInterface(addr, portNum, mcastarr);
#endif

                }
                if (!iface) {
                    msclr::interop::marshal_context mctx;
                    const char *buf1 = mctx.marshal_as<const char *>(desc);
                    fprintf(logFile, "Failed to add interface %s\n", buf1);
                } else {
                    ifaceDescs[iface] = desc;
                    if (iface2) {
                        desc += " CHAT";
                        ifaceDescs[iface2] = desc;
                    }
                }
            }
            Console::WriteLine("Next args!");
            fflush(logFile);
            curArg += 2;
            if (curArg < args->Length)
                nextCmdLineTime = time(NULL) + Convert::ToInt32(args[curArg]);
        } else if (curArg == args->Length) {
            Console::WriteLine("Done processing args");
            curArg++;
        }

    }

}

void test::CommoAppCLI::ContactAdded(String ^const c)
{
    contactMutex->WaitOne();
    contactList->Add(c);
    String ^s = gcnew String(c);
    s->Insert(0, "Contact Added: ");
    Log(ICommoLogger::Level::LevelInfo, s);
    contactMutex->ReleaseMutex();
}

void test::CommoAppCLI::ContactRemoved(String ^const c)
{
    contactMutex->WaitOne();
    String ^s = gcnew String(c);
    if (contactList->Contains(c)) {
        contactList->Remove(c);
    }
    s->Insert(0, "Contact Removed: ");
    Log(ICommoLogger::Level::LevelInfo, s);
    contactMutex->ReleaseMutex();
}

void test::CommoAppCLI::Log(ICommoLogger::Level level, String ^message)
{
    std::string s(LEVEL_STRINGS[Convert::ToInt32(level)]);
    std::string time(getTimeString());
    fprintf(logFile, "[%s] %s: %s\n", s.c_str(), time.c_str(), message);
    fflush(logFile);
}

void test::CommoAppCLI::CotMessageReceived(String ^cotMessage, String^ endpointId)
{
    msclr::interop::marshal_context mctx;
    const char *msg = mctx.marshal_as<const char *>(cotMessage);
    std::string s(getTimeString());
    fprintf(messageLog, "%s: %s\n", s.c_str(), msg);
    fflush(messageLog);
    {
        s = msg;
        size_t pos = s.find("<remarks");
        if (pos == std::string::npos)
            return;
        pos = s.find(">", pos);
        if (pos == std::string::npos)
            return;
        pos++;
        size_t end = s.find("</remarks>", pos);
        if (end == std::string::npos)
            return;
        s = s.substr(pos, end - pos);
    }
}

void test::CommoAppCLI::InterfaceDown(NetInterface ^iface)
{
    String ^s = nullptr;
    if (!ifaceDescs->TryGetValue(iface, s)) {
        fprintf(logFile, "Interface Down: Unknown interface?!?\n");
    } else {
        msclr::interop::marshal_context mctx;
        const char *msg = mctx.marshal_as<const char *>(s);
        fprintf(logFile, "Interface Down: %s\n", msg);
    }
}

void test::CommoAppCLI::InterfaceUp(NetInterface ^iface)
{
    String ^s = nullptr;
    if (!ifaceDescs->TryGetValue(iface, s)) {
        fprintf(logFile, "Interface Up: Unknown interface?!?\n");
    } else {
        msclr::interop::marshal_context mctx;
        const char *msg = mctx.marshal_as<const char *>(s);
        fprintf(logFile, "Interface Up: %s\n", msg);
    }
}

void test::CommoAppCLI::InterfaceError(NetInterface ^iface, NetInterfaceErrorCode err)
{

}

void test::CommoAppCLI::cloudOperationUpdate(CloudIOUpdate ^update)
{
    fprintf(logFile, "CloudUpdate: %d %d %d (%s) %d of %d\n",
        update->operation,
        update->transferId, update->status,
        update->additionalInfo ? update->additionalInfo : "no info",
        (int)update->totalBytesTransferred, (int)update->totalBytesToTransfer);
    if (update->operation == CloudIOOperation::ListCollection && update->status == SimpleFileIOStatus::Success && update->entries) {
        fprintf(logFile, "CloudUpdate listing: %d\n", (int)update->entries->Length);
        for (size_t i = 0; i < update->entries->Length; ++i)
            fprintf(logFile, "    %s (%s) %d\n", update->entries[i]->path,
                update->entries[i]->type == CloudCollectionEntry::Type::Collection ? "folder" : "file",
                (int)update->entries[i]->size);
    }
    fflush(stdout);
}


MissionPackageTransferStatus test::CommoAppCLI::MissionPackageReceiveInit(
    System::String ^% destFile,
    System::String ^transferName, System::String ^sha256hash,
    System::Int64 size,
    System::String ^senderCallsign)
{
    static int mpTransferSerial = 0;

    String ^ss = gcnew String("mprx-");
    ss += mpTransferSerial.ToString();
    ss += "-";
    ss += destFile;

    destFile = ss;

    msclr::interop::marshal_context mctx;
    const char *transferNameBuf = mctx.marshal_as<const char *>(transferName);
    const char *senderBuf = mctx.marshal_as<const char *>(senderCallsign);
    const char *destFileBuf = mctx.marshal_as<const char *>(destFile);
    fprintf(logFile, "Receive of MP %s from %s requested - assigned output file %s\n",
            transferNameBuf, senderBuf, destFileBuf);

    mpTransferSerial++;
    return MissionPackageTransferStatus::TransferFinishedSuccess;
}

void test::CommoAppCLI::MissionPackageReceiveStatusUpdate(TAK::Commo::MissionPackageReceiveStatusUpdate ^update)
{
    msclr::interop::marshal_context mctx;
    const char *errBuf = update->errorDetail == nullptr ? "" : mctx.marshal_as<const char *>(update->errorDetail);
    const char *destFileBuf = mctx.marshal_as<const char *>(update->localFile);
    fprintf(logFile, "Receive of MP %s result ok? %d %s\n",
            destFileBuf, update->status == MissionPackageTransferStatus::TransferFinishedSuccess ? 1 : 0,
            errBuf);
}

void test::CommoAppCLI::MissionPackageSendStatusUpdate(TAK::Commo::MissionPackageSendStatusUpdate ^update)
{
    msclr::interop::marshal_context mctx;
    const char *recipientBuf = mctx.marshal_as<const char *>(update->recipient);
    const char *reasonBuf = update->additionalDetail == nullptr ? "" : mctx.marshal_as<const char *>(update->additionalDetail);
    fprintf(logFile, "Mission package %d sent to %s, success? %d, reason: %s\n",
            update->xferid,
            recipientBuf,
            update->status == MissionPackageTransferStatus::TransferFinishedSuccess ? 1 : 0,
            reasonBuf);
}

CoTPointData test::CommoAppCLI::GetCurrentPoint()
{
    CoTPointData p(36.5261810013514, -77.3862509255614, CoTPointData::NO_VALUE,
                   CoTPointData::NO_VALUE, CoTPointData::NO_VALUE);
    return p;
}

std::string test::CommoAppCLI::buildSAMessage()
{
    static char buf[20];
    static int speedSerial = 0;
    time_t now = time(NULL);
    struct tm t;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    const size_t timebufsize = 256;
    char timebuf[timebufsize];
    char timebuf2[timebufsize];
    strftime(timebuf, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);
    now += 15;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    strftime(timebuf2, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);

    msclr::interop::marshal_context mctx;
    const char *myUIDBuf = mctx.marshal_as<const char *>(myUID);
    const char *myCallsignBuf = mctx.marshal_as<const char *>(myCallsign);


    std::string saString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"";
    saString += myUIDBuf;
    saString += "\" type=\"a-f-G-U-C\" time=\"";
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\" how=\"h-e\">";
    saString += "<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" "
        "ce=\"9999999\" le=\"9999999\"/>"
        "<detail>        <contact phone=\"3152545187\" endpoint=\"10.233.154.103:4242:tcp\""
        " callsign=\"";
    saString += myCallsignBuf;
    saString += "\"/>"
        "<uid Droid=\"JDOG\"/>"
        "<__group name=\"Cyan\" role=\"Team Member\"/>"
        "<status battery=\"100\"/>"
        "<track speed=\"";
    sprintf(buf, "%d", speedSerial);
    saString += buf;
    speedSerial++;
    saString += "\" course=\"56.23885995781046\"/>"
        "<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>"
        "</detail>"
        "</event>";

    return saString;
}

void test::CommoAppCLI::sendSA()
{
    std::string saString = buildSAMessage();
    commo->BroadcastCoT(gcnew String(saString.c_str()));

}


namespace {
    HANDLE *locks;
    void thread_lock(int mode, int type, const char *file, int line)
    {
        if (mode & CRYPTO_LOCK)
            WaitForSingleObject(locks[type], INFINITE);
        else
            ReleaseMutex(locks[type]);
    }
    void sslInitThreads() {
        int n = CRYPTO_num_locks();
        locks = new HANDLE[n];
        for (int i = 0; i < n; ++i)
            locks[i] = CreateMutex(NULL, FALSE, NULL);
        CRYPTO_set_locking_callback(thread_lock);
    }
    void sslDeallocThreads() {
        CRYPTO_set_locking_callback(NULL);
        int n = CRYPTO_num_locks();
        for (int i = 0; i < n; ++i) {
            CloseHandle(locks[i]);
        }
        delete[] locks;
    }
}

int main(array<System::String ^> ^args)
{
#ifdef WIN32
    WSAData wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);
#endif

    // Initialize libxml2
    xmlInitParser();

    SSL_library_init();
    SSL_load_error_strings();
    OpenSSL_add_ssl_algorithms();
    sslInitThreads();

    // Initialize curl
    curl_global_init(CURL_GLOBAL_NOTHING);

    // Create test
    try {
        test::CommoAppCLI app;

        app.run(args);
    } catch (int &x) {
        fprintf(stderr, "Failed to init %d\n", x);
    }
    sslDeallocThreads();

    // Tear down libxml2
    xmlCleanupParser();

#ifdef WIN32
    WSACleanup();
#endif
    return 0;
}




