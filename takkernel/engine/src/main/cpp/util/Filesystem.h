#ifndef TAK_ENGINE_UTIL_FILESYSTEM_H_INCLUDED
#define TAK_ENGINE_UTIL_FILESYSTEM_H_INCLUDED

#include <cstdio>
#include <cstdlib>

#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/NonCopyable.h"
#include "util/NonHeapAllocatable.h"


namespace TAK {
    namespace Engine {
        namespace Util {
            class ENGINE_API DataInput2;
            class ENGINE_API DataOutput2;

            enum ListFilesMethod {
                /** Lists all files and directories in the current directory */
                TELFM_Immediate,
                /** Lists all files in the current directory */
                TELFM_ImmediateFiles,
                /** Lists all files and directories in the current directory, and all subdirectories */
                TELFM_Recursive,
                /** Lists all files in the current directory, and all subdirectories */
                TELFM_RecursiveFiles
            };

            class ENGINE_API Filesystem
            {
            protected :
                ~Filesystem() NOTHROWS = default;
            public :
	            virtual TAKErr createTempFile(Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS = 0;
            	virtual TAKErr createTempDirectory(Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS = 0;
	            virtual TAKErr getFileCount(std::size_t *value, const char *path, const std::size_t limit = 0u) NOTHROWS = 0;
	            /**
	             * Lists files and subdirectories in the given directory
	             * @param value
	             * @param path
	             * @return
	             */
                virtual TAKErr listFiles(Port::Collection<Port::String> &value, const char *path) NOTHROWS = 0;
	            virtual TAKErr length(int64_t *value, const char* path) NOTHROWS = 0;
	            virtual TAKErr getLastModified(int64_t *value, const char* path) NOTHROWS = 0;
	            virtual TAKErr isDirectory(bool *value, const char* path) NOTHROWS = 0;
	            virtual TAKErr isFile(bool *value, const char* path) NOTHROWS = 0;
	            virtual TAKErr exists(bool *value, const char *path) NOTHROWS = 0;
	            /**
	             *  Deletes the file or directory at the specified path
	             * @param path
	             * @return
	             */
	            virtual TAKErr remove(const char *path) NOTHROWS = 0;
	            virtual TAKErr mkdirs(const char* dirPath) NOTHROWS = 0;
                virtual TAKErr openFile(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *path) NOTHROWS = 0;
                virtual TAKErr openFile(std::unique_ptr<DataOutput2, void(*)(const DataOutput2 *)> &dataPtr, const char *path) NOTHROWS = 0;
            };
        }
    }
}

#endif
