#include "commotest.h"
#include "netinterface.h"

#include "libxml/tree.h"
#include "libxml/xmlerror.h"
#include "openssl/ssl.h"
#include "openssl/crypto.h"
#include "curl/curl.h"
#include <vector>
#include <sstream>
#include <string.h>
#include <time.h>
#include <stdarg.h>
#include <stddef.h>
#include <inttypes.h>
#include <iomanip>
#ifndef WIN32
#include <unistd.h>
#include <signal.h>
#endif


using namespace atakmap::commoncommo;


namespace {
    const char UID_LABEL[] = "UID";
    const int INPUT_AREA_HEIGHT = 3;
    const int INPUT_AREA_WIDTH = 30;
    const int MIN_WIDTH = INPUT_AREA_WIDTH + 2 * sizeof(UID_LABEL);
    const int TERM_HEIGHT = 24;  // Common

    const char *LEVEL_STRINGS[] = {
            "VERBOSE",
            "DEBUG",
            "WARNING",
            "INFO",
            "ERROR"
    };
    const char* TYPE_STRINGS[] = {
            "GENERAL",
            "PARSING",
            "NETWORK"
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
    
    uint8_t *readFile(const char *fileName, size_t *len)
    {
        FILE *f = fopen(fileName, "rb");
        if (!f)
            return NULL;
        fseek(f, 0, SEEK_END);
        long ln = ftell(f);
        if (ln < 0) {
            fclose(f);
            return NULL;
        }
        size_t n = (size_t)ln;
        fseek(f, 0, SEEK_SET);
        uint8_t *buf = new uint8_t[n];
        n = fread(buf, 1, n, f);
        fclose(f);
        *len = n;
        return buf;
    }
    
    std::vector<std::string> split(const std::string &s, char delim) {
        std::stringstream ss(s);
        std::vector<std::string> ret;
        std::string item;
        while (std::getline(ss, item, delim)) {
            ret.push_back(item);
        }
        return ret;
    }
}


test::CommoTest::CommoTest() : 
        CoTMessageListener(), ContactPresenceListener(),
        myUID("Commo-uninit"), myCallsign("CommoCS-uninit"), 
        baseDir("."), nextCmdLineTime(0),
        sendFreqMillis(1000), millisToNextSend(1),
        ifaceDescs(), contactList(),
        contactMutex(), contactsMenuDirty(false), loggerMutex(), commo(NULL),
        logFile(NULL), messageLog(NULL), logQueue(),
        msgQueue(), logMutex(), msgMutex(), msgDirty(false),
        logDirty(false),
        contactH(0), contactW(0), textH(0), curW(0), curH(0)
{
}

test::CommoTest::~CommoTest()
{
    delete commo;
    if (messageLog)
        fclose(messageLog);
    if (logFile)
        fclose(logFile);
}

void test::CommoTest::do_help(const char *name)
{
    fprintf(stderr, "Full help listing:\n");
    fprintf(stderr, "Usage: %s <uid> <callsign> <output directory> { <wait-seconds> <command> } ... \n", name);
    fprintf(stderr, "  <uid> is UID used by client\n");
    fprintf(stderr, "  <callsign> is callsign used by client\n");
    fprintf(stderr, "  <output directory> is dir where log & files will be written\n    MUST BE WRITABLE\n");
    fprintf(stderr, "  <wait-seconds> is # of seconds to wait before next cmd; may be 0\n");
    fprintf(stderr, "  <command> is next action to take. See commands below.\n");
    
    fprintf(stderr, "  Commands:\n");
    fprintf(stderr,
"    quit\n"
"        Terminate cleanly and cleanup. If not given, will run\n"
"        until externally interrupted/killed\n"
"    cryptokey:<authkey>:<cryptokey>\n"
"        Turn on mesh network encryption/decryption using\n"
"        supplied keys.\n"
"        <authkey> and <cryptokey> must be precisely 32 ASCII characters\n"
"    chatsend:<uid>\n"
"        Send a chat message to the named uid\n"
"    remiface:<ifaceNum>\n"
"        Remove the \"ifaceNum'th\" previously added interface\n"
"        ifaceNum is specified as zero-based.\n"
"    safreq:<millisec>\n"
"        Override default 1000 millisecond SA send frequence\n"
"    filesend:<url>:<file>\n"
"    filerecv:<url>:<file>\n"
"        Do a 'simple' transfer of the named <file> to/from <url>\n"
"        <url> must be URL-encoded!\n"
"    sfilesend:<file>^<certfile>^<certpw^<url>\n"
"    sfilerecv:<file>^<certfile>^<certpw^<url>\n"
"    sfilesend:<file>^<certfile>^<certpw^<user>^<password>^<url>\n"
"    sfilerecv:<file>^<certfile>^<certpw^<user>^<password>^<url>\n"
"        Do a 'simple' transfer over TLS\n"
"        of the named <file> to/from <url>.\n"
"        Uses certificate in <certfile> which is encrypted with <certpw>.\n"
"        Longer forms use <user> and <password> to log in once connected.\n"
"    mpsend:<file>:<uid>\n"
"        Send <file> via MP transfer to <uid>\n"
"    smpsend:<file>:<ifaceNum>\n"
"        Upload <file> to TAK server that was configured as the\n"
"        ifaceNum'th interface (starting from 0)\n"
"    tcpin:<port>\n"
"        Add tcp inbound interface on <port>\n"
"    stream:<ip or host>:<port>\n"
"        Add streaming interface to TAK server at <ip or host> and <port>\n"
"    sstream:<certfile>:<truststore>:<cert pass>:<ip or host>:<port>\n"
"    sstream:<certfile>:<truststore>:<cert pass>:<user>:<pass>:<ip or host>:<port>\n"
"        Add streaming interface to TAK server at <ip or host> on <port>\n"
"        using TLS encryption.\n"
"        <cert pass> is used for both <certfile> and <truststore>.\n"
"        The longer form will send <user> and <pass> in an auth document\n"
"        after connection (can only be used on auth-enabled ports)\n"
"    mcast:<macaddr>:<port>\n"
"        Add an outbound broadcasting interface to 239.2.3.1 on interface\n"
"        with <macaddr> mac address. Uses destination UDP port <port>.\n"
"    inbound:<macaddr>:<port>\n"
"        Add an inbound interface on the interface specified by <macaddr>\n"
"        that listens for traffic on UDP port <port>. Subscribes to\n"
"        multicast group 239.2.3.1 on that interface.\n"
"        Also listens on UDP port 17012 of the same interface, subscribing\n"
"        to multicast group 224.10.10.1\n"
"    genin:<macaddr>:<port>\n"
"        Add an inbound interface on the interface specified by <macaddr>\n"
"        that listens for traffic on UDP port <port>.\n"
"        Traffic received is treated as generic traffic, not cot/XML\n"
"    cloudc:<host>:<port>:<path>:<user>:<pass>:<truststore>:<trustpass>\n"
"        Create a cloud client to talk to the specified server\n"
"        Use \"-\" for truststore and trustpass to accept any cert\n"
"    cloudf:<host>:<port>:<path>:<user>:<pass>:<truststore>:<trustpass>\n"
"        Create an FTP cloud client to talk to the specified server\n"
"        Use \"-\" for truststore and trustpass to accept any cert\n"
"    cloudd:<clientNum>\n"
"        delete the cloud client of the specified number\n"
"        (clients are numbered starting from 0\n"
"    cloudl:<clientNum>:<path>\n"
"        list contents using cloud client at sub-path\n"
"        (clients are numbered starting from 0\n"
"    cloudg:<clientNum>:<path>:<localpath>\n"
"        get file at sub-path using cloud client\n"
"        (clients are numbered starting from 0\n"
"    cloudp:<clientNum>:<path>:<localpath>\n"
"        put file at sub-path using cloud client\n"
"        (clients are numbered starting from 0\n"
"    cloudm:<clientNum>:<path>\n"
"        make new collection at path using cloud client\n"
"        (clients are numbered starting from 0\n"
"    cloudv:<clientNum>:<oldpath>:<newpath>\n"
"        rename resource at path using cloud client\n"
"        (clients are numbered starting from 0\n"
"    cloudt:<clientNum>\n"
"        test cloud client setup\n"
"        (clients are numbered starting from 0\n"
"\n"
"  <macaddr> for all interface specifications is to be given as a series\n"
"            of hex digits with no separators (colons, dashes)\n"
);

}

void test::CommoTest::run(int argc, char *argv[])
{
    bool quit = false;
    int curArg = 4;

    if (argc == 2 && strcmp(argv[1], "-h") == 0) {
        do_help(argv[0]);
        return;
    }

    if (argc < 4 || ((argc - 4) % 2) != 0) {
        fprintf(stderr, "Usage: %s <uid> <callsign> <output directory> { <wait-seconds> <command> } ... \n", argv[0]);
        fprintf(stderr, "       %s -h for full help listing\n", argv[0]);
        return;
    }
    myUID = argv[1];
    myCallsign = argv[2];
    baseDir = argv[3];

    // Setup log first
    std::string logFn = baseDir + "/commo-log.txt";
    std::string xmlFn = baseDir + "/commo-xml.txt";
    logFile = fopen(logFn.c_str(), "w");
    if (!logFile) {
        fprintf(stderr, "Could not open log file %s\n", logFn.c_str());
        return;
    }
    messageLog = fopen(xmlFn.c_str(), "w");
    if (!messageLog) {
        fprintf(stderr, "Could not open xml output file %s\n", xmlFn.c_str());
        return;
    }
    
	fprintf(logFile, "Running against commo version %s\n", Commo::getVersionString());

    ContactUID mycommouid((const uint8_t *)myUID.c_str(), myUID.length());
    commo = new Commo(this, &mycommouid, myCallsign.c_str());
    commo->addCoTMessageListener(this);
    commo->addGenericDataListener(this);
    commo->addCoTSendFailureListener(this);
    commo->addContactPresenceListener(this);
    commo->addInterfaceStatusListener(this);
    commo->setupMissionPackageIO(this);
    commo->setMissionPackageLocalPort(8080);
    
    uint8_t *httpsCertBuf = NULL;
    size_t httpsCertLen = commo->generateSelfSignedCert(&httpsCertBuf, "atak99123");
    if (httpsCertLen == 0) {
        fprintf(stderr, "Could not generate self signed cert- https will be disabled\n");
    } else {
        commo->setMissionPackageLocalHttpsParams(8443, httpsCertBuf, httpsCertLen, "atak99123");
    }
    commo->enableSimpleFileIO(this);
    //commo->setMulticastLoopbackEnabled(true);

    if (argc > 4) {
        int n = atoi(argv[4]);
        nextCmdLineTime = time(NULL) + n;
    }

    while (!quit) {
        int napTime = millisToNextSend;
        if (curArg < argc) {
            time_t now = time(NULL);
            if (now >= nextCmdLineTime) {
                // Don't sleep
                napTime = 0;
            } else {
                time_t dt = nextCmdLineTime - now;
                int dtMillis = dt * 1000;
                if (dtMillis < napTime)
                    napTime = dt;
            }
        }

#ifdef WIN32
        Sleep(napTime);
#else
        //sleep(1);
        if (napTime > 0) {
            usleep(napTime * 1000);
        }
#endif
        millisToNextSend -= napTime;
        if (millisToNextSend <= 0) {
            sendSA(true);
            millisToNextSend = sendFreqMillis;
        }
//        sendSA(false, "d0580174-4919-45a7-888f-db8f14e274f1");

#ifdef MARKER_BROADCAST
        if (curArg >= argc) {
            static int n = 0;
            static double pts[] = {
                40.1, -120.1, 
                41.1, -121.1, 
                42.1, -121.1, 
                43.1, -121.1, 
                44.1, -121.1, 
            };
            static int count = sizeof(pts) / sizeof(double);
            
            if (n < count) {
               sendMarker(pts[n], pts[n+1]);
               n += 2;
            }
            printf("DONE  %d %d\n", n, count);
            
            
        }
#endif
        if (curArg < argc && time(NULL) >= nextCmdLineTime) {
            std::string desc = argv[curArg + 1];
            std::string s(desc);
            if (s == "quit") {
                break;
            } else if (s.find("cloudc:") == 0 || s.find("cloudf:") == 0) {
                bool isFTP = s[5] == 'f';
                // create cloud client or FTP cloud client
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 7) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloud create\n");
                } else {
                    CloudClient *c;
                    int port = atoi(params[1].c_str());
                    size_t caLen = 0;
                    uint8_t *caCert = NULL;
                    bool hadErr = false;
                    if (params[5].compare("-") != 0) {
                        caCert = readFile(params[5].c_str(), &caLen);
                        if (!caCert) {
                             fprintf(logFile, "ERROR IN COMMAND LINE: ca cert file not readable\n");
                             hadErr = true;
                        }
                    }
                    if (!hadErr) {
                        if (commo->createCloudClient(&c, this, isFTP ? CLOUDIO_PROTO_FTP : CLOUDIO_PROTO_HTTPS,
                                 params[0].c_str(), port,
                                 params[2].c_str(),
                                 params[3].c_str(),
                                 params[4].c_str(),
                                 caCert,
                                 caLen,
                                 params[6].c_str()) != COMMO_SUCCESS) {
                            fprintf(logFile, "ERROR failed to create cloud client\n");
                        } else {
                            cloudClients.push_back(c);
                        }
                    }
                }                

            } else if (s.find("cloudd:") == 0) {
                // destroy cloud client
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 1) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudd\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        if (commo->destroyCloudClient(c) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to delete cloud client!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudd\n");
                    }
                }
            } else if (s.find("cloudl:") == 0) {
                // list cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 2) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudl\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->listCollectionInit(&id, params[1].c_str()) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to list cloud client!\n");
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudl\n");
                    }
                }
            } else if (s.find("cloudg:") == 0) {
                // get cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 3) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudg\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->getFileInit(&id, params[2].c_str(), params[1].c_str()) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to get cloud!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudg\n");
                    }
                }
            
            } else if (s.find("cloudp:") == 0) {
                // put cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 3) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudp\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->putFileInit(&id, params[1].c_str(), params[2].c_str()) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to put cloud!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudp\n");
                    }
                }
            
            } else if (s.find("cloudm:") == 0) {
                // make collection cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 2) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudm\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->createCollectionInit(&id, params[1].c_str()) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to make collection cloud!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudm\n");
                    }
            
                }
            } else if (s.find("cloudv:") == 0) {
                // move collection cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 3) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudv\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->moveResourceInit(&id, params[1].c_str(), params[2].c_str()) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to move cloud!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudv\n");
                    }
                }
            
            } else if (s.find("cloudt:") == 0) {
                // test cloud 
                s = s.substr(7);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 1) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cloudt\n");
                } else {
                    size_t idx = atoi(params[0].c_str());
                    if (idx < cloudClients.size()) {
                        CloudClient *c = cloudClients[idx];
                        int id;
                        if (c->testServerInit(&id) != COMMO_SUCCESS ||
                            c->startOperation(id) != COMMO_SUCCESS)
                            fprintf(logFile, "ERROR failed to test cloud!\n");
                            
                    } else {
                        fprintf(logFile, "ERROR IN COMMAND LINE: invalid cloud client index for cloudt\n");
                    }
                }
            
            } else if (s.find("epcontact:") == 0) {
                // add known contact
                s = s.substr(10);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 4) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for epcontact - needs epcontact:uid:callsign:ip:port%d\n", (int)params.size());
                } else {
                    int port = atoi(params[3].c_str());
                    ContactUID uid((const uint8_t *)params[0].c_str(), params[0].length());
                    CommoResult res = commo->configKnownEndpointContact(
                                                            &uid,
                                                            params[1].c_str(),
                                                            params[2].c_str(),
                                                            port);
                    fprintf(logFile, "Known EP Contact added! Result = %d\n", res == COMMO_SUCCESS);
                }
            
            } else if (s.find("cryptokey:") == 0) {
                s = s.substr(10);
                std::vector<std::string> params = split(s, ':');
                if (params.size() != 2 || params[0].length() != 32 || params[1].length() != 32) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: wrong args for cryptokey - needs <authkey>:<cryptokey> and each must be 32 characters\n");
                } else {
                    if (commo->setCryptoKeys((const uint8_t *)params[0].c_str(),
                                         (const uint8_t *)params[1].c_str())) {
                        fprintf(logFile, "CRYPTO KEY SET FAILED\n");
                    } else {
                        fprintf(logFile, "CRYPTO KEY SET SUCCESS\n");
                    }
                }
            } else if (s.find("chatsend:") == 0) {
                // Send chat to a contact directly; tests unicasting
                s = s.substr(9);
                std::string saString = makeChat(s);

                ContactUID uid((uint8_t *)s.c_str(), s.length());
                const ContactUID *uidList = &uid;
                ContactList cl(1, &uidList);
                commo->sendCoT(&cl, saString.c_str());
                fprintf(logFile, "Chat Sent to %s - %s\n", s.c_str(), saString.c_str());
//                commo->sendCoTTcpDirect("192.168.99.111", 1111, saString.c_str());
            
            } else if (s.find("remiface:") == 0) {
                // Remove interface
                s = s.substr(9);
                int ifaceNum = atoi(s.c_str());
                if (ifaceNum < 0 || (size_t)ifaceNum > ifaceList.size()) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: unknown iface number to remove!\n");
                } else {
                    fprintf(logFile, "Removed iface %d\n", ifaceNum);
                    fflush(logFile);
                    std::vector<NetIfaceInfo> &v = ifaceList[ifaceNum];
                    for (std::vector<NetIfaceInfo>::iterator iter = v.begin(); iter != v.end(); ++iter) {
                        NetIfaceInfo &inf = *iter;
                        CommoResult res = COMMO_ILLEGAL_ARGUMENT;
                        switch (inf.type) {
                            case NetIfaceInfo::IT_STREAM:
                                res = commo->removeStreamingInterface((StreamingNetInterface *)inf.iface);
                                break;
                            case NetIfaceInfo::IT_BCAST:
                                res = commo->removeBroadcastInterface((PhysicalNetInterface *)inf.iface);
                                break;
                            case NetIfaceInfo::IT_INBOUND:
                                res = commo->removeInboundInterface((PhysicalNetInterface *)inf.iface);
                                break;
                            case NetIfaceInfo::IT_TCPIN:
                                res = commo->removeTcpInboundInterface((TcpInboundNetInterface *)inf.iface);
                                break;
                            
                        }
                        fprintf(logFile, "interface  %d removed? %d\n", ifaceNum, res == COMMO_SUCCESS);
                        fflush(logFile);
                    }
                }
            } else if (s.find("safreq:") == 0) {
                s = s.substr(7);
                int freq = atoi(s.c_str());
                if (freq <= 0)
                    fprintf(logFile, "SA Frequency cannot be 0 or negative\n");
                else
                    sendFreqMillis = freq;
                
            } else if (s.find("filesend:") == 0 || s.find("filerecv:") == 0) {
                // Send a file (simple)
                bool upload = (s[4] == 's');
                s = s.substr(9);
                size_t colon = s.find(":");
                std::string fn = s.substr(0, colon);
                std::string url = s.substr(colon+1);
                int id;
                CommoResult rc = commo->simpleFileTransferInit(&id, upload, 
                               url.c_str(), NULL, 0,
                               NULL, NULL, NULL, fn.c_str());
                if (rc != COMMO_SUCCESS)
                    fprintf(logFile, "FAILED TO INIT SIMPLE XFER %d\n", rc);
                else {
                    fprintf(logFile, "Simple Xfer Init success, id = %d\n", id);
                    rc = commo->simpleFileTransferStart(id);
                    if (rc != COMMO_SUCCESS)
                        fprintf(logFile, "FAILED TO START SIMPLE XFER %d with id %d\n", rc, id);
                }                    

            } else if (s.find("sfilesend:") == 0 || s.find("sfilerecv:") == 0) {
                // transfer a file (simple with ssl params)
                bool upload = (s[5] == 's');
                s = s.substr(10);

                std::vector<std::string> params = split(s, '^');
                // url will contain ':'s... so use ^ as a separator!
                // obvious limitation is no ^ in passwords, but oh well,
                // this is just a test app
                // file^certfile^certpw^user^password^url .. or ..
                // file^certfile^certpw^url
                if (params.size() == 4 || params.size() == 6) {
                    std::string fn = params[0];
                    std::string cert = params[1];
                    std::string certpw = params[2];
                    std::string url = params[params.size() - 1];
                    const char *user = NULL;
                    const char *passwd = NULL;
                    
                    
                    if (params.size() == 6) {
                        user = params[3].c_str();
                        passwd = params[4].c_str();
                    }
                    
                    size_t certLen = 0;
                    const uint8_t *certData = readFile(cert.c_str(), &certLen);
                    int id;
                    CommoResult rc = commo->simpleFileTransferInit(&id, upload, 
                                   url.c_str(), certData, certLen,
                                   certpw.c_str(), user, passwd, fn.c_str());
                    if (rc != COMMO_SUCCESS)
                        fprintf(logFile, "FAILED TO INIT SIMPLE SSL XFER %d\n", rc);
                    else {
                        fprintf(logFile, "Simple SSLXfer Init success, id = %d\n", id);
                        rc = commo->simpleFileTransferStart(id);
                        if (rc != COMMO_SUCCESS)
                            fprintf(logFile, "FAILED TO START SIMPLE SSL XFER %d with id %d\n", rc, id);
                    }                    
                } else {
                    fprintf(logFile, "ERROR IN COMMAND LINE: ssl file send wrong # of args!\n");
                }
                
            } else if (s.find("mpsend:") == 0) {
                // Send a mission package!
                s = s.substr(7);
                size_t colon = s.find(":");
                std::string fn = s.substr(0, colon);
                std::string uid = s.substr(colon+1);
                ContactUID cuid((const uint8_t *)uid.c_str(), uid.length());
                const ContactUID *cptr = &cuid;
                ContactList clist(1, &cptr);
                int id;
                if (commo->sendMissionPackageInit(&id, &clist, fn.c_str(), fn.c_str(), fn.c_str()) != COMMO_SUCCESS)
                    fprintf(logFile, "FAILED TO SEND MP INIT\n");
                else {
                    fprintf(logFile, "MP send %d initialized successfully, now starting\n", id);
                    if (commo->sendMissionPackageStart(id) != COMMO_SUCCESS)
                        fprintf(logFile, "MP send %d failed to start!!\n", id);
                    else
                        fprintf(logFile, "MP send %d started successfully!\n", id);
                }
                
                
            } else if (s.find("smpsend:") == 0) {
                // Send a mission package to a tak server!
                s = s.substr(8);
                size_t colon = s.find(":");
                std::string fn = s.substr(0, colon);
                std::string sNum = s.substr(colon+1);
                int ifaceNum = atoi(sNum.c_str());
                const char *remoteEndpointId = NULL;
                if (ifaceNum < 0 || (size_t)ifaceNum > ifaceList.size()) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: unknown iface number to send MP to!\n");
                } else {
                    std::vector<NetIfaceInfo> &v = ifaceList[ifaceNum];
                    for (std::vector<NetIfaceInfo>::iterator iter = v.begin(); iter != v.end(); ++iter) {
                        NetIfaceInfo &inf = *iter;
                        switch (inf.type) {
                            case NetIfaceInfo::IT_STREAM:
                                remoteEndpointId = ((StreamingNetInterface *)inf.iface)->remoteEndpointId;
                                break;
                            default:
                                fprintf(logFile, "interface %d is NOT streaming - cannot send file!\n", ifaceNum);
                                break;
                            
                        }
                    }
                }

                if (remoteEndpointId) {
                    int id;
                    if (commo->sendMissionPackageInit(&id, remoteEndpointId, fn.c_str(), fn.c_str()) != COMMO_SUCCESS)
                        fprintf(logFile, "FAILED TO INIT SEND MP TO SERVER\n");
                    else {
                        fprintf(logFile, "MP send %d TO SERVER initialized successfully\n", id);
                        if (commo->sendMissionPackageStart(id) == COMMO_SUCCESS)
                            fprintf(logFile, "MP send %d TO SERVER started successfully\n", id);
                        else
                            fprintf(logFile, "MP send %d TO SERVER failed to start!\n", id);
                    }
                }
                
                
            } else if (s.find("tcpin:") <= 1) {
                size_t loc = s.find(':');
                std::string port = s.substr(loc + 1);
                int portNum = atoi(port.c_str());

                TcpInboundNetInterface *iface = commo->addTcpInboundInterface(portNum);
                if (!iface)
                    fprintf(logFile, "Failed to add interface %s\n", s.c_str());
                else {
                    ifaceDescs[iface] = s;

                    std::vector<NetIfaceInfo> v;
                    v.push_back(NetIfaceInfo(NetIfaceInfo::IT_TCPIN, iface));
                    ifaceList.push_back(v);
                }
            } else if (s.find("stream:") <= 1) {
                std::vector<std::string> params = split(s, ':');
                uint8_t *caCert = NULL;
                size_t caLen = 0;
                size_t certLen = 0;
                uint8_t *cert = NULL;
                const char *certPw = NULL;
                const char *user = NULL;
                const char *userPw = NULL;
                StreamingNetInterface *iface = NULL;
                int portNum = 0;
                int hostIdx = 1;
                if (params.size() < 3) {
                    fprintf(logFile, "ERROR IN COMMAND LINE: need at least 2 args for streams!\n");
                    goto streamerr;
                }
                if (params[0].compare("sstream") == 0) {
                    if (params.size() != 6 && params.size() != 8) {
                        fprintf(logFile, "ERROR IN COMMAND LINE: need 5 or 7 args for sstream. treating as plain stream\n");
                        goto streamerr;
                    } else {
                        hostIdx = 4;
                        cert = readFile(params[1].c_str(), &certLen);
                        caCert = readFile(params[2].c_str(), &caLen);
                        if (!cert || !caCert) {
                            fprintf(logFile, "ERROR IN COMMAND LINE: cert %s or ca cert file %s not readable\n", params[1].c_str(), params[2].c_str());
                            goto streamerr;
                        } else {
                            certPw = params[3].c_str();
                            if (params.size() == 8) {
                                user = params[4].c_str();
                                userPw = params[5].c_str();
                                hostIdx = 6;
                            }
                        }
                    }
                }

                portNum = atoi(params[hostIdx+1].c_str());

                static const CoTMessageType allTypes[2] = {
                        CHAT,
                        SITUATIONAL_AWARENESS
                };

                iface = commo->addStreamingInterface(
                       params[hostIdx].c_str(), portNum, allTypes,
                       2, cert, certLen,
                       caCert, caLen, certPw, certPw, user, userPw);
                delete[] cert;
                delete[] caCert;
                if (!iface)
streamerr:
                    fprintf(logFile, "Failed to add interface %s\n", desc.c_str());
                else {
                    ifaceDescs[iface] = desc;

                    std::vector<NetIfaceInfo> v;
                    v.push_back(NetIfaceInfo(NetIfaceInfo::IT_STREAM, iface));
                    ifaceList.push_back(v);
                }
            } else {
                bool mcastIface = false;
                bool generic = false;
                bool ok = true;
                if (s.find("mcast:") == 0) {
                    s = s.substr(6);
                    mcastIface = true;
                } else if (s.find("genin:") == 0) {
                    s = s.substr(6);
                    generic = true;
                } else if (s.find("inbound:") == 0) {
                    s = s.substr(8);
                } else {
                    fprintf(stderr, "Unknown command: %s", s.c_str());
                    ok = false;
                }

                if (ok) {
                size_t loc = s.find(':');
                std::string port = s.substr(loc + 1);

                uint8_t *buf = new uint8_t[loc / 2];
                for (size_t i = 0; i + 1 < loc; i += 2) {
                    short shrt;
                    sscanf(s.c_str() + i, "%2hx", &shrt);
                    buf[i / 2] = (uint8_t)shrt;
                }

                HwAddress addr(buf, loc / 2);
                //const char *mcast = "224.0.1.0";
                const char *mcast = "239.2.3.1";
                int portNum = atoi(port.c_str());
                PhysicalNetInterface *iface;
                PhysicalNetInterface *iface2 = NULL;
                if (mcastIface) {
                    CoTMessageType t = SITUATIONAL_AWARENESS;
                    iface = commo->addBroadcastInterface(&addr, &t, 1, mcast, portNum);
                } else if (generic) {
                    iface = commo->addInboundInterface(&addr, portNum, NULL, 1, true);
                } else {
                    iface = commo->addInboundInterface(&addr, portNum, &mcast, 1, false);

                    const char *chat = "224.10.10.1";
                    iface2 = commo->addInboundInterface(&addr, 17012, &chat, 1, false);

                }
                if (!iface)
                    fprintf(logFile, "Failed to add interface %s\n", desc.c_str());
                else {
                    ifaceDescs[iface] = desc;
                    std::vector<NetIfaceInfo> v;
                    v.push_back(NetIfaceInfo(mcastIface ? NetIfaceInfo::IT_BCAST : NetIfaceInfo::IT_INBOUND, iface));
                    if (iface2) {
                        desc += " CHAT";
                        ifaceDescs[iface2] = desc;
                        v.push_back(NetIfaceInfo(mcastIface ? NetIfaceInfo::IT_BCAST : NetIfaceInfo::IT_INBOUND, iface2));
                    }
                    ifaceList.push_back(v);
                }
                
                delete[] buf;
                }
            }
            curArg += 2;
            if (curArg < argc)
                nextCmdLineTime = time(NULL) + atoi(argv[curArg]);
        }

    }

}

