#ifndef IMPL_CLOUDIOMANAGER_H_
#define IMPL_CLOUDIOMANAGER_H_


#include "cloudio.h"
#include "urlrequestmanager.h"
#include "commothread.h"
#include <libxml/parser.h>
#include <set>
#include <vector>

namespace atakmap {
namespace commoncommo {
namespace impl
{

struct InternalCloudCollectionEntry : public CloudCollectionEntry
{
    InternalCloudCollectionEntry(Type type, const std::string &path,
                                 uint64_t fileSize);
    virtual ~InternalCloudCollectionEntry();
    
private:
    COMMO_DISALLOW_COPY(InternalCloudCollectionEntry);
};


struct InternalCloudUpdate : public URLIOUpdate, public CloudIOUpdate
{
    InternalCloudUpdate(CloudIOOperation op,
            const int xferid,
            const SimpleFileIOStatus status,
            const char *additionalInfo,
            uint64_t bytesTransferred,
            uint64_t totalBytesToTransfer,
            const CloudCollectionEntry **entries,
            size_t numEntries);
    virtual ~InternalCloudUpdate();
    
    virtual SimpleFileIOUpdate *getBaseUpdate(); 
};


struct OwncloudURLRequest : public URLRequest
{
    OwncloudURLRequest(CloudIOOperation op,
               URLRequestType type, const std::string &baseUrl,
               const std::string &servicePath,
               const std::string &requestPath,
               const char *localFileName,
               bool useLogin,
               const std::string &user,
               const std::string &pass,
               bool useSSL,
               STACK_OF(X509) *caCerts);
    virtual ~OwncloudURLRequest();
    
    virtual void curlExtraConfig(CURL *curlCtx)
                           COMMO_THROW (IOStatusException);
    virtual URLIOUpdate *createUpdate(
        const int xferid,
        SimpleFileIOStatus status,
        const char *additionalInfo,
        uint64_t bytesTransferred,
        uint64_t totalBytesToTransfer);
        
    virtual SimpleFileIOStatus statusForResponse(int response);

    // Repeatedly invoked for data retrieved when type == BUFFER_DOWNLOAD
    virtual void downloadedData(uint8_t *data, size_t len);
    
    void setDestPath(const std::string &path);

private:
    CloudIOOperation op;
    bool useLogin;
    std::string user;
    std::string pass;
    std::string servicePath;
    std::string requestPath;
    struct curl_slist *customHeaders;
    
    xmlSAXHandler saxHandler;
    enum { NOT_STARTED, TEST_IN_VERSION, TEST_IN_VSTRING,
           TEST_IN_DAVROOT,
           IN_DOC, IN_RESP, IN_REF,
           IN_RESOURCETYPE, IN_CONTENT_LEN, COMPLETE, PARSE_ERROR } saxParseState;
    xmlParserCtxtPtr xmlContext;
    std::string xmlError;
    
    // TEST_SERVER only
    std::string versionString;
    std::string testDavRoot;
    
    // LIST only
    std::vector<InternalCloudCollectionEntry *> entries;
    std::string curEntryPath;
    CloudCollectionEntry::Type curEntryType;
    std::string curEntryFileSize;

    // MOVE only
    std::string destPath;
    
    
    void pushEntry();
    

    // SAX parser callback functions    
    static void startDocumentRedir(void *ctx);
    static void endDocumentRedir(void *ctx);
    static void startElementRedir(void *ctx, const xmlChar *name,
                                  const xmlChar *prefix,
                                  const xmlChar *namespaceUri,
                                  int numNamespaces,
                                  const xmlChar **namespaces,
                                  int numAttribs,
                                  int numDefaulted,
                                  const xmlChar **attibs);
    static void endElementRedir(void *ctx, const xmlChar *name,
                                const xmlChar *prefix,
                                const xmlChar *namespaceUri);
    static void charactersRedir(void *ctx, const xmlChar *text, int len);
    static xmlEntityPtr getEntity(void *ctx, const xmlChar *name);
    
    
};


struct FTPCloudURLRequest : public URLRequest
{
    FTPCloudURLRequest(CloudIOOperation op,
               URLRequestType type, const std::string &baseUrl,
               const std::string &requestPath,
               const char *localFileName,
               bool useLogin,
               const std::string &user,
               const std::string &pass,
               bool useSSL,
               STACK_OF(X509) *caCerts);
    virtual ~FTPCloudURLRequest();
    
    virtual void curlExtraConfig(CURL *curlCtx)
                           COMMO_THROW (IOStatusException);
    virtual URLIOUpdate *createUpdate(
        const int xferid,
        SimpleFileIOStatus status,
        const char *additionalInfo,
        uint64_t bytesTransferred,
        uint64_t totalBytesToTransfer);
        
    virtual SimpleFileIOStatus statusForResponse(int response);

    // Repeatedly invoked for data retrieved when type == BUFFER_DOWNLOAD
    virtual void downloadedData(uint8_t *data, size_t len);
    
