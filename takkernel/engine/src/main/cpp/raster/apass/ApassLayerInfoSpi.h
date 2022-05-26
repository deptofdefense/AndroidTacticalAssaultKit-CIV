#ifndef ATAKMAP_RASTER_APASS_APASSLAYERINFOSPI_H_INCLUDED
#define ATAKMAP_RASTER_APASS_APASSLAYERINFOSPI_H_INCLUDED

#include "raster/DatasetDescriptor.h"

namespace atakmap {
    namespace raster {
        namespace apass {
            class ApassLayerInfoSpi : public DatasetDescriptor::Factory {
            public:
                ApassLayerInfoSpi();
                virtual ~ApassLayerInfoSpi() NOTHROWS;

                //
                // Returns the parse version associated with this Factory.  The parse
                // version should be incremented every time the Factory's implementation is
                // modified to produce different results.
                //
                // The version number 0 is a reserved value that should never be returned.
                //
                virtual
                    unsigned short
                    getVersion()
                    const
                    NOTHROWS override;

                static bool convertDatabase(const char *apassDbPath, const char *atakDbPath, DatasetDescriptor::CreationCallback *callback = nullptr);

            private:

                typedef DatasetDescriptor::CreationCallback CreationCallback;
                typedef DatasetDescriptor::DescriptorSet    DescriptorSet;

                DescriptorSet*
                createImpl (const char* filePath,       // Never NULL.
                            const char* workingDir,     // May be NULL.
                            CreationCallback*)          // May be NULL.
                    const override;

                void
                    isSupported(const char* filePath, CreationCallback*)
                    const;

                static const char *IMAGES_TABLE_COLUMN_NAMES[];
                static std::vector<std::string> getColNames();
            };
        }
    }
}
#endif
