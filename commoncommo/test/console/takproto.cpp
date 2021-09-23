#include <commo.h>

#include "libxml/tree.h"
#include "libxml/xmlerror.h"
#include "openssl/ssl.h"
#include "openssl/crypto.h"
#include "curl/curl.h"
#include <string.h>
#include <string>

#ifndef WIN32
#include <unistd.h>
#include <signal.h>
#endif

using namespace atakmap::commoncommo;


namespace {
    char *readFile(const char *fileName)
    {
        FILE *f;
        if (!strcmp(fileName, "-"))
            f = stdin;
        else {
            f = fopen(fileName, "rb");
            if (!f) {
                fprintf(stderr, "Could not read input file\n");
                return NULL;
            }
        }
            
        size_t blockSize = 1024;
        char *ret = new char[1024];
        size_t buflen = blockSize;

        while (true) {
            size_t n = fread(ret + buflen - blockSize, 1, blockSize, f);
            if (n != blockSize) {
                ret[buflen - blockSize + n] = '\0';
                break;
            }
                
            char *nret = new char[buflen + blockSize];
            memcpy(nret, ret, buflen);
            buflen += blockSize;
            delete[] ret;
        }
        
        if (f != stdin)
            fclose(f);

        return ret;
    }

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
    class Logger : public CommoLogger {
    public:
        void log(Level level, Type type, const char* message, void* data) override {
            std::string s(LEVEL_STRINGS[level]);
            std::string t(TYPE_STRINGS[type]);
            fprintf(stderr, "[%s-%s]: %s\n", s.c_str(), t.c_str(), message);
        }
    };

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
    
    OPENSSL_init_ssl(0, NULL);
    SSL_load_error_strings();

    // Initialize curl
    curl_global_init(CURL_GLOBAL_NOTHING);
    
    
    Logger logger;
    const char *uidstr = "someuid";
    ContactUID uid((const uint8_t *)uidstr, strlen(uidstr));
    Commo *c = new Commo(&logger, &uid, "somecs");
    

    char *protodata;
    char *xmlOut;
    size_t protoLen;
    
    const char *file = "-";
    if (argc == 2)
        file = argv[1];
    else if (argc != 1) {
        fprintf(stderr, "Usage %s [input file]\n", argv[0]);
        return 1;
    }
    
    char *cotXml = readFile(file);
    if (!cotXml)
        return 1;
    
    CommoResult r = c->cotXmlToTakproto(&protodata, &protoLen,
                                 cotXml, 1);
    if (r != COMMO_SUCCESS) {
        fprintf(stderr, "Conversion to takproto failed\n");
        return 1;
    }                             
    r = c->takprotoToCotXml(&xmlOut, protodata, protoLen);
    if (r != COMMO_SUCCESS) {
        fprintf(stderr, "Conversion from takproto to xml failed\n");
        return 1;
    }
    
    printf("%s\n", xmlOut);
    
    
    c->takmessageFree(protodata);
    c->takmessageFree(xmlOut);



    // Tear down libxml2
    xmlCleanupParser();

#ifdef WIN32
    WSACleanup();
#endif
    return 0;
}