void test::CommoTest::contactAdded(const ContactUID *c)
{
    std::lock_guard<std::mutex> lock(contactMutex);
    std::string s((const char * const)c->contactUID, c->contactUIDLen);
    contactList.insert(s);
    contactsMenuDirty = true;
    s.insert(0, "Contact Added: ");
    log(LEVEL_INFO, TYPE_GENERAL, s.c_str(), nullptr);
}

void test::CommoTest::contactRemoved(const ContactUID* c)
{
    std::lock_guard<std::mutex> lock(contactMutex);
    std::string s((const char * const)c->contactUID, c->contactUIDLen);
    std::set<std::string>::iterator iter = contactList.find(s);
    if (iter != contactList.end()) {
        contactList.erase(iter);
        contactsMenuDirty = true;
    }
    s.insert(0, "Contact Removed: ");
    log(LEVEL_INFO, TYPE_GENERAL, s.c_str(), nullptr);
}

void test::CommoTest::log(Level level, Type type, const char* message, void* data)
{
    std::string s(LEVEL_STRINGS[level]);
    std::string t(TYPE_STRINGS[type]);
    std::string time(getTimeString());
    fprintf(logFile, "[%s-%s] %s: %s\n", s.c_str(), t.c_str(), time.c_str(), message);
    fflush(logFile);
}

