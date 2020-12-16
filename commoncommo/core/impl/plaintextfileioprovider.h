#ifndef IMPL_PLAINTEXTFILEIOPROVIDER_H_
#define IMPL_PLAINTEXTFILEIOPROVIDER_H_

#include "fileioprovider.h"
#include <stdio.h>

namespace atakmap {
namespace commoncommo {
namespace impl
{

class PlainTextFileIOProvider : public atakmap::commoncommo::FileIOProvider {
    FileHandle* open(const char* path, const char * mode) override;

    int close(FileHandle* filePtr) override;

    size_t write(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) override;

    size_t read(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) override;

    int eof(FileHandle* filePtr) override;

    long tell(FileHandle* filePtr) override;

    int seek(long offset, int whence, FileHandle* filePtr) override;

    int error(FileHandle* filePtr) override;

    size_t getSize(const char* path) override COMMO_THROW (std::invalid_argument);
};

}
}
}


#endif /* IMPL_PLAINTEXTFILEIOPROVIDER_H_ */