    // MUST be full path from server root! /some/dir/to/rename
    void setRenameSrcPath(const std::string &path);
    // MUST be full path from server root!
    void setDestPath(const std::string &path);

private:
    CloudIOOperation op;
    bool useLogin;
    std::string user;
    std::string pass;
    std::string requestPath;
    struct curl_slist *customHeaders;
    
    // LIST only
    std::vector<InternalCloudCollectionEntry *> entries;
    std::string curEntryBuf;

    // MOVE only
    std::string srcPath;
    
    // MOVE, DELETE, MAKE_COLLECTION only
    std::string destPath;
    
    void parseEntry();
    void pushEntry(const std::string &name, bool isDir, uint64_t len);
};



class CloudIOManager;
struct InternalCloudClient : public URLRequestIO, public CloudClient
{
    InternalCloudClient(CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *user,
                  const char *pass) COMMO_THROW (CommoResult);
    virtual ~InternalCloudClient();

    virtual CommoResult startOperation(int cloudIOid);
    virtual void cancelOperation(int cloudIOid);
    
protected:
    void parseCerts(const uint8_t *caCert,
                    const size_t caCertLen,
                    const char *caCertPassword) COMMO_THROW (CommoResult);
    
    CloudIOManager *owner;
    CloudIO *clientIO;

    CloudIOProtocol proto;
    STACK_OF(X509) *caCerts;
    bool useLogin;
    std::string user;
    std::string pass;

private:
    COMMO_DISALLOW_COPY(InternalCloudClient);
};


struct OwncloudClient : public InternalCloudClient
{
    OwncloudClient(CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *host,
                  int port,
                  const char *basePath,
                  const char *user,
                  const char *pass,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword) COMMO_THROW (CommoResult);
    virtual ~OwncloudClient();
    
    // URLRequestIO callback
    virtual void urlRequestUpdate(URLIOUpdate *update);
    
    // CloudClient public API
    virtual CommoResult testServerInit(int *cloudIOid);
    virtual CommoResult listCollectionInit(int *cloudIOid,
                                           const char *path);
    virtual CommoResult getFileInit(int *cloudIOid,
                                    const char *localFile,
                                    const char *remotePath);
    virtual CommoResult putFileInit(int *cloudIOid,
                                    const char *remotePath,
                                    const char *localFile);
    virtual CommoResult moveResourceInit(int *cloudIOid,
                                         const char *fromPath,
                                         const char *toPath);
    virtual CommoResult deleteResourceInit(int *cloudIOid,
                                    const char *remotePath);
    virtual CommoResult createCollectionInit(int *cloudIOid,
                                             const char *path);

private:
    bool isSSL;
    // Path portion of webdav from server root WITHOUT trailing slash
    // and all double slashes removed
    std::string basePath;
    // Path portion of caps from server root WITHOUT trailing slash
    // and all double slashes removed
    std::string capsPath;
    // URL without path portion.
    std::string baseUrl;
    

    COMMO_DISALLOW_COPY(OwncloudClient);
};


struct FTPCloudClient : public InternalCloudClient
{
    FTPCloudClient(CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *host,
                  int port,
                  const char *basePath,
                  const char *user,
                  const char *pass,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword) COMMO_THROW (CommoResult);
    virtual ~FTPCloudClient();
    
    // URLRequestIO callback
    virtual void urlRequestUpdate(URLIOUpdate *update);
    
    // CloudClient public API
    virtual CommoResult testServerInit(int *cloudIOid);
    virtual CommoResult listCollectionInit(int *cloudIOid,
                                           const char *path);
    virtual CommoResult getFileInit(int *cloudIOid,
                                    const char *localFile,
                                    const char *remotePath);
    virtual CommoResult putFileInit(int *cloudIOid,
                                    const char *remotePath,
                                    const char *localFile);
    virtual CommoResult moveResourceInit(int *cloudIOid,
                                         const char *fromPath,
                                         const char *toPath);
    virtual CommoResult deleteResourceInit(int *cloudIOid,
                                    const char *remotePath);
    virtual CommoResult createCollectionInit(int *cloudIOid,
                                             const char *path);

private:
    bool isSSL;
    // Starts and ends with /
    std::string basePath;
    // URL with path portion - no trailing /
    std::string baseUrl;
    

    COMMO_DISALLOW_COPY(FTPCloudClient);
};


class CloudIOManager
{
public:
    CloudIOManager(CommoLogger *logger, URLRequestManager *urlManager);
    ~CloudIOManager();

    CommoResult createCloudClient(CloudClient **result,
                         CloudIO *io,
                         CloudIOProtocol proto,
                         const char *host,
                         int port,
                         const char *basePath,
                         const char *user,
                         const char *pass,
                         const uint8_t *caCerts,
                         size_t caCertsLen,
                         const char *caCertPassword);
    CommoResult destroyCloudClient(CloudClient *client);

private:
    CommoLogger *logger;
    URLRequestManager *urlManager;
    
    thread::Mutex clientsMutex;
    std::set<InternalCloudClient *> clients;

    friend struct InternalCloudClient;
    friend struct OwncloudClient;
    friend struct FTPCloudClient;
};

}
}
}

#endif
