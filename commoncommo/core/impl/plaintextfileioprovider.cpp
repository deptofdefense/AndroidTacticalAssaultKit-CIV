#include "plaintextfileioprovider.h"
#include <stdio.h>
#include <sys/stat.h>
#include <stdexcept>

namespace atakmap {
namespace commoncommo {
namespace impl
{

FileHandle* PlainTextFileIOProvider::open(const char* path, const char * mode) {
    return fopen(path, mode);
}

int PlainTextFileIOProvider::close(FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return fclose(f);
}

size_t PlainTextFileIOProvider::write(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return fwrite(buf, size, nmemb, f);
}

size_t PlainTextFileIOProvider::read(void* buf, size_t size, size_t nmemb, FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return fread(buf, size, nmemb, f);
}

int PlainTextFileIOProvider::eof(FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return feof(f);
}

long PlainTextFileIOProvider::tell(FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return ftell(f);
}

int PlainTextFileIOProvider::seek(long offset, int whence, FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return fseek(f, offset, whence);
}

int PlainTextFileIOProvider::error(FileHandle* filePtr) {
    FILE *f = static_cast<FILE*>(filePtr);
    return ferror(f);
}

size_t PlainTextFileIOProvider::getSize(const char* path) COMMO_THROW (std::invalid_argument) {
    struct stat s;

    int r = ::stat(path, &s);
    if (r != 0)
        throw std::invalid_argument("cannot stat file");
    return s.st_size;
}

}
}
}
