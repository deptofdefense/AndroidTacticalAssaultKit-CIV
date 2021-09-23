#ifndef ATAKMAP_RASTER_PFPSLAYERINFOSPI_H_INCLUDED
#define ATAKMAP_RASTER_PFPSLAYERINFOSPI_H_INCLUDED

#include "raster/DatasetDescriptor.h"

namespace atakmap {
    namespace raster {
        namespace pfps {

            class PfpsLayerInfoSpi : public DatasetDescriptor::Factory {

            public:
                PfpsLayerInfoSpi();
                ~PfpsLayerInfoSpi() NOTHROWS;


                virtual unsigned short getVersion() const NOTHROWS;

            private:

                typedef DatasetDescriptor::CreationCallback CreationCallback;
                typedef DatasetDescriptor::DescriptorSet    DescriptorSet;

                DescriptorSet *createImpl(const char* filePath,       // Never NULL.
                                          const char* workingDir,     // May be NULL.
                                          CreationCallback*)          // May be NULL.
                                          const;

                void isSupported(const char* filePath, CreationCallback*) const;

            };
        }
    }
}


#endif
