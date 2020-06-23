#ifndef SIMPLE_FILEIO_H_
#define SIMPLE_FILEIO_H_

#include <stdint.h>

#include "commoutils.h"
#include "commoresult.h"

namespace atakmap {
namespace commoncommo {


enum SimpleFileIOStatus {
    // In progress
    FILEIO_INPROGRESS,

    // Finished ok
    FILEIO_SUCCESS,

    // Setup/init errors
    FILEIO_HOST_RESOLUTION_FAIL,
    FILEIO_CONNECT_FAIL,
    FILEIO_URL_INVALID,     // URL syntax error
    FILEIO_URL_UNSUPPORTED,
    FILEIO_URL_NO_RESOURCE, // remote file/dir did not exist
    FILEIO_LOCAL_FILE_OPEN_FAILURE,
    
    // Fatal error reading/writing local file
    FILEIO_LOCAL_IO_ERROR,
    
    // SSL errors
    FILEIO_SSL_UNTRUSTED_SERVER,
    FILEIO_SSL_OTHER_ERROR,

    // Login failure
    FILEIO_AUTH_ERROR,

    // Lack of permission on server after login
    FILEIO_ACCESS_DENIED,
    
    // Timeout
    FILEIO_TRANSFER_TIMEOUT,

    // Catch all of other errors
    FILEIO_OTHER_ERROR,
};


struct COMMONCOMMO_API SimpleFileIOUpdate
{
    const int xferid;

    const SimpleFileIOStatus status;
    const char * const additionalInfo;
    const uint64_t bytesTransferred;
    // if known, else 0
    const uint64_t totalBytesToTransfer;

protected:
    SimpleFileIOUpdate(const int xferid,
            const SimpleFileIOStatus status,
            const char *additionalInfo,
            uint64_t bytesTransferred,
            uint64_t totalBytesToTransfer) : xferid(xferid),
                                  status(status),
                                  additionalInfo(additionalInfo),
                                  bytesTransferred(bytesTransferred),
                                  totalBytesToTransfer(totalBytesToTransfer) {};
    virtual ~SimpleFileIOUpdate() {};

private:
    COMMO_DISALLOW_COPY(SimpleFileIOUpdate);
};

class COMMONCOMMO_API SimpleFileIO
{
public:
    virtual void fileTransferUpdate(const SimpleFileIOUpdate *update) = 0;

protected:
    SimpleFileIO() {};
    virtual ~SimpleFileIO() {};

private:
    COMMO_DISALLOW_COPY(SimpleFileIO);
};

}
}

#endif
