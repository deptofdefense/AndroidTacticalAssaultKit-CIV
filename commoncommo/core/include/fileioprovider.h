#ifndef FILEIOPROVIDER_H_
#define FILEIOPROVIDER_H_

#include <stdio.h>
#include <stdexcept>

#define COMMO_THROW(...)

namespace atakmap {
namespace commoncommo {

typedef void FileHandle;

//abstract class to provide all file io operations
class FileIOProvider{
public:
    virtual FileHandle* open(const char* path, const char * mode) = 0;
    virtual int close(FileHandle* filePtr) = 0;
    virtual size_t write(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) = 0;
    virtual size_t read(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) = 0;
    virtual int eof(FileHandle* filePtr) = 0;
    virtual long tell(FileHandle* filePtr) = 0;
    virtual int seek(long offset, int whence, FileHandle* filePtr) = 0;
    virtual int error(FileHandle* filePtr) = 0;
    virtual size_t getSize(const char* path) = 0 COMMO_THROW (std::invalid_argument);
};

}
}



#endif /* FILEIOPROVIDER_H_ */