void test::CommoTest::sendCoTFailure(const char *host, int port, const char *errorReason)
{
    fprintf(logFile, "TCP SEND FAILED: host = %s port = %d reason = %s\n", host, port, errorReason);
    
}

void test::CommoTest::genericDataReceived(const uint8_t *data, size_t length, const char *rxIfaceEndpointId)
{
    std::stringstream ss;
    ss << "generic data from " << rxIfaceEndpointId << " {";
    ss << std::hex;
    for (size_t i = 0; i < length; ++i)
        ss << std::setw(2) << (int)data[i];
    ss << "}";
    std::string s = ss.str();
    log(LEVEL_INFO, TYPE_GENERAL, s.c_str(), nullptr);
}

void test::CommoTest::cotMessageReceived(const char* msg, const char *rxIfaceEndpointId)
{
    std::string s(getTimeString());
    fprintf(messageLog, "%s: [%s] %s\n", s.c_str(), rxIfaceEndpointId, msg);
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

void test::CommoTest::interfaceError(NetInterface *iface, netinterfaceenums::NetInterfaceErrorCode errCode)
{
    std::map<NetInterface *, std::string>::iterator iter;
    iter = ifaceDescs.find(iface);
    if (iter == ifaceDescs.end()) {
        fprintf(logFile, "Interface Error: Unknown interface?!?\n");
    } else {
        fprintf(logFile, "Interface Error: %d\n", errCode);
    }
}

void test::CommoTest::interfaceDown(NetInterface *iface)
{
    std::map<NetInterface *, std::string>::iterator iter;
    iter = ifaceDescs.find(iface);
    if (iter == ifaceDescs.end()) {
        fprintf(logFile, "Interface Down: Unknown interface?!?\n");
    } else {
        fprintf(logFile, "Interface Down: %s\n", iter->second.c_str());
    }
}

void test::CommoTest::interfaceUp(NetInterface *iface)
{
    std::map<NetInterface *, std::string>::iterator iter;
    iter = ifaceDescs.find(iface);
    if (iter == ifaceDescs.end()) {
        fprintf(logFile, "Interface Up: Unknown interface?!?\n");
    } else {
        fprintf(logFile, "Interface Up: %s\n", iter->second.c_str());
    }
}


MissionPackageTransferStatus test::CommoTest::missionPackageReceiveInit(
                char *destFile, size_t destFileSize,
                const char *transferName, const char *sha256hash,
                uint64_t expectedByteSize,
                const char *senderCallsign)
{
    static int mpTransferSerial = 0;
    
    std::stringstream ss;
    ss << baseDir << "/" << "mprx-"
       << mpTransferSerial
       << "-"
       << destFile;
    std::string s = ss.str();
    if (s.length() >= destFileSize)
        return MP_TRANSFER_FINISHED_FAILED;
    strcpy(destFile, s.c_str());
    
    fprintf(logFile, "Receive of MP %s from %s requested - assigned output file %s\n",
        transferName, senderCallsign, destFile);

    mpTransferSerial++;
    return MP_TRANSFER_FINISHED_SUCCESS;
}
               
void test::CommoTest::missionPackageReceiveStatusUpdate(
                            const MissionPackageReceiveStatusUpdate *update)
{
    switch (update->status) {
      case MP_TRANSFER_FINISHED_SUCCESS:
      case MP_TRANSFER_FINISHED_FAILED:
        fprintf(logFile, "Receive of MP %s result %s error detail = %s total bytes %" PRIu64 "\n",
            update->localFile,
            update->status == MP_TRANSFER_FINISHED_SUCCESS ? "OK" : "FAIL",
            update->errorDetail == NULL ? "[none]" : update->errorDetail,
            update->totalBytesReceived);
        break;
      case MP_TRANSFER_ATTEMPT_IN_PROGRESS:
        fprintf(logFile, "Receive of MP %s progress report attempt %d of %d bytes %" PRIu64 " of %" PRIu64 "\n",
            update->localFile,
            update->attempt,
            update->maxAttempts,
            update->totalBytesReceived,
            update->totalBytesExpected);
        break;
      case MP_TRANSFER_ATTEMPT_FAILED:
        fprintf(logFile, "Receive of MP %s attempt %d of %d FAILED! Bytes %" PRIu64 " of %" PRIu64 "\n",
            update->localFile,
            update->attempt,
            update->maxAttempts,
            update->totalBytesReceived,
            update->totalBytesExpected);
        break;
      default:
        fprintf(logFile, "Receive of MP %s UNEXPECTED STATUS CODE\n",
            update->localFile);
        break;
    }
}

void test::CommoTest::missionPackageSendStatusUpdate(const MissionPackageSendStatusUpdate *update)
{
    std::string s;
    if (update->recipient)
        s = std::string((const char *)update->recipient->contactUID, update->recipient->contactUIDLen);
    else
        s = "TAK Server";

    switch (update->status) {
      case MP_TRANSFER_FINISHED_SUCCESS:
      case MP_TRANSFER_FINISHED_FAILED:
        fprintf(logFile, "Mission package %d sent to %s, result = %s, reason: %s bytes: %" PRIu64 "\n",
            update->xferid, 
            s.c_str(), 
            update->status == MP_TRANSFER_FINISHED_SUCCESS ? "SUCCESS" : "FAILED",
            update->additionalDetail ? update->additionalDetail : "[none]",
            update->totalBytesTransferred);
        break;
      case MP_TRANSFER_FINISHED_CONTACT_GONE:
        fprintf(logFile, "Mission package %d done, contact gone!\n",
            update->xferid);
        break;
      case MP_TRANSFER_FINISHED_DISABLED_LOCALLY:
        fprintf(logFile, "Mission package %d aborted; transfers disabled locally!\n",
            update->xferid);
        break;
      case MP_TRANSFER_SERVER_UPLOAD_PENDING:
        fprintf(logFile, "Mission package %d to %s, pending upload to server!\n",
            update->xferid, s.c_str());
        break;
      case MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS:
        fprintf(logFile, "Mission package %d to %s, performing upload to server! %" PRIu64 " bytes uploaded\n",
            update->xferid, s.c_str(),
            update->totalBytesTransferred);
        break;
      case MP_TRANSFER_SERVER_UPLOAD_SUCCESS:
        fprintf(logFile, "Mission package %d to %s, completed upload to server! %" PRIu64 " bytes uploaded, URL is %s\n",
            update->xferid, s.c_str(),
            update->totalBytesTransferred,
            update->additionalDetail);
        break;
      case MP_TRANSFER_SERVER_UPLOAD_FAILED:
        fprintf(logFile, "Mission package %d to %s, failed upload to server! %" PRIu64 " bytes uploaded\n",
            update->xferid, s.c_str(),
            update->totalBytesTransferred);
        break;
      case MP_TRANSFER_ATTEMPT_IN_PROGRESS:
        fprintf(logFile, "Mission package %d to %s, in progress, waiting for recipient ack!\n",
            update->xferid, s.c_str());
        break;
      default:
        fprintf(logFile, "Send of MP %d UNEXPECTED STATUS CODE\n",
            update->xferid);
        break;
    }
}

CoTPointData test::CommoTest::getCurrentPoint()
{
    CoTPointData p(36.5261810013514, -77.3862509255614, COMMO_COT_POINT_NO_VALUE,
        COMMO_COT_POINT_NO_VALUE, COMMO_COT_POINT_NO_VALUE);
    return p;
}

void test::CommoTest::createUUID(char *uuidString)
{
#ifdef WIN32
    UUID uuid = {0};
    RPC_CSTR wstr = NULL;

    UuidCreate(&uuid);
    UuidToStringA(&uuid, &wstr);
    strcpy(uuidString, (char *)wstr);
    RpcStringFreeA(&wstr);
#else
    FILE *f = fopen("/proc/sys/kernel/random/uuid", "rb");
    fread(uuidString, 1, COMMO_UUID_STRING_BUFSIZE, f);
    fclose(f);
#endif
}

void test::CommoTest::fileTransferUpdate(const SimpleFileIOUpdate *update)
{
    fprintf(logFile, "TransferUpdate: %d %d (%s) %" PRIu64 " of %" PRIu64 "\n",
                      update->xferid, update->status, 
                      update->additionalInfo ? update->additionalInfo : "no info",
                      update->bytesTransferred, update->totalBytesToTransfer);
    fflush(stdout);

}


void test::CommoTest::cloudOperationUpdate(const CloudIOUpdate *update)
{
    fprintf(logFile, "CloudUpdate: %d %d %d (%s) %" PRIu64 " of %" PRIu64 "\n",
                      update->operation,
                      update->xferid, update->status, 
                      update->additionalInfo ? update->additionalInfo : "no info",
                      update->bytesTransferred, update->totalBytesToTransfer);
    if (update->operation == CLOUDIO_OP_LIST_COLLECTION && update->status == FILEIO_SUCCESS && update->entries) {
        fprintf(logFile, "CloudUpdate listing: %d\n", (int)update->numEntries);
        for (size_t i = 0; i < update->numEntries; ++i)
            fprintf(logFile, "    %s (%s) %" PRIu64 "\n", update->entries[i]->path, update->entries[i]->type == CloudCollectionEntry::Type::TYPE_COLLECTION ? "folder" : "file", update->entries[i]->fileSize);
    }
    fflush(logFile);
}




void test::CommoTest::sendMarker(double lat, double lon) { 
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
    now += 120;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    strftime(timebuf2, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);

    std::string saString = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><event version=\"2.0\" uid=\"";
    saString += "2084306f-8896-42a6-80a5-";

    char zz[13];
    static int serial = 0;
    sprintf(zz, "%012d", serial);
    serial++;
    saString += zz;
    saString += "\" type=\"a-h-G\" how=\"h-g-i-g-o\" time=\"";
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\">";

    std::stringstream ss;
//    ss << "aacc<point lat=\"" << lat << "\" lon=\"" <<
// ^^ iTAK crash
    ss << "<point lat=\"-Infinity\" lon=\"" <<
// ^^ wintak crash
//    ss << "<point lat=\"" << lat << "\" lon=\"" <<
// ^^ normal
       lon << "\" hae=\"9999999.0\" ce=\"9999999.0\" le=\"9999999.0\"/>" <<
           "<detail><status readiness=\"true\"/><archive/><contact callsign=\"zzz" <<
           serial << "\"/><link uid=\"" << myUID << "\" production_time=\"" <<
           timebuf << "\" relation=\"p-p\" type=\"a-f-G-U-C\" parent_callsign=\"" << myCallsign <<  "\"" <<
           "/>" <<
//           "<usericon setpath=\"COT_MAPPING_2525B/a-h/a-h-G\"/>" <<
           "<color argb=\"-1\"/>" <<
           "<nineline/><altNinelines/><fiveline/><precisionlocation geopointsrc=\"Calc\" altsrc=\"???\"/></detail></event>";
    saString += ss.str();

     CommoResult res = commo->broadcastCoT(saString.c_str());
     if (res != COMMO_SUCCESS)
         printf("bcastaaa %d %s\n", res, saString.c_str());

}


std::string test::CommoTest::makeChat(std::string &destUid) 
{
    static char buf[COMMO_UUID_STRING_BUFSIZE];
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
    now += 120;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    createUUID(buf);
    buf[COMMO_UUID_STRING_BUFSIZE-1] = 0;
    strftime(timebuf2, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);
//<__serverdestination destinations="192.168.167.167:4242:tcp:ANDROID-48:5A:3F:49:93:24"/><precisionlocation geopointsrc="???" altsrc="???"/></detail></event>
    std::string saString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"GeoChat.";
    saString += myUID;
    saString += ".";
    saString += buf;
    saString += "\" type=\"b-t-f\" time=\"";
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\" how=\"h-g-i-g-o\">";
    saString += "<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" "
           "ce=\"9999999\" le=\"9999999\"/>"
           "<detail> <__chat id=\"";
    saString += destUid;
    saString += "\" chatroom=\"testroom\"><chatgrp id=\"";
    saString += destUid;
    saString += "\" uid0=\"";
    saString += myUID;
    saString += "\" uid1=\"";
    saString += destUid;
    saString += "\"/></__chat><link relation=\"p-p\" type=\"a-f-G-U-C\" uid=\"";
    saString += myUID;
    saString += "\"/><remarks to=\"";
    saString += destUid;
    saString += "\" source=\"";
    saString += myUID;
    saString += "\" time=\"";
    saString += timebuf;
    saString += "\">Test from commo</remarks>";
    saString += "<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>"
           "</detail>"
           "</event>";
    return saString;
}




void test::CommoTest::sendSA(bool mcast, const char *uid)
{
#if 1
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
    now += 120;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    strftime(timebuf2, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);

    std::string saString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"";
if (mcast) {
    saString += myUID;
    saString += "\" type=\"a-f-G-U-C\" how = \"h-e\" time=\"";
} else {
    saString += "2084306f-8896-42a6-80a5-7c1706f93620";
    saString += "\" type=\"b-m-p-c\" how=\"h-g-i-g-o\" time=\"";
}
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\">";

if (mcast) {
    saString += "<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" "
           "ce=\"9999999\" le=\"9999999\"/>"
           "<detail>        <contact endpoint=\"10.233.154.103:4242:tcp\""
           " callsign=\"";
    saString += myCallsign;
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
} else {
    saString += "<point lat=\"46.5261810013514\" lon=\"-87.3862509255614\" hae=\"9999999.0\" "
           "ce=\"9999999\" le=\"9999999\"/>"
           "<detail>        <contact callsign=\"";
    saString += myCallsign;
    saString += ".22.161714\"/>";
    saString += "<link type=\"b-m-p-c\" uid=\"";
    saString += myUID;
    saString += "\" relation=\"p-p\" production_time=\"";
    saString += timebuf;
    saString += "\" />"
           "<archive /><request notify=\"192.168.1.2:4242:udp\"/>";
    saString += 
           "</detail>";
     saString += "<handled />";
    saString += "</event>";


}
#else
std::string saString = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><event version='2.0' uid='ANDROID-6' type='a-f-G-U-C-I' time='2017-02-17T17:42:00.000Z' start='2017-02-17T17:42:00.000Z' stale='2017-02-17T17:42:15.000Z' how='h-e'><point lat='33.31207' lon='-111.81861' hae='503' ce='9999999' le='9999999' /><detail><track course='198.21238773761536' speed='0.0'/><precisionlocation altsrc='DTED2'/></detail></event>";


#endif
    if (mcast) {
        CommoResult res = commo->broadcastCoT(saString.c_str());
        if (res != COMMO_SUCCESS)
            printf("bcast %d\n", res);
    } else {
//printf("bcast2\n");
                const char *name;
                if (uid)
                    name = uid;
                else
                    name = contactList.begin()->c_str();
                ContactUID uid((uint8_t *)name, strlen(name));
                const ContactUID *uidList = &uid;
                ContactList cl(1, &uidList);
                fprintf(logFile, "Sending: %s\n", saString.c_str());
                fflush(logFile);
                commo->sendCoT(&cl, saString.c_str());
    }

}


int main(int argc, char *argv[])
{
#ifdef WIN32
    WSAData wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);
#else
    // Ignore sigpipe resulting from network I/O that writes to disconnected
    // remote tcp pipes
    struct sigaction action;
    struct sigaction oldaction;
    memset(&oldaction, 0, sizeof(struct sigaction));
    sigaction(SIGPIPE, NULL, &oldaction);
    action = oldaction;
    action.sa_handler = SIG_IGN;
    sigaction(SIGPIPE, &action, NULL);
#endif

    // Initialize libxml2
    xmlInitParser();
    
    //SSL_library_init();
    OPENSSL_init_ssl(0, NULL);
    SSL_load_error_strings();

    // Initialize curl
    curl_global_init(CURL_GLOBAL_NOTHING);

    // Create test
    try {
        test::CommoTest test;

        test.run(argc, argv);
    } catch (int &x) {
        fprintf(stderr, "Failed to init %d\n", x);
    }

    // Tear down libxml2
    xmlCleanupParser();

#ifdef WIN32
    WSACleanup();
#endif
    return 0;
}
