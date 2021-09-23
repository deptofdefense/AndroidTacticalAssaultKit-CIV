#ifndef ATAKMAP_CPP_RASTER_IMAGERYFILETYPE_H_INCLUDED
#define ATAKMAP_CPP_RASTER_IMAGERYFILETYPE_H_INCLUDED

#include <vector>
#include <string>

#include "thread/Mutex.h"

namespace atakmap {
        namespace raster {
            /**
            * Attempts to determine a given File objects imagery file type. When reasonable logic/libraries
            * specific to each type are used to analyse objects. Otherwise the filename suffix is the default
            * in determining type. Notes - Suffix processing assumes the common imagery zip file naming
            * convention of name+type+zip; "NY_MOSAIC.sid.zip" - MIME types are currently not used but left as
            * a hook; incomplete.
            */
            class ImageryFileType
            {
            public :
                ImageryFileType(const char *desc);
                ~ImageryFileType();
            public :
                virtual const char *getDescription() const;
            private :
                virtual bool test(const char *path) const = 0;
            public :
                /**
                * Attempts to determine the file type otherwise returns null;
                *
                * @param source a File object
                */
                static const char *getFileType(const char *file);
                static void registerType(const ImageryFileType *type);
                static void unregisterType(const ImageryFileType *type);
            private :
                static TAK::Engine::Thread::Mutex mutex;
            private :
                const char *desc;
            };

            class ExtensionImageryFileType : public ImageryFileType
            {
            public :
                ExtensionImageryFileType(const char *desc, const char **ext, const size_t numExt);
            private :
                virtual bool test(const char *path) const;
            private :
                std::vector<std::string> extensions;
            };
        }
}

#endif ATAKMAP_CPP_RASTER_IMAGERYFILETYPE_H_INCLUDED
