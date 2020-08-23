#ifndef CLOUDIO_H_
#define CLOUDIO_H_

#include <stdint.h>
#include <stddef.h>

#include "simplefileio.h"

namespace atakmap {
namespace commoncommo {

enum CloudIOProtocol {
    CLOUDIO_PROTO_HTTP,
    CLOUDIO_PROTO_HTTPS,
    CLOUDIO_PROTO_FTP,
    CLOUDIO_PROTO_FTPS,
};

enum CloudIOOperation {
    CLOUDIO_OP_TEST_SERVER,
    CLOUDIO_OP_LIST_COLLECTION,
    CLOUDIO_OP_GET,
    CLOUDIO_OP_PUT,
    CLOUDIO_OP_MOVE,
    CLOUDIO_OP_MAKE_COLLECTION,
    CLOUDIO_OP_DELETE,
};

struct COMMONCOMMO_API CloudCollectionEntry
{
    typedef enum { TYPE_FILE, TYPE_COLLECTION } Type;
    const char * const path;
    const Type type;
    const uint64_t fileSize;
    static const uint64_t FILE_SIZE_UNKNOWN = 18446744073709551615U;

protected:
    CloudCollectionEntry(Type type, const char *path,
                         uint64_t fileSize) : path(path),
                                              type(type), 
                                              fileSize(fileSize) {};
    virtual ~CloudCollectionEntry() {};

private:
    COMMO_DISALLOW_COPY(CloudCollectionEntry);
};


// Uses same semantics as it's superclass, but adds identifier of what type
// of cloud operation and data fields to supplement
// For TEST_SERVER, additionalInfo is version string of server
struct COMMONCOMMO_API CloudIOUpdate : public SimpleFileIOUpdate
{
    const CloudIOOperation operation;

    // Only for operation == LIST_COLLECTION
    const CloudCollectionEntry ** const entries;
    const size_t numEntries;
    
protected:
    CloudIOUpdate(CloudIOOperation op,
            const int xferid,
            const SimpleFileIOStatus status,
            const char *additionalInfo,
            uint64_t bytesTransferred,
            uint64_t totalBytesToTransfer,
            const CloudCollectionEntry **entries,
            size_t numEntries) : SimpleFileIOUpdate(
                                        xferid,
                                        status,
                                        additionalInfo,
                                        bytesTransferred,
                                        totalBytesToTransfer),
                                        operation(op),
                                        entries(entries),
                                        numEntries(numEntries)
                                        {};
    virtual ~CloudIOUpdate() {};

private:
    COMMO_DISALLOW_COPY(CloudIOUpdate);
};

class COMMONCOMMO_API CloudIO
{
public:
    virtual void cloudOperationUpdate(const CloudIOUpdate *update) = 0;

protected:
    CloudIO() {};
    virtual ~CloudIO() {};

private:
    COMMO_DISALLOW_COPY(CloudIO);
};

// Path elements *must* be URL-encoded! They can include or omit the leading '/'
class COMMONCOMMO_API CloudClient
{
public:
    virtual CommoResult testServerInit(int *cloudIOid) = 0;
    virtual CommoResult listCollectionInit(int *cloudIOid,
                                           const char *path) = 0;
    virtual CommoResult getFileInit(int *cloudIOid,
                                    const char *localFile,
                                    const char *remotePath) = 0;
    virtual CommoResult putFileInit(int *cloudIOid,
                                    const char *remotePath, 
                                    const char *localFile) = 0;
    virtual CommoResult moveResourceInit(int *cloudIOid,
                                         const char *fromPath,
                                         const char *toPath) = 0;
    virtual CommoResult deleteResourceInit(int *cloudIOid,
                                         const char *remotePath) = 0;
    virtual CommoResult createCollectionInit(int *cloudIOid,
                                             const char *path) = 0;
    
    virtual CommoResult startOperation(int cloudIOid) = 0;
    virtual void cancelOperation(int cloudIOid) = 0;

protected:
    CloudClient() {};
    virtual ~CloudClient() {};

private:
    COMMO_DISALLOW_COPY(CloudClient);
};



}
}

#endif
